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

data class SiteInfo(val id: String, val label: String, val baseUrl: String? = null)

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

class MultiSiteSearcher(
    private val extractors: List<SiteExtractor> = defaultExtractors(),
    private val network: NetworkCondition = NetworkCondition.Default,
    rules: ScrapeRules = ScrapeRules.Empty
) {

    private val activeExtractors: List<SiteExtractor> = buildList {
        extractors.forEach { builtIn ->
            val rule = rules.forSite(builtIn.siteName)
            add(if (rule != null) RuleSiteExtractor(rule) else builtIn)
        }
        addAll(rules.newSites(extractors.map { it.siteName }).map { RuleSiteExtractor(it) })
    }

    val siteInfos: List<SiteInfo> = activeExtractors.map { SiteInfo(it.siteName, it.label, it.baseUrl) }

    private fun timeoutMessage(millis: Long): String = "Tog för lång tid (>${millis / 1000} s) – hoppades över"

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
        val siteSemaphore = Semaphore(network.siteParallelism.coerceAtLeast(1))

        val perSite = selected.map { extractor ->
            async(Dispatchers.IO) {
              siteSemaphore.withPermit {
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
                            val found = searchOne(extractor, query.text, page)
                            if (found == null) {
                                failure = failure ?: timeoutMessage(network.siteTimeoutMs)
                                continue
                            }
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

    suspend fun enrichMetadata(
        candidates: List<SearchResult>,
        concurrency: Int = network.enrichmentParallelism.coerceAtLeast(1),
        onEnriched: suspend (SearchResult) -> Unit = {}
    ): Int = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope 0
        val semaphore = Semaphore(concurrency)
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
            PornTrexExtractor(),
            LuxureTvExtractor()
        )
    }
}
