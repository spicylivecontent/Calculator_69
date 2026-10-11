package com.calculator.app.data.extractor

import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.Locale
import java.util.regex.Pattern

/**
 * Försöker extrahera en direkt stream-URL (mp4/m3u8) från en detaljsida.
 * Best-effort – olika sajter har olika skydd.
 */
object StreamResolver {

    suspend fun resolve(detailUrl: String, sourceSite: String): String? = withContext(Dispatchers.IO) {
        try {
            val candidate = when (sourceSite.lowercase(Locale.ROOT)) {
                "eporner" -> resolveEporner(detailUrl)
                "spankbang" -> resolveGeneric(detailUrl)
                "hqporner" -> resolveGeneric(detailUrl)
                "xvideos", "xnxx" -> resolveXVideosFamily(detailUrl)
                else -> resolveGeneric(detailUrl)
            }
            validateStreamUrl(candidate)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun validateStreamUrl(candidate: String?): String? {
        val rawUrl = candidate?.trim()?.replace("\\/", "/") ?: return null
        val normalizedUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
        val parsedUrl = normalizedUrl.toHttpUrlOrNull() ?: return null
        return normalizedUrl.takeIf { parsedUrl.isHttps }
    }

    private fun fetchHtml(url: String): String? {
        val request = Request.Builder().url(url).build()
        NetworkClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun resolveEporner(detailUrl: String): String? {
        val html = fetchHtml(detailUrl) ?: return null

        // Eporner har ofta download-länkar eller player-källor.
        val patterns = listOf(
            Pattern.compile("""https?://[^"'\s<>]+\.mp4[^"'\s<>]*""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""https?://[^"'\s<>]+\.m3u8[^"'\s<>]*""", Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                """["]src["]\s*:\s*["](https?://[^"'\s<>]+\.(?:mp4|m3u8)[^"'\s<>]*)["]""",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile("""file\s*:\s*['"](https?://[^'"\s<>]+)['"]""", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                val url = if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group()
                if (url != null && (url.contains(".mp4", ignoreCase = true) || url.contains(".m3u8", ignoreCase = true))) {
                    return url.replace("\\/", "/")
                }
            }
        }

        // Försök hitta EP.video eller liknande JSON.
        val jsonPattern = Pattern.compile(
            """EP\.video\.player\s*=\s*(\{.*?\})""",
            Pattern.DOTALL
        )
        val jsonMatcher = jsonPattern.matcher(html)
        if (jsonMatcher.find()) {
            val block = jsonMatcher.group(1) ?: return null
            val mp4Pattern = Pattern.compile(
                """["](https?://[^"'\s<>]+\.mp4[^"'\s<>]*)["]""",
                Pattern.CASE_INSENSITIVE
            )
            val mp4Matcher = mp4Pattern.matcher(block)
            if (mp4Matcher.find()) return mp4Matcher.group(1)?.replace("\\/", "/")
        }

        return null
    }

    private fun resolveXVideosFamily(detailUrl: String): String? {
        val html = fetchHtml(detailUrl) ?: return null

        // setVideoUrlHigh / setVideoUrlLow / html5player.setVideoUrl
        val patterns = listOf(
            Pattern.compile("""(?:html5player\.)?setVideoUrlHigh\(['"](https?://[^'"\s<>]+)['"]\)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:html5player\.)?setVideoUrlLow\(['"](https?://[^'"\s<>]+)['"]\)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                """["]contentUrl["]\s*:\s*["](https?://[^"'\s<>]+\.mp4[^"'\s<>]*)["]""",
                Pattern.CASE_INSENSITIVE
            )
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                return matcher.group(1)?.replace("\\/", "/")
            }
        }
        return null
    }

    private fun resolveGeneric(detailUrl: String): String? {
        val html = fetchHtml(detailUrl) ?: return null

        // Generiska mönster för mp4/m3u8 i sidans källkod.
        val patterns = listOf(
            Pattern.compile("""https?://[^"'\s<>]+\.m3u8[^"'\s<>]*""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""https?://[^"'\s<>]+\.mp4[^"'\s<>]*""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""["]file["]\s*:\s*["](https?://[^"'\s<>]+)["]""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""["]source["]\s*:\s*["](https?://[^"'\s<>]+)["]""", Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                """["]src["]\s*:\s*["](https?://[^"'\s<>]+\.(?:mp4|m3u8)[^"'\s<>]*)["]""",
                Pattern.CASE_INSENSITIVE
            )
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                val candidate = (if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group())
                    ?.replace("\\/", "/")

                if (candidate != null &&
                    (candidate.contains(".mp4", ignoreCase = true) || candidate.contains(".m3u8", ignoreCase = true)) &&
                    !candidate.contains("preview", ignoreCase = true) &&
                    !candidate.contains("thumb", ignoreCase = true)
                ) {
                    return candidate
                }
            }
        }

        // Jsoup: video/source-taggar.
        val doc = Jsoup.parse(html)
        doc.select("source[src], video[src]").forEach { element ->
            val src = element.attr("src")
            if (src.contains(".mp4", ignoreCase = true) || src.contains(".m3u8", ignoreCase = true)) return src
        }

        return null
    }
}
