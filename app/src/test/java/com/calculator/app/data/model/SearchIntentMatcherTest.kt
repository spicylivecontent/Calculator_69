package com.calculator.app.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIntentMatcherTest {
    private fun result(
        title: String,
        tags: List<String> = emptyList(),
        categories: List<String> = emptyList(),
        description: String? = null,
        comments: List<String> = emptyList()
    ) = SearchResult(
        id = title,
        title = title,
        thumbnailUrl = null,
        duration = null,
        detailUrl = "https://example.com/item",
        tags = tags,
        categories = categories,
        description = description,
        comments = comments
    )

    @Test
    fun strictMatchUsesAllEnrichedTextFields() {
        val item = result(
            title = "Solo Latina",
            tags = listOf("Amateur"),
            description = "A continuous scene with ass to mouth",
            comments = listOf("Great nonstop performance")
        )

        assertTrue(
            SearchIntentMatcher.matchesRequiredTerms(
                item,
                listOf("solo", "latina", "amateur", "nonstop", "atm")
            )
        )
    }

    @Test
    fun strictMatchRejectsMissingRequiredConcept() {
        val item = result(
            title = "Solo Latina",
            description = "A continuous scene"
        )

        assertFalse(
            SearchIntentMatcher.matchesRequiredTerms(
                item,
                listOf("solo", "latina", "atm")
            )
        )
    }

    @Test
    fun soloIntentRejectsCoupleAndGroupLabels() {
        val item = result(
            title = "Solo Latina ATM",
            tags = listOf("Amateur"),
            categories = listOf("Group Sex")
        )

        assertFalse(
            SearchIntentMatcher.matchesRequiredTerms(
                item,
                listOf("solo", "latina", "atm")
            )
        )
    }

    @Test
    fun soloIntentRejectsCommonGroupSynonyms() {
        val titles = listOf(
            "Solo Latina ATM threesome",
            "Solo Latina ATM gangbang",
            "Solo Latina ATM with two women",
            "Solo Latina ATM real couple"
        )

        for (title in titles) {
            assertFalse(
                title,
                SearchIntentMatcher.matchesRequiredTerms(
                    result(title),
                    listOf("solo", "latina", "atm")
                )
            )
        }
    }

    @Test
    fun groupWordsInsideLargerWordsDoNotTriggerExclusion() {
        val item = result(
            title = "Solo Latina ATM",
            tags = listOf("groupie style", "couplet")
        )

        assertTrue(
            SearchIntentMatcher.matchesRequiredTerms(
                item,
                listOf("solo", "latina", "atm")
            )
        )
    }

    @Test
    fun commentsCanHelpMatchButDoNotAloneTriggerGroupExclusion() {
        val item = result(
            title = "Solo Latina ATM",
            comments = listOf("People keep comparing this to group scenes")
        )

        assertTrue(
            SearchIntentMatcher.matchesRequiredTerms(
                item,
                listOf("solo", "latina", "atm")
            )
        )
    }

    @Test
    fun noRequiredTermsAcceptsAnyResult() {
        assertTrue(SearchIntentMatcher.matchesRequiredTerms(result("Anything"), emptyList()))
    }
}
