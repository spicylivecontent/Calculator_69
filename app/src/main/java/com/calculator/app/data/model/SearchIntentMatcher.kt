package com.calculator.app.data.model

/**
 * Applies strict positive-term matching after a result's detail page has been enriched.
 *
 * Source-site searches are OR-ed for recall, so this final gate makes sure every requested
 * concept appears in the scraped metadata. Solo intent also rejects titles/tags/categories
 * that explicitly identify the item as a couple or group scene.
 */
object SearchIntentMatcher {
    private val groupSignals = listOf(
        "couple", "couples", "duo", "threesome", "threeway", "three way",
        "group", "group sex", "gangbang", "gang bang", "orgy", "orgies",
        "two girls", "two women", "two men", "multiple partners",
        "boyfriend", "girlfriend", "husband", "wife", "mf couple", "ff couple",
        "mmf", "mfm", "ffm", "mff", "group scene", "real couple"
    )

    fun matchesRequiredTerms(result: SearchResult, requiredTerms: List<String>): Boolean {
        val required = requiredTerms.map { it.trim() }.filter { it.isNotEmpty() }
        if (required.isEmpty()) return true

        val searchableText = result.searchableText
        val wantsSolo = required.any { it.equals("solo", ignoreCase = true) }

        // Use identity/label fields for contradiction checks. Comments can mention a group
        // while discussing a solo scene, so they should help positive matching but not
        // automatically disqualify a result.
        if (wantsSolo) {
            val identityText = (listOf(result.title) + result.tags + result.categories)
                .joinToString(" ")
            if (groupSignals.any { SearchTextMatcher.containsTerm(identityText, it) }) return false
        }

        return required.all { term ->
            val alternatives = when (term.lowercase(java.util.Locale.ROOT)) {
                "atm", "a2m", "ass to mouth" -> listOf("atm", "a2m", "ass to mouth")
                "latina", "latin" -> listOf("latina", "latin", "hispanic")
                "redhead" -> listOf("redhead", "red hair")
                else -> listOf(term)
            }
            alternatives.any { SearchTextMatcher.containsTerm(searchableText, it) }
        }
    }
}
