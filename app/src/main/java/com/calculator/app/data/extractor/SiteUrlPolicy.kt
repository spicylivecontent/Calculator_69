package com.calculator.app.data.extractor

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** HTTPS URL checks that keep a request on its declared host or a trusted subdomain. */
object SiteUrlPolicy {

    fun isSameSiteHttps(url: String?, sourceBaseUrl: String?): Boolean {
        val target = url?.toHttpUrlOrNull() ?: return false
        val base = sourceBaseUrl?.toHttpUrlOrNull() ?: return false
        if (!target.isHttps || !base.isHttps) return false
        if (target.port != base.port) return false
        if (target.encodedUsername.isNotEmpty() || target.encodedPassword.isNotEmpty()) return false
        if (base.encodedUsername.isNotEmpty() || base.encodedPassword.isNotEmpty()) return false

        return target.host == base.host ||
            target.host.endsWith(".${base.host}") ||
            target.host.removePrefix("www.") == base.host.removePrefix("www.")
    }
}
