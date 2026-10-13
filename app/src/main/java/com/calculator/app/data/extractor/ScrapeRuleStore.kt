package com.calculator.app.data.extractor

import android.content.Context
import android.content.SharedPreferences
import com.calculator.app.data.AppPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Håller reda på skrapreglerna: senast hämtade version, när den hämtades och vad som
 * gäller just nu.
 *
 * Hämtningen är helt frivillig och får aldrig stå i vägen: misslyckas den används den
 * senast sparade versionen, och finns ingen sådan används de inbyggda reglerna. Bara data
 * sparas – texten tolkas med [ScrapeRules.parse] som kastar allt den inte förstår.
 */
class ScrapeRuleStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val preferences = AppPreferences(appContext)

    @Volatile
    private var cached: ScrapeRules? = null

    /** Vad som styr sökningen just nu. */
    enum class Source(val label: String) {
        BUILT_IN("Inbyggda regler"),
        CACHED("Hämtade regler"),
        UPDATED("Uppdaterade nu")
    }

    data class Status(
        val source: Source,
        val version: Int,
        val sites: Int,
        val fetchedAt: Long
    ) {
        /** Rad som visas i inställningarna. */
        fun label(): String = when {
            source == Source.BUILT_IN -> "Inbyggda regler (10 sajter)"
            else -> buildString {
                append(source.label)
                append(" v")
                append(version)
                append(" · ")
                append(sites)
                append(" sajter")
                if (fetchedAt > 0L) append(" · hämtade ${whenLabel(fetchedAt)}")
            }
        }

        private fun whenLabel(time: Long): String {
            val age = System.currentTimeMillis() - time
            if (age < 0) return "nyss"
            val minutes = age / 60_000
            if (minutes < 2) return "nyss"
            if (minutes < 60) return "för $minutes min sedan"
            val hours = minutes / 60
            if (hours < 24) return "för $hours h sedan"
            val days = hours / 24
            if (days < 7) return "för $days dagar sedan"
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
        }
    }

    sealed interface RefreshResult {
        /** Inget gjordes: avstängt, eller ingen ny version tillgänglig. */
        data object Skipped : RefreshResult

        /** Hämtningen eller tolkningen misslyckades – appen fortsätter som förut. */
        data object Failed : RefreshResult

        data class Updated(val version: Int, val sites: Int, val rejected: Int) : RefreshResult
    }

    /** Reglerna som gäller nu (minnescache → sparad fil → inga). */
    fun current(): ScrapeRules {
        cached?.let { return it }
        val loaded = if (preferences.remoteRulesEnabled) {
            ScrapeRules.parse(prefs.getString(KEY_JSON, null))
        } else {
            null
        }
        val rules = loaded ?: ScrapeRules.Empty
        cached = rules
        return rules
    }

    fun status(): Status {
        val version = prefs.getInt(KEY_VERSION, 0)
        val rules = current()
        return Status(
            source = when {
                version <= 0 -> Source.BUILT_IN
                else -> Source.CACHED
            },
            version = version,
            sites = rules.bySite.size,
            fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        )
    }

    /** Sant när hämtning är påslagen och det finns ett intervall kvar att vänta. */
    fun shouldRefresh(force: Boolean = false): Boolean {
        if (!preferences.remoteRulesEnabled) return false
        if (force) return true
        if (!prefs.contains(KEY_JSON)) return true
        val last = prefs.getLong(KEY_FETCHED_AT, 0L)
        return System.currentTimeMillis() - last >= REFRESH_INTERVAL_MS
    }

    /**
     * Hämtar regel-filen om det behövs. Returnerar vad som hände, men kastar aldrig:
     * en trasig regel-fil får inte påverka sökningen.
     */
    suspend fun refresh(force: Boolean = false): RefreshResult {
        if (!shouldRefresh(force)) return RefreshResult.Skipped

        val body = runCatching { Fetch.page(RULES_URL, timeoutMs = FETCH_TIMEOUT_MS) }.getOrNull()
            ?: return RefreshResult.Failed
        if (body.length > MAX_BODY_CHARS) return RefreshResult.Failed

        val parsed = ScrapeRules.parse(body, minVersion = prefs.getInt(KEY_VERSION, 0))
            ?: return RefreshResult.Failed

        prefs.edit()
            .putString(KEY_JSON, body)
            .putInt(KEY_VERSION, parsed.version)
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .apply()
        cached = parsed

        return RefreshResult.Updated(
            version = parsed.version,
            sites = parsed.bySite.size,
            rejected = parsed.rejected
        )
    }

    /** Går tillbaka till de inbyggda reglerna och glömmer den sparade filen. */
    fun reset() {
        prefs.edit().remove(KEY_JSON).remove(KEY_VERSION).remove(KEY_FETCHED_AT).apply()
        cached = ScrapeRules.Empty
    }

    /** Glömmer minnescachen, t.ex. när användaren slår av eller på hämtning. */
    fun invalidate() {
        cached = null
    }

    companion object {
        /**
         * Regel-filen ligger i appens eget öppna repo. Den innehåller bara adresser och
         * mönster – villkoren för varje sajt gäller fortfarande.
         */
        const val RULES_URL =
            "https://raw.githubusercontent.com/spicylivecontent/Calculator_69/main/scrape-rules.json"

        private const val PREFS_NAME = "calculator_scrape_rules"
        private const val KEY_JSON = "rules_json"
        private const val KEY_VERSION = "rules_version"
        private const val KEY_FETCHED_AT = "rules_fetched_at"
        private const val FETCH_TIMEOUT_MS = 8_000L
        private const val MAX_BODY_CHARS = 200_000
        private const val REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}
