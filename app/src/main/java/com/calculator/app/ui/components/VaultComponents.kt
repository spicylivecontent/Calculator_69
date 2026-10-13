package com.calculator.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.calculator.app.data.RecentPlayback
import com.calculator.app.data.model.Playability
import com.calculator.app.data.model.SearchResult
import com.calculator.app.ui.theme.VaultAccent
import com.calculator.app.ui.theme.VaultAmber
import com.calculator.app.ui.theme.VaultBlue
import com.calculator.app.ui.theme.VaultBorder
import com.calculator.app.ui.theme.VaultPink
import com.calculator.app.ui.theme.VaultSurface
import com.calculator.app.ui.theme.VaultSurfaceHigh
import com.calculator.app.ui.theme.VaultTextPrimary
import com.calculator.app.ui.theme.VaultTextSecondary

/** Mjuk platshållare som visas medan tumnageln laddas. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(VaultSurfaceHigh, Color(0xFF2B2B42), VaultSurfaceHigh),
                start = Offset(offset * 420f, 0f),
                end = Offset(offset * 420f + 420f, 260f)
            )
        )
    )
}

@Composable
fun Thumbnail(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    var state by remember(url) { mutableStateOf<AsyncImagePainter.State?>(null) }
    val loading = url.isNullOrBlank() || state !is AsyncImagePainter.State.Success

    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(VaultSurfaceHigh)) {
        if (url.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = VaultTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            return@Box
        }

        if (loading) {
            ShimmerBox(Modifier.fillMaxSize())
        }

        AsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            onState = { newState -> state = newState },
            modifier = Modifier.fillMaxSize()
        )

        if (state is AsyncImagePainter.State.Error) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = VaultTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Color.Black.copy(alpha = 0.68f),
    textColor: Color = Color.White,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun PlayabilityPill(playability: Playability, modifier: Modifier = Modifier) {
    when (playability) {
        Playability.PLAYABLE -> Pill(
            text = "Sidan svarar",
            modifier = modifier,
            background = Color(0xFF10331F),
            textColor = VaultAccent,
            icon = Icons.Default.CheckCircle
        )

        Playability.CHECKING -> Pill(
            text = "Läser info",
            modifier = modifier,
            background = Color(0xFF2A2A17),
            textColor = VaultAmber,
            icon = Icons.Default.HourglassEmpty
        )

        Playability.UNVERIFIED -> Pill(
            text = "Ej inläst",
            modifier = modifier,
            background = Color(0xFF2A2A17),
            textColor = VaultAmber
        )

        Playability.UNPLAYABLE -> Pill(
            text = "Borta",
            modifier = modifier,
            background = Color(0xFF33191B),
            textColor = Color(0xFFFF8A80),
            icon = Icons.Default.ErrorOutline
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = VaultTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, color = VaultTextSecondary, fontSize = 12.sp)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun VideoListCard(
    result: SearchResult,
    onClick: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onWatchLater: (() -> Unit)? = null,
    onNeverShow: (() -> Unit)? = null,
    onOpenOnSite: (() -> Unit)? = null,
    onSearchTag: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .height(80.dp)
            ) {
                Thumbnail(
                    url = result.thumbnailUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = result.title
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.12f))
                )
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(4.dp)
                )
                result.duration?.takeIf { it.isNotBlank() }?.let { duration ->
                    Pill(
                        text = duration,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    )
                }
                result.qualityLabel?.takeIf { it.isNotBlank() }?.let { quality ->
                    Pill(
                        text = quality,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        background = VaultBlue.copy(alpha = 0.85f),
                        textColor = Color.Black
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    color = VaultTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        text = result.siteLabel,
                        background = VaultSurfaceHigh,
                        textColor = VaultAccent
                    )
                    Spacer(Modifier.width(6.dp))
                    PlayabilityPill(result.playability)
                }
                Spacer(Modifier.height(4.dp))
                if (result.description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.description,
                        color = VaultTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (result.tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(result.tagsPreview(6), key = { it }) { tag ->
                            TagChip(text = tag, selected = false, onClick = { onSearchTag?.invoke(tag) })
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onFavorite != null) {
                        IconButton(onClick = onFavorite, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Favorite, contentDescription = "Favorit", tint = VaultPink, modifier = Modifier.size(17.dp))
                        }
                    }
                    if (onWatchLater != null) {
                        IconButton(onClick = onWatchLater, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.WatchLater, contentDescription = "Titta senare", tint = VaultBlue, modifier = Modifier.size(17.dp))
                        }
                    }
                    if (onOpenOnSite != null) {
                        IconButton(onClick = onOpenOnSite, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Public, contentDescription = "Öppna på sajten", tint = VaultAccent, modifier = Modifier.size(17.dp))
                        }
                    }
                    if (onNeverShow != null) {
                        IconButton(onClick = onNeverShow, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Block, contentDescription = "Visa aldrig igen", tint = VaultTextSecondary, modifier = Modifier.size(17.dp))
                        }
                    }
                    if (result.isEnriched && result.tagCount > 0) {
                        Spacer(Modifier.weight(1f))
                        StatBadge(text = "${result.tagCount} taggar", color = VaultTextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentVideoCard(
    entry: RecentPlayback,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(
                url = entry.thumbnailUrl,
                modifier = Modifier.width(120.dp).height(72.dp),
                contentDescription = entry.title
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    color = VaultTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { entry.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = VaultAccent,
                    trackColor = VaultSurfaceHigh
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(text = SearchResult.prettySiteName(entry.sourceSite), background = VaultSurfaceHigh, textColor = VaultAccent)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Fortsätt från ${entry.positionLabel}",
                        color = VaultTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = VaultTextSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = VaultTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            color = VaultTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.Button(
                onClick = onAction,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = VaultAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(actionLabel, color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun VaultCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VaultBorder)
    ) {
        Box(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun TagChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (selected) VaultAccent.copy(alpha = 0.16f) else VaultSurfaceHigh
    val borderColor = if (selected) VaultAccent else VaultBorder
    val textColor = if (selected) VaultAccent else VaultTextPrimary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun StatBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
