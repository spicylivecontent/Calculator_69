package com.calculator.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPlannerTest {

    private val planner = SearchPlanner()

    @Test
    fun blankInputWithoutCategoriesGivesAHint() {
        val plan = planner.plan("   ")

        assertTrue(plan.queries.isEmpty())
        assertTrue(plan.negatives.isEmpty())
        assertEquals("Skriv ett sökord eller välj en kategori.", plan.explanation)
        assertEquals(null, plan.blockedReason)
    }

    @Test
    fun mainQueryKeepsEveryPositiveTerm() {
        val plan = planner.plan("brunette kitchen amateur")

        assertFalse(plan.queries.isEmpty())
        assertEquals("brunette kitchen amateur", plan.queries.first().text)
        assertTrue(plan.queries.first().isPrimary)
    }

    @Test
    fun searchVariantsCoverEverySingleTerm() {
        val plan = planner.plan("brunette kitchen amateur")

        val texts = plan.queries.map { it.text }
        assertTrue("saknar variant för kitchen: $texts", texts.contains("kitchen"))
        assertTrue("saknar variant för amateur: $texts", texts.contains("amateur"))
        assertTrue(texts.size >= 3)
    }

    @Test
    fun negativePhrasesBecomeFiltersInsteadOfSearchTerms() {
        val plan = planner.plan("brunette pov no anal")

        assertEquals("brunette pov", plan.queries.first().text)
        assertEquals(listOf("anal"), plan.negatives)
        assertFalse(plan.queries.any { it.text.contains("anal") || it.text.contains("no") })
    }

    @Test
    fun dashPrefixIsTreatedAsExclusion() {
        val plan = planner.plan("massage -anal")

        assertEquals("massage", plan.queries.first().text)
        assertEquals(listOf("anal"), plan.negatives)
    }

    @Test
    fun explicitExclusionFieldIsMerged() {
        val plan = planner.plan("massage", extraNegatives = listOf("anal", "feet"))

        assertTrue(plan.negatives.containsAll(listOf("anal", "feet")))
    }

    @Test
    fun exclusionOnlySearchAsksForAPositiveTerm() {
        val plan = planner.plan("absolut inte anal")

        assertTrue(plan.queries.isEmpty())
        assertEquals(listOf("anal"), plan.negatives)
        assertTrue(plan.explanation.contains("positivt sökord"))
    }

    @Test
    fun swedishTermsAreTranslatedToEnglishSearchTerms() {
        assertEquals("brunette", planner.plan("brunett").queries.first().text)
        assertEquals("kitchen", planner.plan("kök").queries.first().text)
        assertEquals("outdoor", planner.plan("utomhus").queries.first().text)

        val redHairPlan = planner.plan("rött hår")
        assertEquals("redhead", redHairPlan.queries.first().text)
        assertTrue(redHairPlan.queries.map { it.text }.contains("red hair"))
    }

    @Test
    fun synonymVariantsAreAddedButCapped() {
        val plan = planner.plan("brunette", maxQueries = 5)

        assertTrue(plan.queries.map { it.text }.contains("brown hair"))
    }

    @Test
    fun quotedPhrasesAreKeptTogether() {
        val plan = planner.plan("\"on her knees\" kitchen")

        assertEquals("kitchen", plan.queries.first().text)
        assertTrue(plan.queries.any { it.text == "on her knees" })
    }

    @Test
    fun selectedCategoriesBecomeBothMainQueryAndVariants() {
        val plan = planner.plan("", categoryTerms = listOf("blonde", "kitchen"))

        assertEquals("blonde kitchen", plan.queries.first().text)
        val texts = plan.queries.map { it.text }
        assertTrue(texts.contains("blonde"))
        assertTrue(texts.contains("kitchen"))
    }

    @Test
    fun numberOfQueriesIsCapped() {
        val plan = planner.plan("blonde brunette redhead kitchen outdoor", maxQueries = 2)

        assertEquals(2, plan.queries.size)
    }

    @Test
    fun termsContainEverythingSearchedFor() {
        val plan = planner.plan("blonde kitchen", categoryTerms = listOf("outdoor"))

        assertTrue(plan.terms.contains("blonde"))
        assertTrue(plan.terms.contains("kitchen"))
        assertTrue(plan.terms.contains("outdoor"))
    }

    @Test
    fun explanationListsQueriesAndFilters() {
        val plan = planner.plan("blonde no anal")

        val explanation = plan.explanation
        assertNotNull(explanation)
        assertTrue(explanation.contains("Söker:"))
        assertTrue(explanation.contains("anal"))
    }

    @Test
    fun quotedNegativesNeverBecomePositiveSearches() {
        for (input in listOf("kitchen without \"black hair\"", "kitchen -\"black hair\"")) {
            val plan = planner.plan(input)
            assertEquals(listOf("black hair"), plan.negatives)
            assertTrue(plan.queries.all { it.text == "kitchen" })
        }
    }

    @Test
    fun exclusionsAlsoApplyToCategoriesAndSynonymVariants() {
        val plan = planner.plan("brunette", categoryTerms = listOf("Kitchen"), extraNegatives = listOf("KITCHEN", "brown hair"))
        assertTrue(plan.queries.all { it.text == "brunette" })
    }

    @Test
    fun stopWordsAreIgnored() {
        val plan = planner.plan("blonde and kitchen")

        assertEquals("blonde kitchen", plan.queries.first().text)
    }
}
