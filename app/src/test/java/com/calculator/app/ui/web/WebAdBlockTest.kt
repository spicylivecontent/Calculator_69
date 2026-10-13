package com.calculator.app.ui.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAdBlockTest {

    private val site = "https://www.mytube.example/video/abc123"

    @Test
    fun blocksKnownAdAndTrackerHosts() {
        assertTrue(WebAdBlock.isBlockedRequest("https://exoclick.com/ads.js"))
        assertTrue(WebAdBlock.isBlockedRequest("https://s1.exoclick.com/x?y=1"))
        assertTrue(WebAdBlock.isBlockedRequest("https://cdn.trafficjunky.com/script.js"))
        assertTrue(WebAdBlock.isBlockedRequest("https://www.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(WebAdBlock.isBlockedRequest("https://google-analytics.com/collect"))
    }

    @Test
    fun blocksTypicalAdPathsOnAnyHost() {
        assertTrue(WebAdBlock.isBlockedRequest("https://mytube.example/popunder.js"))
        assertTrue(WebAdBlock.isBlockedRequest("https://mytube.example/ads.php?zone=12"))
        assertTrue(WebAdBlock.isBlockedRequest("https://mytube.example/vast.xml"))
    }

    @Test
    fun allowsTheSitesOwnPagesAndMedia() {
        assertFalse(WebAdBlock.isBlockedRequest(site))
        assertFalse(WebAdBlock.isBlockedRequest("https://cdn.mytube.example/media/master.m3u8"))
        assertFalse(WebAdBlock.isBlockedRequest("https://mytube.example/video/abc123"))
    }

    @Test
    fun offLevelDisablesAdFilteringButKeepsNavigationGuard() {
        assertFalse(WebAdBlock.isBlockedRequest("https://exoclick.com/ads.js", AdBlockLevel.OFF))
        assertEquals(
            NavigationDecision.EXTERNAL,
            WebAdBlock.navigationFor(site, "https://another-site.example/watch", AdBlockLevel.OFF)
        )
        assertEquals(
            NavigationDecision.ALLOW,
            WebAdBlock.navigationFor(site, "https://another-site.example/movie.mp4", AdBlockLevel.OFF)
        )
    }

    @Test
    fun sameSiteNavigationIsAllowed() {
        assertEquals(NavigationDecision.ALLOW, WebAdBlock.navigationFor(site, site))
        assertEquals(
            NavigationDecision.ALLOW,
            WebAdBlock.navigationFor(site, "https://www.mytube.example/tag/blonde")
        )
    }

    @Test
    fun adAndPopunderNavigationIsBlocked() {
        assertEquals(
            NavigationDecision.BLOCK,
            WebAdBlock.navigationFor(site, "https://exoclick.com/click?ad=1")
        )
        assertEquals(
            NavigationDecision.BLOCK,
            WebAdBlock.navigationFor(site, "https://mytube.example/popunder.js")
        )
        assertEquals(
            NavigationDecision.BLOCK,
            WebAdBlock.navigationFor(site, "http://insecure.example/video")
        )
        assertEquals(
            NavigationDecision.BLOCK,
            WebAdBlock.navigationFor(site, "intent://play/#Intent;end")
        )
    }

    @Test
    fun otherSitesAreOpenedOutsideTheApp() {
        assertEquals(
            NavigationDecision.EXTERNAL,
            WebAdBlock.navigationFor(site, "https://somewhere-else.example/page")
        )
        assertEquals(
            NavigationDecision.BLOCK,
            WebAdBlock.navigationFor(site, "https://somewhere-else.example/page", AdBlockLevel.STRICT)
        )
    }

    @Test
    fun mediaFromAnotherHostIsStillAllowed() {
        assertEquals(
            NavigationDecision.ALLOW,
            WebAdBlock.navigationFor(site, "https://edge-cdn.example/videos/master.m3u8")
        )
    }

    @Test
    fun sameSiteRecognisesSubdomainsAndMultiPartSuffixes() {
        assertTrue(WebAdBlock.sameSite("www.mytube.example", "cdn.mytube.example"))
        assertTrue(WebAdBlock.sameSite("mytube.co.uk", "videos.mytube.co.uk"))
        assertFalse(WebAdBlock.sameSite("www.mytube.example", "other.example"))
        assertFalse(WebAdBlock.sameSite("", "mytube.example"))
    }

    @Test
    fun injectionScriptKillsPopupsAndAddsHelpers() {
        val script = WebAdBlock.injectionScript(AdBlockLevel.STANDARD)

        assertTrue(script.contains("window.open=function(){return null;}"))
        assertTrue(script.contains("__pcSeekBy"))
        assertTrue(script.contains("__pcState"))
        assertTrue(script.contains("__pcResume"))
        assertTrue(script.contains("__pcToggle"))
        assertTrue(script.contains("display: none !important"))
        assertTrue(script.contains("'standard'"))
    }

    @Test
    fun strictModeBlocksExternalResourcesNotOnlyNavigation() {
        assertTrue(WebAdBlock.isBlockedRequest("https://other.example/video.mp4", AdBlockLevel.STRICT, site))
        assertFalse(WebAdBlock.isBlockedRequest("https://cdn.mytube.example/video.mp4", AdBlockLevel.STRICT, site))
    }

    @Test
    fun mediaDetectionDoesNotTrustQueryParameters() {
        assertFalse(WebAdBlock.isMedia("https://other.example/landing?to=movie.mp4"))
        assertEquals(NavigationDecision.EXTERNAL, WebAdBlock.navigationFor(site, "https://other.example/landing?to=movie.mp4"))
    }

    @Test
    fun publicSuffixTenantsAndIpAddressesAreNotTheSameSite() {
        assertFalse(WebAdBlock.sameSite("first.github.io", "second.github.io"))
        assertFalse(WebAdBlock.sameSite("10.0.0.1", "192.168.0.1"))
        assertTrue(WebAdBlock.sameSite("FIRST.COM", "cdn.first.com"))
    }

    @Test
    fun offDisablesCosmeticBlockingButNeverAllowsCleartext() {
        assertFalse(WebAdBlock.injectionScript(AdBlockLevel.OFF).contains("display: none !important"))
        assertTrue(WebAdBlock.isBlockedRequest("http://example.com/video.mp4", AdBlockLevel.OFF))
    }

    @Test
    fun javascriptStringsAreEscaped() {
        val value = WebAdBlock.jsString("en\"rad\\med\ntecken")

        assertFalse(value.contains("\n"))
        assertTrue(value.startsWith("\""))
        assertTrue(value.endsWith("\""))
        assertTrue(value.contains("\\\""))
    }
}
