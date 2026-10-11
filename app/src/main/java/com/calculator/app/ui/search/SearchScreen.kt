package com.calculator.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calculator.app.data.model.LengthFilter
import com.calculator.app.data.model.SortOrder
import com.calculator.app.data.taxonomy.SearchTaxonomy
import com.calculator.app.ui.components.EmptyState
import com.calculator.app.ui.components.SectionHeader
import com.calculator.app.ui.components.TagChip
import com.calculator.app.ui.components.VideoListCard
import com.calculator.app.ui.theme.VaultAccent
import com.calculator.app.ui.theme.VaultAmber
import com.calculator.app.ui.theme.VaultBorder
import com.calculator.app.ui.theme.VaultSurface
import com.calculator.app.ui.theme.VaultSurfaceHigh
import com.calculator.app.ui.theme.VaultTextPrimary
import com.calculator.app.ui.theme.VaultTextSecondary

/**
 * Sökskärmen: fritextsökning, kategorier med underkategorier, sajtval, längd, sortering
 * och resultat som kontrolleras så att de går att spela.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
    requestedCategoryId: String? = null,
    onCategoryRequestHandled: () -> Unit = {}
) {
    var showCategories by remember { mutableStateOf(false) }
    var openCategoryId by remember { mutableStateOf(SearchTaxonomy.categories.first().id) }
    var showSitesMenu by remember { mutableStateOf(false) }
    var showLengthMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showExclusions by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var presetError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(requestedCategoryId) {
        if (requestedCategoryId != null) {
            openCategoryId = requestedCategoryId
            showCategories = true
            onCategoryRequestHandled()
        }
    }

    val activeSubcategories = viewModel.activeSubcategories

    Column(modifier = modifier.fillMaxSize()) {
        // --- Sökfält ---
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Sök: blond, kök, massage …", color = VaultTextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VaultTextSecondary) },
                    trailingIcon = {
                        if (viewModel.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Rensa", tint = VaultTextSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultAccent,
                        unfocusedBorderColor = VaultBorder,
                        focusedTextColor = VaultTextPrimary,
                        unfocusedTextColor = VaultTextPrimary,
                        cursorColor = VaultAccent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        viewModel.search()
                    },
                    enabled = !viewModel.isSearching,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    if (viewModel.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text("Sök", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // --- Filterrad ---
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    TagChip(
                        text = if (activeSubcategories.isEmpty()) "Kategorier" else "Kategorier (${activeSubcategories.size})",
                        selected = showCategories || activeSubcategories.isNotEmpty(),
                        onClick = { showCategories = !showCategories }
                    )
                }
                item {
                    Box {
                        TagChip(
                            text = if (viewModel.selectedSiteIds.isEmpty()) "Alla sajter" else "Sajter (${viewModel.selectedSiteIds.size})",
                            selected = viewModel.selectedSiteIds.isNotEmpty(),
                            onClick = { showSitesMenu = true }
                        )
                        DropdownMenu(expanded = showSitesMenu, onDismissRequest = { showSitesMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Alla sajter (${viewModel.siteInfos.size})") },
                                onClick = {
                                    viewModel.selectAllSites()
                                    showSitesMenu = false
                                },
                                trailingIcon = {
                                    if (viewModel.selectedSiteIds.isEmpty()) Icon(Icons.Default.Check, contentDescription = null)
                                }
                            )
                            viewModel.siteInfos.forEach { site ->
                                DropdownMenuItem(
                                    text = { Text(site.label) },
                                    onClick = { viewModel.toggleSite(site.id) },
                                    trailingIcon = {
                                        if (site.id in viewModel.selectedSiteIds) Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    Box {
                        TagChip(
                            text = viewModel.lengthFilter.label,
                            selected = viewModel.lengthFilter != LengthFilter.ALL,
                            onClick = { showLengthMenu = true }
                        )
                        DropdownMenu(expanded = showLengthMenu, onDismissRequest = { showLengthMenu = false }) {
                            LengthFilter.entries.forEach { filter ->
                                DropdownMenuItem(
                                    text = { Text(filter.label) },
                                    onClick = {
                                        viewModel.onLengthFilterChange(filter)
                                        showLengthMenu = false
                                    },
                                    trailingIcon = {
                                        if (filter == viewModel.lengthFilter) Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    Box {
                        TagChip(
                            text = "Sortera: ${viewModel.sortOrder.label}",
                            selected = viewModel.sortOrder != SortOrder.RELEVANCE,
                            onClick = { showSortMenu = true }
                        )
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = {
                                        viewModel.onSortOrderChange(order)
                                        showSortMenu = false
                                    },
                                    trailingIcon = {
                                        if (order == viewModel.sortOrder) Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    TagChip(
                        text = "Bara spelbara",
                        selected = viewModel.onlyPlayable,
                        onClick = { viewModel.onOnlyPlayableChange(!viewModel.onlyPlayable) }
                    )
                }
                item {
                    TagChip(
                        text = "Exkludera",
                        selected = showExclusions || viewModel.negativeKeywords.isNotBlank(),
                        onClick = { showExclusions = !showExclusions }
                    )
                }
                item {
                    TagChip(
                        text = "Spara sökning",
                        selected = false,
                        onClick = {
                            presetName = viewModel.query.trim().take(60)
                            presetError = null
                            showSaveDialog = true
                        }
                    )
                }
            }

            // --- Kategoripanel ---
            if (showCategories) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VaultSurface)
                        .padding(vertical = 10.dp)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SearchTaxonomy.categories, key = { it.id }) { category ->
                            TagChip(
                                text = "${category.emoji} ${category.label}",
                                selected = category.id == openCategoryId,
                                onClick = { openCategoryId = category.id }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = SearchTaxonomy.category(openCategoryId)?.description.orEmpty(),
                        color = VaultTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchTaxonomy.subcategoriesFor(openCategoryId).forEach { subcategory ->
                            TagChip(
                                text = subcategory.label,
                                selected = subcategory.id in viewModel.selectedSubcategoryIds,
                                onClick = { viewModel.onSubcategoryToggle(subcategory.id) }
                            )
                        }
                    }
                    if (activeSubcategories.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Valt: " + activeSubcategories.joinToString(" · ") { it.label },
                                color = VaultAccent,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearCategories() }) {
                                Text("Rensa", color = VaultTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (showExclusions) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = viewModel.negativeKeywords,
                    onValueChange = { viewModel.onNegativeKeywordsChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Exkludera ord, separera med komma", color = VaultTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF5A5A),
                        unfocusedBorderColor = VaultBorder,
                        focusedTextColor = VaultTextPrimary,
                        unfocusedTextColor = VaultTextPrimary,
                        cursorColor = Color(0xFFFF5A5A)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }
        }

        // --- Resultat ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (viewModel.isSearching) {
                item {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = VaultAccent)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Söker med ${viewModel.activeQueries.size} frågor på " +
                                if (viewModel.selectedSiteIds.isEmpty()) "alla sajter" else "${viewModel.selectedSiteIds.size} sajter" +
                                "…",
                            color = VaultTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (viewModel.isVerifyingResults) {
                item {
                    val total = viewModel.verificationTotal.coerceAtLeast(1)
                    val progress = viewModel.verificationDone.toFloat() / total.toFloat()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(VaultSurface)
                            .padding(14.dp)
                    ) {
                        Text(
                            "Kontrollerar att videorna går att spela (${viewModel.verificationDone}/${viewModel.verificationTotal})",
                            color = VaultTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = VaultAccent,
                            trackColor = VaultSurfaceHigh
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${viewModel.verifiedCount} spelbara så långt",
                            color = VaultAccent,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (viewModel.siteReports.isNotEmpty() && !viewModel.isSearching) {
                item {
                    Column {
                        Text("Sajtstatus", color = VaultTextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(viewModel.siteReports, key = { it.siteId }) { report ->
                                TagChip(
                                    text = if (report.count > 0) "${report.label} ${report.count}" else "${report.label} –",
                                    selected = false,
                                    onClick = { viewModel.toggleSite(report.siteId) }
                                )
                            }
                        }
                    }
                }
            }

            viewModel.explanation?.let { text ->
                item {
                    Text(text, color = VaultTextSecondary, fontSize = 11.sp)
                }
            }

            viewModel.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A1618))
                            .padding(12.dp)
                    )
                }
            }

            viewModel.noticeMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = VaultAmber,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A2616))
                            .padding(12.dp)
                    )
                }
            }

            if (viewModel.results.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = if (viewModel.isVerifyingResults) {
                            "${viewModel.verifiedCount} spelbara · ${(viewModel.verificationTotal - viewModel.verificationDone).coerceAtLeast(0)} kvar att kontrollera"
                        } else {
                            "${viewModel.verifiedCount} spelbara träffar"
                        },
                        subtitle = if (viewModel.activeQueries.isEmpty()) null else viewModel.activeQueries.joinToString(" · ") { "“$it”" }
                    )
                }
                items(viewModel.results, key = { it.id }) { result ->
                    VideoListCard(
                        result = result,
                        onClick = { viewModel.playVideo(result) },
                        onFavorite = { viewModel.addFavorite(result) },
                        onWatchLater = { viewModel.addWatchLater(result) },
                        onNeverShow = { viewModel.addNeverShowAgain(neverShowKeyword(result.title)) }
                    )
                }
                if (viewModel.canLoadMore) {
                    item {
                        Button(
                            onClick = { viewModel.loadMore() },
                            enabled = !viewModel.isLoadingMore,
                            colors = ButtonDefaults.buttonColors(containerColor = VaultSurfaceHigh),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (viewModel.isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = VaultAccent, strokeWidth = 2.dp)
                            } else {
                                Text("Ladda fler träffar", color = VaultTextPrimary)
                            }
                        }
                    }
                }
            } else if (!viewModel.isSearching && viewModel.query.isBlank() && activeSubcategories.isEmpty()) {
                item {
                    if (viewModel.searchHistory.isNotEmpty()) {
                        Column {
                            SectionHeader(title = "Senaste sökningar")
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                viewModel.searchHistory.forEach { term ->
                                    TagChip(
                                        text = term,
                                        selected = false,
                                        onClick = {
                                            viewModel.useHistoryTerm(term)
                                            viewModel.search()
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { viewModel.clearSearchHistory() }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = VaultTextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Rensa historiken", color = VaultTextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        EmptyState(
                            icon = Icons.Default.Search,
                            title = "Sök eller välj kategori",
                            message = "Skriv vad du vill se, eller öppna kategorierna och kombinera underkategorier. " +
                                "Appen söker på alla valda sajter och visar bara videor som går att spela."
                        )
                    }
                }
            } else if (!viewModel.isSearching && viewModel.results.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Movie,
                        title = "Inga spelbara träffar",
                        message = "Prova andra ord, välj fler sajter eller ladda fler träffar. " +
                            "Vi visar bara videor där strömmen svarat – stäng av \"Bara spelbara\" om du vill se okontrollerade träffar.",
                        actionLabel = if (viewModel.canLoadMore) "Ladda fler" else null,
                        onAction = if (viewModel.canLoadMore) ({ viewModel.loadMore() }) else null
                    )
                }
            }

            if (viewModel.isResolvingStream) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = VaultAccent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Hämtar stream för “${viewModel.resolvingTitle}”…", color = VaultTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Spara sökning") },
            text = {
                Column {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it.take(60) },
                        label = { Text("Namn") },
                        singleLine = true
                    )
                    presetError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        presetName.isBlank() -> presetError = "Ange ett namn."
                        viewModel.saveSearchPreset(presetName) -> {
                            showSaveDialog = false
                            presetName = ""
                        }

                        else -> presetError = viewModel.errorMessage ?: "Sökningen kunde inte sparas."
                    }
                }) { Text("Spara") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Avbryt") }
            }
        )
    }
}

private fun neverShowKeyword(title: String): String =
    title.split(' ')
        .map { it.trim().trim(',', '.', '-', '|') }
        .firstOrNull { it.length > 4 }
        ?: title.take(12)
