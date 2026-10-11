package com.calculator.app.data.model

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Gör om textlängder från sajterna ("12:34", "1:02:03", "12 min", "1h 5min") till sekunder
 * så att resultaten kan filtreras och sorteras på längd.
 */
object DurationParser {

    private val wordPatterns = listOf(
        Regex("(\\d+)\\s*(?:h|hr|hrs|hour|hours|tim|timme|timmar)\\b") to 3_600,
        Regex("(\\d+)\\s*(?:m|min|mins|minute|minutes|minut|minuter)\\b") to 60,
        Regex("(\\d+)\\s*(?:s|sec|secs|second|seconds|sek|sekund|sekunder)\\b") to 1
    )

    fun toSeconds(raw: String?): Int? {
        val text = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (text.isEmpty()) return null

        if (text.contains(':')) {
            val parts = text.split(':').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty() || parts.size > 3) return null
            if (parts.any { part -> part.isNotEmpty() && !part.all { it.isDigit() } }) return null
            val numbers = parts.map { it.toDoubleOrNull() }
            if (numbers.any { it == null }) return null
            var total = 0.0
            for (value in numbers) total = total * 60.0 + (value ?: 0.0)
            return total.roundToInt().takeIf { it > 0 }
        }

        var totalSeconds = 0
        var found = false
        for ((pattern, multiplier) in wordPatterns) {
            val match = pattern.find(text) ?: continue
            val value = match.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
            totalSeconds += value * multiplier
            found = true
        }
        if (found) return totalSeconds.takeIf { it > 0 }

        // Rena tal utan enhet är tvetydiga – hoppa över hellre än att gissa fel.
        return null
    }

    /** Formaterar sekunder som "12:34" / "1:02:03". */
    fun format(seconds: Int?): String? {
        if (seconds == null || seconds <= 0) return null
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, secs)
        }
    }
}
