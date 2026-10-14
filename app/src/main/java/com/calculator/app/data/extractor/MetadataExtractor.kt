package com.calculator.app.data.extractor

import com.calculator.app.data.model.DurationParser
import com.calculator.app.data.model.VideoMetadata
import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.util.Locale

/**
 * Läser ut all tillgänglig information om en film från dess detaljsida:
 * rubrik, beskrivning, taggar, kategorier, medverkande, kvalitet, längd och datum.
 *
 * Källor i prioritetsordning: strukturerad data (`application/ld+json`), Open Graph-
 * och itemprop-metataggar, och sist vanliga element på sidan. Ingen videoström
 * plockas fram – uppspelningen sker i WebView-spelaren på sajten.
 *
 * Resultaten sparas kortvarigt i [MetadataCache] (20 min): när samma detaljsida
 * berikas igen under en session återanvänds det första svaret i stället för att
 * hämta och tolka sidan en gång till.
 */
object MetadataExtractor {

    private const val MAX_DESCRIPTION_CHARS = 1_500
    private const val MAX_KEYWORDS = 40
    private const val MAX_COMMENTS = 25
    private const val MAX_COMMENT_CHARS = 240

    /** Hämtar och tolkar detaljsidan. Null om sidan inte svarar alls. */
    suspend fun fetch(
        detailUrl: String,
        siteName: String = "",
        client: OkHttpClient = NetworkClient.client
    ): VideoMetadata? {
        // Kort TTL-cache: samma detaljsida berikas ofta flera gånger under en
        // session, och då räcker det första svaret.
        MetadataCache.Default.get(detailUrl)?.let { return it }

        val html = try {
            Fetch.page(detailUrl, client = client)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return null

        return try {
            withContext(Dispatchers.Default) {
                parse(html, detailUrl, siteName).also { metadata ->
                    MetadataCache.Default.put(detailUrl, metadata)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    /** Ren tolkning av HTML – enhetstestbar. */
    fun parse(html: String, detailUrl: String, siteName: String = ""): VideoMetadata {
        if (html.isBlank()) {
            return VideoMetadata(detailUrl = detailUrl, pageMissing = true)
        }
        val doc = Jsoup.parse(html, detailUrl)
        val structured = structuredData(doc)
        val removed = StreamSelection.looksRemoved(html)

        val title = firstNonBlank(
            metaContent(doc, "meta[property=og:title]"),
            metaContent(doc, "meta[name=twitter:title]"),
            metaJsonString(structured, "name"),
            doc.selectFirst("h1")?.text(),
            doc.title()
        )

        val description = firstNonBlank(
            metaContent(doc, "meta[property=og:description]"),
            metaContent(doc, "meta[name=description]"),
            metaContent(doc, "meta[name=twitter:description]"),
            metaJsonString(structured, "description"),
            selectText(doc, DESCRIPTION_SELECTORS)
        )?.let { cleanText(it, MAX_DESCRIPTION_CHARS) }

        val metaKeywords = metaStrings(doc, "meta[name=keywords]")
        // keywords i strukturerad data är filmens taggar, genre är kategorier.
        val jsonTags = metaJsonStrings(structured, "keywords")
        val jsonGenres = metaJsonStrings(structured, "genre")

        val performers = keywordsMatching(doc, PERFORMER_HREFS) +
            metaJsonNames(structured, "actor") +
            metaJsonNames(structured, "director")
        val categories = keywordsMatching(doc, CATEGORY_HREFS) + jsonGenres
        val tags = (keywordsMatching(doc, TAG_HREFS) + jsonTags + containerKeywords(doc) + metaKeywords)
            .filterNot { candidate -> performerOrCategory(candidate, performers, categories) }

        val height = detectHeight(doc, title, description)
        val durationSeconds = detectDurationSeconds(doc, structured, title)
        val uploadDate = firstNonBlank(
            metaContent(doc, "meta[itemprop=uploadDate]"),
            metaJsonString(structured, "uploadDate"),
            doc.selectFirst("time[datetime]")?.attr("datetime")
        )
        val views = firstNonBlank(
            metaContent(doc, "meta[itemprop=interactionCount]"),
            selectText(doc, VIEW_SELECTORS)
        )?.let { cleanText(it, 40) }

        val comments = extractTopComments(doc, structured)

        return VideoMetadata(
            detailUrl = detailUrl,
            title = title?.let { cleanText(it, 200) },
            description = description,
            tags = tags.distinctKeywords(MAX_KEYWORDS),
            performers = performers.distinctKeywords(MAX_KEYWORDS),
            categories = categories.distinctKeywords(MAX_KEYWORDS),
            comments = comments,
            qualityLabel = height?.let { StreamSelection.qualityLabel(it) },
            durationSeconds = durationSeconds,
            uploadDate = uploadDate,
            views = views,
            pageMissing = removed
        )
    }

    // ------------------------------------------------------------------
    // Strukturerad data (JSON-LD)
    // ------------------------------------------------------------------

    private fun structuredData(doc: Document): Map<String, Any?>? {
        val scripts = doc.select("script[type=application/ld+json]")
        for (script in scripts) {
            val parsed = MiniJson.parse(script.data().ifBlank { script.html() }) ?: continue
            val video = MiniJson.findObject(parsed) { map ->
                val type = MiniJson.string(map["@type"]).orEmpty().lowercase(Locale.ROOT)
                type.contains("videoobject") || type.contains("movie") || type.contains("clip") ||
                    type.contains("tvseries") || type.contains("episode")
            }
            if (video != null) return video
        }
        // Fall tillbaka på det första objektet om inget har en känd @type.
        return scripts.firstNotNullOfOrNull { MiniJson.findObject(MiniJson.parse(it.data()), { true }) }
    }

    private fun metaJsonString(data: Map<String, Any?>?, key: String): String? =
        data?.let { MiniJson.string(it[key]) }

    private fun metaJsonStrings(data: Map<String, Any?>?, key: String): List<String> =
        data?.let { MiniJson.strings(it[key]) }.orEmpty()

    private fun metaJsonNames(data: Map<String, Any?>?, key: String): List<String> {
        val value = data?.get(key) ?: return emptyList()
        val direct = MiniJson.strings(value)
        if (direct.isNotEmpty()) return direct
        return when (value) {
            is Map<*, *> -> listOfNotNull(MiniJson.string(value["name"]))
            is List<*> -> value.mapNotNull { item ->
                when (item) {
                    is Map<*, *> -> MiniJson.string(item["name"])
                    else -> MiniJson.string(item)
                }
            }

            else -> emptyList()
        }
    }

    // ------------------------------------------------------------------
    // HTML-urval
    // ------------------------------------------------------------------

    private fun metaContent(doc: Document, selector: String): String? =
        doc.selectFirst(selector)?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }

    private fun metaStrings(doc: Document, selector: String): List<String> =
        doc.select(selector).flatMap { element ->
            element.attr("content").split(',', '|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

    private fun selectText(doc: Document, selectors: List<String>): String? {
        for (selector in selectors) {
            val text = doc.selectFirst(selector)?.text()?.trim()
            if (!text.isNullOrEmpty() && text.length >= 10) return text
        }
        return null
    }

    /** Som [selectText] men utan längdkrav – kvalitets- och tidsetiketter är ofta korta. */
    private fun selectShortText(doc: Document, selectors: List<String>): String? {
        for (selector in selectors) {
            val text = doc.selectFirst(selector)?.text()?.trim()
            if (!text.isNullOrEmpty()) return text
        }
        return null
    }

    /** Nyckelord från länkar vars adress avslöjar vad de är (taggar, kategorier, modeller). */
    private fun keywordsMatching(doc: Document, hrefMatch: List<String>): List<String> {
        val found = mutableListOf<String>()
        for (anchor in doc.select("a[href]")) {
            val href = anchor.attr("href").lowercase(Locale.ROOT)
            if (hrefMatch.none { href.contains(it) }) continue
            val text = anchor.text().trim()
            if (!isUsableKeyword(text)) continue
            found.add(text)
            if (found.size >= 120) break
        }
        return found
    }

    /** Nyckelord från typiska tagg-/kategoribehållare, även när länken inte säger något. */
    private fun containerKeywords(doc: Document): List<String> {
        val found = mutableListOf<String>()
        for (selector in TAG_CONTAINER_SELECTORS) {
            val containers = doc.select(selector)
            for (container in containers) {
                for (anchor in container.select("a[href]")) {
                    val text = anchor.text().trim()
                    if (isUsableKeyword(text)) found.add(text)
                }
                if (found.size >= 120) return found
            }
        }
        return found
    }

    private fun isUsableKeyword(text: String): Boolean {
        if (text.length !in 2..40) return false
        if (text.all { it.isDigit() }) return false
        if (text.contains('\n')) return false
        val lower = text.lowercase(Locale.ROOT)
        if (lower in NAVIGATION_WORDS) return false
        if (NAVIGATION_WORDS.any { lower == it }) return false
        if (lower.endsWith(".com") || lower.endsWith(".net") || lower.endsWith(".tv")) return false
        if (lower.startsWith("http")) return false
        return true
    }

    private fun performerOrCategory(candidate: String, performers: List<String>, categories: List<String>): Boolean {
        val lower = candidate.lowercase(Locale.ROOT)
        return performers.any { it.lowercase(Locale.ROOT) == lower } ||
            categories.any { it.lowercase(Locale.ROOT) == lower }
    }

    private fun List<String>.distinctKeywords(limit: Int): List<String> =
        map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(limit)

    /**
     * Skrapar toppkommentarerna (upp till [MAX_COMMENTS]) från strukturerad JSON-LD,
     * kommentarscontainrar i DOM:en samt inbäddad kommentarsdata i skriptblock.
     */
    private fun extractTopComments(doc: Document, structured: Map<String, Any?>?): List<String> {
        val collected = mutableListOf<String>()

        // 1. Strukturerad data (schema.org comment / commentText)
        val rawComments = structured?.get("comment")
        when (rawComments) {
            is Map<*, *> -> {
                val text = MiniJson.string(rawComments["text"]) ?: MiniJson.string(rawComments["commentText"])
                if (text != null) collected.add(text)
            }
            is List<*> -> {
                for (item in rawComments) {
                    val map = item as? Map<*, *> ?: continue
                    val text = MiniJson.string(map["text"]) ?: MiniJson.string(map["commentText"])
                    if (text != null) collected.add(text)
                    if (collected.size >= MAX_COMMENTS * 2) break
                }
            }
        }

        // 2. HTML-kommentarselement
        for (selector in COMMENT_SELECTORS) {
            for (element in doc.select(selector)) {
                val text = element.text().trim()
                if (text.isNotEmpty()) {
                    collected.add(text)
                    if (collected.size >= MAX_COMMENTS * 3) break
                }
            }
            if (collected.size >= MAX_COMMENTS * 3) break
        }

        // 3. Inbäddade JSON-kommentarer i skriptblock (vanligt på bl.a. XVideos, xHamster, SpankBang)
        if (collected.size < MAX_COMMENTS) {
            for (script in doc.select("script")) {
                val data = script.data()
                if (data.length < 20 || !data.contains("comment", ignoreCase = true)) continue
                for (match in SCRIPT_COMMENT_REGEX.findAll(data)) {
                    val raw = match.groupValues.getOrNull(1)?.trim().orEmpty()
                    if (raw.isNotEmpty()) {
                        collected.add(raw.replace("\\\"", "\"").replace("\\n", " "))
                        if (collected.size >= MAX_COMMENTS * 3) break
                    }
                }
                if (collected.size >= MAX_COMMENTS * 3) break
            }
        }

        return collected
            .map { cleanText(it, MAX_COMMENT_CHARS) }
            .filter { isUsableComment(it) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(MAX_COMMENTS)
    }

    private fun isUsableComment(text: String): Boolean {
        if (text.length !in 4..MAX_COMMENT_CHARS) return false
        val lower = text.lowercase(Locale.ROOT)
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) return false
        if (lower in COMMENT_BOILERPLATE) return false
        return text.any { it.isLetter() }
    }

    private fun detectHeight(doc: Document, title: String?, description: String?): Int? {
        val candidates = listOf(
            metaContent(doc, "meta[property=og:video:height]"),
            metaContent(doc, "meta[itemprop=height]"),
            doc.selectFirst("video[height]")?.attr("height")
        )
        for (candidate in candidates) {
            val value = candidate?.trim()?.toIntOrNull()
            if (value != null && value in 144..4320) return value
        }
        val fromTitle = StreamSelection.qualityFromUrl(title.orEmpty())
        if (fromTitle != null) return fromTitle
        val quality = selectShortText(doc, QUALITY_SELECTORS)
        return StreamSelection.qualityFromUrl(quality.orEmpty())
    }

    private fun detectDurationSeconds(
        doc: Document,
        structured: Map<String, Any?>?,
        title: String?
    ): Int? {
        val direct = listOf(
            metaContent(doc, "meta[property=video:duration]"),
            metaContent(doc, "meta[property=og:video:duration]"),
            metaContent(doc, "meta[itemprop=duration]"),
            doc.selectFirst("[itemprop=duration]")?.attr("content"),
            doc.selectFirst("[itemprop=duration]")?.text()
        )
        for (candidate in direct) {
            val seconds = candidate?.trim()?.let { raw ->
                raw.toIntOrNull() ?: parseIsoDuration(raw) ?: DurationParser.toSeconds(raw)
            }
            if (seconds != null && seconds > 0) return seconds
        }

        val iso = MiniJson.string(structured?.get("duration"))
        if (iso != null) {
            parseIsoDuration(iso)?.let { if (it > 0) return it }
            DurationParser.toSeconds(iso)?.let { if (it > 0) return it }
        }

        return DurationParser.toSeconds(title)
    }

    /** Tolkar ISO 8601-längder som "PT1H2M3S". */
    fun parseIsoDuration(raw: String?): Int? {
        val value = raw?.trim()?.uppercase(Locale.ROOT) ?: return null
        if (!value.startsWith("P")) return null
        val seconds = runCatching { java.time.Duration.parse(value).seconds }.getOrNull() ?: return null
        return seconds.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
    }

    private fun cleanText(raw: String, maxChars: Int): String =
        raw.replace(WHITESPACE, " ")
            .trim()
            .take(maxChars)

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private val WHITESPACE = Regex("\\s+")

    private val DESCRIPTION_SELECTORS = listOf(
        "[itemprop=description]",
        ".video-description",
        ".description",
        "#description",
        "[class*=video-desc]",
        "[class*=description]",
        ".video-details .text",
        "meta[name=twitter:description]"
    )

    private val VIEW_SELECTORS = listOf(
        "[class*=views]",
        "[id*=views]",
        ".view-count",
        "[itemprop=interactionCount]"
    )

    private val QUALITY_SELECTORS = listOf(
        ".quality",
        "[class*=quality]",
        "[class*=resolution]",
        "[class*=hd]",
        "video[data-quality]"
    )

    private val TAG_CONTAINER_SELECTORS = listOf(
        ".tags a[href]",
        ".video-tags a[href]",
        "[class*=video-tag] a[href]",
        "[id*=tags] a[href]",
        "[class*=tags] a[href]",
        "[class*=keywords] a[href]",
        "[class*=video-info] a[href]"
    )

    private val TAG_HREFS = listOf("/tag/", "/tags/", "/tag?", "tag=", "/keyword", "/niche")
    private val PERFORMER_HREFS = listOf("/pornstar", "/pornstars", "/model/", "/models/", "/actress", "/performer")
    private val CATEGORY_HREFS = listOf("/category", "/categories", "/channel", "/channels", "/genre")

    private val COMMENT_SELECTORS = listOf(
        "[itemprop=commentText]",
        "[itemprop=comment] [itemprop=text]",
        ".comment-text",
        ".comment_text",
        ".comment-body",
        ".comment-content",
        ".comment-message",
        ".cmt-text",
        ".user-comment",
        "[class*=comment-text]",
        "[class*=comment_text]",
        "[class*=commentBody]",
        "[class*=comment-content]",
        "#comments .comment p",
        ".comments .comment p",
        ".comments-list .comment p"
    )

    private val SCRIPT_COMMENT_REGEX = Regex(
        """"(?:comment(?:Text|_text|_body)?|comment_message)"\s*:\s*"([^"\\]{4,240})"""",
        RegexOption.IGNORE_CASE
    )

    private val COMMENT_BOILERPLATE = setOf(
        "write a comment", "add a comment", "post a comment", "post comment",
        "log in to comment", "login to comment", "sign in to comment",
        "load more comments", "show more comments", "no comments yet",
        "skriv en kommentar", "inga kommentarer"
    )

    private val NAVIGATION_WORDS = setOf(
        "home", "login", "log in", "sign up", "signup", "register", "upload", "videos", "video",
        "photos", "search", "menu", "next", "previous", "back", "more", "less", "all", "top",
        "new", "popular", "trending", "best", "hd", "4k", "porn", "porno", "sex", "free porn",
        "watch", "download", "share", "report", "dmca", "terms", "privacy", "contact", "help",
        "hem", "sök", "nästa", "föregående", "tillbaka", "mer", "alla", "topp", "ny", "populär"
    )
}
