package com.bacuflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bacuflix.app.model.GenreCategories
import com.bacuflix.app.model.GenreCategory
import com.bacuflix.app.model.Movie
import com.bacuflix.app.ui.theme.FlixColors
import com.bacuflix.app.util.FormatTime

@Composable
fun MovieCarouselSection(
    title: String,
    movies: List<Movie>,
    favoriteKeys: Set<String>,
    isTop10: Boolean = false,
    onSelectMovie: (Movie) -> Unit,
    onToggleFavorite: (Movie) -> Unit,
    onViewAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) return
    val displayList = remember(movies, isTop10) {
        movies.take(if (isTop10) 10 else 14)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = onViewAll != null) { onViewAll?.invoke() }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = FlixColors.RedBright,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (onViewAll != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onViewAll() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Xem tất cả",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlixColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = FlixColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(displayList, key = { idx, m -> "${m.effectiveKey}_$idx" }) { idx, movie ->
                CarouselPosterCard(
                    movie = movie,
                    isFavorite = favoriteKeys.contains(movie.effectiveKey),
                    isTop10 = isTop10,
                    rank = idx + 1,
                    onClick = { onSelectMovie(movie) },
                    onToggleFavorite = { onToggleFavorite(movie) }
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingSection(
    historyItems: List<Movie>,
    onResumeItem: (Movie) -> Unit,
    onRemoveItem: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    if (historyItems.isEmpty()) return
    val displayItems = remember(historyItems) { historyItems.take(10) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tiếp tục xem",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = FlixColors.RedBright,
                modifier = Modifier.size(22.dp)
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayItems, key = { it.effectiveKey }) { item ->
                ContinueWatchingCard(
                    item = item,
                    onResume = { onResumeItem(item) },
                    onRemove = { onRemoveItem(item) }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: Movie,
    onResume: () -> Unit,
    onRemove: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val progressPct = item.progressPercent.coerceIn(5, 98) / 100f

    Column(
        modifier = Modifier
            .width(230.dp)
            .clip(shape)
            .background(FlixColors.BgCard)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else FlixColors.Border,
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onResume)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AsyncImagePoster(
                url = item.thumb.ifBlank { item.poster },
                alternateUrl = item.poster,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x5508090D))
            )

            // Center Play Badge
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xCCE50920)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Top-left Episode badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC14151B))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = item.episodeName.ifBlank { "Tập 1" },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }

            // Top-right Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA08090D))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Xóa khỏi lịch sử xem",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Bottom Progress Track
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x55FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressPct)
                        .fillMaxHeight()
                        .background(FlixColors.RedBright)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.5.sp
            )
            val timeLabel = if (item.currentTime > 5) {
                "Xem tiếp ${FormatTime.formatTime(item.currentTime)} • ${item.remainingText.ifBlank { "Đang xem" }}"
            } else {
                item.remainingText.ifBlank { "Đang xem" }
            }
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = FlixColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.5.sp
            )
        }
    }
}

@Composable
fun GenreBrowsingSection(
    backdropMovies: List<Movie>,
    onSelectGenre: (GenreCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Thể loại",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = FlixColors.RedBright,
                modifier = Modifier.size(22.dp)
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(GenreCategories.ALL, key = { _, g -> g.slug }) { idx, genre ->
                val backdropUrl = if (backdropMovies.isNotEmpty()) {
                    val m = backdropMovies[(idx * 2) % backdropMovies.size]
                    m.thumb.ifBlank { m.poster }
                } else "icon.png"

                var isFocused by remember { mutableStateOf(false) }
                val accentColor = Color(genre.accentHex)
                val shape = RoundedCornerShape(14.dp)

                Box(
                    modifier = Modifier
                        .width(190.dp)
                        .height(104.dp)
                        .clip(shape)
                        .background(FlixColors.BgCard)
                        .border(
                            width = if (isFocused) 2.5.dp else 1.dp,
                            color = if (isFocused) Color.White else accentColor.copy(alpha = 0.45f),
                            shape = shape
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickable { onSelectGenre(genre) }
                ) {
                    AsyncImagePoster(
                        url = backdropUrl,
                        contentDescription = genre.name,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.72f),
                                        Color(0xDD08090D)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = genre.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = genre.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE4E4E7),
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}
