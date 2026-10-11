package com.calculator.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultRankerTest {

    private fun result(
        id: String,
        title: String,
        playability: Playability = Playability.UNVERIFIED,
        durationSeconds: Int? = null,
        site: String = "eporner"
    ) = SearchResult(
        id = id,
        title = title,
        thumbnailUrl = null,
        duration = null,
        detailUrl = "https://example.com/$id",
        sourceSite = site,
        playability = playability,
        durationSeconds = durationSeconds
    )

    @Test
    fun tokenizeKeepsWordsOfAtLeastThreeLetters() {
        assertEquals(listOf("blonde", "kitchen"), ResultRanker.tokenize("Blonde, kitchen!"))
        assertEquals(listOf("abc"), ResultRanker.tokenize("a ab abc"))
        assertEquals(emptyList<String>(), ResultRanker.tokenize("ab"))
    }

    @Test
    fun fullPhraseScoresHigherThanASingleWord() {
        val phrase = ResultRanker.relevanceFor("Blonde in the kitchen", listOf("blonde kitchen"))
        val singleWord = ResultRanker.relevanceFor("Blonde in the kitchen", listOf("blonde"))
        assertTrue("fras ska ge mer än enstaka ord ($phrase vs $singleWord)", phrase > singleWord)
    }

    @Test
    fun partialWordsStillGiveSomeRelevance() {
        assertTrue(ResultRanker.relevanceFor("Blondes at the beach", listOf("blonde")) > 0)
        assertEquals(0, ResultRanker.relevanceFor("A long kitchen video", listOf("massage")))
    }

    @Test
    fun rankPutsPlayableResultsFirst() {
        val weakButPlayable = result("a", "Random clip", Playability.PLAYABLE)
        val strongButUnverified = result("b", "Blonde kitchen massage video", Playability.UNVERIFIED)

        val ranked = ResultRanker.rank(listOf(strongButUnverified, weakButPlayable), listOf("blonde", "kitchen"))

        assertEquals("a", ranked.first().id)
    }

    @Test
    fun rankSortsOnRelevanceWithinSamePlayability() {
        val strong = result("strong", "Blonde kitchen amateur", Playability.PLAYABLE)
        val weak = result("weak", "Something else entirely", Playability.PLAYABLE)

        val ranked = ResultRanker.rank(listOf(weak, strong), listOf("blonde", "kitchen"))

        assertEquals("strong", ranked.first().id)
    }

    @Test
    fun duplicateTitlesAreRemovedAndPlayableWins() {
        val unverified = result("unverified", "Blonde Kitchen Clip", Playability.UNVERIFIED)
        val playable = result("playable", "blonde kitchen clip", Playability.PLAYABLE)

        val deduped = ResultRanker.dedupeByTitle(listOf(unverified, playable))

        assertEquals(1, deduped.size)
        assertEquals("playable", deduped.first().id)
    }

    @Test
    fun sortingByLengthKeepsUnknownLengthsLast() {
        val short = result("short", "Short", durationSeconds = 120)
        val long = result("long", "Long", durationSeconds = 3600)
        val unknown = result("unknown", "Unknown", durationSeconds = null)

        val longest = ResultRanker.applySort(listOf(short, unknown, long), SortOrder.LONGEST)
        assertEquals(listOf("long", "short", "unknown"), longest.map { it.id })

        val shortest = ResultRanker.applySort(listOf(long, unknown, short), SortOrder.SHORTEST)
        assertEquals(listOf("short", "long", "unknown"), shortest.map { it.id })
    }

    @Test
    fun siteSortIsGroupedBySource() {
        val b = result("b", "B", site = "beeg")
        val a = result("a", "A", site = "eporner")
        val c = result("c", "C", site = "beeg")

        assertEquals(listOf("b", "c", "a"), ResultRanker.applySort(listOf(b, a, c), SortOrder.SITE).map { it.id })
    }
}
