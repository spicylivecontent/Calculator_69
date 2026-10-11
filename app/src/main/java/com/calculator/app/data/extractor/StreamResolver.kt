package com.calculator.app.data.extractor

import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** En verifierad, spelbar ström med information om kvaliteten. */
data class ResolvedStream(
    val url: String,
    val referer: String?,
    val isHls: Boolean,
    val maxHeight: Int?,
    val bandwidth: Int?,
    val qualityLabel: String?
) {
    val isAdaptive: Boolean get() = isHls
}

/**
 * Hämtar en direkt mediaström från en sajts detaljsida och kontrollerar att den
 * verkligen svarar som video/HLS. Resultatet cachas kort så att upprepade
 * sökningar och uppspelning går snabbt.
 */
object StreamResolver {

    private const val DETAIL_TIMEOUT_MS = 7_000L
    private const val PROBE_TIMEOUT_MS = 4_500L
    private const val CACHE_TTL_MS = 20L * 60L * 1000L
    private const val MAX_CANDIDATES_TO_PROBE = 6
    private const val MAX_HLS_BYTES = 512 * 1024
    private const val RANGE_PROBE_BYTES = "bytes=0-2047"

    private const val PLAYER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private data class CacheEntry(val stream: ResolvedStream, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun cached(detailUrl: String): ResolvedStream? {
        val entry = cache[detailUrl] ?: return null
        if (entry.expiresAt < System.currentTimeMillis()) {
            cache.remove(detailUrl)
            return null
        }
        return entry.stream
    }

    fun remember(detailUrl: String, stream: ResolvedStream) {
        if (cache.size > MAX_CACHE_ENTRIES) cache.clear()
        cache[detailUrl] = CacheEntry(stream, System.currentTimeMillis() + CACHE_TTL_MS)
    }

    fun invalidate(detailUrl: String? = null) {
        if (detailUrl == null) cache.clear() else cache.remove(detailUrl)
    }

    /** Löser ut en stream och bekräftar att den svarar som spelbar media. */
    suspend fun resolvePlayable(
        detailUrl: String,
        sourceSite: String,
        forceRefresh: Boolean = false
    ): ResolvedStream? {
        if (detailUrl.isBlank()) return null
        val cachedStream = if (forceRefresh) null else cached(detailUrl)
        if (cachedStream != null && isPlayable(cachedStream.url, cachedStream.referer ?: detailUrl)) {
            return cachedStream
        }
        if (cachedStream != null) cache.remove(detailUrl)

        val resolved = resolve(detailUrl, sourceSite) ?: return null
        if (isPlayable(resolved.url, resolved.referer ?: detailUrl)) {
            remember(detailUrl, resolved)
            return resolved
        }
        return null
    }

    /** Löser ut bästa kandidat utan att göra en extra probe (probe görs ändå i [resolvePlayable]). */
    suspend fun resolve(detailUrl: String, sourceSite: String): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            val html = fetchDetail(detailUrl, sourceSite) ?: return@withContext null
            val candidates = LinkedHashSet<String>()
            candidates.addAll(StreamSelection.extractCandidates(html, detailUrl))
            candidates.addAll(siteSpecificCandidates(sourceSite, detailUrl))

            val ranked = StreamSelection.rankCandidates(candidates).take(MAX_CANDIDATES_TO_PROBE)
            for (candidate in ranked) {
                val resolved = inspect(candidate, detailUrl) ?: continue
                return@withContext resolved.also { remember(detailUrl, it) }
            }
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun siteSpecificCandidates(sourceSite: String, detailUrl: String): List<String> {
        if (sourceSite.lowercase() != "beeg") return emptyList()
        val id = detailUrl.trimEnd('/').substringAfterLast('/').substringBefore('?')
        if (id.isBlank() || !id.all { it.isDigit() }) return emptyList()
        val json = Fetch.pageBlocking("https://api.beeg.com/api/v6/video/$id?v=2", timeoutMs = DETAIL_TIMEOUT_MS)
            ?: return emptyList()
        return StreamSelection.extractCandidates(json, detailUrl)
    }

    private fun fetchDetail(detailUrl: String, sourceSite: String): String? {
        val html = Fetch.pageBlocking(detailUrl, timeoutMs = DETAIL_TIMEOUT_MS) ?: return null
        if (StreamSelection.looksRemoved(html)) return null
        return html
    }

    private fun inspect(url: String, referer: String?): ResolvedStream? {
        val probe = probe(url, referer) ?: return null
        return if (probe.isHls) {
            val manifest = fetchManifest(url, referer) ?: return null
            if (!StreamSelection.looksLikeHlsPlaylist(manifest)) return null
            val best = StreamSelection.bestVariant(manifest, url)
            val height = best?.height ?: StreamSelection.qualityFromUrl(url)
            ResolvedStream(
                url = url,
                referer = referer,
                isHls = true,
                maxHeight = height,
                bandwidth = best?.bandwidth,
                qualityLabel = StreamSelection.qualityLabel(height)?.let { "upp till $it" } ?: "Adaptiv"
            )
        } else {
            val height = StreamSelection.qualityFromUrl(url)
            ResolvedStream(
                url = url,
                referer = referer,
                isHls = false,
                maxHeight = height,
                bandwidth = null,
                qualityLabel = StreamSelection.qualityLabel(height)
            )
        }
    }

    private data class ProbeResult(val isHls: Boolean, val contentType: String?)

    private fun probe(url: String, referer: String?): ProbeResult? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!parsed.isHttps) return null

