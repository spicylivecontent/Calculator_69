package com.calculator.app.data.extractor

import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Gemensam hämtning av HTML från sajterna med rimliga timeouts och maxstorlek.
 */
object Fetch {

    const val PAGE_TIMEOUT_MS = 12_000L
    private const val MAX_BODY_CHARS = 6_000_000

    suspend fun page(
        url: String,
        referer: String? = null,
        timeoutMs: Long = PAGE_TIMEOUT_MS,
        client: OkHttpClient = NetworkClient.client
    ): String? = withContext(Dispatchers.IO) { pageBlocking(url, referer, timeoutMs, client) }

    /** [client] är samma delade klient som standard; parametern finns för tester. */
    fun pageBlocking(
        url: String,
        referer: String? = null,
        timeoutMs: Long = PAGE_TIMEOUT_MS,
        client: OkHttpClient = NetworkClient.client
    ): String? {
        val parsed = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        val builder = Request.Builder().url(parsed).get()
        val parsedReferer = referer?.toHttpUrlOrNull()
        if (parsedReferer?.isHttps == true) {
            builder.header("Referer", parsedReferer.toString())
            builder.header("Origin", "${parsedReferer.scheme}://${parsedReferer.host}")
        }
        val call = client.newCall(builder.build())
        call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                if (body.contentLength() > MAX_BODY_CHARS) return null
                val text = body.string()
                text.take(MAX_BODY_CHARS)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
