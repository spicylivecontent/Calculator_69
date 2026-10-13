package com.calculator.app.data.extractor

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.util.Locale

/** En variant i en HLS-masterplaylist. */
data class HlsVariant(
    val url: String,
    val bandwidth: Int,
    val width: Int?,
    val height: Int?
)

/**
 * Ren logik för att hitta, rangordna och tolka mediaströmmar.
 * Ingen Android-kod här – allt kan enhetstestas på JVM.
 */
object StreamSelection {

    private val urlPattern = Regex("""https?://[^\s"'<>]+""")
    private val relativePattern = Regex("""["'](/[^"'\s<>]+\.(?:m3u8|mp4)(?:\?[^"'\s<>]*)?)["']""", RegexOption.IGNORE_CASE)

    private val mediaHints = listOf(".m3u8", ".mp4", ".webm")
    private val rejectedHints = listOf(
        ".mpd", ".ts", ".vtt", ".srt", ".ass", ".jpg", ".jpeg", ".png", ".webp", ".gif",
        ".css", ".js", ".json", ".xml", ".html", ".woff", ".ico", ".svg"
    )
    private val junkHints = listOf(
        "preview", "trailer", "teaser", "sample", "thumb", "sprite", "poster",
        "placeholder", "placeholder", "blank", "1x1", "sticker", "banner"
    )
    private val audioHints = listOf(".mp3", ".m4a", ".aac", ".ogg", ".wav")

    private val removedPhrases = listOf(
        "video has been removed", "video was removed", "has been deleted", "no longer available",
        "content removed", "removed by the uploader", "404 not found", "page not found",
        "video is not available", "this video is unavailable"
    )

    /**
     * Gör om en rå URL till en absolut HTTPS-URL. Relativa sökvägar löses mot [baseUrl].
     * Returnerar null för allt som inte är HTTPS (appen tillåter ingen klartexttrafik).
     */
    fun normalize(raw: String?, baseUrl: String? = null): String? {
        val cleaned = raw
            ?.replace("\\/", "/")
            ?.replace("\\u002F", "/", ignoreCase = true)
            ?.trim()
            ?.trim('"', '\'', ',', ';', ')', ']', '}')
            ?.trim()
            ?: return null
        if (cleaned.isEmpty()) return null
        if (cleaned.contains(' ')) return null
        if (cleaned.startsWith("http://", ignoreCase = true)) return null

        val resolved = when {
            cleaned.startsWith("//") -> "https:$cleaned".toHttpUrlOrNull()
            cleaned.startsWith("https://", ignoreCase = true) -> cleaned.toHttpUrlOrNull()
            baseUrl != null -> baseUrl.toHttpUrlOrNull()?.resolve(cleaned)
            else -> null
        } ?: return null

        return if (resolved.isHttps) resolved.toString() else null
    }

    fun isHls(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains(".m3u8") || lower.contains("mpegurl")
    }

    fun isProgressive(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains(".mp4") || lower.contains(".webm")
    }

    fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (rejectedHints.any { lower.contains(it) }) return false
        if (audioHints.any { lower.contains(it) }) return false
        return mediaHints.any { lower.contains(it) }
    }

    /** Högre är bättre. Används för att välja vilken kandidat som provas först. */
    fun score(url: String): Int {
        val lower = url.lowercase(Locale.ROOT)
        var score = 0
        if (isHls(lower)) score += 60 else if (isProgressive(lower)) score += 40
        score += heightBonus(qualityFromUrl(lower))
        if (lower.contains("_hd") || lower.contains("-hd") || lower.contains("/hd") || lower.contains("high")) score += 14
        if (lower.contains("_sd") || lower.contains("low")) score -= 10
        if (junkHints.any { lower.contains(it) }) score -= 70
        if (lower.startsWith("https://")) score += 5
        return score
    }

    fun rankCandidates(urls: Collection<String>): List<String> =
        urls.mapNotNull { normalize(it) }
            .distinct()
            .filter { isMediaUrl(it) }
            .sortedWith(compareByDescending<String> { score(it) }.thenBy { it.length })

    /** Plockar ut alla troliga mediaströmmar ur en HTML-sida eller ett JS-block. */
    fun extractCandidates(html: String, baseUrl: String? = null): List<String> {
        if (html.isBlank()) return emptyList()
        val decoded = html.replace("\\/", "/").replace("\\u002F", "/", ignoreCase = true)
        val found = LinkedHashSet<String>()

        for (match in urlPattern.findAll(decoded)) {
            val candidate = normalize(match.value, baseUrl) ?: continue
            if (isMediaUrl(candidate)) found.add(candidate)
        }

        // Relativa sökvägar som ser ut som media (t.ex. "src":"/media/abc.mp4")
        for (match in relativePattern.findAll(decoded)) {
            val candidate = normalize(match.groupValues.getOrNull(1), baseUrl) ?: continue
            if (isMediaUrl(candidate)) found.add(candidate)
        }

        val doc = Jsoup.parse(decoded)
        for (element in doc.select("source[src], source[data-src], video[src], video[data-src]")) {
            val raw = element.attr("src").ifBlank { element.attr("data-src") }
            val candidate = normalize(raw, baseUrl) ?: continue
            if (isMediaUrl(candidate)) found.add(candidate)
        }

        return found.toList()
    }

    /** Gissar upplösning utifrån URL:en (t.ex. "..._1080p.mp4"). */
    fun qualityFromUrl(url: String): Int? {
        val lower = url.lowercase(Locale.ROOT)
        for (height in QUALITY_MARKERS) {
            if (lower.contains("${height}p") || lower.contains("/$height/") || lower.contains("_$height.")) return height
        }
        if (lower.contains("2160") || lower.contains("4k") || lower.contains("uhd")) return 2160
        return null
    }

    fun qualityLabel(height: Int?): String? = when {
        height == null -> null
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        height >= 360 -> "360p"
        else -> "${height}p"
    }

    fun looksRemoved(html: String): Boolean {
        val lower = html.lowercase(Locale.ROOT)
        return removedPhrases.any { lower.contains(it) }
    }

    fun isMasterPlaylist(manifest: String): Boolean = manifest.contains("#EXT-X-STREAM-INF")

    fun looksLikeHlsPlaylist(body: String?): Boolean =
        body?.trimStart('\uFEFF', ' ', '\n', '\r', '\t')?.startsWith("#EXTM3U") == true

    /** Tolkar en HLS-masterplaylist och returnerar alla varianter. */
    fun parseVariants(manifest: String, baseUrl: String): List<HlsVariant> {
        if (!manifest.contains("#EXTM3U")) return emptyList()
        val lines = manifest.lines().map { it.trim() }
        val variants = mutableListOf<HlsVariant>()

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (!line.startsWith("#EXT-X-STREAM-INF")) {
                index++
                continue
            }
            val attributes = parseAttributes(line.substringAfter(':', ""))
            val bandwidth = attributes["BANDWIDTH"]?.toIntOrNull()
                ?: attributes["AVERAGE-BANDWIDTH"]?.toIntOrNull()
                ?: 0
            val resolution = attributes["RESOLUTION"]
            val width = resolution?.substringBefore('x')?.trim()?.toIntOrNull()
            val height = resolution?.substringAfter('x')?.trim()?.toIntOrNull()

            var uriIndex = index + 1
            while (uriIndex < lines.size && (lines[uriIndex].isBlank() || lines[uriIndex].startsWith("#"))) {
                uriIndex++
            }
            if (uriIndex < lines.size) {
                normalize(lines[uriIndex], baseUrl)?.let { url ->
                    variants.add(HlsVariant(url = url, bandwidth = bandwidth, width = width, height = height))
                }
            }
            index = uriIndex + 1
        }
        return variants
    }

    /** Högsta tillgängliga variant – används för kvalitetsetiketten i resultatlistan. */
    fun bestVariant(manifest: String, baseUrl: String): HlsVariant? =
        parseVariants(manifest, baseUrl).maxWithOrNull(
            compareBy<HlsVariant> { it.height ?: 0 }.thenBy { it.bandwidth }
        )

    private fun parseAttributes(input: String): Map<String, String> {
        val attributes = LinkedHashMap<String, String>()
        val key = StringBuilder()
        val value = StringBuilder()
        var readingKey = true
        var inQuotes = false

        fun flush() {
            val name = key.toString().trim()
            val raw = value.toString().trim()
            if (name.isNotEmpty()) {
                attributes[name.uppercase(Locale.ROOT)] = raw.removePrefix("\"").removeSuffix("\"")
            }
            key.setLength(0)
            value.setLength(0)
            readingKey = true
        }

        for (char in input) {
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                    value.append(char)
                }
                char == '=' && readingKey -> readingKey = false
                char == ',' && !inQuotes -> flush()
                readingKey -> key.append(char)
                else -> value.append(char)
            }
        }
        flush()
        return attributes
    }

    private fun heightBonus(height: Int?): Int = when {
        height == null -> 0
        height >= 2160 -> 34
        height >= 1440 -> 30
        height >= 1080 -> 26
        height >= 720 -> 20
        height >= 480 -> 12
        height >= 360 -> 6
        else -> 2
    }

    private val QUALITY_MARKERS = listOf(2160, 1440, 1080, 720, 480, 360, 240)

    /** Är innehållstypen något som kan spelas upp som video/HLS? */
    fun isPlayableContentType(contentType: String?, url: String): Boolean {
        val type = contentType?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim().orEmpty()
        if (type.startsWith("text/") || type.contains("html")) return false
        if (type.contains("json") || type.contains("xml") || type.startsWith("image/")) return false
        if (isHls(url)) {
            return type.isEmpty() || type.contains("mpegurl") || type.contains("octet-stream") ||
                type.startsWith("video/") || type.startsWith("application/x-mpegurl")
        }
        return type.isEmpty() || type.startsWith("video/") || type.contains("octet-stream")
    }
}
