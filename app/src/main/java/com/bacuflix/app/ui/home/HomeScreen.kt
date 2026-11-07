package com.bacuflix.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bacuflix.app.model.HomeTab
import com.bacuflix.app.ui.components.AsyncImagePoster
import com.bacuflix.app.ui.components.CatalogMovieCard
import com.bacuflix.app.ui.components.ContinueWatchingSection
import com.bacuflix.app.ui.components.GenreBrowsingSection
import com.bacuflix.app.ui.components.HeroCarousel
import com.bacuflix.app.ui.components.MovieCarouselSection
import com.bacuflix.app.ui.components.TvSearchKeyboardPanel
import com.bacuflix.app.ui.detail.MovieDetailModal
import com.bacuflix.app.ui.theme.FlixColors
import kotlinx.coroutines.launch

/**
 * Main BACUFlix Home & Catalog Screen ported from js/home/home-screen.js.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onExitApp: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val favoriteKeys by viewModel.favoriteKeys.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Double-Back & Tab Back Handler (matching js/navigation/double-back.js and js/app.js)
    BackHandler {
        when {
            state.detailModalMovie != null -> {
                viewModel.closeMovieDetail()
            }
            state.isTvKeyboardOpen -> {
                viewModel.setTvKeyboardOpen(false)
            }
            state.activeTab == HomeTab.SEARCH && state.searchQuery.isNotBlank() -> {
                viewModel.onSearchQueryChanged("")
                viewModel.switchTab(state.previousTab)
            }
            state.activeTab == HomeTab.GENRE || state.activeTab == HomeTab.RECOMMENDED -> {
                viewModel.switchTab(HomeTab.NEW)
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2200L) {
                    onExitApp()
                } else {
                    lastBackPressTime = now
                    viewModel.showToast("Nhấn BACK lần nữa để thoát BACUFlix")
                }
            }
        }
    }

    // Infinite scroll detection
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 6 && lastVisibleIndex >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B0E17),
                        FlixColors.BgPrimary,
                        Color(0xFF06070A)
                    )
                )
            )
    ) {
        val isWideScreen = maxWidth >= 720.dp
        val columnsCount = when {
            maxWidth < 480.dp -> 3
            maxWidth < 800.dp -> 4
            maxWidth < 1100.dp -> if (state.isTvKeyboardOpen) 3 else 5
            else -> if (state.isTvKeyboardOpen) 4 else 6
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                HomeTopHeader(
                    activeTab = state.activeTab,
                    searchQuery = state.searchQuery,
                    isWideScreen = isWideScreen,
                    onToggleSidebar = { viewModel.toggleSidebar() },
                    onSelectTab = { tab ->
                        viewModel.switchTab(tab)
                        coroutineScope.launch { gridState.scrollToItem(0) }
                    },
                    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onOpenTvKeyboard = { viewModel.setTvKeyboardOpen(!state.isTvKeyboardOpen) },
                    onNotificationClick = {
                        viewModel.switchTab(HomeTab.NEW)
                        viewModel.showToast("Đã cập nhật danh sách phim mới nhất hôm nay!")
                    },
                    onProfileClick = {
                        viewModel.switchTab(HomeTab.FAVORITES)
                    }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    MobileBottomNavBar(
                        activeTab = state.activeTab,
                        onSelectTab = { tab ->
                            viewModel.switchTab(tab)
                            coroutineScope.launch { gridState.scrollToItem(0) }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Left Sidebar on Wide Screen / Android TV
                if (isWideScreen && !state.isSidebarCollapsed) {
                    AppSidebar(
                        activeTab = state.activeTab,
                        onSelectTab = { tab ->
                            viewModel.switchTab(tab)
                            coroutineScope.launch { gridState.scrollToItem(0) }
                        },
                        onOpenSearch = {
                            viewModel.setTvKeyboardOpen(true)
                        },
                        onOpenSettings = {
                            viewModel.showToast("BACUFlix v3.0 Native · Chất lượng phát tự động FHD / 4K")
                        }
                    )
                }

                // Main Content Area
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val showHero = (state.activeTab == HomeTab.NEW ||
                        state.activeTab == HomeTab.MOVIES ||
                        state.activeTab == HomeTab.TVSHOWS ||
                        state.activeTab == HomeTab.TRENDING) && !state.isTvKeyboardOpen

                    val showCarousels = state.activeTab == HomeTab.NEW && !state.isTvKeyboardOpen

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columnsCount),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Hero Banner Carousel
                        if (showHero && state.trendingCarousel.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                HeroCarousel(
                                    movies = state.trendingCarousel,
                                    favoriteKeys = favoriteKeys,
                                    descriptionsMap = state.heroDescriptions,
                                    onPlayMovie = { viewModel.openMovieOrPlay(it) },
                                    onOpenDetail = { viewModel.openMovieOrPlay(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                                )
                            }
                        }

                        // 7 Home Carousel Sections (when on Trang chủ)
                        if (showCarousels) {
                            // 1. Phim mới cập nhật
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                MovieCarouselSection(
                                    title = "Phim mới cập nhật",
                                    movies = state.newReleasesCarousel,
                                    favoriteKeys = favoriteKeys,
                                    onSelectMovie = { viewModel.openMovieOrPlay(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                                )
                            }

                            // 2. Top 10 hôm nay
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                MovieCarouselSection(
                                    title = "Top 10 hôm nay",
                                    movies = state.trendingCarousel,
                                    favoriteKeys = favoriteKeys,
                                    isTop10 = true,
                                    onSelectMovie = { viewModel.openMovieOrPlay(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onViewAll = { viewModel.switchTab(HomeTab.TRENDING) }
                                )
                            }

                            // 3. Tiếp tục xem
                            if (watchHistory.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ContinueWatchingSection(
                                        historyItems = watchHistory,
                                        onResumeItem = { viewModel.resumeHistoryItem(it) },
                                        onRemoveItem = { viewModel.removeWatchHistory(it) }
                                    )
                                }
                            }

                            // 4. Phim thịnh hành
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                MovieCarouselSection(
                                    title = "Phim thịnh hành",
                                    movies = state.trendingCarousel,
                                    favoriteKeys = favoriteKeys,
                                    onSelectMovie = { viewModel.openMovieOrPlay(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onViewAll = { viewModel.switchTab(HomeTab.TRENDING) }
                                )
                            }

                            // 5. Phim đề xuất (ML Personalized)
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                MovieCarouselSection(
                                    title = "Phim đề xuất",
                                    movies = state.recommendedCarousel,
                                    favoriteKeys = favoriteKeys,
                                    onSelectMovie = { viewModel.openMovieOrPlay(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onViewAll = { viewModel.switchTab(HomeTab.RECOMMENDED) }
                                )
                            }

                            // 6. Thể loại
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                GenreBrowsingSection(
                                    backdropMovies = state.newReleasesCarousel.ifEmpty { state.trendingCarousel },
                                    onSelectGenre = { viewModel.openGenreTab(it) }
                                )
                            }

                            // 7. Phim bộ nổi bật
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                MovieCarouselSection(
                                    title = "Phim bộ nổi bật",
                                    movies = state.featuredSeriesCarousel,
                                    favoriteKeys = favoriteKeys,
                                    onSelectMovie = { viewModel.openMovieOrPlay(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onViewAll = { viewModel.switchTab(HomeTab.TVSHOWS) }
                                )
                            }
                        }

                        // Section Heading for Grid
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val sectionHeading = when (state.activeTab) {
                                HomeTab.NEW -> "Kho phim khám phá"
                                HomeTab.MOVIES -> "Phim lẻ đặc sắc"
                                HomeTab.TVSHOWS -> "Phim bộ & TV Show"
                                HomeTab.TRENDING -> "Phim đang thịnh hành"
                                HomeTab.RECOMMENDED -> "Phim đề xuất cho bạn"
                                HomeTab.FAVORITES -> "Danh sách của tôi"
                                HomeTab.HISTORY -> "Lịch sử xem phim"
                                HomeTab.GENRE -> "Thể loại: ${state.activeGenre?.name ?: ""}"
                                HomeTab.SEARCH -> "Kết quả tìm kiếm: \"${state.searchQuery}\""
                            }

                            val statusMessage = when {
                                state.isLoadingTab -> "Đang tải danh sách phim..."
                                state.errorMessage != null -> "Không thể tải danh sách phim. ${state.errorMessage}"
                                state.gridMovies.isEmpty() -> when (state.activeTab) {
                                    HomeTab.FAVORITES -> "Chưa có phim nào trong danh sách của bạn. Hãy chọn ♥ hoặc + Thêm vào danh sách!"
                                    HomeTab.HISTORY -> "Chưa có lịch sử xem phim. Hãy chọn một bộ phim để bắt đầu thưởng thức!"
                                    HomeTab.SEARCH -> "Không tìm thấy phim phù hợp với từ khóa \"${state.searchQuery}\"."
                                    else -> "Hiện không có phim nào trong mục này."
                                }
                                else -> "${state.gridMovies.size} phim"
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = sectionHeading,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White
                                    )
                                    if (state.gridMovies.isNotEmpty()) {
                                        Text(
                                            text = statusMessage,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = FlixColors.TextSecondary
                                        )
                                    }
                                }

                                if (state.gridMovies.isEmpty()) {
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text(
                                        text = statusMessage,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (state.errorMessage != null) FlixColors.RedBright else FlixColors.TextSecondary
                                    )
                                }
                            }
                        }

                        // Catalog Grid Cards
                        itemsIndexed(
                            items = state.gridMovies,
                            key = { idx, m -> "grid_${m.effectiveKey}_$idx" }
                        ) { _, movie ->
                            CatalogMovieCard(
                                movie = movie,
                                isFavorite = favoriteKeys.contains(movie.effectiveKey),
                                onClick = { viewModel.openMovieOrPlay(movie) },
                                onToggleFavorite = { viewModel.toggleFavorite(movie) }
                            )
                        }

                        // Loading more spinner
                        if (state.isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = FlixColors.Red,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Đang tải thêm phim...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FlixColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // TV QWERTY Keyboard Floating Panel
                    if (state.isTvKeyboardOpen) {
                        TvSearchKeyboardPanel(
                            query = state.searchQuery,
                            onQueryChange = { viewModel.onSearchQueryChanged(it) },
                            onClose = { viewModel.setTvKeyboardOpen(false) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }

        // Movie Detail Modal
        val modalMovie = state.detailModalMovie
        if (modalMovie != null) {
            MovieDetailModal(
                moviePreview = modalMovie,
                detailResponse = state.detailResponse,
                isLoading = state.isDetailLoading,
                errorMessage = state.detailError,
                isFavorite = favoriteKeys.contains(modalMovie.effectiveKey),
                resumeInfoProvider = { slug, epIdx -> viewModel.getEpisodeResumeInfo(slug, epIdx) },
                onRetry = { viewModel.retryLoadDetail() },
                onClose = { viewModel.closeMovieDetail() },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onSelectEpisode = { playbackMovie -> viewModel.startPlayer(playbackMovie) }
            )
        }

        // Toast Banner
        val toastText = state.toastMessage
        if (!toastText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isWideScreen) 28.dp else 88.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xEE181A22))
                    .border(1.dp, FlixColors.RedBright.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = toastText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun HomeTopHeader(
    activeTab: HomeTab,
    searchQuery: String,
    isWideScreen: Boolean,
    onToggleSidebar: () -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenTvKeyboard: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF508090D))
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left: Menu + Brand + Nav Tabs
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isWideScreen) {
                IconButton(
                    onClick = onToggleSidebar,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Đóng mở thanh bên",
                        tint = Color.White
                    )
                }
            }

            // BACUFlix Brand Wordmark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSelectTab(HomeTab.NEW) }
            ) {
                AsyncImagePoster(
                    url = "icon.png",
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (isWideScreen) 28.dp else 26.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) {
                            append("BACU")
                        }
                        withStyle(SpanStyle(color = FlixColors.Red, fontWeight = FontWeight.Black)) {
                            append("Flix")
                        }
                    },
                    fontSize = if (isWideScreen) 20.sp else 18.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (isWideScreen) {
                Spacer(modifier = Modifier.width(6.dp))
                val tabs = listOf(
                    HomeTab.NEW,
                    HomeTab.MOVIES,
                    HomeTab.TVSHOWS,
                    HomeTab.FAVORITES,
                    HomeTab.TRENDING
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tabs.forEach { tab ->
                        val selected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) FlixColors.Red else Color.Transparent)
                                .clickable { onSelectTab(tab) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tab.label,
                                color = if (selected) Color.White else FlixColors.TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        if (isWideScreen) {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Right: Search Input + TV Keyboard Toggle (TV/Wide) + Notification + Profile
        Row(
            modifier = if (isWideScreen) Modifier else Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = (if (isWideScreen) Modifier.width(230.dp) else Modifier.weight(1f))
                    .height(36.dp)
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
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Tìm phim, diễn viên...",
                            color = FlixColors.TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 12.5.sp
                        ),
                        cursorBrush = SolidColor(FlixColors.RedBright),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Xóa tìm kiếm",
                        tint = FlixColors.TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onSearchQueryChange("") }
                    )
                }
            }

            if (isWideScreen) {
                IconButton(
                    onClick = onOpenTvKeyboard,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(FlixColors.BgElevated)
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Bàn phím TV",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = onNotificationClick,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(FlixColors.BgElevated)
            ) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Thông báo phim mới",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(FlixColors.RedBright)
                    )
                }
            }

            IconButton(
                onClick = onProfileClick,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(FlixColors.Red)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Danh sách của tôi",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AppSidebar(
    activeTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val navItems = listOf(
        Triple(HomeTab.NEW, "Trang chủ", Icons.Default.Home),
        Triple(HomeTab.MOVIES, "Phim", Icons.Default.Movie),
        Triple(HomeTab.TVSHOWS, "TV Show", Icons.Default.Tv),
        Triple(HomeTab.TRENDING, "Thịnh hành", Icons.Default.Whatshot),
        Triple(HomeTab.FAVORITES, "Danh sách", Icons.Default.Bookmark),
        Triple(HomeTab.HISTORY, "Lịch sử", Icons.Default.History)
    )

    Column(
        modifier = Modifier
            .width(190.dp)
            .fillMaxHeight()
            .background(FlixColors.BgSecondary)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            navItems.forEach { (tab, label, icon) ->
                SidebarNavButton(
                    label = label,
                    icon = icon,
                    isActive = activeTab == tab,
                    onClick = { onSelectTab(tab) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(FlixColors.Border)
            )
            Spacer(modifier = Modifier.height(8.dp))

            SidebarNavButton(
                label = "Tìm kiếm TV",
                icon = Icons.Default.Search,
                isActive = activeTab == HomeTab.SEARCH,
                onClick = onOpenSearch
            )
        }

        SidebarNavButton(
            label = "Cài đặt",
            icon = Icons.Default.Settings,
            isActive = false,
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun SidebarNavButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) FlixColors.Red else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color.White else FlixColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = if (isActive) Color.White else FlixColors.TextSecondary,
            fontSize = 13.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun MobileBottomNavBar(
    activeTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit
) {
    val items = listOf(
        Triple(HomeTab.NEW, "Trang chủ", Icons.Default.Home),
        Triple(HomeTab.MOVIES, "Phim", Icons.Default.Movie),
        Triple(HomeTab.TVSHOWS, "TV Show", Icons.Default.Tv),
        Triple(HomeTab.FAVORITES, "Danh sách", Icons.Default.Bookmark),
        Triple(HomeTab.HISTORY, "Lịch sử", Icons.Default.History)
    )

    NavigationBar(
        containerColor = Color(0xF214151B),
        contentColor = Color.White,
        tonalElevation = 0.dp
    ) {
        items.forEach { (tab, label, icon) ->
            val selected = activeTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(20.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FlixColors.RedBright,
                    selectedTextColor = FlixColors.RedBright,
                    unselectedIconColor = FlixColors.TextSecondary,
                    unselectedTextColor = FlixColors.TextSecondary,
                    indicatorColor = Color(0x26E50920)
                )
            )
        }
    }
}
