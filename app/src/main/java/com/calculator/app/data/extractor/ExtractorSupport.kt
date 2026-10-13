package com.calculator.app.data.extractor

import com.calculator.app.data.model.DurationParser
import com.calculator.app.data.model.SearchResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale

/**
 * Strukturell parsning av resultatlistor: i stället för att lita på varje sajts
 * klassnamn letar vi upp alla länkar som matchar sajtens video-URL och plockar
 * titel, tumnagel och längd från närmaste omgivande block. Det gör parsningen
 * betydligt mindre känslig för att sajterna byter layout.
 */
data class ListingConfig(
    val siteName: String,
    val baseUrl: String,
    val hrefPattern: Regex,
    val idGroup: Int = 1,
    val requireHrefContains: String? = null,
    val titleSelectors: List<String> = DEFAULT_TITLE_SELECTORS,
    val thumbAttributes: List<String> = DEFAULT_THUMB_ATTRIBUTES,
    val durationSelectors: List<String> = DEFAULT_DURATION_SELECTORS,
    val limit: Int = DEFAULT_LIMIT
) {
    companion object {
        val DEFAULT_TITLE_SELECTORS = listOf(
            "p.title", "a.title", ".title", ".video-title", ".video-thumb-info__name",
            ".name", "span.name", "h3", "h2", "strong", "p"
        )
        val DEFAULT_THUMB_ATTRIBUTES = listOf(
            "data-src", "data-original", "data-thumb", "data-thumbnail", "data-mediumthumb",
            "data-lazy-src", "data-image", "src"
        )
        val DEFAULT_DURATION_SELECTORS = listOf(
            ".duration", "[class*=duration]", ".dur", ".length", ".time", "[class*=time]", "var"
        )
        const val DEFAULT_LIMIT = 36
    }
}

object ExtractorSupport {

    private val brands = listOf(
        "xvideos", "xnxx", "xhamster", "pornhub", "eporner", "spankbang", "hqporner",
        "beeg", "youporn", "porntrex", "hd porn", "tube", "porn"
    )

    private val durationInText = Regex("\\b(\\d{1,2}:\\d{2}(?::\\d{2})?)\\b")

    /** Länkar som aldrig är en videosida. */
    private val NOT_VIDEO_HINTS = listOf(
        "/tag/", "/tags/", "/category", "/categories", "/channel", "/model", "/models/",
        "/pornstar", "/actress", "/performer", "/login", "/signup", "/register", "/search",
        "/page/", "/user/", "/profile", "/settings", "/dmca", "/terms", "/privacy", "/contact",
        "/help", "/upload", "/premium", "/vip", "/live/", "/cams/", "/photos/", "/photo/",
        "/blog", "/news", "/forum", "/community", ".css", ".js", ".json", ".xml", ".jpg",
        ".jpeg", ".png", ".gif", ".webp", ".svg", ".ico"
    )

    /** Filändelser som betyder media, inte en detaljsida. */
    private val VIDEO_FILE_HINTS = listOf(".m3u8", ".mp4", ".webm", ".mpd", ".ts")
    private val whitespace = Regex("\\s+")

