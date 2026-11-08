package com.bacuflix.app.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bacuflix.app.model.EpisodeItem
import com.bacuflix.app.model.EpisodeResumeInfo
import com.bacuflix.app.model.EpisodeServer
import com.bacuflix.app.model.Movie
import com.bacuflix.app.model.MovieDetailResponse
import com.bacuflix.app.ui.components.AsyncImagePoster
import com.bacuflix.app.ui.components.CatalogMovieCard
import com.bacuflix.app.ui.theme.FlixColors
import com.bacuflix.app.util.FormatTime
import com.bacuflix.app.util.UrlUtils

/**
 * Movie Detail Modal ported from js/home/movie-detail-modal.js, enhanced with:
 * - Multi-Source Server Selector (KKPhim, OPhim, Nguonc)
 * - Episode Range Chunk Tabs & Quick Episode Search for long series
 * - Clickable Actor / Category / Country tags to search related movies
 * - ML Similar Movies ("Phim tương tự cùng thể loại") carousel
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailModal(
    moviePreview: Movie,
    detailResponse: MovieDetailResponse?,
    similarMovies: List<Movie> = emptyList(),
    favoriteKeys: Set<String> = emptySet(),
    isLoading: Boolean,
    errorMessage: String?,
    isFavorite: Boolean,
    resumeInfoProvider: (slug: String, episodeIndex: Int?) -> EpisodeResumeInfo?,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onToggleFavorite: (Movie) -> Unit,
    onSelectEpisode: (Movie) -> Unit,
    onSelectSimilarMovie: (Movie) -> Unit = {},
    onSearchByTag: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var activeServerIndex by remember(detailResponse) { mutableIntStateOf(0) }
    var episodeChunkIndex by remember(detailResponse, activeServerIndex) { mutableIntStateOf(0) }
    var episodeFilterQuery by remember(detailResponse, activeServerIndex) { mutableStateOf("") }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, FlixColors.Border, RoundedCornerShape(20.dp)),
            color = FlixColors.BgSecondary
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val activeMovie = detailResponse?.movie ?: moviePreview

                // Backdrop
                AsyncImagePoster(
                    url = activeMovie.thumb.ifBlank { activeMovie.poster },
                    alternateUrl = activeMovie.poster,
                    contentDescription = null,
                    maxDimension = 1080,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x8808090D),
                                    Color(0xDD14151B),
                                    FlixColors.BgSecondary
                                )
                            )
                        )
                )

                val movie = detailResponse?.movie ?: moviePreview
                val servers = detailResponse?.servers.orEmpty()
                val currentServer = servers.getOrNull(activeServerIndex) ?: servers.firstOrNull()
                val episodes = currentServer?.episodes.orEmpty()
                val overallResume = resumeInfoProvider(movie.effectiveKey, null)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Poster
                                AsyncImagePoster(
                                    url = movie.poster,
                                    alternateUrl = movie.thumb,
                                    contentDescription = movie.title,
                                    modifier = Modifier
                                        .width(120.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, FlixColors.Border, RoundedCornerShape(12.dp))
                                )

                                // Title & Badges
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = movie.title,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White
                                    )
                                    if (movie.originTitle.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = movie.originTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = FlixColors.TextSecondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (movie.quality.isNotBlank()) {
                                            DetailBadge(text = movie.quality, bg = FlixColors.Red)
                                        }
                                        if (movie.year.isNotBlank()) {
                                            DetailBadge(text = movie.year, bg = FlixColors.BgElevated)
                                        }
                                        if (movie.lang.isNotBlank()) {
                                            DetailBadge(text = movie.lang, bg = FlixColors.BgElevated)
                                        }
                                        if (movie.episodeCurrent.isNotBlank()) {
                                            DetailBadge(text = movie.episodeCurrent, bg = Color(0xFF1E293B))
                                        }
                                        if (servers.size > 1) {
                                            DetailBadge(text = "${servers.size} Server", bg = Color(0xFF065F46))
                                        }
                                    }

                                    // Clickable Categories
                                    if (movie.categories.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Thể loại:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = FlixColors.TextSecondary,
                                                modifier = Modifier.align(Alignment.CenterVertically)
                                            )
                                            movie.categories.forEach { cat ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF1F2330))
                                                        .border(1.dp, FlixColors.Border, RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            onClose()
                                                            onSearchByTag(cat)
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = cat,
                                                        color = Color(0xFFE4E4E7),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Clickable Countries
                                    if (movie.countries.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Quốc gia:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = FlixColors.TextSecondary,
                                                modifier = Modifier.align(Alignment.CenterVertically)
                                            )
                                            movie.countries.forEach { country ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF1F2330))
                                                        .border(1.dp, FlixColors.Border, RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            onClose()
                                                            onSearchByTag(country)
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = country,
                                                        color = Color(0xFFE4E4E7),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Synopsis
                            val cleanDesc = remember(movie.content) {
                                UrlUtils.stripHtmlTags(movie.content)
                                    .ifBlank { "Chưa có mô tả nội dung cho phim này." }
                            }
                            Text(
                                text = cleanDesc,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFD4D4D8)
                            )

                            // Clickable Actor Chips
                            if (movie.actors.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Diễn viên:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FlixColors.TextSecondary,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                    movie.actors.take(8).forEach { actor ->
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(Color(0xFF1C202B))
                                                .border(1.dp, FlixColors.Border, RoundedCornerShape(999.dp))
                                                .clickable {
                                                    onClose()
                                                    onSearchByTag(actor)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = FlixColors.RedBright,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = actor,
                                                color = Color(0xFFE4E4E7),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action Buttons Row
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (overallResume != null && episodes.isNotEmpty() && overallResume.episodeIndex < episodes.size) {
                                    val epLabel = episodes[overallResume.episodeIndex].name
                                    Button(
                                        onClick = {
                                            val playback = buildPlaybackMovie(
                                                movie = movie,
                                                serverName = currentServer?.serverName ?: "Vietsub",
                                                episodes = episodes,
                                                servers = servers,
                                                epIndex = overallResume.episodeIndex,
                                                resumeSeconds = overallResume.currentTime
                                            )
                                            if (playback != null) onSelectEpisode(playback)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = FlixColors.Red,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Tiếp tục $epLabel (${FormatTime.formatTime(overallResume.currentTime)})",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else if (episodes.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            val playback = buildPlaybackMovie(
                                                movie = movie,
                                                serverName = currentServer?.serverName ?: "Vietsub",
                                                episodes = episodes,
                                                servers = servers,
                                                epIndex = 0,
                                                resumeSeconds = 0
                                            )
                                            if (playback != null) onSelectEpisode(playback)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = FlixColors.Red,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Xem ngay (${episodes[0].name})",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { onToggleFavorite(movie) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = FlixColors.BgElevated,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isFavorite) FlixColors.RedBright else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isFavorite) "Đã yêu thích" else "Thêm vào yêu thích",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val shareUrl = UrlUtils.buildMovieUrl(movie.effectiveKey)
                                        UrlUtils.shareMovieLink(context, movie.title, shareUrl)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = FlixColors.BgElevated,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Chia sẻ", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            if (isLoading && detailResponse == null) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    color = FlixColors.BgElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, FlixColors.Border)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = FlixColors.Red,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Đang tải nguồn phát & danh sách tập...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = FlixColors.TextSecondary
                                        )
                                    }
                                }
                            } else if (errorMessage != null && detailResponse == null) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    color = FlixColors.BgElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, FlixColors.Border)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = errorMessage,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = FlixColors.RedBright
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = onRetry,
                                            colors = ButtonDefaults.buttonColors(containerColor = FlixColors.Red)
                                        ) {
                                            Text("Thử lại", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                // Section Header + Quick Episode Search Input (when >= 12 episodes)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Chọn tập phim (${episodes.size} tập):",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )

                                if (episodes.size >= 12) {
                                    Row(
                                        modifier = Modifier
                                            .width(175.dp)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(FlixColors.BgElevated)
                                            .border(1.dp, FlixColors.Border, RoundedCornerShape(999.dp))
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = FlixColors.TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (episodeFilterQuery.isEmpty()) {
                                                Text(
                                                    text = "Tìm số tập...",
                                                    color = FlixColors.TextMuted,
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                            BasicTextField(
                                                value = episodeFilterQuery,
                                                onValueChange = { episodeFilterQuery = it },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                cursorBrush = SolidColor(FlixColors.RedBright),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (episodeFilterQuery.isNotEmpty()) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Xóa",
                                                tint = FlixColors.TextSecondary,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { episodeFilterQuery = "" }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Multi-Source Server Tabs
                            if (servers.size > 1) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    servers.forEachIndexed { idx, srv ->
                                        val active = idx == activeServerIndex
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (active) FlixColors.Red else FlixColors.BgElevated)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (active) Color.White.copy(alpha = 0.6f) else FlixColors.Border,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { activeServerIndex = idx }
                                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = srv.serverName,
                                                color = Color.White,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Episode Range Tabs (e.g. 1-50, 51-100...) when > 50 episodes and not searching
                            val chunkSize = 50
                            val totalChunks = ((episodes.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)
                            if (totalChunks > 1 && episodeFilterQuery.isBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (cIdx in 0 until totalChunks) {
                                        val startEp = cIdx * chunkSize + 1
                                        val endEp = ((cIdx + 1) * chunkSize).coerceAtMost(episodes.size)
                                        val selected = episodeChunkIndex.coerceIn(0, totalChunks - 1) == cIdx
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) Color(0xFF282D3E) else FlixColors.BgElevated)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (selected) FlixColors.RedBright else FlixColors.Border,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { episodeChunkIndex = cIdx }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "Tập $startEp – $endEp",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // Episodes Grid
                            if (episodes.isNotEmpty()) {
                                val displayedIndexedEpisodes = remember(episodes, episodeChunkIndex, episodeFilterQuery) {
                                    val q = episodeFilterQuery.trim().lowercase()
                                    if (q.isNotEmpty()) {
                                        episodes.mapIndexedNotNull { idx, ep ->
                                            if (ep.name.lowercase().contains(q) || (idx + 1).toString() == q) {
                                                idx to ep
                                            } else null
                                        }
                                    } else if (totalChunks > 1) {
                                        val safeChunk = episodeChunkIndex.coerceIn(0, totalChunks - 1)
                                        val from = safeChunk * chunkSize
                                        val to = ((safeChunk + 1) * chunkSize).coerceAtMost(episodes.size)
                                        (from until to).map { idx -> idx to episodes[idx] }
                                    } else {
                                        episodes.mapIndexed { idx, ep -> idx to ep }
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    displayedIndexedEpisodes.forEach { (epIdx, ep) ->
                                        val epResume = resumeInfoProvider(movie.effectiveKey, epIdx)
                                        val isCurrentEp = overallResume?.episodeIndex == epIdx
                                        val bgColor = when {
                                            isCurrentEp -> FlixColors.Red
                                            epResume != null -> Color(0xFF272B3A)
                                            else -> FlixColors.BgElevated
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(bgColor)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isCurrentEp) Color.White else FlixColors.Border,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    val playback = buildPlaybackMovie(
                                                        movie = movie,
                                                        serverName = currentServer?.serverName ?: "Vietsub",
                                                        episodes = episodes,
                                                        servers = servers,
                                                        epIndex = epIdx,
                                                        resumeSeconds = epResume?.currentTime ?: 0
                                                    )
                                                    if (playback != null) onSelectEpisode(playback)
                                                }
                                                .padding(horizontal = 14.dp, vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val label = if (epResume != null) {
                                                "${ep.name} · ${FormatTime.formatTime(epResume.currentTime)}"
                                            } else {
                                                ep.name
                                            }
                                            Text(
                                                text = label,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = if (isCurrentEp) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Chưa có tập phim nào trên server này.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FlixColors.TextSecondary
                                )
                            }
                        }

                        // Similar Movies Section ("Phim tương tự cùng thể loại")
                        if (similarMovies.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(26.dp))
                            Text(
                                text = "Phim tương tự cùng thể loại",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                similarMovies.forEach { simMovie ->
                                    CatalogMovieCard(
                                        movie = simMovie,
                                        isFavorite = favoriteKeys.contains(simMovie.effectiveKey),
                                        onClick = { onSelectSimilarMovie(simMovie) },
                                        onToggleFavorite = { onToggleFavorite(simMovie) },
                                        modifier = Modifier.width(135.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xB308090D))
                        .border(1.dp, FlixColors.Border, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng chi tiết",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBadge(text: String, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun buildPlaybackMovie(
    movie: Movie,
    serverName: String,
    episodes: List<EpisodeItem>,
    epIndex: Int,
    resumeSeconds: Int,
    servers: List<EpisodeServer> = movie.serversList
): Movie? {
    val epObj = episodes.getOrNull(epIndex) ?: return null
    val streamUrl = epObj.m3u8.ifBlank { epObj.fallbackEmbed }
    if (streamUrl.isBlank()) return null

    val cleanSlug = com.bacuflix.app.data.FavoritesRepository.sanitizeApiSlug(movie.slug)
    val baseKey = com.bacuflix.app.data.FavoritesRepository.normalizeMovieKey(cleanSlug, movie.id.ifBlank { movie.effectiveKey })

    return movie.copy(
        id = baseKey,
        slug = cleanSlug,
        seriesTitle = movie.title,
        episodeName = epObj.name,
        title = "${movie.title} - ${epObj.name}",
        m3u8 = streamUrl,
        fallbackEmbed = epObj.fallbackEmbed,
        playbackEnabled = true,
        source = "KKPhim · $serverName",
        episodesList = episodes,
        serversList = servers,
        currentEpisodeIndex = epIndex,
        resumeTime = resumeSeconds,
        serverName = serverName
    )
}
