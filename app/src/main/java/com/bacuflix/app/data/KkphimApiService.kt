package com.bacuflix.app.data

import com.bacuflix.app.model.EpisodeItem
import com.bacuflix.app.model.EpisodeServer
import com.bacuflix.app.model.Movie
import com.bacuflix.app.model.MovieDetailResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * KKPhim + Multi-Source (OPhim & Nguonc) API client ported from js/services/kkphim-api.js.
 */
class KkphimApiService {
    companion object {
        const val BASE_URL = "https://phimapi.com"
        const val DEFAULT_CDN = "https://phimimg.com"
        const val OPHIM_BASE_URL = "https://ophim1.com"
        const val OPHIM_CDN = "https://img.ophim.live/uploads/movies"
        const val NGUONC_BASE_URL = "https://phim.nguonc.com"
        private const val REQUEST_TIMEOUT_MS = 12000
        private const val BACKUP_SOURCE_TIMEOUT_MS = 5000

        fun buildImageUrl(url: String?, cdnDomain: String? = DEFAULT_CDN): String {
            val clean = url?.trim().orEmpty()
            if (clean.isEmpty()) {
                return CatalogSources.FALLBACK_POSTER
            }
            if (clean.startsWith("http://") || clean.startsWith("https://")) {
                return clean
            }
            val domain = (cdnDomain?.takeIf { it.isNotBlank() } ?: DEFAULT_CDN).trimEnd('/')
            val path = clean.trimStart('/')
            return "$domain/$path"
        }

        fun normalizeMovie(item: JSONObject?, cdnDomain: String? = DEFAULT_CDN): Movie? {
            if (item == null) return null
            val slug = item.optString("slug").ifBlank { item.optString("_id") }
            val title = item.optString("name")
                .ifBlank { item.optString("title") }
                .ifBlank { item.optString("origin_name") }
                .ifBlank { "Phim không tên" }
            val rawPoster = item.optString("poster_url").ifBlank { item.optString("thumb_url") }
            val rawThumb = item.optString("thumb_url").ifBlank { item.optString("poster_url") }
            val poster = buildImageUrl(rawPoster, cdnDomain)
            val thumb = buildImageUrl(rawThumb, cdnDomain)
            val year = item.opt("year")?.toString()?.takeIf { it != "null" }.orEmpty()
            val quality = item.optString("quality").ifBlank { "FHD" }
            val lang = item.optString("lang").ifBlank { "Vietsub" }
            val episodeCurrent = item.optString("episode_current")
            val time = item.optString("time")
            val type = item.optString("type")

            return Movie(
                id = slug.ifBlank { title.hashCode().toString() },
                slug = slug,
                title = title,
                seriesTitle = title,
                originTitle = item.optString("origin_name"),
                poster = poster,
                thumb = thumb,
                year = year,
                quality = quality,
                lang = lang,
                episodeCurrent = episodeCurrent,
                time = time,
                type = type,
                source = "KKPhim",
                playbackEnabled = true
            )
        }

        fun parseStandardServers(episodesArray: JSONArray?, sourcePrefix: String = ""): List<EpisodeServer> {
            if (episodesArray == null) return emptyList()
            val servers = mutableListOf<EpisodeServer>()
            for (i in 0 until episodesArray.length()) {
                val srvObj = episodesArray.optJSONObject(i) ?: continue
                val rawName = srvObj.optString("server_name").ifBlank { "Server ${i + 1}" }
                val serverName = if (sourcePrefix.isNotBlank() && !rawName.contains(sourcePrefix, ignoreCase = true)) {
                    "$sourcePrefix · $rawName"
                } else {
                    rawName
                }
                val dataArr = srvObj.optJSONArray("server_data") ?: JSONArray()
                val episodeList = mutableListOf<EpisodeItem>()
                for (j in 0 until dataArr.length()) {
                    val epObj = dataArr.optJSONObject(j) ?: continue
                    val name = epObj.optString("name").ifBlank { "Tập ${j + 1}" }
                    val epSlug = epObj.optString("slug").ifBlank { j.toString() }
                    val linkM3u8 = epObj.optString("link_m3u8").trim()
                    val linkEmbed = epObj.optString("link_embed").trim()
                    val stream = linkM3u8.ifBlank { linkEmbed }
                    if (stream.isNotBlank()) {
                        episodeList.add(
                            EpisodeItem(
                                index = episodeList.size,
                                name = name,
                                slug = epSlug,
                                m3u8 = stream,
                                fallbackEmbed = linkEmbed
                            )
                        )
                    }
                }
                if (episodeList.isNotEmpty()) {
                    servers.add(
                        EpisodeServer(
                            serverName = serverName,
                            episodes = episodeList
                        )
                    )
                }
            }
            return servers
        }

        fun parseNguoncServers(episodesArray: JSONArray?, sourcePrefix: String = "Nguonc"): List<EpisodeServer> {
            if (episodesArray == null) return emptyList()
            val servers = mutableListOf<EpisodeServer>()
            for (i in 0 until episodesArray.length()) {
                val srvObj = episodesArray.optJSONObject(i) ?: continue
                val rawName = srvObj.optString("server_name").ifBlank { "Server ${i + 1}" }
                val serverName = if (sourcePrefix.isNotBlank() && !rawName.contains(sourcePrefix, ignoreCase = true)) {
                    "$sourcePrefix · $rawName"
                } else {
                    rawName
                }
                val itemsArr = srvObj.optJSONArray("items") ?: srvObj.optJSONArray("server_data") ?: JSONArray()
                val episodeList = mutableListOf<EpisodeItem>()
                for (j in 0 until itemsArr.length()) {
                    val epObj = itemsArr.optJSONObject(j) ?: continue
                    val name = epObj.optString("name").ifBlank { "Tập ${j + 1}" }
                    val epSlug = epObj.optString("slug").ifBlank { j.toString() }
                    val linkM3u8 = epObj.optString("m3u8").ifBlank { epObj.optString("link_m3u8") }.trim()
                    val linkEmbed = epObj.optString("embed").ifBlank { epObj.optString("link_embed") }.trim()
                    val stream = linkM3u8.ifBlank { linkEmbed }
                    if (stream.isNotBlank()) {
                        episodeList.add(
                            EpisodeItem(
                                index = episodeList.size,
                                name = name,
                                slug = epSlug,
                                m3u8 = stream,
                                fallbackEmbed = linkEmbed
                            )
                        )
                    }
                }
                if (episodeList.isNotEmpty()) {
                    servers.add(
                        EpisodeServer(
                            serverName = serverName,
                            episodes = episodeList
                        )
                    )
                }
            }
            return servers
        }
    }

