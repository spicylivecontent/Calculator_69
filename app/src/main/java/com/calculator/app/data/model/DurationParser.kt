package com.calculator.app.data.model

import java.util.Locale

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
            val parts = text.split(':').map { it.trim() }
            if (parts.size !in 2..3 || parts.any { it.isEmpty() || !it.all { c -> c in '0'..'9' } }) return null
            val numbers = parts.map { it.toLongOrNull() ?: return null }
            if (numbers.drop(1).any { it !in 0..59 }) return null
            var total = 0L
            for (value in numbers) {
                if (value > Int.MAX_VALUE || total > Int.MAX_VALUE / 60L) return null
                total = total * 60L + value
            }
            return total.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
        }

        var totalSeconds = 0L
        var found = false
        for ((pattern, multiplier) in wordPatterns) {
            val match = pattern.find(text) ?: continue
            val value = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
            if (value > Int.MAX_VALUE / multiplier.toLong()) return null
            totalSeconds += value * multiplier
            found = true
        }
        if (found) return totalSeconds.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()

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
