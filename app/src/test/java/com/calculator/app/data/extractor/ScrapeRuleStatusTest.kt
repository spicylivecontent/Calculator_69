package com.calculator.app.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrapeRuleStatusTest {
    @Test
    fun builtInStatusDoesNotClaimAnOutdatedSiteCount() {
        val status = ScrapeRuleStore.Status(
            source = ScrapeRuleStore.Source.BUILT_IN,
            version = 0,
            sites = 0,
            fetchedAt = 0L
        )

        assertEquals("Inbyggda regler", status.label())
    }
}
