package com.calculator.app.data.extractor

class EpornerExtractor : BaseSiteExtractor() {

    override val siteName = "eporner"

    override val listing = ListingConfig(
        siteName = siteName,
        baseUrl = "https://www.eporner.com",
        hrefPattern = Regex("/video-([A-Za-z0-9]+)"),
        limit = 42
    )

    override fun searchUrl(query: String, page: Int): String =
        "https://www.eporner.com/search/${Fetch.urlEncode(query)}/$page/"
}
