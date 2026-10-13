package com.calculator.app.data.extractor

/**
 * En skrapregel som kan komma från nätet i stället för att vara inbyggd i appen.
 *
 * Regeln innehåller **bara data** – adresser, mönster och CSS-selektorer – aldrig kod.
 * Allt valideras innan det används: adresser måste vara HTTPS, sök-URL:en måste innehålla
 * `{query}`, mönstret måste gå att kompilera och listorna hålls korta. En regel som inte
 * klarar kontrollen kastas, medan de övriga behålls.
 */
data class ScrapeRule(
    val siteName: String,
    val baseUrl: String,
    val searchUrlTemplate: String,
    val hrefPattern: Regex?,
    val requireHrefContains: String?,
    val idGroup: Int,
    val titleSelectors: List<String>,
    val thumbAttributes: List<String>,
    val durationSelectors: List<String>,
    val limit: Int
) {
    val supportsPagination: Boolean get() = searchUrlTemplate.contains(PAGE_PLACEHOLDER)

    /** Bygger sök-URL:en. Returnerar null om frågan är tom eller adressen inte är HTTPS. */
    fun searchUrlFor(query: String, page: Int): String? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return null
        val url = searchUrlTemplate
            .replace(QUERY_PLACEHOLDER, Fetch.urlEncode(trimmed))
            .replace(PAGE_PLACEHOLDER, page.coerceAtLeast(1).toString())
        return url.takeIf { it.startsWith("https://") }
    }

    /** Gör om regeln till samma konfiguration som de inbyggda sajterna använder. */
    fun toListingConfig(): ListingConfig = ListingConfig(
        siteName = siteName,
        baseUrl = baseUrl,
        hrefPattern = hrefPattern ?: GENERIC_PATTERN,
        idGroup = idGroup,
        requireHrefContains = requireHrefContains,
        titleSelectors = titleSelectors.ifEmpty { ListingConfig.DEFAULT_TITLE_SELECTORS },
        thumbAttributes = thumbAttributes.ifEmpty { ListingConfig.DEFAULT_THUMB_ATTRIBUTES },
        durationSelectors = durationSelectors.ifEmpty { ListingConfig.DEFAULT_DURATION_SELECTORS },
        limit = limit
    )

    companion object {
        const val QUERY_PLACEHOLDER = "{query}"
        const val PAGE_PLACEHOLDER = "{page}"

        private val GENERIC_PATTERN = Regex(".")
        private val SITE_ID = Regex("^[a-z0-9]{2,40}$")
        private const val MAX_TEMPLATE_LENGTH = 300
        private const val MAX_PATTERN_LENGTH = 240
        private const val MAX_SELECTORS = 20
        private const val MAX_SELECTOR_LENGTH = 80
        private const val MAX_LIMIT = 60

        /** Läser en regel ur ett JSON-objekt. Null om något obligatoriskt saknas eller är osäkert. */
        fun from(siteName: String, raw: Map<String, Any?>): ScrapeRule? {
            val site = siteName.trim().lowercase()
            if (!SITE_ID.matches(site)) return null

            val baseUrl = MiniJson.string(raw["baseUrl"])?.trim()?.trimEnd('/') ?: return null
            if (!baseUrl.startsWith("https://")) return null

            val template = MiniJson.string(raw["searchUrl"])?.trim() ?: return null
            if (!template.startsWith("https://")) return null
            if (!template.contains(QUERY_PLACEHOLDER)) return null
            if (template.length > MAX_TEMPLATE_LENGTH) return null

            val patternText = MiniJson.string(raw["hrefPattern"])
            val pattern = if (patternText.isNullOrBlank()) {
                null
            } else {
                if (patternText.length > MAX_PATTERN_LENGTH) return null
                runCatching { Regex(patternText) }.getOrNull() ?: return null
            }

            return ScrapeRule(
                siteName = site,
                baseUrl = baseUrl,
                searchUrlTemplate = template,
                hrefPattern = pattern,
                requireHrefContains = MiniJson.string(raw["requireHrefContains"])
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.take(60),
                idGroup = (raw["idGroup"] as? Double)?.toInt()?.takeIf { it in 0..9 } ?: 1,
                titleSelectors = sanitize(MiniJson.strings(raw["titleSelectors"])),
                thumbAttributes = sanitize(MiniJson.strings(raw["thumbAttributes"])),
                durationSelectors = sanitize(MiniJson.strings(raw["durationSelectors"])),
                limit = (raw["limit"] as? Double)?.toInt()?.coerceIn(1, MAX_LIMIT) ?: ListingConfig.DEFAULT_LIMIT
            )
        }

        private fun sanitize(values: List<String>): List<String> =
            values.asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && it.length <= MAX_SELECTOR_LENGTH }
                .take(MAX_SELECTORS)
                .toList()
    }
}

/**
 * Alla regler som hör till en version av regel-filen.
 *
 * [version] måste vara högre än den version appen redan använder, så att en gammal eller
 * manipulerad fil inte kan rulla tillbaka en fungerande konfiguration.
 */
data class ScrapeRules(
    val version: Int,
    val bySite: Map<String, ScrapeRule>,
    val rejected: Int = 0
) {
    fun forSite(siteName: String): ScrapeRule? = bySite[siteName.trim().lowercase()]

    /** Regler för sajter som appen inte redan har inbyggda – de läggs till i slutet. */
    fun newSites(existing: Collection<String>): List<ScrapeRule> =
        bySite.values.filterNot { it.siteName in existing }

    companion object {
        val Empty = ScrapeRules(version = 0, bySite = emptyMap())

        /** Hur många sajter en regel-fil får styra över. */
        const val MAX_SITES = 40

        /**
         * Tolkar en regel-fil. Returnerar null när JSON:en är trasig, versionen saknas
         * eller inte är nyare än [minVersion] – då behåller appen sina inbyggda regler.
         */
        fun parse(json: String?, minVersion: Int = 0): ScrapeRules? {
            if (json.isNullOrBlank()) return null
            val root = MiniJson.parse(json) as? Map<*, *> ?: return null
            val version = (root["version"] as? Double)?.toInt() ?: return null
            if (version <= minVersion || version <= 0) return null

            val sitesRaw = root["sites"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val parsed = LinkedHashMap<String, ScrapeRule>()
            var rejected = 0

            for ((key, value) in sitesRaw) {
                val name = key as? String
                val map = (value as? Map<*, *>)?.entries
                    ?.associate { (entryKey, entryValue) -> (entryKey as? String).orEmpty() to entryValue }
                if (name == null || map == null || parsed.size >= MAX_SITES) {
                    rejected++
                    continue
                }
                val rule = ScrapeRule.from(name, map)
                if (rule == null) {
                    rejected++
                } else {
                    parsed[rule.siteName] = rule
                }
            }

            return ScrapeRules(version = version, bySite = parsed, rejected = rejected)
        }
    }
}