    data class MovieListResponse(
        val movies: List<Movie>,
        val cdn: String = DEFAULT_CDN,
        val title: String = "Danh sách phim",
        val totalItems: Int = movies.size
    )

    suspend fun getHome(): MovieListResponse = withContext(Dispatchers.IO) {
        val res = fetchJson("$BASE_URL/v1/api/home")
        val data = res.optJSONObject("data") ?: JSONObject()
        val cdn = data.optString("APP_DOMAIN_CDN_IMAGE").ifBlank { DEFAULT_CDN }
        val movies = parseItemsArray(data.optJSONArray("items"), cdn)
        MovieListResponse(
            movies = movies,
            cdn = cdn,
            title = "Trending / Nổi bật"
        )
    }

    suspend fun getTrending(page: Int = 1): MovieListResponse = withContext(Dispatchers.IO) {
        val pageNum = if (page > 0) page else 1
        if (pageNum == 1) {
            return@withContext getHome()
        }
        val category = if (pageNum % 2 == 0) "phim-bo" else "phim-le"
        val targetPage = pageNum / 2 + 1
        val res = fetchJson("$BASE_URL/v1/api/danh-sach/$category?page=$targetPage")
        val data = res.optJSONObject("data") ?: JSONObject()
        val cdn = data.optString("APP_DOMAIN_CDN_IMAGE").ifBlank { DEFAULT_CDN }
        val movies = parseItemsArray(data.optJSONArray("items"), cdn)
        MovieListResponse(
            movies = movies,
            cdn = cdn,
            title = "Trending / Nổi bật"
        )
    }

    suspend fun getNewUpdated(page: Int = 1): MovieListResponse = withContext(Dispatchers.IO) {
        val pageNum = if (page > 0) page else 1
        val res = fetchJson("$BASE_URL/v1/api/danh-sach/phim-moi-cap-nhat?page=$pageNum")
        val data = res.optJSONObject("data") ?: JSONObject()
        val cdn = data.optString("APP_DOMAIN_CDN_IMAGE").ifBlank { DEFAULT_CDN }
        val movies = parseItemsArray(data.optJSONArray("items"), cdn)
        MovieListResponse(
            movies = movies,
            cdn = cdn,
            title = "Phim mới cập nhật"
        )
    }

