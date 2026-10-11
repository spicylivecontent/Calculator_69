package com.calculator.app.data.extractor

class XnxxExtractor : BaseSiteExtractor() {

    override val siteName = "xnxx"

    override val listing = ListingConfig(
        siteName = siteName,
        baseUrl = "https://www.xnxx.com",
        hrefPattern = Regex("/video-([A-Za-z0-9]+)"),
        titleSelectors = listOf("p.title", ".title", "a.title", "h3", "p"),
        limit = 42
    )

    override fun searchUrl(query: String, page: Int): String =
        "https://www.xnxx.com/search/${Fetch.urlEncode(query)}/$page"
}
