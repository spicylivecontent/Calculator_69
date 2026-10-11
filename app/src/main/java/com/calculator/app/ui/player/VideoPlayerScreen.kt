package com.calculator.app.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale

private data class QualityOption(val label: String, val maxHeight: Int?)

private val qualityOptions = listOf(
    QualityOption("Auto", null),
    QualityOption("2160p max", 2160),
    QualityOption("1440p max", 1440),
    QualityOption("1080p max", 1080),
    QualityOption("720p max", 720),
    QualityOption("480p max", 480)
)

/** Media3 player with seeking, skip controls, adaptive quality and playback errors. */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    title: String = "",
    onBack: () -> Unit,
    refererUrl: String? = null,
    videoId: String? = null,
    onPlaybackError: (String?) -> Unit = {}
) {
    if (videoUrl.isBlank()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Ingen video-URL", color = Color.White)
        }
        return
    }

    val context = LocalContext.current
    val trackSelector = remember(context, videoUrl) { DefaultTrackSelector(context) }
    val exoPlayer = remember(context, videoUrl, refererUrl, trackSelector) {
        val headers = refererUrl
            ?.takeIf { it.startsWith("https://", ignoreCase = true) }
            ?.let { mapOf("Referer" to it) }
            .orEmpty()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_USER_AGENT)
            .setDefaultRequestProperties(headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = true
            }
    }

    var isPlaying by remember(exoPlayer) { mutableStateOf(exoPlayer.isPlaying) }
    var isBuffering by remember(exoPlayer) { mutableStateOf(true) }
    var showControls by remember(exoPlayer) { mutableStateOf(true) }
    var isScrubbing by remember(exoPlayer) { mutableStateOf(false) }
    var playerError by remember(exoPlayer) { mutableStateOf<String?>(null) }
    var currentPositionMs by remember(exoPlayer) { mutableStateOf(0L) }
    var durationMs by remember(exoPlayer) { mutableStateOf(0L) }
    var showQualityMenu by remember(exoPlayer) { mutableStateOf(false) }
    var selectedQuality by remember(trackSelector) { mutableStateOf(qualityOptions.first()) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error.localizedMessage ?: "Uppspelningen misslyckades."
                showControls = true
                onPlaybackError(videoId)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            val duration = exoPlayer.duration
            durationMs = if (duration == C.TIME_UNSET || duration < 0L) 0L else duration
            if (!isScrubbing) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            delay(PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    LaunchedEffect(showControls, isPlaying, isScrubbing, playerError) {
        if (showControls && isPlaying && !isScrubbing && playerError == null) {
            delay(CONTROLS_HIDE_DELAY_MS)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(exoPlayer) {
                detectTapGestures { showControls = !showControls }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && playerError == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        if (showControls) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Tillbaka", tint = Color.White)
                }
                Text(
                    text = title.ifBlank { "Videospelare" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    TextButton(onClick = { showQualityMenu = true }) {
                        Text("${selectedQuality.label} ▾", color = Color.White, maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { showQualityMenu = false }
                    ) {
                        qualityOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedQuality = option
                                    trackSelector.setParameters(
                                        trackSelector.buildUponParameters().apply {
                                            if (option.maxHeight == null) {
                                                clearVideoSizeConstraints()
                                            } else {
                                                setMaxVideoSize(Int.MAX_VALUE, option.maxHeight)
                                            }
                                        }
                                    )
                                    showQualityMenu = false
                                    showControls = true
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { seekBy(exoPlayer, currentPositionMs, durationMs, -SEEK_INCREMENT_MS) },
                    enabled = durationMs > 0L
                ) {
                    Text("−10 s", color = Color.White)
                }
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        showControls = true
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausa" else "Spela",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
                TextButton(
                    onClick = { seekBy(exoPlayer, currentPositionMs, durationMs, SEEK_INCREMENT_MS) },
                    enabled = durationMs > 0L
                ) {
                    Text("+10 s", color = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Slider(
                    value = currentPositionMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
                    onValueChange = { value ->
                        isScrubbing = true
                        currentPositionMs = value.toLong()
                    },
                    onValueChangeFinished = {
                        exoPlayer.seekTo(currentPositionMs.coerceIn(0L, durationMs))
                        isScrubbing = false
                        showControls = true
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    enabled = durationMs > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E676),
                        activeTrackColor = Color(0xFF00E676)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(currentPositionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (durationMs > 0L) formatTime(durationMs) else "LIVE",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        playerError?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color(0xEE171717))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Videon verkar vara otillgänglig just nu.",
                    color = Color(0xFFB0BEC5),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = {
                    playerError = null
                    exoPlayer.seekTo(0L)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    showControls = true
                }) {
                    Text("Försök igen", color = Color(0xFF80CBC4))
                }
            }
        }
    }
}

private fun seekBy(player: ExoPlayer, currentPositionMs: Long, durationMs: Long, offsetMs: Long) {
    if (durationMs <= 0L) return
    player.seekTo((currentPositionMs + offsetMs).coerceIn(0L, durationMs))
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/** Small test action that plays a public sample video. */
@Composable
fun TestPlayerButton(onPlayTest: () -> Unit) {
    Button(
        onClick = onPlayTest,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text("Testa videospelaren", color = Color.Black)
    }
}

private const val PLAYER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
private const val SEEK_INCREMENT_MS = 10_000L
private const val PROGRESS_UPDATE_INTERVAL_MS = 400L
private const val CONTROLS_HIDE_DELAY_MS = 4_000L
