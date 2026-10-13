package com.calculator.app.data.extractor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteUrlPolicyTest {

    @Test
    fun acceptsTheDeclaredHostAndItsSubdomains() {
        assertTrue(SiteUrlPolicy.isSameSiteHttps(
            "https://videos.example.test/watch/abc",
            "https://example.test"
        ))
        assertTrue(SiteUrlPolicy.isSameSiteHttps(
            "https://example.test/watch/abc",
            "https://www.example.test"
        ))
    }

    @Test
    fun rejectsLookalikeAndUnrelatedHosts() {
        assertFalse(SiteUrlPolicy.isSameSiteHttps(
            "https://example.test.attacker.invalid/watch/abc",
            "https://example.test"
        ))
        assertFalse(SiteUrlPolicy.isSameSiteHttps(
            "https://attacker.invalid/watch/abc",
            "https://example.test"
        ))
    }

    @Test
    fun rejectsCleartextNonstandardPortsCredentialsAndMissingBase() {
        assertFalse(SiteUrlPolicy.isSameSiteHttps("http://example.test/video", "https://example.test"))
        assertFalse(SiteUrlPolicy.isSameSiteHttps("https://example.test:8443/video", "https://example.test"))
        assertFalse(SiteUrlPolicy.isSameSiteHttps("https://user@example.test/video", "https://example.test"))
        assertFalse(SiteUrlPolicy.isSameSiteHttps("https://example.test/video", null))
    }
}