    fun parseListings(html: String, config: ListingConfig): List<SearchResult> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, config.baseUrl)
        val results = LinkedHashMap<String, SearchResult>()

        for (anchor in doc.select("a[href]")) {
            val href = anchor.attr("href")
            if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript")) continue
            if (config.requireHrefContains != null && !href.contains(config.requireHrefContains)) continue

            val match = config.hrefPattern.find(href) ?: continue
            val rawId = match.groupValues.getOrNull(config.idGroup)?.trim().orEmpty()
            val id = rawId.ifBlank {
                href.trimEnd('/').substringAfterLast('/').substringBefore('?')
            }
            if (id.isBlank() || id.length > 64) continue

            val detailUrl = absoluteUrl(config.baseUrl, href) ?: continue
            if (!detailUrl.startsWith("https://")) continue

            val container = anchor.closest("div, li, article, figure, section")
                ?: anchor.parent()
                ?: anchor

            val title = titleFrom(anchor, container, config)
                ?: titleFromSlug(href)
                ?: continue
            val key = "${config.siteName}_$id"
            val thumb = imageUrl(container, anchor, config)

            val existing = results[key]
            if (existing != null) {
                if (existing.thumbnailUrl.isNullOrBlank() && !thumb.isNullOrBlank()) {
                    results[key] = existing.copy(thumbnailUrl = thumb)
                }
                continue
            }

            val duration = durationFrom(container, config)
            results[key] = SearchResult(
                id = key,
                title = title,
                thumbnailUrl = thumb,
                duration = duration,
                durationSeconds = DurationParser.toSeconds(duration),
                detailUrl = detailUrl,
                sourceSite = config.siteName,
                qualityLabel = qualityFromTitle(title)
            )

            if (results.size >= config.limit) break
        }

        return results.values.toList()
    }

    /**
     * Reservparsning som används när sajtens egen konfiguration inte hittar något.
     *
     * I stället för att lita på klassnamn eller URL-mönster letar vi upp länkar som
     * *ser ut* som videosidor (en slug med minst två ord, eller en siffer-id) och
     * plockar titel, tumnagel och längd från närmaste omgivande block. Det gör att en
     * sajt som bytt layout eller klassnamn ändå ger träffar.
     */
    fun parseListingsGeneric(
        html: String,
        baseUrl: String,
        siteName: String,
        limit: Int = ListingConfig.DEFAULT_LIMIT
    ): List<SearchResult> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, baseUrl)
        val config = genericConfig(siteName, baseUrl)
        val results = LinkedHashMap<String, SearchResult>()

        for (anchor in doc.select("a[href]")) {
            if (results.size >= limit) break
            val href = anchor.attr("href").trim()
            if (!looksLikeVideoHref(href)) continue

            val detailUrl = StreamSelection.normalize(href, baseUrl) ?: continue
            if (!detailUrl.startsWith("https://")) continue

            val path = detailUrl.substringAfter("://").substringAfter('/', "").substringBefore('?')
            val id = path.trimEnd('/').substringAfterLast('/')
            if (id.isBlank() || id.length > 64) continue

            val container = anchor.closest("div, li, article, figure, section") ?: anchor.parent() ?: anchor
            // Saknar länken rubrik får sluggen i URL:en bli titeln, precis som i den
            // konfigurerade parsningen – annars tappas träffar som bara visar en bild.
            val title = titleFrom(anchor, container, config) ?: titleFromSlug(href) ?: continue
            val key = "${siteName}_$id"
            val thumb = imageUrl(container, anchor, config)

            val existing = results[key]
            if (existing != null) {
                if (existing.thumbnailUrl.isNullOrBlank() && !thumb.isNullOrBlank()) {
                    results[key] = existing.copy(thumbnailUrl = thumb)
                }
                continue
            }

            val duration = durationFrom(container, config)
            results[key] = SearchResult(
                id = key,
                title = title,
                thumbnailUrl = thumb,
                duration = duration,
                durationSeconds = DurationParser.toSeconds(duration),
                detailUrl = detailUrl,
                sourceSite = siteName,
                qualityLabel = qualityFromTitle(title)
            )
        }
        return results.values.toList()
    }

    /** Heuristik: ser länken ut som en videosida? */
    fun looksLikeVideoHref(href: String?): Boolean {
        val raw = href?.trim().orEmpty()
        if (raw.isEmpty() || raw.startsWith("#")) return false
        val lower = raw.lowercase(Locale.ROOT)
        if (lower.startsWith("javascript") || lower.startsWith("mailto") || lower.startsWith("tel")) return false
        if (NOT_VIDEO_HINTS.any { lower.contains(it) }) return false

        val path = lower.substringBefore('?').substringBefore('#')
        val segments = path.trimEnd('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return false

        val last = segments.last().substringBeforeLast('.')
        if (VIDEO_FILE_HINTS.any { path.endsWith(it) }) return false
        if (last.length < 6 || last.length > 90) return false

        val words = last.split('-', '_', '+').filter { it.isNotBlank() }
        if (words.size >= 2 && words.count { it.length >= 2 } >= 2) return true
        // Numeriskt id (minst fyra siffror) eller en blandad slug med siffror
        if (last.all { it.isDigit() } && last.length >= 4) return true
        return last.count { it.isDigit() } >= 3 && last.count { it.isLetter() } >= 4
    }

    private fun genericConfig(siteName: String, baseUrl: String): ListingConfig =
        ListingConfig(
            siteName = siteName,
            baseUrl = baseUrl,
            hrefPattern = Regex("."),
            limit = ListingConfig.DEFAULT_LIMIT
        )
    fun absoluteUrl(baseUrl: String, href: String?): String? = StreamSelection.normalize(href, baseUrl)

    private fun titleFrom(anchor: Element, container: Element, config: ListingConfig): String? {
        val candidates = mutableListOf<String>()
        anchor.attr("title").takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        for (selector in config.titleSelectors) {
            container.selectFirst(selector)?.text()?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        }
        container.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        anchor.text().takeIf { it.isNotBlank() }?.let { candidates.add(it) }

        for (candidate in candidates) {
            val cleaned = cleanTitle(candidate) ?: continue
            if (cleaned.length >= 3) return cleaned
        }
        return null
    }

    fun cleanTitle(raw: String?): String? {
        var title = raw?.replace(whitespace, " ")?.trim() ?: return null
        if (title.isEmpty()) return null
        title = title.trim(' ', '-', '|', '·', ':', '.', ',')
        for (separator in listOf(" | ", " — ", " – ", " - ")) {
            val index = title.lastIndexOf(separator)
            if (index >= 3) {
                val tail = title.substring(index + separator.length).trim()
                if (isBrandish(tail)) title = title.substring(0, index).trim()
            }
        }
        title = title.trim(' ', '-', '|', '·', ':', '.', ',')
        if (title.length < 3) return null
        return title.take(180)
    }

    /** Sista utvägen: bygg en läsbar titel av URL-sluggen (t.ex. "blonde_in_the_kitchen"). */
    fun titleFromSlug(href: String): String? {
        val path = href.substringBefore('?').substringBefore('#').trimEnd('/')
        val segment = path.split('/').lastOrNull { it.isNotBlank() } ?: return null
        var slug = segment.substringBeforeLast('.')
        slug = slug.replace(Regex("[-_+]+"), " ")
            .replace(Regex("\\b\\d{4,}\\b"), " ")
            .replace(whitespace, " ")
            .trim()
        if (slug.length < 4) return null
        return slug.replaceFirstChar { it.uppercase() }
    }

    private fun isBrandish(tail: String): Boolean {
        val lower = tail.lowercase(Locale.ROOT)
        if (lower.length > 30) return false
        if (lower.contains('.')) return true
        return brands.any { brand -> lower == brand || lower.startsWith("$brand ") || lower.endsWith(" $brand") }
    }

    fun imageUrl(container: Element?, anchor: Element?, config: ListingConfig): String? {
        val elements = listOfNotNull(container, anchor)
        for (element in elements) {
            val images = element.select("img")
            for (image in images) {
                for (attribute in config.thumbAttributes) {
                    val value = image.attr(attribute)
                    if (value.isBlank()) continue
                    val normalized = StreamSelection.normalize(value, config.baseUrl)
                    if (normalized != null && !normalized.contains("blank.gif")) return normalized
                }
                val srcset = image.attr("srcset").ifBlank { image.attr("data-srcset") }
                if (srcset.isNotBlank()) {
                    val first = srcset.split(',').firstOrNull()?.trim()?.substringBefore(' ')
                    StreamSelection.normalize(first, config.baseUrl)?.let { return it }
                }
            }
        }
        return null
    }

    fun durationFrom(container: Element?, config: ListingConfig): String? {
        if (container == null) return null
        for (selector in config.durationSelectors) {
            val element = container.selectFirst(selector) ?: continue
            val text = element.text().trim()
            if (DurationParser.toSeconds(text) != null) return text
        }
        val text = container.text().take(300)
        val match = durationInText.find(text) ?: return null
        return match.groupValues.getOrNull(1)
    }

    fun qualityFromTitle(title: String): String? {
        val lower = title.lowercase(Locale.ROOT)
        return when {
            lower.contains("2160") || lower.contains("4k") || lower.contains("uhd") -> "4K"
            lower.contains("1440") -> "1440p"
            lower.contains("1080") -> "1080p"
            lower.contains("720") -> "720p"
            lower.contains("hd") -> "HD"
            else -> null
        }
    }
}
