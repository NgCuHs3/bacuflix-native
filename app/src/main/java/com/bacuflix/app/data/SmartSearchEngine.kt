package com.bacuflix.app.data

import com.bacuflix.app.model.EpisodeItem
import com.bacuflix.app.model.EpisodeServer
import com.bacuflix.app.model.Movie
import com.bacuflix.app.model.MovieDetailResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Intelligent Hybrid Search & 404-Resilient Movie Resolver for BACUFlix.
 * Combines:
 * 1. Query normalization (prefix/suffix stripping, Vietnamese diacritic insensitivity)
 * 2. Parallel KKPhim API multi-query search
 * 3. Live Google Suggest movie title autocomplete & query expansion
 * 4. Genre/Theme intent detection
 * 5. Local catalog, history & favorites fuzzy + Dice bigram typo-tolerant matching
 * 6. Multi-signal relevance ranking
 */
object SmartSearchEngine {
    private val CLEAN_PREFIX_REGEX = Regex(
        "^(xem\\s+phim\\s+bộ|xem\\s+phim\\s+lẻ|xem\\s+phim|phim\\s+bộ|phim\\s+lẻ|phim\\s+hoạt\\s+hình|phim\\s+chiếu\\s+rạp|phim)\\s+",
        RegexOption.IGNORE_CASE
    )
    private val CLEAN_SUFFIX_REGEX = Regex(
        "\\s+(tập\\s+\\d+|tap\\s+\\d+|vietsub|thuyết\\s+minh|thuyet\\s+minh|lồng\\s+tiếng|long\\s+tieng|full\\s+hd|full|fhd|hd)\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val EPISODE_SLUG_SUFFIX_REGEX = Regex("-(tap-\\d+|ep-\\d+|\\d+)$", RegexOption.IGNORE_CASE)

    val DEFAULT_TRENDING_KEYWORDS = listOf(
        "Cổ trang",
        "Hành động",
        "Tình cảm",
        "Hoạt hình 3D",
        "Kinh dị",
        "Hài hước",
        "Viễn tưởng",
        "Thuyết minh"
    )

    data class SmartSearchResult(
        val movies: List<Movie>,
        val suggestions: List<String>
    )

    fun cleanSearchQuery(raw: String): String {
        var s = raw.trim().replace(Regex("\\s+"), " ")
        val strippedPrefix = CLEAN_PREFIX_REGEX.replace(s, "").trim()
        if (strippedPrefix.length >= 2) {
            s = strippedPrefix
        }
        val strippedSuffix = CLEAN_SUFFIX_REGEX.replace(s, "").trim()
        if (strippedSuffix.length >= 2) {
            s = strippedSuffix
        }
        return s
    }

    fun normalizeForMatch(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return MlRecommendationService.stripVietnameseAccents(text)
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Character bigram Dice similarity [0.0 .. 1.0] for typo-tolerant matching.
     */
    fun diceSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.length < 2 || b.length < 2) return if (a == b) 1.0 else 0.0

        val bigramsA = HashMap<String, Int>()
        for (i in 0 until a.length - 1) {
            val bg = a.substring(i, i + 2)
            bigramsA[bg] = (bigramsA[bg] ?: 0) + 1
        }

        var intersection = 0
        for (i in 0 until b.length - 1) {
            val bg = b.substring(i, i + 2)
            val count = bigramsA[bg] ?: 0
            if (count > 0) {
                intersection++
                bigramsA[bg] = count - 1
            }
        }
        return (2.0 * intersection) / ((a.length - 1) + (b.length - 1))
    }

