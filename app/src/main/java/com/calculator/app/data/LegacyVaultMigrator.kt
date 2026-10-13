package com.calculator.app.data

import android.content.Context
import androidx.room.Room
import com.calculator.app.data.entity.DislikeRuleEntity
import com.calculator.app.data.entity.FavoriteEntity
import com.calculator.app.data.entity.SearchPresetEntity
import com.calculator.app.data.entity.WatchLaterEntity

/**
 * Flyttar innehållet från en tidigare **okrypterad** databas till den krypterade.
 *
 * Allt skrivs till mål-databasen först; den gamla filen (med WAL- och SHM-filer) tas bort
 * först när överföringen lyckats. Misslyckas något lämnas den gamla filen kvar, så att
 * inget går förlorat.
 */
internal object LegacyVaultMigrator {

    /** Returnerar antalet överförda rader, eller null om ingen migrering behövdes. */
    suspend fun migrateIfNeeded(context: Context, target: AppDatabase, sourceName: String): Int? {
        val sourceFile = context.getDatabasePath(sourceName)
        if (!sourceFile.exists()) return null

        val source = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, sourceName).build()
        return try {
            val videos = source.videoDao().getAllVideosOnce()
            val favoriteIds = source.favoriteDao().getAllFavoriteIds()
            val watchLaterIds = source.watchLaterDao().getAllWatchLaterIds()
            val rules = source.dislikeRuleDao().getAllRulesOnce()
            val presets = source.searchPresetDao().getAllPresetsOnce()

            if (videos.isNotEmpty()) target.videoDao().insertVideos(videos)
            favoriteIds.forEach { id -> target.favoriteDao().addFavorite(FavoriteEntity(videoId = id)) }
            watchLaterIds.forEach { id -> target.watchLaterDao().add(WatchLaterEntity(videoId = id)) }
            rules.forEach { rule ->
                target.dislikeRuleDao().insertRule(
                    DislikeRuleEntity(type = rule.type, value = rule.value, createdAt = rule.createdAt)
                )
            }
            presets.forEach { preset ->
                target.searchPresetDao().insertPreset(
                    SearchPresetEntity(
                        name = preset.name,
                        query = preset.query,
                        negativeKeywords = preset.negativeKeywords,
                        createdAt = preset.createdAt
                    )
                )
            }

            val copied = videos.size + favoriteIds.size + watchLaterIds.size + rules.size + presets.size
            source.close()
            deleteDatabaseFiles(context, sourceName)
            copied
        } catch (_: Exception) {
            runCatching { source.close() }
            null
        }
    }

    private fun deleteDatabaseFiles(context: Context, name: String) {
        val base = context.getDatabasePath(name)
        listOf(base, java.io.File(base.path + "-wal"), java.io.File(base.path + "-shm"), java.io.File(base.path + "-journal"))
            .forEach { file -> runCatching { if (file.exists()) file.delete() } }
    }
}
