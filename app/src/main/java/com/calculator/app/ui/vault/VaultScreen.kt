package com.calculator.app.ui.vault

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calculator.app.ui.home.HomeScreen
import com.calculator.app.ui.library.LibraryScreen
import com.calculator.app.ui.library.LibraryViewModel
import com.calculator.app.ui.library.LibraryViewModelFactory
import com.calculator.app.ui.player.VideoPlayerScreen
import com.calculator.app.ui.web.WebPlayerScreen
import com.calculator.app.ui.search.SearchScreen
import com.calculator.app.ui.search.SearchViewModel
import com.calculator.app.ui.search.SearchViewModelFactory
import com.calculator.app.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import com.calculator.app.ui.theme.VaultAccent
import com.calculator.app.ui.theme.VaultBackground
import com.calculator.app.ui.theme.VaultError
import com.calculator.app.ui.theme.VaultGradient
import com.calculator.app.ui.theme.VaultSurface
import com.calculator.app.ui.theme.VaultTextPrimary
import com.calculator.app.ui.theme.VaultTextSecondary
import com.calculator.app.ui.theme.VaultTheme

enum class VaultTab { HOME, SEARCH, LIBRARY, SETTINGS }

private const val AGE_CONFIRMATION_PREFERENCES = "calculator_content_access"
private const val AGE_CONFIRMATION_KEY = "adult_content_confirmed"

@Composable
fun VaultScreen(
    isDecoy: Boolean = false,
    onLock: () -> Unit,
    onChangeRealPin: (String) -> Boolean = { false },
    onChangeDecoyPin: (String) -> Boolean = { false }
) {
    val context = LocalContext.current
    val agePreferences = remember(context, isDecoy) {
        context.applicationContext.getSharedPreferences(AGE_CONFIRMATION_PREFERENCES, Context.MODE_PRIVATE)
    }
    var adultContentConfirmed by remember(isDecoy) {
        mutableStateOf(isDecoy || agePreferences.getBoolean(AGE_CONFIRMATION_KEY, false))
    }

    if (!isDecoy && !adultContentConfirmed) {
        AdultContentConfirmation(
            onConfirm = {
                agePreferences.edit().putBoolean(AGE_CONFIRMATION_KEY, true).apply()
                adultContentConfirmed = true
            },
            onExit = onLock
        )
        return
    }

    // Decoy-läget ska aldrig röra sök- eller databasvyerna.
    if (isDecoy) {
        VaultTheme {
            Box(modifier = Modifier.fillMaxSize().background(VaultGradient)) {
                DecoyContent(onLock = onLock)
            }
        }
        return
    }

    VaultTheme {
        VaultContent(
            onLock = onLock,
            onChangeRealPin = onChangeRealPin,
            onChangeDecoyPin = onChangeDecoyPin
        )
    }
}

