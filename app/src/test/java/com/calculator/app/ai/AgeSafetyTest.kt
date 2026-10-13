package com.calculator.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeSafetyTest {

    @Test
    fun blocksAgeRelatedTermsInBothLanguages() {
        assertTrue(AgeSafety.containsBlockedTerm("teen"))
        assertTrue(AgeSafety.containsBlockedTerm("Teens at the beach"))
        assertTrue(AgeSafety.containsBlockedTerm("schoolgirl"))
        assertTrue(AgeSafety.containsBlockedTerm("barely legal"))
        assertTrue(AgeSafety.containsBlockedTerm("minderårig"))
        assertTrue(AgeSafety.containsBlockedTerm("tonåring"))
        assertTrue(AgeSafety.containsBlockedTerm("barn"))
        assertTrue(AgeSafety.containsBlockedTerm("preteen"))
    }

    @Test
    fun allowsOrdinaryAdultTerms() {
        assertFalse(AgeSafety.containsBlockedTerm("mature amateur"))
        assertFalse(AgeSafety.containsBlockedTerm("milf kitchen"))
        assertFalse(AgeSafety.containsBlockedTerm("brunette pov"))
        assertFalse(AgeSafety.containsBlockedTerm(""))
        assertFalse(AgeSafety.containsBlockedTerm("blonde"))
    }

    @Test
    fun wholeWordsOnlySoOrdinaryWordsAreNotBlockedByAccident() {
        // "kinder" innehåller "kid" som delsträng men ska inte blockeras som eget ord
        assertFalse(AgeSafety.containsBlockedTerm("kinder surprise"))
        // "youthful" ska inte heller slå igenom som "youth"
        assertFalse(AgeSafety.containsBlockedTerm("youthful"))
    }

    @Test
    fun blockedQueriesReturnAPlanWithReason() {
        val plan = SearchPlanner().plan("young couple")
        assertNotNull(plan.blockedReason)
        assertTrue(plan.queries.isEmpty())
        assertEquals(AgeSafety.BLOCKED_MESSAGE, plan.blockedReason)
    }

    @Test
    fun blockingAlsoAppliesToSelectedCategoryTerms() {
        val plan = SearchPlanner().plan("kitchen", categoryTerms = listOf("teen"))
        assertNotNull(plan.blockedReason)
    }

    @Test
    fun blankInputIsNotBlocked() {
        assertFalse(AgeSafety.containsBlockedTerm("   "))
        assertNull(SearchPlanner().plan("  ").blockedReason)
    }
}
