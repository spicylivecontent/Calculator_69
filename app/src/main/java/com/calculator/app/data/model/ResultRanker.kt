package com.calculator.app.data.model

import com.calculator.app.ai.embedding.EmbeddingEngine
import com.calculator.app.ai.embedding.OnnxEmbeddingEngine
import java.util.Locale

/**
 * Hybrid relevansberäkning (regelbaserad + lokal AI-embedding), dubblettrensning
 * och sortering av sökträffar.
 *
 * Poängsätter rubrik, taggar, medverkande, kategorier, beskrivning, kommentarer
 * och längd. Straffar par/kille när query innehåller solo, och kräver att
 * sällsynta termer (scat, atm …) faktiskt syns i skrapad text.
 */
object ResultRanker {

    const val SEMANTIC_WEIGHT = 70
    private const val MIN_SEMANTIC_SIMILARITY = 0.12f
    private const val SEMANTIC_SEARCH_MIN_SCORE = 18

    private const val PHRASE_HIT = 45
    private const val TOKEN_HIT = 16
    private const val PARTIAL_HIT = 7
    private const val PLAYABLE_HIT = 8
    private const val SHORT_TITLE_PENALTY = -6
    private const val TAG_HIT = 22
    private const val PERFORMER_HIT = 20
    private const val CATEGORY_HIT = 16
    private const val DESCRIPTION_HIT = 12
    private const val COMMENT_HIT = 14
    private const val COVERAGE_BONUS = 35
    private const val UNTOUCHED_METADATA_BONUS = 5

    private const val COUPLE_PENALTY = -60
    private const val RARE_MISS_PENALTY = -50
    private const val RARE_HIT_BONUS = 40
    private const val SOLO_PURE_BONUS = 28

    private val minTokenLength = 3

    @Volatile
    var embeddingEngine: EmbeddingEngine = OnnxEmbeddingEngine.Default

    @Volatile
    var semanticEnabled: Boolean = true

    private val junkWords = setOf(
        "trailer", "preview", "teaser", "sample", "compilation", "interview", "podcast",
        "audio", "slideshow", "photo", "photos"
    )

    private val qualityWords = setOf("4k", "uhd", "2160p", "1440p", "1080p", "60fps", "hd")

    private val COUPLE_SIGNALS = setOf(
        "couple", "boyfriend", "girlfriend", "husband", "wife", "guy", "man fucks",
        "cock", "dick", "penis", "he fucks", "with a guy", "mmf", "mfm", "threesome",
        "duo", "partners", "real couple", "amateur couple", "fucked by", "his cock",
        "her boyfriend", "stepbrother", "step brother"
    )

    private val RARE_QUERY_TERMS = setOf(
        "scat", "atm", "a2m", "fisting", "prolapse", "pegging", "cbt",
        "watersports", "enema"
    )

    private val SOLO_QUERY_TERMS = setOf("solo", "masturbation", "onani")

    /**
     * Synonymer som räknas som träff för sällsynta termer: sajterna skriver ofta
     * ut "ass to mouth" i stället för "atm". Utan aliaserna straffas och filtreras
     * ekvivoka träffar bort fast de matchar frågan semantiskt.
     */
    private val RARE_TERM_ALIASES: Map<String, List<String>> = mapOf(
        "scat" to listOf("scatology", "toilet", "poop", "pooping", "dirty ass"),
        "atm" to listOf("a2m", "ass to mouth", "asstomouth", "ass 2 mouth"),
        "a2m" to listOf("atm", "ass to mouth", "asstomouth"),
        "fisting" to listOf("fist"),
        "pegging" to listOf("strapon", "strap on", "strap-on"),
        "cbt" to listOf("cock and ball", "ballbusting", "ball busting"),
        "watersports" to listOf("water sports", "golden shower", "piss", "pee")
    )

    private fun rareTermMatches(haystack: String, rare: String): Boolean =
        SearchTextMatcher.containsTerm(haystack, rare) ||
            RARE_TERM_ALIASES[rare].orEmpty().any { alias ->
                SearchTextMatcher.containsTerm(haystack, alias)
            }

    fun normalizeTitle(title: String): String =
        title.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    fun tokenize(text: String): List<String> =
        normalizeTitle(text).split(' ')
            .map { it.trim() }
            .filter { it.length >= minTokenLength }
            .distinct()

