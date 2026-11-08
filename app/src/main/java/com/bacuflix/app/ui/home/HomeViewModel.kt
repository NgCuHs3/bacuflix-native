package com.bacuflix.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bacuflix.app.BacuFlixApplication
import com.bacuflix.app.data.FavoritesRepository
import com.bacuflix.app.data.MlRecommendationService
import com.bacuflix.app.data.SmartSearchEngine
import com.bacuflix.app.model.CatalogFilterState
import com.bacuflix.app.model.EpisodeResumeInfo
import com.bacuflix.app.model.FilterSortOrder
import com.bacuflix.app.model.GenreCategory
import com.bacuflix.app.model.HomeTab
import com.bacuflix.app.model.Movie
import com.bacuflix.app.model.MovieDetailResponse
import com.bacuflix.app.ui.detail.buildPlaybackMovie
import com.bacuflix.app.util.BackupRestoreManager
import com.bacuflix.app.util.UrlUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BacuFlixApplication
    val kkphimApi = app.kkphimApi
    val catalogRepo = app.catalogRepository
    val favoritesRepo = app.favoritesRepository
    val watchHistoryRepo = app.watchHistoryRepository
    val googleTrendingService = app.googleTrendingService
    val mlRecommendationService = app.mlRecommendationService

    data class HomeUiState(
        val activeTab: HomeTab = HomeTab.NEW,
        val previousTab: HomeTab = HomeTab.NEW,
        val activeGenre: GenreCategory? = null,
        val filterState: CatalogFilterState = CatalogFilterState(),
        val isFilterExpanded: Boolean = false,
        val searchQuery: String = "",
        val searchSuggestions: List<String> = SmartSearchEngine.DEFAULT_TRENDING_KEYWORDS,
        val isTvKeyboardOpen: Boolean = false,
        val isSidebarCollapsed: Boolean = false,
        val isSettingsModalOpen: Boolean = false,
        val isLoadingTab: Boolean = true,
        val isLoadingMore: Boolean = false,
        val hasMorePages: Boolean = true,
        val currentPage: Int = 1,
        val errorMessage: String? = null,
        val rawGridMovies: List<Movie> = emptyList(),
        val gridMovies: List<Movie> = emptyList(),
        val newReleasesCarousel: List<Movie> = emptyList(),
        val trendingCarousel: List<Movie> = emptyList(),
        val recommendedCarousel: List<Movie> = emptyList(),
        val featuredSeriesCarousel: List<Movie> = emptyList(),
        val heroDescriptions: Map<String, String> = emptyMap(),
        val detailModalMovie: Movie? = null,
        val detailResponse: MovieDetailResponse? = null,
        val detailSimilarMovies: List<Movie> = emptyList(),
        val isDetailLoading: Boolean = false,
        val detailError: String? = null,
        val activePlayerMovie: Movie? = null,
        val toastMessage: String? = null
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val favoriteKeys: StateFlow<Set<String>> = favoritesRepo.favoriteKeys
    val watchHistory: StateFlow<List<Movie>> = watchHistoryRepo.history

    private var loadTabJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var detailJob: Job? = null
    private var toastJob: Job? = null

    init {
        loadInitialCarousels()
        switchTab(HomeTab.NEW)
        startPeriodicMlRefresh()
    }

    private fun loadInitialCarousels() {
        // 1. Trending + Hero + Top 10
        viewModelScope.launch {
            try {
                val res = googleTrendingService.getTrending(force = false)
                if (res.movies.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        trendingCarousel = res.movies
                    )
                    enrichHeroDescriptions(res.movies.take(3))
                }
            } catch (_: Exception) {}
        }

        // 2. ML Personalized Recommendations
        viewModelScope.launch {
            delay(250L)
            refreshMlRecommendations()
        }

        // 3. Featured Series (phim-bo)
        viewModelScope.launch {
            delay(600L)
            try {
                val res = kkphimApi.getMoviesByCategory("phim-bo", page = 1, limit = 14)
                if (res.movies.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        featuredSeriesCarousel = res.movies
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private fun enrichHeroDescriptions(movies: List<Movie>) {
        movies.forEach { movie ->
            val slug = movie.slug
            if (slug.isNotBlank()) {
                viewModelScope.launch {
                    try {
                        val detail = kkphimApi.getMovieDetail(slug)
                        val cleaned = UrlUtils.stripHtmlTags(detail.movie.content)
                        if (cleaned.length > 20) {
                            val updated = _uiState.value.heroDescriptions.toMutableMap()
                            updated[slug] = cleaned
                            _uiState.value = _uiState.value.copy(heroDescriptions = updated)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun startPeriodicMlRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(15000L)
                if (_uiState.value.activePlayerMovie == null) {
                    refreshMlRecommendations()
                }
            }
        }
    }

    fun refreshMlRecommendations() {
        viewModelScope.launch {
            try {
                val recs = mlRecommendationService.getRecommendations(limit = 24)
                if (recs.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        recommendedCarousel = recs
                    )
                    if (_uiState.value.activeTab == HomeTab.RECOMMENDED) {
                        _uiState.value = _uiState.value.copy(
                            gridMovies = recs,
                            isLoadingTab = false
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun switchTab(tab: HomeTab, keepKeyboardOpen: Boolean = false) {
        val prev = if (_uiState.value.activeTab != HomeTab.SEARCH) {
            _uiState.value.activeTab
        } else {
            _uiState.value.previousTab
        }

        _uiState.value = _uiState.value.copy(
            activeTab = tab,
            previousTab = prev,
            currentPage = 1,
            hasMorePages = tab != HomeTab.FAVORITES && tab != HomeTab.HISTORY,
            isLoadingTab = true,
            isLoadingMore = false,
            errorMessage = null,
            searchQuery = if (tab != HomeTab.SEARCH) "" else _uiState.value.searchQuery,
            isTvKeyboardOpen = if (!keepKeyboardOpen && tab != HomeTab.SEARCH) false else _uiState.value.isTvKeyboardOpen
        )

        loadTabJob?.cancel()
        loadTabJob = viewModelScope.launch {
            try {
                val baseMovies: List<Movie> = when {
                    _uiState.value.filterState.countrySlug.isNotBlank() &&
                        tab != HomeTab.FAVORITES && tab != HomeTab.HISTORY && tab != HomeTab.SEARCH -> {
                        val cSlug = _uiState.value.filterState.countrySlug
                        val gSlug = _uiState.value.filterState.genreSlug
                        val countryRes = try {
                            kkphimApi.getMoviesByCountry(cSlug, page = 1, limit = 24).movies
                        } catch (_: Exception) {
                            emptyList()
                        }
                        if (gSlug.isNotBlank()) {
                            val genreRes = try {
                                kkphimApi.getMoviesByGenre(gSlug, page = 1, limit = 24).movies
                            } catch (_: Exception) {
                                emptyList()
                            }
                            (countryRes + genreRes).distinctBy { it.effectiveKey }
                        } else {
                            countryRes
                        }
                    }
                    _uiState.value.filterState.genreSlug.isNotBlank() &&
                        tab != HomeTab.FAVORITES && tab != HomeTab.HISTORY && tab != HomeTab.SEARCH -> {
                        kkphimApi.getMoviesByGenre(_uiState.value.filterState.genreSlug, page = 1, limit = 24).movies
                    }
                    else -> when (tab) {
                        HomeTab.NEW -> {
                            val kkphimNew = try {
                                kkphimApi.getNewUpdated(page = 1).movies
                            } catch (_: Exception) {
                                emptyList()
                            }
                            val catalogMovies = try {
                                catalogRepo.load(forceRefresh = false).movies
                            } catch (_: Exception) {
                                emptyList()
                            }
                            if (kkphimNew.isNotEmpty()) {
                                _uiState.value = _uiState.value.copy(newReleasesCarousel = kkphimNew)
                            }
                            (kkphimNew + catalogMovies).distinctBy { it.effectiveKey }
                        }
                        HomeTab.MOVIES -> kkphimApi.getMoviesByCategory("phim-le", page = 1, limit = 24).movies
                        HomeTab.TVSHOWS -> kkphimApi.getMoviesByCategory("phim-bo", page = 1, limit = 24).movies
                        HomeTab.TRENDING -> googleTrendingService.getTrending(force = false).movies
                        HomeTab.RECOMMENDED -> {
                            val cached = _uiState.value.recommendedCarousel
                            cached.ifEmpty { mlRecommendationService.getRecommendations(limit = 24) }
                        }
                        HomeTab.GENRE -> {
                            val genreSlug = _uiState.value.activeGenre?.slug ?: "hanh-dong"
                            kkphimApi.getMoviesByGenre(genreSlug, page = 1, limit = 24).movies
                        }
                        HomeTab.FAVORITES -> favoritesRepo.getAll()
                        HomeTab.HISTORY -> watchHistoryRepo.getAll()
                        HomeTab.SEARCH -> {
                            val q = _uiState.value.searchQuery.trim()
                            if (q.isNotEmpty()) {
                                val res = SmartSearchEngine.search(
                                    keyword = q,
                                    page = 1,
                                    kkphimApi = kkphimApi,
                                    localPool = buildLocalSearchPool()
                                )
                                if (res.suggestions.isNotEmpty()) {
                                    _uiState.value = _uiState.value.copy(
                                        searchSuggestions = (res.suggestions + SmartSearchEngine.DEFAULT_TRENDING_KEYWORDS)
                                            .distinctBy { it.lowercase() }
                                            .take(10)
                                    )
                                }
                                res.movies
                            } else emptyList()
                        }
                    }
                }

                val filtered = applyFilterAndSort(baseMovies, _uiState.value.filterState)
                _uiState.value = _uiState.value.copy(
                    rawGridMovies = baseMovies,
                    gridMovies = filtered,
                    isLoadingTab = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    gridMovies = emptyList(),
                    isLoadingTab = false,
                    errorMessage = e.message ?: "Không thể tải danh sách phim."
                )
            }
        }
    }

    private suspend fun buildLocalSearchPool(): List<Movie> {
        val catalogMovies = try {
            catalogRepo.load(forceRefresh = false).movies
        } catch (_: Exception) {
            emptyList()
        }
        val state = _uiState.value
        return (
            watchHistoryRepo.getAll() +
                favoritesRepo.getAll() +
                state.trendingCarousel +
                state.newReleasesCarousel +
                state.recommendedCarousel +
                state.featuredSeriesCarousel +
                catalogMovies
            ).distinctBy { it.effectiveKey }
    }

    fun openGenreTab(genre: GenreCategory) {
        _uiState.value = _uiState.value.copy(activeGenre = genre)
        switchTab(HomeTab.GENRE)
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.value = _uiState.value.copy(searchQuery = newQuery)
        searchDebounceJob?.cancel()
        val trimmed = newQuery.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                searchSuggestions = SmartSearchEngine.DEFAULT_TRENDING_KEYWORDS
            )
            if (_uiState.value.activeTab == HomeTab.SEARCH) {
                switchTab(_uiState.value.previousTab, keepKeyboardOpen = _uiState.value.isTvKeyboardOpen)
            }
            return
        }

        // Instant local pool & search cache response (< 5ms)
        val localMatches = buildLocalSearchPool().filter { movie ->
            SmartSearchEngine.scoreMovieMatch(movie, trimmed, trimmed) >= 120.0
        }.sortedByDescending { SmartSearchEngine.scoreMovieMatch(it, trimmed, trimmed) }

        val cachedRes = kkphimApi.getCachedSearch(trimmed)
        val combinedInstant = (cachedRes?.movies.orEmpty() + localMatches).distinctBy { it.slug }

        if (combinedInstant.isNotEmpty()) {
            val prev = if (_uiState.value.activeTab != HomeTab.SEARCH) _uiState.value.activeTab else _uiState.value.previousTab
            _uiState.value = _uiState.value.copy(
                activeTab = HomeTab.SEARCH,
                previousTab = prev,
                gridMovies = combinedInstant,
                isLoadingTab = false,
                errorMessage = null
            )
        }

        searchDebounceJob = viewModelScope.launch {
            delay(220L)
            executeSearch(trimmed)
        }
    }

    fun executeSearch(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        mlRecommendationService.recordSearchQuery(kw)
        val prev = if (_uiState.value.activeTab != HomeTab.SEARCH) {
            _uiState.value.activeTab
        } else {
            _uiState.value.previousTab
        }

        val cachedRes = kkphimApi.getCachedSearch(kw)
        _uiState.value = _uiState.value.copy(
            activeTab = HomeTab.SEARCH,
            previousTab = prev,
            searchQuery = kw,
            currentPage = 1,
            hasMorePages = true,
            gridMovies = cachedRes?.movies ?: _uiState.value.gridMovies,
            isLoadingTab = cachedRes == null && _uiState.value.gridMovies.isEmpty(),
            isLoadingMore = false,
            errorMessage = null
        )

        loadTabJob?.cancel()
        loadTabJob = viewModelScope.launch {
            try {
                val res = SmartSearchEngine.search(
                    keyword = kw,
                    page = 1,
                    kkphimApi = kkphimApi,
                    localPool = buildLocalSearchPool()
                )
                val mergedSuggestions = (res.suggestions + SmartSearchEngine.DEFAULT_TRENDING_KEYWORDS)
                    .filter { !it.equals(kw, ignoreCase = true) }
                    .distinctBy { it.lowercase() }
                    .take(10)

                _uiState.value = _uiState.value.copy(
                    gridMovies = res.movies,
                    searchSuggestions = mergedSuggestions.ifEmpty { SmartSearchEngine.DEFAULT_TRENDING_KEYWORDS },
                    isLoadingTab = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                if (_uiState.value.gridMovies.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        gridMovies = emptyList(),
                        isLoadingTab = false,
                        errorMessage = e.message ?: "Lỗi khi tìm kiếm phim."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoadingTab = false)
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingTab || state.isLoadingMore || !state.hasMorePages) return
        if (state.activeTab == HomeTab.FAVORITES || state.activeTab == HomeTab.HISTORY) return

        val nextPage = state.currentPage + 1
        _uiState.value = state.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val moreMovies: List<Movie> = when {
                    state.filterState.countrySlug.isNotBlank() &&
                        state.activeTab != HomeTab.SEARCH -> {
                        kkphimApi.getMoviesByCountry(state.filterState.countrySlug, nextPage, 24).movies
                    }
                    state.filterState.genreSlug.isNotBlank() &&
                        state.activeTab != HomeTab.SEARCH -> {
                        kkphimApi.getMoviesByGenre(state.filterState.genreSlug, nextPage, 24).movies
                    }
                    else -> when (state.activeTab) {
                        HomeTab.NEW -> kkphimApi.getNewUpdated(nextPage).movies
                        HomeTab.MOVIES -> kkphimApi.getMoviesByCategory("phim-le", nextPage, 24).movies
                        HomeTab.TVSHOWS -> kkphimApi.getMoviesByCategory("phim-bo", nextPage, 24).movies
                        HomeTab.TRENDING -> kkphimApi.getTrending(nextPage).movies
                        HomeTab.RECOMMENDED -> {
                            val profile = mlRecommendationService.trainUserPreferenceModel()
                            val topGenre = profile.rankedGenres.firstOrNull()?.first ?: "hanh-dong"
                            kkphimApi.getMoviesByGenre(topGenre, nextPage, 24).movies.map { m ->
                                val ev = MlRecommendationService.scoreMovieWithModel(m, profile)
                                m.copy(mlScore = ev.score, mlMatchPercent = ev.matchPercent)
                            }.sortedByDescending { it.mlScore ?: 0.0 }
                        }
                        HomeTab.GENRE -> {
                            val slug = state.activeGenre?.slug ?: "hanh-dong"
                            kkphimApi.getMoviesByGenre(slug, nextPage, 24).movies
                        }
                        HomeTab.SEARCH -> {
                            val cleanKw = SmartSearchEngine.cleanSearchQuery(state.searchQuery)
                                .ifBlank { state.searchQuery }
                            kkphimApi.searchMovies(cleanKw, 24, nextPage).movies
                        }
                        else -> emptyList()
                    }
                }

                if (moreMovies.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        hasMorePages = false
                    )
                } else {
                    val existingKeys = _uiState.value.rawGridMovies.map { it.effectiveKey }.toSet()
                    val uniqueNew = moreMovies.filter { !existingKeys.contains(it.effectiveKey) }
                    val updatedRaw = _uiState.value.rawGridMovies + uniqueNew
                    _uiState.value = _uiState.value.copy(
                        rawGridMovies = updatedRaw,
                        gridMovies = applyFilterAndSort(updatedRaw, _uiState.value.filterState),
                        currentPage = nextPage,
                        isLoadingMore = false
                    )
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun applyFilterAndSort(movies: List<Movie>, filter: CatalogFilterState): List<Movie> {
        var result = movies
        if (filter.year.isNotBlank()) {
            result = result.filter { it.year.trim() == filter.year.trim() }
        }
        if (filter.langFilter.isNotBlank()) {
            result = result.filter { m ->
                val norm = MlRecommendationService.stripVietnameseAccents(m.lang).lowercase()
                when (filter.langFilter) {
                    "thuyet-minh" -> norm.contains("thuyet minh") || norm.contains("long tieng")
                    "vietsub" -> norm.contains("vietsub") || norm.isBlank()
                    else -> true
                }
            }
        }
        result = when (filter.sortOrder) {
            FilterSortOrder.DEFAULT -> result
            FilterSortOrder.YEAR_DESC -> result.sortedByDescending { it.year.toIntOrNull() ?: 0 }
            FilterSortOrder.ML_MATCH -> {
                val profile = mlRecommendationService.trainUserPreferenceModel()
                result.map { m ->
                    if (m.mlScore != null && m.mlMatchPercent != null) m
                    else {
                        val ev = MlRecommendationService.scoreMovieWithModel(m, profile)
                        m.copy(mlScore = ev.score, mlMatchPercent = ev.matchPercent)
                    }
                }.sortedByDescending { it.mlScore ?: 0.0 }
            }
            FilterSortOrder.TITLE_ASC -> result.sortedBy { it.title.lowercase() }
        }
        return result
    }

    fun toggleFilterExpanded() {
        _uiState.value = _uiState.value.copy(isFilterExpanded = !_uiState.value.isFilterExpanded)
    }

    fun updateCatalogFilter(newFilter: CatalogFilterState) {
        val prevFilter = _uiState.value.filterState
        _uiState.value = _uiState.value.copy(filterState = newFilter)
        val needsApiReload = prevFilter.countrySlug != newFilter.countrySlug ||
            prevFilter.genreSlug != newFilter.genreSlug
        if (needsApiReload) {
            switchTab(_uiState.value.activeTab)
        } else {
            val base = _uiState.value.rawGridMovies.ifEmpty { _uiState.value.gridMovies }
            _uiState.value = _uiState.value.copy(
                gridMovies = applyFilterAndSort(base, newFilter)
            )
        }
    }

    fun resetCatalogFilter() {
        val hadApiFilter = _uiState.value.filterState.countrySlug.isNotBlank() ||
            _uiState.value.filterState.genreSlug.isNotBlank()
        val empty = CatalogFilterState()
        _uiState.value = _uiState.value.copy(filterState = empty)
        if (hadApiFilter) {
            switchTab(_uiState.value.activeTab)
        } else {
            val base = _uiState.value.rawGridMovies.ifEmpty { _uiState.value.gridMovies }
            _uiState.value = _uiState.value.copy(gridMovies = base)
        }
    }

    fun setSettingsModalOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isSettingsModalOpen = open)
    }

    fun exportBackupJson(): String {
        return BackupRestoreManager.exportAll(
            favoritesRepo = favoritesRepo,
            watchHistoryRepo = watchHistoryRepo,
            mlService = mlRecommendationService
        )
    }

    fun importBackupJson(rawJson: String) {
        try {
            val summary = BackupRestoreManager.importAll(
                rawJson = rawJson,
                favoritesRepo = favoritesRepo,
                watchHistoryRepo = watchHistoryRepo,
                mlService = mlRecommendationService
            )
            if (_uiState.value.activeTab == HomeTab.FAVORITES) {
                _uiState.value = _uiState.value.copy(gridMovies = favoritesRepo.getAll())
            } else if (_uiState.value.activeTab == HomeTab.HISTORY) {
                _uiState.value = _uiState.value.copy(gridMovies = watchHistoryRepo.getAll())
            }
            refreshMlRecommendations()
            showToast("Khôi phục thành công: ${summary.favoritesRestored} Yêu thích, ${summary.historyRestored} Lịch sử xem!")
        } catch (e: Exception) {
            showToast(e.message ?: "Mã JSON sao lưu không hợp lệ.")
        }
    }

    fun clearAllHistory() {
        watchHistoryRepo.clearWatchHistory()
        if (_uiState.value.activeTab == HomeTab.HISTORY) {
            _uiState.value = _uiState.value.copy(gridMovies = emptyList())
        }
        refreshMlRecommendations()
        showToast("Đã xóa toàn bộ lịch sử xem phim")
    }

    fun clearAllFavorites() {
        favoritesRepo.clearFavorites()
        if (_uiState.value.activeTab == HomeTab.FAVORITES) {
            _uiState.value = _uiState.value.copy(gridMovies = emptyList())
        }
        refreshMlRecommendations()
        showToast("Đã xóa toàn bộ danh sách yêu thích")
    }

    fun toggleFavorite(movie: Movie) {
        val isNowFav = favoritesRepo.toggleFavorite(movie)
        if (_uiState.value.activeTab == HomeTab.FAVORITES) {
            _uiState.value = _uiState.value.copy(gridMovies = favoritesRepo.getAll())
        }
        showToast(if (isNowFav) "Đã thêm \"${movie.title}\" vào danh sách" else "Đã xóa khỏi danh sách")
        refreshMlRecommendations()
    }

    fun removeWatchHistory(movie: Movie) {
        watchHistoryRepo.removeWatchHistory(movie.effectiveKey)
        if (_uiState.value.activeTab == HomeTab.HISTORY) {
            _uiState.value = _uiState.value.copy(gridMovies = watchHistoryRepo.getAll())
        }
        refreshMlRecommendations()
    }

    fun openMovieOrPlay(movie: Movie) {
        val cleanSlug = FavoritesRepository.sanitizeApiSlug(movie.slug)
        if (cleanSlug.isNotBlank()) {
            openMovieDetail(movie.copy(slug = cleanSlug))
        } else if (movie.playbackEnabled && (movie.m3u8.isNotBlank() || movie.fallbackEmbed.isNotBlank() || movie.episodesList.isNotEmpty())) {
            resumeHistoryItem(movie)
        } else {
            openMovieDetail(movie)
        }
    }

    fun openMovieDetail(movie: Movie) {
        val cachedDetail = kkphimApi.getCachedMovieDetail(movie.slug)
        if (cachedDetail != null) {
            watchHistoryRepo.enrichMetadata(cachedDetail.movie)
            mlRecommendationService.recordMovieInteraction(cachedDetail.movie, "view")
            _uiState.value = _uiState.value.copy(
                detailModalMovie = cachedDetail.movie,
                detailResponse = cachedDetail,
                detailSimilarMovies = emptyList(),
                isDetailLoading = false,
                detailError = null
            )
            viewModelScope.launch {
                val similar = try {
                    mlRecommendationService.getSimilarMovies(
                        targetMovie = cachedDetail.movie,
                        localPool = buildLocalSearchPool(),
                        limit = 10
                    )
                } catch (_: Exception) {
                    emptyList()
                }
                if (_uiState.value.detailModalMovie?.effectiveKey == cachedDetail.movie.effectiveKey) {
                    _uiState.value = _uiState.value.copy(detailSimilarMovies = similar)
                }
            }
            return
        }

        _uiState.value = _uiState.value.copy(
            detailModalMovie = movie,
            detailResponse = null,
            detailSimilarMovies = emptyList(),
            isDetailLoading = true,
            detailError = null
        )
        loadDetailForModal(movie)
    }

    fun retryLoadDetail() {
        val movie = _uiState.value.detailModalMovie ?: return
        loadDetailForModal(movie)
    }

    private fun loadDetailForModal(movie: Movie) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true, detailError = null)
            try {
                val cachedHistory = watchHistoryRepo.getItem(movie.effectiveKey)
                val detail = SmartSearchEngine.resolveMovieDetailWithFallback(
                    movie = movie,
                    cachedHistoryItem = cachedHistory,
                    kkphimApi = kkphimApi,
                    onSlugResolved = { oldKey, resolvedDetail ->
                        val eps = resolvedDetail.servers.firstOrNull()?.episodes.orEmpty()
                        watchHistoryRepo.updateResolvedMovie(oldKey, resolvedDetail.movie, eps)
                    }
                )
                watchHistoryRepo.enrichMetadata(detail.movie)
                mlRecommendationService.recordMovieInteraction(detail.movie, "view")
                _uiState.value = _uiState.value.copy(
                    detailModalMovie = detail.movie,
                    detailResponse = detail,
                    isDetailLoading = false,
                    detailError = null
                )
                // Load ML similar movies asynchronously
                val similar = try {
                    mlRecommendationService.getSimilarMovies(
                        targetMovie = detail.movie,
                        localPool = buildLocalSearchPool(),
                        limit = 10
                    )
                } catch (_: Exception) {
                    emptyList()
                }
                if (_uiState.value.detailModalMovie?.effectiveKey == detail.movie.effectiveKey) {
                    _uiState.value = _uiState.value.copy(detailSimilarMovies = similar)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDetailLoading = false,
                    detailError = e.message ?: "Không thể tải thông tin chi tiết phim."
                )
            }
        }
    }

    fun closeMovieDetail() {
        detailJob?.cancel()
        _uiState.value = _uiState.value.copy(
            detailModalMovie = null,
            detailResponse = null,
            detailSimilarMovies = emptyList(),
            isDetailLoading = false,
            detailError = null
        )
        refreshMlRecommendations()
    }

    fun resumeHistoryItem(item: Movie) {
        val historyEntry = watchHistoryRepo.getItem(item.effectiveKey) ?: item
        val epIndex = historyEntry.currentEpisodeIndex.coerceAtLeast(0)
        val resumeInfo = watchHistoryRepo.getEpisodeResumeInfo(historyEntry.effectiveKey, epIndex)
        val savedTime = when {
            resumeInfo != null && resumeInfo.currentTime > 5 -> resumeInfo.currentTime
            historyEntry.currentTime > 5 -> historyEntry.currentTime
            item.currentTime > 5 -> item.currentTime
            else -> 0
        }

        val cleanSlug = FavoritesRepository.sanitizeApiSlug(historyEntry.slug)
        val baseKey = FavoritesRepository.normalizeMovieKey(cleanSlug, historyEntry.id.ifBlank { historyEntry.effectiveKey })

        // Fast path: cached episodes list or direct stream already available in history
        val cachedEpisodes = historyEntry.episodesList.ifEmpty { item.episodesList }
        val targetEpisode = cachedEpisodes.getOrNull(epIndex) ?: cachedEpisodes.firstOrNull()
        val directM3u8 = targetEpisode?.m3u8?.ifBlank { historyEntry.m3u8 } ?: historyEntry.m3u8.ifBlank { item.m3u8 }
        val directEmbed = targetEpisode?.fallbackEmbed?.ifBlank { historyEntry.fallbackEmbed } ?: historyEntry.fallbackEmbed.ifBlank { item.fallbackEmbed }

        if (directM3u8.isNotBlank() || directEmbed.isNotBlank()) {
            val safeEpIndex = if (cachedEpisodes.isNotEmpty()) epIndex.coerceIn(0, cachedEpisodes.lastIndex) else 0
            val epName = targetEpisode?.name
                ?.takeIf { it.isNotBlank() }
                ?: historyEntry.episodeName.ifBlank { "Tập ${safeEpIndex + 1}" }
            val seriesTitle = historyEntry.seriesTitle.ifBlank { historyEntry.title.substringBefore(" - ") }

            startPlayer(
                historyEntry.copy(
                    id = baseKey,
                    slug = cleanSlug,
                    seriesTitle = seriesTitle,
                    episodeName = epName,
                    title = "$seriesTitle - $epName",
                    m3u8 = directM3u8.ifBlank { directEmbed },
                    fallbackEmbed = directEmbed,
                    episodesList = cachedEpisodes,
                    currentEpisodeIndex = safeEpIndex,
                    resumeTime = savedTime,
                    playbackEnabled = true
                )
            )
            return
        }

        // Fallback: resolve detail from KKPhim API (by slug or title search) and start playback
        viewModelScope.launch {
            try {
                val detail = SmartSearchEngine.resolveMovieDetailWithFallback(
                    movie = historyEntry,
                    cachedHistoryItem = historyEntry,
                    kkphimApi = kkphimApi,
                    onSlugResolved = { oldKey, resolvedDetail ->
                        val eps = resolvedDetail.servers.firstOrNull()?.episodes.orEmpty()
                        watchHistoryRepo.updateResolvedMovie(oldKey, resolvedDetail.movie, eps)
                    }
                )
                val firstServer = detail.servers.firstOrNull()
                val episodes = firstServer?.episodes.orEmpty()
                if (episodes.isNotEmpty()) {
                    val safeIdx = epIndex.coerceIn(0, episodes.lastIndex)
                    val playback = buildPlaybackMovie(
                        movie = detail.movie,
                        serverName = firstServer?.serverName ?: "Vietsub",
                        episodes = episodes,
                        epIndex = safeIdx,
                        resumeSeconds = savedTime
                    )
                    if (playback != null) {
                        startPlayer(playback)
                        return@launch
                    }
                }
                openMovieDetail(detail.movie)
            } catch (_: Exception) {
                openMovieDetail(historyEntry)
            }
        }
    }

    fun startPlayer(playbackMovie: Movie) {
        _uiState.value = _uiState.value.copy(
            detailModalMovie = null,
            detailResponse = null,
            activePlayerMovie = playbackMovie
        )
    }

    fun exitPlayer(returnToDetail: Boolean = true) {
        val previous = _uiState.value.activePlayerMovie
        _uiState.value = _uiState.value.copy(activePlayerMovie = null)
        if (returnToDetail && previous != null && previous.slug.isNotBlank()) {
            openMovieDetail(
                previous.copy(
                    title = previous.seriesTitle.ifBlank { previous.title.substringBefore(" - ") }
                )
            )
        }
        refreshMlRecommendations()
    }

    fun handleDeepLink(movieSlug: String?, epParam: String?) {
        if (movieSlug.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val detail = kkphimApi.getMovieDetail(movieSlug)
                if (!epParam.isNullOrBlank()) {
                    val firstServer = detail.servers.firstOrNull()
                    val episodes = firstServer?.episodes.orEmpty()
                    if (episodes.isNotEmpty()) {
                        val epNum = epParam.toIntOrNull()
                        val targetIndex = when {
                            epNum != null && epNum in 1..episodes.size -> epNum - 1
                            else -> episodes.indexOfFirst {
                                it.slug.equals(epParam, ignoreCase = true) ||
                                    it.name.equals(epParam, ignoreCase = true)
                            }.coerceAtLeast(0)
                        }
                        val playback = buildPlaybackMovie(
                            movie = detail.movie,
                            serverName = firstServer?.serverName ?: "Vietsub",
                            episodes = episodes,
                            epIndex = targetIndex,
                            resumeSeconds = getEpisodeResumeInfo(movieSlug, targetIndex)?.currentTime ?: 0
                        )
                        if (playback != null) {
                            startPlayer(playback)
                            return@launch
                        }
                    }
                }
                openMovieDetail(detail.movie)
            } catch (_: Exception) {
                showToast("Không thể mở liên kết phim.")
            }
        }
    }

    fun getEpisodeResumeInfo(slug: String, episodeIndex: Int?): EpisodeResumeInfo? {
        return watchHistoryRepo.getEpisodeResumeInfo(slug, episodeIndex)
    }

    fun saveWatchProgress(movie: Movie, currentTimeSec: Int, durationSec: Int) {
        watchHistoryRepo.saveWatchProgress(movie, currentTimeSec, durationSec)
    }

    fun setTvKeyboardOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isTvKeyboardOpen = open)
    }

    fun toggleSidebar() {
        _uiState.value = _uiState.value.copy(isSidebarCollapsed = !_uiState.value.isSidebarCollapsed)
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.value = _uiState.value.copy(toastMessage = message)
        toastJob = viewModelScope.launch {
            delay(2500L)
            _uiState.value = _uiState.value.copy(toastMessage = null)
        }
    }
}