@Composable
private fun VaultContent(
    onLock: () -> Unit,
    onChangeRealPin: (String) -> Boolean,
    onChangeDecoyPin: (String) -> Boolean
) {
    val context = LocalContext.current
    // Validate storage before constructing ViewModels whose Room flows would otherwise
    // fail asynchronously. Never hide a missing key by creating a plaintext vault.
    val storage by androidx.compose.runtime.produceState<Result<com.calculator.app.data.AppDatabase>?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                com.calculator.app.data.AppDatabase.getInstance(context).also { it.openHelper.writableDatabase }
            }
        }
    }
    val storageState = storage
    if (storageState == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }
    if (storageState.isFailure) {
        AlertDialog(
            onDismissRequest = onLock,
            title = { Text("Lagringen kunde inte öppnas") },
            text = { Text("Krypteringen eller databasnyckeln är inte tillgänglig. Inga data har raderats och ingen okrypterad databas skapas. Försök starta om appen.") },
            confirmButton = { TextButton(onClick = onLock) { Text("Lås") } }
        )
        return
    }

    val application = remember(context) { context.applicationContext as Application }

    var currentTab by remember { mutableStateOf(VaultTab.HOME) }
    var pendingCategoryId by remember { mutableStateOf<String?>(null) }
    var pinToChange by remember { mutableStateOf<String?>(null) }

    // Vault ViewModels must not survive a lock in the Activity's ViewModelStore.
    val vaultOwner = remember {
        object : androidx.lifecycle.ViewModelStoreOwner {
            override val viewModelStore = androidx.lifecycle.ViewModelStore()
        }
    }
    val searchViewModel: SearchViewModel = viewModel(viewModelStoreOwner = vaultOwner, factory = SearchViewModelFactory(application))
    val libraryViewModel: LibraryViewModel = viewModel(viewModelStoreOwner = vaultOwner, factory = LibraryViewModelFactory(application))

    DisposableEffect(searchViewModel) {
        onDispose {
            searchViewModel.clearSensitiveState()
            vaultOwner.viewModelStore.clear()
        }
    }

    // Panik/lås: töm känsliga data ur minnet innan appen lämnar vaulten.
    val lockEverything: () -> Unit = {
        searchViewModel.clearSensitiveState()
        onLock()
    }

    // Autolås efter inaktivitet. Spelarna ligger före den här punkten, så tiden räknas
    // bara medan användaren faktiskt är i menyerna – en film som spelas låser inte appen.
    val autoLockModifier = rememberAutoLockModifier(
        timeoutMillis = if (searchViewModel.webPlaying != null || searchViewModel.nowPlaying != null) 0L
            else searchViewModel.preferences.autoLock.timeoutMillis,
        onTimeout = lockEverything
    )

    val favorites by libraryViewModel.favorites.collectAsState()
    val watchLater by libraryViewModel.watchLater.collectAsState()
    val rules by libraryViewModel.dislikeRules.collectAsState()
    val presets by libraryViewModel.presets.collectAsState()
    val favoriteCount by libraryViewModel.favoriteCount.collectAsState()
    val watchLaterCount by libraryViewModel.watchLaterCount.collectAsState()
    val dislikeCount by libraryViewModel.dislikeCount.collectAsState()
    val presetCount by libraryViewModel.presetCount.collectAsState()

    val playbackRevision = searchViewModel.playbackRevision
    val continueWatching = remember(playbackRevision) { searchViewModel.continueWatching() }
    val recentPlaybacks = remember(playbackRevision) { searchViewModel.recentPlaybacks(24) }

    // --- WebView-spelaren (standard): videon spelas på sajten med appens annonsblockering ---
    val onSite = searchViewModel.webPlaying
    if (onSite != null) {
        androidx.compose.runtime.key(onSite.result.id) {
        WebPlayerScreen(
            result = onSite.result,
            skipSeconds = searchViewModel.preferences.skipSeconds,
            keepScreenOn = searchViewModel.preferences.keepScreenOn,
            adBlockLevel = searchViewModel.adBlockLevel,
            onAdBlockLevelChange = { searchViewModel.onAdBlockLevelChange(it) },
            startPositionMs = onSite.startPositionMs,
            hasNext = onSite.hasNext,
            hasPrevious = onSite.hasPrevious,
            onNext = { searchViewModel.playRelative(1) },
            onPrevious = { searchViewModel.playRelative(-1) },
            onProgress = { videoId, positionMs, durationMs ->
                searchViewModel.onPlaybackProgress(videoId, positionMs, durationMs)
            },
            onSearchTag = { tag ->
                searchViewModel.closeWebPlayer()
                currentTab = VaultTab.SEARCH
                searchViewModel.searchTag(tag)
            },
            onOpenNativePlayer = { result -> searchViewModel.playNativeStream(result) },
            onBack = { searchViewModel.closeWebPlayer() }
        )
        }
        return
    }

    // --- Inbyggd spelare (beta) ---
    val playing = searchViewModel.nowPlaying
    if (playing != null) {
        VideoPlayerScreen(
            videoUrl = playing.streamUrl,
            title = playing.result.title,
            subtitle = listOfNotNull(
                playing.result.siteLabel,
                playing.result.qualityLabel
            ).joinToString("  ·  "),
            refererUrl = playing.result.detailUrl,
            videoId = playing.result.id,
            startPositionMs = playing.startPositionMs,
            skipSeconds = searchViewModel.preferences.skipSeconds,
            preferredQuality = searchViewModel.preferences.preferredQuality,
            keepScreenOn = searchViewModel.preferences.keepScreenOn,
            gesturesEnabled = searchViewModel.preferences.gestureSeeking,
            hasNext = playing.hasNext,
            hasPrevious = playing.hasPrevious,
            onNext = { searchViewModel.playRelative(1) },
            onPrevious = { searchViewModel.playRelative(-1) },
            onProgress = { videoId, positionMs, durationMs ->
                searchViewModel.onPlaybackProgress(videoId, positionMs, durationMs)
            },
            onBack = { searchViewModel.closePlayer() },
            onPlaybackError = { videoId -> searchViewModel.onStreamFailed(videoId) }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().then(autoLockModifier),
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = VaultSurface, contentColor = VaultTextPrimary) {
                VaultNavItem(Icons.Default.Home, "Hem", currentTab == VaultTab.HOME) { currentTab = VaultTab.HOME }
                VaultNavItem(Icons.Default.Search, "Sök", currentTab == VaultTab.SEARCH) { currentTab = VaultTab.SEARCH }
                VaultNavItem(Icons.Default.VideoLibrary, "Bibliotek", currentTab == VaultTab.LIBRARY) { currentTab = VaultTab.LIBRARY }
                VaultNavItem(Icons.Default.Settings, "Mer", currentTab == VaultTab.SETTINGS) { currentTab = VaultTab.SETTINGS }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VaultGradient)
                .padding(innerPadding)
        ) {
            when (currentTab) {
                VaultTab.HOME -> HomeScreen(
                    continueWatching = continueWatching,
                    feed = searchViewModel.lastFeed,
                    isLoading = searchViewModel.isSearching,
                    resolvingTitle = searchViewModel.resolvingTitle,
                    onResume = { entry -> searchViewModel.playRecent(entry) },
                    onPlay = { result -> searchViewModel.playVideo(result) },
                    onFavorite = { result -> searchViewModel.addFavorite(result) },
                    onWatchLater = { result -> searchViewModel.addWatchLater(result) },
                    onQuickCategory = { terms ->
                        currentTab = VaultTab.SEARCH
                        searchViewModel.applyQuickPick(terms)
                    },
                    onOpenSearch = { categoryId ->
                        pendingCategoryId = categoryId
                        currentTab = VaultTab.SEARCH
                    },
                    onLock = lockEverything
                )

                VaultTab.SEARCH -> SearchScreen(
                    viewModel = searchViewModel,
                    requestedCategoryId = pendingCategoryId,
                    onCategoryRequestHandled = { pendingCategoryId = null }
                )

                VaultTab.LIBRARY -> LibraryScreen(
                    favoriteCount = favoriteCount,
                    watchLaterCount = watchLaterCount,
                    dislikeCount = dislikeCount,
                    presetCount = presetCount,
                    favorites = favorites,
                    watchLater = watchLater,
                    rules = rules,
                    presets = presets,
                    history = recentPlaybacks,
                    onResume = { entry -> searchViewModel.playRecent(entry) },
                    onPlayVideo = { video -> searchViewModel.playSavedVideo(video) },
                    onClearHistory = { searchViewModel.clearPlaybackHistory() },
                    onUsePreset = { preset ->
                        searchViewModel.useSearchPreset(preset)
                        currentTab = VaultTab.SEARCH
                    },
                    onRemoveFavorite = { id -> libraryViewModel.removeFavorite(id) },
                    onRemoveWatchLater = { id -> libraryViewModel.removeWatchLater(id) },
                    onRemoveDislikeRule = { rule -> libraryViewModel.removeDislikeRule(rule) },
                    onRemovePreset = { preset -> libraryViewModel.removeSearchPreset(preset) }
                )

                VaultTab.SETTINGS -> SettingsScreen(
                    preferences = searchViewModel.preferences,
                    sites = searchViewModel.siteInfos,
                    disabledSiteIds = searchViewModel.disabledSiteIds,
                    onSiteEnabledChange = { siteId, enabled -> searchViewModel.onSiteEnabledChange(siteId, enabled) },
                    historySize = searchViewModel.searchHistory.size,
                    onlyPlayable = searchViewModel.onlyPlayable,
                    onOnlyPlayableChange = { searchViewModel.onOnlyPlayableChange(it) },
                    adBlockLevel = searchViewModel.adBlockLevel,
                    onAdBlockLevelChange = { searchViewModel.onAdBlockLevelChange(it) },
                    onLock = lockEverything,
                    onOpenChangePin = { pinType -> pinToChange = pinType },
                    onClearSearchHistory = { searchViewModel.clearSearchHistory() },
                    rulesEnabled = searchViewModel.remoteRulesEnabled,
                    rulesStatus = searchViewModel.rulesStatus.label(),
                    rulesMessage = searchViewModel.rulesMessage,
                    onRulesEnabledChange = { searchViewModel.onRemoteRulesEnabledChange(it) },
                    onRefreshRules = { searchViewModel.refreshRulesNow() },
                    onResetRules = { searchViewModel.resetRules() }
                )
            }
        }
    }

    pinToChange?.let { pinType ->
        PinChangeDialog(
            title = if (pinType == "real") "Byt huvudkod" else "Byt reservkod",
            onDismiss = { pinToChange = null },
            onSave = { pin ->
                val saved = if (pinType == "real") onChangeRealPin(pin) else onChangeDecoyPin(pin)
                if (saved) pinToChange = null
                saved
            }
        )
    }
}

