package com.calculator.app.data

import android.content.Context
import com.calculator.app.data.model.SearchResult
import com.calculator.app.security.SecureValue
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
 *
 * Titlar, adresser och positioner är känsliga, därför sparas de **krypterade**
 * ([SecureValue]) och positionerna ligger i en enda krypterad karta i stället för i
 * nycklar som `pos_<video-id>` – annars hade videons id läckt via nyckelnamnet.
 */
class PlaybackStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun position(videoId: String): Long = readPositions()[videoId]?.first ?: 0L

    fun saveProgress(videoId: String, positionMs: Long, durationMs: Long) {
        val positions = readPositions().toMutableMap()
        positions[videoId] = positionMs.coerceAtLeast(0L) to durationMs.coerceAtLeast(0L)
        writePositions(positions)
    }

    /** videoId -> (position, längd) */
    private fun readPositions(): Map<String, Pair<Long, Long>> {
        migrateLegacyPositions()
        val raw = SecureValue.read(prefs.getString(KEY_POSITIONS, null)) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap {
                for (id in json.keys()) {
                    val entry = json.optJSONArray(id) ?: continue
                    put(id, entry.optLong(0) to entry.optLong(1))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writePositions(positions: Map<String, Pair<Long, Long>>) {
        val json = JSONObject()
        for ((id, value) in positions) {
            json.put(id, JSONArray().put(value.first).put(value.second))
        }
        prefs.edit().putString(KEY_POSITIONS, SecureValue.write(json.toString())).apply()
    }

    /** Flyttar gamla `pos_<id>`/`dur_<id>`-nycklar till den krypterade kartan, en gång. */
    private fun migrateLegacyPositions() {
        if (prefs.contains(KEY_POSITIONS)) return
        val ids = prefs.all.keys
            .filter { it.startsWith(LEGACY_POSITION_PREFIX) }
            .map { it.removePrefix(LEGACY_POSITION_PREFIX) }
        if (ids.isEmpty()) return

        val migrated = buildMap {
            for (id in ids) {
                val position = prefs.getLong(LEGACY_POSITION_PREFIX + id, 0L)
                val duration = prefs.getLong(LEGACY_DURATION_PREFIX + id, 0L)
                if (position > 0L || duration > 0L) put(id, position to duration)
            }
        }

        val editor = prefs.edit()
        for (id in ids) {
            editor.remove(LEGACY_POSITION_PREFIX + id)
            editor.remove(LEGACY_DURATION_PREFIX + id)
        }
        editor.apply()

        if (migrated.isNotEmpty()) writePositions(migrated)
    }

    private fun readAll(): List<RecentPlayback> {
        val raw = SecureValue.read(prefs.getString(KEY_ENTRIES, null)) ?: return emptyList()
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
        prefs.edit().putString(KEY_ENTRIES, SecureValue.write(array.toString())).apply()
    }

    private companion object {
        const val PREFS_NAME = "calculator_playback"
        const val KEY_ENTRIES = "recent_entries"
        const val KEY_POSITIONS = "positions"
        const val LEGACY_POSITION_PREFIX = "pos_"
        const val LEGACY_DURATION_PREFIX = "dur_"
        const val MAX_ENTRIES = 40
        const val RESUME_THRESHOLD_MS = 15_000L
        const val RESUME_END_MARGIN_MS = 30_000L
    }
}
