package com.calculator.app.data.extractor

class SpankBangExtractor : BaseSiteExtractor() {

    override val siteName = "spankbang"

    override val listing = ListingConfig(
        siteName = siteName,
        baseUrl = "https://spankbang.com",
        hrefPattern = Regex("/([A-Za-z0-9]{4,})/video"),
        titleSelectors = listOf("span.name", ".name", ".title", "a.title", "h3", "p"),
        durationSelectors = listOf("span.length", ".length", ".duration", "[class*=duration]", "var"),
        limit = 42
    )

    override fun searchUrl(query: String, page: Int): String =
        "https://spankbang.com/s/${Fetch.urlEncode(query)}/$page/"
}
