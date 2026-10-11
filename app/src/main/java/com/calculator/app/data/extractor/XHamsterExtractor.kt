package com.calculator.app.data.extractor

class XHamsterExtractor : BaseSiteExtractor() {

    override val siteName = "xhamster"

    override val listing = ListingConfig(
        siteName = siteName,
        baseUrl = "https://xhamster.com",
        hrefPattern = Regex("/videos/([A-Za-z0-9\\-]{3,})"),
        requireHrefContains = "/videos/",
        titleSelectors = listOf(".video-thumb-info__name", ".title", "a.title", "h3", "p"),
        durationSelectors = listOf(".thumb-image-container__duration", "[class*=duration]", ".duration", "var"),
        limit = 42
    )

    override fun searchUrl(query: String, page: Int): String =
        "https://xhamster.com/search/${Fetch.urlEncode(query)}?page=$page"
}
