package com.calculator.app.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Blockerar kända reklam- och trackingdomäner.
 * Om en request går till en blockerad host kastas ett IOException
 * så att den aldrig når nätverket.
 */
class AdBlockInterceptor : Interceptor {

    private val blockedHosts = setOf(
        // Vanliga ad/tracking-nätverk
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "google-analytics.com",
        "googletagmanager.com",
        "facebook.com",
        "facebook.net",
        "fbcdn.net",
        "scorecardresearch.com",
        "quantserve.com",
        "outbrain.com",
        "taboola.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "ads.yahoo.com",
        "advertising.com",
        "adnxs.com",
        "adform.net",
        "moatads.com",
        "amazon-adsystem.com",
        "adsystem.amazon",
        "criteo.com",
        "criteo.net",
        "pubmatic.com",
        "openx.net",
        "rubiconproject.com",
        "casalemedia.com",
        "adsrvr.org",
        "bidswitch.net",
        "smartadserver.com",
        "contextweb.com",
        "adsafeprotected.com",
        "exelator.com",
        "bluekai.com",
        "krxd.net",
        "tapad.com",
        "demdex.net",
        "omtrdc.net",
        "2mdn.net",
        "adobedtm.com",
        "hotjar.com",
        "mouseflow.com",
        "fullstory.com",
        "newrelic.com",
        "nr-data.net",
        "segment.io",
        "segment.com",
        "mixpanel.com",
        "amplitude.com",
        "sentry.io",
        // Popunder / redirect-nätverk som ofta syns på tubesajter
        "juicyads.com",
        "exoclick.com",
        "trafficjunky.com",
        "adsterra.com",
        "clickadu.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "hilltopads.com",
        "adcash.com",
        "mgid.com",
        "revcontent.com"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase()

        // Blockera om hosten innehåller någon blockerad domän
        if (blockedHosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }) {
            throw IOException("Blocked by AdBlockInterceptor: $host")
        }

        return chain.proceed(request)
    }
}
