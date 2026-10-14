package com.calculator.app.data.model

/**
 * Hur långt kontrollen av en träff har kommit. Sedan uppspelningen flyttats till
 * WebView-spelaren handlar kontrollen om att detaljsidan svarar och att dess
 * information kunnat läsas in – inte om en extraherad videoström.
 */
enum class Playability {
    /** Sidan är hämtad och innehållet finns kvar. */
    PLAYABLE,

    /** Kontroll och inläsning av information pågår just nu. */
    CHECKING,

    /** Ännu inte kontrollerad. */
    UNVERIFIED,

    /** Sidan är borta eller innehållet borttaget. */
    UNPLAYABLE
}

data class SearchResult(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: String?,
    val detailUrl: String,
    val sourceSite: String = "eporner",
    /** Verifierad direkt mediaström (HTTPS) när träffen gått igenom spelbarhetskontrollen. */
    val streamUrl: String? = null,
    val playability: Playability = Playability.UNVERIFIED,
    /** Taggar, kategorier och medverkande som skrapats från detaljsidan. */
    val tags: List<String> = emptyList(),
    /** Medverkande (performers/modeller) från detaljsidan. */
    val performers: List<String> = emptyList(),
    /** Kategorier/kanaler från detaljsidan. */
    val categories: List<String> = emptyList(),
    /** Beskrivningen från detaljsidan ("allt som händer i filmen" så långt sajten skriver det). */
    val description: String? = null,
    /** Toppkommentarer skrapade från detaljsidan (ger extra signal om vad som händer i videon). */
    val comments: List<String> = emptyList(),
    /** Antal skrapade nyckelord – används för att visa hur mycket information vi fick. */
    val tagCount: Int = 0,
    /** T.ex. "1080p" eller "Upp till 4K" när källan avslöjar vilken kvalitet som finns. */
    val qualityLabel: String? = null,
    val durationSeconds: Int? = null,
    /** Vilka söktermer som gav träffen (används för relevans). */
    val matchedTerms: List<String> = emptyList(),
    /** Beräknad relevans mot sökningen. */
    val relevance: Int = 0
) {
    val isPlayable: Boolean get() = playability == Playability.PLAYABLE

    /** True när detaljsidans information (taggar/beskrivning/medverkande/kommentarer) har lästs in. */
    val isEnriched: Boolean
        get() = tagCount > 0 || description != null || performers.isNotEmpty() ||
            categories.isNotEmpty() || comments.isNotEmpty()

    /** Allt skrapat innehåll samlat – används av den avancerade sökningen och AI-embedding. */
    val searchableText: String
        get() = buildString {
            append(title).append(' ')
            if (tags.isNotEmpty()) append(tags.joinToString(" ")).append(' ')
            if (performers.isNotEmpty()) append(performers.joinToString(" ")).append(' ')
            if (categories.isNotEmpty()) append(categories.joinToString(" ")).append(' ')
            description?.let { append(it).append(' ') }
            if (comments.isNotEmpty()) append(comments.joinToString(" "))
        }.trim()

    /** Rik text som skickas till den lokala embedding-modellen. */
    val embeddingText: String
        get() = searchableText

    /** Hur stor del av söktermerna som finns i den skrapade informationen (0f–1f). */
    fun coverageOf(terms: List<String>): Float {
        val wanted = terms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) return 0f
        return wanted.count { SearchTextMatcher.containsTerm(searchableText, it) }
            .toFloat() / wanted.size.toFloat()
    }

    fun tagsPreview(limit: Int = 4): List<String> = tags.take(limit)

    val siteLabel: String get() = prettySiteName(sourceSite)

    /** Kort etikett för tid: "12:34" eller "1:02:03". */
    val durationLabel: String? get() = duration?.takeIf { it.isNotBlank() }

    companion object {
        private val siteLabels = mapOf(
            "eporner" to "Eporner",
            "spankbang" to "SpankBang",
            "hqporner" to "HQporner",
            "xvideos" to "XVideos",
            "xnxx" to "XNXX",
            "xhamster" to "xHamster",
            "pornhub" to "Pornhub",
            "beeg" to "Beeg",
            "youporn" to "YouPorn",
            "porntrex" to "PornTrex",
            "luxuretv" to "LuxureTV"
        )

        fun prettySiteName(site: String): String =
            siteLabels[site.lowercase()] ?: site.replaceFirstChar { it.uppercase() }
    }
}
