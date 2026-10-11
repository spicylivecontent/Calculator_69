package com.calculator.app.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calculator.app.data.extractor.MultiSiteSearcher
import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val searcher = MultiSiteSearcher()

    var query by mutableStateOf("")
        private set

    var results by mutableStateOf<List<SearchResult>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    val supportedSites: List<String> = searcher.getSupportedSites()

    fun onQueryChange(newQuery: String) {
        query = newQuery
    }

    fun search() {
        if (query.isBlank()) return

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                results = searcher.search(query)
                if (results.isEmpty()) {
                    errorMessage = "Inga resultat hittades från någon sajt"
                }
            } catch (e: Exception) {
                errorMessage = "Fel vid sökning: ${e.message}"
                results = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun clearResults() {
        results = emptyList()
        errorMessage = null
    }
}
