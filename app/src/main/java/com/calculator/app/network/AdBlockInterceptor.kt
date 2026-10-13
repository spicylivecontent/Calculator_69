package com.calculator.app.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Locale

/**
 * Blockerar kända reklam- och trackingdomäner.
 * Om en request går till en blockerad host kastas ett IOException
 * så att den aldrig når nätverket.
 */
class AdBlockInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase(Locale.ROOT)

        // Blockera kända annons-/trackingdomäner och typiska annonsvägar.
        if (AdBlockHosts.isBlocked(host)) {
            throw IOException("Blocked by AdBlockInterceptor: $host")
        }

        return chain.proceed(request)
    }
}
