package com.calculator.app.data.extractor

import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Gemensam hämtning av HTML från sajterna med rimliga timeouts och maxstorlek.
 *
 * En tillfällig svacka (timeout, nätverksfel eller 5xx/429) försöker vi en gång till efter
 * en kort paus – men bara när det faktiskt kan hjälpa: en sida som svarat 404 eller som
 * ligger på http:// görs det aldrig om försök på.
 */
object Fetch {

    const val PAGE_TIMEOUT_MS = 12_000L
    const val MAX_ATTEMPTS = 2

    private const val MAX_BODY_CHARS = 6_000_000
    private const val RETRY_DELAY_MS = 450L

    /** Vad ett enskilt försök ledde till. */
    internal sealed interface Attempt {
        data class Body(val text: String) : Attempt

        /** Servern svarade, men med ett fel (t.ex. 404, 429, 503). */
        data class HttpError(val code: Int) : Attempt

        /** Ingen kontakt: timeout, nätverksfel eller avbrott. */
        data object NetworkError : Attempt

        /** Förfrågan ska aldrig göras: klartext, för stor kropp eller tom adress. */
        data object Rejected : Attempt
    }

    /** Sant om ett nytt försök kan ge ett annat svar. Ren logik, testas separat. */
    internal fun shouldRetry(attempt: Attempt, attemptIndex: Int, attempts: Int): Boolean {
        if (attemptIndex >= attempts - 1) return false
        return when (attempt) {
            is Attempt.Body -> false
            Attempt.Rejected -> false
            Attempt.NetworkError -> true
            is Attempt.HttpError -> attempt.code == 429 || attempt.code in 500..599
        }
    }

    suspend fun page(
        url: String,
        referer: String? = null,
        timeoutMs: Long = PAGE_TIMEOUT_MS,
        client: OkHttpClient = NetworkClient.client,
        attempts: Int = MAX_ATTEMPTS
    ): String? {
        val total = attempts.coerceAtLeast(1)
        var last: Attempt = Attempt.Rejected
        for (index in 0 until total) {
            last = withContext(Dispatchers.IO) { attemptBlocking(url, referer, timeoutMs, client) }
            if (last is Attempt.Body) return last.text
            if (!shouldRetry(last, index, total)) break
            delay(RETRY_DELAY_MS * (index + 1))
        }
        return null
    }

    /** [client] är samma delade klient som standard; parametern finns för tester. */
    fun pageBlocking(
        url: String,
        referer: String? = null,
        timeoutMs: Long = PAGE_TIMEOUT_MS,
        client: OkHttpClient = NetworkClient.client
    ): String? = (attemptBlocking(url, referer, timeoutMs, client) as? Attempt.Body)?.text

    private fun attemptBlocking(
        url: String,
        referer: String?,
        timeoutMs: Long,
        client: OkHttpClient
    ): Attempt {
        val parsed = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return Attempt.Rejected
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
                if (!response.isSuccessful) return Attempt.HttpError(response.code)
                val body = response.body ?: return Attempt.NetworkError
                if (body.contentLength() > MAX_BODY_CHARS) return Attempt.Rejected
                val source = body.source()
                source.request(MAX_BODY_CHARS.toLong() + 1)
                if (source.buffer.size > MAX_BODY_CHARS) return Attempt.Rejected
                Attempt.Body(source.readString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Attempt.NetworkError
        }
    }

    fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
