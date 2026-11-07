package com.bacuflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import com.bacuflix.app.model.Movie
import com.bacuflix.app.ui.theme.FlixColors
import com.bacuflix.app.util.FormatTime

@Composable
fun CatalogMovieCard(
    movie: Movie,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(FlixColors.BgCard)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else FlixColors.Border,
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            AsyncImagePoster(
                url = movie.poster,
                alternateUrl = movie.thumb,
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom gradient shade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xCC08090D)
                            )
                        )
                    )
            )

            // Top-left Quality + Episode badges
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val qualityText = movie.quality.ifBlank { "FHD" }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(FlixColors.Red)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = qualityText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }

                val secondaryBadge = movie.episodeCurrent.ifBlank { movie.lang }
                if (secondaryBadge.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xB314151B))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = secondaryBadge,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Top-right Favorite button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0x9908090D))
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Bỏ yêu thích" else "Thêm yêu thích",
                    tint = if (isFavorite) FlixColors.RedBright else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.5.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            val metaParts = buildList {
                if (movie.currentTime > 5) {
                    add("Xem tiếp ${FormatTime.formatTime(movie.currentTime)}")
                } else if (movie.mlMatchPercent != null) {
                    add("${movie.mlMatchPercent}% phù hợp")
                }
                if (movie.episodeCurrent.isNotBlank()) add(movie.episodeCurrent)
                if (movie.year.isNotBlank()) add(movie.year)
                if (isEmpty()) {
                    add(movie.lang.ifBlank { movie.source.ifBlank { "Tập mới" } })
                }
            }

            Text(
                text = metaParts.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (movie.mlMatchPercent != null) FlixColors.MatchGreen else FlixColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (movie.playbackEnabled) FlixColors.Red else FlixColors.BgElevated)
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (movie.playbackEnabled) "Xem ngay" else "Unavailable",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CarouselPosterCard(
    movie: Movie,
    isFavorite: Boolean,
    isTop10: Boolean = false,
    rank: Int = 0,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .width(if (isTop10) 156.dp else 142.dp)
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(FlixColors.BgCard)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else FlixColors.Border,
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        AsyncImagePoster(
            url = movie.poster,
            alternateUrl = movie.thumb,
            contentDescription = movie.title,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x3308090D),
                            Color(0xEE08090D)
                        )
                    )
                )
        )

        // Quality badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(FlixColors.Red)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = movie.quality.ifBlank { "FHD" },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontSize = 10.sp
            )
        }

        // Favorite Heart
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0x9908090D))
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Bỏ yêu thích" else "Thêm yêu thích",
                tint = if (isFavorite) FlixColors.RedBright else Color.White,
                modifier = Modifier.size(15.dp)
            )
        }

        // Bottom overlay info (with optional Top 10 rank number)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (isTop10 && rank > 0) {
                Text(
                    text = rank.toString(),
                    color = FlixColors.RedBright,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp
                )
                val metaText = when {
                    movie.mlMatchPercent != null -> {
                        val tail = movie.episodeCurrent.ifBlank { movie.year.ifBlank { movie.lang.ifBlank { "Đề xuất" } } }
                        "${movie.mlMatchPercent}% phù hợp • $tail"
                    }
                    movie.episodeCurrent.isNotBlank() -> {
                        movie.episodeCurrent + if (movie.year.isNotBlank()) " • ${movie.year}" else ""
                    }
                    movie.year.isNotBlank() -> {
                        movie.year + if (movie.lang.isNotBlank()) " • ${movie.lang}" else ""
                    }
                    else -> movie.lang.ifBlank { "Vietsub" }
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (movie.mlMatchPercent != null) FlixColors.MatchGreen else FlixColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp
                )
            }
        }
    }
}
