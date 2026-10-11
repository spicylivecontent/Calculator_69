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
            .build()
    }
}