        val builder = Request.Builder()
            .url(parsed)
            .header("Range", RANGE_PROBE_BYTES)
            .get()
        applyMediaHeaders(builder, referer)

        val call = NetworkClient.client.newCall(builder.build())
        call.timeout().timeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) return null
                val contentType = response.body?.contentType()?.toString()
                val isHls = StreamSelection.isHls(url) ||
                    contentType?.contains("mpegurl", ignoreCase = true) == true
                if (!StreamSelection.isPlayableContentType(contentType, url)) return null
                ProbeResult(isHls = isHls, contentType = contentType)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchManifest(url: String, referer: String?): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!parsed.isHttps) return null
        val builder = Request.Builder().url(parsed).get()
        applyMediaHeaders(builder, referer)
        val call = NetworkClient.client.newCall(builder.build())
        call.timeout().timeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val length = body.contentLength()
                if (length > MAX_HLS_BYTES) return null
                body.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Snabb kontroll att en stream svarar som spelbar media. */
    suspend fun isPlayable(
        streamUrl: String,
        refererUrl: String? = null,
        timeoutMillis: Long = PROBE_TIMEOUT_MS
    ): Boolean = withContext(Dispatchers.IO) {
        if (streamUrl.isBlank()) return@withContext false
        try {
            val probe = probe(streamUrl, refererUrl)
            if (probe == null) return@withContext false
            if (!probe.isHls) return@withContext true
            val manifest = fetchManifest(streamUrl, refererUrl)
            StreamSelection.looksLikeHlsPlaylist(manifest)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    fun mediaHeaders(refererUrl: String?): Map<String, String> {
        val referer = refererUrl?.toHttpUrlOrNull() ?: return emptyMap()
        if (!referer.isHttps) return emptyMap()
        return mapOf(
            "Referer" to referer.toString(),
            "Origin" to "${referer.scheme}://${referer.host}",
            "User-Agent" to PLAYER_USER_AGENT
        )
    }

    private fun applyMediaHeaders(builder: Request.Builder, referer: String?) {
        for ((name, value) in mediaHeaders(referer)) builder.header(name, value)
        builder.header("Accept", "*/*")
    }

    private const val MAX_CACHE_ENTRIES = 400
}
