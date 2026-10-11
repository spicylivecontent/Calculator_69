package com.calculator.app.data.extractor

class HQpornerExtractor : BaseSiteExtractor() {

    override val siteName = "hqporner"

    override val listing = ListingConfig(
        siteName = siteName,
        baseUrl = "https://hqporner.com",
        hrefPattern = Regex("(?:hdporn|hd|video)/([A-Za-z0-9\\-]{3,})"),
        titleSelectors = listOf("h3", ".title", ".meta", "a.title", "p"),
        limit = 36
    )

    override fun searchUrl(query: String, page: Int): String {
        val encoded = Fetch.urlEncode(query)
        return if (page <= 1) {
            "https://hqporner.com/?q=$encoded"
        } else {
            "https://hqporner.com/?q=$encoded&p=$page"
        }
    }
}
