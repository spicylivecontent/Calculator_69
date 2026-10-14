package com.calculator.app.data.extractor

/**
 * LuxureTV (https://en.luxuretv.com/) – sök och listning.
 * Video-URL:er är typiskt /videos/slug-id.html
 */
class LuxureTvExtractor : BaseSiteExtractor() {

    override val siteName = "luxuretv"

    override val listing = ListingConfig(
        siteName = siteName,
        baseUrl = "https://en.luxuretv.com",
        hrefPattern = Regex("/videos?/([A-Za-z0-9][A-Za-z0-9_\\-]{4,120})"),
        limit = 36
    )

    override fun searchUrl(query: String, page: Int): String {
        val q = Fetch.urlEncode(query.trim())
        return if (page <= 1) {
            "https://en.luxuretv.com/search/$q/"
        } else {
            "https://en.luxuretv.com/search/$q/$page/"
        }
    }
}
