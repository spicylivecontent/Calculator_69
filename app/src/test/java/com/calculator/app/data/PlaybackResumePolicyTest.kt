package com.calculator.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResumePolicyTest {

    @Test
    fun disabledResumeAlwaysStartsAtTheBeginning() {
        assertEquals(0L, PlaybackResumePolicy.startPosition(false, 90_000L, 120_000L))
        assertEquals(0L, PlaybackResumePolicy.startPosition(false, -1L, 120_000L))
    }

    @Test
    fun explicitRecentPositionWinsWhenResumeIsEnabled() {
        assertEquals(45_000L, PlaybackResumePolicy.startPosition(true, 45_000L, 90_000L))
        assertEquals(0L, PlaybackResumePolicy.startPosition(true, 0L, 90_000L))
    }

    @Test
    fun aFreshPlaybackUsesItsSavedPositionWhenResumeIsEnabled() {
        assertEquals(30_000L, PlaybackResumePolicy.startPosition(true, -1L, 30_000L))
        assertEquals(0L, PlaybackResumePolicy.startPosition(true, -1L, -10L))
    }
}
