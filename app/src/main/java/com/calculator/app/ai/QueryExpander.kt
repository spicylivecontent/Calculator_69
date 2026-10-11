package com.calculator.app.ai

import java.util.Locale

/**
 * Rule-based query expander that runs entirely on-device.
 * It separates positive terms from explicit exclusions and blocks
 * age-related terms in this adult-content search experience.
 */
class QueryExpander {

    data class ExpandedQuery(
        val searchTerms: String,          // Det som skickas till sajterna
        val negativeKeywords: List<String>, // Extra filter
        val explanation: String,           // Kort förklaring till användaren
        val blockedReason: String? = null
    )

    // Vanliga negativa mönster (svenska + engelska)
    private val negativePatterns = listOf(
        "no ", "not ", "utan ", "ingen ", "inte ", "without ", "exclude ",
        "absolut inte ", "aldrig ", "never ", "förbjudet ", "undvik "
    ).sortedByDescending { it.length }

    // Synonymer / expansioner (positiv intent)
    private val expansions = mapOf(
        "brunette" to listOf("brunette", "brown hair", "dark hair"),
        "blonde" to listOf("blonde", "blond"),
        "redhead" to listOf("redhead", "red hair", "ginger"),
        "amateur" to listOf("amateur", "homemade", "real"),
        "pov" to listOf("pov", "point of view"),
        "slow" to listOf("slow", "sensual", "gentle"),
        "rough" to listOf("rough", "hard", "aggressive"),
        "kitchen" to listOf("kitchen", "cooking"),
        "outdoor" to listOf("outdoor", "outside", "public"),
        "natural" to listOf("natural", "no makeup"),
        "milf" to listOf("milf", "mature", "mom"),
        "lesbian" to listOf("lesbian", "girl on girl", "gg"),
        "solo" to listOf("solo", "masturbation"),
        "couple" to listOf("couple", "boyfriend", "girlfriend")
    )

    // Block explicit age-related search terms in an adult-content context.
    private val disallowedQueryPattern = Regex(
        """(?<![\p{L}\p{N}])(?:underage|minor(?:s)?|child(?:ren)?|kid(?:s)?|teen(?:s|age|ager|agers)?|young|schoolgirl|schoolboy|loli(?:ta)?|jailbait|minderårig(?:a|t)?|tonår(?:ing|ingar|ingarna)?|ungdom(?:ar|arna)?|barn(?:et|en)?|barely\s+legal)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )

    fun containsBlockedAgeTerm(text: String): Boolean = disallowedQueryPattern.containsMatchIn(text)

    fun expand(rawInput: String): ExpandedQuery {
        val input = rawInput.trim().lowercase(Locale.ROOT)
        if (input.isBlank()) {
            return ExpandedQuery("", emptyList(), explanation = "Tom sökfras")
        }

        if (disallowedQueryPattern.containsMatchIn(input)) {
            val reason = "Sökningen stoppades eftersom den innehåller termer som kan avse minderåriga."
            return ExpandedQuery(
                searchTerms = "",
                negativeKeywords = emptyList(),
                explanation = reason,
                blockedReason = reason
            )
        }

        val negatives = mutableSetOf<String>()
        val positives = mutableListOf<String>()

        // 1. Hitta explicita negativa fraser ("no anal", "utan rough", "never X")
        var cleaned = input
        for (pattern in negativePatterns) {
            val regex = Regex("$pattern([a-z0-9äöå\\-]+)", RegexOption.IGNORE_CASE)
            regex.findAll(cleaned).forEach { match ->
                val word = match.groupValues[1].trim()
                if (word.length > 1) {
                    negatives.add(word)
                    // Ta bort hela "no word" från texten så den inte blir sökterm
                    cleaned = cleaned.replace(match.value, " ")
                }
            }
        }

        // 2. Dela upp resten i ord
        val tokens = cleaned
            .split(Regex("[\\s,;.!?]+"))
            .map { it.trim() }
            .filter { it.length > 1 }

        for (token in tokens) {
            if (token in negatives) continue
            val expanded = expansions[token]
            if (expanded != null) positives.addAll(expanded) else positives.add(token)
        }

        // 3. Bygg söksträng (unika termer, max rimlig längd)
        val searchTerms = positives
            .distinct()
            .take(8)
            .joinToString(" ")

        val explanation = buildString {
            append("Lokalt analyserat: ")
            if (searchTerms.isNotBlank()) append("söker “$searchTerms”")
            if (negatives.isNotEmpty()) {
                if (searchTerms.isNotBlank()) append(" • ")
                append("filtrerar bort: ${negatives.joinToString(", ")}")
            }
        }

        return ExpandedQuery(
            searchTerms = searchTerms,
            negativeKeywords = negatives.toList(),
            explanation = explanation
        )
    }
}
