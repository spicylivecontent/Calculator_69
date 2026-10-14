package com.calculator.app.data.model

import java.util.Locale

/**
 * Whole-term matching for user exclusions and strict search constraints.
 *
 * Single words must be separated from adjacent letters/digits; multi-word phrases
 * may contain variable whitespace but must still start/end on term boundaries.
 * This avoids a negative keyword such as "ass" accidentally excluding "classic".
 */
object SearchTextMatcher {
    private val whitespace = Regex("\\s+")

    fun containsTerm(text: String, term: String): Boolean {
        val normalizedText = normalize(text)
        val normalizedTerm = normalize(term)
        if (normalizedText.isBlank() || normalizedTerm.isBlank()) return false

        val tokens = normalizedTerm.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        val expression = tokens.joinToString("\\\\s+") { Regex.escape(it) }
        return Regex(
            "(?<![\\\\p{L}\\\\p{N}])$expression(?![\\\\p{L}\\\\p{N}])"
        ).containsMatchIn(normalizedText)
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(whitespace, " ").trim()
}
