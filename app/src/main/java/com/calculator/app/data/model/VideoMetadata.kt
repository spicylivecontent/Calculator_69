package com.calculator.app.data.model

import java.util.Locale

/**
 * Allt appen lyckas läsa ut om en film från dess detaljsida: rubrik, beskrivning,
 * taggar, kategorier, medverkande, kvalitet, längd och publiceringsdatum.
 *
 * Informationen används både för att visa mer om varje träff och för den avancerade
 * sökningen, som matchar mot hela texten – inte bara rubriken.
 */
data class VideoMetadata(
    val detailUrl: String,
    val title: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val performers: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    /** De mest gillade/senaste kommentarerna skrapade från detaljsidan. */
    val comments: List<String> = emptyList(),
    val qualityLabel: String? = null,
    val durationSeconds: Int? = null,
    val uploadDate: String? = null,
    val views: String? = null,
    /** True när sidan finns men innehållet är borttaget. */
    val pageMissing: Boolean = false
) {
    /** Alla nyckelord samlade (taggar, medverkande, kategorier) utan dubbletter. */
    val keywords: List<String>
        get() = (tags + performers + categories)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(MAX_KEYWORDS)

    /** Hela den skrapade texten – det avancerade sökningen och AI-rankingen matchar mot. */
    val searchableText: String
        get() = buildString {
            title?.let { append(it).append(' ') }
            if (keywords.isNotEmpty()) append(keywords.joinToString(" ")).append(' ')
            description?.let { append(it).append(' ') }
            if (comments.isNotEmpty()) append(comments.joinToString(" "))
        }.trim()

    val hasRichData: Boolean
        get() = description != null || tags.isNotEmpty() || performers.isNotEmpty() ||
            categories.isNotEmpty() || comments.isNotEmpty()

    /** Hur många av söktermerna som finns i den skrapade texten (0f–1f). */
    fun coverage(terms: List<String>): Float {
        val wanted = terms.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) return 0f
        val haystack = searchableText.lowercase(Locale.ROOT)
        val hits = wanted.count { haystack.contains(it) }
        return hits.toFloat() / wanted.size.toFloat()
    }

    fun matchedTerms(terms: List<String>): List<String> {
        val haystack = searchableText.lowercase(Locale.ROOT)
        return terms.map { it.trim() }
            .filter { it.isNotEmpty() && haystack.contains(it.lowercase(Locale.ROOT)) }
            .distinct()
    }

    fun isEmpty(): Boolean =
        title == null && description == null && tags.isEmpty() && performers.isEmpty() &&
            categories.isEmpty() && comments.isEmpty() && qualityLabel == null && durationSeconds == null

    private companion object {
        const val MAX_KEYWORDS = 60
    }
}
