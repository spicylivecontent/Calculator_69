package com.calculator.app.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calculator.app.data.RecentPlayback
import com.calculator.app.data.model.SearchResult
import com.calculator.app.data.taxonomy.Category
import com.calculator.app.data.taxonomy.SearchTaxonomy
import com.calculator.app.ui.components.EmptyState
import com.calculator.app.ui.components.RecentVideoCard
import com.calculator.app.ui.components.SectionHeader
import com.calculator.app.ui.components.VideoListCard
import com.calculator.app.ui.theme.VaultAccent
import com.calculator.app.ui.theme.VaultBorder
import com.calculator.app.ui.theme.VaultSurface
import com.calculator.app.ui.theme.VaultSurfaceHigh
import com.calculator.app.ui.theme.VaultTextPrimary
import com.calculator.app.ui.theme.VaultTextSecondary

/**
 * Startsidan: fortsätt titta, snabbval av kategorier och de senaste spelbara träffarna.
 */
@Composable
fun HomeScreen(
    continueWatching: List<RecentPlayback>,
    feed: List<SearchResult>,
    isLoading: Boolean,
    resolvingTitle: String,
    onResume: (RecentPlayback) -> Unit,
    onPlay: (SearchResult) -> Unit,
    onFavorite: (SearchResult) -> Unit,
    onWatchLater: (SearchResult) -> Unit,
    onQuickCategory: (List<String>) -> Unit,
    onOpenSearch: (String?) -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Discover", color = VaultTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Tryck på en kategori eller sök – allt som visas går att spela",
                        color = VaultTextSecondary,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onLock) {
                    Icon(Icons.Default.Lock, contentDescription = "Lås", tint = Color(0xFFFF5A5A))
                }
            }
        }

        if (isLoading || resolvingTitle.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VaultSurface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = VaultAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (resolvingTitle.isNotBlank()) "Hämtar stream för “$resolvingTitle”…" else "Söker…",
                        color = VaultTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Fortsätt titta",
                    subtitle = "${continueWatching.size} påbörjade"
                )
            }
            items(continueWatching, key = { "resume_${it.id}" }) { entry ->
                RecentVideoCard(
                    entry = entry,
                    onClick = { onResume(entry) }
                )
            }
        }

        item { SectionHeader(title = "Snabbsök", subtitle = "Populära kategorier") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(QUICK_PICKS) { pick ->
                    QuickCategoryCard(
                        label = pick.first,
                        caption = pick.second,
                        onClick = { onQuickCategory(pick.third) }
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "Bläddra",
                subtitle = "${SearchTaxonomy.categories.size} kategorier · ${SearchTaxonomy.allSubcategories.size} underkategorier"
            )
        }
        items(SearchTaxonomy.categories, key = { it.id }) { category ->
            CategoryCard(category = category, onOpen = { onOpenSearch(category.id) })
        }

        if (feed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Senast hittade",
                    subtitle = "Verifierade spelbara träffar"
                )
            }
            items(feed, key = { "feed_${it.id}" }) { result ->
                VideoListCard(
                    result = result,
                    onClick = { onPlay(result) },
                    onFavorite = { onFavorite(result) },
                    onWatchLater = { onWatchLater(result) }
                )
            }
        } else if (!isLoading && continueWatching.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Movie,
                    title = "Inget sparat ännu",
                    message = "Välj en kategori eller gör en sökning. Resultaten kontrolleras så att bara videor som går att spela visas.",
                    actionLabel = "Öppna sökningen",
                    onAction = { onOpenSearch(null) }
                )
            }
        }
    }
}

@Composable
private fun QuickCategoryCard(
    label: String,
    caption: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(VaultSurfaceHigh, VaultSurface)))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(Icons.Default.Bolt, contentDescription = null, tint = VaultAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text(label, color = VaultTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(caption, color = VaultTextSecondary, fontSize = 11.sp, maxLines = 2)
    }
}

@Composable
private fun CategoryCard(category: Category, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VaultSurface)
            .clickable { onOpen(category.id) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VaultSurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(category.emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(category.label, color = VaultTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = category.description + " · " + category.subcategories.take(3).joinToString(", ") { it.label },
                color = VaultTextSecondary,
                fontSize = 11.sp,
                maxLines = 2
            )
        }
        Icon(Icons.Default.Search, contentDescription = null, tint = VaultTextSecondary, modifier = Modifier.size(18.dp))
    }
}

private val QUICK_PICKS: List<Triple<String, String, List<String>>> = listOf(
    Triple("Blond", "Klassisk favorit", listOf("blonde")),
    Triple("Kök", "Vardaglig miljö", listOf("kitchen")),
    Triple("Amatör", "Verkligt och naket", listOf("amateur")),
    Triple("Massage", "Lugnt tempo", listOf("massage")),
    Triple("Utomhus", "Frisk luft", listOf("outdoor")),
    Triple("Lesbisk", "Två tjejer", listOf("lesbian")),
    Triple("POV", "Första person", listOf("pov")),
    Triple("4K", "Bästa bildkvalitet", listOf("4k"))
)
