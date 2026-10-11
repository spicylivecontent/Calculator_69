package com.calculator.app.ui.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calculator.app.ai.QueryExpander
import com.calculator.app.data.Repository
import com.calculator.app.data.entity.VideoEntity
import com.calculator.app.data.extractor.MultiSiteSearcher
import com.calculator.app.data.extractor.StreamResolver
import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val searcher = MultiSiteSearcher()
    private val repository = Repository(application)
    private val expander = QueryExpander()

    var query by mutableStateOf("")
        private set

    var results by mutableStateOf<List<SearchResult>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var negativeKeywords by mutableStateOf("")
        private set

    var aiExplanation by mutableStateOf<String?>(null)
        private set

    var lastFeed by mutableStateOf<List<SearchResult>>(emptyList())
        private set

    // Video som ska spelas
    var playingUrl by mutableStateOf<String?>(null)
        private set
    var playingTitle by mutableStateOf("")
        private set
    var isResolvingStream by mutableStateOf(false)
        private set

    val supportedSites: List<String> = searcher.getSupportedSites()

    fun onQueryChange(newQuery: String) {
        query = newQuery
        aiExplanation = null
    }

    fun onNegativeKeywordsChange(value: String) {
        negativeKeywords = value
    }

    fun search() {
        if (query.isBlank()) return

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            aiExplanation = null

            try {
                val expanded = expander.expand(query)
                aiExplanation = expanded.explanation

                val raw = searcher.search(expanded.searchTerms)

                val blocked = mutableSetOf<String>()
                expanded.negativeKeywords.forEach { blocked.add(it.lowercase()) }
                negativeKeywords.split(",", "\n")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .forEach { blocked.add(it) }

                val rules = repository.getDislikeRules().first()
                rules.filter { it.type == "keyword" || it.type == "tag" }
                    .forEach { blocked.add(it.value.lowercase()) }

                val filtered = raw.filter { result ->
                    val titleLower = result.title.lowercase()
                    blocked.none { bad -> titleLower.contains(bad) }
                }

                results = filtered
                lastFeed = filtered

                filtered.take(40).forEach { r ->
                    repository.insertVideo(
                        VideoEntity(
                            id = r.id,
                            title = r.title,
                            thumbnailUrl = r.thumbnailUrl,
                            sourceSite = r.sourceSite,
                            detailPageUrl = r.detailUrl
                        )
                    )
                }

                if (results.isEmpty()) {
                    errorMessage = if (raw.isEmpty()) {
                        "Inga resultat hittades från någon sajt"
                    } else {
                        "Alla resultat filtrerades bort av dina regler"
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Fel vid sökning: ${e.message}"
                results = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun playVideo(result: SearchResult) {
        viewModelScope.launch {
            isResolvingStream = true
            errorMessage = null
            try {
                val streamUrl = StreamResolver.resolve(result.detailUrl, result.sourceSite)
                if (streamUrl != null) {
                    playingUrl = streamUrl
                    playingTitle = result.title
                } else {
                    errorMessage = "Kunde inte hitta stream för denna video (${result.sourceSite})"
                }
            } catch (e: Exception) {
                errorMessage = "Fel vid stream-extract: ${e.message}"
            } finally {
                isResolvingStream = false
            }
        }
    }

    fun closePlayer() {
        playingUrl = null
        playingTitle = ""
    }

    fun addFavorite(result: SearchResult) {
        viewModelScope.launch {
            repository.insertVideo(
                VideoEntity(
                    id = result.id,
                    title = result.title,
                    thumbnailUrl = result.thumbnailUrl,
                    sourceSite = result.sourceSite,
                    detailPageUrl = result.detailUrl
                )
            )
            repository.addFavorite(result.id)
        }
    }

    fun addWatchLater(result: SearchResult) {
        viewModelScope.launch {
            repository.insertVideo(
                VideoEntity(
                    id = result.id,
                    title = result.title,
                    thumbnailUrl = result.thumbnailUrl,
                    sourceSite = result.sourceSite,
                    detailPageUrl = result.detailUrl
                )
            )
            repository.addWatchLater(result.id)
        }
    }

    fun addNeverShowAgain(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            repository.addDislikeRule(type = "keyword", value = keyword.trim().lowercase())
            results = results.filter {
                !it.title.lowercase().contains(keyword.trim().lowercase())
            }
        }
    }
}
