package com.calculator.app.data.model

import java.util.Locale

/**
 * Relevansberäkning, dubblettrensning och sortering av sökträffar.
 * Ren Kotlin (ingen Android-kod) så att logiken kan enhetstestas.
 */
object ResultRanker {

    private const val PHRASE_HIT = 40
    private const val TOKEN_HIT = 14
    private const val PARTIAL_HIT = 6
    private const val PLAYABLE_HIT = 8
    private const val SHORT_TITLE_PENALTY = -6
    private const val TAG_HIT = 18
    private const val DESCRIPTION_HIT = 9
    private const val COVERAGE_BONUS = 30
    private const val UNTOUCHED_METADATA_BONUS = 4

    private val minTokenLength = 3

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
     * Relevans som även räknar in den skrapade informationen (taggar, kategorier,
     * medverkande och beskrivning) – det är så "allt som händer i filmen" kan
     * påverka träffbilden.
     */
    fun relevanceFor(result: SearchResult, terms: List<String>): Int {
        var score = relevanceFor(result.title, terms)
        if (terms.isEmpty()) return score

        val normalizedTags = result.tags.map { normalizeTitle(it) }.filter { it.isNotEmpty() }
        val normalizedDescription = result.description?.let { normalizeTitle(it) }.orEmpty()

        for (term in terms) {
            val normalizedTerm = normalizeTitle(term)
            if (normalizedTerm.isBlank()) continue

            val inTags = normalizedTags.any { tag -> tag == normalizedTerm || tag.contains(normalizedTerm) }
            if (inTags) score += TAG_HIT
            if (normalizedDescription.isNotEmpty() && normalizedDescription.contains(normalizedTerm)) {
                score += DESCRIPTION_HIT
            }
        }

        val coverage = result.coverageOf(terms)
        if (coverage >= 1f) score += COVERAGE_BONUS else score += (coverage * COVERAGE_BONUS).toInt()
        if (result.isEnriched) score += UNTOUCHED_METADATA_BONUS
        return score
    }

    /** Sant om varje term finns i rubriken, taggarna eller beskrivningen. */
    fun matchesEverything(result: SearchResult, terms: List<String>): Boolean {
        val wanted = terms.map { it.trim() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return true
        val haystack = result.searchableText.lowercase(Locale.ROOT)
        return wanted.all { haystack.contains(it.lowercase(Locale.ROOT)) }
    }

    /** Avancerad sökning direkt i allt som skrapats in (rubrik, taggar, beskrivning). */
    fun searchScraped(results: List<SearchResult>, text: String): List<SearchResult> {
        val terms = tokenize(text)
        if (terms.isEmpty()) return results
        return results
            .filter { result ->
                val haystack = normalizeTitle(result.searchableText)
                terms.any { haystack.contains(it) }
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

    /** Sätter relevans, tar bort dubbletter och sorterar spelbara träffar först. */
    fun rank(results: List<SearchResult>, terms: List<String>): List<SearchResult> {
        val scored = dedupeByTitle(
            results.map { result ->
                result.copy(relevance = relevanceFor(result, terms) + if (result.isPlayable) PLAYABLE_HIT else 0)
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
