package com.calculator.app.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Delad OkHttpClient med reklamblockering + privacy-headers.
 * Används av alla extractors.
 */
object NetworkClient {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(AdBlockInterceptor())
            .addInterceptor(PrivacyInterceptor())
            .addNetworkInterceptor { chain ->
                val url = chain.request().url
                // Network interceptors also see redirect hops.
                if (!url.isHttps || AdBlockHosts.isBlocked(url.host)) {
                    throw java.io.IOException("Unsafe or blocked request")
                }
                chain.proceed(chain.request())
            }
            .build()
    }
}
