package com.calculator.app.data.extractor

class PornTrexExtractor : BaseSiteExtractor() {

    override val siteName = "porntrex"

    override val listing = ListingConfig(
        siteName = siteName,
        baseUrl = "https://www.porntrex.com",
        hrefPattern = Regex("/video/(\\d+)"),
        titleSelectors = listOf(".title", "strong", "a.title", "h3", "p"),
        durationSelectors = listOf(".duration", "[class*=duration]", ".time", "var"),
        limit = 42
    )

    override fun searchUrl(query: String, page: Int): String =
        "https://www.porntrex.com/search/${Fetch.urlEncode(query)}/$page/"
}
