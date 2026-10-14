package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorSupportTest {

    private val config = ListingConfig(
        siteName = "mysite",
        baseUrl = "https://mysite.example/",
        hrefPattern = Regex("/video/([a-z0-9]+)"),
        idGroup = 1,
        requireHrefContains = "/video/"
    )

    private val listingHtml = """
        <html><body>
          <div class="item">
            <a href="/video/abc123" title="Blonde in the Kitchen | Pornhub">
              <img data-src="https://cdn.example.com/t/abc123.jpg" alt="Blonde in the Kitchen">
            </a>
            <span class="duration">12:34</span>
          </div>
          <div class="item">
            <a href="http://insecure.example.com/video/def456">Ignored because of http</a>
          </div>
          <div class="item">
            <a href="https://attacker.example/video/evil789">Off-site HTTPS detail link</a>
          </div>
          <div class="item">
            <a href="/video/abc123">
              <img src="https://cdn.example.com/t/abc123-hd.jpg">
            </a>
          </div>
          <div class="item">
            <a href="/tag/blonde">Tag link that should be ignored</a>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun listingsAreParsedIntoPlayableCandidates() {
        val results = ExtractorSupport.parseListings(listingHtml, config)

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals("mysite_abc123", result.id)
        assertEquals("Blonde in the Kitchen", result.title)
        assertEquals("https://mysite.example/video/abc123", result.detailUrl)
        assertEquals("mysite", result.sourceSite)
        assertEquals("https://cdn.example.com/t/abc123.jpg", result.thumbnailUrl)
        assertEquals("12:34", result.duration)
        assertEquals(754, result.durationSeconds)
    }

    @Test
    fun titleAndDurationCanBeReadFromDataAttributes() {
        val html = """
            <div class="video-card" data-duration="01:25">
              <a href="/video/xyz789" data-title="A title stored only in data-title">
                <img data-src="/thumb.jpg" alt="thumbnail">
              </a>
            </div>
        """.trimIndent()

        val result = ExtractorSupport.parseListings(html, config).single()
        assertEquals("A title stored only in data-title", result.title)
        assertEquals("01:25", result.duration)
        assertEquals(85, result.durationSeconds)
    }

    @Test
    fun emptyOrUnrelatedHtmlGivesNoResults() {
        assertTrue(ExtractorSupport.parseListings("", config).isEmpty())
        assertTrue(ExtractorSupport.parseListings("<html><body>Inget här</body></html>", config).isEmpty())
    }

    @Test
    fun cleanTitleRemovesBrandSuffixesAndJunk() {
        assertEquals("Blonde in the Kitchen", ExtractorSupport.cleanTitle("Blonde in the Kitchen | Pornhub"))
        assertEquals("Blonde in the Kitchen", ExtractorSupport.cleanTitle("Blonde in the Kitchen — xvideos.com"))
        assertEquals("Kitchen Fun", ExtractorSupport.cleanTitle("  Kitchen Fun  "))
        assertNull(ExtractorSupport.cleanTitle("ab"))
        assertNull(ExtractorSupport.cleanTitle("   "))
        assertNull(ExtractorSupport.cleanTitle(null))
    }

    @Test
    fun slugFallbackBuildsAReadableTitle() {
        assertEquals(
            "Blonde in the kitchen",
            ExtractorSupport.titleFromSlug("/video/blonde_in_the_kitchen_2024")
        )
        assertNull(ExtractorSupport.titleFromSlug("/v/ab"))
    }

    @Test
    fun qualityIsDetectedFromTheTitle() {
        assertEquals("1080p", ExtractorSupport.qualityFromTitle("Blonde kitchen 1080p"))
        assertEquals("4K", ExtractorSupport.qualityFromTitle("Amateur 4K clip"))
        assertNull(ExtractorSupport.qualityFromTitle("Blonde kitchen"))
    }

    @Test
    fun genericListingsRejectOffSiteDetailLinks() {
        val html = """
            <a href="https://attacker.example/video/blonde-in-kitchen-123">Bad host</a>
            <a href="https://www.mysite.example/video/blonde-in-kitchen-456">Trusted subdomain</a>
        """.trimIndent()

        val results = ExtractorSupport.parseListingsGeneric(
            html = html,
            baseUrl = "https://mysite.example",
            siteName = "mysite"
        )

        assertEquals(1, results.size)
        assertEquals("https://www.mysite.example/video/blonde-in-kitchen-456", results.single().detailUrl)
    }

    @Test
    fun absoluteUrlRejectsCleartext() {
        assertEquals(
            "https://mysite.example/video/abc",
            ExtractorSupport.absoluteUrl("https://mysite.example/", "/video/abc")
        )
        assertNull(ExtractorSupport.absoluteUrl("https://mysite.example/", "http://mysite.example/video/abc"))
    }
}
