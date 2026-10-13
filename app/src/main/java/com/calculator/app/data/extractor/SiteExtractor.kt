package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult
import kotlinx.coroutines.CancellationException

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

    /**
     * Liten intern yta för enhetstester och diagnostik: samma information som
     * sökningen använder, utan att göra den publika API:n större.
     */
    internal val listingSpec: ListingConfig get() = listing

    internal fun urlForQuery(query: String, page: Int): String = searchUrl(query, page)

    override suspend fun search(query: String, page: Int): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val url = try {
            searchUrl(query.trim(), page.coerceAtLeast(1))
        } catch (_: Exception) {
            return emptyList()
        }
        val html = Fetch.page(url) ?: return emptyList()
        return try {
            val parsed = ExtractorSupport.parseListings(html, listing)
            if (parsed.isNotEmpty()) return parsed
            // Sajten kan ha bytt layout eller klassnamn – reservparsningen letar
            // upp videolänkar på nytt i stället för att ge noll träffar.
            ExtractorSupport.parseListingsGeneric(
                html = html,
                baseUrl = listing.baseUrl,
                siteName = listing.siteName,
                limit = listing.limit
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Sant om sajten svarar och ger minst en träff (används av självtestet). */
    suspend fun responds(query: String = "amateur"): Boolean =
        try {
            search(query, 1).isNotEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
}
