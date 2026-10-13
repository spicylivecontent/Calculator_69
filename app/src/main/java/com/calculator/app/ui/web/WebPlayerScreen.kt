package com.calculator.app.ui.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.calculator.app.data.extractor.MiniJson
import com.calculator.app.data.extractor.SiteUrlPolicy
import com.calculator.app.data.model.SearchResult
import com.calculator.app.ui.theme.VaultPink
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Spelar upp videon direkt i helskärm på sajten i en WebView med appens egen
 * annonsblockering, popup-skydd och omdirigeringsskydd – utan överliggande
 * dubblettspelare och med en liten knapp för att spara i favoriter medan filmen spelas.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPlayerScreen(
    result: SearchResult,
    trustedSiteBaseUrl: String?,
    onBack: () -> Unit,
    skipSeconds: Int = 10,
    keepScreenOn: Boolean = true,
    gesturesEnabled: Boolean = true,
    adBlockLevel: AdBlockLevel = AdBlockLevel.STANDARD,
    onAdBlockLevelChange: (AdBlockLevel) -> Unit = {},
    startPositionMs: Long = 0L,
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onProgress: (String, Long, Long) -> Unit = { _, _, _ -> },
    onSearchTag: (String) -> Unit = {},
    onOpenNativePlayer: ((SearchResult) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentResult by rememberUpdatedState(result)
    val isInitialUrlTrusted = remember(result.id, result.detailUrl, trustedSiteBaseUrl) {
        SiteUrlPolicy.isSameSiteHttps(result.detailUrl, trustedSiteBaseUrl)
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var blockedCount by remember { mutableIntStateOf(0) }
    var currentUrl by remember(result.id) { mutableStateOf("") }
    var resumePositionMs by remember(result.id) { mutableLongStateOf(startPositionMs.coerceAtLeast(0L)) }
    var notice by remember { mutableStateOf<String?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var resumeApplied by remember { mutableStateOf(false) }
    val webHost = remember(result.id) { WebPlayerHost(context) }

    val blockedCounter = remember(result.id) { AtomicInteger(0) }
    val levelHolder = remember { arrayOf(adBlockLevel) }

    // Fullskärm: dölj systemfälten medan spelaren är öppen.
    DisposableEffect(activity, keepScreenOn) {
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
                setSupportMultipleWindows(true)
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
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
                    // Only the app uses about:blank during teardown; page-initiated
                    // navigations are still rejected by shouldOverrideUrlLoading.
                    if (url == "about:blank") return null
                    if (request?.isForMainFrame == true &&
                        WebAdBlock.navigationFor(result.detailUrl, url, levelHolder[0]) != NavigationDecision.ALLOW
                    ) {
                        // Navigation callbacks do not cover every redirect path. Intercept the
                        // main-frame request too so an off-site redirect never reaches the network.
                        blockedCounter.incrementAndGet()
                        return emptyResponse()
                    }
                    if (WebAdBlock.isBlockedRequest(url, levelHolder[0], result.detailUrl)) {
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
                    return when (WebAdBlock.navigationFor(result.detailUrl, target, levelHolder[0])) {
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
                    url?.takeIf {
                        WebAdBlock.navigationFor(result.detailUrl, it, levelHolder[0]) == NavigationDecision.ALLOW
                    }?.let { currentUrl = it }
                    isLoading = true
                    hasError = false
                    view?.evaluateJavascript(WebAdBlock.injectionScript(levelHolder[0]), null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    view?.let { target ->
                        target.evaluateJavascript(WebAdBlock.injectionScript(levelHolder[0]), null)
                        if (resumePositionMs > 15_000L && !resumeApplied) {
                            resumeApplied = true
                            val seconds = (resumePositionMs / 1_000L).toString()
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
                    if (newProgress >= 20) {
                        view?.evaluateJavascript(WebAdBlock.injectionScript(levelHolder[0]), null)
                    }
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

                // HTML5 players provide their fullscreen surface through these callbacks.
                override fun onShowCustomView(
                    view: View?,
                    callback: WebChromeClient.CustomViewCallback?
                ) {
                    if (view == null || callback == null) return
                    webHost.showCustomView(view, callback)
                }

                override fun onHideCustomView() {
                    webHost.hideCustomView()
                    evaluateJavascript("if(window.__pcFullscreen){window.__pcFullscreen();}", null)
                }
            }

            webHost.attachWebView(this)
        }
    }

    DisposableEffect(webView, webHost) {
        onDispose {
            webHost.destroyWebView()
        }
    }

    DisposableEffect(webView, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                webView.evaluateJavascript("var v=window.__pcVideo&&window.__pcVideo();if(v){v.pause();}", null)
                webView.onPause()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                webView.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Only start on an HTTPS detail URL that belongs to the declared source site.
    LaunchedEffect(result.id, result.detailUrl, webView, isInitialUrlTrusted) {
        isLoading = isInitialUrlTrusted
        hasError = !isInitialUrlTrusted
        resumeApplied = false
        resumePositionMs = startPositionMs.coerceAtLeast(0L)
        positionMs = 0L
        durationMs = 0L
        blockedCount = 0
        currentUrl = if (isInitialUrlTrusted) result.detailUrl else ""
        if (isInitialUrlTrusted) {
            webView.loadUrl(result.detailUrl)
        } else {
            notice = "Videoadressen avvisades eftersom den inte tillhör källsajten."
        }
    }

    // Håll blockeringsnivån uppdaterad utan att skapa en ny WebView.
    LaunchedEffect(adBlockLevel) {
        val changed = levelHolder[0] != adBlockLevel
        levelHolder[0] = adBlockLevel
        if (changed) {
            if (positionMs > 0L) resumePositionMs = positionMs
            resumeApplied = false
            webView.reload()
        }
        webView.evaluateJavascript(WebAdBlock.injectionScript(adBlockLevel), null)
    }

    // Läs av videons position och skicka vidare den till appen med jämna mellanrum.
    LaunchedEffect(webView) {
        var tick = 0
        while (true) {
            webView.evaluateJavascript("window.__pcState()") { value ->
                val state = parsePlayerState(value)
                if (state != null) {
                    positionMs = (state.position * 1_000.0).toLong()
                    durationMs = (state.duration * 1_000.0).toLong()
                    if (tick % SAVE_EVERY_TICKS == 0 && durationMs > 0L) {
                        onProgress(currentResult.id, positionMs, durationMs)
                    }
                }
                blockedCount = blockedCounter.get()
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

    DisposableEffect(Unit) {
        onDispose {
            if (durationMs > 0L) onProgress(currentResult.id, positionMs, durationMs)
        }
    }

    // När man backar lämnar man direkt videon och kommer tillbaka till appen.
    BackHandler {
        if (webHost.isShowingCustomView) {
            webHost.hideCustomView()
        }
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(factory = { webHost }, modifier = Modifier.fillMaxSize())

        if (isLoading) {
            LinearProgressIndicator(
                progress = { (loadProgress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                color = Color(0xFF00E676),
                trackColor = Color.Transparent
            )
        }

        // Liten diskret knapp i hörnet för att spara i favoriter + meny för inställningar medan filmen spelas.
        WebFloatingActions(
            isFavorite = isFavorite,
            blockedCount = blockedCount,
            adBlockLevel = adBlockLevel,
            showMenu = showMenu,
            hasPrevious = hasPrevious && onPrevious != null,
            hasNext = hasNext && onNext != null,
            hasNativePlayer = onOpenNativePlayer != null,
            onToggleFavorite = {
                notice = if (isFavorite) "Borttagen ur favoriter" else "Sparad i favoriter"
                onToggleFavorite()
            },
            onToggleMenu = { showMenu = !showMenu },
            onPrevious = {
                showMenu = false
                onPrevious?.invoke()
            },
            onNext = {
                showMenu = false
                onNext?.invoke()
            },
            onReload = {
                showMenu = false
                if (positionMs > 0L) resumePositionMs = positionMs
                resumeApplied = false
                webView.reload()
            },
            onOpenInBrowser = {
                showMenu = false
                openInBrowser(context, currentUrl, notice = { notice = it })
            },
            onSelectAdBlockLevel = { level ->
                onAdBlockLevelChange(level)
                showMenu = false
            },
            onShowSiteControls = {
                webView.evaluateJavascript("window.__pcControls(true)", null)
                notice = "Webbläsarens videokontroller påslagna"
                showMenu = false
            },
            onOpenNative = {
                showMenu = false
                onOpenNativePlayer?.invoke(result)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 8.dp)
        )

        notice?.let { text ->
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 64.dp)
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
                    TextButton(onClick = {
                        hasError = false
                        if (positionMs > 0L) resumePositionMs = positionMs
                        resumeApplied = false
                        webView.reload()
                    }) {
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
private fun WebFloatingActions(
    isFavorite: Boolean,
    blockedCount: Int,
    adBlockLevel: AdBlockLevel,
    showMenu: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    hasNativePlayer: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleMenu: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReload: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onSelectAdBlockLevel: (AdBlockLevel) -> Unit,
    onShowSiteControls: () -> Unit,
    onOpenNative: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.52f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Ta bort från favoriter" else "Spara i favoriter",
                tint = if (isFavorite) VaultPink else Color.White,
                modifier = Modifier.size(19.dp)
            )
        }

        Box {
            IconButton(
                onClick = onToggleMenu,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Mer",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = onToggleMenu
            ) {
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "Ta bort från favoriter" else "Spara i favoriter") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) VaultPink else Color.Unspecified
                        )
                    },
                    onClick = {
                        onToggleMenu()
                        onToggleFavorite()
                    }
                )
                if (hasPrevious) {
                    DropdownMenuItem(
                        text = { Text("Föregående video") },
                        leadingIcon = { Icon(Icons.Default.SkipPrevious, contentDescription = null) },
                        onClick = onPrevious
                    )
                }
                if (hasNext) {
                    DropdownMenuItem(
                        text = { Text("Nästa video") },
                        leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null) },
                        onClick = onNext
                    )
                }
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
                    text = buildString {
                        append("Annonsblockering: ${adBlockLevel.label}")
                        if (blockedCount > 0) append(" ($blockedCount blockerade)")
                    },
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
}

private fun emptyResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

internal data class PlayerState(
    val position: Double,
    val duration: Double,
    val paused: Boolean,
    val videoWidth: Int,
    val videoHeight: Int
)

/** Tolkar svaret från `window.__pcState()` (kan vara en JSON-sträng inkapslad i en sträng). */
internal fun parsePlayerState(raw: String?): PlayerState? {
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
private const val NOTICE_MS = 2_600L
