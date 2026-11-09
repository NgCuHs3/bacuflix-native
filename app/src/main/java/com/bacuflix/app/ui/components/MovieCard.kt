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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.bacuflix.app.download.DownloadItem
import com.bacuflix.app.download.DownloadStatus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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

            // Top-right Save / Bookmark button
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
                    imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isFavorite) "Bỏ lưu phim" else "Lưu phim",
                    tint = if (isFavorite) FlixColors.RedBright else Color.White,
                    modifier = Modifier.size(17.dp)
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
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    // Apple TV "lift" effect: focused poster scales up with soft shadow, pressed sinks slightly
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isFocused -> 1.08f
            else -> 1f
        },
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "posterScale"
    )

    Box(
        modifier = modifier
            .width(if (isTop10) 156.dp else 142.dp)
            .aspectRatio(2f / 3f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isFocused) 18.dp else 4.dp,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(shape)
            .background(FlixColors.BgCard)
            .then(
                if (isFocused) Modifier.border(2.dp, Color(0xE6FFFFFF), shape) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
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

        // Save / Bookmark button
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
                imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = if (isFavorite) "Bỏ lưu phim" else "Lưu phim",
                tint = if (isFavorite) FlixColors.RedBright else Color.White,
                modifier = Modifier.size(16.dp)
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

@Composable
fun DownloadedMovieCard(
    item: DownloadItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val isDone = item.status == DownloadStatus.COMPLETED

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
            .clickable(enabled = isDone, onClick = onPlay)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            AsyncImagePoster(
                url = item.posterUrl,
                alternateUrl = item.thumbUrl,
                contentDescription = item.movieTitle,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient shade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x5508090D),
                                Color(0xEE08090D)
                            )
                        )
                    )
            )

            // Top-left MP4 Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isDone) Color(0xFF10B981) else FlixColors.Red)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isDone) "MP4" else "${item.progressPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Top-right Delete or Cancel button
            IconButton(
                onClick = { if (isDone) onDelete() else onCancel() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x9908090D))
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Default.Delete else Icons.Default.Close,
                    contentDescription = if (isDone) "Xóa file MP4" else "Hủy tải",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Center Play Icon if completed
            if (isDone) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0x8C000000))
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Phát offline",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                // Downloading progress indicator
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { item.progressPercent / 100f },
                        modifier = Modifier.size(38.dp),
                        color = FlixColors.RedBright,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${item.progressPercent}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Info
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = item.movieTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subText = when {
                isDone -> {
                    val sizeStr = FormatTime.formatBytes(item.downloadedBytes)
                    val ep = if (item.episodeName.isNotBlank()) "${item.episodeName} • " else ""
                    "${ep}$sizeStr • Xem offline"
                }
                item.status == DownloadStatus.REMUXING -> "Đang lưu file MP4..."
                else -> {
                    val speed = if (item.speedText.isNotBlank()) " • ${item.speedText}" else ""
                    "${FormatTime.formatBytes(item.downloadedBytes)}$speed"
                }
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDone) Color(0xFF10B981) else FlixColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
