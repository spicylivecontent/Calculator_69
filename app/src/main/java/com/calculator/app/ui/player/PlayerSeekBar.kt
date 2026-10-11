package com.calculator.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Egen tidslinje med buffertindikator, uppspelningsläge och tydlig drag-/tappstyrning.
 * Fungerar både för att hoppa direkt (tryck) och för att skrubba (dra).
 */
@Composable
fun PlayerSeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onScrubStart: () -> Unit,
    onScrubPreview: (Long) -> Unit,
    onScrubCommit: (Long) -> Unit,
    onScrubCancel: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF00E676)
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val playFraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownFraction = if (isDragging) dragFraction else playFraction

    fun fractionFor(x: Float, width: Float): Float =
        if (width <= 0f) 0f else (x / width).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled, durationMs) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val fraction = fractionFor(offset.x, size.width.toFloat())
                    dragFraction = fraction
                    onScrubPreview((fraction * safeDuration).toLong())
                    onScrubCommit((fraction * safeDuration).toLong())
                }
            }
            .pointerInput(enabled, durationMs) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = fractionFor(offset.x, size.width.toFloat())
                        onScrubStart()
                        onScrubPreview((dragFraction * safeDuration).toLong())
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        dragFraction = (dragFraction + dragAmount / width).coerceIn(0f, 1f)
                        onScrubPreview((dragFraction * safeDuration).toLong())
                    },
                    onDragEnd = {
                        isDragging = false
                        onScrubCommit((dragFraction * safeDuration).toLong())
                    },
                    onDragCancel = {
                        isDragging = false
                        onScrubCancel()
                    }
                )
            }
    ) {
        val centerY = size.height / 2f
        val trackHeight = if (isDragging) 8.dp.toPx() else 5.dp.toPx()
        val corner = CornerRadius(trackHeight / 2f)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = corner
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.38f),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width * bufferedFraction, trackHeight),
            cornerRadius = corner
        )
        drawRoundRect(
            color = accent,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width * shownFraction, trackHeight),
            cornerRadius = corner
        )

        val thumbRadius = if (isDragging) 11.dp.toPx() else 7.dp.toPx()
        // Skydda mot extremt smal yta (t.ex. första mätningen) så att coerceIn aldrig får ett tomt intervall.
        val thumbX = if (size.width > thumbRadius * 2f) {
            (size.width * shownFraction).coerceIn(thumbRadius, size.width - thumbRadius)
        } else {
            size.width * shownFraction
        }
        drawCircle(color = accent.copy(alpha = 0.25f), radius = thumbRadius * 1.7f, center = Offset(thumbX, centerY))
        drawCircle(color = Color.White, radius = thumbRadius, center = Offset(thumbX, centerY))
        drawCircle(color = accent, radius = thumbRadius * 0.55f, center = Offset(thumbX, centerY))
    }
}

/** Formaterar millisekunder som 12:34 / 1:02:03. */
fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L)) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/** "+0:15" / "-0:30" för skrubbrutan. */
fun formatSeekDelta(deltaMs: Long): String {
    val sign = if (deltaMs < 0) "−" else "+"
    return sign + formatPlayerTime(kotlin.math.abs(deltaMs))
}
