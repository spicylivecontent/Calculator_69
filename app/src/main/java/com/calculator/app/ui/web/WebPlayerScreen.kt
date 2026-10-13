package com.calculator.app.ui.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.calculator.app.data.model.SearchResult
import com.calculator.app.data.extractor.MiniJson
import com.calculator.app.ui.components.TagChip
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Spelar upp videon på sajten i en WebView med appens egen annonsblockering,
 * popup-skydd och omdirigeringsskydd – i stället för att extrahera en stream-URL.
 *
 * Blockerade förfrågningar räknas och visas i spelaren, och små injicerade
 * hjälpfunktioner ger samma spol- och pausmöjligheter som den inbyggda spelaren.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPlayerScreen(
    result: SearchResult,
    onBack: () -> Unit,
    skipSeconds: Int = 10,
    keepScreenOn: Boolean = true,
    adBlockLevel: AdBlockLevel = AdBlockLevel.STANDARD,
    onAdBlockLevelChange: (AdBlockLevel) -> Unit = {},
    startPositionMs: Long = 0L,
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onProgress: (String, Long, Long) -> Unit = { _, _, _ -> },
    onSearchTag: (String) -> Unit = {},
    onOpenNativePlayer: ((SearchResult) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val currentResult by rememberUpdatedState(result)
    val currentOnSearchTag by rememberUpdatedState(onSearchTag)

    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var blockedCount by remember { mutableIntStateOf(0) }
    var blockingActive by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf(result.detailUrl) }
    var notice by remember { mutableStateOf<String?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var videoReady by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var resumeApplied by remember { mutableStateOf(false) }

    val blockedCounter = remember(result.id) { AtomicInteger(0) }
    val levelHolder = remember { arrayOf(adBlockLevel) }

    // Fullskärm: dölj systemfälten medan spelaren är öppen.
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val webView = remember(result.id) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            isFocusable = true
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = false
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = false
                allowContentAccess = false
                setGeolocationEnabled(false)
                userAgentString = WEB_USER_AGENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString()
                    if (WebAdBlock.isBlockedRequest(url, levelHolder[0])) {
                        blockedCounter.incrementAndGet()
                        return emptyResponse()
                    }
                    return null
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val target = request?.url?.toString() ?: return true
                    return when (WebAdBlock.navigationFor(view?.url, target, levelHolder[0])) {
                        NavigationDecision.ALLOW -> {
                            currentUrl = target
                            false
                        }

                        NavigationDecision.BLOCK -> {
                            blockedCounter.incrementAndGet()
                            if (WebAdBlock.isBlockedRequest(target, levelHolder[0])) {
                                notice = "Reklamlänk blockerad"
                            }
                            true
                        }

                        NavigationDecision.EXTERNAL -> {
                            notice = "Länk till annan sajt stoppad – använd \"Öppna i webbläsare\" om du vill"
                            true
                        }
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                    hasError = false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    view?.let { target ->
                        target.evaluateJavascript(WebAdBlock.injectionScript(levelHolder[0]), null)
                        if (startPositionMs > 15_000L && !resumeApplied) {
                            resumeApplied = true
                            val seconds = (startPositionMs / 1_000L).toString()
                            target.evaluateJavascript("window.__pcResume($seconds)", null)
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        hasError = true
                        isLoading = false
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadProgress = newProgress
                    if (newProgress >= 100) isLoading = false
                }

                // Popups och nya flikar blockeras helt.
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    blockedCounter.incrementAndGet()
                    notice = "Popup-fönster blockerat"
                    return false
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.deny()
                }
            }

            loadUrl(result.detailUrl)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    // Ny video: nollställ statusen och läs in den nya sidan.
    LaunchedEffect(result.id) {
        isLoading = true
        hasError = false
        videoReady = false
        resumeApplied = false
        positionMs = 0L
        durationMs = 0L
        blockedCount = 0
    }

    // Håll blockeringsnivån uppdaterad utan att skapa en ny WebView.
    LaunchedEffect(adBlockLevel) {
        levelHolder[0] = adBlockLevel
        blockingActive = adBlockLevel != AdBlockLevel.OFF
        webView.evaluateJavascript(WebAdBlock.injectionScript(adBlockLevel), null)
    }

    // Läs av videons position och skicka vidare den till appen med jämna mellanrum.
    LaunchedEffect(webView) {
        var tick = 0
        while (true) {
            webView.evaluateJavascript("window.__pcState()") { value ->
                val state = parsePlayerState(value)
                if (state != null) {
                    videoReady = state.duration > 0.0
                    positionMs = (state.position * 1_000.0).toLong()
                    durationMs = (state.duration * 1_000.0).toLong()
                    isPlaying = !state.paused
                    if (tick % SAVE_EVERY_TICKS == 0 && durationMs > 0L) {
                        onProgress(currentResult.id, positionMs, durationMs)
                    }
                }
                blockedCount = blockedCounter.get()
                blockingActive = levelHolder[0] != AdBlockLevel.OFF
            }
            tick++
            delay(POLL_INTERVAL_MS)
        }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MS)
            notice = null
        }
    }

    LaunchedEffect(showControls, isPlaying, hasError) {
        if (showControls && isPlaying && !hasError) {
            delay(CONTROLS_HIDE_MS)
            showControls = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (durationMs > 0L) onProgress(currentResult.id, positionMs, durationMs)
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        // Knapparna sköter spolningen; dubbeltryck visar bara kontrollerna.
                        showControls = true
                        if (offset.x < size.width * 0.5f) {
                            webView.evaluateJavascript("window.__pcSeekBy(-$skipSeconds)", null)
                        } else {
                            webView.evaluateJavascript("window.__pcSeekBy($skipSeconds)", null)
                        }
                    }
                )
            }
    ) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

        if (isLoading) {
            LinearProgressIndicator(
                progress = { (loadProgress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter),
                color = Color(0xFF00E676),
                trackColor = Color.Transparent
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            WebTopBar(
                result = result,
                currentUrl = currentUrl,
                blockedCount = blockedCount,
                adBlockLevel = adBlockLevel,
                showMenu = showMenu,
                hasNativePlayer = onOpenNativePlayer != null,
                onBack = onBack,
                onToggleMenu = { showMenu = !showMenu },
                onReload = { webView.reload() },
                onOpenInBrowser = { openInBrowser(context, currentUrl, notice = { notice = it }) },
                onSelectAdBlockLevel = { level ->
                    onAdBlockLevelChange(level)
                    showMenu = false
                },
                onShowSiteControls = {
                    webView.evaluateJavascript("window.__pcControls(true)", null)
                    notice = "Webbläsarens videokontroller påslagna"
                    showMenu = false
                },
                onSearchTag = { tag ->
                    currentOnSearchTag(tag)
                    showMenu = false
                },
                onOpenNative = {
                    showMenu = false
                    onOpenNativePlayer?.invoke(result)
                }
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasPrevious && onPrevious != null) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Föregående video", tint = Color.White)
                    }
                }

                IconButton(
                    onClick = {
                        webView.evaluateJavascript("window.__pcSeekBy(-$skipSeconds)", null)
                    },
                    enabled = videoReady,
                    modifier = Modifier.size(58.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Tillbaka $skipSeconds sekunder", tint = Color.White)
                }

                IconButton(
                    onClick = {
                        webView.evaluateJavascript("window.__pcToggle()", null)
                    },
                    enabled = videoReady,
                    modifier = Modifier.size(76.dp).background(Color.Black.copy(alpha = 0.62f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausa" else "Spela",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                IconButton(
                    onClick = {
                        webView.evaluateJavascript("window.__pcSeekBy($skipSeconds)", null)
                    },
                    enabled = videoReady,
                    modifier = Modifier.size(58.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = "Framåt $skipSeconds sekunder", tint = Color.White)
                }

                if (hasNext && onNext != null) {
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "Nästa video", tint = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            WebBottomBar(
                positionMs = positionMs,
                durationMs = durationMs,
                blockedCount = blockedCount,
                blockingActive = blockingActive
            )
        }

        notice?.let { text ->
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 74.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (hasError) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xE6171717))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sidan kunde inte laddas", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Kontrollera anslutningen eller öppna videon i webbläsaren.",
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { hasError = false; webView.reload() }) {
                        Text("Försök igen", color = Color(0xFF80CBC4))
                    }
                    TextButton(onClick = { openInBrowser(context, currentUrl, notice = { notice = it }) }) {
                        Text("Öppna i webbläsare", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun WebTopBar(
    result: SearchResult,
    currentUrl: String,
    blockedCount: Int,
    adBlockLevel: AdBlockLevel,
    showMenu: Boolean,
    hasNativePlayer: Boolean,
    onBack: () -> Unit,
    onToggleMenu: () -> Unit,
    onReload: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onSelectAdBlockLevel: (AdBlockLevel) -> Unit,
    onShowSiteControls: () -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenNative: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.72f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tillbaka", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = result.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(result.siteLabel)
                        result.qualityLabel?.let { append("  ·  $it") }
                        result.durationLabel?.let { append("  ·  $it") }
                        if (blockedCount > 0) append("  ·  $blockedCount blockerade")
                    },
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (blockedCount > 0) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10331F))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("$blockedCount", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }

            Box {
                IconButton(onClick = onToggleMenu) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Mer", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onToggleMenu
                ) {
                    DropdownMenuItem(
                        text = { Text("Ladda om sidan") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = onReload
                    )
                    DropdownMenuItem(
                        text = { Text("Öppna i webbläsare") },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                        onClick = onOpenInBrowser
                    )
                    DropdownMenuItem(
                        text = { Text("Visa videokontroller") },
                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                        onClick = onShowSiteControls
                    )
                    if (hasNativePlayer) {
                        DropdownMenuItem(
                            text = { Text("Direktuppspelning (beta)") },
                            onClick = onOpenNative
                        )
                    }
                    Text(
                        text = "Annonsblockering: ${adBlockLevel.label}",
                        color = Color(0xFF98A2B3),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp)
                    )
                    AdBlockLevel.entries.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level.label) },
                            onClick = { onSelectAdBlockLevel(level) }
                        )
                    }
                }
            }
        }

        if (result.tags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(result.tags.take(18), key = { it }) { tag ->
                    TagChip(text = tag, selected = false, onClick = { onSearchTag(tag) })
                }
            }
        }

        if (result.description != null) {
            Text(
                text = result.description,
                color = Color(0xFF98A2B3),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun WebBottomBar(
    positionMs: Long,
    durationMs: Long,
    blockedCount: Int,
    blockingActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.62f))
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = formatClock(positionMs), color = Color.White, fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            if (durationMs > 0L) {
                val fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .background(Color(0xFF00E676))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (durationMs > 0L) formatClock(durationMs) else "—",
            color = Color(0xFFB0BEC5),
            fontSize = 12.sp
        )
        if (blockingActive) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.Block, contentDescription = "Annonsblockering på", tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
            if (blockedCount > 0) {
                Spacer(Modifier.width(3.dp))
                Text("$blockedCount", color = Color(0xFF00E676), fontSize = 11.sp)
            }
        }
    }
}

