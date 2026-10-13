package com.calculator.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.calculator.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Applikationsklass som ställer in bildhanteringen en gång för hela appen.
 *
 * Tumnaglar kan bli många (60+ per sökning), så minnes- och diskcachen har fasta
 * gränser i stället för att växa fritt – det håller nere både minnesanvändning och
 * batteriförbrukning när listor scrollas.
 *
 * Vid start flyttas också innehållet från en tidigare okrypterad databas till den
 * krypterade, en gång, i bakgrunden.
 */
class CalculatorApp : Application(), ImageLoaderFactory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Engångsflytt av gammalt innehåll till den krypterade databasen. Misslyckas den
        // lämnas originalfilen kvar och appen fortsätter som vanligt.
        if (getDatabasePath("calculator_vault.db").exists()) applicationScope.launch {
            runCatching { AppDatabase.migrateLegacyIfNeeded(applicationContext) }
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Max 20 % av appens tillgängliga minne (Coil räknar upp det själv).
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbnails"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .okHttpClient(com.calculator.app.network.NetworkClient.client)
            .crossfade(true)
            // Tumnaglarna ändras i praktiken aldrig – cacha dem även om servern säger annat.
            .respectCacheHeaders(false)
            .build()
}
