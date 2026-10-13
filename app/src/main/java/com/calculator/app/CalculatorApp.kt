package com.calculator.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Applikationsklass som ställer in bildhanteringen en gång för hela appen.
 *
 * Tumnaglar kan bli många (60+ per sökning), så minnes- och diskcachen har fasta
 * gränser i stället för att växa fritt – det håller nere både minnesanvändning och
 * batteriförbrukning när listor scrollas.
 */
class CalculatorApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Max 20 % av appens tillgängliga minne, minst 8 MB.
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .minSizeBytes(8L * 1024 * 1024)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbnails"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            // Tumnaglarna ändras i praktiken aldrig – cacha dem även om servern säger annat.
            .respectCacheHeaders(false)
            .build()
}
