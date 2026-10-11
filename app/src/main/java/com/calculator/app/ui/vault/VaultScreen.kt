package com.calculator.app.ui.vault

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.calculator.app.data.entity.DislikeRuleEntity
import com.calculator.app.data.entity.VideoEntity
import com.calculator.app.data.model.SearchResult
import com.calculator.app.ui.library.LibraryViewModel
import com.calculator.app.ui.library.LibraryViewModelFactory
import com.calculator.app.ui.player.TestPlayerButton
import com.calculator.app.ui.player.VideoPlayerScreen
import com.calculator.app.ui.search.SearchViewModel
import com.calculator.app.ui.search.SearchViewModelFactory

enum class VaultTab { HOME, SEARCH, LIBRARY, SETTINGS }
enum class LibrarySection { OVERVIEW, FAVORITES, WATCH_LATER, DISLIKES }

@Composable
fun VaultScreen(
    isDecoy: Boolean = false,
    onLock: () -> Unit
) {
    var currentTab by remember { mutableStateOf(VaultTab.HOME) }
    var showTestPlayer by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModelFactory(context.applicationContext as Application)
    )

    // Riktig videoplayer från sökresultat
    if (searchViewModel.playingUrl != null) {
        VideoPlayerScreen(
            videoUrl = searchViewModel.playingUrl!!,
            title = searchViewModel.playingTitle,
            onBack = { searchViewModel.closePlayer() }
        )
        return
    }

    if (showTestPlayer) {
        VideoPlayerScreen(
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            title = "Big Buck Bunny (Test)",
            onBack = { showTestPlayer = false }
        )
        return
    }

    val backgroundBrush = if (isDecoy) {
        Brush.verticalGradient(listOf(Color(0xFF2C2C2C), Color(0xFF1E1E1E)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF0D0D1A), Color(0xFF12122B), Color(0xFF1A1A3A)))
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (!isDecoy) {
                NavigationBar(containerColor = Color(0xFF1A1A2E), contentColor = Color.White) {
                    VaultNavItem(Icons.Default.Home, "Home", currentTab == VaultTab.HOME) { currentTab = VaultTab.HOME }
                    VaultNavItem(Icons.Default.Search, "Search", currentTab == VaultTab.SEARCH) { currentTab = VaultTab.SEARCH }
                    VaultNavItem(Icons.Default.VideoLibrary, "Library", currentTab == VaultTab.LIBRARY) { currentTab = VaultTab.LIBRARY }
                    VaultNavItem(Icons.Default.Settings, "Settings", currentTab == VaultTab.SETTINGS) { currentTab = VaultTab.SETTINGS }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
        ) {
            if (isDecoy) {
                DecoyContent(onLock = onLock)
            } else {
                when (currentTab) {
                    VaultTab.HOME -> HomeTab(
                        onLock = onLock,
                        onPlayTest = { showTestPlayer = true },
                        feed = searchViewModel.lastFeed,
                        isResolving = searchViewModel.isResolvingStream,
                        onPlay = { searchViewModel.playVideo(it) },
                        onFavorite = { searchViewModel.addFavorite(it) },
                        onWatchLater = { searchViewModel.addWatchLater(it) }
                    )
                    VaultTab.SEARCH -> SearchTab(viewModel = searchViewModel)
                    VaultTab.LIBRARY -> LibraryTab()
                    VaultTab.SETTINGS -> SettingsTab(onLock = onLock)
                }
            }
        }
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
            selectedIconColor = Color(0xFF00E676),
            selectedTextColor = Color(0xFF00E676),
            unselectedIconColor = Color(0xFF90A4AE),
            unselectedTextColor = Color(0xFF90A4AE),
            indicatorColor = Color(0xFF1A1A2E)
        )
    )
}

