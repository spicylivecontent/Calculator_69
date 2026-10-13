package com.calculator.app.data

import android.content.Context
import android.content.SharedPreferences
import com.calculator.app.ui.web.AdBlockLevel

/** Standardkvalitet i spelaren. */
enum class PreferredQuality(val id: String, val label: String) {
    AUTO("auto", "Automatisk (rekommenderad)"),
    HIGHEST("highest", "Högsta möjliga"),
    P1080("1080", "1080p max"),
    P720("720", "720p max"),
    P480("480", "480p max"),
    DATA_SAVER("data", "Spara data");

    companion object {
        fun fromId(id: String?): PreferredQuality = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * Användarens inställningar för sökning och uppspelning.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var onlyPlayable: Boolean
        get() = prefs.getBoolean(KEY_ONLY_PLAYABLE, true)
        set(value) = prefs.edit().putBoolean(KEY_ONLY_PLAYABLE, value).apply()

    var skipSeconds: Int
        get() = prefs.getInt(KEY_SKIP_SECONDS, DEFAULT_SKIP_SECONDS).coerceIn(5, 60)
        set(value) = prefs.edit().putInt(KEY_SKIP_SECONDS, value.coerceIn(5, 60)).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var gestureSeeking: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_SEEKING, true)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_SEEKING, value).apply()

    var resumePlayback: Boolean
        get() = prefs.getBoolean(KEY_RESUME, true)
        set(value) = prefs.edit().putBoolean(KEY_RESUME, value).apply()

    var preferredQuality: PreferredQuality
        get() = PreferredQuality.fromId(prefs.getString(KEY_QUALITY, PreferredQuality.AUTO.id))
        set(value) = prefs.edit().putString(KEY_QUALITY, value.id).apply()

    /**
     * Hämtar uppdaterade skrapregler från appens eget repo (högst en gång i dygnet).
     * Endast data – adresser och mönster – aldrig kod.
     */
    var remoteRulesEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMOTE_RULES, true)
        set(value) = prefs.edit().putBoolean(KEY_REMOTE_RULES, value).apply()

    /** Hur hårt WebView-spelaren blockerar annonser och omdirigeringar. */
    var adBlockLevel: AdBlockLevel
        get() = AdBlockLevel.fromId(prefs.getString(KEY_ADBLOCK, AdBlockLevel.STANDARD.id))
        set(value) = prefs.edit().putString(KEY_ADBLOCK, value.id).apply()

    fun searchHistory(): List<String> =
        prefs.getString(KEY_HISTORY, null)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun addSearchHistory(term: String) {
        val cleaned = term.trim().take(80)
        if (cleaned.isBlank()) return
        val updated = (listOf(cleaned) + searchHistory().filterNot { it.equals(cleaned, ignoreCase = true) })
            .take(MAX_HISTORY)
        prefs.edit().putString(KEY_HISTORY, updated.joinToString(SEPARATOR)).apply()
    }

    fun clearSearchHistory() = prefs.edit().remove(KEY_HISTORY).apply()

    private companion object {
        const val PREFS_NAME = "calculator_app_prefs"
        const val KEY_ONLY_PLAYABLE = "only_playable"
        const val KEY_SKIP_SECONDS = "skip_seconds"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_GESTURE_SEEKING = "gesture_seeking"
        const val KEY_RESUME = "resume_playback"
        const val KEY_QUALITY = "preferred_quality"
        const val KEY_ADBLOCK = "adblock_level"
        const val KEY_REMOTE_RULES = "remote_rules_enabled"
        const val KEY_HISTORY = "search_history"
        const val SEPARATOR = "\n"
        const val MAX_HISTORY = 12
        const val DEFAULT_SKIP_SECONDS = 10
    }
}
