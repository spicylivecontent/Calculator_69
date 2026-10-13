package com.calculator.app.data.extractor

class XVideosExtractor : BaseSiteExtractor() {

    override val siteName = "xvideos"

    override val listing = ListingConfig(
        siteName = siteName,
        baseUrl = "https://www.xvideos.com",
        hrefPattern = Regex("/video\\.([A-Za-z0-9]+)"),
        titleSelectors = listOf("p.title", ".title", "a.title", "span.title", "h3", "p"),
        limit = 42
    )

    override fun searchUrl(query: String, page: Int): String {
        val encoded = Fetch.urlEncode(query)
        return if (page <= 1) {
            "https://www.xvideos.com/?k=$encoded"
        } else {
            "https://www.xvideos.com/?k=$encoded&p=${page - 1}"
        }
    }
}