@Composable
private fun HomeTab(
    onLock: () -> Unit,
    onPlayTest: () -> Unit,
    feed: List<SearchResult>,
    isResolving: Boolean,
    onPlay: (SearchResult) -> Unit,
    onFavorite: (SearchResult) -> Unit,
    onWatchLater: (SearchResult) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Discover", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onLock) {
                Icon(Icons.Default.Lock, "Lås", tint = Color(0xFFFF5252))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        TestPlayerButton(onPlayTest = onPlayTest)
        Spacer(modifier = Modifier.height(12.dp))

        if (isResolving) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF00E676)
            )
            Text("Hämtar stream...", color = Color(0xFF90A4AE), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (feed.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3A)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("10 sajter aktiva", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Eporner, SpankBang, HQporner, XVideos, XNXX, xHamster, Pornhub, Beeg, YouPorn, PornTrex\n\nSök och tryck på ett resultat för att spela.",
                        color = Color(0xFF90A4AE),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            Text("Senaste sökning (${feed.size} st) – tryck för att spela", color = Color(0xFFB0BEC5), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(feed) { result ->
                    FeedCard(
                        result = result,
                        onPlay = { onPlay(result) },
                        onFavorite = { onFavorite(result) },
                        onWatchLater = { onWatchLater(result) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedCard(
    result: SearchResult,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onWatchLater: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = result.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(110.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A4A))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(result.sourceSite, color = Color(0xFF00E676), fontSize = 11.sp)
            }
            Column {
                IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF4081), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onWatchLater, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.WatchLater, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchTab(viewModel: SearchViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Search", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = viewModel.query,
                onValueChange = { viewModel.onQueryChange(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("brunette pov, no anal...", color = Color(0xFF78909C)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E676),
                    unfocusedBorderColor = Color(0xFF37474F),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF00E676)
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.search() },
                enabled = !viewModel.isLoading && viewModel.query.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(56.dp)
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text("Sök", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = viewModel.negativeKeywords,
            onValueChange = { viewModel.onNegativeKeywordsChange(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Extra exkludera (valfritt)", color = Color(0xFF78909C)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFF5252),
                unfocusedBorderColor = Color(0xFF37474F),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFFFF5252)
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        viewModel.aiExplanation?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = Color(0xFF80CBC4), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "${viewModel.supportedSites.size} sajter  •  Tryck på resultat för att spela",
            color = Color(0xFF607D8B),
            fontSize = 11.sp
        )

        if (viewModel.isResolvingStream) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF00E676))
            Text("Hämtar stream...", color = Color(0xFF90A4AE), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        viewModel.errorMessage?.let {
            Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            items(viewModel.results) { result ->
                SearchResultCard(
                    result = result,
                    onPlay = { viewModel.playVideo(result) },
                    onFavorite = { viewModel.addFavorite(result) },
                    onWatchLater = { viewModel.addWatchLater(result) },
                    onNeverShow = { viewModel.addNeverShowAgain(it) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onWatchLater: () -> Unit,
    onNeverShow: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = result.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(100.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A4A))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    result.duration?.let {
                        Text(it, color = Color(0xFF90A4AE), fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(result.sourceSite, color = Color(0xFF00E676), fontSize = 11.sp)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF4081), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onWatchLater, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.WatchLater, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        val word = result.title.split(" ")
                            .map { it.trim().lowercase() }
                            .firstOrNull { it.length > 3 } ?: result.title.take(12)
                        onNeverShow(word)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Block, null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryTab() {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModelFactory(context.applicationContext as Application)
    )

    var section by remember { mutableStateOf(LibrarySection.OVERVIEW) }

    val favCount by viewModel.favoriteCount.collectAsState()
    val laterCount by viewModel.watchLaterCount.collectAsState()
    val dislikeCount by viewModel.dislikeCount.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val watchLater by viewModel.watchLater.collectAsState()
    val rules by viewModel.dislikeRules.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Library", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        when (section) {
            LibrarySection.OVERVIEW -> {
                LibraryOverviewItem("Favoriter", "$favCount videor", Color(0xFFFF4081)) { section = LibrarySection.FAVORITES }
                Spacer(modifier = Modifier.height(10.dp))
                LibraryOverviewItem("Watch Later", "$laterCount videor", Color(0xFF64B5F6)) { section = LibrarySection.WATCH_LATER }
                Spacer(modifier = Modifier.height(10.dp))
                LibraryOverviewItem("Never Show Again", "$dislikeCount regler", Color(0xFFFF5252)) { section = LibrarySection.DISLIKES }
            }
            LibrarySection.FAVORITES -> {
                TextButton(onClick = { section = LibrarySection.OVERVIEW }) { Text("← Tillbaka", color = Color(0xFF90A4AE)) }
                Spacer(modifier = Modifier.height(8.dp))
                if (favorites.isEmpty()) Text("Inga favoriter ännu", color = Color(0xFF90A4AE))
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favorites) { video -> VideoEntityCard(video) { viewModel.removeFavorite(video.id) } }
                }
            }
            LibrarySection.WATCH_LATER -> {
                TextButton(onClick = { section = LibrarySection.OVERVIEW }) { Text("← Tillbaka", color = Color(0xFF90A4AE)) }
                Spacer(modifier = Modifier.height(8.dp))
                if (watchLater.isEmpty()) Text("Inget i Watch Later", color = Color(0xFF90A4AE))
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(watchLater) { video -> VideoEntityCard(video) { viewModel.removeWatchLater(video.id) } }
                }
            }
            LibrarySection.DISLIKES -> {
                TextButton(onClick = { section = LibrarySection.OVERVIEW }) { Text("← Tillbaka", color = Color(0xFF90A4AE)) }
                Spacer(modifier = Modifier.height(8.dp))
                if (rules.isEmpty()) Text("Inga regler ännu", color = Color(0xFF90A4AE))
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rules) { rule -> DislikeRuleCard(rule) { viewModel.removeDislikeRule(rule) } }
                }
            }
        }
    }
}

@Composable
private fun LibraryOverviewItem(title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(accent, RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Color(0xFF90A4AE), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun VideoEntityCard(video: VideoEntity, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(100.dp).height(60.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A4A))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.title, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.sourceSite, color = Color(0xFF00E676), fontSize = 11.sp)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "Ta bort", tint = Color(0xFFFF5252))
            }
        }
    }
}

@Composable
private fun DislikeRuleCard(rule: DislikeRuleEntity, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(rule.value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(rule.type, color = Color(0xFF90A4AE), fontSize = 12.sp)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "Ta bort", tint = Color(0xFFFF5252))
            }
        }
    }
}

@Composable
private fun SettingsTab(onLock: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        SettingsItem("Byt PIN-kod", "Ändra den hemliga koden")
        Spacer(modifier = Modifier.height(12.dp))
        SettingsItem("Decoy-PIN", "Ändra den falska koden")
        Spacer(modifier = Modifier.height(12.dp))
        SettingsItem("Biometri", "Kräv fingeravtryck/ansikte")
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onLock,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Lås vaulten (Panic)", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SettingsItem(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF90A4AE), fontSize = 13.sp)
        }
    }
}

@Composable
private fun DecoyContent(onLock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, tint = Color(0xFFFFC107), modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Private Notes", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Inga anteckningar sparade ännu.", color = Color(0xFFB0BEC5), fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onLock,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF546E7A)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Stäng")
        }
    }
}
