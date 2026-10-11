package com.calculator.app.data.extractor

import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Extracts and validates a direct HTTPS media URL from a site's detail page.
 * The stream probe uses a tiny ranged request so search never downloads the video itself.
 */
object StreamResolver {

    suspend fun resolve(
        detailUrl: String,
        sourceSite: String,
        timeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MS
    ): String? = withContext(Dispatchers.IO) {
        try {
            val candidate = when (sourceSite.lowercase(Locale.ROOT)) {
                "eporner" -> resolveEporner(detailUrl, timeoutMillis)
                "spankbang", "hqporner" -> resolveGeneric(detailUrl, timeoutMillis)
                "xvideos", "xnxx" -> resolveXVideosFamily(detailUrl, timeoutMillis)
                else -> resolveGeneric(detailUrl, timeoutMillis)
            }
            validateStreamUrl(candidate)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    /** Resolves a stream and confirms it responds like playable MP4/HLS media. */
    suspend fun resolvePlayable(
        detailUrl: String,
        sourceSite: String,
        detailTimeoutMillis: Long = VERIFY_DETAIL_TIMEOUT_MS,
        probeTimeoutMillis: Long = VERIFY_PROBE_TIMEOUT_MS
    ): String? {
        val streamUrl = resolve(detailUrl, sourceSite, detailTimeoutMillis) ?: return null
        return if (isPlayable(streamUrl, detailUrl, probeTimeoutMillis)) streamUrl else null
    }

    /** Checks a direct URL with an HTTPS-only, small byte-range request. */
    suspend fun isPlayable(
        streamUrl: String,
        refererUrl: String? = null,
        timeoutMillis: Long = VERIFY_PROBE_TIMEOUT_MS
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val parsedUrl = streamUrl.toHttpUrlOrNull() ?: return@withContext false
            if (!parsedUrl.isHttps) return@withContext false

            val requestBuilder = Request.Builder()
                .url(parsedUrl)
                .header("Range", "bytes=0-1023")
                .get()
            val parsedReferer = refererUrl?.toHttpUrlOrNull()
            if (parsedReferer?.isHttps == true) {
                requestBuilder.header("Referer", parsedReferer.toString())
            }

            val call = NetworkClient.client.newCall(requestBuilder.build())
            call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
            call.execute().use { response ->
                val body = response.body ?: return@use false
                if (!response.isSuccessful) return@use false

                val contentType = body.contentType()?.toString()?.lowercase(Locale.ROOT).orEmpty()
                val path = parsedUrl.encodedPath.lowercase(Locale.ROOT)
                val isHls = path.endsWith(".m3u8") || contentType.contains("mpegurl")
                if (contentType.contains("text/html") || contentType.contains("application/json")) {
                    return@use false
                }

                if (isHls) {
                    body.source().peek().readUtf8Line()
                        ?.removePrefix("\uFEFF")
                        ?.trim() == "#EXTM3U"
                } else {
                    val isTextResponse = contentType.startsWith("text/") ||
                        contentType.contains("json") ||
                        contentType.contains("xml")
                    val hasMediaType = contentType.startsWith("video/") ||
                        contentType.contains("octet-stream") ||
                        contentType.isBlank()
                    val looksLikeMp4 = path.endsWith(".mp4")
                    body.contentLength() != 0L && !isTextResponse && (hasMediaType || looksLikeMp4)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private fun validateStreamUrl(candidate: String?): String? {
        val rawUrl = candidate?.trim()?.replace("\\/", "/") ?: return null
        val normalizedUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
        val parsedUrl = normalizedUrl.toHttpUrlOrNull() ?: return null
        return normalizedUrl.takeIf { parsedUrl.isHttps }
    }

    private fun fetchHtml(url: String, timeoutMillis: Long): String? {
        val parsedUrl = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        val request = Request.Builder().url(parsedUrl).build()
        val call = NetworkClient.client.newCall(request)
        call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
        call.execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun resolveEporner(detailUrl: String, timeoutMillis: Long): String? {
        val html = fetchHtml(detailUrl, timeoutMillis) ?: return null

        // Prefer adaptive HLS, then fall back to progressive MP4.
        val patterns = listOf(
            Pattern.compile("""https?://[^\"'\s<>]+\.m3u8[^\"'\s<>]*""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""https?://[^\"'\s<>]+\.mp4[^\"'\s<>]*""", Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                """[\"]src[\"]\s*:\s*[\"](https?://[^\"'\s<>]+\.(?:m3u8|mp4)[^\"'\s<>]*)[\"]""",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile("""file\s*:\s*['\"](https?://[^'\"\s<>]+)['\"]""", Pattern.CASE_INSENSITIVE)
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

        val jsonPattern = Pattern.compile(
            """EP\.video\.player\s*=\s*(\{.*?\})""",
            Pattern.DOTALL
        )
        val jsonMatcher = jsonPattern.matcher(html)
        if (jsonMatcher.find()) {
            val block = jsonMatcher.group(1) ?: return null
            val mediaPattern = Pattern.compile(
                """[\"](https?://[^\"'\s<>]+\.(?:m3u8|mp4)[^\"'\s<>]*)[\"]""",
                Pattern.CASE_INSENSITIVE
            )
            val mediaMatcher = mediaPattern.matcher(block)
            if (mediaMatcher.find()) return mediaMatcher.group(1)?.replace("\\/", "/")
        }

        return null
    }

    private fun resolveXVideosFamily(detailUrl: String, timeoutMillis: Long): String? {
        val html = fetchHtml(detailUrl, timeoutMillis) ?: return null

        // The High source is intentionally searched before the Low fallback.
        val patterns = listOf(
            Pattern.compile("""(?:html5player\.)?setVideoUrlHigh\(['\"](https?://[^'\"\s<>]+)['\"]\)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:html5player\.)?setVideoUrlLow\(['\"](https?://[^'\"\s<>]+)['\"]\)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                """[\"]contentUrl[\"]\s*:\s*[\"](https?://[^\"'\s<>]+\.mp4[^\"'\s<>]*)[\"]""",
                Pattern.CASE_INSENSITIVE
            )
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) return matcher.group(1)?.replace("\\/", "/")
        }
        return null
    }

    private fun resolveGeneric(detailUrl: String, timeoutMillis: Long): String? {
        val html = fetchHtml(detailUrl, timeoutMillis) ?: return null
        val patterns = listOf(
            Pattern.compile("""https?://[^\"'\s<>]+\.m3u8[^\"'\s<>]*""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""https?://[^\"'\s<>]+\.mp4[^\"'\s<>]*""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""[\"]file[\"]\s*:\s*[\"](https?://[^\"'\s<>]+)[\"]""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""[\"]source[\"]\s*:\s*[\"](https?://[^\"'\s<>]+)[\"]""", Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                """[\"]src[\"]\s*:\s*[\"](https?://[^\"'\s<>]+\.(?:m3u8|mp4)[^\"'\s<>]*)[\"]""",
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

        val doc = Jsoup.parse(html)
        doc.select("source[src], video[src]").forEach { element ->
            val src = element.attr("src")
            if (src.contains(".mp4", ignoreCase = true) || src.contains(".m3u8", ignoreCase = true)) return src
        }
        return null
    }

    private const val DEFAULT_REQUEST_TIMEOUT_MS = 20_000L
    private const val VERIFY_DETAIL_TIMEOUT_MS = 6_000L
    private const val VERIFY_PROBE_TIMEOUT_MS = 5_000L
}
