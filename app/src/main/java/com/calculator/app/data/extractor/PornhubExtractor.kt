package com.calculator.app.data.extractor

class PornhubExtractor : BaseSiteExtractor() {

    override val siteName = "pornhub"

    override val listing = ListingConfig(
        siteName = siteName,
        baseUrl = "https://www.pornhub.com",
        hrefPattern = Regex("viewkey=([A-Za-z0-9]+)"),
        requireHrefContains = "view_video",
        titleSelectors = listOf("span.title", ".title", "a.title", "h3", "p"),
        durationSelectors = listOf("var.duration", ".duration", "[class*=duration]", "var"),
        limit = 42
    )

    override fun searchUrl(query: String, page: Int): String =
        "https://www.pornhub.com/video/search?search=${Fetch.urlEncode(query)}&page=$page"
}
