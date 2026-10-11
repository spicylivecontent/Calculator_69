package com.calculator.app.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calculator.app.data.extractor.EpornerExtractor
import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val extractor = EpornerExtractor()

    var query by mutableStateOf("")
        private set

    var results by mutableStateOf<List<SearchResult>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onQueryChange(newQuery: String) {
        query = newQuery
    }

    fun search() {
        if (query.isBlank()) return

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                results = extractor.search(query)
                if (results.isEmpty()) {
                    errorMessage = "Inga resultat hittades"
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
