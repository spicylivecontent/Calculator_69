package com.calculator.app.ui.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calculator.app.data.Repository
import com.calculator.app.data.extractor.MultiSiteSearcher
import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val searcher = MultiSiteSearcher()
    private val repository = Repository(application)

    var query by mutableStateOf("")
        private set

    var results by mutableStateOf<List<SearchResult>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Globala negativa nyckelord (kommaseparerade eller en per rad)
    var negativeKeywords by mutableStateOf("")
        private set

    val supportedSites: List<String> = searcher.getSupportedSites()

    fun onQueryChange(newQuery: String) {
        query = newQuery
    }

    fun onNegativeKeywordsChange(value: String) {
        negativeKeywords = value
    }

    fun search() {
        if (query.isBlank()) return

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                // 1. Hämta råresultat från alla sajter
                val raw = searcher.search(query)

                // 2. Hämta sparade dislike-regler från databasen
                val rules = repository.getDislikeRules().first()

                // 3. Bygg lista av förbjudna ord (från UI + databas)
                val blocked = mutableSetOf<String>()

                // Från det tillfälliga filterfältet
                negativeKeywords
                    .split(",", "\n")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .forEach { blocked.add(it) }

                // Från sparade regler (keyword + tag)
                rules
                    .filter { it.type == "keyword" || it.type == "tag" }
                    .forEach { blocked.add(it.value.lowercase()) }

                // 4. Filtrera resultaten
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

    /** Lägg till en permanent "Never show again"-regel */
    fun addNeverShowAgain(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            repository.addDislikeRule(type = "keyword", value = keyword.trim().lowercase())
            // Uppdatera aktuella resultat direkt
            results = results.filter {
                !it.title.lowercase().contains(keyword.trim().lowercase())
            }
        }
    }

    fun clearResults() {
        results = emptyList()
        errorMessage = null
    }
}
