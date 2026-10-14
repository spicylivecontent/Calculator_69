package com.calculator.app.data.model

enum class Playability {
    PLAYABLE,
    CHECKING,
    UNVERIFIED,
    UNPLAYABLE
}

data class SearchResult(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: String?,
    val detailUrl: String,
    val sourceSite: String = "eporner",
    val streamUrl: String? = null,
    val playability: Playability = Playability.UNVERIFIED,
    val tags: List<String> = emptyList(),
    val performers: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val description: String? = null,
    val comments: List<String> = emptyList(),
    val tagCount: Int = 0,
    val qualityLabel: String? = null,
    val durationSeconds: Int? = null,
    val matchedTerms: List<String> = emptyList(),
    val relevance: Int = 0
) {
    val isPlayable: Boolean get() = playability == Playability.PLAYABLE

    val isEnriched: Boolean
        get() = tagCount > 0 || description != null || performers.isNotEmpty() ||
            categories.isNotEmpty() || comments.isNotEmpty()

    val searchableText: String
        get() = buildString {
            append(title).append(' ')
            if (tags.isNotEmpty()) append(tags.joinToString(" ")).append(' ')
            if (performers.isNotEmpty()) append(performers.joinToString(" ")).append(' ')
            if (categories.isNotEmpty()) append(categories.joinToString(" ")).append(' ')
            description?.let { append(it).append(' ') }
            if (comments.isNotEmpty()) append(comments.joinToString(" "))
        }.trim()

    val embeddingText: String
        get() = searchableText

    fun coverageOf(terms: List<String>): Float {
        val wanted = terms.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) return 0f
        val haystack = searchableText.lowercase()
        return wanted.count { haystack.contains(it) }.toFloat() / wanted.size.toFloat()
    }

    fun tagsPreview(limit: Int = 4): List<String> = tags.take(limit)

    val siteLabel: String get() = prettySiteName(sourceSite)

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
