package com.bacuflix.app.ui.components

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bacuflix.app.model.Movie
import com.bacuflix.app.ui.theme.FlixColors
import com.bacuflix.app.util.UrlUtils
import kotlinx.coroutines.delay

/**
 * Hero Banner Carousel ported from js/home/hero-carousel.js.
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

    LaunchedEffect(heroMovies.size, currentIndex) {
        if (heroMovies.size > 1) {
            delay(7000L)
            currentIndex = (currentIndex + 1) % heroMovies.size
        }
    }

    val activeMovie = heroMovies[currentIndex.coerceIn(0, heroMovies.lastIndex)]
    val isFav = favoriteKeys.contains(activeMovie.effectiveKey)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(FlixColors.BgSecondary)
    ) {
        // Backdrop image
        AsyncImagePoster(
            url = activeMovie.thumb.ifBlank { activeMovie.poster },
            alternateUrl = activeMovie.poster,
            contentDescription = activeMovie.title,
            maxDimension = 1280,
            modifier = Modifier.fillMaxSize()
        )

        // Multi-directional cinema gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x6608090D),
                            Color(0x3308090D),
                            Color(0xEA08090D),
                            FlixColors.BgPrimary
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
                            Color(0xE608090D),
                            Color(0x8008090D),
                            Color.Transparent
                        )
                    )
                )
        )

        // Content overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            // Quality badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(FlixColors.Red)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = activeMovie.quality.ifBlank { "FHD" },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = activeMovie.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onOpenDetail(activeMovie) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Metadata row
            val metaItems = remember(activeMovie) { formatHeroMetadata(activeMovie) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                metaItems.forEachIndexed { idx, part ->
                    Text(
                        text = part,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE4E4E7),
                        fontWeight = FontWeight.Medium
                    )
                    if (idx < metaItems.lastIndex) {
                        Text(
                            text = "•",
                            color = FlixColors.RedBright,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
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
                style = MaterialTheme.typography.bodyLarge,
                color = FlixColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons + Carousel Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onPlayMovie(activeMovie) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FlixColors.Red,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
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
                            fontSize = 14.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { onToggleFavorite(activeMovie) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0x8814151B),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFav) "Đã trong danh sách" else "Danh sách",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    IconButton(
                        onClick = { onToggleFavorite(activeMovie) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x8814151B))
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

                // Slide Indicators + Prev/Next
                if (heroMovies.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                currentIndex = (currentIndex - 1 + heroMovies.size) % heroMovies.size
                            },
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x8014151B))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Trước",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            heroMovies.indices.forEach { idx ->
                                val selected = idx == currentIndex
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(if (selected) 20.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) FlixColors.Red else Color(0x66FFFFFF))
                                        .clickable { currentIndex = idx }
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                currentIndex = (currentIndex + 1) % heroMovies.size
                            },
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x8014151B))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Tiếp",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
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
