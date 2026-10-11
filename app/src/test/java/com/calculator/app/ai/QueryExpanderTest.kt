package com.calculator.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryExpanderTest {

    private val expander = QueryExpander()

    @Test
    fun blankInputReturnsAnEmptyQuery() {
        val result = expander.expand("   ")

        assertEquals("", result.searchTerms)
        assertTrue(result.negativeKeywords.isEmpty())
        assertEquals("Tom sökfras", result.explanation)
        assertEquals(null, result.blockedReason)
    }

    @Test
    fun expandsSafeTermsAndExtractsNegativeKeywords() {
        val result = expander.expand("brunette pov no anal")

        assertEquals("brunette brown hair dark hair pov point of view", result.searchTerms)
        assertEquals(listOf("anal"), result.negativeKeywords)
        assertEquals(null, result.blockedReason)
    }

    @Test
    fun negativeOnlySearchDoesNotSendTheExclusionPhraseAsAQuery() {
        val result = expander.expand("absolut inte anal")

        assertEquals("", result.searchTerms)
        assertEquals(listOf("anal"), result.negativeKeywords)
    }

    @Test
    fun blocksSearchesWithAgeRelatedTerms() {
        val result = expander.expand("young adult video")

        assertEquals("", result.searchTerms)
        assertNotNull(result.blockedReason)
        assertTrue(result.explanation.contains("stoppades"))
    }

    @Test
    fun blocksSwedishAgeRelatedTermsWithoutBlockingOrdinaryQueries() {
        assertNotNull(expander.expand("tonåring").blockedReason)
        assertFalse(expander.expand("mature amateur").blockedReason != null)
        assertTrue(expander.containsBlockedAgeTerm("Teen content"))
        assertFalse(expander.containsBlockedAgeTerm("Mature amateur"))
    }
}
