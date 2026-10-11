package com.calculator.app.ai

import java.util.Locale

/**
 * Säkerhetsregler som gäller oavsett vad användaren skriver.
 *
 * Alla sökningar och träffar filtreras mot listan innan de når fram till sajterna
 * respektive resultatlistan. Reglerna är avsiktligt breda: hellre att en otydlig
 * sökning stoppas än att innehåll som kan avse minderåriga visas.
 */
object AgeSafety {

    private val blockedPattern = Regex(
        """(?<![\p{L}\p{N}])(?:""" +
            """underage[d]?|under-age|preteen|pre-teen|minor(?:s)?|child(?:ren|s)?|kid(?:s)?|""" +
            """teen(?:s|age|ager|agers)?|young|youth|schoolgirl|schoolboy|school\s?girl|loli(?:ta)?|""" +
            """jailbait|barely\s+legal|baby|toddler|infant|""" +
            """minderårig(?:a|t)?|tonår(?:ing|ingar|ingarna)?|ungdom(?:ar|arna)?|barn(?:et|en)?|""" +
            """skolbarn|småbarn|spädbarn|bäbis""" +
            """)(?![\p{L}\p{N}])""",
        RegexOption.IGNORE_CASE
    )

    /** Rekommenderad förklaring när en sökning blockeras. */
    const val BLOCKED_MESSAGE: String =
        "Sökningen stoppades eftersom den innehåller termer som kan avse minderåriga. " +
            "Appen söker bara efter innehåll med vuxna medverkande."

    fun containsBlockedTerm(text: String): Boolean {
        if (text.isBlank()) return false
        return blockedPattern.containsMatchIn(text.lowercase(Locale.ROOT))
    }

    /** True om hela söksträngen ska avvisas. */
    fun blocksQuery(text: String): Boolean = containsBlockedTerm(text)
}
