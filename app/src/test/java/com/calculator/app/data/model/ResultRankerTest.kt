package com.calculator.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultRankerTest {

    private fun result(
        id: String,
        title: String,
        playability: Playability = Playability.UNVERIFIED,
        durationSeconds: Int? = null,
        site: String = "eporner",
        tags: List<String> = emptyList(),
        description: String? = null
    ) = SearchResult(
        id = id,
        title = title,
        thumbnailUrl = null,
        duration = null,
        detailUrl = "https://example.com/$id",
        sourceSite = site,
        playability = playability,
        durationSeconds = durationSeconds,
        tags = tags,
        description = description,
        tagCount = tags.size
    )

    /**
     * Snapshot av rangordningen: en fast uppsättning träffar ska alltid komma i samma
     * ordning. Ändrar någon viktningen i ResultRanker får det inte ske av misstag – då
     * faller det här testet och ändringen blir ett medvetet val.
     */
    @Test
    fun unrelatedRichMetadataDoesNotMatchScrapedSearch() {
        val unrelated = result("u", "A completely unrelated long title 1080p", tags = listOf("outdoor"))
        assertTrue(ResultRanker.searchScraped(listOf(unrelated), "kitchen").isEmpty())
    }

    @Test
    fun tagCloudTrimsTagsBeforeLookingUpTheirDisplayName() {
        assertEquals(listOf("Kitchen"), ResultRanker.tagCloud(listOf(result("a", "A", tags = listOf(" Kitchen ")))))
    }

    @Test
    fun rankingOrderIsStableForAFixedSetOfResults() {
        val results = listOf(
            result(
                id = "traff-c",
                title = "Massage clip",
                playability = Playability.PLAYABLE
            ),
            result(
                id = "traff-d",
                title = "Blonde kitchen",
                playability = Playability.UNVERIFIED,
                tags = emptyList(),
                description = null
            ),
            result(
                id = "traff-b",
                title = "Kitchen blonde amateur",
                playability = Playability.PLAYABLE,
                tags = listOf("Blonde"),
                description = null
            ),
            result(
                id = "traff-a",
                title = "Blonde in the kitchen",
                playability = Playability.PLAYABLE,
                tags = listOf("Blonde", "Kitchen"),
                description = "En blond kock i ett litet kök."
            )
        )

        val ranked = ResultRanker.rank(results, listOf("blonde", "kitchen"))

        assertEquals(
            listOf("traff-a", "traff-b", "traff-c", "traff-d"),
            ranked.map { it.id }
        )
        assertEquals("dubbletter ska inte ändra antalet", 4, ranked.size)
        assertTrue("alla träffar ska ha en poäng", ranked.all { it.relevance > 0 })
    }

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
    fun scrapedTagsAndDescriptionRaiseRelevance() {
        val plain = result("plain", "Ett annat klipp")
        val tagged = result(
            "tagged",
            "Ett helt annat klipp",
            tags = listOf("kitchen", "blonde"),
            description = "Hon lagar mat i köket innan det blir stökigt."
        )

        assertTrue(
            ResultRanker.relevanceFor(tagged, listOf("kitchen")) >
                ResultRanker.relevanceFor(plain, listOf("kitchen"))
        )

        val ranked = ResultRanker.rank(listOf(plain, tagged), listOf("kitchen", "blonde"))
        assertEquals("tagged", ranked.first().id)
    }

    @Test
    fun matchesEverythingChecksTagsAndDescription() {
        val tagged = result(
            "tagged",
            "Blonde clip",
            tags = listOf("kitchen"),
            description = "Hon dansar på ett bord"
        )

        assertTrue(ResultRanker.matchesEverything(tagged, listOf("blonde", "kitchen")))
        assertTrue(ResultRanker.matchesEverything(tagged, listOf("dansar")))
        assertFalse(ResultRanker.matchesEverything(tagged, listOf("blonde", "motorcycle")))
        assertTrue(ResultRanker.matchesEverything(tagged, emptyList()))
    }

    @Test
    fun searchScrapedFindsMatchesInTaggedInformation() {
        val a = result("a", "Klipp ett", tags = listOf("utomhus", "sensuell"))
        val b = result("b", "Klipp två", tags = listOf("kök"))
        val c = result("c", "Klipp tre", description = "En lång promenad i skogen")

        val hits = ResultRanker.searchScraped(listOf(a, b, c), "skogen")

        // "c" matchar bokstavligen ("skogen" i beskrivningen) och ska därför ligga först.
        // "a" blir dessutom en semantisk träff: den lokala embedding-motorn lägger både
        // "skog" och "utomhus" i konceptklustret outdoor_public, vilket är precis den
        // översättning hybrid-rankingen är till för. "b" ("kök") är orelaterat (olika
        // kluster => cosine similarity 0) och ska inte hittas alls.
        assertEquals(listOf("c", "a"), hits.map { it.id })
        assertTrue(ResultRanker.searchScraped(listOf(a, b, c), "").size == 3)
    }

    @Test
    fun tagFilterRequiresEveryTag() {
        val a = result("a", "A", tags = listOf("kök", "amatör"))
        val b = result("b", "B", tags = listOf("kök"))

        assertEquals(listOf("a"), ResultRanker.filterByTags(listOf(a, b), listOf("kök", "amatör")).map { it.id })
        assertEquals(listOf("a", "b"), ResultRanker.filterByTags(listOf(a, b), emptyList()).map { it.id })
    }

    @Test
    fun tagCloudCountsTheMostCommonTagsFirst() {
        val results = listOf(
            result("a", "A", tags = listOf("kök", "amatör")),
            result("b", "B", tags = listOf("kök")),
            result("c", "C", tags = listOf("kök", "utomhus"))
        )

        val cloud = ResultRanker.tagCloud(results, 3)

        assertEquals("kök", cloud.first().lowercase())
        assertEquals(3, cloud.size)
    }

    @Test
    fun siteSortIsGroupedBySource() {
        val b = result("b", "B", site = "beeg")
        val a = result("a", "A", site = "eporner")
        val c = result("c", "C", site = "beeg")

        assertEquals(listOf("b", "c", "a"), ResultRanker.applySort(listOf(b, a, c), SortOrder.SITE).map { it.id })
    }

    @Test
    fun rareTermMatchingUsesWholeWordBoundaries() {
        val falseScat = result("false-scat", "Scatterbrain interview")
        val trueScat = result("true-scat", "Scat play")
        val falseAtmAlias = result("false-atm", "A2many amateur clip")
        val trueAtmAlias = result("true-atm", "A2M scene")

        assertTrue(ResultRanker.requireRareMatches(listOf(falseScat), listOf("scat")).isEmpty())
        assertEquals(
            listOf("true-scat"),
            ResultRanker.requireRareMatches(listOf(falseScat, trueScat), listOf("scat")).map { it.id }
        )
        assertTrue(ResultRanker.requireRareMatches(listOf(falseAtmAlias), listOf("atm")).isEmpty())
        assertEquals(
            listOf("true-atm"),
            ResultRanker.requireRareMatches(listOf(falseAtmAlias, trueAtmAlias), listOf("atm")).map { it.id }
        )
    }

    @Test
    fun hybridSemanticRankingBoostsMatchingConceptsAndComments() {
        val generic = result(
            id = "generic",
            title = "Random beach clip",
            playability = Playability.PLAYABLE,
            tags = listOf("beach")
        )
        val semanticHit = SearchResult(
            id = "semantic",
            title = "Hispanic beauty by herself",
            thumbnailUrl = null,
            duration = "14:20",
            detailUrl = "https://example.com/semantic",
            sourceSite = "eporner",
            playability = Playability.PLAYABLE,
            tags = listOf("backdoor"),
            performers = listOf("Maria Lopez"),
            categories = listOf("Solo"),
            description = "Continuous marathon session",
            comments = listOf("12:10 nonstop ass to mouth scene is amazing"),
            tagCount = 3
        )

        val terms = listOf("latina", "solo", "anal", "nonstop", "atm")
        val ranked = ResultRanker.rank(listOf(generic, semanticHit), terms)

        assertEquals("semantic", ranked.first().id)
        assertTrue(ResultRanker.semanticScoreFor(semanticHit, terms) > 30)
    }
}
