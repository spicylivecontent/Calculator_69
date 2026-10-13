package com.calculator.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calculator.app.data.entity.VideoEntity
import com.calculator.app.ui.library.LibraryScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun favoritesAndWatchLaterOpenTheSelectedVideo() {
        val video = VideoEntity("test-id", "Saved regression video", sourceSite = "example", detailPageUrl = "https://example.com/video/test")
        var playedId: String? = null
        compose.setContent {
            LibraryScreen(
                favoriteCount = 1, watchLaterCount = 1, dislikeCount = 0, presetCount = 0,
                favorites = listOf(video), watchLater = listOf(video), rules = emptyList(),
                presets = emptyList(), history = emptyList(), onResume = {}, onClearHistory = {},
                onUsePreset = {}, onRemoveFavorite = {}, onRemoveWatchLater = {},
                onRemoveDislikeRule = {}, onRemovePreset = {}, onPlayVideo = { playedId = it.id }
            )
        }
        compose.onNodeWithText("Favoriter").performClick()
        compose.onNodeWithText(video.title).performClick()
        compose.runOnIdle { assertEquals(video.id, playedId); playedId = null }
        compose.onNodeWithText("← Översikt").performClick()
        compose.onNodeWithText("Watch Later").performClick()
        compose.onNodeWithText(video.title).performClick()
        compose.runOnIdle { assertEquals(video.id, playedId) }
    }
}
