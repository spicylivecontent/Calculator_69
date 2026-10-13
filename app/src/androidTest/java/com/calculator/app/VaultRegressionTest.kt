package com.calculator.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calculator.app.data.AppDatabase
import com.calculator.app.data.AppPreferences
import com.calculator.app.data.LegacyVaultMigrator
import com.calculator.app.data.PlaybackStore
import com.calculator.app.data.entity.AppSettingsEntity
import com.calculator.app.data.entity.SearchPresetEntity
import com.calculator.app.data.entity.VideoEntity
import com.calculator.app.data.model.SearchResult
import com.calculator.app.data.extractor.ScrapeRuleStore
import com.calculator.app.security.VaultDatabaseKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only regression coverage; no external sites or media are requested. */
@RunWith(AndroidJUnit4::class)
class VaultRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun databasePassphraseIsReturnedAsAnIndependentCopy() {
        val first = requireNotNull(VaultDatabaseKey.passphrase(context))
        val expected = first.copyOf()
        first.fill(0)
        assertArrayEquals(expected, VaultDatabaseKey.passphrase(context))
    }

    @Test
    fun interruptedMigrationUsesItsMarkerAndPreservesSettings() = runBlocking {
        val name = "migration-regression.db"
        context.deleteDatabase(name)
        val source = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            source.videoDao().insertVideo(VideoEntity(
                id = "test", title = "Legacy title", sourceSite = "example",
                detailPageUrl = "https://example.com/video/test"
            ))
            source.searchPresetDao().insertPreset(SearchPresetEntity(name = "Saved", query = "kitchen"))
            source.settingsDao().set(AppSettingsEntity("theme", "dark"))
            source.close()
            target.videoDao().insertVideo(VideoEntity(
                id = "test", title = "Newer title", sourceSite = "example",
                detailPageUrl = "https://example.com/video/test"
            ))
            assertNotNull(LegacyVaultMigrator.migrateIfNeeded(context, target, name))
            assertEquals("Newer title", target.videoDao().getVideoById("test")?.title)
            assertEquals("dark", target.settingsDao().get("theme"))
            assertEquals(1, target.searchPresetDao().getAllPresetsOnce().size)

            // Recreate the source to model a crash/deletion failure after the commit.
            val restored = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            restored.searchPresetDao().insertPreset(SearchPresetEntity(name = "Saved", query = "kitchen"))
            restored.close()
            assertEquals(0, LegacyVaultMigrator.migrateIfNeeded(context, target, name))
            assertEquals(1, target.searchPresetDao().getAllPresetsOnce().size)
            assertFalse(context.getDatabasePath(name).exists())
        } finally {
            source.close()
            target.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun clearingPlaybackAlsoRemovesLegacyPositions() {
        val prefs = context.getSharedPreferences("calculator_playback", Context.MODE_PRIVATE)
        prefs.edit().clear().putLong("pos_old", 20000L).putLong("dur_old", 60000L).commit()
        PlaybackStore(context).clear()
        assertEquals(0L, PlaybackStore(context).position("old"))
        assertFalse(prefs.contains("pos_old"))
        assertFalse(prefs.contains("dur_old"))
    }

    @Test
    fun playbackPositionsArePrunedWithTheBoundedRecentHistory() {
        val store = PlaybackStore(context)
        store.clear()
        try {
            repeat(41) { index ->
                val id = "video-$index"
                store.savePlayed(
                    SearchResult(
                        id = id,
                        title = "Video $index",
                        thumbnailUrl = null,
                        duration = null,
                        detailUrl = "https://example.test/video/$index",
                        sourceSite = "example"
                    )
                )
                store.saveProgress(id, (index + 1L) * 1_000L, 120_000L)
            }

            assertEquals(40, store.recent().size)
            assertEquals("the entry pruned from history has no resumable position", 0L, store.position("video-0"))
            assertEquals(2_000L, store.position("video-1"))
            store.saveProgress("not-in-history", 30_000L, 60_000L)
            assertEquals(0L, store.position("not-in-history"))
        } finally {
            store.clear()
        }
    }

    @Test
    fun ruleRefreshThrottlesFailuresAndTreatsUnchangedVersionsAsSkipped() = runBlocking {
        val prefs = context.getSharedPreferences("calculator_scrape_rules", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        AppPreferences(context).remoteRulesEnabled = true
        var requests = 0
        var body: String? = null
        val store = ScrapeRuleStore(context, fetchRules = { requests++; body }, now = { 100000L })
        try {
            assertEquals(ScrapeRuleStore.RefreshResult.Failed, store.refresh())
            assertEquals(ScrapeRuleStore.RefreshResult.Skipped, store.refresh())
            assertEquals(1, requests)
            body = """{"version":1,"sites":{}}"""
            assertTrue(store.refresh(force = true) is ScrapeRuleStore.RefreshResult.Updated)
            assertEquals(ScrapeRuleStore.RefreshResult.Skipped, store.refresh(force = true))
        } finally {
            store.reset()
        }
    }

    @Test
    fun ruleRefreshPropagatesCancellation() = runBlocking {
        AppPreferences(context).remoteRulesEnabled = true
        val store = ScrapeRuleStore(context, fetchRules = { throw CancellationException("cancelled") })
        try {
            store.refresh(force = true)
            fail("Cancellation must reach the caller")
        } catch (_: CancellationException) {
            // Expected; cancellation is not a network error.
        } finally {
            store.reset()
        }
    }
}
