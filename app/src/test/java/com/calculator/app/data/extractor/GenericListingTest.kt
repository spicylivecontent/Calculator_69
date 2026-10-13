package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reservparsningen ska rädda en sajt vars klassnamn eller URL-mönster ändrats,
 * utan att förväxla navigering, taggar eller bilder med videosidor.
 */
class GenericListingTest {

    private val baseUrl = "https://mysite.example/"

    @Test
    fun findsVideoLinksEvenWithoutClassNamesOrPatterns() {
        val html = """
            <html><body>
              <header><a href="/">Hem</a><a href="/login">Logga in</a></header>
              <main>
                <div>
                  <a href="/video/blonde-in-the-kitchen-2024">
                    <img data-src="https://cdn.example.com/t/1.jpg">
                  </a>
                  <span class="whatever">12:34</span>
                </div>
                <article>
                  <a href="/watch/massage-amateur-checkup/">Massage Amateur Checkup</a>
                </article>
              </main>
              <footer><a href="/dmca">DMCA</a><a href="/tag/blonde">Blonde</a></footer>
            </body></html>
        """.trimIndent()

        val results = ExtractorSupport.parseListingsGeneric(html, baseUrl, "mysite")

        assertEquals(2, results.size)
        val first = results.first()
        assertEquals("mysite", first.sourceSite)
        assertTrue(first.detailUrl.startsWith("https://mysite.example/"))
        assertTrue(first.title.isNotBlank())
        assertTrue("längden ska läsas från omgivningen", first.durationSeconds == 754)
    }

    @Test
    fun navigationTagsAndMediaFilesAreNotTreatedAsVideos() {
        val html = """
            <html><body>
              <a href="/tag/blonde-kitchen">Blonde kitchen</a>
              <a href="/category/amateur-videos">Amateur videos</a>
              <a href="/model/anna-andersson">Anna Andersson</a>
              <a href="/video/stream-master.m3u8">Stream</a>
              <a href="https://cdn.example.com/v/clip-fun.mp4">Clip</a>
              <a href="/login-page">Logga in här</a>
              <a href="javascript:void(0)">Klicka</a>
            </body></html>
        """.trimIndent()

        assertTrue(ExtractorSupport.parseListingsGeneric(html, baseUrl, "mysite").isEmpty())
    }

    @Test
    fun hrefHeuristicsAcceptSlugsAndNumericIds() {
        assertTrue(ExtractorSupport.looksLikeVideoHref("/video/blonde-kitchen-amateur"))
        assertTrue(ExtractorSupport.looksLikeVideoHref("/1234567"))
        assertTrue(ExtractorSupport.looksLikeVideoHref("https://mysite.example/watch/massage-clip-8341"))

        assertFalse(ExtractorSupport.looksLikeVideoHref("/tag/blonde"))
        assertFalse(ExtractorSupport.looksLikeVideoHref("/"))
        assertFalse(ExtractorSupport.looksLikeVideoHref("/v/short"))
        assertFalse(ExtractorSupport.looksLikeVideoHref("javascript:alert(1)"))
        assertFalse(ExtractorSupport.looksLikeVideoHref(null))
    }

    @Test
    fun theSameVideoIsOnlyReturnedOnce() {
        val html = """
            <html><body>
              <div><a href="/video/abc-def-123"><img src="https://cdn.example.com/a.jpg"></a></div>
              <div><a href="/video/abc-def-123">Samma klipp igen</a></div>
            </body></html>
        """.trimIndent()

        val results = ExtractorSupport.parseListingsGeneric(html, baseUrl, "mysite")

        assertEquals(1, results.size)
        assertEquals("mysite_abc-def-123", results.first().id)
    }

    @Test
    fun cleartextLinksAreNeverTurnedIntoResults() {
        val html = """
            <html><body>
              <a href="http://insecure.example/video/blonde-kitchen-clip">Blonde kitchen clip</a>
            </body></html>
        """.trimIndent()

        assertTrue(ExtractorSupport.parseListingsGeneric(html, baseUrl, "mysite").isEmpty())
    }

    @Test
    fun theConfiguredParserStillWinsWhenItWorks() {
        val config = ListingConfig(
            siteName = "mysite",
            baseUrl = baseUrl,
            hrefPattern = Regex("/video/([a-z0-9-]+)")
        )
        val html = """
            <html><body>
              <div class="item">
                <a href="/video/blonde-kitchen-abc"><img data-src="https://cdn.example.com/t/abc.jpg"></a>
                <span class="duration">10:00</span>
              </div>
              <div class="item">
                <a href="/video/massage-clip-def">Massage clip def</a>
              </div>
            </body></html>
        """.trimIndent()

        val configured = ExtractorSupport.parseListings(html, config)
        val generic = ExtractorSupport.parseListingsGeneric(html, baseUrl, "mysite")

        assertEquals(2, configured.size)
        assertEquals("båda vägarna ska hitta samma två klipp", configured.size, generic.size)
    }
}
