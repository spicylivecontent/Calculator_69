package com.calculator.app.network

import java.util.Locale

/**
 * Den delade listan över reklam-, popunder- och trackingdomäner.
 *
 * Används både av [AdBlockInterceptor] (all hämtning via OkHttp) och av WebView-
 * spelaren, så att samma blockering gäller oavsett hur innehållet laddas.
 */
object AdBlockHosts {

    /** Vanliga annons- och trackingnätverk. */
    val trackers: Set<String> = setOf(
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
        "yandex.ru/metrika",
        "mc.yandex.ru",
        "statcounter.com",
        "histats.com",
        "livejasmin.com",
        "bongacams.com",
        "stripchat.com",
        "chaturbate.com",
        "camsoda.com",
        "flirt4free.com",
        "imlive.com",
        "streamate.com",
        "xlovecam.com",
        "sexad.net",
        "sextracker.com",
        "ero-advertising.com",
        "adsupply.com",
        "adnium.com",
        "tsyndicate.com",
        "onclickads.net",
        "onclckds.com",
        "realsrv.com",
        "realsrv.co",
        "adtng.com",
        "trafmag.com",
        "push-notifications.com",
        "onesignal.com",
        "pushengage.com",
        "notix.io",
        "sendpulse.com",
        "pushnami.com",
        "histats.co",
        "adbrite.com",
        "adcash.com",
        "adspyglass.com",
        "adexchangegate.com",
        "clickaine.com",
        "evadav.com",
        "gothamads.com",
        "zangio.com",
        "admaven.com",
        "plugrush.com",
        "trafficstars.com",
        "trafficjunky.net",
        "exosrv.com",
        "exoclick.net",
        "juicyads.net",
        "adspyglass.net",
        "vidoomy.com",
        "loopme.me",
        "smartclip.net",
        "innovid.com",
        "spotxchange.com",
        "teads.tv",
        "smartadserver.net",
        "adtelligent.com",
        "clickagy.com",
        "privacytools.io",
        "clicki.io"
    )

    /** Popunder- och omdirigeringsnätverk som är extra vanliga på tube-sajter. */
    val popunder: Set<String> = setOf(
        "juicyads.com",
        "exoclick.com",
        "trafficjunky.com",
        "trafficstars.com",
        "adsterra.com",
        "clickadu.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerpops.com",
        "hilltopads.com",
        "adcash.com",
        "mgid.com",
        "revcontent.com",
        "adspyglass.com",
        "onclickads.net",
        "onclckds.com",
        "realsrv.com",
        "realsrv.co",
        "adtng.com",
        "tsyndicate.com",
        "tsyndicate.net",
        "adnium.com",
        "adbuddiz.com",
        "zangio.com",
        "admaven.com",
        "clickadu.net",
        "hilltopads.net",
        "popmyads.com",
        "popmonetizer.net",
        "galaksion.com",
        "monetag.com",
        "profitableratecpm.com",
        "highperformancecpm.com",
        "highperformanceformat.com",
        "displaycontentnetwork.com",
        "omg10.com",
        "dolohen.com",
        "ophoacit.com",
        "swanksign.com",
        "surescope.today",
        "cleverwebserver.com",
        "e-v-e-r-y-o-n-e.com",
        "pushsrv.com",
        "lanthanum.co",
        "despisenvy.com",
        "mfcapp.mobi",
        "livejasmin.net",
        "bngpt.com",
        "bngprm.com",
        "tsyndicate.site"
    )

    /** Appbutiker och appar som sidor försöker skicka vidare till. */
    val externalSchemes: Set<String> = setOf(
        "intent", "market", "play", "app", "tg", "whatsapp", "sms", "tel", "mailto",
        "bitcoin", "ethereum", "viber", "line", "snapchat", "fb", "instagram"
    )

    fun isBlocked(host: String): Boolean {
        val normalized = host.lowercase(Locale.ROOT).removePrefix("www.")
        return trackers.any { matches(normalized, it) } || popunder.any { matches(normalized, it) }
    }

    /** True om URL:en ser ut som en annons-/popunderresurs (även på en tillåten host). */
    fun looksLikeAdPath(pathAndQuery: String): Boolean {
        val value = pathAndQuery.lowercase(Locale.ROOT)
        return AD_PATH_KEYWORDS.any { value.contains(it) }
    }

    private fun matches(host: String, blocked: String): Boolean {
        if (blocked.contains('/')) {
            // Poster med sökväg (t.ex. yandex.ru/metrika) kan inte matchas på host ensam.
            return false
        }
        return host == blocked || host.endsWith(".$blocked")
    }

    private val AD_PATH_KEYWORDS = listOf(
        "/popunder", "/pop-under", "popunder.", "pop.js", "ppunder", "/popup", "popup.js",
        "/ads.js", "ads.js?", "/ads.", "/ads/", "/ads?", "/ad.", "/ad/", "/ad?", "/adframe", "/ad-frame", "/advert", "/banner/", "banner.php",
        "/vast", "/vpaid", "/preroll", "preroll.", "/pre-roll", "/promo/", "/track.php",
        "/click.php", "/click?", "exoclick", "trafficjunky", "juicyads", "exosrv",
        "/push.js", "push-notification", "/sw.js?ad", "/redirect?", "/go.php?", "/out.php",
        "syndication.php", "/adserver", "/ads?", "utm_source=ad", "/sponsor/"
    )
}
