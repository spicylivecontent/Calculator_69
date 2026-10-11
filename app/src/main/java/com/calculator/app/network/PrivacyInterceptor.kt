package com.calculator.app.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale

/**
 * Lägger till privacy-vänliga headers och tar bort onödiga tracking-parametrar
 * från URL:er innan requesten skickas.
 */
class PrivacyInterceptor : Interceptor {

    // Query-parametrar som ofta används för tracking
    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "msclkid", "dclid", "twclid",
        "mc_eid", "mc_cid", "_ga", "_gl", "yclid",
        "ref", "referrer", "source", "campaign_id",
        "ad_id", "adid", "affiliate", "aff_id", "partner"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val originalUrl = original.url

        // Keep media query strings intact; some signed stream URLs use parameter names
        // that also appear in the tracking list (for example "source" or "ref").
        val isMediaProbe = original.header("Range") != null
        val cleanUrlBuilder = originalUrl.newBuilder()
        if (!isMediaProbe) {
            for (name in originalUrl.queryParameterNames) {
                if (name.lowercase(Locale.ROOT) in trackingParams) {
                    cleanUrlBuilder.removeAllQueryParameters(name)
                }
            }
        }

        val cleanRequest = original.newBuilder()
            .url(cleanUrlBuilder.build())
            // Privacy-friendly headers; allow media MIME types on the range probe.
            .header("User-Agent", USER_AGENT)
            .header(
                "Accept",
                if (isMediaProbe) "*/*" else "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("DNT", "1")                          // Do Not Track
            .header("Sec-GPC", "1")                      // Global Privacy Control
            .header("Cache-Control", "no-cache")
            .removeHeader("X-Requested-With")            // Tar bort Android WebView-signatur om den finns
            .build()

        return chain.proceed(cleanRequest)
    }

    companion object {
        // Generisk mobil User-Agent (inte unik för appen)
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
