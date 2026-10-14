package com.calculator.app.data.extractor

import com.calculator.app.network.HostRateLimiter
import com.calculator.app.network.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Gemensam hämtning av HTML från sajterna med rimliga timeouts och maxstorlek.
 * Alla anrop går via [HostRateLimiter] så att en domän inte får för täta request.
 */
object Fetch {

    const val PAGE_TIMEOUT_MS = 12_000L
    const val MAX_ATTEMPTS = 2

    private const val MAX_BODY_CHARS = 6_000_000
    private const val RETRY_DELAY_MS = 450L
    private const val MAX_REDIRECTS = 5
    private val REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)

    internal sealed interface Attempt {
        data class Body(val text: String) : Attempt
        data class HttpError(val code: Int) : Attempt
        data object NetworkError : Attempt
        data class Redirect(val target: okhttp3.HttpUrl) : Attempt
        data object Rejected : Attempt
    }

    internal fun shouldRetry(attempt: Attempt, attemptIndex: Int, attempts: Int): Boolean {
        if (attemptIndex >= attempts - 1) return false
        return when (attempt) {
            is Attempt.Body -> false
            is Attempt.Redirect, Attempt.Rejected -> false
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
    ): String? = HostRateLimiter.withPermit(url) {
        val total = attempts.coerceAtLeast(1)
        var last: Attempt = Attempt.Rejected
        for (index in 0 until total) {
            last = attempt(url, referer, timeoutMs, client)
            if (last is Attempt.Body) return@withPermit last.text
            if (!shouldRetry(last, index, total)) break
            delay(RETRY_DELAY_MS * (index + 1))
        }
        null
    }

    fun pageBlocking(
        url: String,
        referer: String? = null,
        timeoutMs: Long = PAGE_TIMEOUT_MS,
        client: OkHttpClient = NetworkClient.client
    ): String? = (attemptBlocking(url, referer, timeoutMs, client) as? Attempt.Body)?.text

    private suspend fun attempt(
        url: String,
        referer: String?,
        timeoutMs: Long,
        client: OkHttpClient
    ): Attempt {
        val source = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return Attempt.Rejected
        val noRedirectClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        var current = source

        repeat(MAX_REDIRECTS + 1) { hop ->
            val call = newCall(current.toString(), referer, timeoutMs, noRedirectClient)
                ?: return Attempt.Rejected
            when (val response = awaitResponse(
                call,
                fallback = { Attempt.NetworkError },
                transform = ::readResponse
            )) {
                is Attempt.Redirect -> {
                    if (hop >= MAX_REDIRECTS ||
                        !SiteUrlPolicy.isSameSiteHttps(response.target.toString(), source.toString())
                    ) {
                        return Attempt.Rejected
                    }
                    current = response.target
                }
                else -> return response
            }
        }
        return Attempt.Rejected
    }

    internal suspend fun <T> awaitResponse(
        call: Call,
        fallback: () -> T,
        transform: (Response) -> T
    ): T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        val callback = object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resume(fallback())
            }

            override fun onResponse(call: Call, response: Response) {
                val value = try {
                    response.use(transform)
                } catch (_: Exception) {
                    fallback()
                }
                if (continuation.isActive) continuation.resume(value)
            }
        }
        try {
            call.enqueue(callback)
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resume(fallback())
        }
    }

    private fun newCall(
        url: String,
        referer: String?,
        timeoutMs: Long,
        client: OkHttpClient
    ): Call? {
        val parsed = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        val builder = Request.Builder().url(parsed).get()
        val parsedReferer = referer?.toHttpUrlOrNull()
        if (parsedReferer?.isHttps == true) {
            builder.header("Referer", parsedReferer.toString())
            builder.header("Origin", "${parsedReferer.scheme}://${parsedReferer.host}")
        }
        return client.newCall(builder.build()).also {
            it.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun attemptBlocking(
        url: String,
        referer: String?,
        timeoutMs: Long,
        client: OkHttpClient
    ): Attempt {
        val source = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return Attempt.Rejected
        val noRedirectClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        var current = source

        repeat(MAX_REDIRECTS + 1) { hop ->
            val call = newCall(current.toString(), referer, timeoutMs, noRedirectClient)
                ?: return Attempt.Rejected
            val response = try {
                call.execute().use(::readResponse)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return Attempt.NetworkError
            }
            when (response) {
                is Attempt.Redirect -> {
                    if (hop >= MAX_REDIRECTS ||
                        !SiteUrlPolicy.isSameSiteHttps(response.target.toString(), source.toString())
                    ) {
                        return Attempt.Rejected
                    }
                    current = response.target
                }
                else -> return response
            }
        }
        return Attempt.Rejected
    }

    private fun readResponse(response: Response): Attempt {
        if (response.code in REDIRECT_CODES) {
            val location = response.header("Location") ?: return Attempt.HttpError(response.code)
            val target = response.request.url.resolve(location) ?: return Attempt.Rejected
            return Attempt.Redirect(target)
        }
        if (!response.isSuccessful) return Attempt.HttpError(response.code)
        val body = response.body ?: return Attempt.NetworkError
        if (body.contentLength() > MAX_BODY_CHARS) return Attempt.Rejected
        val source = body.source()
        source.request(MAX_BODY_CHARS.toLong() + 1)
        if (source.buffer.size > MAX_BODY_CHARS) return Attempt.Rejected
        return Attempt.Body(source.readString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8))
    }

    fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