    /**
     * Scores a candidate Movie against the user's search query.
     * Returns 0.0 if the movie does not meaningfully match.
     */
    fun scoreMovieMatch(
        movie: Movie,
        rawQuery: String,
        coreQuery: String,
        expandedQueries: List<String> = emptyList(),
        fromDirectApiSearch: Boolean = false
    ): Double {
        val qAccented = coreQuery.lowercase().trim()
        val qNorm = normalizeForMatch(coreQuery)
        if (qNorm.isEmpty()) return 0.0

        val titleLower = movie.title.lowercase().trim()
        val originLower = movie.originTitle.lowercase().trim()
        val titleNorm = normalizeForMatch(movie.title)
        val originNorm = normalizeForMatch(movie.originTitle)
        val actorsNorm = normalizeForMatch(movie.actors.joinToString(" "))
        val categoriesNorm = normalizeForMatch(movie.categories.joinToString(" "))
        val countriesNorm = normalizeForMatch(movie.countries.joinToString(" "))

        var score = 0.0

        // 1. Exact & Prefix Matches on Title / OriginTitle
        when {
            titleLower == qAccented || originLower == qAccented -> score += 1200.0
            titleNorm == qNorm || (originNorm.isNotEmpty() && originNorm == qNorm) -> score += 1050.0
            titleLower.startsWith(qAccented) -> score += 750.0
            titleNorm.startsWith(qNorm) || (originNorm.isNotEmpty() && originNorm.startsWith(qNorm)) -> score += 650.0
            titleLower.contains(qAccented) -> score += 480.0
            titleNorm.contains(qNorm) || (originNorm.isNotEmpty() && originNorm.contains(qNorm)) -> score += 420.0
        }

        // 2. Word-level token overlap
        val qTokens = qNorm.split(" ").filter { it.isNotEmpty() }
        val titleTokens = (titleNorm.split(" ") + originNorm.split(" ")).filter { it.isNotEmpty() }.toSet()
        if (qTokens.isNotEmpty()) {
            var matchedTokens = 0
            var prefixMatchedTokens = 0
            for (qt in qTokens) {
                if (titleTokens.contains(qt)) {
                    matchedTokens++
                } else if (qt.length >= 2 && titleTokens.any { it.startsWith(qt) || qt.startsWith(it) }) {
                    prefixMatchedTokens++
                }
            }
            val tokenRatio = (matchedTokens + prefixMatchedTokens * 0.65) / qTokens.size.toDouble()
            if (matchedTokens == qTokens.size) {
                score += 340.0
            } else if (tokenRatio >= 0.5) {
                score += tokenRatio * 240.0
            }

            // Check actor / category / country token matches
            val metaText = "$actorsNorm $categoriesNorm $countriesNorm"
            val metaMatches = qTokens.count { it.length >= 2 && metaText.contains(it) }
            if (metaMatches > 0) {
                score += (metaMatches.toDouble() / qTokens.size.toDouble()) * 180.0
            }
        }

        // 3. Fuzzy Dice similarity for typo tolerance
        val simTitle = diceSimilarity(qNorm, titleNorm)
        val simOrigin = if (originNorm.isNotEmpty()) diceSimilarity(qNorm, originNorm) else 0.0
        val bestSim = max(simTitle, simOrigin)
        if (bestSim >= 0.58) {
            score += bestSim * 220.0
        }

        // 4. Expanded Google Suggest query match
        for ((idx, exp) in expandedQueries.withIndex()) {
            val expNorm = normalizeForMatch(exp)
            if (expNorm.length >= 3 && (titleNorm.contains(expNorm) || expNorm.contains(titleNorm))) {
                score += max(80.0, 220.0 - idx * 40.0)
                break
            }
        }

        // 5. Base trust for direct API search results + recency boost
        if (fromDirectApiSearch && score < 120.0) {
            score += 95.0
        }
        if (score > 0.0) {
            when (movie.year) {
                "2026" -> score += 24.0
                "2025" -> score += 16.0
                "2024" -> score += 8.0
            }
        }

        return score
    }

    /**
     * Detects if the query corresponds to a specific movie genre slug.
     */
    fun detectGenreSlug(query: String): String? {
        val qNorm = normalizeForMatch(query)
        if (qNorm.length < 2) return null
        for (prior in MlRecommendationService.GENRE_PRIORS) {
            val nameNorm = normalizeForMatch(prior.name)
            if (qNorm == nameNorm || qNorm.contains(nameNorm)) {
                return prior.slug
            }
            for (kw in prior.keywords) {
                val kwNorm = normalizeForMatch(kw)
                if (kwNorm.length >= 4 && (qNorm == kwNorm || qNorm.contains(kwNorm))) {
                    return prior.slug
                }
            }
        }
        return null
    }

