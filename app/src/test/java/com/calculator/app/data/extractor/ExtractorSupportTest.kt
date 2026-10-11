package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorSupportTest {

    private val xvideosConfig = ListingConfig(
        siteName = "xvideos",
        baseUrl = "https://www.xvideos.com",
        hrefPattern = Regex("/video\\.([A-Za-z0-9]+)"),
        titleSelectors = listOf("p.title", ".title", "a.title")
    )

    @Test
    fun parsesListingsFromStructuralMarkup() {
        val html = """
            <html><body>
              <div class="mozaique">
                <div class="thumb-block">
                  <a href="/video.abcdefg/blonde_in_the_kitchen" title="Blonde in the kitchen">
                    <img data-src="//img.example.com/thumb1.jpg" alt="Blonde in the kitchen">
                  </a>
                  <p class="title">Blonde in the kitchen</p>
                  <span class="duration">12:34</span>
                </div>
                <div class="thumb-block">
                  <a href="/video.xyz1234/brunette_amateur">
                    <img src="https://img.example.com/thumb2.jpg">
                  </a>
                  <span class="duration">05:00</span>
                </div>
                <div class="thumb-block">
                  <a href="/profiles/some-user">Inte en video</a>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val results = ExtractorSupport.parseListings(html, xvideosConfig)

        assertEquals(2, results.size)
        val first = results.first()
        assertEquals("xvideos_abcdefg", first.id)
        assertEquals("Blonde in the kitchen", first.title)
        assertEquals("https://www.xvideos.com/video.abcdefg/blonde_in_the_kitchen", first.detailUrl)
        assertEquals("https://img.example.com/thumb1.jpg", first.thumbnailUrl)
        assertEquals("12:34", first.duration)
        assertEquals(754, first.durationSeconds)

        val second = results[1]
        assertEquals("xvideos_xyz1234", second.id)
        assertEquals("Brunette amateur", second.title)
        assertEquals("https://img.example.com/thumb2.jpg", second.thumbnailUrl)
        assertEquals(300, second.durationSeconds)
    }

    @Test
    fun removesBrandSuffixesFromTitles() {
        assertEquals("Blonde in the kitchen", ExtractorSupport.cleanTitle("Blonde in the kitchen - XVideos.com"))
        assertEquals("Blonde fun", ExtractorSupport.cleanTitle("Blonde fun | Eporner"))
        assertEquals("Amateur couple - part 2", ExtractorSupport.cleanTitle("Amateur couple - part 2"))
        assertNull(ExtractorSupport.cleanTitle("   "))
        assertNull(ExtractorSupport.cleanTitle("ab"))
    }

    @Test
    fun detectsQualityHintsInTitles() {
        assertEquals("4K", ExtractorSupport.qualityFromTitle("Blonde kitchen 4K"))
        assertEquals("1080p", ExtractorSupport.qualityFromTitle("Amateur 1080p fun"))
        assertEquals("HD", ExtractorSupport.qualityFromTitle("HD blonde"))
        assertNull(ExtractorSupport.qualityFromTitle("Blonde kitchen"))
    }

    @Test
    fun pornhubStyleLinksAreParsed() {
        val config = ListingConfig(
            siteName = "pornhub",
            baseUrl = "https://www.pornhub.com",
            hrefPattern = Regex("viewkey=([A-Za-z0-9]+)"),
            requireHrefContains = "view_video"
        )
        val html = """
            <ul>
              <li class="pcVideoListItem">
                <a href="/view_video.php?viewkey=ph12345abcd" title="Kitchen fun">
                  <img data-mediumthumb="https://ci.example.com/1.jpg">
                </a>
                <var class="duration">08:15</var>
              </li>
            </ul>
        """.trimIndent()

        val results = ExtractorSupport.parseListings(html, config)

        assertEquals(1, results.size)
        assertEquals("pornhub_ph12345abcd", results.first().id)
        assertEquals(495, results.first().durationSeconds)
        assertEquals("https://ci.example.com/1.jpg", results.first().thumbnailUrl)
    }

    @Test
    fun duplicateIdsAreCollapsedAndBestThumbnailKept() {
        val html = """
            <div class="list">
              <div class="item"><a href="/video.abcdefg/title-one" title="Title one">Title one</a></div>
              <div class="item">
                <a href="/video.abcdefg/title-one"><img data-src="https://img.example.com/a.jpg" alt="Title one"></a>
              </div>
            </div>
        """.trimIndent()

        val results = ExtractorSupport.parseListings(html, xvideosConfig)

        assertEquals(1, results.size)
        assertEquals("https://img.example.com/a.jpg", results.first().thumbnailUrl)
    }

    @Test
    fun listingsSkipCleartextUrls() {
        val config = xvideosConfig.copy(baseUrl = "https://www.xvideos.com")
        val html = """<div><a href="http://www.xvideos.com/video.abcdefg/title">T</a></div>"""

        assertTrue(ExtractorSupport.parseListings(html, config).isEmpty())
    }

    @Test
    fun siteExtractorsExposeFriendlyNames() {
        assertNotNull(EpornerExtractor().siteName)
        assertEquals("Eporner", EpornerExtractor().label)
        assertEquals("SpankBang", SpankBangExtractor().label)
        assertEquals("xHamster", XHamsterExtractor().label)
        assertEquals("PornTrex", PornTrexExtractor().label)
        assertFalse(BeegExtractor().supportsPagination)
        assertTrue(SpankBangExtractor().supportsPagination)
    }
}
