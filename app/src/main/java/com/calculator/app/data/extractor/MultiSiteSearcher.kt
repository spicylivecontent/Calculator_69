package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Söker parallellt på flera sajter och blandar resultaten.
 */
class MultiSiteSearcher {

    private val extractors: List<SiteExtractor> = listOf(
        EpornerExtractor(),
        SpankBangExtractor(),
        HQpornerExtractor()
    )

    suspend fun search(query: String): List<SearchResult> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()

        // Kör alla extractors parallellt
        val deferred = extractors.map { extractor ->
            async {
                try {
                    extractor.search(query)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        // Samla och blanda resultaten (round-robin så en sajt inte dominerar)
        val allResults = deferred.map { it.await() }

        val mixed = mutableListOf<SearchResult>()
        val maxSize = allResults.maxOfOrNull { it.size } ?: 0

        for (i in 0 until maxSize) {
            for (list in allResults) {
                if (i < list.size) {
                    mixed.add(list[i])
                }
            }
        }

        mixed.distinctBy { it.id }
    }

    fun getSupportedSites(): List<String> = extractors.map { it.siteName }
}
