package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult

interface SiteExtractor {
    val siteName: String

    /** Visningsnamn i gränssnittet. */
    val label: String get() = SearchResult.prettySiteName(siteName)

    /** Vissa sajter (t.ex. Beeg) saknar sidnumrering i sökningen. */
    val supportsPagination: Boolean get() = true

    suspend fun search(query: String, page: Int = 1): List<SearchResult>
}

/**
 * Gemensam bas för alla sajter: bygger sök-URL:en och parsar resultatlistan
 * med den strukturella parsaren i [ExtractorSupport].
 */
abstract class BaseSiteExtractor : SiteExtractor {

    protected abstract val listing: ListingConfig

    protected abstract fun searchUrl(query: String, page: Int): String

    override suspend fun search(query: String, page: Int): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val url = try {
            searchUrl(query.trim(), page.coerceAtLeast(1))
        } catch (_: Exception) {
            return emptyList()
        }
        val html = Fetch.page(url) ?: return emptyList()
        return try {
            ExtractorSupport.parseListings(html, listing)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
