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
import com.calculator.app.data.extractor.ScrapeRuleStore
import com.calculator.app.data.extractor.ScrapeRules
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
import com.calculator.app.network.NetworkCondition
import com.calculator.app.ui.web.AdBlockLevel
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

/** Videon som visas i WebView-spelaren, med kö för nästa/föregående video. */
data class WebPlaying(
    val result: SearchResult,
    val startPositionMs: Long = 0L,
    val queueIndex: Int = 0,
    val queueSize: Int = 1
) {
    val hasNext: Boolean get() = queueIndex < queueSize - 1
    val hasPrevious: Boolean get() = queueIndex > 0
}

/**
 * Håller ihop sökning, inläsning av all information om träffarna och uppspelning.
 *
 * Sökningen sker i två steg: först samlas alla träffar in (de visas direkt med
 * status "kontrollerar"), därefter kontrolleras strömmarna i tur och ordning.
 * När kontrollen är klar finns bara videor som går att titta på kvar.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    /** Läses av vid varje sökning – nätverksläget styr parallellitet och tidsgränser. */
    private var network: NetworkCondition = NetworkCondition.current(application)

    private var searcher by mutableStateOf(MultiSiteSearcher(network = network))
    private val repository = Repository(application)
    private val planner = SearchPlanner()
    val preferences = AppPreferences(application)
    private val playbackStore = PlaybackStore(application)

    /** Skrapregler: inbyggda, eller en nyare version hämtad från nätet. */
    private val ruleStore = ScrapeRuleStore(application)
    private var rules: ScrapeRules = ruleStore.current()

    /** Status för skrapreglerna, visas i inställningarna. */
    var rulesStatus by mutableStateOf(ruleStore.status())
        private set

    /** Kort besked efter en manuell uppdatering. */
    var rulesMessage by mutableStateOf<String?>(null)
        private set

    init {
        // Uppdaterade regler hämtas i bakgrunden (högst en gång i dygnet) och slår igenom
        // först när de finns – en långsam hämtning får aldrig fördröja en sökning.
        viewModelScope.launch {
            val result = ruleStore.refresh()
            if (result is ScrapeRuleStore.RefreshResult.Updated) {
                applyRules()
                rulesMessage = "Skrapregler uppdaterade till v${result.version}"
            }
        }
    }

    private fun applyRules() {
        rules = ruleStore.current()
        searcher = MultiSiteSearcher(network = network, rules = rules)
        rulesStatus = ruleStore.status()
    }

    private val candidates = LinkedHashMap<String, SearchResult>()
    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var playbackJob: Job? = null
    private var playbackGeneration = 0L

    private fun cancelSearch() {
        searchGeneration++
        searchJob?.cancel()
        searchJob = null
        isSearching = false
        isLoadingMore = false
    }

    private fun cancelPlayback() {
        playbackGeneration++
        playbackJob?.cancel()
        playbackJob = null
        isResolvingStream = false
        resolvingTitle = ""
    }
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

    /** Sajter användaren stängt av permanent i inställningarna. */
    var disabledSiteIds by mutableStateOf(preferences.disabledSites())
        private set

    var sortOrder by mutableStateOf(SortOrder.RELEVANCE)
        private set

    var lengthFilter by mutableStateOf(LengthFilter.ALL)
        private set

    var onlyPlayable by mutableStateOf(preferences.onlyPlayable)
        private set

    val siteInfos: List<SiteInfo> get() = searcher.siteInfos
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

    /** Antal träffar vars detaljsida ska läsas in / har lästs in. */
    var verificationTotal by mutableStateOf(0)
        private set

    var verificationDone by mutableStateOf(0)
        private set

    /** Taggar som används för att filtrera träffarna direkt i appen. */
    var selectedTagFilters by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Taggar som finns bland träffarna, vanligast först. */
    var availableTags by mutableStateOf<List<String>>(emptyList())
        private set

    /** Fritext som söks direkt i allt som skrapats in (rubrik, taggar, beskrivning). */
    var scrapedQuery by mutableStateOf("")
        private set

    /** True när träffar visas medan deras information läses in. */
    val isEnriching: Boolean get() = isVerifyingResults

    /** Hur många av träffarna som har fått taggar och beskrivning inlästa. */
    val enrichedCount: Int get() = results.count { it.isEnriched }

    var canLoadMore by mutableStateOf(false)
        private set

    /** Senaste spelbara träffar – används av "Fortsätt titta"/flödet på startsidan. */
    var lastFeed by mutableStateOf<List<SearchResult>>(emptyList())
        private set

    // ---------- Uppspelning ----------
    /** Videon som spelas på sajten i WebView-spelaren (standardvägen). */
    var webPlaying by mutableStateOf<WebPlaying?>(null)
        private set

    /** Videon som spelas i den inbyggda spelaren (beta, kräver extraherad ström). */
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set

    /** Annonsblockeringsnivå för WebView-spelaren. */
    var adBlockLevel by mutableStateOf(preferences.adBlockLevel)
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
        query = wanted.filterNot { term ->
            SearchTaxonomy.allSubcategories.any { sub ->
                sub.id in matchingIds && sub.terms.any { it.equals(term, ignoreCase = true) }
            }
        }.joinToString(" ")
        selectedTagFilters = emptySet()
        scrapedQuery = ""
        explanation = null
        errorMessage = null
        search()
    }

    /** Filtrerar träffarna på en tagg (växlar av/på). */
    fun toggleTagFilter(tag: String) {
        val cleaned = tag.trim()
        if (cleaned.isBlank()) return
        selectedTagFilters = if (selectedTagFilters.any { it.equals(cleaned, ignoreCase = true) }) {
            selectedTagFilters.filterNot { it.equals(cleaned, ignoreCase = true) }.toSet()
        } else {
            selectedTagFilters + cleaned
        }
        publishResults()
    }

    fun clearTagFilters() {
        selectedTagFilters = emptySet()
        publishResults()
    }

    /** Sök vidare på en tagg: filtrerar träffarna och gör en ny sökning på sajterna. */
    fun searchTag(tag: String) {
        val cleaned = tag.trim()
        if (cleaned.isBlank()) return
        selectedTagFilters = selectedTagFilters + cleaned
        val base = query.trim()
        query = if (base.isEmpty() || base.contains(cleaned, ignoreCase = true)) {
            if (base.isEmpty()) cleaned else base
        } else {
            "$base $cleaned"
        }
        search()
    }

    /** Avancerad sökning direkt i all skrapad information. */
    fun onScrapedQueryChange(text: String) {
        scrapedQuery = text
        publishResults()
    }

    fun clearScrapedQuery() {
        scrapedQuery = ""
        publishResults()
    }

    /**
     * Panik/lås: tömmer allt känsligt ur minnet (träffar, frågor, taggar, historik i
     * cache) utan att röra det som användaren sparat i biblioteket.
     */
    // ---------------------------------------------------------------------
    // Skrapregler (inställningarna)
    // ---------------------------------------------------------------------

    val remoteRulesEnabled: Boolean get() = preferences.remoteRulesEnabled

    fun onRemoteRulesEnabledChange(enabled: Boolean) {
        preferences.remoteRulesEnabled = enabled
        ruleStore.invalidate()
        if (!enabled) ruleStore.reset()
        applyRules()
        rulesMessage = if (enabled) {
            "Regler hämtas automatiskt, högst en gång i dygnet"
        } else {
            "Inbyggda regler används"
        }
    }

    fun refreshRulesNow() {
        viewModelScope.launch {
            val result = ruleStore.refresh(force = true)
            rulesMessage = when (result) {
                is ScrapeRuleStore.RefreshResult.Updated -> {
                    applyRules()
                    "Uppdaterade till v${result.version}: ${result.sites} sajter" +
                        if (result.rejected > 0) " (${result.rejected} ogiltiga regler hoppades över)" else ""
                }
                ScrapeRuleStore.RefreshResult.Failed -> "Kunde inte hämta – nuvarande regler behålls"
                else -> "Ingen nyare version hittades"
            }
            rulesStatus = ruleStore.status()
        }
    }

    fun resetRules() {
        ruleStore.reset()
        applyRules()
        rulesMessage = "Tillbaka till de inbyggda reglerna"
    }

    fun clearSensitiveState() {
        cancelSearch()
        cancelPlayback()
        retryingIds.clear()
        failedOnce.clear()
        query = ""
        negativeKeywords = ""
        scrapedQuery = ""
        selectedSubcategoryIds = emptySet()
        selectedTagFilters = emptySet()
        availableTags = emptyList()
        activePlan = null
        blockedWords = emptyList()
        candidates.clear()
        results = emptyList()
        lastFeed = emptyList()
        siteReports = emptyList()
        activeQueries = emptyList()
        explanation = null
        errorMessage = null
        noticeMessage = null
        verificationTotal = 0
        verificationDone = 0
        canLoadMore = false
        page = 1
        webPlaying = null
        nowPlaying = null
        queue = emptyList()
        queueIndex = 0
        StreamResolver.clearCache()
        playbackRevision++
    }

    fun onAdBlockLevelChange(level: AdBlockLevel) {
        adBlockLevel = level
        preferences.adBlockLevel = level
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

    /** Slår av eller på en sajt för gott (sparas i inställningarna). */
    fun onSiteEnabledChange(siteId: String, enabled: Boolean) {
        preferences.setSiteEnabled(siteId, enabled)
        disabledSiteIds = preferences.disabledSites()
        if (!enabled) selectedSiteIds = selectedSiteIds - siteId
    }

    /**
     * Sajterna som en sökning faktiskt använder: de valda (eller alla) minus de som
     * stängts av permanent. Tom mängd betyder att inget kan sökas.
     */
    private fun effectiveSiteIds(): Set<String> {
        val disabled = disabledSiteIds
        val available = siteInfos.map { it.id }.filterNot { it in disabled }.toSet()
        val explicitlySelected = selectedSiteIds
        return if (explicitlySelected.isEmpty()) available else explicitlySelected.intersect(available)
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
        cancelSearch()
        query = ""
        negativeKeywords = ""
        selectedSubcategoryIds = emptySet()
        selectedTagFilters = emptySet()
        availableTags = emptyList()
        scrapedQuery = ""
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
        network = NetworkCondition.current(getApplication())
        searcher = MultiSiteSearcher(network = network, rules = rules)

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
        cancelSearch()
        val generation = searchGeneration
        isSearching = true
        searchJob = viewModelScope.launch {
            candidates.clear()
            results = emptyList()
            siteReports = emptyList()
            verificationTotal = 0
            verificationDone = 0
            canLoadMore = false
            page = 1
            blockedWords = loadBlockedWords(plan)
            if (query.isNotBlank()) preferences.addSearchHistory(query)

            val siteIds = effectiveSiteIds()
            if (siteIds.isEmpty()) {
                errorMessage = "Alla sajter är avstängda i inställningarna."
                isSearching = false
                return@launch
            }

            try {
                val outcome = searcher.search(
                    queries = plan.queries,
                    siteIds = siteIds,
                    startPage = 1,
                    pageCount = 1,
                    onBatch = { batch -> applyBatch(batch) }
                )
                siteReports = outcome.reports
                canLoadMore = outcome.reports.any { it.count > 0 }
                isSearching = false
                enrichPending(plan, waveLimit = FULL_WAVE_LIMIT, budget = FULL_VERIFY_BUDGET)
                finalizeSearch()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = "Sökningen misslyckades: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                if (generation == searchGeneration) {
                    isSearching = false
                    verificationTotal = verificationDone
                    publishResults()
                }
            }
        }
    }

    /** Hämtar nästa sida och verifierar de nya träffarna. */
    fun loadMore() {
        val plan = activePlan ?: return
        if (isSearching || isLoadingMore || isEnriching || !canLoadMore) return
        cancelSearch()
        val generation = searchGeneration
        isLoadingMore = true
        searchJob = viewModelScope.launch {
            errorMessage = null
            try {
                val nextPage = page + 1
                val outcome = searcher.search(
                    queries = plan.queries,
                    siteIds = effectiveSiteIds(),
                    startPage = nextPage,
                    pageCount = 1,
                    onBatch = { batch -> applyBatch(batch) }
                )
                page = nextPage
                siteReports = outcome.reports
                canLoadMore = outcome.reports.any { it.count > 0 }
                enrichPending(plan, waveLimit = FULL_WAVE_LIMIT, budget = LOAD_MORE_BUDGET)
                finalizeSearch()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = "Kunde inte hämta fler träffar: ${error.message}"
            } finally {
                if (generation == searchGeneration) {
                    isLoadingMore = false
                    verificationTotal = verificationDone
                    publishResults()
                }
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

    /**
     * Läser in all information om träffarna (taggar, kategorier, medverkande,
     * kvalitet, längd och beskrivning) i vågor, så att listan fylls på medan
     * hämtningen pågår. Sidor som inte svarar tas bort.
     */
    private suspend fun enrichPending(plan: SearchPlanner.Plan, waveLimit: Int, budget: Int): Int {
        var enriched = 0
        var waves = 0
        var spent = 0

        while (waves < waveLimit && spent < budget) {
            val pending = ResultRanker.rank(
                candidates.values.filter { it.playability == Playability.CHECKING || it.playability == Playability.UNVERIFIED },
                plan.terms
            ).take(WAVE_SIZE)
            if (pending.isEmpty()) break

            spent += pending.size
            verificationTotal += pending.size
            enriched += searcher.enrichMetadata(
                candidates = pending,
                concurrency = network.enrichmentParallelism
            ) { updated ->
                withContext(Dispatchers.Main) { applyEnriched(updated) }
            }
            waves++
            if (enriched >= TARGET_VERIFIED) break
        }
        return enriched
    }

    private fun applyEnriched(updated: SearchResult) {
        verificationDone++
        if (isFilteredOut(updated)) candidates.remove(updated.id)
        else candidates[updated.id] = updated
        publishResults()
    }

    private fun finalizeSearch() {
        for (item in candidates.values.filter { it.playability == Playability.CHECKING }) {
            candidates[item.id] = item.copy(playability = Playability.UNVERIFIED)
        }
        publishResults()
        if (onlyPlayable && results.none { it.isPlayable } && candidates.isNotEmpty()) {
            noticeMessage = "Ingen av träffarna kunde läsas in just nu. Försök igen om en stund eller " +
                "tryck på \"Ladda fler\" för fler träffar."
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

    private fun isFilteredOut(result: SearchResult): Boolean {
        val text = result.searchableText.lowercase(Locale.ROOT)
        return AgeSafety.containsBlockedTerm(text) || blockedWords.any { text.contains(it) }
    }

    private fun publishResults() {
        val plan = activePlan
        val terms = plan?.terms?.takeIf { it.isNotEmpty() } ?: ResultRanker.tokenize(query)
        val ranked = ResultRanker.rank(candidates.values.filterNot { isFilteredOut(it) }, terms)
        val filtered = ranked.filter { result ->
            lengthFilter.matches(result.durationSeconds) && when (result.playability) {
                // Inlästa träffar visas alltid.
                Playability.PLAYABLE -> true
                // Träffar som läses in just nu visas medan hämtningen pågår.
                Playability.CHECKING -> true
                // Sidor som är borta eller inte kontrollerade döljs som standard.
                Playability.UNPLAYABLE, Playability.UNVERIFIED -> !onlyPlayable
            }
        }

        availableTags = ResultRanker.tagCloud(filtered, TAG_CLOUD_SIZE)

        val byTags = ResultRanker.filterByTags(filtered, selectedTagFilters)
        results = if (scrapedQuery.isBlank()) {
            ResultRanker.applySort(byTags, sortOrder)
        } else {
            // Avancerad sökning i allt som skrapats in – rubrik, taggar och beskrivning.
            ResultRanker.searchScraped(byTags, scrapedQuery)
        }

        val playable = results.filter { it.isPlayable }.take(30)
        lastFeed = playable
    }

    // ---------------------------------------------------------------------
    // Uppspelning
    // ---------------------------------------------------------------------

    /** Öppnar filmen på sajten i den inbyggda WebView-spelaren. */
    fun playVideo(result: SearchResult) = openOnSite(result, startPositionMs = -1L)

    /** Öppnar en sparad/spelad video igen, med återupptagning där användaren slutade. */
    fun playSavedVideo(video: VideoEntity) {
        val detail = video.detailPageUrl
        if (detail.isNullOrBlank()) {
            errorMessage = "Den sparade videon saknar en sidadress."
            return
        }
        playVideo(SearchResult(
            id = video.id, title = video.title, thumbnailUrl = video.thumbnailUrl,
            duration = com.calculator.app.data.model.DurationParser.format(video.durationSeconds),
            durationSeconds = video.durationSeconds, sourceSite = video.sourceSite,
            detailUrl = detail, streamUrl = video.streamUrl, qualityLabel = video.quality,
            tags = video.tags.split(',').map { it.trim() }.filter { it.isNotBlank() }
        ))
    }

    fun playRecent(entry: RecentPlayback) {
        openOnSite(entry.toSearchResult(), startPositionMs = entry.positionMs)
    }

    fun playRelative(offset: Int) {
        val target = queue.getOrNull(queueIndex + offset) ?: return
        openOnSite(target, startPositionMs = -1L, keepQueue = true)
    }

    /** Stänger WebView-spelaren. */
    fun closeWebPlayer() {
        cancelPlayback()
        webPlaying = null
        queue = emptyList()
        queueIndex = 0
        playbackRevision++
    }

    /**
     * Beta: spelar upp en extraherad ström i den inbyggda spelaren i stället.
     * Används bara när användaren själv väljer det i spelarens meny.
     */
    fun playNativeStream(result: SearchResult) {
        cancelPlayback()
        val generation = playbackGeneration
        playbackJob = viewModelScope.launch {
            isResolvingStream = true
            resolvingTitle = result.title
            errorMessage = null
            try {
                val stream = resolveStream(result, forceRefresh = true)
                if (stream == null) {
                    StreamResolver.invalidate(result.detailUrl)
                    errorMessage = "Ingen direktuppspelningsbar ström hittades – videon öppnas på sajten i stället."
                    return@launch
                }
                val playable = result.copy(
                    streamUrl = stream.url,
                    playability = Playability.PLAYABLE,
                    qualityLabel = stream.qualityLabel ?: result.qualityLabel
                )
                candidates[playable.id] = playable
                publishResults()
                webPlaying = null
                nowPlaying = NowPlaying(
                    result = playable,
                    streamUrl = stream.url,
                    startPositionMs = playbackStore.position(result.id),
                    queueIndex = queueIndex,
                    queueSize = queue.size.coerceAtLeast(1)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = "Direktuppspelning misslyckades (${error.message ?: result.sourceSite})."
            } finally {
                if (generation == playbackGeneration) {
                    isResolvingStream = false
                    resolvingTitle = ""
                }
            }
        }
    }

    private fun openOnSite(result: SearchResult, startPositionMs: Long, keepQueue: Boolean = false) {
        cancelPlayback()
        nowPlaying = null
        val enriched = candidates[result.id] ?: result
        if (isFilteredOut(enriched)) {
            errorMessage = "Videon är blockerad av innehållsfiltret."
            return
        }
        val saved = when {
            startPositionMs >= 0L -> startPositionMs
            preferences.resumePlayback -> playbackStore.position(enriched.id)
            else -> 0L
        }

        if (!keepQueue || queue.isEmpty()) {
            queue = results.filter { it.isPlayable }
            if (queue.none { it.id == enriched.id }) queue = listOf(enriched)
            queueIndex = queue.indexOfFirst { it.id == enriched.id }.coerceAtLeast(0)
        } else {
            queueIndex = queue.indexOfFirst { it.id == enriched.id }.coerceAtLeast(0)
        }

        candidates[enriched.id] = enriched
        playbackStore.savePlayed(enriched)
        playbackRevision++
        errorMessage = null
        webPlaying = WebPlaying(
            result = enriched,
            startPositionMs = saved,
            queueIndex = queueIndex,
            queueSize = queue.size.coerceAtLeast(1)
        )
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
        if (id != playing.result.id) return
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
        if (videoId != null && videoId != playing.result.id) return false
        val id = videoId ?: playing.result.id
        if (!retryingIds.add(id)) return false
        playbackJob = viewModelScope.launch {
            try {
                val stream = resolveStream(playing.result, forceRefresh = true)
                if (nowPlaying != playing) return@launch
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
        cancelPlayback()
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
                .filter { isFilteredOut(it.value) }
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
        cancelSearch()
        query = preset.query
        negativeKeywords = preset.negativeKeywords
        selectedSubcategoryIds = emptySet()
        selectedTagFilters = emptySet()
        scrapedQuery = ""
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

        val queryForPreset = (listOf(queryToSave) +
            SearchTaxonomy.termsFor(selectedSubcategoryIds)).filter { it.isNotBlank() }.joinToString(" ")

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
        tags = tags.joinToString(", ")
    )

    private companion object {
        const val MAX_CANDIDATES = 140
        const val TAG_CLOUD_SIZE = 24
        const val WAVE_SIZE = 10
        const val TARGET_VERIFIED = 20
        const val FULL_WAVE_LIMIT = 6
        const val FULL_VERIFY_BUDGET = 60
        const val LOAD_MORE_BUDGET = 30
    }
}
