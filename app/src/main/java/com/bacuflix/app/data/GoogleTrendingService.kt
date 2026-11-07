package com.bacuflix.app.data

import android.content.Context
import com.bacuflix.app.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Suggest Trending movie discovery service ported from js/services/google-trending-service.js.
 */
class GoogleTrendingService(
    context: Context,
    private val kkphimApi: KkphimApiService
) {
    companion object {
        private const val PREFS_NAME = "bacuflix_trending_prefs"
        private const val CACHE_KEY = "kkphim_google_trending_cache"
        private const val CACHE_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours

        private val IGNORE_PATTERNS = listOf(
            "chiếu rạp", "chieu rap", "quốc gia", "quoc gia", "hôm nay", "hom nay",
            "mới nhất", "moi nhat", "trung quốc", "trung quoc", "hàn quốc", "han quoc",
            "thái lan", "thai lan", "việt nam", "viet nam", "tvb", "hk", "cgv", "lotte",
            "hoạt hình", "hoat hinh", "tổng tài", "tong tai", "2024", "2025", "2026", "2027",
            "xem phim", "phim", "phim hay", "phim mới", "phim bộ", "phim lẻ", "thuyết minh", "lồng tiếng"
        )

        private val QUESTION_REGEX = Regex(
            "có bao nhiêu tập|co bao nhieu tap|lịch chiếu|lich chieu|review|tóm tắt|tom tat|diễn viên|dien vien|kết thúc|ket thuc|là ai|la ai"
        )
        private val PREFIX_REGEX = Regex("^(xem\\s+phim|phim\\s+bộ|phim\\s+lẻ|phim\\s+hoạt\\s+hình|phim)\\s+")
        private val SUFFIX_REGEX = Regex(
            "\\s+(tập\\s+\\d+|tap\\s+\\d+|\\d+\\s*tập|\\d+$|vietsub|thuyết minh|thuyet minh|lồng tiếng|long tieng|full|hd|fhd|3d|2d|anime|hoạt hình 3d|hoat hinh 3d|phần\\s+\\d+|phan\\s+\\d+|mới nhất|moi nhat|motphim|hhkungfu|yanhh|phimmoi|subnhanh|chillhay|rophim|bilutv|vieon|fpt).*$"
        )

        fun extractKeywords(suggestions: List<String>): List<String> {
            val titles = mutableListOf<String>()
            val seen = mutableSetOf<String>()

            for (raw in suggestions) {
                var s = raw.lowercase().trim()
                if (QUESTION_REGEX.containsMatchIn(s)) continue
                s = PREFIX_REGEX.replace(s, "").trim()
                s = SUFFIX_REGEX.replace(s, "").trim()

                val isIgnored = IGNORE_PATTERNS.any { pattern ->
                    s == pattern || (s.length < 10 && s.contains(pattern))
                }
                if (!isIgnored && s.length >= 3 && seen.add(s)) {
                    titles.add(s)
                }
            }
            return titles
        }

        suspend fun fetchGoogleSuggestions(seed: String): List<String> = withContext(Dispatchers.IO) {
            val query = URLEncoder.encode(seed.ifBlank { "phim " }, "UTF-8")
            val urlString = "https://suggestqueries.google.com/complete/search?client=chrome&hl=vi&gl=vn&ie=utf-8&oe=utf-8&q=$query"
            try {
                val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; BACUFlix/3.0)")
                }
                try {
                    if (conn.responseCode !in 200..299) return@withContext emptyList()
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(text)
                    val suggestionsArr = arr.optJSONArray(1) ?: return@withContext emptyList()
                    val list = mutableListOf<String>()
                    for (i in 0 until suggestionsArr.length()) {
                        val item = suggestionsArr.optString(i).trim()
                        if (item.isNotEmpty()) list.add(item)
                    }
                    list
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class TrendingResult(
        val movies: List<Movie>,
        val isCached: Boolean,
        val source: String
    )

    suspend fun getTrending(force: Boolean = false): TrendingResult = withContext(Dispatchers.IO) {
        if (!force) {
            val cached = getCachedTrending()
            if (!cached.isNullOrEmpty()) {
                return@withContext TrendingResult(
                    movies = cached,
                    isCached = true,
                    source = "google-cache"
                )
            }
        }

        val seeds = listOf("xem phim ", "phim ", "phim bo ", "phim chieu rap ")
        try {
            val allSuggestions = coroutineScope {
                seeds.map { seed -> async { fetchGoogleSuggestions(seed) } }
                    .awaitAll()
                    .flatten()
            }
            val topKeywords = extractKeywords(allSuggestions).take(15)
            if (topKeywords.isEmpty()) {
                val fallback = kkphimApi.getHome().movies
                return@withContext TrendingResult(fallback, isCached = false, source = "kkphim-fallback")
            }

            val nestedMovies = coroutineScope {
                topKeywords.map { kw ->
                    async {
                        try {
                            kkphimApi.searchMovies(kw, limit = 3, page = 1).movies
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll()
            }

            val trendingMovies = mutableListOf<Movie>()
            val seenSlugs = mutableSetOf<String>()
            for (list in nestedMovies) {
                for (m in list) {
                    val key = m.effectiveKey
                    if (key.isNotBlank() && seenSlugs.add(key)) {
                        trendingMovies.add(m)
                    }
                }
            }

            if (trendingMovies.size < 24) {
                try {
                    val homeMovies = kkphimApi.getHome().movies
                    for (hm in homeMovies) {
                        val key = hm.effectiveKey
                        if (key.isNotBlank() && seenSlugs.add(key)) {
                            trendingMovies.add(hm)
                        }
                    }
                } catch (_: Exception) {}
            }

            if (trendingMovies.isNotEmpty()) {
                saveCachedTrending(trendingMovies)
            }
            TrendingResult(trendingMovies, isCached = false, source = "google")
        } catch (_: Exception) {
            val fallback = try {
                kkphimApi.getHome().movies
            } catch (_: Exception) {
                emptyList()
            }
            TrendingResult(fallback, isCached = false, source = "kkphim-fallback")
        }
    }

    private fun getCachedTrending(): List<Movie>? {
        return try {
            val raw = prefs.getString(CACHE_KEY, null) ?: return null
            val obj = JSONObject(raw)
            val timestamp = obj.optLong("timestamp", 0L)
            if (System.currentTimeMillis() - timestamp >= CACHE_TTL_MS) return null
            val arr = obj.optJSONArray("movies") ?: return null
            val list = mutableListOf<Movie>()
            for (i in 0 until arr.length()) {
                val mObj = arr.optJSONObject(i) ?: continue
                val slug = mObj.optString("slug").ifBlank { mObj.optString("id") }
                if (slug.isBlank()) continue
                list.add(
                    Movie(
                        id = slug,
                        slug = slug,
                        title = mObj.optString("title", "Phim"),
                        seriesTitle = mObj.optString("title", "Phim"),
                        originTitle = mObj.optString("originTitle"),
                        poster = mObj.optString("poster", CatalogSources.FALLBACK_POSTER),
                        thumb = mObj.optString("thumb", CatalogSources.FALLBACK_POSTER),
                        year = mObj.optString("year"),
                        quality = mObj.optString("quality", "FHD"),
                        lang = mObj.optString("lang", "Vietsub"),
                        episodeCurrent = mObj.optString("episodeCurrent"),
                        time = mObj.optString("time"),
                        type = mObj.optString("type"),
                        playbackEnabled = true
                    )
                )
            }
            list.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCachedTrending(movies: List<Movie>) {
        try {
            val arr = JSONArray()
            for (m in movies) {
                arr.put(
                    JSONObject().apply {
                        put("id", m.id)
                        put("slug", m.slug)
                        put("title", m.title)
                        put("originTitle", m.originTitle)
                        put("poster", m.poster)
                        put("thumb", m.thumb)
                        put("year", m.year)
                        put("quality", m.quality)
                        put("lang", m.lang)
                        put("episodeCurrent", m.episodeCurrent)
                        put("time", m.time)
                        put("type", m.type)
                    }
                )
            }
            val record = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("movies", arr)
            }
            prefs.edit().putString(CACHE_KEY, record.toString()).apply()
        } catch (_: Exception) {}
    }
}
