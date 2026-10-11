package com.calculator.app.data.extractor

class BeegExtractor : BaseSiteExtractor() {

    override val siteName = "beeg"

    override val supportsPagination = false

    override val listing = ListingConfig(
        siteName = siteName,
        baseUrl = "https://beeg.com",
        hrefPattern = Regex("^/(\\d{5,})"),
        titleSelectors = listOf(".title", "a.title", "h3", "p", "span"),
        limit = 30
    )

    override fun searchUrl(query: String, page: Int): String =
        "https://beeg.com/search?q=${Fetch.urlEncode(query)}"
}
