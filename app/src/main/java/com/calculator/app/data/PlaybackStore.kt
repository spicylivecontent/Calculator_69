package com.calculator.app.data

import android.content.Context
import com.calculator.app.data.model.SearchResult
import org.json.JSONArray
import org.json.JSONObject

/** En video användaren har spelat upp, med senaste position. */
data class RecentPlayback(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val sourceSite: String,
    val detailUrl: String,
    val streamUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val progressFraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val positionLabel: String
        get() {
            val totalSeconds = positionMs / 1000L
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    fun toSearchResult(): SearchResult = SearchResult(
        id = id,
        title = title,
        thumbnailUrl = thumbnailUrl,
        duration = null,
        detailUrl = detailUrl,
        sourceSite = sourceSite,
        streamUrl = streamUrl
    )
}

/**
 * Sparar uppspelningspositioner och senaste spelade videor så att appen kan
 * erbjuda "Fortsätt titta" och starta om där användaren slutade.
 */
class PlaybackStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun position(videoId: String): Long =
        prefs.getLong(positionKey(videoId), 0L)

    fun saveProgress(videoId: String, positionMs: Long, durationMs: Long) {
        prefs.edit()
            .putLong(positionKey(videoId), positionMs.coerceAtLeast(0L))
            .putLong(durationKey(videoId), durationMs.coerceAtLeast(0L))
            .apply()
    }

    fun savePlayed(result: SearchResult) {
        readAll().let { list ->
            val existing = list.firstOrNull { it.id == result.id }
            val entry = RecentPlayback(
                id = result.id,
                title = result.title,
                thumbnailUrl = result.thumbnailUrl,
                sourceSite = result.sourceSite,
                detailUrl = result.detailUrl,
                streamUrl = result.streamUrl ?: existing?.streamUrl,
                positionMs = existing?.positionMs ?: 0L,
                durationMs = existing?.durationMs ?: 0L,
                updatedAt = System.currentTimeMillis()
            )
            val updated = (listOf(entry) + list.filterNot { it.id == result.id }).take(MAX_ENTRIES)
            writeAll(updated)
        }
    }

    fun recent(limit: Int = MAX_ENTRIES): List<RecentPlayback> = readAll().take(limit)

    /** Videor som användaren inte tittat klart på, senast först. */
    fun continueWatching(limit: Int = 10): List<RecentPlayback> =
        readAll()
            .filter { it.positionMs > RESUME_THRESHOLD_MS }
            .filter { it.durationMs <= 0L || it.positionMs < it.durationMs - RESUME_END_MARGIN_MS }
            .take(limit)

    fun clear() = prefs.edit().remove(KEY_ENTRIES).apply()

    private fun readAll(): List<RecentPlayback> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                RecentPlayback(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    thumbnailUrl = item.optString("thumb").takeIf { it.isNotBlank() && it != "null" },
                    sourceSite = item.optString("site"),
                    detailUrl = item.optString("detail"),
                    streamUrl = item.optString("stream").takeIf { it.isNotBlank() && it != "null" },
                    positionMs = item.optLong("pos"),
                    durationMs = item.optLong("dur"),
                    updatedAt = item.optLong("at")
                )
            }.filter { it.id.isNotBlank() && it.detailUrl.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeAll(entries: List<RecentPlayback>) {
        val array = JSONArray()
        for (entry in entries) {
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title)
                    .put("thumb", entry.thumbnailUrl ?: "")
                    .put("site", entry.sourceSite)
                    .put("detail", entry.detailUrl)
                    .put("stream", entry.streamUrl ?: "")
                    .put("pos", entry.positionMs)
                    .put("dur", entry.durationMs)
                    .put("at", entry.updatedAt)
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun positionKey(videoId: String) = "pos_$videoId"
    private fun durationKey(videoId: String) = "dur_$videoId"

    private companion object {
        const val PREFS_NAME = "calculator_playback"
        const val KEY_ENTRIES = "recent_entries"
        const val MAX_ENTRIES = 40
        const val RESUME_THRESHOLD_MS = 15_000L
        const val RESUME_END_MARGIN_MS = 30_000L
    }
}