    suspend fun getMoviesByCategory(
        categorySlug: String,
        page: Int = 1,
        limit: Int = 24
    ): MovieListResponse = withContext(Dispatchers.IO) {
        val slug = categorySlug.trim().ifBlank { "phim-bo" }
        val pageNum = if (page > 0) page else 1
        val limitNum = if (limit > 0) limit else 24
        val encoded = URLEncoder.encode(slug, "UTF-8")
        val res = fetchJson("$BASE_URL/v1/api/danh-sach/$encoded?page=$pageNum&limit=$limitNum")
        val data = res.optJSONObject("data") ?: JSONObject()
        val cdn = data.optString("APP_DOMAIN_CDN_IMAGE").ifBlank { DEFAULT_CDN }
        val movies = parseItemsArray(data.optJSONArray("items"), cdn)
        MovieListResponse(
            movies = movies,
            cdn = cdn,
            title = data.optString("titlePage").ifBlank { "Danh sách phim" }
        )
    }

    suspend fun getMoviesByGenre(
        genreSlug: String,
        page: Int = 1,
        limit: Int = 24
    ): MovieListResponse = withContext(Dispatchers.IO) {
        val slug = genreSlug.trim().ifBlank { "hanh-dong" }
        val pageNum = if (page > 0) page else 1
        val limitNum = if (limit > 0) limit else 24
        val encoded = URLEncoder.encode(slug, "UTF-8")
        val res = fetchJson("$BASE_URL/v1/api/the-loai/$encoded?page=$pageNum&limit=$limitNum")
        val data = res.optJSONObject("data") ?: JSONObject()
        val cdn = data.optString("APP_DOMAIN_CDN_IMAGE").ifBlank { DEFAULT_CDN }
        val movies = parseItemsArray(data.optJSONArray("items"), cdn)
        MovieListResponse(
            movies = movies,
            cdn = cdn,
            title = data.optString("titlePage").ifBlank { "Thể loại phim" }
        )
    }

    suspend fun getMoviesByCountry(
        countrySlug: String,
        page: Int = 1,
        limit: Int = 24
    ): MovieListResponse = withContext(Dispatchers.IO) {
        val slug = countrySlug.trim().ifBlank { "han-quoc" }
        val pageNum = if (page > 0) page else 1
        val limitNum = if (limit > 0) limit else 24
        val encoded = URLEncoder.encode(slug, "UTF-8")
        val res = fetchJson("$BASE_URL/v1/api/quoc-gia/$encoded?page=$pageNum&limit=$limitNum")
        val data = res.optJSONObject("data") ?: JSONObject()
        val cdn = data.optString("APP_DOMAIN_CDN_IMAGE").ifBlank { DEFAULT_CDN }
        val movies = parseItemsArray(data.optJSONArray("items"), cdn)
        MovieListResponse(
            movies = movies,
            cdn = cdn,
            title = data.optString("titlePage").ifBlank { "Quốc gia" }
        )
    }

    suspend fun searchMovies(
        keyword: String,
        limit: Int = 20,
        page: Int = 1
    ): MovieListResponse = withContext(Dispatchers.IO) {
        val kw = keyword.trim()
        if (kw.isEmpty()) {
            return@withContext MovieListResponse(movies = emptyList(), title = "")
        }
        val limitNum = if (limit > 0) limit else 20
        val pageNum = if (page > 0) page else 1
        val encoded = URLEncoder.encode(kw, "UTF-8")
        val res = fetchJson("$BASE_URL/v1/api/tim-kiem?keyword=$encoded&limit=$limitNum&page=$pageNum")
        val data = res.optJSONObject("data") ?: JSONObject()
        val cdn = data.optString("APP_DOMAIN_CDN_IMAGE").ifBlank { DEFAULT_CDN }
        val movies = parseItemsArray(data.optJSONArray("items"), cdn)
        val total = data.optJSONObject("params")
            ?.optJSONObject("pagination")
            ?.optInt("totalItems", movies.size) ?: movies.size
        MovieListResponse(
            movies = movies,
            cdn = cdn,
            title = kw,
            totalItems = total
        )
    }

