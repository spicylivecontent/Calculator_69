package com.calculator.app.ui.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calculator.app.ai.AgeSafety
import com.calculator.app.ai.SearchPlanner
import com.calculator.app.data.AppPreferences
import com.calculator.app.data.PlaybackStore
import com.calculator.app.data.RecentPlayback
import com.calculator.app.data.Repository
import com.calculator.app.data.entity.SearchPresetEntity
import com.calculator.app.data.entity.VideoEntity
import com.calculator.app.data.extractor.MultiSiteSearcher
import com.calculator.app.data.extractor.ResolvedStream
import com.calculator.app.data.extractor.SiteInfo
import com.calculator.app.data.extractor.SiteSearchReport
import com.calculator.app.data.extractor.StreamResolver
import com.calculator.app.data.extractor.StreamSelection
import com.calculator.app.data.model.LengthFilter
import com.calculator.app.data.model.Playability
import com.calculator.app.data.model.ResultRanker
import com.calculator.app.data.model.SearchResult
import com.calculator.app.data.model.SortOrder
import com.calculator.app.data.taxonomy.SearchTaxonomy
import com.calculator.app.data.taxonomy.Subcategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Det som spelas just nu, inklusive kö för nästa/föregående video. */
data class NowPlaying(
    val result: SearchResult,
    val streamUrl: String,
    val startPositionMs: Long = 0L,
    val queueIndex: Int = 0,
    val queueSize: Int = 1
) {
    val hasNext: Boolean get() = queueIndex < queueSize - 1
    val hasPrevious: Boolean get() = queueIndex > 0
}

