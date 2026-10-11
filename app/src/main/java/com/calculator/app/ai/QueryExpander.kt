package com.calculator.app.ai

/**
 * Lättvikts on-device "AI"-expander.
 * Körs 100 % lokalt – ingen molntjänst, ingen stor modell.
 *
 * Den tolkar naturligt språk, plockar ut:
 * - Positiva söktermer
 * - Negativa / förbjudna termer
 * - Stil / kvalitet / längd-hints
 *
 * Resultatet används för att bygga en renare sökfråga + filter.
 *
 * Senare kan denna klass bytas ut mot en riktig TFLite / ONNX-modell
 * utan att ändra resten av appen.
 */
class QueryExpander {

    data class ExpandedQuery(
        val searchTerms: String,          // Det som skickas till sajterna
        val negativeKeywords: List<String>, // Extra filter
        val preferredQuality: String? = null,
        val maxDurationMinutes: Int? = null,
        val explanation: String           // Kort förklaring till användaren
    )

    // Vanliga negativa mönster (svenska + engelska)
    private val negativePatterns = listOf(
        "no ", "not ", "utan ", "ingen ", "inte ", "without ", "exclude ",
        "absolut inte ", "aldrig ", "never ", "förbjudet ", "undvik "
    )

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
        "young" to listOf("young", "teen", "18+"),
        "milf" to listOf("milf", "mature", "mom"),
        "lesbian" to listOf("lesbian", "girl on girl", "gg"),
        "solo" to listOf("solo", "masturbation"),
        "couple" to listOf("couple", "boyfriend", "girlfriend")
    )

    // Ord som oftast är negativa när de dyker upp efter "no/utan/..."
    private val commonNegatives = setOf(
        "anal", "rough", "hardcore", "bdsm", "pain", "force", "rape",
        "creampie", "cumshot", "facial", "dp", "gangbang", "bbc",
        "old", "ugly", "fat", "skinny", "tattoo", "piercing",
        "squirting", "piss", "scat", "blood"
    )

    fun expand(rawInput: String): ExpandedQuery {
        val input = rawInput.trim().lowercase()
        if (input.isBlank()) {
            return ExpandedQuery("", emptyList(), explanation = "Tom sökfras")
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
            .split(Regex("[\\s,;.!?]+") )
            .map { it.trim() }
            .filter { it.length > 1 }

        for (token in tokens) {
            when {
                // Redan identifierad som negativ
                negatives.contains(token) -> {}
                // Vanliga negativa ord som står ensamma kan också filtreras om användaren
                // skrivit dem i negativ kontext – här behandlar vi dem neutralt
                else -> {
                    // Expandera med synonymer om det finns
                    val expanded = expansions[token]
                    if (expanded != null) {
                        positives.addAll(expanded)
                    } else {
                        positives.add(token)
                    }
                }
            }
        }

        // 3. Extra: om användaren skrev ett vanligt negativt ord tillsammans med "no"/"utan"
        // har vi redan fångat det. Annars lämnar vi det.

        // 4. Bygg söksträng (unika termer, max rimlig längd)
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
            searchTerms = searchTerms.ifBlank { rawInput },
            negativeKeywords = negatives.toList(),
            explanation = explanation
        )
    }
}
