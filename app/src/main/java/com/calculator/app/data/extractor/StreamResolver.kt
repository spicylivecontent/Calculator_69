package com.calculator.app.data.extractor

import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.regex.Pattern

/**
 * Försöker extrahera en direkt stream-URL (mp4/m3u8) från en detaljsida.
 * Best-effort – olika sajter har olika skydd.
 */
object StreamResolver {

    suspend fun resolve(detailUrl: String, sourceSite: String): String? = withContext(Dispatchers.IO) {
        try {
            when (sourceSite.lowercase()) {
                "eporner" -> resolveEporner(detailUrl)
                "spankbang" -> resolveGeneric(detailUrl)
                "hqporner" -> resolveGeneric(detailUrl)
                "xvideos", "xnxx" -> resolveXVideosFamily(detailUrl)
                else -> resolveGeneric(detailUrl)
            }
        } catch (_: Exception) {
            null
        }
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

        // Eporner har ofta download-länkar eller player-källor
        val patterns = listOf(
            Pattern.compile("https?://[^"]+\\.mp4[^"]*"),
            Pattern.compile("https?://[^"]+\\.m3u8[^"]*"),
            Pattern.compile(""src"\\s*:\\s*"(https?://[^"]+\\.(mp4|m3u8)[^"]*)""),
            Pattern.compile("file\\s*:\\s*['\"](https?://[^'\"]+)['\"]")
        )

        for (p in patterns) {
            val m = p.matcher(html)
            while (m.find()) {
                val url = if (m.groupCount() >= 1 && m.group(1) != null) m.group(1) else m.group(0)
                if (url != null && (url.contains(".mp4") || url.contains(".m3u8"))) {
                    return url.replace("\\/", "/")
                }
            }
        }

        // Försök hitta EP.video eller liknande JSON
        val jsonPattern = Pattern.compile("EP\\.video\\.player\\s*=\\s*(\\{.*?\\})", Pattern.DOTALL)
        val jm = jsonPattern.matcher(html)
        if (jm.find()) {
            val block = jm.group(1) ?: return null
            val mp4 = Pattern.compile(""(https?://[^"]+\\.mp4[^"]*)"").matcher(block)
            if (mp4.find()) return mp4.group(1)?.replace("\\/", "/")
        }

        return null
    }

    private fun resolveXVideosFamily(detailUrl: String): String? {
        val html = fetchHtml(detailUrl) ?: return null

        // setVideoUrlHigh / setVideoUrlLow / html5player.setVideoUrl
        val patterns = listOf(
            Pattern.compile("setVideoUrlHigh\\(['\"](https?://[^'\"]+)['\"]\\)"),
            Pattern.compile("setVideoUrlLow\\(['\"](https?://[^'\"]+)['\"]\\)"),
            Pattern.compile("html5player\\.setVideoUrlHigh\\(['\"](https?://[^'\"]+)['\"]\\)"),
            Pattern.compile("html5player\\.setVideoUrlLow\\(['\"](https?://[^'\"]+)['\"]\\)"),
            Pattern.compile(""contentUrl"\\s*:\\s*"(https?://[^"]+\\.mp4[^"]*)"")
        )

        for (p in patterns) {
            val m = p.matcher(html)
            if (m.find()) {
                return m.group(1)?.replace("\\/", "/")
            }
        }
        return null
    }

    private fun resolveGeneric(detailUrl: String): String? {
        val html = fetchHtml(detailUrl) ?: return null

        // Generiska mönster för mp4/m3u8 i sidans källkod
        val patterns = listOf(
            Pattern.compile("https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*"),
            Pattern.compile("https?://[^\"'\\s]+\\.mp4[^\"'\\s]*"),
            Pattern.compile(""file"\\s*:\\s*"(https?://[^"]+)""),
            Pattern.compile(""source"\\s*:\\s*"(https?://[^"]+)""),
            Pattern.compile(""src"\\s*:\\s*"(https?://[^"]+\\.(mp4|m3u8)[^"]*)"")
        )

        for (p in patterns) {
            val m = p.matcher(html)
            while (m.find()) {
                val candidate = when {
                    m.groupCount() >= 1 && m.group(1) != null -> m.group(1)
                    else -> m.group(0)
                }?.replace("\\/", "/")

                if (candidate != null &&
                    (candidate.contains(".mp4") || candidate.contains(".m3u8")) &&
                    !candidate.contains("preview") &&
                    !candidate.contains("thumb")
                ) {
                    return candidate
                }
            }
        }

        // Jsoup: video/source-taggar
        val doc = Jsoup.parse(html)
        doc.select("source[src], video[src]").forEach { el ->
            val src = el.attr("src")
            if (src.contains(".mp4") || src.contains(".m3u8")) return src
        }

        return null
    }
}
