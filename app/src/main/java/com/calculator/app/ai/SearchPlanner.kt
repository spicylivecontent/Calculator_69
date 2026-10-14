package com.calculator.app.ai

import com.calculator.app.data.model.SearchQuery
import java.text.Normalizer
import java.util.Locale

/**
 * Gör om användarens text (och valda kategorier) till ett antal konkreta sökfrågor.
 *
 * Strategi för träffsäkerhet:
 *  - Sällsynta termer (scat, atm, fisting …) söks separat först så de inte drunknar
 *  - Vanliga termer kombineras med sällsynta i mindre grupper
 *  - Negationer blir filter, svenska ord översätts till sajternas engelska index
 */
class SearchPlanner {

    data class Plan(
        val queries: List<SearchQuery>,
        val negatives: List<String>,
        val terms: List<String>,
        val explanation: String,
        val blockedReason: String? = null
    ) {
        val isEmpty: Boolean get() = queries.isEmpty()
    }

    fun plan(
        rawInput: String,
        categoryTerms: List<String> = emptyList(),
        extraNegatives: List<String> = emptyList(),
        maxQueries: Int = DEFAULT_MAX_QUERIES
    ): Plan {
        val input = rawInput.trim()
        val safeCategoryTerms = categoryTerms.map { it.trim() }.filter { it.isNotBlank() }

        if (AgeSafety.blocksQuery(input) || safeCategoryTerms.any { AgeSafety.containsBlockedTerm(it) }) {
            return Plan(
                queries = emptyList(),
                negatives = emptyList(),
                terms = emptyList(),
                explanation = AgeSafety.BLOCKED_MESSAGE,
                blockedReason = AgeSafety.BLOCKED_MESSAGE
            )
        }

        if (input.isBlank() && safeCategoryTerms.isEmpty()) {
            return Plan(
                queries = emptyList(),
                negatives = emptyList(),
                terms = emptyList(),
                explanation = "Skriv ett sökord eller välj en kategori."
            )
        }

        val parsed = parseInput(input)
        val negatives = (parsed.negatives + extraNegatives.flatMap { splitNegatives(it) })
            .map { canonicalTerm(it) }
            .filter { it.isNotBlank() }
            .distinct()

        if (maxQueries <= 0) {
            return Plan(
                queries = emptyList(),
                negatives = negatives,
                terms = emptyList(),
                explanation = "Inga sökfrågor tillåts när maxQueries inte är positivt."
            )
        }

        val excluded = negatives.map { normalizeTerm(it) }.toSet()
        val positiveTerms = parsed.positives
            .map { canonicalTerm(it) }
            .filter { it.isNotBlank() && normalizeTerm(it) !in excluded }
            .distinct()
        val quotedPhrases = parsed.phrases
            .map { it.trim() }
            .filter { it.isNotBlank() && normalizeTerm(it) !in excluded }
            .distinct()

        val primaryTerms = (positiveTerms + safeCategoryTerms.filter { normalizeTerm(it) !in excluded })
            .distinct()
            .take(MAX_PRIMARY_TERMS)

        val queries = mutableListOf<SearchQuery>()
        fun addQuery(text: String, terms: List<String>, isPrimary: Boolean = false) {
            if (queries.size >= maxQueries) return
            val cleaned = text.trim()
            if (cleaned.isBlank()) return
            if (queries.any { it.text.equals(cleaned, ignoreCase = true) }) return
            queries.add(SearchQuery(text = cleaned, terms = terms, isPrimary = isPrimary))
        }

        val rare = primaryTerms.filter { normalizeTerm(it) in RARE_TERMS }
        val common = primaryTerms.filter { normalizeTerm(it) !in RARE_TERMS }

        for (term in rare) {
            addQuery(term, listOf(term))
        }

        for (r in rare) {
            for (c in common.take(3)) {
                addQuery("$r $c", listOf(r, c))
            }
        }

        for (phrase in quotedPhrases) {
            addQuery(phrase, listOf(phrase))
        }

        if (common.isNotEmpty()) {
            addQuery(common.joinToString(" "), common, isPrimary = rare.isEmpty())
        }

        if (primaryTerms.size > 1) {
            addQuery(primaryTerms.joinToString(" "), primaryTerms, isPrimary = rare.isEmpty() && common.isEmpty())
        }

        for (term in common.filter { it.length >= MIN_VARIANT_LENGTH }) {
            addQuery(term, listOf(term))
        }
        for (variant in parsed.synonymVariants.filter { normalizeTerm(it) !in excluded }.take(MAX_SYNONYM_VARIANTS)) {
            addQuery(variant, listOf(variant))
        }
        for (cat in safeCategoryTerms.filter { normalizeTerm(it) !in excluded }) {
            addQuery(cat, listOf(cat))
        }

        if (queries.isEmpty()) {
            return Plan(
                queries = emptyList(),
                negatives = negatives,
                terms = emptyList(),
                explanation = "Ange minst ett positivt sökord utöver exkluderingarna."
            )
        }

        return Plan(
            queries = queries,
            negatives = negatives,
            terms = queries.flatMap { it.terms }.distinct(),
            explanation = buildExplanation(queries, negatives)
        )
    }

