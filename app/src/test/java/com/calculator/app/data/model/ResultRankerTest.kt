package com.calculator.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultRankerTest {

    private fun result(
        id: String,
        title: String,
        site: String = "xvideos",
        playable: Boolean = false,
        durationSeconds: Int? = null
    ) = SearchResult(
        id = id,
        title = title,
        thumbnailUrl = null,
        duration = DurationParser.format(durationSeconds),
        detailUrl = "https://example.com/$id",
        sourceSite = site,
        playability = if (playable) Playability.PLAYABLE else Playability.UNVERIFIED,
        durationSeconds = durationSeconds
    )

    @Test
    fun titlesWithMoreMatchingTermsRankHigher() {
        val good = ResultRanker.relevanceFor("Blonde amateur in the kitchen", listOf("blonde", "kitchen"))
        val weak = ResultRanker.relevanceFor("Brunette in the bedroom", listOf("blonde", "kitchen"))

        assertTrue(good > weak)
        assertTrue(good > 0)
    }

    @Test
    fun exactPhrasesScoreHigherThanSingleWords() {
        val phrase = ResultRanker.relevanceFor("Big natural tits of the day", listOf("big natural tits"))
        val single = ResultRanker.relevanceFor("Big natural tits of the day", listOf("big"))

        assertTrue(phrase > single)
    }

    @Test
    fun playableResultsAreSortedFirst() {
        val ranked = ResultRanker.rank(
            listOf(
                result("a", "Blonde kitchen amateur", playable = false),
                result("b", "Random clip", playable = true)
            ),
            listOf("blonde", "kitchen")
        )

        assertEquals("b", ranked.first().id)
        assertTrue(ranked.first().isPlayable)
    }

    @Test
    fun duplicatesWithTheSameTitleAreRemoved() {
        val ranked = ResultRanker.rank(
            listOf(
                result("a", "Blonde Kitchen Fun", site = "xvideos"),
                result("b", "blonde kitchen fun", site = "xnxx"),
                result("c", "Something else")
            ),
            emptyList()
        )

        assertEquals(2, ranked.size)
        assertEquals("a", ranked.first().id)
    }

    @Test
    fun dedupePrefersThePlayableCopy() {
        val deduped = ResultRanker.dedupeByTitle(
            listOf(
                result("a", "Same Clip", playable = false),
                result("b", "Same Clip", playable = true)
            )
        )

        assertEquals(1, deduped.size)
        assertEquals("b", deduped.first().id)
    }

    @Test
    fun sortingByLengthPutsUnknownDurationsLast() {
        val items = listOf(
            result("short", "Short", durationSeconds = 60),
            result("unknown", "Unknown", durationSeconds = null),
            result("long", "Long", durationSeconds = 3600)
        )

        assertEquals(
            listOf("long", "short", "unknown"),
            ResultRanker.applySort(items, SortOrder.LONGEST).map { it.id }
        )
        assertEquals(
            listOf("short", "long", "unknown"),
            ResultRanker.applySort(items, SortOrder.SHORTEST).map { it.id }
        )
    }

    @Test
    fun lengthFilterMatchesDurations() {
        assertTrue(LengthFilter.ALL.matches(null))
        assertTrue(LengthFilter.SHORT.matches(120))
        assertTrue(!LengthFilter.SHORT.matches(600))
        assertTrue(LengthFilter.MEDIUM.matches(600))
        assertTrue(LengthFilter.LONG.matches(1800))
        assertTrue(!LengthFilter.LONG.matches(null))
    }

    @Test
    fun tokenizeIgnoresShortWordsAndDuplicates() {
        assertEquals(listOf("blonde", "kitchen"), ResultRanker.tokenize("Blonde, KITCHEN and a blonde"))
    }
}
