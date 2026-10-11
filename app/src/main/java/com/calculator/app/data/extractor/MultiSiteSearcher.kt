package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Söker parallellt på alla stödda sajter och blandar resultaten.
 */
class MultiSiteSearcher {

    private val extractors: List<SiteExtractor> = listOf(
        EpornerExtractor(),
        SpankBangExtractor(),
        HQpornerExtractor(),
        XVideosExtractor(),
        XnxxExtractor(),
        XHamsterExtractor(),
        PornhubExtractor(),
        BeegExtractor(),
        YouPornExtractor(),
        PornTrexExtractor()
    )
    private val verificationSemaphore = Semaphore(VERIFICATION_CONCURRENCY)

    suspend fun search(query: String): List<SearchResult> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()

        val deferred = extractors.map { extractor ->
            async {
                try {
                    extractor.search(query)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

        val allResults = deferred.map { it.await() }
        val mixed = mutableListOf<SearchResult>()
        val maxSize = allResults.maxOfOrNull { it.size } ?: 0

        for (i in 0 until maxSize) {
            for (list in allResults) {
                if (i < list.size) mixed.add(list[i])
            }
        }

        mixed.distinctBy { it.id }
    }

    /** Returns only results whose direct stream responds as playable media. */
    suspend fun verifyPlayable(candidates: List<SearchResult>): List<SearchResult> = coroutineScope {
        candidates
            .distinctBy { it.id }
            .take(MAX_CANDIDATES_TO_VERIFY)
            .map { candidate ->
                async {
                    verificationSemaphore.withPermit {
                        try {
                            val streamUrl = StreamResolver.resolvePlayable(
                                detailUrl = candidate.detailUrl,
                                sourceSite = candidate.sourceSite
                            )
                            streamUrl?.let { candidate.copy(streamUrl = it) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    fun getSupportedSites(): List<String> = extractors.map { it.siteName }

    private companion object {
        const val VERIFICATION_CONCURRENCY = 6
        const val MAX_CANDIDATES_TO_VERIFY = 24
    }
}
