package com.calculator.app.data.model

/**
 * En enskild sökfråga som skickas till en sajt.
 *
 * @param text själva frågan
 * @param terms de termer frågan representerar (används för relevansberäkning)
 * @param isPrimary true för huvudsökningen, false för varianter som breddar träffbilden
 */
data class SearchQuery(
    val text: String,
    val terms: List<String>,
    val isPrimary: Boolean = false
)

enum class SortOrder(val label: String) {
    RELEVANCE("Relevans"),
    LONGEST("Längst först"),
    SHORTEST("Kortast först"),
    SITE("Källa")
}

enum class LengthFilter(val label: String, val minSeconds: Int?, val maxSeconds: Int?) {
    ALL("Alla längder", null, null),
    SHORT("Under 5 min", null, 5 * 60),
    MEDIUM("5–20 min", 5 * 60, 20 * 60),
    LONG("Över 20 min", 20 * 60, null);

    fun matches(seconds: Int?): Boolean {
        if (this == ALL) return true
        if (seconds == null) return false
        if (minSeconds != null && seconds < minSeconds) return false
        if (maxSeconds != null && seconds >= maxSeconds) return false
        return true
    }
}