    fun relevanceFor(title: String, terms: List<String>): Int {
        val normalizedTitle = normalizeTitle(title)
        if (normalizedTitle.isEmpty()) return 0
        val titleTokens = normalizedTitle.split(' ').toSet()
        var score = 0

        for (term in terms) {
            val normalizedTerm = normalizeTitle(term)
            if (normalizedTerm.isBlank()) continue
            val termTokens = normalizedTerm.split(' ').filter { it.isNotBlank() }
            if (termTokens.isEmpty()) continue

            when {
                termTokens.size > 1 && normalizedTitle.contains(normalizedTerm) -> score += PHRASE_HIT
                termTokens.size > 1 -> score += termTokens.count { it in titleTokens || normalizedTitle.contains(it) } * TOKEN_HIT
                normalizedTerm in titleTokens -> score += TOKEN_HIT
                normalizedTitle.contains(normalizedTerm) -> score += PARTIAL_HIT
            }
        }

        if (title.length < 12) score += SHORT_TITLE_PENALTY
        if (junkWords.any { it in titleTokens }) score += SHORT_TITLE_PENALTY
        if (qualityWords.any { it in titleTokens }) score += 4

        return score
    }

    fun ruleBasedRelevanceFor(result: SearchResult, terms: List<String>): Int {
        var score = relevanceFor(result.title, terms)
        if (terms.isEmpty()) return score

        val haystack = normalizeTitle(result.searchableText)
        val normalizedTags = result.tags.map { normalizeTitle(it) }.filter { it.isNotEmpty() }
        val normalizedPerformers = result.performers.map { normalizeTitle(it) }.filter { it.isNotEmpty() }
        val normalizedCategories = result.categories.map { normalizeTitle(it) }.filter { it.isNotEmpty() }
        val normalizedDescription = result.description?.let { normalizeTitle(it) }.orEmpty()
        val normalizedComments = if (result.comments.isEmpty()) "" else normalizeTitle(result.comments.joinToString(" "))

        for (term in terms) {
            val normalizedTerm = normalizeTitle(term)
            if (normalizedTerm.isBlank()) continue

            if (normalizedTags.any { tag -> tag == normalizedTerm || tag.contains(normalizedTerm) }) {
                score += TAG_HIT
            }
            if (normalizedPerformers.any { p -> p == normalizedTerm || p.contains(normalizedTerm) }) {
                score += PERFORMER_HIT
            }
            if (normalizedCategories.any { c -> c == normalizedTerm || c.contains(normalizedTerm) }) {
                score += CATEGORY_HIT
            }
            if (normalizedDescription.isNotEmpty() && normalizedDescription.contains(normalizedTerm)) {
                score += DESCRIPTION_HIT
            }
            if (normalizedComments.isNotEmpty() && normalizedComments.contains(normalizedTerm)) {
                score += COMMENT_HIT
            }
        }

        val normalizedTerms = terms.map { normalizeTitle(it) }.filter { it.isNotBlank() }
        val rareInQuery = normalizedTerms.filter { it in RARE_QUERY_TERMS }
        for (rare in rareInQuery) {
            if (rareTermMatches(haystack, rare)) score += RARE_HIT_BONUS
            else score += RARE_MISS_PENALTY
        }

        val wantsSolo = normalizedTerms.any { it in SOLO_QUERY_TERMS }
        if (wantsSolo) {
            val hasCoupleSignal = COUPLE_SIGNALS.any { haystack.contains(it) }
            if (hasCoupleSignal) {
                score += COUPLE_PENALTY
            } else if (haystack.contains("solo") || haystack.contains("masturbation")) {
                score += SOLO_PURE_BONUS
            }
        }

        val coverage = result.coverageOf(terms)
        if (coverage >= 1f) score += COVERAGE_BONUS else score += (coverage * COVERAGE_BONUS).toInt()
        if (result.isEnriched) score += UNTOUCHED_METADATA_BONUS
        return score
    }

    fun semanticScoreFor(
        result: SearchResult,
        terms: List<String>,
        engine: EmbeddingEngine = embeddingEngine
    ): Int {
        if (!semanticEnabled || terms.isEmpty() || !engine.isReady) return 0
        val queryText = terms.joinToString(" ").trim()
        val docText = result.embeddingText
        if (queryText.isBlank() || docText.isBlank()) return 0
        val cacheKey = "${result.id}:${docText.hashCode()}"
        val similarity = engine.similarity(queryText, docText, cacheKey = cacheKey)
        return if (similarity >= MIN_SEMANTIC_SIMILARITY) {
            (similarity * SEMANTIC_WEIGHT).toInt()
        } else {
            0
        }
    }