    suspend fun getMovieDetail(slug: String): MovieDetailResponse = withContext(Dispatchers.IO) {
        val cleanSlug = slug.trim()
        if (cleanSlug.isEmpty()) {
            throw IllegalArgumentException("Slug phim không hợp lệ.")
        }
        val encoded = URLEncoder.encode(cleanSlug, "UTF-8")

        coroutineScope {
            val kkphimDeferred = async {
                try {
                    fetchJson("$BASE_URL/phim/$encoded", REQUEST_TIMEOUT_MS)
                } catch (_: Exception) {
                    null
                }
            }
            val ophimDeferred = async {
                try {
                    fetchJson("$OPHIM_BASE_URL/phim/$encoded", BACKUP_SOURCE_TIMEOUT_MS)
                } catch (_: Exception) {
                    null
                }
            }
            val nguoncDeferred = async {
                try {
                    fetchJson("$NGUONC_BASE_URL/api/film/$encoded", BACKUP_SOURCE_TIMEOUT_MS)
                } catch (_: Exception) {
                    null
                }
            }

            val kkphimRes = kkphimDeferred.await()
            val ophimRes = ophimDeferred.await()
            val nguoncRes = nguoncDeferred.await()

            val primaryRes = kkphimRes ?: ophimRes
            val primaryMovieObj = primaryRes?.optJSONObject("movie") ?: nguoncRes?.optJSONObject("movie")
                ?: throw IllegalStateException("Không tìm thấy thông tin phim.")

            val primaryCdn = if (kkphimRes != null) DEFAULT_CDN else OPHIM_CDN
            val baseMovie = normalizeMovie(primaryMovieObj, primaryCdn)
                ?: throw IllegalStateException("Không thể phân tích thông tin phim.")

            val actors = parseStringOrNameArray(primaryMovieObj.optJSONArray("actor"))
                .ifEmpty {
                    val casts = primaryMovieObj.optString("casts")
                    if (casts.isNotBlank() && casts != "null") {
                        casts.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    } else emptyList()
                }
            val categories = parseStringOrNameArray(primaryMovieObj.optJSONArray("category"))
            val countries = parseStringOrNameArray(primaryMovieObj.optJSONArray("country"))

            val enrichedMovie = baseMovie.copy(
                content = primaryMovieObj.optString("content").ifBlank { primaryMovieObj.optString("description") },
                actors = actors,
                categories = categories,
                countries = countries,
                episodeTotal = primaryMovieObj.optString("episode_total").ifBlank { primaryMovieObj.optString("total_episodes") },
                status = primaryMovieObj.optString("status")
            )

            val allServers = mutableListOf<EpisodeServer>()
            val seenStreamFirstUrls = mutableSetOf<String>()

            fun appendUniqueServers(candidateServers: List<EpisodeServer>) {
                for (srv in candidateServers) {
                    val firstStream = srv.episodes.firstOrNull()?.m3u8?.trim().orEmpty()
                    if (firstStream.isNotBlank() && seenStreamFirstUrls.add(firstStream)) {
                        allServers.add(srv)
                    } else if (firstStream.isBlank() && srv.episodes.isNotEmpty()) {
                        allServers.add(srv)
                    }
                }
            }

            if (kkphimRes != null) {
                appendUniqueServers(parseStandardServers(kkphimRes.optJSONArray("episodes"), ""))
            }
            if (ophimRes != null) {
                appendUniqueServers(parseStandardServers(ophimRes.optJSONArray("episodes"), "OPhim"))
            }
            if (nguoncRes != null) {
                val nguoncEpArr = nguoncRes.optJSONObject("movie")?.optJSONArray("episodes")
                    ?: nguoncRes.optJSONArray("episodes")
                appendUniqueServers(parseNguoncServers(nguoncEpArr, "Nguonc"))
            }

            val finalMovie = enrichedMovie.copy(serversList = allServers)
            MovieDetailResponse(
                movie = finalMovie,
                servers = allServers
            )
        }
    }

    private fun parseItemsArray(array: JSONArray?, cdn: String): List<Movie> {
        if (array == null) return emptyList()
        val list = mutableListOf<Movie>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
            val movie = normalizeMovie(item, cdn)
            if (movie != null) {
                list.add(movie)
            }
        }
        return list
    }

    private fun parseStringOrNameArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            when (item) {
                is JSONObject -> {
                    val name = item.optString("name").trim()
                    if (name.isNotEmpty()) result.add(name)
                }
                is String -> {
                    val str = item.trim()
                    if (str.isNotEmpty() && str != "null") result.add(str)
                }
            }
        }
        return result
    }

    private fun fetchJson(urlString: String, timeoutMs: Int = REQUEST_TIMEOUT_MS): JSONObject {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "BACUFlix-Android/3.0")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("API trả về mã lỗi HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
