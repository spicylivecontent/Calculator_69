package com.calculator.app.data.extractor

import com.calculator.app.data.model.DurationParser
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
import kotlinx.coroutines.withTimeoutOrNull
import com.calculator.app.network.NetworkCondition
import java.util.concurrent.atomic.AtomicInteger

/** Information om en sajt som används i gränssnittet. */
data class SiteInfo(val id: String, val label: String, val baseUrl: String? = null)

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
    private val extractors: List<SiteExtractor> = defaultExtractors(),
    /** Nätverksläget styr hur många anrop som körs samtidigt och hur länge en sajt får ta. */
    private val network: NetworkCondition = NetworkCondition.Default,
    /** Regler från nätet. Tomma regler betyder att allt är inbyggt. */
    rules: ScrapeRules = ScrapeRules.Empty
) {

    /**
     * Sajterna som faktiskt används: en hämtad regel ersätter den inbyggda regeln för
     * samma sajt, och regler för nya sajter läggs till i slutet av listan.
     */
    private val activeExtractors: List<SiteExtractor> = buildList {
        extractors.forEach { builtIn ->
            val rule = rules.forSite(builtIn.siteName)
            add(if (rule != null) RuleSiteExtractor(rule) else builtIn)
        }
        addAll(rules.newSites(extractors.map { it.siteName }).map { RuleSiteExtractor(it) })
    }

    val siteInfos: List<SiteInfo> = activeExtractors.map { SiteInfo(it.siteName, it.label, it.baseUrl) }

    /** Sant när en sajt tog för lång tid och hoppades över. */
    private fun timeoutMessage(millis: Long): String = "Tog för lång tid (>${millis / 1000} s) – hoppades över"

    /**
     * En sajt får aldrig stoppa resten av sökningen: varje hämtning har en egen
     * tidsgräns och ett fel på en sajt rapporteras i stället för att kasta vidare.
     */
    private suspend fun searchOne(extractor: SiteExtractor, query: String, page: Int): List<SearchResult>? =
        withTimeoutOrNull(network.siteTimeoutMs) {
            extractor.search(query, page)
        }

    suspend fun search(
        queries: List<SearchQuery>,
        siteIds: Set<String> = emptySet(),
        startPage: Int = 1,
        pageCount: Int = 1,
        maxPerSite: Int = MAX_PER_SITE,
        onBatch: suspend (List<SearchResult>) -> Unit = {}
    ): SearchOutcome = coroutineScope {
        if (queries.isEmpty()) return@coroutineScope SearchOutcome(emptyList(), emptyList())

        val selected = if (siteIds.isEmpty()) activeExtractors
        else activeExtractors.filter { it.siteName in siteIds }

        // Avoid spending one timeout per site when Android already reports no internet.
        if (network.isOffline) {
            val reports = selected.map { extractor ->
                SiteSearchReport(
                    siteId = extractor.siteName,
                    label = extractor.label,
                    count = 0,
                    ok = false,
                    message = "Ingen nätverksanslutning"
                )
            }
            return@coroutineScope SearchOutcome(results = emptyList(), reports = reports)
        }

        val siteSemaphore = Semaphore(network.siteParallelism.coerceAtLeast(1))

        val perSite = selected.map { extractor ->
            async(Dispatchers.IO) {
              siteSemaphore.withPermit {
                val collected = LinkedHashMap<String, SearchResult>()
                var failure: String? = null
                var attempted = false
                // Give every query variant a share of the site's budget. Previously the first
                // broad query could return 36 items and stop the loop before any variants ran.
                val safeBudget = maxPerSite.coerceAtLeast(1)
                val baseQueryBudget = safeBudget / queries.size
                val remainderQueryBudget = safeBudget % queries.size
                val acceptedByQuery = HashMap<String, Int>()

                val firstPage = startPage.coerceAtLeast(1)
                for (page in firstPage until (firstPage + pageCount.coerceAtLeast(1))) {
                    if (page > 1 && !extractor.supportsPagination) break
                    for ((queryIndex, query) in queries.withIndex()) {
                        if (collected.size >= maxPerSite) break
                        val queryBudget = baseQueryBudget + if (queryIndex < remainderQueryBudget) 1 else 0
                        if (queryBudget <= 0) continue
                        // Keep a separate quota for each page so pagination can contribute new results.
                        val queryKey = "${page}:${query.text.lowercase(java.util.Locale.ROOT)}"
                        val alreadyAccepted = acceptedByQuery[queryKey] ?: 0
                        if (alreadyAccepted >= queryBudget) continue
                        attempted = true
                        try {
                            val found = searchOne(extractor, query.text, page)
                            if (found == null) {
                                // Tidsgränsen slog till: hoppa över frågan men fortsätt med sajtens övriga frågor.
                                failure = failure ?: timeoutMessage(network.siteTimeoutMs)
                                continue
                            }
                            if (found.isEmpty()) continue
                            val tagged = found.map { result ->
                                result.copy(matchedTerms = (result.matchedTerms + query.terms).distinct())
                            }
                            val remainingForQuery = queryBudget - alreadyAccepted
                            val fresh = tagged.distinctBy { it.id }
                                .filterNot { it.id in collected }
                                .take(remainingForQuery)
                            fresh.forEach { collected[it.id] = it }
                            if (fresh.isNotEmpty()) {
                                acceptedByQuery[queryKey] = alreadyAccepted + fresh.size
                                onBatch(fresh)
                            }
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
            }
        }.awaitAll()

        val reports = perSite.map { site ->
            SiteSearchReport(
                siteId = site.info.id,
                label = site.info.label,
                count = site.results.size,
                // Partial results are useful, but a failed query variant must remain visible.
                ok = site.failure == null,
                message = site.failure
            )
        }

        SearchOutcome(results = perSite.flatMap { it.results }, reports = reports)
    }

    /**
     * Läser in all information om träffarna (rubrik, taggar, kategorier, medverkande,
     * kvalitet, längd och beskrivning) från deras detaljsidor och rapporterar varje
     * träff löpande via [onEnriched] så att listan fylls på medan hämtningen pågår.
     *
     * Sidor som inte svarar eller där innehållet tagits bort markeras som ospelbara.
     */
    suspend fun enrichMetadata(
        candidates: List<SearchResult>,
        concurrency: Int = VERIFICATION_CONCURRENCY,
        onEnriched: suspend (SearchResult) -> Unit = {}
    ): Int = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope 0
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))
        val enriched = AtomicInteger()

        candidates.map { candidate ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val metadata = try {
                        MetadataExtractor.fetch(candidate.detailUrl, candidate.sourceSite)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }

                    val updated = when {
                        metadata == null -> candidate.copy(playability = Playability.UNPLAYABLE)

                        metadata.pageMissing -> candidate.copy(
                            playability = Playability.UNPLAYABLE,
                            tags = metadata.tags,
                            performers = metadata.performers,
                            categories = metadata.categories,
                            description = metadata.description,
                            comments = metadata.comments,
                            tagCount = metadata.keywords.size
                        )

                        else -> {
                            if (metadata.hasRichData) enriched.incrementAndGet()
                            candidate.copy(
                                title = metadata.title ?: candidate.title,
                                tags = metadata.keywords,
                                performers = metadata.performers,
                                categories = metadata.categories,
                                description = metadata.description,
                                comments = metadata.comments,
                                tagCount = metadata.keywords.size,
                                qualityLabel = metadata.qualityLabel ?: candidate.qualityLabel,
                                durationSeconds = metadata.durationSeconds ?: candidate.durationSeconds,
                                duration = candidate.duration
                                    ?: metadata.durationSeconds?.let { DurationParser.format(it) },
                                playability = Playability.PLAYABLE
                            )
                        }
                    }
                    onEnriched(updated)
                    updated
                }
            }
        }.awaitAll()

        enriched.get()
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
        const val MAX_PER_SITE = 72
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
            PornTrexExtractor(),
            LuxureTvExtractor()
        )
    }
}
