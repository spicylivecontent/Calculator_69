package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Konfiguration och parsning för LuxureTV-testas mot en typisk sida – utan nät.
 */
class LuxureTvExtractorTest {

    private val extractor = LuxureTvExtractor()

    @Test
    fun searchUrlEncodesQueryAndAppendsPage() {
        assertEquals("https://en.luxuretv.com/search/stegmom/", extractor.urlForQuery("stegmom", 1))
        assertEquals("https://en.luxuretv.com/search/stegmom/3/", extractor.urlForQuery("stegmom", 3))
        // URLEncoder ersätter mellanslag med + och percentkodar åäö.
        assertEquals("https://en.luxuretv.com/search/r%C3%B6d+h%C3%A5r/", extractor.urlForQuery("röd hår", 1))
    }

    @Test
    fun listingFindsVideoLinksInTypicalMarkup() {
        val html = """
            <html><body>
              <a href="/videos/amateur-weekend-12345.html">
                <img data-src="https://cdn.example/t/1.jpg">
              </a>
              <span class="duration">08:15</span>
              <a href="/tags/amateur">Amateur</a>
              <a href="/login">Logga in</a>
            </body></html>
        """.trimIndent()

        val results = ExtractorSupport.parseListings(html, extractor.listingSpec)

        assertEquals(1, results.size)
        val hit = results.first()
        assertEquals("luxuretv", hit.sourceSite)
        assertEquals("https://en.luxuretv.com/videos/amateur-weekend-12345.html", hit.detailUrl)
        assertEquals(495, hit.durationSeconds)
        assertTrue("titeln fallbackar till slug", hit.title.isNotBlank())
    }
}
