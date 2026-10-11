package com.calculator.app.data.extractor

import com.calculator.app.data.model.Playability
import com.calculator.app.data.model.SearchQuery
import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/** Information om en sajt som används i gränssnittet. */
data class SiteInfo(val id: String, val label: String)

/** Hur det gick för en sajt i en sökning. */
data class SiteSearchReport(
    val siteId: String,
    val label: String,
    val count: Int,
    val ok: Boolean,
    val message: String? = null
)

data class SearchOutcome(
    val results: List<SearchResult>,
    val reports: List<SiteSearchReport>
)

/**
 * Söker parallellt på alla valda sajter med flera frågevarianter.
 *
 * Varje (sajt, fråga, sida) hämtas en gång och alla träffar slås ihop med
 * dubblettrensning, vilket gör att användaren får med allt som matchar
 * även när sajterna tolkar flerordssökningar som "alla ord måste finnas".
 */
class MultiSiteSearcher(
    private val extractors: List<SiteExtractor> = defaultExtractors()
) {

    val siteInfos: List<SiteInfo> = extractors.map { SiteInfo(it.siteName, it.label) }

    suspend fun search(
        queries: List<SearchQuery>,
        siteIds: Set<String> = emptySet(),
        startPage: Int = 1,
        pageCount: Int = 1,
        maxPerSite: Int = MAX_PER_SITE,
        onBatch: suspend (List<SearchResult>) -> Unit = {}
    ): SearchOutcome = coroutineScope {
        if (queries.isEmpty()) return@coroutineScope SearchOutcome(emptyList(), emptyList())

        val selected = if (siteIds.isEmpty()) extractors else extractors.filter { it.siteName in siteIds }

        val perSite = selected.map { extractor ->
            async(Dispatchers.IO) {
                val collected = LinkedHashMap<String, SearchResult>()
                var failure: String? = null
                var attempted = false

                val firstPage = startPage.coerceAtLeast(1)
                for (page in firstPage until (firstPage + pageCount.coerceAtLeast(1))) {
                    if (page > 1 && !extractor.supportsPagination) break
                    for (query in queries) {
                        if (collected.size >= maxPerSite) break
                        attempted = true
                        try {
                            val found = extractor.search(query.text, page)
                            if (found.isEmpty()) continue
                            val tagged = found.map { result ->
                                result.copy(matchedTerms = (result.matchedTerms + query.terms).distinct())
                            }
                            val fresh = tagged.filter { collected.putIfAbsent(it.id, it) == null }
                            if (fresh.isNotEmpty()) onBatch(fresh)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            failure = error.message ?: error.javaClass.simpleName
                        }
                    }
                }

                SiteResult(
                    info = SiteInfo(extractor.siteName, extractor.label),
                    results = collected.values.toList(),
                    failure = failure,
                    attempted = attempted
                )
            }
        }.awaitAll()

        val reports = perSite.map { site ->
            SiteSearchReport(
                siteId = site.info.id,
                label = site.info.label,
                count = site.results.size,
                ok = site.results.isNotEmpty() || site.failure == null,
                message = site.failure.takeIf { site.results.isEmpty() }
            )
        }

        SearchOutcome(results = perSite.flatMap { it.results }, reports = reports)
    }

    /**
     * Kontrollerar att träffarna går att spela upp och rapporterar varje resultat
     * löpande via [onVerified] så att listan kan uppdateras medan kontrollen pågår.
     */
    suspend fun verifyPlayable(
        candidates: List<SearchResult>,
        concurrency: Int = VERIFICATION_CONCURRENCY,
        onVerified: suspend (SearchResult) -> Unit = {}
    ): Int = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope 0
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))
        val verified = AtomicInteger()

        candidates.map { candidate ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val stream = try {
                        StreamResolver.resolvePlayable(candidate.detailUrl, candidate.sourceSite)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }

                    val updated = if (stream != null) {
                        verified.incrementAndGet()
                        candidate.copy(
                            streamUrl = stream.url,
                            playability = Playability.PLAYABLE,
                            qualityLabel = stream.qualityLabel ?: candidate.qualityLabel
                        )
                    } else {
                        candidate.copy(playability = Playability.UNPLAYABLE)
                    }
                    onVerified(updated)
                    updated
                }
            }
        }.awaitAll()

        verified.get()
    }

    /** Tömmer den korta streamcachen, t.ex. när en stream slutat fungera. */
    fun invalidateStream(detailUrl: String? = null) = StreamResolver.invalidate(detailUrl)

    private data class SiteResult(
        val info: SiteInfo,
        val results: List<SearchResult>,
        val failure: String?,
        val attempted: Boolean
    )

    companion object {
        const val MAX_PER_SITE = 36
        const val VERIFICATION_CONCURRENCY = 8

        private fun defaultExtractors(): List<SiteExtractor> = listOf(
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
    }
}
