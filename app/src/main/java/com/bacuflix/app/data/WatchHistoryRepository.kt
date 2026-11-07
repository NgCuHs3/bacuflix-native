package com.bacuflix.app.data

import android.content.Context
import com.bacuflix.app.model.EpisodeItem
import com.bacuflix.app.model.EpisodeProgressEntry
import com.bacuflix.app.model.EpisodeResumeInfo
import com.bacuflix.app.model.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Watch history repository ported from js/services/watch-history-service.js.
 */
class WatchHistoryRepository(context: Context) {
    companion object {
        private const val PREFS_NAME = "bacuflix_history_prefs"
        private const val HISTORY_STORAGE_KEY = "kkphim_watch_history"
        private const val MAX_HISTORY_ITEMS = 40
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _history = MutableStateFlow<List<Movie>>(emptyList())
    val history: StateFlow<List<Movie>> = _history.asStateFlow()

    private val episodeProgressMap = mutableMapOf<String, MutableMap<String, EpisodeProgressEntry>>()

    init {
        loadFromPrefs()
    }

    fun getAll(): List<Movie> = _history.value

    fun getItem(slugOrId: String?): Movie? {
        if (slugOrId.isNullOrBlank()) return null
        return _history.value.firstOrNull { it.slug == slugOrId || it.id == slugOrId }
    }

    fun getEpisodeResumeInfo(slugOrId: String?, episodeIndex: Int? = null): EpisodeResumeInfo? {
        if (slugOrId.isNullOrBlank()) return null
        val item = getItem(slugOrId) ?: return null
        val targetEpIdx = episodeIndex ?: item.currentEpisodeIndex
        val epKey = "ep_$targetEpIdx"

        val epData = episodeProgressMap[item.effectiveKey]?.get(epKey)
        val curTime: Int
        val durTime: Int
        val epName: String

        if (epData != null) {
            curTime = epData.currentTime
            durTime = epData.duration
            epName = epData.episodeName.ifBlank { item.episodeName.ifBlank { "Tập ${targetEpIdx + 1}" } }
        } else if (episodeIndex == null || item.currentEpisodeIndex == targetEpIdx) {
            curTime = item.currentTime
            durTime = item.duration
            epName = item.episodeName.ifBlank { "Tập ${targetEpIdx + 1}" }
        } else {
            return null
        }

        if (curTime <= 5) return null
        if (durTime > 60 && curTime >= durTime - 12) return null

        val progressPercent = if (durTime > 0) {
            max(5, min(98, ((curTime.toDouble() / durTime.toDouble()) * 100.0).roundToInt()))
        } else {
            item.progressPercent.takeIf { it > 0 } ?: 15
        }

        return EpisodeResumeInfo(
            slug = item.effectiveKey,
            episodeIndex = targetEpIdx,
            episodeName = epName,
            currentTime = curTime,
            duration = durTime,
            progressPercent = progressPercent
        )
    }

    @Synchronized
    fun enrichMetadata(movie: Movie): Boolean {
        val slug = movie.effectiveKey
        if (slug.isBlank()) return false
        val list = _history.value.toMutableList()
        val idx = list.indexOfFirst { it.slug == slug || it.id == slug }
        if (idx < 0) return false

        val existing = list[idx]
        var changed = false
        var updated = existing

        if (movie.categories.isNotEmpty() && existing.categories.isEmpty()) {
            updated = updated.copy(categories = movie.categories)
            changed = true
        }
        if (movie.countries.isNotEmpty() && existing.countries.isEmpty()) {
            updated = updated.copy(countries = movie.countries)
            changed = true
        }
        if (movie.actors.isNotEmpty() && existing.actors.isEmpty()) {
            updated = updated.copy(actors = movie.actors.take(10))
            changed = true
        }
        if (movie.originTitle.isNotBlank() && existing.originTitle.isBlank()) {
            updated = updated.copy(originTitle = movie.originTitle)
            changed = true
        }
        if (movie.type.isNotBlank() && existing.type.isBlank()) {
            updated = updated.copy(type = movie.type)
            changed = true
        }

        if (changed) {
            list[idx] = updated
            saveHistoryList(list)
        }
        return changed
    }

    @Synchronized
    fun saveWatchProgress(movie: Movie, currentTimeSec: Int, durationSec: Int): Boolean {
        val slug = movie.effectiveKey
        if (slug.isBlank()) return false
        val list = _history.value.toMutableList()
        val existingIdx = list.indexOfFirst { it.slug == slug || it.id == slug }
        val prev = if (existingIdx >= 0) list[existingIdx] else null

        val epIndex = movie.currentEpisodeIndex.takeIf { it >= 0 } ?: (prev?.currentEpisodeIndex ?: 0)
        val epKey = "ep_$epIndex"
        val prevEpMap = episodeProgressMap[slug] ?: mutableMapOf()
        val prevEpData = prevEpMap[epKey]

        val curTime = when {
            currentTimeSec > 0 -> currentTimeSec
            prevEpData != null && prevEpData.currentTime > 0 -> prevEpData.currentTime
            currentTimeSec == 0 -> 0
            prev != null && prev.currentTime > 0 -> prev.currentTime
            else -> 120
        }

        val durTime = when {
            durationSec > 0 && (currentTimeSec > 0 || prevEpData == null || prevEpData.duration <= 0) -> durationSec
            prevEpData != null && prevEpData.duration > 0 -> prevEpData.duration
            prev != null && prev.duration > 0 -> prev.duration
            else -> 2700
        }

        val progress = if (durTime > 0) {
            max(5, min(98, ((curTime.toDouble() / durTime.toDouble()) * 100.0).roundToInt()))
        } else 15
        val remainingMin = max(1, (max(0, durTime - curTime) / 60.0).roundToInt())

        val seriesTitle = movie.seriesTitle.ifBlank {
            prev?.seriesTitle ?: movie.title.substringBefore(" - ").ifBlank { "Phim" }
        }
        val episodeName = movie.episodeName
            .ifBlank { movie.episodeCurrent }
            .ifBlank { prevEpData?.episodeName.orEmpty() }
            .ifBlank { prev?.episodeName.orEmpty() }
            .ifBlank { "Tập ${epIndex + 1}" }

        prevEpMap[epKey] = EpisodeProgressEntry(
            episodeIndex = epIndex,
            episodeName = episodeName,
            currentTime = curTime,
            duration = durTime,
            progress = progress,
            updatedAt = System.currentTimeMillis()
        )
        episodeProgressMap[slug] = prevEpMap

        val entry = Movie(
            id = slug,
            slug = slug,
            title = seriesTitle,
            seriesTitle = seriesTitle,
            originTitle = movie.originTitle.ifBlank { prev?.originTitle.orEmpty() },
            episodeName = episodeName,
            episodeCurrent = episodeName,
            currentEpisodeIndex = epIndex,
            poster = movie.poster.ifBlank { prev?.poster ?: CatalogSources.FALLBACK_POSTER },
            thumb = movie.thumb.ifBlank { movie.poster }.ifBlank { prev?.thumb ?: CatalogSources.FALLBACK_POSTER },
            year = movie.year.ifBlank { prev?.year ?: "2026" },
            quality = movie.quality.ifBlank { prev?.quality ?: "FHD" },
            lang = movie.lang.ifBlank { prev?.lang ?: "Vietsub" },
            type = movie.type.ifBlank { prev?.type.orEmpty() },
            categories = movie.categories.ifEmpty { prev?.categories.orEmpty() },
            countries = movie.countries.ifEmpty { prev?.countries.orEmpty() },
            actors = movie.actors.ifEmpty { prev?.actors.orEmpty() }.take(10),
            m3u8 = movie.m3u8.ifBlank { prev?.m3u8.orEmpty() },
            fallbackEmbed = movie.fallbackEmbed.ifBlank { prev?.fallbackEmbed.orEmpty() },
            serverName = movie.serverName.ifBlank { prev?.serverName ?: "Vietsub" },
            episodesList = movie.episodesList.ifEmpty { prev?.episodesList.orEmpty() }.take(120),
            currentTime = curTime,
            duration = durTime,
            progressPercent = progress,
            remainingText = "Còn $remainingMin phút",
            playbackEnabled = true,
            source = movie.source.ifBlank { "KKPhim" },
            updatedAt = System.currentTimeMillis()
        )

        if (existingIdx >= 0) {
            list.removeAt(existingIdx)
        }
        list.add(0, entry)
        saveHistoryList(list.take(MAX_HISTORY_ITEMS))
        return true
    }

    @Synchronized
    fun removeWatchHistory(slugOrId: String?): Boolean {
        if (slugOrId.isNullOrBlank()) return false
        episodeProgressMap.remove(slugOrId)
        val filtered = _history.value.filter { it.slug != slugOrId && it.id != slugOrId }
        saveHistoryList(filtered)
        return true
    }

    @Synchronized
    fun clearWatchHistory() {
        episodeProgressMap.clear()
        saveHistoryList(emptyList())
    }

    private fun loadFromPrefs() {
        try {
            val raw = prefs.getString(HISTORY_STORAGE_KEY, null)
            if (raw.isNullOrBlank()) {
                _history.value = emptyList()
                return
            }
            val arr = JSONArray(raw)
            val list = mutableListOf<Movie>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val slug = obj.optString("slug").ifBlank { obj.optString("id") }
                if (slug.isBlank()) continue

                val epProgObj = obj.optJSONObject("episodeProgress")
                if (epProgObj != null) {
                    val map = mutableMapOf<String, EpisodeProgressEntry>()
                    val keys = epProgObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val epObj = epProgObj.optJSONObject(k) ?: continue
                        map[k] = EpisodeProgressEntry(
                            episodeIndex = epObj.optInt("episodeIndex", 0),
                            episodeName = epObj.optString("episodeName", "Tập 1"),
                            currentTime = epObj.optInt("currentTime", 0),
                            duration = epObj.optInt("duration", 2700),
                            progress = epObj.optInt("progress", 15),
                            updatedAt = epObj.optLong("updatedAt", 0L)
                        )
                    }
                    episodeProgressMap[slug] = map
                }

                val episodesList = mutableListOf<EpisodeItem>()
                val epArr = obj.optJSONArray("episodesList")
                if (epArr != null) {
                    for (j in 0 until epArr.length()) {
                        val eObj = epArr.optJSONObject(j) ?: continue
                        episodesList.add(
                            EpisodeItem(
                                index = eObj.optInt("index", j),
                                name = eObj.optString("name", "Tập ${j + 1}"),
                                slug = eObj.optString("slug", j.toString()),
                                m3u8 = eObj.optString("m3u8"),
                                fallbackEmbed = eObj.optString("fallbackEmbed")
                            )
                        )
                    }
                }

                val seriesTitle = obj.optString("seriesTitle").ifBlank { obj.optString("title", "Phim") }
                list.add(
                    Movie(
                        id = slug,
                        slug = slug,
                        title = seriesTitle,
                        seriesTitle = seriesTitle,
                        originTitle = obj.optString("originTitle"),
                        episodeName = obj.optString("episodeName", "Tập 1"),
                        episodeCurrent = obj.optString("episodeCurrent", "Tập 1"),
                        currentEpisodeIndex = obj.optInt("currentEpisodeIndex", 0),
                        poster = obj.optString("poster", CatalogSources.FALLBACK_POSTER),
                        thumb = obj.optString("thumb", CatalogSources.FALLBACK_POSTER),
                        year = obj.optString("year", "2026"),
                        quality = obj.optString("quality", "FHD"),
                        lang = obj.optString("lang", "Vietsub"),
                        type = obj.optString("type"),
                        categories = jsonArrayToList(obj.optJSONArray("categories")),
                        countries = jsonArrayToList(obj.optJSONArray("countries")),
                        actors = jsonArrayToList(obj.optJSONArray("actors")),
                        m3u8 = obj.optString("m3u8"),
                        fallbackEmbed = obj.optString("fallbackEmbed"),
                        serverName = obj.optString("serverName", "Vietsub"),
                        episodesList = episodesList,
                        currentTime = obj.optInt("currentTime", 0),
                        duration = obj.optInt("duration", 2700),
                        progressPercent = obj.optInt("progressPercent", obj.optInt("progress", 15)),
                        remainingText = obj.optString("remainingText", "Đang xem"),
                        playbackEnabled = true,
                        source = obj.optString("source", "KKPhim"),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            _history.value = list
        } catch (_: Exception) {
            _history.value = emptyList()
        }
    }

    private fun saveHistoryList(list: List<Movie>) {
        _history.value = list
        try {
            val arr = JSONArray()
            for (movie in list) {
                val slug = movie.effectiveKey
                val epProgObj = JSONObject()
                episodeProgressMap[slug]?.forEach { (k, v) ->
                    epProgObj.put(
                        k,
                        JSONObject().apply {
                            put("episodeIndex", v.episodeIndex)
                            put("episodeName", v.episodeName)
                            put("currentTime", v.currentTime)
                            put("duration", v.duration)
                            put("progress", v.progress)
                            put("updatedAt", v.updatedAt)
                        }
                    )
                }

                val epListArr = JSONArray()
                movie.episodesList.forEach { ep ->
                    epListArr.put(
                        JSONObject().apply {
                            put("index", ep.index)
                            put("name", ep.name)
                            put("slug", ep.slug)
                            put("m3u8", ep.m3u8)
                            put("fallbackEmbed", ep.fallbackEmbed)
                        }
                    )
                }

                arr.put(
                    JSONObject().apply {
                        put("id", slug)
                        put("slug", slug)
                        put("title", movie.title)
                        put("seriesTitle", movie.seriesTitle)
                        put("originTitle", movie.originTitle)
                        put("episodeName", movie.episodeName)
                        put("episodeCurrent", movie.episodeCurrent)
                        put("currentEpisodeIndex", movie.currentEpisodeIndex)
                        put("poster", movie.poster)
                        put("thumb", movie.thumb)
                        put("year", movie.year)
                        put("quality", movie.quality)
                        put("lang", movie.lang)
                        put("type", movie.type)
                        put("categories", JSONArray(movie.categories))
                        put("countries", JSONArray(movie.countries))
                        put("actors", JSONArray(movie.actors))
                        put("m3u8", movie.m3u8)
                        put("fallbackEmbed", movie.fallbackEmbed)
                        put("serverName", movie.serverName)
                        put("episodesList", epListArr)
                        put("episodeProgress", epProgObj)
                        put("currentTime", movie.currentTime)
                        put("duration", movie.duration)
                        put("progress", movie.progressPercent)
                        put("progressPercent", movie.progressPercent)
                        put("remainingText", movie.remainingText)
                        put("playbackEnabled", true)
                        put("source", movie.source)
                        put("updatedAt", movie.updatedAt)
                    }
                )
            }
            prefs.edit().putString(HISTORY_STORAGE_KEY, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val res = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i).trim()
            if (s.isNotEmpty()) res.add(s)
        }
        return res
    }
}