@Composable
private fun RowScope.VaultNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, fontSize = 11.sp) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = VaultAccent,
            selectedTextColor = VaultAccent,
            unselectedIconColor = VaultTextSecondary,
            unselectedTextColor = VaultTextSecondary,
            indicatorColor = VaultBackground
        )
    )
}

@Composable
private fun AdultContentConfirmation(onConfirm: () -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onExit,
        title = { Text("Åldersbekräftelse") },
        text = {
            Text("Den här delen kan innehålla vuxet material. Fortsätt endast om du är minst 18 år eller myndig enligt lokal lag.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Jag är 18+ – fortsätt") }
        },
        dismissButton = {
            TextButton(onClick = onExit) { Text("Tillbaka") }
        }
    )
}

@Composable
private fun PinChangeDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        if (value.length <= 12 && value.all { it in '0'..'9' }) pin = value
                    },
                    label = { Text("Ny kod (4–12 siffror)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { value ->
                        if (value.length <= 12 && value.all { it in '0'..'9' }) confirmation = value
                    },
                    label = { Text("Bekräfta ny kod") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = VaultError, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    errorMessage = when {
                        pin.length !in 4..12 -> "Koden måste vara 4–12 siffror."
                        pin != confirmation -> "Koderna stämmer inte."
                        !onSave(pin) -> "Koden måste skilja sig från den andra koden."
                        else -> null
                    }
                }
            ) { Text("Spara") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}

