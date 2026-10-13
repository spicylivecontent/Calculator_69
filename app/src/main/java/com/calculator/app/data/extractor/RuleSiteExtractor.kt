package com.calculator.app.data.extractor

import com.calculator.app.data.model.SearchResult

/**
 * En sajt som styrs helt av en [ScrapeRule] i stället för av inbyggd kod. Används när en
 * hämtad regel ersätter en inbyggd sajt (t.ex. för att sajten bytt adress eller layout)
 * eller när regeln beskriver en helt ny sajt.
 */
internal class RuleSiteExtractor(private val rule: ScrapeRule) : SiteExtractor {

    override val siteName: String = rule.siteName

    override val baseUrl: String = rule.baseUrl

    override val supportsPagination: Boolean get() = rule.supportsPagination

    override suspend fun search(query: String, page: Int): List<SearchResult> {
        val url = rule.searchUrlFor(query, page) ?: return emptyList()
        val html = Fetch.page(url) ?: return emptyList()
        if (html.isBlank()) return emptyList()

        val config = rule.toListingConfig()
        val parsed = runCatching { ExtractorSupport.parseListings(html, config) }.getOrDefault(emptyList())
        if (parsed.isNotEmpty()) return parsed

        // Regeln kan vara skriven innan sajten bytte klassnamn – reservparsningen
        // letar då upp videolänkarna i stället för att ge noll träffar.
        return runCatching {
            ExtractorSupport.parseListingsGeneric(
                html = html,
                baseUrl = rule.baseUrl,
                siteName = rule.siteName,
                limit = rule.limit
            )
        }.getOrDefault(emptyList())
    }
}
