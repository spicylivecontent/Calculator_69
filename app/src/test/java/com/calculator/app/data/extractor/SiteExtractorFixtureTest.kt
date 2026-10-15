package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Offline HTML fixtures for every built-in site adapter; no live website dependency. */
class SiteExtractorFixtureTest {

    @Test
    fun everyBuiltInExtractorParsesItsOwnSiteFixture() {
        val fixtures = listOf(
            BeegExtractor() to "/12345",
            EpornerExtractor() to "/video-abc123",
            HQpornerExtractor() to "/video/abc123",
            LuxureTvExtractor() to "/videos/sample-video-12345.html",
            PornTrexExtractor() to "/video/123456",
            PornhubExtractor() to "/view_video.php?viewkey=abc123",
            SpankBangExtractor() to "/abcd1234/video",
            XHamsterExtractor() to "/videos/sample-video-123",
            XVideosExtractor() to "/video.abc123",
            XnxxExtractor() to "/video-abc123",
            YouPornExtractor() to "/watch/123456"
        )

        assertEquals("Expected a fixture for every built-in site", 11, fixtures.size)
        assertEquals("Built-in site IDs must be unique", 11, fixtures.map { it.first.siteName }.distinct().size)

        fixtures.forEach { (extractor, path) ->
            val config = extractor.listingSpec
            val html = """
                <html><body>
                  <article class="video-card">
                    <a href="$path" title="Fixture video for ${extractor.siteName}">
                      <img src="/thumb.jpg" alt="Fixture thumbnail">
                    </a>
                    <span class="duration">12:34</span>
                  </article>
                </body></html>
            """.trimIndent()

            val results = ExtractorSupport.parseListings(html, config)
            assertEquals("Fixture should yield one result for ${extractor.siteName}", 1, results.size)
            val result = results.single()
            assertEquals(extractor.siteName, result.sourceSite)
            assertEquals("Fixture video for ${extractor.siteName}", result.title)
            assertEquals("https://${config.baseUrl.removePrefix("https://").trimEnd('/')}${path}", result.detailUrl)
            assertFalse("Fixture URL must not escape its site's origin", result.detailUrl.contains("attacker.example"))
        }
    }

    @Test
    fun everyBuiltInExtractorBuildsASecondPageUrl() {
        val extractors = listOf(
            BeegExtractor(), EpornerExtractor(), HQpornerExtractor(), LuxureTvExtractor(),
            PornTrexExtractor(), PornhubExtractor(), SpankBangExtractor(), XHamsterExtractor(),
            XVideosExtractor(), XnxxExtractor(), YouPornExtractor()
        )

        extractors.filter { it.supportsPagination }.forEach { extractor ->
            val secondPage = extractor.urlForQuery("two words", 2)
            assertEquals("https", secondPage.substringBefore("://"))
            assertEquals(true, secondPage.contains("two") || secondPage.contains("two%20words") || secondPage.contains("two+words"))
        }
        assertEquals(false, BeegExtractor().supportsPagination)
    }
}
