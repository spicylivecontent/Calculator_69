package com.calculator.app.data

/** Resolves a playback start position while honoring the user's resume preference. */
internal object PlaybackResumePolicy {
    fun startPosition(
        resumeEnabled: Boolean,
        requestedPositionMs: Long,
        savedPositionMs: Long
    ): Long = when {
        !resumeEnabled -> 0L
        requestedPositionMs >= 0L -> requestedPositionMs
        else -> savedPositionMs.coerceAtLeast(0L)
    }
}
