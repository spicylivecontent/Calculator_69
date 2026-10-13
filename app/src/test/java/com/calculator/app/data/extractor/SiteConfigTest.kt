package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vaktar sajtkonfigurationen: skrivs en URL eller ett mönster fel (eller byter en sajt
 * adress) ska CI säga till innan användaren får noll träffar.
 */
class SiteConfigTest {

    private val extractors: List<BaseSiteExtractor> = listOf(
        EpornerExtractor(),
        SpankBangExtractor(),
        HQpornerExtractor(),
        XVideosExtractor(),
        XnxxExtractor(),
        XHamsterExtractor(),
        PornhubExtractor(),
        BeegExtractor(),
        YouPornExtractor(),
        PornTrexExtractor()
    )

    /** Ett typiskt videolänkmönster per sajt, som sajtens regex måste matcha. */
    private val sampleLinks = mapOf(
        "eporner" to "/video-ABC1234/blonde_kitchen/",
        "spankbang" to "/a1b2c3d4/video",
        "hqporner" to "/hd/blonde-kitchen-clip",
        "xvideos" to "/video.abcdefghij/blonde_kitchen",
        "xnxx" to "/video-abcdefghi/blonde_kitchen",
        "xhamster" to "/videos/blonde-kitchen-123456",
        "pornhub" to "/view_video.php?viewkey=ph1234abcd",
        "beeg" to "/12345678",
        "youporn" to "/watch/12345678/blonde-kitchen/",
        "porntrex" to "/video/123456/blonde-kitchen/"
    )

    @Test
    fun everySiteIsRegisteredWithAUniqueIdAndLabel() {
        assertEquals("alla tio sajter ska finnas", 10, extractors.size)

        val ids = extractors.map { it.siteName }
        assertEquals("id:n måste vara unika", ids.size, ids.distinct().size)

        for (extractor in extractors) {
            assertTrue("tomt id", extractor.siteName.isNotBlank())
            assertTrue("tom etikett för ${extractor.siteName}", extractor.label.isNotBlank())
        }
    }

    @Test
    fun searchUrlsAreHttpsAndKeepTheQuery() {
        for (extractor in extractors) {
            val url = extractor.urlForQuery("blonde kitchen", 2)

            assertTrue("${extractor.siteName} använder inte https: $url", url.startsWith("https://"))
            assertTrue("${extractor.siteName} innehåller tomrum: $url", !url.contains(" "))
            assertTrue(
                "${extractor.siteName} tappar sökordet: $url",
                url.contains("blonde") && url.contains("kitchen")
            )
        }
    }

    @Test
    fun listingPatternsMatchTheirOwnVideoLinks() {
        for (extractor in extractors) {
            val sample = sampleLinks[extractor.siteName]
                ?: error("ingen exempellänk för ${extractor.siteName}")
            val config = extractor.listingSpec

            assertTrue(
                "${extractor.siteName}: mönstret ${config.hrefPattern} matchar inte $sample",
                config.hrefPattern.containsMatchIn(sample)
            )
            config.requireHrefContains?.let { required ->
                assertTrue(
                    "${extractor.siteName}: kravet '$required' stämmer inte med $sample",
                    sample.contains(required)
                )
            }
            assertTrue(
                "${extractor.siteName}: id-gruppen ${config.idGroup} finns inte i mönstret",
                config.hrefPattern.find(sample)!!.groupValues.size > config.idGroup
            )
        }
    }

    @Test
    fun listingConfigsAreConsistent() {
        for (extractor in extractors) {
            val config = extractor.listingSpec

            assertEquals("${extractor.siteName}: fel sajtnamn i konfigurationen", extractor.siteName, config.siteName)
            assertTrue("${extractor.siteName}: bas-URL måste vara https", config.baseUrl.startsWith("https://"))
            assertTrue("${extractor.siteName}: limit utanför rimligt intervall", config.limit in 1..100)
            assertTrue("${extractor.siteName}: idGroup måste vara >= 0", config.idGroup >= 0)
            assertTrue("${extractor.siteName}: saknar titelselektorer", config.titleSelectors.isNotEmpty())
            assertTrue("${extractor.siteName}: saknar tumnagelattribut", config.thumbAttributes.isNotEmpty())
        }
    }

    @Test
    fun beegUsesItsOwnApiForStreamsAndStillWorksForListings() {
        val beeg = BeegExtractor()
        assertEquals("beeg", beeg.siteName)
        assertTrue(beeg.urlForQuery("blonde", 1).startsWith("https://beeg.com/"))
    }

    @Test
    fun onlyKnownSitesAreRegistered() {
        val registered = extractors.map { it.siteName }.toSet()

        assertTrue(
            "saknade sajter i registreringen: ${sampleLinks.keys - registered}",
            registered.containsAll(sampleLinks.keys)
        )
        assertEquals("nya sajter ska också ha en exempellänk", sampleLinks.keys, registered)
    }
}
