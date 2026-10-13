package com.calculator.app.data

import android.content.Context
import android.content.SharedPreferences
import com.calculator.app.security.SecureValue
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
 * När vaulten ska låsas av sig själv.
 *
 * Låsningen kräver alltid kod + biometri för att öppna igen – den stänger bara vyn.
 */
enum class AutoLock(
    val id: String,
    val label: String,
    val shortLabel: String,
    val timeoutMillis: Long
) {
    ON_BACKGROUND("background", "Direkt när appen lämnas", "Direkt", 0L),
    AFTER_1_MIN("1min", "Efter 1 minut utan att skärmen rörs", "1 min", 60_000L),
    AFTER_5_MIN("5min", "Efter 5 minuter utan att skärmen rörs", "5 min", 300_000L),
    NEVER("never", "Aldrig – vaulten låses bara med panikknappen", "Aldrig", -1L);

    companion object {
        fun fromId(id: String?): AutoLock = entries.firstOrNull { it.id == id } ?: ON_BACKGROUND
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

    /** Sajter användaren stängt av permanent (tomt = alla är på). */
    fun disabledSites(): Set<String> =
        prefs.getStringSet(KEY_DISABLED_SITES, emptySet()).orEmpty().toSet()

    fun setSiteEnabled(siteId: String, enabled: Boolean) {
        val updated = disabledSites().toMutableSet()
        if (enabled) updated.remove(siteId) else updated.add(siteId)
        prefs.edit().putStringSet(KEY_DISABLED_SITES, updated).apply()
    }

    /** När vaulten låser sig själv. */
    var autoLock: AutoLock
        get() = AutoLock.fromId(prefs.getString(KEY_AUTO_LOCK, AutoLock.ON_BACKGROUND.id))
        set(value) = prefs.edit().putString(KEY_AUTO_LOCK, value.id).apply()

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

    /** Sökhistoriken sparas krypterad (se [SecureValue]). */
    fun searchHistory(): List<String> =
        SecureValue.read(prefs.getString(KEY_HISTORY, null))
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun addSearchHistory(term: String) {
        val cleaned = term.trim().take(80)
        if (cleaned.isBlank()) return
        val updated = (listOf(cleaned) + searchHistory().filterNot { it.equals(cleaned, ignoreCase = true) })
            .take(MAX_HISTORY)
        val encrypted = SecureValue.write(updated.joinToString(SEPARATOR)) ?: return
        prefs.edit().putString(KEY_HISTORY, encrypted).apply()
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
        const val KEY_AUTO_LOCK = "auto_lock"
        const val KEY_DISABLED_SITES = "disabled_sites"
        const val KEY_HISTORY = "search_history"
        const val SEPARATOR = "\n"
        const val MAX_HISTORY = 12
        const val DEFAULT_SKIP_SECONDS = 10
    }
}
