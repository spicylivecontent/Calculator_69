package com.calculator.app.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.calculator.app.data.PreferredQuality
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class OverlayFeedback(val text: String, val icon: ImageVector)

private enum class ResizeOption(val label: String) {
    FIT("Anpassa"),
    FILL("Fyll skärmen"),
    ZOOM("Zooma in");

    fun mode(): Int = when (this) {
        FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

    fun next(): ResizeOption = entries[(ordinal + 1) % entries.size]
}

/**
 * Videospelare med kvalitetsval från strömmens egna spår, skrubbning med drag,
 * dubbeltryck för att hoppa, volym/ljusstyrka med vertikala drag, hastighetsval
 * och återupptagning där man slutade.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    title: String = "",
    onBack: () -> Unit,
    refererUrl: String? = null,
    videoId: String? = null,
    subtitle: String = "",
    startPositionMs: Long = 0L,
    skipSeconds: Int = 10,
    preferredQuality: PreferredQuality = PreferredQuality.AUTO,
    keepScreenOn: Boolean = true,
    gesturesEnabled: Boolean = true,
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onProgress: (String, Long, Long) -> Unit = { _, _, _ -> },
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
    val activity = context as? Activity
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Fullskärm: dölj systemfälten så länge spelaren är öppen.
    DisposableEffect(activity) {
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null && originalBrightness != null) {
                window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
            }
        }
    }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    val trackSelector = remember(context) { DefaultTrackSelector(context) }
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()
    }
    val renderersFactory = remember(context) {
        DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    }

    val exoPlayer = remember(context, videoUrl, refererUrl, trackSelector, loadControl, renderersFactory) {
        val headers = buildMap {
            val parsed = refererUrl?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }
            if (parsed != null) {
                put("Referer", parsed)
                runCatching {
                    val host = java.net.URI(parsed).host
                    if (!host.isNullOrBlank()) put("Origin", "https://$host")
                }
            }
        }
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_USER_AGENT)
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(false)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setHandleAudioBecomingNoisy(true)
                setSeekParameters(SeekParameters.DEFAULT)
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = true
            }
    }

    var isPlaying by remember(exoPlayer) { mutableStateOf(exoPlayer.isPlaying) }
    var playbackState by remember(exoPlayer) { mutableIntStateOf(Player.STATE_BUFFERING) }
    var showControls by remember(exoPlayer) { mutableStateOf(true) }
    var isScrubbing by remember(exoPlayer) { mutableStateOf(false) }
    var scrubStartMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var scrubTargetMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var positionMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var durationMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var bufferedMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var tracks by remember(exoPlayer) { mutableStateOf<Tracks?>(null) }
    var currentFormat by remember(exoPlayer) { mutableStateOf<Format?>(null) }
    var videoSizeLabel by remember(exoPlayer) { mutableStateOf("") }
    var playerError by remember(exoPlayer) { mutableStateOf<String?>(null) }
    var errorReported by remember(exoPlayer) { mutableStateOf(false) }
    var speed by remember(exoPlayer) { mutableFloatStateOf(1f) }
    var resizeOption by remember(exoPlayer) { mutableStateOf(ResizeOption.FIT) }
    var qualityChoice by remember(exoPlayer) { mutableStateOf(initialQuality(preferredQuality)) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var feedback by remember(exoPlayer) { mutableStateOf<OverlayFeedback?>(null) }
    var hud by remember(exoPlayer) { mutableStateOf<String?>(null) }
    var resumeNotice by remember(exoPlayer) { mutableStateOf<Long?>(null) }
    var appliedStart by remember(exoPlayer) { mutableStateOf(false) }

    fun seekTo(targetMs: Long) {
        if (durationMs <= 0L) return
        val clamped = targetMs.coerceIn(0L, durationMs)
        exoPlayer.seekTo(clamped)
        positionMs = clamped
    }

    fun refreshFormat() {
        currentFormat = runCatching { exoPlayer.videoFormat }.getOrNull()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) refreshFormat()
            }

            override fun onTracksChanged(tracks1: Tracks) {
                tracks = tracks1
                refreshFormat()
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSizeLabel = if (size.height > 0) "${size.width}×${size.height}" else ""
                refreshFormat()
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error.localizedMessage ?: "Uppspelningen misslyckades."
                showControls = true
                if (!errorReported) {
                    errorReported = true
                    onPlaybackError(videoId)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            val duration = exoPlayer.duration
            if (duration > 0L) {
                onProgress(videoId ?: videoUrl, exoPlayer.currentPosition.coerceAtLeast(0L), duration)
            }
            exoPlayer.release()
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(exoPlayer) {
        var tick = 0
        while (true) {
            val duration = exoPlayer.duration
            durationMs = if (duration == C.TIME_UNSET || duration < 0L) 0L else duration
            bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            if (!isScrubbing) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            if (tick % PROGRESS_SAVE_TICKS == 0 && durationMs > 0L) {
                onProgress(videoId ?: videoUrl, positionMs, durationMs)
            }
            tick++
            delay(PROGRESS_INTERVAL_MS)
        }
    }

    LaunchedEffect(showControls, isPlaying, isScrubbing, playerError, showQualityMenu, showSpeedMenu) {
        if (showControls && isPlaying && !isScrubbing && playerError == null && !showQualityMenu && !showSpeedMenu) {
            delay(CONTROLS_HIDE_DELAY_MS)
            showControls = false
        }
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(FEEDBACK_DURATION_MS)
            feedback = null
        }
    }

    LaunchedEffect(hud) {
        if (hud != null) {
            delay(HUD_DURATION_MS)
            hud = null
        }
    }

    LaunchedEffect(resumeNotice) {
        if (resumeNotice != null) {
            delay(RESUME_NOTICE_MS)
            resumeNotice = null
        }
    }

    // Återuppta där användaren slutade (när längden är känd).
    LaunchedEffect(exoPlayer, startPositionMs) {
        if (appliedStart) return@LaunchedEffect
        if (startPositionMs <= RESUME_MIN_MS) {
            appliedStart = true
            return@LaunchedEffect
        }
        var waited = 0
        while (exoPlayer.duration == C.TIME_UNSET && waited < 8_000) {
            delay(100)
            waited += 100
        }
        val duration = exoPlayer.duration
        if (duration > 0L) {
            val target = startPositionMs.coerceIn(0L, (duration - RESUME_TAIL_MARGIN_MS).coerceAtLeast(0L))
            if (target > 0L) {
                exoPlayer.seekTo(target)
                positionMs = target
                resumeNotice = target
            }
        }
        appliedStart = true
    }

    LaunchedEffect(qualityChoice) {
        val builder = trackSelector.buildUponParameters()
        when (val choice = qualityChoice) {
            QualityChoice.Auto -> builder
                .clearVideoSizeConstraints()
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .setForceHighestSupportedBitrate(false)

            QualityChoice.Highest -> builder
                .clearVideoSizeConstraints()
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .setForceHighestSupportedBitrate(true)

            QualityChoice.DataSaver -> builder
                .clearVideoSizeConstraints()
                .setMaxVideoSize(Int.MAX_VALUE, DATA_SAVER_HEIGHT)
                .setMaxVideoBitrate(DATA_SAVER_BITRATE)
                .setForceHighestSupportedBitrate(false)

            is QualityChoice.Exact -> builder
                .clearVideoSizeConstraints()
                .setMaxVideoSize(Int.MAX_VALUE, choice.height)
                .setMaxVideoBitrate(PlayerQuality.bitrateCeiling(choice.bitrate))
                .setForceHighestSupportedBitrate(false)
        }
        trackSelector.setParameters(builder)
    }

    LaunchedEffect(speed) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    val trackQualities = remember(tracks) {
        val list = mutableListOf<TrackQuality>()
        tracks?.groups?.forEach { group ->
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (index in 0 until group.length) {
                    val format = group.mediaTrackGroup.getFormat(index)
                    list.add(TrackQuality(height = format.height, bitrate = format.bitrate))
                }
            }
        }
        list
    }

    val qualityChoices = remember(trackQualities) {
        buildList {
            add(QualityChoice.Auto)
            add(QualityChoice.Highest)
            add(QualityChoice.DataSaver)
            addAll(PlayerQuality.choicesFrom(trackQualities))
        }
    }

    val metaLabel = remember(subtitle, currentFormat, videoSizeLabel, speed) {
        buildList {
            if (subtitle.isNotBlank()) add(subtitle)
            currentFormat?.let { format ->
                val height = format.height
                if (height > 0) {
                    add(
                        buildString {
                            append(if (height >= 2160) "4K" else "${height}p")
                            val bitrate = PlayerQuality.formatBitrate(format.bitrate)
                            if (bitrate.isNotEmpty()) append(" · $bitrate")
                        }
                    )
                }
            }
            if (videoSizeLabel.isNotBlank()) add(videoSizeLabel)
            if (abs(speed - 1f) > 0.01f) add(formatSpeed(speed))
        }.joinToString("  ·  ")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(exoPlayer, gesturesEnabled, skipSeconds) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = doubleTap@{ offset ->
                        if (!gesturesEnabled) return@doubleTap
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        when {
                            offset.x < width * 0.4f -> if (durationMs > 0L) {
                                seekTo(positionMs - skipSeconds * 1_000L)
                                feedback = OverlayFeedback("−${skipSeconds}s", Icons.Default.FastRewind)
                                showControls = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }

                            offset.x > width * 0.6f -> if (durationMs > 0L) {
                                seekTo(positionMs + skipSeconds * 1_000L)
                                feedback = OverlayFeedback("+${skipSeconds}s", Icons.Default.FastForward)
                                showControls = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }

                            else -> {
                                val wasPlaying = exoPlayer.isPlaying
                                if (wasPlaying) exoPlayer.pause() else exoPlayer.play()
                                feedback = OverlayFeedback(
                                    if (wasPlaying) "Paus" else "Spelar",
                                    if (wasPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
                                )
                            }
                        }
                    }
                )
            }
            .then(
                if (!gesturesEnabled) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(exoPlayer, durationMs) {
                            if (durationMs <= 0L) return@pointerInput
                            var accumulated = 0f
                            var lastPreviewAt = 0L
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isScrubbing = true
                                    scrubStartMs = positionMs
                                    scrubTargetMs = positionMs
                                    accumulated = 0f
                                    exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulated += dragAmount
                                    val width = size.width.toFloat().coerceAtLeast(1f)
                                    val deltaMs = (accumulated / width) * durationMs * SCRUB_SENSITIVITY
                                    scrubTargetMs = (scrubStartMs + deltaMs.toLong()).coerceIn(0L, durationMs)
                                    val now = System.currentTimeMillis()
                                    if (now - lastPreviewAt > SCRUB_PREVIEW_INTERVAL_MS) {
                                        lastPreviewAt = now
                                        exoPlayer.seekTo(scrubTargetMs)
                                    }
                                },
                                onDragEnd = {
                                    isScrubbing = false
                                    exoPlayer.setSeekParameters(SeekParameters.DEFAULT)
                                    seekTo(scrubTargetMs)
                                    showControls = true
                                },
                                onDragCancel = {
                                    isScrubbing = false
                                    exoPlayer.setSeekParameters(SeekParameters.DEFAULT)
                                }
                            )
                        }
                        .pointerInput(exoPlayer, durationMs) {
                            var mode = 0
                            var startValue = 0f
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    val width = size.width.toFloat().coerceAtLeast(1f)
                                    mode = if (offset.x > width / 2f) MODE_VOLUME else MODE_BRIGHTNESS
                                    startValue = if (mode == MODE_VOLUME) currentVolume(audioManager) else currentBrightness(activity)
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val height = size.height.toFloat().coerceAtLeast(1f)
                                    val delta = -dragAmount / height
                                    if (mode == MODE_VOLUME) {
                                        val value = (startValue + delta).coerceIn(0f, 1f)
                                        startValue = value
                                        setVolume(audioManager, value)
                                        hud = "Volym ${(value * 100).roundToInt()}%"
                                    } else {
                                        val value = (startValue + delta).coerceIn(0.04f, 1f)
                                        startValue = value
                                        setBrightness(activity, value)
                                        hud = "Ljus ${(value * 100).roundToInt()}%"
                                    }
                                },
                                onDragEnd = { mode = 0 },
                                onDragCancel = { mode = 0 }
                            )
                        }
                }
            )
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    this.keepScreenOn = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = resizeOption.mode()
                playerView.keepScreenOn = keepScreenOn
            },
            modifier = Modifier.fillMaxSize()
        )

        val isBuffering = playbackState == Player.STATE_BUFFERING

        if (isScrubbing && durationMs > 0L) {
            ScrubOverlay(
                targetMs = scrubTargetMs,
                deltaMs = scrubTargetMs - scrubStartMs,
                availableMs = durationMs,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        if (isBuffering && !showControls && playerError == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF00E676)
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            PlayerTopBar(
                title = title,
                meta = metaLabel,
                qualityLabel = qualityChoice.label,
                speedLabel = formatSpeed(speed),
                resizeLabel = resizeOption.label,
                qualityChoices = qualityChoices,
                selectedQuality = qualityChoice,
                showQualityMenu = showQualityMenu,
                showSpeedMenu = showSpeedMenu,
                onBack = onBack,
                onToggleQualityMenu = { showQualityMenu = !showQualityMenu },
                onToggleSpeedMenu = { showSpeedMenu = !showSpeedMenu },
                onSelectQuality = {
                    qualityChoice = it
                    showQualityMenu = false
                    showControls = true
                },
                currentSpeed = speed,
                onSelectSpeed = {
                    speed = it
                    showSpeedMenu = false
                    showControls = true
                    feedback = OverlayFeedback(formatSpeed(it), Icons.Default.Speed)
                },
                onCycleResize = {
                    resizeOption = resizeOption.next()
                    feedback = OverlayFeedback(resizeOption.label, Icons.Default.AspectRatio)
                }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            PlayerCenterControls(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                skipSeconds = skipSeconds,
                hasNext = hasNext,
                hasPrevious = hasPrevious,
                seekEnabled = durationMs > 0L,
                onPlayPause = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    showControls = true
                },
                onBack10 = { seekTo(positionMs - skipSeconds * 1_000L); showControls = true },
                onForward10 = { seekTo(positionMs + skipSeconds * 1_000L); showControls = true },
                onNext = onNext,
                onPrevious = onPrevious
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PlayerBottomBar(
                positionMs = if (isScrubbing) scrubTargetMs else positionMs,
                bufferedMs = bufferedMs,
                durationMs = durationMs,
                resumeNotice = resumeNotice,
                onScrubStart = {
                    isScrubbing = true
                    scrubStartMs = positionMs
                    scrubTargetMs = positionMs
                    exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                },
                onScrubPreview = { scrubTargetMs = it },
                onScrubCommit = { target ->
                    isScrubbing = false
                    exoPlayer.setSeekParameters(SeekParameters.DEFAULT)
                    seekTo(target)
                    showControls = true
                },
                onScrubCancel = {
                    isScrubbing = false
                    exoPlayer.setSeekParameters(SeekParameters.DEFAULT)
                },
                onRestart = {
                    seekTo(0L)
                    resumeNotice = null
                }
            )
        }

        feedback?.let {
            PlayerFeedbackOverlay(feedback = it, modifier = Modifier.align(Alignment.Center))
        }

        hud?.let {
            HudOverlay(text = it, modifier = Modifier.align(Alignment.Center))
        }

        playerError?.let { message ->
            PlayerErrorPanel(
                message = message,
                hasNext = hasNext,
                onRetry = {
                    playerError = null
                    errorReported = false
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    showControls = true
                },
                onNext = onNext,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    meta: String,
    qualityLabel: String,
    speedLabel: String,
    resizeLabel: String,
    qualityChoices: List<QualityChoice>,
    selectedQuality: QualityChoice,
    showQualityMenu: Boolean,
    showSpeedMenu: Boolean,
    onBack: () -> Unit,
    onToggleQualityMenu: () -> Unit,
    onToggleSpeedMenu: () -> Unit,
    onSelectQuality: (QualityChoice) -> Unit,
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onCycleResize: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.62f))
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tillbaka", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { "Videospelare" },
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box {
            TextButton(onClick = onToggleQualityMenu) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = shortQualityLabel(qualityLabel),
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
            DropdownMenu(expanded = showQualityMenu, onDismissRequest = { onToggleQualityMenu() }) {
                qualityChoices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.label) },
                        onClick = { onSelectQuality(choice) },
                        trailingIcon = {
                            if (choice == selectedQuality) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }

        Box {
            IconButton(onClick = onToggleSpeedMenu) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = "Hastighet", tint = Color.White)
                    Text(speedLabel, color = Color.White, fontSize = 11.sp)
                }
            }
            DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { onToggleSpeedMenu() }) {
                SPEED_OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(formatSpeed(option)) },
                        onClick = { onSelectSpeed(option) },
                        trailingIcon = {
                            if (abs(option - currentSpeed) < 0.01f) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }

        IconButton(onClick = onCycleResize) {
            Icon(Icons.Default.AspectRatio, contentDescription = resizeLabel, tint = Color.White)
        }
    }
}

@Composable
private fun PlayerCenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    skipSeconds: Int,
    hasNext: Boolean,
    hasPrevious: Boolean,
    seekEnabled: Boolean,
    onPlayPause: () -> Unit,
    onBack10: () -> Unit,
    onForward10: () -> Unit,
    onNext: (() -> Unit)?,
    onPrevious: (() -> Unit)?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasPrevious && onPrevious != null) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Föregående", tint = Color.White)
                }
            }

            IconButton(
                onClick = onBack10,
                enabled = seekEnabled,
                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (skipSeconds == 10) Icons.Default.Replay10 else Icons.Default.FastRewind,
                    contentDescription = "Tillbaka $skipSeconds sekunder",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(76.dp).background(Color.Black.copy(alpha = 0.62f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausa" else "Spela",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            IconButton(
                onClick = onForward10,
                enabled = seekEnabled,
                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (skipSeconds == 10) Icons.Default.Forward10 else Icons.Default.FastForward,
                    contentDescription = "Framåt $skipSeconds sekunder",
                    tint = Color.White
                )
            }

            if (hasNext && onNext != null) {
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Nästa", tint = Color.White)
                }
            }
        }

        if (isBuffering) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = Color(0xFF00E676),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun PlayerBottomBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    resumeNotice: Long?,
    onScrubStart: () -> Unit,
    onScrubPreview: (Long) -> Unit,
    onScrubCommit: (Long) -> Unit,
    onScrubCancel: () -> Unit,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.72f))
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        PlayerSeekBar(
            positionMs = positionMs,
            bufferedMs = bufferedMs,
            durationMs = durationMs,
            enabled = durationMs > 0L,
            onScrubStart = onScrubStart,
            onScrubPreview = onScrubPreview,
            onScrubCommit = onScrubCommit,
            onScrubCancel = onScrubCancel
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatPlayerTime(positionMs),
                color = Color.White,
                fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (durationMs > 0L) formatPlayerTime(durationMs) else "LIVE",
                color = Color(0xFFB0BEC5),
                fontSize = 12.sp
            )
        }
        if (resumeNotice != null && resumeNotice > 0L) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Fortsätter från ${formatPlayerTime(resumeNotice)}",
                    color = Color(0xFF80CBC4),
                    fontSize = 12.sp
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRestart) {
                    Text("Börja om", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PlayerErrorPanel(
    message: String,
    hasNext: Boolean,
    onRetry: () -> Unit,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE6171717))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Videon verkar vara otillgänglig just nu.",
            color = Color(0xFFB0BEC5),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onRetry) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF80CBC4), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Försök igen", color = Color(0xFF80CBC4))
                }
            }
            if (hasNext && onNext != null) {
                TextButton(onClick = onNext) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nästa video", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerFeedbackOverlay(feedback: OverlayFeedback, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.66f))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(feedback.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
        Spacer(Modifier.height(6.dp))
        Text(feedback.text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HudOverlay(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.66f))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

@Composable
private fun ScrubOverlay(
    targetMs: Long,
    deltaMs: Long,
    availableMs: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatPlayerTime(targetMs),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatSeekDelta(deltaMs), color = Color(0xFF00E676), fontSize = 13.sp)
            Spacer(Modifier.width(10.dp))
            Text("/ ${formatPlayerTime(availableMs)}", color = Color(0xFFB0BEC5), fontSize = 13.sp)
        }
    }
}

/** Liten testknapp som spelar en offentlig provvideo. */
@Composable
fun TestPlayerButton(onPlayTest: () -> Unit) {
    TextButton(onClick = onPlayTest) {
        Text("Testa videospelaren", color = Color(0xFF80CBC4))
    }
}

private fun shortQualityLabel(label: String): String = when {
    label.startsWith("Automatisk") -> "Auto"
    label.startsWith("Högsta") -> "Max"
    label.startsWith("Spara data") -> "480p"
    else -> label.substringBefore(" · ")
}

private fun formatSpeed(speed: Float): String {
    val trimmed = if (abs(speed - speed.roundToInt()) < 0.01f) {
        speed.roundToInt().toString()
    } else {
        String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.')
    }
    return "$trimmed×"
}

private fun initialQuality(preferred: PreferredQuality): QualityChoice = when (preferred) {
    PreferredQuality.AUTO -> QualityChoice.Auto
    PreferredQuality.HIGHEST -> QualityChoice.Highest
    PreferredQuality.P1080 -> QualityChoice.Exact(1080, 0)
    PreferredQuality.P720 -> QualityChoice.Exact(720, 0)
    PreferredQuality.P480 -> QualityChoice.Exact(480, 0)
    PreferredQuality.DATA_SAVER -> QualityChoice.DataSaver
}

private fun currentVolume(audioManager: AudioManager?): Float {
    val manager = audioManager ?: return 0.5f
    val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
    return (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)
}

private fun setVolume(audioManager: AudioManager?, value: Float) {
    val manager = audioManager ?: return
    val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val target = (value * max).roundToInt().coerceIn(0, max)
    try {
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    } catch (_: SecurityException) {
        // Saknar behörighet – ignorera.
    }
}

private fun currentBrightness(activity: Activity?): Float {
    val current = activity?.window?.attributes?.screenBrightness ?: -1f
    return if (current < 0f) 0.5f else current
}

private fun setBrightness(activity: Activity?, value: Float) {
    val window = activity?.window ?: return
    val attributes = window.attributes
    attributes.screenBrightness = value.coerceIn(0.01f, 1f)
    window.attributes = attributes
}

private const val PLAYER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 20_000
private const val MIN_BUFFER_MS = 15_000
private const val MAX_BUFFER_MS = 60_000
private const val BUFFER_FOR_PLAYBACK_MS = 1_500
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000
private const val PROGRESS_INTERVAL_MS = 500L
private const val PROGRESS_SAVE_TICKS = 10
private const val CONTROLS_HIDE_DELAY_MS = 4_200L
private const val FEEDBACK_DURATION_MS = 700L
private const val HUD_DURATION_MS = 900L
private const val RESUME_NOTICE_MS = 6_000L
private const val RESUME_MIN_MS = 15_000L
private const val RESUME_TAIL_MARGIN_MS = 20_000L
private const val SCRUB_SENSITIVITY = 0.45f
private const val SCRUB_PREVIEW_INTERVAL_MS = 180L
private const val DATA_SAVER_HEIGHT = 480
private const val DATA_SAVER_BITRATE = 900_000
private const val MODE_BRIGHTNESS = 1
private const val MODE_VOLUME = 2

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
