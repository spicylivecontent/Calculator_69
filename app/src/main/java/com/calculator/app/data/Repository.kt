package com.calculator.app.data

import android.content.Context
import com.calculator.app.data.entity.*
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.Flow

class Repository(context: Context) {

    private val db = AppDatabase.getInstance(context)

    // === Videos ===
    fun getAllVideos(): Flow<List<VideoEntity>> = db.videoDao().getAllVideos()
    suspend fun insertVideo(video: VideoEntity) = db.videoDao().insertVideo(video)
    suspend fun insertVideos(videos: List<VideoEntity>) = db.videoDao().insertVideos(videos)

    /**
     * Search only the locally saved library. FTS5 uses BM25 ranking and a highlighted snippet;
     * older SQLite builds fall back to a bounded LIKE query without affecting remote searches.
     */
    suspend fun searchSavedVideos(query: String, limit: Int = 50): List<LocalVideoSearchHit> {
        val expression = Fts5VideoIndex.matchExpression(query)
        if (expression.isBlank()) return emptyList()
        val safeLimit = limit.coerceIn(1, 200)
        val sql = if (Fts5VideoIndex.isAvailable()) {
            """
            SELECT v.id, v.title, v.thumbnailUrl, v.durationSeconds, v.sourceSite,
                   v.detailPageUrl, v.tags, v.quality,
                   bm25(video_search_fts) AS relevance,
                   snippet(video_search_fts, 0, '[', ']', '…', 18) AS snippet
            FROM video_search_fts
            JOIN videos AS v ON v.rowid = video_search_fts.rowid
            WHERE video_search_fts MATCH ?
            ORDER BY relevance ASC
            LIMIT ?
            """.trimIndent()
        } else {
            """
            SELECT id, title, thumbnailUrl, durationSeconds, sourceSite, detailPageUrl,
                   tags, quality, 0.0 AS relevance, title AS snippet
            FROM videos
            WHERE title LIKE ? OR tags LIKE ? OR sourceSite LIKE ?
            ORDER BY addedAt DESC
            LIMIT ?
            """.trimIndent()
        }
        val args: Array<Any> = if (Fts5VideoIndex.isAvailable()) {
            arrayOf(expression, safeLimit)
        } else {
            val pattern = "%${query.trim().take(120)}%"
            arrayOf(pattern, pattern, pattern, safeLimit)
        }
        return runCatching { db.videoDao().searchLocalVideoHits(SimpleSQLiteQuery(sql, args)) }
            .getOrDefault(emptyList())
    }

    // === Favorites ===
    fun getFavoriteVideos(): Flow<List<VideoEntity>> = db.favoriteDao().getFavoriteVideos()
    fun getFavoriteCount(): Flow<Int> = db.favoriteDao().getFavoriteCount()
    suspend fun isFavorite(videoId: String) = db.favoriteDao().isFavorite(videoId)
    suspend fun addFavorite(videoId: String) = db.favoriteDao().addFavorite(FavoriteEntity(videoId))
    suspend fun removeFavorite(videoId: String) = db.favoriteDao().removeFavorite(videoId)

    // === Watch Later ===
    fun getWatchLaterVideos(): Flow<List<VideoEntity>> = db.watchLaterDao().getWatchLaterVideos()
    fun getWatchLaterCount(): Flow<Int> = db.watchLaterDao().getCount()
    suspend fun addWatchLater(videoId: String) = db.watchLaterDao().add(WatchLaterEntity(videoId))
    suspend fun removeWatchLater(videoId: String) = db.watchLaterDao().remove(videoId)

    // === Dislike Rules ===
    fun getDislikeRules(): Flow<List<DislikeRuleEntity>> = db.dislikeRuleDao().getAllRules()
    fun getDislikeCount(): Flow<Int> = db.dislikeRuleDao().getCount()
    suspend fun addDislikeRule(type: String, value: String) =
        db.dislikeRuleDao().insertRule(DislikeRuleEntity(type = type, value = value))
    suspend fun removeDislikeRule(rule: DislikeRuleEntity) = db.dislikeRuleDao().deleteRule(rule)

    // === Search Presets ===
    fun getSearchPresets(): Flow<List<SearchPresetEntity>> = db.searchPresetDao().getAllPresets()
    fun getPresetCount(): Flow<Int> = db.searchPresetDao().getCount()
    suspend fun addSearchPreset(name: String, query: String, negative: String = "") =
        db.searchPresetDao().insertPreset(SearchPresetEntity(name = name, query = query, negativeKeywords = negative))
    suspend fun removeSearchPreset(preset: SearchPresetEntity) = db.searchPresetDao().deletePreset(preset)

    // === Settings ===
    suspend fun getSetting(key: String): String? = db.settingsDao().get(key)
    suspend fun setSetting(key: String, value: String) =
        db.settingsDao().set(AppSettingsEntity(key, value))
}
