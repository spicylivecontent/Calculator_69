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
