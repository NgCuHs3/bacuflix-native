package com.bacuflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bacuflix.app.model.Movie
import com.bacuflix.app.ui.theme.FlixColors
import com.bacuflix.app.util.UrlUtils
import kotlinx.coroutines.delay

/**
 * Hero Banner Carousel ported from js/home/hero-carousel.js and css/home.css.
 */
@Composable
fun HeroCarousel(
    movies: List<Movie>,
    favoriteKeys: Set<String>,
    descriptionsMap: Map<String, String>,
    onPlayMovie: (Movie) -> Unit,
    onOpenDetail: (Movie) -> Unit,
    onToggleFavorite: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val heroMovies = remember(movies) { movies.take(5) }
    if (heroMovies.isEmpty()) return

    var currentIndex by remember(heroMovies) { mutableIntStateOf(0) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(heroMovies.size, currentIndex) {
        if (heroMovies.size > 1) {
            delay(7000L)
            currentIndex = (currentIndex + 1) % heroMovies.size
        }
    }

    val safeIndex = currentIndex.coerceIn(0, heroMovies.lastIndex)
    val activeMovie = heroMovies[safeIndex]
    val isFav = favoriteKeys.contains(activeMovie.effectiveKey)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FlixColors.BgSecondary)
            .border(1.dp, FlixColors.Border, RoundedCornerShape(18.dp))
            .pointerInput(heroMovies.size) {
                if (heroMovies.size > 1) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragAccumulator += dragAmount
                        },
                        onDragEnd = {
                            if (dragAccumulator > 70f) {
                                currentIndex = (currentIndex - 1 + heroMovies.size) % heroMovies.size
                            } else if (dragAccumulator < -70f) {
                                currentIndex = (currentIndex + 1) % heroMovies.size
                            }
                            dragAccumulator = 0f
                        }
                    )
                }
            }
    ) {
        val isCompact = maxWidth < 600.dp
        val heroHeight = if (isCompact) 390.dp else 410.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
        ) {
            // Backdrop image (uses poster on portrait mobile if thumb is missing, cropped cleanly)
            AsyncImagePoster(
                url = activeMovie.thumb.ifBlank { activeMovie.poster },
                alternateUrl = activeMovie.poster,
                contentDescription = activeMovie.title,
                contentScale = ContentScale.Crop,
                maxDimension = 1280,
                modifier = Modifier.fillMaxSize()
            )

            // Cinema vertical & horizontal gradients matching css/home.css
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color(0x5508090D),
                                0.30f to Color(0x2608090D),
                                0.68f to Color(0xD908090D),
                                1.0f to FlixColors.BgPrimary
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xCC08090D),
                                Color(0x5908090D),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Top-right Slide Indicator Dots (always clean and never crowds bottom content)
            if (heroMovies.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x8C08090D))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    heroMovies.indices.forEach { idx ->
                        val selected = idx == safeIndex
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (selected) 18.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (selected) FlixColors.Red else Color(0x66FFFFFF))
                                .clickable { currentIndex = idx }
                        )
                    }
                }
            }

            // Bottom Content Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = if (isCompact) 16.dp else 24.dp,
                        end = if (isCompact) 16.dp else 24.dp,
                        top = 16.dp,
                        bottom = if (isCompact) 18.dp else 22.dp
                    )
            ) {
                // Quality + Episode Badge Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(FlixColors.Red)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = activeMovie.quality.ifBlank { "FHD" },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            maxLines = 1
                        )
                    }

                    if (activeMovie.episodeCurrent.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xB314151B))
                                .border(1.dp, FlixColors.Border, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = activeMovie.episodeCurrent,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Movie Title
                Text(
                    text = activeMovie.title,
                    color = Color.White,
                    fontSize = if (isCompact) 23.sp else 28.sp,
                    lineHeight = if (isCompact) 28.sp else 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onOpenDetail(activeMovie) }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Single-line Metadata Row (never wraps into vertical columns on narrow screens)
                val metaItems = remember(activeMovie) { formatHeroMetadata(activeMovie) }
                val metaAnnotated = remember(metaItems) {
                    buildAnnotatedString {
                        metaItems.forEachIndexed { idx, part ->
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFFE4E4E7),
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append(part)
                            }
                            if (idx < metaItems.lastIndex) {
                                withStyle(
                                    SpanStyle(
                                        color = FlixColors.RedBright,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append("  •  ")
                                }
                            }
                        }
                    }
                }

                Text(
                    text = metaAnnotated,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Synopsis Description
                val description = remember(activeMovie, descriptionsMap) {
                    val enriched = descriptionsMap[activeMovie.effectiveKey]
                    val rawContent = UrlUtils.stripHtmlTags(activeMovie.content)
                    when {
                        !enriched.isNullOrBlank() && enriched.length > 20 -> enriched
                        rawContent.length > 20 -> rawContent
                        else -> {
                            val origin = if (activeMovie.originTitle.isNotBlank()) " (${activeMovie.originTitle})" else ""
                            "Một câu chuyện vượt thời gian đầy kịch tính và cảm xúc$origin. Giữa những biến động của số phận, liệu họ có thể vượt qua thử thách để ở bên nhau?"
                        }
                    }
                }

                Text(
                    text = description,
                    color = FlixColors.TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetail(activeMovie) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Row matching mobile & TV design in css/home.css
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Primary "Xem ngay" Pill Button
                        Button(
                            onClick = { onPlayMovie(activeMovie) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FlixColors.Red,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .widthIn(min = if (isCompact) 140.dp else 160.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Xem ngay",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        if (isCompact) {
                            // Compact Circular "Thêm vào" Button with caption (matching css/home.css mobile)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onToggleFavorite(activeMovie) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xD914151B))
                                        .border(1.dp, FlixColors.Border, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = if (isFav) "Đã trong danh sách" else "Thêm vào danh sách",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (isFav) "Đã lưu" else "Thêm vào",
                                    color = FlixColors.TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            // Compact Circular "Yêu thích" Button with caption
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onToggleFavorite(activeMovie) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xD914151B))
                                        .border(1.dp, FlixColors.Border, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (isFav) "Bỏ yêu thích" else "Thêm yêu thích",
                                        tint = if (isFav) FlixColors.RedBright else Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Yêu thích",
                                    color = FlixColors.TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            // Wide screen / TV buttons
                            OutlinedButton(
                                onClick = { onToggleFavorite(activeMovie) },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xB314151B),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isFav) "Đã trong danh sách" else "Thêm vào danh sách",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            IconButton(
                                onClick = { onToggleFavorite(activeMovie) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xB314151B))
                                    .border(1.dp, FlixColors.Border, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (isFav) "Bỏ yêu thích" else "Thêm yêu thích",
                                    tint = if (isFav) FlixColors.RedBright else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Prev/Next arrows on Wide Screen / TV
                    if (!isCompact && heroMovies.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    currentIndex = (currentIndex - 1 + heroMovies.size) % heroMovies.size
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xB314151B))
                                    .border(1.dp, FlixColors.Border, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Trước",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    currentIndex = (currentIndex + 1) % heroMovies.size
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xB314151B))
                                    .border(1.dp, FlixColors.Border, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Tiếp",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatHeroMetadata(movie: Movie): List<String> {
    val parts = mutableListOf<String>()
    parts.add(movie.year.ifBlank { "2026" })
    val lang = movie.lang
    if (lang.isNotBlank()) {
        val cleanLang = if (lang.contains("+")) {
            lang
        } else if (lang.lowercase().contains("vietsub") && !lang.lowercase().contains("thuyết")) {
            "$lang + Thuyết Minh"
        } else {
            lang
        }
        parts.add(cleanLang)
    } else {
        parts.add("Vietsub + Thuyết Minh")
    }
    if (movie.episodeCurrent.isNotBlank()) {
        parts.add(movie.episodeCurrent)
    } else if (movie.time.isNotBlank()) {
        parts.add(movie.time)
    } else {
        parts.add("Tập mới")
    }
    if (movie.time.isNotBlank() && movie.episodeCurrent.isNotBlank() && !movie.episodeCurrent.contains(movie.time)) {
        parts.add(movie.time)
    }
    return parts
}