    /**
     * Executes multi-source hybrid search across KKPhim API, Google Suggest query expansion,
     * Genre API (when genre intent is detected), and local Catalog / History / Favorites.
     */
    suspend fun search(
        keyword: String,
        page: Int,
        kkphimApi: KkphimApiService,
        localPool: List<Movie>
    ): SmartSearchResult = withContext(Dispatchers.IO) {
        val rawKw = keyword.trim()
        val coreKw = cleanSearchQuery(rawKw).ifBlank { rawKw }
        val unaccentedKw = MlRecommendationService.stripVietnameseAccents(coreKw).trim()

        if (page > 1) {
            val pageRes = try {
                kkphimApi.searchMovies(coreKw, limit = 24, page = page).movies
            } catch (_: Exception) {
                emptyList()
            }
            return@withContext SmartSearchResult(movies = pageRes, suggestions = emptyList())
        }

        coroutineScope {
            // 1. Primary API Search
            val primaryDeferred = async {
                try {
                    kkphimApi.searchMovies(coreKw, limit = 24, page = 1).movies
                } catch (_: Exception) {
                    emptyList()
                }
            }

            // 2. Variant API Search (if rawKw differs from coreKw or has Vietnamese accents)
            val secondaryVariant = when {
                !rawKw.equals(coreKw, ignoreCase = true) -> rawKw
                !unaccentedKw.equals(coreKw, ignoreCase = true) && unaccentedKw.length >= 3 -> unaccentedKw
                else -> null
            }
            val variantDeferred = async {
                if (secondaryVariant != null) {
                    try {
                        kkphimApi.searchMovies(secondaryVariant, limit = 16, page = 1).movies
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else emptyList()
            }

            // 3. Live Google Suggest Autocomplete & Expansion
            val googleSuggestionsDeferred = async {
                try {
                    val rawSugs = GoogleTrendingService.fetchGoogleSuggestions("phim $coreKw")
                    GoogleTrendingService.extractKeywords(rawSugs)
                        .filter { !it.equals(coreKw, ignoreCase = true) }
                        .take(8)
                } catch (_: Exception) {
                    emptyList()
                }
            }

            // 4. Genre Intent Search (if user searched for a genre/theme like "cổ trang", "kinh dị", "hoạt hình")
            val detectedGenre = detectGenreSlug(rawKw)
            val genreDeferred = async {
                if (detectedGenre != null) {
                    try {
                        kkphimApi.getMoviesByGenre(detectedGenre, page = 1, limit = 16).movies
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else emptyList()
            }

            val primaryMovies = primaryDeferred.await()
            val variantMovies = variantDeferred.await()
            val googleSuggestions = googleSuggestionsDeferred.await()
            val genreMovies = genreDeferred.await()

            // 5. If primary + variant results are sparse (< 8) or user typed unaccented query,
            // expand using top 2 Google Suggest movie titles!
            val expandedMovies = if ((primaryMovies.size < 8 || coreKw == unaccentedKw) && googleSuggestions.isNotEmpty()) {
                googleSuggestions.take(2).map { sug ->
                    async {
                        try {
                            kkphimApi.searchMovies(sug, limit = 8, page = 1).movies
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            } else {
                emptyList()
            }

            // 6. Score & Combine API + Local Pool movies
            val scoredMap = LinkedHashMap<String, Pair<Movie, Double>>()

            fun consider(movie: Movie, fromDirectApi: Boolean) {
                val key = FavoritesRepository.normalizeMovieKey(movie.slug, movie.id.ifBlank { movie.effectiveKey })
                if (key.isBlank()) return
                val sc = scoreMovieMatch(
                    movie = movie,
                    rawQuery = rawKw,
                    coreQuery = coreKw,
                    expandedQueries = googleSuggestions,
                    fromDirectApiSearch = fromDirectApi
                )
                if (sc <= 0.0 && !fromDirectApi) return
                val existing = scoredMap[key]
                if (existing == null || sc > existing.second) {
                    scoredMap[key] = movie to sc
                }
            }

            primaryMovies.forEach { consider(it, fromDirectApi = true) }
            variantMovies.forEach { consider(it, fromDirectApi = true) }
            expandedMovies.forEach { consider(it, fromDirectApi = true) }

            // Local fuzzy matching (Catalog, Trending, Watch History, Favorites)
            for (localMovie in localPool) {
                val sc = scoreMovieMatch(
                    movie = localMovie,
                    rawQuery = rawKw,
                    coreQuery = coreKw,
                    expandedQueries = googleSuggestions,
                    fromDirectApiSearch = false
                )
                if (sc >= 160.0) {
                    val key = FavoritesRepository.normalizeMovieKey(localMovie.slug, localMovie.id.ifBlank { localMovie.effectiveKey })
                    val existing = scoredMap[key]
                    if (existing == null || sc > existing.second) {
                        scoredMap[key] = localMovie to sc
                    }
                }
            }

            // Genre intent results
            for (gm in genreMovies) {
                val key = FavoritesRepository.normalizeMovieKey(gm.slug, gm.id.ifBlank { gm.effectiveKey })
                if (!scoredMap.containsKey(key)) {
                    val sc = scoreMovieMatch(gm, rawKw, coreKw, googleSuggestions, fromDirectApiSearch = false)
                    scoredMap[key] = gm to max(sc, 75.0)
                }
            }

            val ranked = scoredMap.values
                .sortedByDescending { it.second }
                .map { it.first }

            SmartSearchResult(
                movies = ranked,
                suggestions = googleSuggestions
            )
        }
    }

    /**
     * Resolves a MovieDetailResponse for any Movie (including Watch History / Favorites / Catalog items)
     * with 4-stage fallback so the user never hits an HTTP 404 dead-end:
     * Stage 1: Direct KKPhim API detail by sanitized slug
     * Stage 2: Stripped episode-suffix slug retry (e.g. "slug-tap-1" -> "slug")
     * Stage 3: Title-based KKPhim search & automatic slug healing
     * Stage 4: Synthesized MovieDetailResponse from cached episodesList / m3u8 / fallbackEmbed
     */
    suspend fun resolveMovieDetailWithFallback(
        movie: Movie,
        cachedHistoryItem: Movie?,
        kkphimApi: KkphimApiService,
        onSlugResolved: (oldKey: String, detail: MovieDetailResponse) -> Unit
    ): MovieDetailResponse = withContext(Dispatchers.IO) {
        val oldKey = movie.effectiveKey
        val cleanSlug = FavoritesRepository.sanitizeApiSlug(movie.slug)
            .ifBlank { FavoritesRepository.sanitizeApiSlug(cachedHistoryItem?.slug) }

        // Stage 1: Direct slug lookup
        if (cleanSlug.isNotBlank()) {
            try {
                return@withContext kkphimApi.getMovieDetail(cleanSlug)
            } catch (_: Exception) {
                // Stage 2: Strip accidental episode suffix (e.g., "-tap-1" or "-0") and retry
                val stripped = EPISODE_SLUG_SUFFIX_REGEX.replace(cleanSlug, "").trim()
                if (stripped.isNotBlank() && stripped != cleanSlug && !stripped.startsWith("movie-")) {
                    try {
                        val detail = kkphimApi.getMovieDetail(stripped)
                        onSlugResolved(oldKey, detail)
                        return@withContext detail
                    } catch (_: Exception) {}
                }
            }
        }

        // Stage 3: Search KKPhim by clean seriesTitle / title / originTitle to heal outdated or missing slug
        val rawTitle = movie.seriesTitle
            .ifBlank { cachedHistoryItem?.seriesTitle.orEmpty() }
            .ifBlank { movie.title }
            .substringBefore(" - ")
            .trim()
        val searchTitle = cleanSearchQuery(rawTitle).ifBlank { rawTitle }

        if (searchTitle.length >= 2) {
            try {
                val candidates = kkphimApi.searchMovies(searchTitle, limit = 8, page = 1).movies
                val bestMatch = candidates
                    .map { cand -> cand to scoreMovieMatch(cand, searchTitle, searchTitle, fromDirectApiSearch = true) }
                    .filter { it.second >= 220.0 && FavoritesRepository.sanitizeApiSlug(it.first.slug).isNotBlank() }
                    .maxByOrNull { it.second }
                    ?.first

                if (bestMatch != null) {
                    val detail = kkphimApi.getMovieDetail(bestMatch.slug)
                    onSlugResolved(oldKey, detail)
                    return@withContext detail
                }
            } catch (_: Exception) {}
        }

        // Stage 4: Synthesize MovieDetailResponse from cached episodesList or direct m3u8 / fallbackEmbed stream
        val merged = cachedHistoryItem ?: movie
        val episodes = when {
            movie.episodesList.isNotEmpty() -> movie.episodesList
            merged.episodesList.isNotEmpty() -> merged.episodesList
            else -> {
                val streamM3u8 = movie.m3u8.ifBlank { merged.m3u8 }
                val streamEmbed = movie.fallbackEmbed.ifBlank { merged.fallbackEmbed }
                if (streamM3u8.isNotBlank() || streamEmbed.isNotBlank()) {
                    val epName = movie.episodeName
                        .ifBlank { merged.episodeName }
                        .ifBlank { movie.episodeCurrent }
                        .ifBlank { "Full" }
                    listOf(
                        EpisodeItem(
                            index = 0,
                            name = epName,
                            slug = "tap-1",
                            m3u8 = streamM3u8.ifBlank { streamEmbed },
                            fallbackEmbed = streamEmbed
                        )
                    )
                } else {
                    emptyList()
                }
            }
        }

        if (episodes.isNotEmpty()) {
            val cleanSeriesTitle = merged.seriesTitle.ifBlank { merged.title.substringBefore(" - ") }
            val synthesizedMovie = merged.copy(
                id = FavoritesRepository.normalizeMovieKey(cleanSlug, merged.id.ifBlank { merged.effectiveKey }),
                slug = cleanSlug,
                title = cleanSeriesTitle,
                seriesTitle = cleanSeriesTitle,
                content = merged.content.ifBlank {
                    "Phim \"$cleanSeriesTitle\" đã được lưu sẵn luồng phát trong lịch sử thiết bị."
                },
                playbackEnabled = true
            )
            return@withContext MovieDetailResponse(
                movie = synthesizedMovie,
                servers = listOf(
                    EpisodeServer(
                        serverName = merged.serverName.ifBlank { "Vietsub" },
                        episodes = episodes
                    )
                )
            )
        }

        throw IllegalStateException("Không tìm thấy luồng phát cho phim \"$rawTitle\".")
    }
}