private fun emptyResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

private data class PlayerState(
    val position: Double,
    val duration: Double,
    val paused: Boolean,
    val videoWidth: Int,
    val videoHeight: Int
)

/** Tolkar svaret från `window.__pcState()` (kan vara en JSON-sträng inkapslad i en sträng). */
fun parsePlayerState(raw: String?): PlayerState? {
    val decoded = decodeJsString(raw) ?: return null
    val map = MiniJson.parse(decoded) as? Map<*, *> ?: return null
    val position = (map["t"] as? Number)?.toDouble() ?: return null
    val duration = (map["d"] as? Number)?.toDouble() ?: 0.0
    return PlayerState(
        position = position,
        duration = duration,
        paused = map["p"] as? Boolean ?: true,
        videoWidth = (map["vw"] as? Number)?.toInt() ?: 0,
        videoHeight = (map["vh"] as? Number)?.toInt() ?: 0
    )
}

/** evaluateJavascript returnerar resultatet som JSON, ibland som en inkapslad sträng. */
private fun decodeJsString(raw: String?): String? {
    val value = raw?.trim() ?: return null
    if (value.isEmpty() || value == "null" || value == "\"\"") return null
    val parsed = MiniJson.parse(value)
    return when (parsed) {
        is String -> parsed
        is Map<*, *> -> value
        is List<*> -> value
        else -> null
    }
}

private fun formatClock(milliseconds: Long): String {
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

private fun openInBrowser(context: Context, url: String, notice: (String) -> Unit) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        notice("Kunde inte öppna webbläsaren")
    }
}

private const val WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
private const val POLL_INTERVAL_MS = 1_000L
private const val SAVE_EVERY_TICKS = 5
private const val CONTROLS_HIDE_MS = 4_500L
private const val NOTICE_MS = 2_600L
