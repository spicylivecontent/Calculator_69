package com.calculator.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.calculator.app.data.entity.AppSettingsEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val mutex = Mutex()
    private const val MIGRATION_KEY = "legacy_migration_completed"

    /** Returnerar antalet överförda rader, eller null om ingen migrering behövdes. */
    suspend fun migrateIfNeeded(context: Context, target: AppDatabase, sourceName: String): Int? = mutex.withLock {
        if (!context.getDatabasePath(sourceName).exists()) return@withLock null
        // The transaction marker survives a crash between copying and deleting the source.
        if (target.settingsDao().get(MIGRATION_KEY) == "true") {
            deleteDatabaseFiles(context, sourceName)
            return@withLock 0
        }

        val source = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, sourceName).build()
        try {
            val videos = source.videoDao().getAllVideosOnce()
            val favoriteIds = source.favoriteDao().getAllFavoriteIds()
            val watchLaterIds = source.watchLaterDao().getAllWatchLaterIds()
            val rules = source.dislikeRuleDao().getAllRulesOnce()
            val presets = source.searchPresetDao().getAllPresetsOnce()
            val settings = source.settingsDao().getAllOnce()

            target.withTransaction {
                // Keep newer rows already written to the target during startup.
                videos.forEach { video ->
                    if (target.videoDao().getVideoById(video.id) == null) target.videoDao().insertVideo(video)
                }
                favoriteIds.forEach { id -> target.favoriteDao().addFavorite(FavoriteEntity(videoId = id)) }
                watchLaterIds.forEach { id -> target.watchLaterDao().add(WatchLaterEntity(videoId = id)) }
                rules.forEach { rule -> target.dislikeRuleDao().insertRule(rule.copy(id = 0)) }
                presets.forEach { preset -> target.searchPresetDao().insertPreset(preset.copy(id = 0)) }
                settings.filterNot { it.key == MIGRATION_KEY }.forEach { setting ->
                    if (target.settingsDao().get(setting.key) == null) target.settingsDao().set(setting)
                }
                target.settingsDao().set(AppSettingsEntity(MIGRATION_KEY, "true"))
            }
            val copied = videos.size + favoriteIds.size + watchLaterIds.size + rules.size + presets.size + settings.size
            source.close()
            deleteDatabaseFiles(context, sourceName)
            copied
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            source.close()
        }
    }

    private fun deleteDatabaseFiles(context: Context, name: String) {
        val base = context.getDatabasePath(name)
        listOf(base, java.io.File(base.path + "-wal"), java.io.File(base.path + "-shm"), java.io.File(base.path + "-journal"))
            .forEach { file -> runCatching { if (file.exists()) file.delete() } }
    }
}
