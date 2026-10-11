package com.calculator.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calculator.app.data.RecentPlayback
import com.calculator.app.data.entity.DislikeRuleEntity
import com.calculator.app.data.entity.SearchPresetEntity
import com.calculator.app.data.entity.VideoEntity
import com.calculator.app.data.model.SearchResult
import com.calculator.app.ui.components.RecentVideoCard
import com.calculator.app.ui.components.SectionHeader
import com.calculator.app.ui.components.Thumbnail
import com.calculator.app.ui.theme.VaultAccent
import com.calculator.app.ui.theme.VaultBorder
import com.calculator.app.ui.theme.VaultPink
import com.calculator.app.ui.theme.VaultSurface
import com.calculator.app.ui.theme.VaultTextPrimary
import com.calculator.app.ui.theme.VaultTextSecondary

enum class LibrarySection { OVERVIEW, FAVORITES, WATCH_LATER, HISTORY, DISLIKES, SEARCH_PRESETS }

@Composable
fun LibraryScreen(
    favoriteCount: Int,
    watchLaterCount: Int,
    dislikeCount: Int,
    presetCount: Int,
    favorites: List<VideoEntity>,
    watchLater: List<VideoEntity>,
    rules: List<DislikeRuleEntity>,
    presets: List<SearchPresetEntity>,
    history: List<RecentPlayback>,
    onResume: (RecentPlayback) -> Unit,
    onClearHistory: () -> Unit,
    onUsePreset: (SearchPresetEntity) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onRemoveWatchLater: (String) -> Unit,
    onRemoveDislikeRule: (DislikeRuleEntity) -> Unit,
    onRemovePreset: (SearchPresetEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var section by remember { mutableStateOf(LibrarySection.OVERVIEW) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Bibliotek", color = VaultTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (section == LibrarySection.OVERVIEW) {
                        "Favoriter, watch later, historik och regler"
                    } else {
                        "Tillbaka till översikten"
                    },
                    color = VaultTextSecondary,
                    fontSize = 12.sp
                )
            }
            if (section != LibrarySection.OVERVIEW) {
                TextButton(onClick = { section = LibrarySection.OVERVIEW }) {
                    Text("← Översikt", color = VaultAccent)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (section) {
                LibrarySection.OVERVIEW -> {
                    item {
                        LibraryOverviewItem("Fortsätt titta", "${history.size} videor", VaultAccent, Icons.Default.History) {
                            section = LibrarySection.HISTORY
                        }
                    }
                    item {
                        LibraryOverviewItem("Favoriter", "$favoriteCount videor", VaultPink, Icons.Default.Favorite) {
                            section = LibrarySection.FAVORITES
                        }
                    }
                    item {
                        LibraryOverviewItem("Watch Later", "$watchLaterCount videor", Color(0xFF64B5F6), Icons.Default.WatchLater) {
                            section = LibrarySection.WATCH_LATER
                        }
                    }
                    item {
                        LibraryOverviewItem("Sparade sökningar", "$presetCount sökningar", Color(0xFF80CBC4), Icons.Default.Search) {
                            section = LibrarySection.SEARCH_PRESETS
                        }
                    }
                    item {
                        LibraryOverviewItem("Never Show Again", "$dislikeCount regler", Color(0xFFFF5A5A), Icons.Default.Block) {
                            section = LibrarySection.DISLIKES
                        }
                    }
                    item {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            SectionHeader(
                                title = "Streamcache",
                                subtitle = "Verifierade strömmar cachas i 20 minuter för snabb uppspelning"
                            )
                        }
                    }
                }

                LibrarySection.HISTORY -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Nyligen spelat", color = VaultTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            if (history.isNotEmpty()) {
                                TextButton(onClick = onClearHistory) {
                                    Text("Rensa", color = Color(0xFFFF8A80), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    if (history.isEmpty()) {
                        item { EmptyRow("Inget spelat ännu") }
                    } else {
                        items(history, key = { it.id }) { entry ->
                            RecentVideoCard(entry = entry, onClick = { onResume(entry) })
                        }
                    }
                }

                LibrarySection.FAVORITES -> {
                    if (favorites.isEmpty()) {
                        item { EmptyRow("Inga favoriter ännu") }
                    } else {
                        items(favorites, key = { it.id }) { video ->
                            VideoEntityCard(video) { onRemoveFavorite(video.id) }
                        }
                    }
                }

                LibrarySection.WATCH_LATER -> {
                    if (watchLater.isEmpty()) {
                        item { EmptyRow("Inget i Watch Later") }
                    } else {
                        items(watchLater, key = { it.id }) { video ->
                            VideoEntityCard(video) { onRemoveWatchLater(video.id) }
                        }
                    }
                }

                LibrarySection.DISLIKES -> {
                    if (rules.isEmpty()) {
                        item { EmptyRow("Inga regler ännu") }
                    } else {
                        items(rules, key = { "${it.type}_${it.value}" }) { rule ->
                            DislikeRuleCard(rule) { onRemoveDislikeRule(rule) }
                        }
                    }
                }

                LibrarySection.SEARCH_PRESETS -> {
                    if (presets.isEmpty()) {
                        item { EmptyRow("Inga sparade sökningar ännu") }
                    } else {
                        items(presets, key = { it.id }) { preset ->
                            SearchPresetCard(
                                preset = preset,
                                onUse = { onUsePreset(preset) },
                                onRemove = { onRemovePreset(preset) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(text, color = VaultTextSecondary, fontSize = 13.sp)
}

@Composable
private fun LibraryOverviewItem(
    title: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.12f))),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = VaultTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = VaultTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun VideoEntityCard(video: VideoEntity, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(
                url = video.thumbnailUrl,
                modifier = Modifier.width(110.dp).height(66.dp),
                contentDescription = video.title
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = VaultTextPrimary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    SearchResult.prettySiteName(video.sourceSite),
                    color = VaultAccent,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Ta bort", tint = Color(0xFFFF5A5A))
            }
        }
    }
}

@Composable
private fun SearchPresetCard(
    preset: SearchPresetEntity,
    onUse: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, color = VaultTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    preset.query,
                    color = VaultTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (preset.negativeKeywords.isNotBlank()) {
                    Text(
                        "Exkludera: ${preset.negativeKeywords}",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onUse) { Text("Använd sökning", color = VaultAccent) }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Ta bort", tint = Color(0xFFFF5A5A))
            }
        }
    }
}

@Composable
private fun DislikeRuleCard(rule: DislikeRuleEntity, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(rule.value, color = VaultTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(rule.type, color = VaultTextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Ta bort", tint = Color(0xFFFF5A5A))
            }
        }
    }
}