    fun relevanceFor(
        result: SearchResult,
        terms: List<String>,
        engine: EmbeddingEngine = embeddingEngine
    ): Int {
        val ruleBasedScore = ruleBasedRelevanceFor(result, terms)
        if (terms.isEmpty()) return ruleBasedScore
        val semanticScore = semanticScoreFor(result, terms, engine)
        return ruleBasedScore + semanticScore
    }

    fun matchesEverything(result: SearchResult, terms: List<String>): Boolean {
        val wanted = terms.map { it.trim() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return true
        return wanted.all { SearchTextMatcher.containsTerm(result.searchableText, it) }
    }

    fun requireRareMatches(results: List<SearchResult>, terms: List<String>): List<SearchResult> {
        val rare = terms.map { normalizeTitle(it) }.filter { it in RARE_QUERY_TERMS }
        if (rare.isEmpty()) return results
        return results.filter { result ->
            val hay = normalizeTitle(result.searchableText)
            rare.any { rareTermMatches(hay, it) }
        }
    }

    fun searchScraped(results: List<SearchResult>, text: String): List<SearchResult> {
        val terms = tokenize(text)
        if (terms.isEmpty()) return results
        return results
            .filter { result ->
                terms.any { SearchTextMatcher.containsTerm(result.searchableText, it) } ||
                    semanticScoreFor(result, terms) >= SEMANTIC_SEARCH_MIN_SCORE
            }
            .map { result -> result.copy(relevance = relevanceFor(result, terms)) }
            .sortedWith(
                compareByDescending<SearchResult> { matchesEverything(it, terms) }
                    .thenByDescending { it.relevance }
            )
    }

    fun filterByTags(results: List<SearchResult>, tags: Collection<String>): List<SearchResult> {
        val wanted = tags.map { normalizeTitle(it) }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return results
        return results.filter { result ->
            val haystack = normalizeTitle(result.tags.joinToString(" "))
            wanted.all { tag -> haystack.contains(tag) }
        }
    }

    fun tagCloud(results: List<SearchResult>, limit: Int = 24): List<String> =
        results.flatMap { it.tags }
            .map { it.trim() }
            .filter { it.length in 2..30 }
            .groupingBy { it.lowercase(Locale.ROOT) }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { entry -> results.flatMap { it.tags }.first { tag -> tag.trim().lowercase(Locale.ROOT) == entry.key }.trim() }
            .take(limit)

    fun rank(
        results: List<SearchResult>,
        terms: List<String>,
        engine: EmbeddingEngine = embeddingEngine
    ): List<SearchResult> {
        val afterRare = requireRareMatches(results, terms)
        val scored = dedupeByTitle(
            afterRare.map { result ->
                result.copy(
                    relevance = relevanceFor(result, terms, engine) +
                        if (result.isPlayable) PLAYABLE_HIT else 0
                )
            }
        )
        return scored.sortedWith(
            compareByDescending<SearchResult> { it.isPlayable }
                .thenByDescending { it.relevance }
                .thenByDescending { it.durationSeconds ?: 0 }
        )
    }

    fun dedupeByTitle(results: List<SearchResult>): List<SearchResult> {
        val seen = LinkedHashMap<String, SearchResult>()
        val seenIds = HashSet<String>()
        for (result in results) {
            if (!seenIds.add(result.id)) continue
            val key = normalizeTitle(result.title)
            if (key.isBlank()) {
                seen[result.id] = result
                continue
            }
            val existing = seen[key]
            if (existing == null) {
                seen[key] = result
            } else if (!existing.isPlayable && result.isPlayable) {
                seen[key] = result
            }
        }
        return seen.values.toList()
    }

    fun applySort(results: List<SearchResult>, sortOrder: SortOrder): List<SearchResult> = when (sortOrder) {
        SortOrder.RELEVANCE -> results
        SortOrder.LONGEST -> results.sortedWith(
            compareBy<SearchResult> { it.durationSeconds == null }
                .thenByDescending { it.durationSeconds ?: 0 }
                .thenByDescending { it.relevance }
        )
        SortOrder.SHORTEST -> results.sortedWith(
            compareBy<SearchResult> { it.durationSeconds == null }
                .thenBy { it.durationSeconds ?: Int.MAX_VALUE }
                .thenByDescending { it.relevance }
        )
        SortOrder.SITE -> results.sortedWith(
            compareBy<SearchResult> { it.sourceSite }
                .thenByDescending { it.relevance }
        )
    }
}
