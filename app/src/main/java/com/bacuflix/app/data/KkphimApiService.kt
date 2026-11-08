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
 * Multi-Source Vietnamese Movie API client combining:
 * 1. KKPhim (https://phimapi.com)
 * 2. VSMov / Phim4K (https://vsmov.com/api) — replaces blocked ophim1.com with 4K/FHD HLS streams
 * 3. NguonC (https://phim.nguonc.com) — with StreamC HLS bootstrap resolver
 */
class KkphimApiService {
    companion object {
        const val BASE_URL = "https://phimapi.com"
        const val DEFAULT_CDN = "https://phimimg.com"
        const val VSMOV_BASE_URL = "https://vsmov.com/api"
        const val VSMOV_CDN = "https://vsmov.com"
        const val NGUONC_BASE_URL = "https://phim.nguonc.com"
        private const val REQUEST_TIMEOUT_MS = 12000
        private const val BACKUP_SOURCE_TIMEOUT_MS = 6000
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; BACUFlix/3.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        private val VSMOV_EMBED_REGEX =
            Regex("""^(https?://[^/]*streamvsmov\.com)/video/([0-9a-fA-F-]+).*$""", RegexOption.IGNORE_CASE)

        /**
         * Converts a VSMov embed link (`https://v15.streamvsmov.com/video/{uuid}`) or
         * `?url=https://...m3u8` wrapper into a direct playable `.m3u8` URL.
         */
        fun extractDirectM3u8FromEmbed(embedUrl: String?): String {
            val clean = embedUrl?.trim().orEmpty()
            if (clean.isEmpty()) return ""
            val vsmovMatch = VSMOV_EMBED_REGEX.matchEntire(clean)
            if (vsmovMatch != null) {
                val baseUrl = vsmovMatch.groupValues[1].trimEnd('/')
                val videoHash = vsmovMatch.groupValues[2]
                return "$baseUrl/stream/$videoHash/master.m3u8"
            }
            if (clean.contains("url=", ignoreCase = true)) {
                val afterUrl = clean.substringAfter("url=", "").substringBefore("&").trim()
                if (afterUrl.contains(".m3u8", ignoreCase = true)) {
                    return try {
                        java.net.URLDecoder.decode(afterUrl, "UTF-8")
                    } catch (_: Exception) {
                        afterUrl
                    }
                }
            }
            return ""
        }

        /**
         * Resolves a NguonC StreamC embed URL (`https://embed18.streamc.xyz/embed.php?hash=...`)
         * into a direct HLS playlist URL via its JSON bootstrap endpoint.
         */
        fun resolveStreamcBootstrapSync(embedUrl: String, timeoutMs: Int = 6000): String? {
            val clean = embedUrl.trim()
            if (!clean.contains("streamc.xyz", ignoreCase = true) || !clean.contains("hash=")) {
                return null
            }
            return try {
                val parsed = URL(clean)
                val origin = "${parsed.protocol}://${parsed.host}"
                val conn = (parsed.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    doOutput = true
                    setRequestProperty("User-Agent", BROWSER_UA)
                    setRequestProperty("Origin", origin)
                    setRequestProperty("Referer", "$origin/")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val payload =
                        """{"action":"bootstrap","referrer":"https://phim.nguonc.com/","frame_origins":["https://phim.nguonc.com"],"request_grant":true,"playlist_format":"hls","pretty_url":true,"path_chunks":true,"bootstrap_format":"json"}"""
                    conn.outputStream.use { os ->
                        os.write(payload.toByteArray(Charsets.UTF_8))
                    }
                    if (conn.responseCode !in 200..299) return null
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    if (json.optBoolean("turnstileEnabled", false)) return null
                    val preissued = json.optJSONObject("preissued") ?: return null
                    val playlist = preissued.optString("playlist").trim()
                    if (playlist.startsWith("http://") || playlist.startsWith("https://")) {
                        com.bacuflix.app.player.PngStrippingDataSource.activeStreamcOrigin = origin
                        playlist
                    } else {
                        null
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                null
            }
        }

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
                .ifBlank { item.optString("original_name") }
                .ifBlank { "Phim không tên" }
            val rawPoster = item.optString("poster_url").ifBlank { item.optString("thumb_url") }
            val rawThumb = item.optString("thumb_url").ifBlank { item.optString("poster_url") }
            val poster = buildImageUrl(rawPoster, cdnDomain)
            val thumb = buildImageUrl(rawThumb, cdnDomain)
            val year = item.opt("year")?.toString()?.takeIf { it != "null" }.orEmpty()
            val quality = item.optString("quality").ifBlank { "FHD" }
            val lang = item.optString("lang").ifBlank { item.optString("language") }.ifBlank { "Vietsub" }
            val episodeCurrent = item.optString("episode_current").ifBlank { item.optString("current_episode") }
            val time = item.optString("time")
            val type = item.optString("type")

            return Movie(
                id = slug.ifBlank { title.hashCode().toString() },
                slug = slug,
                title = title,
                seriesTitle = title,
                originTitle = item.optString("origin_name").ifBlank { item.optString("original_name") },
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
                val dataArr = srvObj.optJSONArray("server_data") ?: srvObj.optJSONArray("items") ?: JSONArray()
                val episodeList = mutableListOf<EpisodeItem>()
                for (j in 0 until dataArr.length()) {
                    val epObj = dataArr.optJSONObject(j) ?: continue
                    val name = epObj.optString("name").ifBlank { "Tập ${j + 1}" }
                    val epSlug = epObj.optString("slug").ifBlank { j.toString() }
                    val rawM3u8 = epObj.optString("link_m3u8").ifBlank { epObj.optString("m3u8") }.trim()
                    val linkEmbed = epObj.optString("link_embed").ifBlank { epObj.optString("embed") }.trim()
                    val linkM3u8 = rawM3u8.ifBlank { extractDirectM3u8FromEmbed(linkEmbed) }
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

        fun parseNguoncServers(episodesArray: JSONArray?, sourcePrefix: String = "NguonC"): List<EpisodeServer> {
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
                    val rawM3u8 = epObj.optString("m3u8").ifBlank { epObj.optString("link_m3u8") }.trim()
                    val linkEmbed = epObj.optString("embed").ifBlank { epObj.optString("link_embed") }.trim()
                    val linkM3u8 = rawM3u8.ifBlank { extractDirectM3u8FromEmbed(linkEmbed) }
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

        coroutineScope {
            val kkphimDeferred = async {
                try {
                    fetchJson("$BASE_URL/v1/api/tim-kiem?keyword=$encoded&limit=$limitNum&page=$pageNum")
                } catch (_: Exception) {
                    null
                }
            }
            val vsmovDeferred = async {
                try {
                    fetchJson("$VSMOV_BASE_URL/tim-kiem?keyword=$encoded&page=$pageNum", BACKUP_SOURCE_TIMEOUT_MS)
                } catch (_: Exception) {
                    null
                }
            }
            val nguoncDeferred = async {
                try {
                    fetchJson("$NGUONC_BASE_URL/api/films/search?keyword=$encoded&page=$pageNum", BACKUP_SOURCE_TIMEOUT_MS)
                } catch (_: Exception) {
                    null
                }
            }

            val kkphimRes = kkphimDeferred.await()
            val vsmovRes = vsmovDeferred.await()
            val nguoncRes = nguoncDeferred.await()

            val data = kkphimRes?.optJSONObject("data") ?: JSONObject()
            val cdn = data.optString("APP_DOMAIN_CDN_IMAGE").ifBlank { DEFAULT_CDN }
            val kkphimMovies = parseItemsArray(data.optJSONArray("items"), cdn)
            val vsmovMovies = parseItemsArray(
                vsmovRes?.optJSONArray("items") ?: vsmovRes?.optJSONObject("data")?.optJSONArray("items"),
                VSMOV_CDN
            )
            val nguoncMovies = parseItemsArray(
                nguoncRes?.optJSONArray("items"),
                NGUONC_BASE_URL
            )

            val merged = mutableListOf<Movie>()
            val seenSlugs = mutableSetOf<String>()
            for (m in kkphimMovies + vsmovMovies + nguoncMovies) {
                val key = m.slug.lowercase().trim()
                if (key.isNotEmpty() && seenSlugs.add(key)) {
                    merged.add(m)
                }
            }

            val total = data.optJSONObject("params")
                ?.optJSONObject("pagination")
                ?.optInt("totalItems", merged.size)
                ?.coerceAtLeast(merged.size) ?: merged.size

            MovieListResponse(
                movies = merged,
                cdn = cdn,
                title = kw,
                totalItems = total
            )
        }
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
            val vsmovDeferred = async {
                try {
                    fetchJson("$VSMOV_BASE_URL/phim/$encoded", BACKUP_SOURCE_TIMEOUT_MS)
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
            var vsmovRes = vsmovDeferred.await()
            var nguoncRes = nguoncDeferred.await()

            val primaryRes = kkphimRes ?: vsmovRes
            val primaryMovieObj = primaryRes?.optJSONObject("movie")
                ?: primaryRes?.optJSONObject("data")?.optJSONObject("movie")
                ?: nguoncRes?.optJSONObject("movie")
                ?: throw IllegalStateException("Không tìm thấy thông tin phim.")

            val primaryCdn = when {
                kkphimRes != null -> DEFAULT_CDN
                vsmovRes != null -> VSMOV_CDN
                else -> NGUONC_BASE_URL
            }
            val baseMovie = normalizeMovie(primaryMovieObj, primaryCdn)
                ?: throw IllegalStateException("Không thể phân tích thông tin phim.")

            // Try alternate year-suffixed or year-stripped slug for backup sources if initial slug missed
            val yearSuffixRegex = Regex("""-(19|20)\d{2}$""")
            val altSlug = if (yearSuffixRegex.containsMatchIn(cleanSlug)) {
                cleanSlug.replace(yearSuffixRegex, "")
            } else if (baseMovie.year.length == 4) {
                "$cleanSlug-${baseMovie.year}"
            } else {
                ""
            }

            if (altSlug.isNotBlank() && altSlug != cleanSlug) {
                val encodedAlt = URLEncoder.encode(altSlug, "UTF-8")
                val altVsmovDeferred = if (vsmovRes == null) {
                    async {
                        try {
                            fetchJson("$VSMOV_BASE_URL/phim/$encodedAlt", BACKUP_SOURCE_TIMEOUT_MS)
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else null
                val altNguoncDeferred = if (nguoncRes == null) {
                    async {
                        try {
                            fetchJson("$NGUONC_BASE_URL/api/film/$encodedAlt", BACKUP_SOURCE_TIMEOUT_MS)
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else null

                if (vsmovRes == null) vsmovRes = altVsmovDeferred?.await()
                if (nguoncRes == null) nguoncRes = altNguoncDeferred?.await()
            }

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
                appendUniqueServers(parseStandardServers(kkphimRes.optJSONArray("episodes"), "KKPhim"))
            }
            if (vsmovRes != null) {
                val vsmovEps = vsmovRes.optJSONArray("episodes")
                    ?: vsmovRes.optJSONObject("data")?.optJSONArray("episodes")
                appendUniqueServers(parseStandardServers(vsmovEps, "Phim4K"))
            }
            if (nguoncRes != null) {
                val nguoncEpArr = nguoncRes.optJSONObject("movie")?.optJSONArray("episodes")
                    ?: nguoncRes.optJSONArray("episodes")
                appendUniqueServers(parseNguoncServers(nguoncEpArr, "NguonC"))
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
