package com.calculator.app.ui.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calculator.app.ai.QueryExpander
import com.calculator.app.data.Repository
import com.calculator.app.data.extractor.MultiSiteSearcher
import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val searcher = MultiSiteSearcher()
    private val repository = Repository(application)
    private val expander = QueryExpander()          // On-device lättvikts-AI

    var query by mutableStateOf("")
        private set

    var results by mutableStateOf<List<SearchResult>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Tillfälliga negativa nyckelord från UI
    var negativeKeywords by mutableStateOf("")
        private set

    // Förklaring från expandern (visas för användaren)
    var aiExplanation by mutableStateOf<String?>(null)
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
                // === 1. On-device expansion ===
                val expanded = expander.expand(query)
                aiExplanation = expanded.explanation

                val searchTerms = expanded.searchTerms

                // === 2. Hämta rådata ===
                val raw = searcher.search(searchTerms)

                // === 3. Samla alla negativa regler ===
                val blocked = mutableSetOf<String>()

                // Från AI-expandern
                expanded.negativeKeywords.forEach { blocked.add(it.lowercase()) }

                // Från UI-fältet
                negativeKeywords
                    .split(",", "\n")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .forEach { blocked.add(it) }

                // Från databasen (permanenta Never-show-regler)
                val rules = repository.getDislikeRules().first()
                rules
                    .filter { it.type == "keyword" || it.type == "tag" }
                    .forEach { blocked.add(it.value.lowercase()) }

                // === 4. Filtrera ===
                val filtered = raw.filter { result ->
                    val titleLower = result.title.lowercase()
                    blocked.none { bad -> titleLower.contains(bad) }
                }

                results = filtered

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

    fun addNeverShowAgain(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            repository.addDislikeRule(type = "keyword", value = keyword.trim().lowercase())
            results = results.filter {
                !it.title.lowercase().contains(keyword.trim().lowercase())
            }
        }
    }

    fun clearResults() {
        results = emptyList()
        errorMessage = null
        aiExplanation = null
    }
}