    private data class ParsedInput(
        val positives: List<String>,
        val negatives: List<String>,
        val phrases: List<String>,
        val synonymVariants: List<String>
    )

    private fun parseInput(input: String): ParsedInput {
        if (input.isBlank()) return ParsedInput(emptyList(), emptyList(), emptyList(), emptyList())

        val negatives = mutableListOf<String>()
        val phrases = mutableListOf<String>()
        val working = StringBuilder(input)

        for (match in QUOTED_PATTERN.findAll(input)) {
            val phrase = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (phrase.length >= MIN_PHRASE_LENGTH) {
                val before = working.substring(0, match.range.first)
                val negation = Regex(
                    "(?:^|\\s)(?:" + NEGATION_PREFIXES.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } +
                        ")\\s*$|(?:^|\\s)-$", RegexOption.IGNORE_CASE
                ).find(before)
                if (negation != null) negatives.add(phrase) else phrases.add(phrase)
                val start = negation?.range?.first ?: match.range.first
                for (index in start until (match.range.last + 1)) {
                    working.setCharAt(index, ' ')
                }
            }
        }

        var remainder = working.toString()

        for (prefix in NEGATION_PREFIXES.sortedByDescending { it.length }) {
            val pattern = Regex(
                "(?<![\\p{L}\\p{N}])" + Regex.escape(prefix) + "\\s+([\\p{L}\\p{N}][\\p{L}\\p{N}\\-']*)",
                RegexOption.IGNORE_CASE
            )
            for (match in pattern.findAll(remainder)) {
                val word = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (word.length > 1) negatives.add(word)
            }
            remainder = pattern.replace(remainder, " ")
        }

        val minusPattern = Regex("(?:^|\\s)-([\\p{L}\\p{N}][\\p{L}\\p{N}\\-']*)")
        for (match in minusPattern.findAll(remainder)) {
            val word = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (word.length > 1) negatives.add(word)
        }
        remainder = minusPattern.replace(remainder, " ")

        val tokens = remainder
            .split(Regex("[\\s,;.!?/|()\\[\\]{}]+"))
            .map { it.trim().trim('-', '\'', '"') }
            .filter { it.length > 1 }
            .filter { it.lowercase(Locale.ROOT) !in STOP_WORDS }

        val positives = mutableListOf<String>()
        val synonymVariants = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val single = tokens[index]
            val bigram = if (index + 1 < tokens.size) "$single ${tokens[index + 1]}" else null
            val bigramMatch = bigram?.let { SYNONYMS[normalizeTerm(it)] }
            if (bigramMatch != null) {
                positives.add(bigramMatch.first())
                synonymVariants.addAll(bigramMatch.drop(1))
                index += 2
                continue
            }

