package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult

interface SiteExtractor {
    val siteName: String
    suspend fun search(query: String, page: Int = 1): List<SearchResult>
}