@Composable
private fun DecoyContent(onLock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = VaultAccent, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("Private Notes", color = VaultTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Inga anteckningar sparade ännu.",
            color = VaultTextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onLock,
            colors = ButtonDefaults.buttonColors(containerColor = VaultSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Stäng", color = VaultTextPrimary)
        }
    }
}

/**
 * Modifierare som låser vaulten efter en stund utan att användaren rört skärmen.
 *
 * Tryck och svep observeras i den första passen och konsumeras aldrig, så att listor,
 * spelare och knappar fungerar precis som förut. Tiden kontrolleras med jämna mellanrum
 * i stället för att starta en timer per beröring.
 */
@Composable
private fun rememberAutoLockModifier(timeoutMillis: Long, onTimeout: () -> Unit): Modifier {
    if (timeoutMillis <= 0L) return Modifier

    val lastInteraction = remember { AtomicLong(android.os.SystemClock.elapsedRealtime()) }
    val currentTimeout by rememberUpdatedState(onTimeout)

    LaunchedEffect(timeoutMillis) {
        while (true) {
            delay(AUTO_LOCK_CHECK_INTERVAL_MS)
            if (android.os.SystemClock.elapsedRealtime() - lastInteraction.get() >= timeoutMillis) {
                currentTimeout()
                break
            }
        }
    }

    return Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                lastInteraction.set(android.os.SystemClock.elapsedRealtime())
            }
        }
    }
}

private const val AUTO_LOCK_CHECK_INTERVAL_MS = 5_000L
