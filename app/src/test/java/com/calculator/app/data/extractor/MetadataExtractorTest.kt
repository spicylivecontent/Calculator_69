package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataExtractorTest {

    private val richPage = """
        <html>
        <head>
          <title>Blonde in the Kitchen - MyTube</title>
          <meta property="og:title" content="Blonde in the Kitchen">
          <meta property="og:description" content="Amateur clip where she cooks, laughs and gets caught in the kitchen.">
          <meta name="keywords" content="kitchen,amateur,cooking">
          <meta property="og:video:height" content="1080">
          <meta property="video:duration" content="754">
          <meta itemprop="uploadDate" content="2024-03-11T09:00:00Z">
          <script type="application/ld+json">
          {
            "@context": "https://schema.org",
            "@type": "VideoObject",
            "name": "Blonde in the Kitchen",
            "description": "She starts by cutting vegetables, then things get wild on the counter.",
            "duration": "PT12M34S",
            "keywords": "kitchen, blonde, amateur",
            "genre": ["Reality", "Amateur"],
            "actor": [{"@type": "Person", "name": "Anna Andersson"}],
            "interactionStatistic": {"@type": "InteractionCounter", "userInteractionCount": 12345}
          }
          </script>
        </head>
        <body>
          <h1>Blonde in the Kitchen</h1>
          <div class="video-tags">
            <a href="/tag/blonde/">Blonde</a>
            <a href="/tag/kitchen/">Kitchen</a>
            <a href="/pornstar/anna-andersson/">Anna Andersson</a>
            <a href="/category/amateur/">Amateur</a>
            <a href="/login">Log in</a>
            <a href="/tag/">Home</a>
          </div>
          <span class="views">12,345 views</span>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun isoDurationsIncludeDaysAndRejectOverflow() {
        assertEquals(90000, MetadataExtractor.parseIsoDuration("P1DT1H"))
        assertNull(MetadataExtractor.parseIsoDuration("PT999999999H"))
        assertNull(MetadataExtractor.parseIsoDuration("P1M"))
    }

    @Test
    fun parsesTitleDescriptionTagsAndQuality() {
        val metadata = MetadataExtractor.parse(richPage, "https://mytube.example/video/abc")

        assertEquals("Blonde in the Kitchen", metadata.title)
        assertNotNull(metadata.description)
        assertTrue(metadata.description!!.contains("cooks"))
        assertTrue(metadata.tags.contains("Blonde"))
        assertTrue(metadata.tags.contains("Kitchen"))
        assertEquals("1080p", metadata.qualityLabel)
        assertTrue(metadata.keywords.any { it.equals("Anna Andersson", ignoreCase = true) })
        assertFalse("navigeringslänkar ska inte bli taggar", metadata.keywords.any { it.equals("Log in", ignoreCase = true) })
        assertFalse(metadata.keywords.any { it.equals("Home", ignoreCase = true) })
    }

    @Test
    fun readsDurationFromMetaAndStructuredData() {
        val metadata = MetadataExtractor.parse(richPage, "https://mytube.example/video/abc")
        assertEquals(754, metadata.durationSeconds)
    }

    @Test
    fun keywordsAreSeparatedFromPerformersAndCategories() {
        val metadata = MetadataExtractor.parse(richPage, "https://mytube.example/video/abc")

        assertTrue("medverkande ska hamna i performers", metadata.performers.any { it.contains("Anna") })
        assertTrue("kategorier ska hamna i categories", metadata.categories.any { it.equals("Amateur", ignoreCase = true) })
        assertTrue(metadata.hasRichData)
    }

    @Test
    fun alternativeMetaNamesAreUsedWhenOpenGraphIsMissing() {
        val page = """
            <html><head>
            <meta name="description" content="Kort beskrivning av filmen som är tillräckligt lång.">
            <meta name="twitter:title" content="Twitter-rubrik">
            </head>
            <body><div class="quality">HD 720p</div></body></html>
        """.trimIndent()

        val metadata = MetadataExtractor.parse(page, "https://mytube.example/video/xyz")

        assertEquals("Twitter-rubrik", metadata.title)
        assertTrue(metadata.description!!.contains("Kort beskrivning"))
        assertEquals("720p", metadata.qualityLabel)
    }

    @Test
    fun removedPagesAreFlagged() {
        val page = "<html><body><h1>This video is unavailable</h1></body></html>"

        val metadata = MetadataExtractor.parse(page, "https://mytube.example/video/dead")

        assertTrue(metadata.pageMissing)
    }

    @Test
    fun blankHtmlIsTreatedAsMissing() {
        assertTrue(MetadataExtractor.parse("", "https://mytube.example/v").pageMissing)
    }

    @Test
    fun isoDurationsAreParsed() {
        assertEquals(3_723, MetadataExtractor.parseIsoDuration("PT1H2M3S"))
        assertEquals(754, MetadataExtractor.parseIsoDuration("PT12M34S"))
        assertEquals(45, MetadataExtractor.parseIsoDuration("PT45S"))
        assertNull(MetadataExtractor.parseIsoDuration("12:34"))
        assertNull(MetadataExtractor.parseIsoDuration(null))
    }

    @Test
    fun searchableTextCoversTitleTagsAndDescription() {
        val metadata = MetadataExtractor.parse(richPage, "https://mytube.example/video/abc")
        val text = metadata.searchableText.lowercase()

        assertTrue(text.contains("kitchen"))
        assertTrue(text.contains("blonde"))
        assertTrue(text.contains("cooks"))

        assertEquals(1f, metadata.coverage(listOf("blonde", "kitchen")), 0.001f)
        assertTrue(metadata.coverage(listOf("blonde", "motorcycle")) < 1f)
        assertTrue(metadata.matchedTerms(listOf("blonde", "motorcycle")).contains("blonde"))
    }

    @Test
    fun vocabularyNeverGrowsBeyondTheLimit() {
        val anchors = (1..200).joinToString("") { "<a href=\"/tag/tag$it\">Tagg $it</a>" }
        val metadata = MetadataExtractor.parse("<html><body>$anchors</body></html>", "https://x.example/v/1")

        assertTrue(metadata.tags.size <= 40)
    }
}
