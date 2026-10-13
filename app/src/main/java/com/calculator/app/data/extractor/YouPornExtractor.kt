package com.calculator.app.data.extractor

class YouPornExtractor : BaseSiteExtractor() {

    override val siteName = "youporn"

    override val listing = ListingConfig(
        siteName = siteName,
        baseUrl = "https://www.youporn.com",
        hrefPattern = Regex("/watch/(\\d+)"),
        requireHrefContains = "/watch/",
        titleSelectors = listOf(".video-title", ".title", "a.title", "h3", "p"),
        durationSelectors = listOf(".video-duration", ".duration", "[class*=duration]", "var"),
        limit = 42
    )

    override fun searchUrl(query: String, page: Int): String =
        "https://www.youporn.com/search/?query=${Fetch.urlEncode(query)}&page=$page"
}
