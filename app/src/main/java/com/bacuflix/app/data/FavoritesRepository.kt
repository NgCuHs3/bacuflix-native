package com.bacuflix.app.data

import android.content.Context
import com.bacuflix.app.model.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favorites repository ported from js/services/favorites-service.js.
 */
class FavoritesRepository(context: Context) {
    companion object {
        private const val PREFS_NAME = "bacuflix_favorites_prefs"
        private const val STORAGE_KEY = "kkphim_favorites"
        private const val MAX_FAVORITES = 200
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow<List<Movie>>(emptyList())
    val favorites: StateFlow<List<Movie>> = _favorites.asStateFlow()

    private val _favoriteKeys = MutableStateFlow<Set<String>>(emptySet())
    val favoriteKeys: StateFlow<Set<String>> = _favoriteKeys.asStateFlow()

    init {
        loadFromPrefs()
    }

    fun getAll(): List<Movie> = _favorites.value

    fun isFavorite(slugOrId: String?): Boolean {
        if (slugOrId.isNullOrBlank()) return false
        return _favoriteKeys.value.contains(slugOrId)
    }

    @Synchronized
    fun addFavorite(movie: Movie): Boolean {
        val key = movie.effectiveKey
        if (key.isBlank()) return false
        val current = _favorites.value.toMutableList()
        current.removeAll { it.effectiveKey == key }
        val entry = movie.copy(
            slug = key,
            id = movie.id.ifBlank { key },
            playbackEnabled = true,
            addedAt = System.currentTimeMillis()
        )
        current.add(0, entry)
        val trimmed = current.take(MAX_FAVORITES)
        saveFavorites(trimmed)
        return true
    }

    @Synchronized
    fun removeFavorite(slugOrId: String?): Boolean {
        if (slugOrId.isNullOrBlank()) return false
        val filtered = _favorites.value.filter { it.effectiveKey != slugOrId }
        saveFavorites(filtered)
        return true
    }

    @Synchronized
    fun toggleFavorite(movie: Movie): Boolean {
        val key = movie.effectiveKey
        if (key.isBlank()) return false
        return if (isFavorite(key)) {
            removeFavorite(key)
            false
        } else {
            addFavorite(movie)
            true
        }
    }

    private fun loadFromPrefs() {
        try {
            val raw = prefs.getString(STORAGE_KEY, null)
            if (raw.isNullOrBlank()) {
                _favorites.value = emptyList()
                _favoriteKeys.value = emptySet()
                return
            }
            val arr = JSONArray(raw)
            val list = mutableListOf<Movie>()
            val keys = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val slug = obj.optString("slug").ifBlank { obj.optString("id") }
                if (slug.isBlank()) continue
                val title = obj.optString("title").ifBlank { obj.optString("name") }.ifBlank { "Phim" }
                val poster = obj.optString("poster").ifBlank { obj.optString("poster_url") }.ifBlank { CatalogSources.FALLBACK_POSTER }
                val thumb = obj.optString("thumb").ifBlank { obj.optString("thumb_url") }.ifBlank { poster }
                val movie = Movie(
                    id = slug,
                    slug = slug,
                    title = title,
                    seriesTitle = title,
                    originTitle = obj.optString("originTitle"),
                    poster = poster,
                    thumb = thumb,
                    year = obj.optString("year"),
                    quality = obj.optString("quality").ifBlank { "FHD" },
                    lang = obj.optString("lang").ifBlank { "Vietsub" },
                    episodeCurrent = obj.optString("episode_current").ifBlank { obj.optString("episodeCurrent") },
                    m3u8 = obj.optString("m3u8"),
                    source = obj.optString("source").ifBlank { "KKPhim" },
                    playbackEnabled = true,
                    addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                )
                list.add(movie)
                keys.add(slug)
            }
            _favorites.value = list
            _favoriteKeys.value = keys
        } catch (_: Exception) {
            _favorites.value = emptyList()
            _favoriteKeys.value = emptySet()
        }
    }

    private fun saveFavorites(list: List<Movie>) {
        _favorites.value = list
        _favoriteKeys.value = list.map { it.effectiveKey }.filter { it.isNotBlank() }.toSet()
        try {
            val arr = JSONArray()
            for (movie in list) {
                arr.put(
                    JSONObject().apply {
                        put("slug", movie.effectiveKey)
                        put("id", movie.id)
                        put("name", movie.title)
                        put("title", movie.title)
                        put("originTitle", movie.originTitle)
                        put("poster_url", movie.poster)
                        put("poster", movie.poster)
                        put("thumb_url", movie.thumb)
                        put("thumb", movie.thumb)
                        put("year", movie.year)
                        put("quality", movie.quality)
                        put("lang", movie.lang)
                        put("episode_current", movie.episodeCurrent)
                        put("m3u8", movie.m3u8)
                        put("source", movie.source)
                        put("playbackEnabled", true)
                        put("addedAt", if (movie.addedAt > 0) movie.addedAt else System.currentTimeMillis())
                    }
                )
            }
            prefs.edit().putString(STORAGE_KEY, arr.toString()).apply()
        } catch (_: Exception) {}
    }
}