            val match = SYNONYMS[normalizeTerm(single)]
            if (match != null) {
                positives.add(match.first())
                synonymVariants.addAll(match.drop(1))
            } else {
                positives.add(single)
            }
            index += 1
        }

        return ParsedInput(
            positives = positives.distinct(),
            negatives = negatives.distinct(),
            phrases = phrases.distinct(),
            synonymVariants = synonymVariants.distinct()
        )
    }

    private fun splitNegatives(raw: String): List<String> =
        raw.split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun canonicalTerm(term: String): String {
        val trimmed = term.trim()
        val normalized = normalizeTerm(trimmed)
        if (normalized.isBlank()) return ""
        return SYNONYMS[normalized]?.first() ?: trimmed
    }

    private fun normalizeTerm(term: String): String =
        Normalizer.normalize(term, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun buildExplanation(queries: List<SearchQuery>, negatives: List<String>): String = buildString {
        append("Söker: ")
        append(queries.joinToString("  •  ") { "“${it.text}”" })
        if (negatives.isNotEmpty()) {
            append("  •  filtrerar bort: ")
            append(negatives.joinToString(", "))
        }
    }

    private companion object {
        const val DEFAULT_MAX_QUERIES = 8
        const val MAX_PRIMARY_TERMS = 8
        const val MAX_SYNONYM_VARIANTS = 3
        const val MIN_VARIANT_LENGTH = 4
        const val MIN_PHRASE_LENGTH = 3

        val QUOTED_PATTERN = Regex("\"([^\"]{3,60})\"")

        val RARE_TERMS = setOf(
            "scat", "atm", "a2m", "fisting", "prolapse", "pegging", "cbt",
            "watersports", "golden shower", "squirting", "enema", "gap", "gape"
        )

        val NEGATION_PREFIXES = listOf(
            "absolut inte", "inte", "utan", "ingen", "aldrig", "förbjudet", "undvik", "exkludera",
            "never", "without", "excluding", "exclude", "avoid", "no", "not", "dont", "don't"
        )

        val STOP_WORDS = setOf(
            "the", "and", "with", "for", "from", "that", "this", "are", "was", "were",
            "och", "med", "att", "som", "den", "det", "har", "jag", "vill", "efter", "till",
            "or", "eller", "any", "all", "från", "fran"
        )

        val SYNONYMS: Map<String, List<String>> = mapOf(
            "brunett" to listOf("brunette"),
            "brunette" to listOf("brunette", "brown hair"),
            "blond" to listOf("blonde"),
            "blonde" to listOf("blonde"),
            "rodharig" to listOf("redhead"),
            "rott har" to listOf("redhead", "red hair"),
            "redhead" to listOf("redhead", "red hair"),
            "svartharig" to listOf("black hair"),
            "langt har" to listOf("long hair"),
            "kort har" to listOf("short hair"),
            "fracknar" to listOf("freckles"),
            "tatueringar" to listOf("tattoo"),
            "piercing" to listOf("piercing"),
            "naturlig" to listOf("natural"),
            "smink" to listOf("makeup", "glamour"),
            "kurvig" to listOf("curvy"),
            "kurvigare" to listOf("curvy"),
            "smal" to listOf("slim"),
            "slimmad" to listOf("slim"),
            "tranad" to listOf("fit body"),
            "valtranad" to listOf("fit body"),
            "lang" to listOf("tall"),
            "kort" to listOf("petite"),
            "stor byst" to listOf("busty"),
            "stora brost" to listOf("big tits"),
            "langa ben" to listOf("long legs"),
            "asiatisk" to listOf("asian"),
            "asiatiska" to listOf("asian"),
            "latin" to listOf("latina"),
            "latina" to listOf("latina", "latin", "hispanic"),
            "latinamerikansk" to listOf("latina"),
            "svart" to listOf("ebony"),
            "indisk" to listOf("indian"),
            "arabisk" to listOf("arab"),
            "europeisk" to listOf("european"),
            "skandinavisk" to listOf("scandinavian"),
            "par" to listOf("couple"),
            "tva" to listOf("couple"),
            "trekant" to listOf("threesome"),
            "grupp" to listOf("group sex", "group"),
            "lesbisk" to listOf("lesbian"),
            "lesbiska" to listOf("lesbian"),
            "bisexuell" to listOf("bisexual"),
            "swinger" to listOf("swinger"),
            "orgie" to listOf("orgy"),
            "klassisk" to listOf("classic sex"),
            "ridande" to listOf("cowgirl"),
            "massage" to listOf("massage"),
            "deepthroat" to listOf("deepthroat", "deep throat"),
            "masturbation" to listOf("masturbation"),
            "onani" to listOf("masturbation"),
            "knulla" to listOf("hardcore"),
            "hardcore" to listOf("hardcore"),
            "handjob" to listOf("handjob"),
            "squirt" to listOf("squirt", "squirting"),
            "squirting" to listOf("squirting", "squirt"),
            "sovrum" to listOf("bedroom"),
            "sang" to listOf("bed"),
            "kok" to listOf("kitchen"),
            "badrum" to listOf("bathroom"),
            "dusch" to listOf("shower"),
            "bastu" to listOf("sauna"),
            "utomhus" to listOf("outdoor"),
            "bil" to listOf("car"),
            "kontor" to listOf("office"),
            "strand" to listOf("beach"),
            "gym" to listOf("gym"),
            "hotell" to listOf("hotel"),
            "lagenhet" to listOf("apartment"),
            "romantisk" to listOf("romantic"),
            "sensuell" to listOf("sensual"),
            "passionerad" to listOf("passionate"),
            "lekfull" to listOf("playful"),
            "dominant" to listOf("dominant"),
            "underdanig" to listOf("submissive"),
            "langsam" to listOf("slow", "gentle"),
            "intensiv" to listOf("intense"),
            "rough" to listOf("rough", "hard"),
            "hard" to listOf("rough", "hard"),
            "mjuk" to listOf("gentle", "sensual"),
            "sjukskoterska" to listOf("nurse"),
            "polis" to listOf("police"),
            "chef" to listOf("boss"),
            "sekreterare" to listOf("secretary"),
            "hemmafru" to listOf("housewife"),
            "brud" to listOf("bride"),
            "underklader" to listOf("lingerie"),
            "strumpbyxor" to listOf("stockings"),
            "lader" to listOf("leather"),
            "narbild" to listOf("close up"),
            "amator" to listOf("amateur"),
            "amatorkamera" to listOf("amateur camera"),
            "handhallen" to listOf("handheld"),
            "spegel" to listOf("mirror"),
            "kamera" to listOf("camera"),
            "casting" to listOf("casting"),
            "langfilm" to listOf("full movie"),
            "retro" to listOf("retro"),
            "vintage" to listOf("vintage"),
            "fotfetisch" to listOf("feet fetish", "feet"),
            "fotter" to listOf("feet"),
            "leksaker" to listOf("sex toys", "dildo"),
            "dildo" to listOf("dildo", "toy", "vibrator"),
            "binda" to listOf("bondage"),
            "avslut" to listOf("cumshot"),
            "avslutning" to listOf("cumshot"),
            "milf" to listOf("milf", "mature"),
            "mogen" to listOf("mature"),
            "pov" to listOf("pov", "point of view"),
            "solo" to listOf("solo", "masturbation"),
            "atm" to listOf("atm", "ass to mouth", "a2m"),
            "a2m" to listOf("atm", "ass to mouth"),
            "ass to mouth" to listOf("ass to mouth", "atm"),
            "scat" to listOf("scat", "scat play", "scatology"),
            "nonstop" to listOf("nonstop", "continuous", "relentless"),
            "non stop" to listOf("nonstop", "continuous"),
            "dp" to listOf("dp", "double penetration"),
            "bj" to listOf("blowjob", "oral"),
            "pawg" to listOf("pawg", "curvy"),
            "creampie" to listOf("creampie"),
            "amateur" to listOf("amateur", "homemade"),
            "homemade" to listOf("homemade"),
            "outdoor" to listOf("outdoor"),
            "kitchen" to listOf("kitchen"),
            "bedroom" to listOf("bedroom"),
            "fisting" to listOf("fisting"),
            "anal" to listOf("anal")
        )
    }
}
