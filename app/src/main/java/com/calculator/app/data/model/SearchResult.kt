package com.calculator.app.data.model

/** Hur långt kontrollen av en träff har kommit. */
enum class Playability {
    /** Ännu inte kontrollerad. */
    UNVERIFIED,

    /** Kontroll pågår just nu. */
    CHECKING,

    /** Streamen svarar som video/HLS och kan spelas. */
    PLAYABLE,

    /** Streamen kunde inte verifieras – visas bara om användaren vill det. */
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
    /** T.ex. "1080p" eller "Upp till 4K" när källan avslöjar vilken kvalitet som finns. */
    val qualityLabel: String? = null,
    val durationSeconds: Int? = null,
    /** Vilka söktermer som gav träffen (används för relevans). */
    val matchedTerms: List<String> = emptyList(),
    /** Beräknad relevans mot sökningen. */
    val relevance: Int = 0
) {
    val isPlayable: Boolean get() = playability == Playability.PLAYABLE

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
            "porntrex" to "PornTrex"
        )

        fun prettySiteName(site: String): String =
            siteLabels[site.lowercase()] ?: site.replaceFirstChar { it.uppercase() }
    }
}
