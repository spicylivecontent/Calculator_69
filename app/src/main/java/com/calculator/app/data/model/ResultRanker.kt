package com.calculator.app.data.model

import com.calculator.app.ai.embedding.EmbeddingEngine
import com.calculator.app.ai.embedding.OnnxEmbeddingEngine
import java.util.Locale

/**
 * Hybrid relevansberäkning (regelbaserad + lokal AI-embedding), dubblettrensning
 * och sortering av sökträffar. Ren Kotlin (ingen Android-kod) så att logiken
 * kan enhetstestas.
 */
object ResultRanker {

    const val SEMANTIC_WEIGHT = 60
    private const val MIN_SEMANTIC_SIMILARITY = 0.15f
    private const val SEMANTIC_SEARCH_MIN_SCORE = 20

    private const val PHRASE_HIT = 40
    private const val TOKEN_HIT = 14
    private const val PARTIAL_HIT = 6
    private const val PLAYABLE_HIT = 8
    private const val SHORT_TITLE_PENALTY = -6
    private const val TAG_HIT = 18
    private const val PERFORMER_HIT = 20
    private const val CATEGORY_HIT = 14
    private const val DESCRIPTION_HIT = 9
    private const val COMMENT_HIT = 10
    private const val COVERAGE_BONUS = 30
    private const val UNTOUCHED_METADATA_BONUS = 4

    private val minTokenLength = 3

    /** Aktiv lokal embedding-motor för semantisk poängsättning. */
    @Volatile
    var embeddingEngine: EmbeddingEngine = OnnxEmbeddingEngine.Default

    /** Gör att AI-ranking kan slås på/av i inställningarna. */
    @Volatile
    var semanticEnabled: Boolean = true

    private val junkWords = setOf(
        "trailer", "preview", "teaser", "sample", "compilation", "interview", "podcast",
        "audio", "slideshow", "photo", "photos"
    )

    private val qualityWords = setOf("4k", "uhd", "2160p", "1440p", "1080p", "60fps", "hd")

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

    /**
     * Regelbaserad baspoäng som räknar in rubrik samt skrapad information (taggar,
     * medverkande, kategorier, beskrivning och toppkommentarer).
     */
    fun ruleBasedRelevanceFor(result: SearchResult, terms: List<String>): Int {
        var score = relevanceFor(result.title, terms)
        if (terms.isEmpty()) return score

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

        val coverage = result.coverageOf(terms)
        if (coverage >= 1f) score += COVERAGE_BONUS else score += (coverage * COVERAGE_BONUS).toInt()
        if (result.isEnriched) score += UNTOUCHED_METADATA_BONUS
        return score
    }

    /**
     * Semantisk poäng (0..60) via lokal embedding-modell och cosine similarity:
     * `(semanticSimilarity * 60).toInt()`.
     */
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

    /**
     * Hybrid relevans: `ruleBasedScore + (semanticSimilarity * 60).toInt()`.
     * Fångar både exakta nyckelord och semantiska samband i rubrik, taggar,
     * medverkande, kategorier, beskrivning och toppkommentarer.
     */
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

    /** Sant om varje term finns i rubriken, taggarna, medverkande, beskrivningen eller kommentarerna. */
    fun matchesEverything(result: SearchResult, terms: List<String>): Boolean {
        val wanted = terms.map { it.trim() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return true
        val haystack = result.searchableText.lowercase(Locale.ROOT)
        return wanted.all { haystack.contains(it.lowercase(Locale.ROOT)) }
    }

    /** Avancerad sökning direkt i allt som skrapats in (inklusive kommentarerna och semantisk matchning). */
    fun searchScraped(results: List<SearchResult>, text: String): List<SearchResult> {
        val terms = tokenize(text)
        if (terms.isEmpty()) return results
        return results
            .filter { result ->
                val haystack = normalizeTitle(result.searchableText)
                terms.any { haystack.contains(it) } ||
                    semanticScoreFor(result, terms) >= SEMANTIC_SEARCH_MIN_SCORE
            }
            .map { result -> result.copy(relevance = relevanceFor(result, terms)) }
            .sortedWith(
                compareByDescending<SearchResult> { matchesEverything(it, terms) }
                    .thenByDescending { it.relevance }
            )
    }

    /** Filtrerar på valda taggar/kategorier (alla måste finnas). */
    fun filterByTags(results: List<SearchResult>, tags: Collection<String>): List<SearchResult> {
        val wanted = tags.map { normalizeTitle(it) }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return results
        return results.filter { result ->
            val haystack = normalizeTitle(result.tags.joinToString(" "))
            wanted.all { tag -> haystack.contains(tag) }
        }
    }

    /** Alla taggar som förekommer i en resultatlista, vanligast först. */
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

    /** Sätter hybridrelevans (regelbaserad + semantisk), tar bort dubbletter och sorterar spelbara träffar först. */
    fun rank(
        results: List<SearchResult>,
        terms: List<String>,
        engine: EmbeddingEngine = embeddingEngine
    ): List<SearchResult> {
        val scored = dedupeByTitle(
            results.map { result ->
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

    /** Tar bort i praktiken identiska titlar (samma klipp kan dyka upp på flera sajter). */
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