/**
 * Håller ihop sökning, verifiering av spelbara träffar och uppspelning.
 *
 * Sökningen sker i två steg: först samlas alla träffar in (de visas direkt med
 * status "kontrollerar"), därefter kontrolleras strömmarna i tur och ordning.
 * När kontrollen är klar finns bara videor som går att titta på kvar.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val searcher = MultiSiteSearcher()
    private val repository = Repository(application)
    private val planner = SearchPlanner()
    val preferences = AppPreferences(application)
    private val playbackStore = PlaybackStore(application)

    private val candidates = LinkedHashMap<String, SearchResult>()
    private var searchJob: Job? = null
    private var activePlan: SearchPlanner.Plan? = null
    private var blockedWords: List<String> = emptyList()
    private var page = 1

    private var queue: List<SearchResult> = emptyList()
    private var queueIndex = 0

    // ---------- Sökfält ----------
    var query by mutableStateOf("")
        private set

    var negativeKeywords by mutableStateOf("")
        private set

    var explanation by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var noticeMessage by mutableStateOf<String?>(null)
        private set

    // ---------- Filter ----------
    var selectedSubcategoryIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var selectedSiteIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var sortOrder by mutableStateOf(SortOrder.RELEVANCE)
        private set

    var lengthFilter by mutableStateOf(LengthFilter.ALL)
        private set

    var onlyPlayable by mutableStateOf(preferences.onlyPlayable)
        private set

    val siteInfos: List<SiteInfo> = searcher.siteInfos
    val categories = SearchTaxonomy.categories

    val searchHistory: List<String> get() = preferences.searchHistory()

    // ---------- Resultat ----------
    var results by mutableStateOf<List<SearchResult>>(emptyList())
        private set

    var siteReports by mutableStateOf<List<SiteSearchReport>>(emptyList())
        private set

    var activeQueries by mutableStateOf<List<String>>(emptyList())
        private set

    var isSearching by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var verificationTotal by mutableStateOf(0)
        private set

    var verificationDone by mutableStateOf(0)
        private set

    var canLoadMore by mutableStateOf(false)
        private set

    /** Senaste spelbara träffar – används av "Fortsätt titta"/flödet på startsidan. */
    var lastFeed by mutableStateOf<List<SearchResult>>(emptyList())
        private set

    // ---------- Uppspelning ----------
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set

    var isResolvingStream by mutableStateOf(false)
        private set

    var resolvingTitle by mutableStateOf("")
        private set

    /**
     * Ökas varje gång uppspelningshistoriken ändras. Vyer som läser historiken
     * direkt (i stället för via flöden) kan räkna om sina listor på den här.
     */
    var playbackRevision by mutableStateOf(0)
        private set

    // ---------- Bakåtkompatibla vyer ----------
    val isLoading: Boolean get() = isSearching
    val isVerifyingResults: Boolean get() = verificationTotal > 0 && verificationDone < verificationTotal
    val supportedSites: List<String> get() = siteInfos.map { it.id }
    val aiExplanation: String? get() = explanation
    val playingUrl: String? get() = nowPlaying?.streamUrl
    val playingTitle: String get() = nowPlaying?.result?.title.orEmpty()
    val playingReferer: String get() = nowPlaying?.result?.detailUrl.orEmpty()
    val playingVideoId: String? get() = nowPlaying?.result?.id
    val verifiedCount: Int get() = results.count { it.isPlayable }

    val activeSubcategories: List<Subcategory>
        get() = SearchTaxonomy.allSubcategories.filter { it.id in selectedSubcategoryIds }

    // ---------------------------------------------------------------------
    // Sökfält och filter
    // ---------------------------------------------------------------------

    fun onQueryChange(newQuery: String) {
        query = newQuery
        explanation = null
        errorMessage = null
    }

    fun onNegativeKeywordsChange(value: String) {
        negativeKeywords = value
    }

    /**
     * Snabbval från startsidan: markerar de underkategorier som matchar valda
     * termer och startar sökningen direkt. Saknas en matchande underkategori
     * används termen som fritext i stället.
     */
    fun applyQuickPick(terms: List<String>) {
        val wanted = terms.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotBlank() }
        val matchingIds = SearchTaxonomy.allSubcategories
            .filter { subcategory -> wanted.any { term -> subcategory.terms.any { it.equals(term, ignoreCase = true) } } }
            .map { it.id }
            .toSet()
        selectedSubcategoryIds = matchingIds
        if (matchingIds.isEmpty()) {
            query = terms.firstOrNull().orEmpty()
        }
        explanation = null
        errorMessage = null
        search()
    }

    fun onSubcategoryToggle(subcategoryId: String) {
        selectedSubcategoryIds = if (subcategoryId in selectedSubcategoryIds) {
            selectedSubcategoryIds - subcategoryId
        } else {
            selectedSubcategoryIds + subcategoryId
        }
        explanation = null
        errorMessage = null
    }

    fun clearCategories() {
        selectedSubcategoryIds = emptySet()
    }

    fun toggleSite(siteId: String) {
        selectedSiteIds = if (siteId in selectedSiteIds) selectedSiteIds - siteId else selectedSiteIds + siteId
    }

    fun selectAllSites() {
        selectedSiteIds = emptySet()
    }

    fun onSortOrderChange(order: SortOrder) {
        sortOrder = order
        publishResults()
    }

    fun onLengthFilterChange(filter: LengthFilter) {
        lengthFilter = filter
        publishResults()
    }

    fun onOnlyPlayableChange(onlyPlayableNow: Boolean) {
        onlyPlayable = onlyPlayableNow
        preferences.onlyPlayable = onlyPlayableNow
        publishResults()
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        query = ""
        negativeKeywords = ""
        selectedSubcategoryIds = emptySet()
        activePlan = null
        candidates.clear()
        results = emptyList()
        siteReports = emptyList()
        activeQueries = emptyList()
        explanation = null
        errorMessage = null
        noticeMessage = null
        verificationTotal = 0
        verificationDone = 0
        canLoadMore = false
        page = 1
    }

    // ---------------------------------------------------------------------
    // Sökning
    // ---------------------------------------------------------------------

    fun search() {
        if (isSearching || isLoadingMore) return

        val plan = planner.plan(
            rawInput = query,
            categoryTerms = SearchTaxonomy.termsFor(selectedSubcategoryIds),
            extraNegatives = negativeKeywords.split(',', '\n', ';')
        )

        errorMessage = null
        noticeMessage = null
        explanation = plan.explanation

        if (plan.blockedReason != null) {
            errorMessage = plan.blockedReason
            return
        }
        if (plan.queries.isEmpty()) {
            errorMessage = plan.explanation
            return
        }

        activePlan = plan
        activeQueries = plan.queries.map { it.text }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            isSearching = true
            candidates.clear()
            results = emptyList()
            siteReports = emptyList()
            verificationTotal = 0
            verificationDone = 0
            canLoadMore = false
            page = 1
            blockedWords = loadBlockedWords(plan)
            if (query.isNotBlank()) preferences.addSearchHistory(query)

            try {
                val outcome = searcher.search(
                    queries = plan.queries,
                    siteIds = selectedSiteIds,
                    startPage = 1,
                    pageCount = 1,
                    onBatch = { batch -> applyBatch(batch) }
                )
                siteReports = outcome.reports
                canLoadMore = outcome.reports.any { it.count > 0 }
                isSearching = false
                verifyPending(plan, waveLimit = FULL_WAVE_LIMIT, budget = FULL_VERIFY_BUDGET)
                finalizeSearch()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = "Sökningen misslyckades: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                isSearching = false
                verificationTotal = verificationDone
                publishResults()
            }
        }
    }

    /** Hämtar nästa sida och verifierar de nya träffarna. */
    fun loadMore() {
        val plan = activePlan ?: return
        if (isSearching || isLoadingMore) return
        viewModelScope.launch {
            isLoadingMore = true
            errorMessage = null
            try {
                page += 1
                searcher.search(
                    queries = plan.queries,
                    siteIds = selectedSiteIds,
                    startPage = page,
                    pageCount = 1,
                    onBatch = { batch -> applyBatch(batch) }
                )
                verifyPending(plan, waveLimit = FULL_WAVE_LIMIT, budget = LOAD_MORE_BUDGET)
                finalizeSearch()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = "Kunde inte hämta fler träffar: ${error.message}"
            } finally {
                isLoadingMore = false
                verificationTotal = verificationDone
                publishResults()
            }
        }
    }

    private suspend fun applyBatch(batch: List<SearchResult>) = withContext(Dispatchers.Main) {
        var added = 0
        for (item in batch) {
            if (AgeSafety.containsBlockedTerm(item.title)) continue
            val title = item.title.lowercase(Locale.ROOT)
            if (blockedWords.any { title.contains(it) }) continue

            val existing = candidates[item.id]
            if (existing == null) {
                if (candidates.size >= MAX_CANDIDATES) continue
                candidates[item.id] = item.copy(playability = Playability.CHECKING)
                added++
            } else {
                candidates[item.id] = existing.copy(
                    matchedTerms = (existing.matchedTerms + item.matchedTerms).distinct(),
                    durationSeconds = existing.durationSeconds ?: item.durationSeconds,
                    duration = existing.duration ?: item.duration
                )
            }
        }
        if (added > 0) publishResults()
    }

    private suspend fun verifyPending(plan: SearchPlanner.Plan, waveLimit: Int, budget: Int): Int {
        var verified = 0
        var waves = 0
        var spent = 0

        while (waves < waveLimit && spent < budget) {
            val pending = ResultRanker.rank(
                candidates.values.filter { it.playability == Playability.CHECKING },
                plan.terms
            ).take(WAVE_SIZE)
            if (pending.isEmpty()) break

            spent += pending.size
            verificationTotal += pending.size
            verified += searcher.verifyPlayable(pending) { updated ->
                withContext(Dispatchers.Main) { applyVerified(updated) }
            }
            waves++
            if (verified >= TARGET_VERIFIED) break
        }
        return verified
    }

    private fun applyVerified(updated: SearchResult) {
        verificationDone++
        candidates[updated.id] = updated
        publishResults()
    }

    private fun finalizeSearch() {
        for (item in candidates.values.filter { it.playability == Playability.CHECKING }) {
            candidates[item.id] = item.copy(playability = Playability.UNVERIFIED)
        }
        publishResults()
        if (onlyPlayable && results.none { it.isPlayable } && candidates.isNotEmpty()) {
            noticeMessage = "Ingen av träffarna kunde verifieras som spelbar just nu. " +
                "Tryck på \"Ladda fler\" för fler träffar, eller stäng av \"Bara spelbara\" för att se okontrollerade träffar."
        }
    }

    private suspend fun loadBlockedWords(plan: SearchPlanner.Plan): List<String> {
        val fromRules = try {
            repository.getDislikeRules().first()
                .filter { it.type == "keyword" || it.type == "tag" }
                .map { it.value.lowercase(Locale.ROOT) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        return (plan.negatives + fromRules)
            .map { it.lowercase(Locale.ROOT).trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun publishResults() {
        val plan = activePlan
        val terms = plan?.terms?.takeIf { it.isNotEmpty() } ?: ResultRanker.tokenize(query)
        val ranked = ResultRanker.rank(candidates.values.toList(), terms)
        val filtered = ranked.filter { result ->
            lengthFilter.matches(result.durationSeconds) && when (result.playability) {
                // Verifierat spelbara träffar visas alltid.
                Playability.PLAYABLE -> true
                // Träffar som kontrolleras just nu visas medan kontrollen pågår.
                Playability.CHECKING -> true
                // Allt annat döljs när användaren bara vill se spelbara videor.
                Playability.UNPLAYABLE, Playability.UNVERIFIED -> !onlyPlayable
            }
        }
        results = ResultRanker.applySort(filtered, sortOrder)
        val playable = results.filter { it.isPlayable }.take(30)
        if (playable.isNotEmpty()) lastFeed = playable
    }

    // ---------------------------------------------------------------------
    // Uppspelning
    // ---------------------------------------------------------------------

    fun playVideo(result: SearchResult) = playResult(result, startPositionMs = -1L)

    fun playRelative(offset: Int) {
        val target = queue.getOrNull(queueIndex + offset) ?: return
        playResult(target, startPositionMs = -1L, keepQueue = true)
    }

    fun playRecent(entry: RecentPlayback) {
        playResult(entry.toSearchResult(), startPositionMs = entry.positionMs)
    }

    private fun playResult(result: SearchResult, startPositionMs: Long, keepQueue: Boolean = false) {
        viewModelScope.launch {
            isResolvingStream = true
            resolvingTitle = result.title
            errorMessage = null
            try {
                val stream = resolveStream(result)
                if (stream == null) {
                    StreamResolver.invalidate(result.detailUrl)
                    markVideoUnavailable(result.id)
                    errorMessage = "Videon är inte längre tillgänglig och har tagits bort från resultaten."
                    return@launch
                }

                val saved = when {
                    startPositionMs >= 0L -> startPositionMs
                    preferences.resumePlayback -> playbackStore.position(result.id)
                    else -> 0L
                }

                val playable = result.copy(
                    streamUrl = stream.url,
                    playability = Playability.PLAYABLE,
                    qualityLabel = stream.qualityLabel ?: result.qualityLabel
                )
                if (candidates.containsKey(playable.id)) {
                    candidates[playable.id] = playable
                    publishResults()
                } else {
                    candidates[playable.id] = playable
                }
                playbackStore.savePlayed(playable)
                playbackRevision++

                if (!keepQueue || queue.isEmpty()) {
                    queue = results.filter { it.isPlayable && !it.streamUrl.isNullOrBlank() }
                    if (queue.none { it.id == playable.id }) queue = listOf(playable)
                    queueIndex = queue.indexOfFirst { it.id == playable.id }.coerceAtLeast(0)
                } else {
                    queueIndex = queue.indexOfFirst { it.id == playable.id }.coerceAtLeast(0)
                }

                nowPlaying = NowPlaying(
                    result = playable,
                    streamUrl = stream.url,
                    startPositionMs = saved,
                    queueIndex = queueIndex,
                    queueSize = queue.size
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = "Videon kunde inte spelas (${error.message ?: result.sourceSite})."
            } finally {
                isResolvingStream = false
                resolvingTitle = ""
            }
        }
    }

    private suspend fun resolveStream(result: SearchResult, forceRefresh: Boolean = false): ResolvedStream? {
        if (!forceRefresh) {
            StreamResolver.cached(result.detailUrl)?.let { return it }
            val known = result.streamUrl
            if (!known.isNullOrBlank()) {
                return ResolvedStream(
                    url = known,
                    referer = result.detailUrl,
                    isHls = StreamSelection.isHls(known),
                    maxHeight = null,
                    bandwidth = null,
                    qualityLabel = result.qualityLabel
                )
            }
        }
        return StreamResolver.resolvePlayable(result.detailUrl, result.sourceSite, forceRefresh = forceRefresh)
    }

    /**
     * Spelaren rapporterar att strömmen inte gick att spela upp. Vi försöker först
     * hämta en helt ny stream-URL; går inte det tas träffen bort från listan.
     */
    fun onStreamFailed(videoId: String?) {
        val playing = nowPlaying ?: return
        val id = videoId ?: playing.result.id
        StreamResolver.invalidate(playing.result.detailUrl)
        errorMessage = "Strömmen slutade svara."
        noticeMessage = if (playing.hasNext) "Försöker igen – annars finns nästa video i listan." else null
        if (!failedOnce.contains(id)) {
            failedOnce.add(id)
            retryStream(id)
        } else {
            markVideoUnavailable(id)
            errorMessage = "Videon går inte att spela längre och togs bort från listan."
        }
    }

    /** Försöker en gång till med en nyfärsk stream-URL. Returnerar false om ett försök redan pågår. */
    fun retryStream(videoId: String?): Boolean {
        val playing = nowPlaying ?: return false
        val id = videoId ?: playing.result.id
        if (!retryingIds.add(id)) return false
        viewModelScope.launch {
            try {
                val stream = resolveStream(playing.result, forceRefresh = true)
                if (stream == null) {
                    markVideoUnavailable(id)
                    errorMessage = "Videon går inte att spela längre."
                } else {
                    val refreshed = playing.result.copy(
                        streamUrl = stream.url,
                        playability = Playability.PLAYABLE,
                        qualityLabel = stream.qualityLabel ?: playing.result.qualityLabel
                    )
                    candidates[id] = refreshed
                    publishResults()
                    nowPlaying = playing.copy(
                        streamUrl = stream.url,
                        startPositionMs = playbackStore.position(id),
                        result = refreshed
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                markVideoUnavailable(id)
                errorMessage = "Videon går inte att spela längre (${error.message ?: "okänt fel"})."
            } finally {
                retryingIds.remove(id)
            }
        }
        return true
    }

    fun onPlaybackProgress(videoId: String, positionMs: Long, durationMs: Long) {
        playbackStore.saveProgress(videoId, positionMs, durationMs)
        playbackRevision++
    }

    fun closePlayer() {
        nowPlaying = null
        queue = emptyList()
        queueIndex = 0
        playbackRevision++
    }

    fun markVideoUnavailable(videoId: String) {
        candidates[videoId]?.let { candidates[videoId] = it.copy(playability = Playability.UNPLAYABLE) }
        results = results.filterNot { it.id == videoId }
        lastFeed = lastFeed.filterNot { it.id == videoId }
        playbackRevision++
    }

    // ---------------------------------------------------------------------
    // Bibliotek
    // ---------------------------------------------------------------------

    fun recentPlaybacks(limit: Int = 12): List<RecentPlayback> = playbackStore.recent(limit)

    fun continueWatching(limit: Int = 10): List<RecentPlayback> = playbackStore.continueWatching(limit)

    fun clearPlaybackHistory() {
        playbackStore.clear()
        playbackRevision++
    }

    fun addFavorite(result: SearchResult) {
        viewModelScope.launch {
            repository.insertVideo(result.toVideoEntity())
            repository.addFavorite(result.id)
        }
    }

    fun addWatchLater(result: SearchResult) {
        viewModelScope.launch {
            repository.insertVideo(result.toVideoEntity())
            repository.addWatchLater(result.id)
        }
    }

    fun addNeverShowAgain(keyword: String) {
        val normalizedKeyword = keyword.trim().lowercase(Locale.ROOT)
        if (normalizedKeyword.isBlank()) return

        viewModelScope.launch {
            repository.addDislikeRule(type = "keyword", value = normalizedKeyword)
            blockedWords = (blockedWords + normalizedKeyword).distinct()
            candidates.asSequence()
                .filter { it.value.title.lowercase(Locale.ROOT).contains(normalizedKeyword) }
                .map { it.key }
                .toList()
                .forEach { id -> candidates.remove(id) }
            publishResults()
        }
    }

    // ---------------------------------------------------------------------
    // Sparade sökningar
    // ---------------------------------------------------------------------

    fun useSearchPreset(preset: SearchPresetEntity) {
        searchJob?.cancel()
        query = preset.query
        negativeKeywords = preset.negativeKeywords
        selectedSubcategoryIds = emptySet()
        activePlan = null
        candidates.clear()
        results = emptyList()
        siteReports = emptyList()
        activeQueries = emptyList()
        explanation = null
        errorMessage = null
        noticeMessage = null
        verificationTotal = 0
        verificationDone = 0
        canLoadMore = false
        page = 1
    }

    fun saveSearchPreset(name: String): Boolean {
        val safeName = name.trim().take(60)
        val queryToSave = query.trim()
        if (safeName.isBlank()) {
            errorMessage = "Ange ett namn på sökningen."
            return false
        }
        if (queryToSave.isBlank() && selectedSubcategoryIds.isEmpty()) {
            errorMessage = "Ange ett sökord eller välj en kategori."
            return false
        }

        val plan = planner.plan(
            rawInput = queryToSave,
            categoryTerms = SearchTaxonomy.termsFor(selectedSubcategoryIds),
            extraNegatives = negativeKeywords.split(',', '\n', ';')
        )
        if (plan.blockedReason != null) {
            errorMessage = plan.blockedReason
            return false
        }
        if (plan.queries.isEmpty()) {
            errorMessage = plan.explanation
            return false
        }

        val queryForPreset = if (queryToSave.isNotBlank()) {
            queryToSave
        } else {
            SearchTaxonomy.labelsFor(selectedSubcategoryIds).joinToString(", ")
        }

        viewModelScope.launch {
            try {
                repository.addSearchPreset(safeName, queryForPreset, negativeKeywords.trim())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = "Kunde inte spara sökningen: ${error.message}"
            }
        }
        return true
    }

    fun useHistoryTerm(term: String) {
        query = term
        explanation = null
        errorMessage = null
    }

    fun clearSearchHistory() {
        preferences.clearSearchHistory()
    }

    /** Kort beskrivning av aktiva filter, t.ex. "Blond · Kök". */
    fun activeFilterSummary(): String = SearchTaxonomy.labelsFor(selectedSubcategoryIds).joinToString(" · ")

    private val retryingIds = mutableSetOf<String>()
    private val failedOnce = mutableSetOf<String>()

    private fun SearchResult.toVideoEntity() = VideoEntity(
        id = id,
        title = title,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        sourceSite = sourceSite,
        detailPageUrl = detailUrl,
        streamUrl = streamUrl,
        quality = qualityLabel,
        tags = matchedTerms.joinToString(", ")
    )

    private companion object {
        const val MAX_CANDIDATES = 140
        const val WAVE_SIZE = 12
        const val TARGET_VERIFIED = 24
        const val FULL_WAVE_LIMIT = 9
        const val FULL_VERIFY_BUDGET = 108
        const val LOAD_MORE_BUDGET = 48
    }
}
