package com.bacuflix.app.data

import android.content.Context
import com.bacuflix.app.model.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favorites repository managing saved movies / bookmark list with persistence.
 */
class FavoritesRepository(context: Context) {
    companion object {
        private const val PREFS_NAME = "bacuflix_favorites_prefs"
        private const val STORAGE_KEY = "kkphim_favorites"
        private const val MAX_FAVORITES = 200

        fun sanitizeApiSlug(rawSlug: String?): String {
            val s = rawSlug?.trim().orEmpty()
            if (s.isEmpty() || s.startsWith("movie-")) return ""
            return s
        }

        fun normalizeMovieKey(rawSlug: String?, rawId: String?): String {
            val cleanSlug = sanitizeApiSlug(rawSlug)
            if (cleanSlug.isNotEmpty()) return cleanSlug
            val id = rawId?.trim().orEmpty()
            if (id.startsWith("movie-")) {
                return id.replace(Regex("^((?:movie-[a-zA-Z0-9]+))-\\d+$"), "$1")
            }
            return id
        }
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
        val cleanSlug = sanitizeApiSlug(slugOrId)
        val normKey = normalizeMovieKey(cleanSlug, slugOrId)
        val currentKeys = _favoriteKeys.value
        return currentKeys.contains(slugOrId) ||
            (cleanSlug.isNotBlank() && currentKeys.contains(cleanSlug)) ||
            (normKey.isNotBlank() && currentKeys.contains(normKey))
    }

    fun isFavorite(movie: Movie): Boolean {
        val cleanSlug = sanitizeApiSlug(movie.slug)
        val normKey = normalizeMovieKey(cleanSlug, movie.id.ifBlank { movie.effectiveKey })
        return isFavorite(movie.effectiveKey) ||
            isFavorite(movie.slug) ||
            isFavorite(movie.id) ||
            (cleanSlug.isNotBlank() && isFavorite(cleanSlug)) ||
            (normKey.isNotBlank() && isFavorite(normKey))
    }

    @Synchronized
    fun addFavorite(movie: Movie): Boolean {
        val realSlug = sanitizeApiSlug(movie.slug)
        val key = normalizeMovieKey(realSlug, movie.id.ifBlank { movie.effectiveKey })
        if (key.isBlank()) return false
        val current = _favorites.value.toMutableList()
        current.removeAll {
            it.effectiveKey == key || it.id == key ||
                (realSlug.isNotBlank() && it.slug == realSlug) ||
                (movie.effectiveKey.isNotBlank() && it.effectiveKey == movie.effectiveKey) ||
                (movie.id.isNotBlank() && it.id == movie.id)
        }
        val entry = movie.copy(
            slug = realSlug.ifBlank { movie.slug },
            id = key,
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
        val cleanSlug = sanitizeApiSlug(slugOrId)
        val normKey = normalizeMovieKey(cleanSlug, slugOrId)
        val filtered = _favorites.value.filter {
            it.effectiveKey != slugOrId &&
                it.id != slugOrId &&
                it.slug != slugOrId &&
                (normKey.isBlank() || (it.effectiveKey != normKey && it.id != normKey && it.slug != normKey)) &&
                (cleanSlug.isBlank() || (it.effectiveKey != cleanSlug && it.slug != cleanSlug))
        }
        saveFavorites(filtered)
        return true
    }

    @Synchronized
    fun toggleFavorite(movie: Movie): Boolean {
        val cleanSlug = sanitizeApiSlug(movie.slug)
        val key = normalizeMovieKey(cleanSlug, movie.id.ifBlank { movie.effectiveKey })
        if (key.isBlank()) return false
        return if (isFavorite(movie)) {
            removeFavorite(key)
            removeFavorite(movie.effectiveKey)
            if (cleanSlug.isNotBlank()) removeFavorite(cleanSlug)
            if (movie.slug.isNotBlank()) removeFavorite(movie.slug)
            if (movie.id.isNotBlank()) removeFavorite(movie.id)
            false
        } else {
            addFavorite(movie)
            true
        }
    }

    @Synchronized
    fun clearFavorites() {
        saveFavorites(emptyList())
    }

    fun exportToJsonArray(): JSONArray {
        val raw = prefs.getString(STORAGE_KEY, null)
        return try {
            if (!raw.isNullOrBlank()) JSONArray(raw) else JSONArray()
        } catch (_: Exception) {
            JSONArray()
        }
    }

    @Synchronized
    fun importFromJsonArray(arr: JSONArray?): Int {
        if (arr == null || arr.length() == 0) return 0
        var importedCount = 0
        val current = _favorites.value.toMutableList()
        for (i in (arr.length() - 1) downTo 0) {
            val obj = arr.optJSONObject(i) ?: continue
            val rawSlug = obj.optString("slug")
            val rawId = obj.optString("id").ifBlank { rawSlug }
            val realSlug = sanitizeApiSlug(rawSlug)
            val key = normalizeMovieKey(realSlug, rawId)
            if (key.isBlank()) continue
            val title = obj.optString("title").ifBlank { obj.optString("name") }.ifBlank { "Phim" }
            val poster = obj.optString("poster").ifBlank { obj.optString("poster_url") }.ifBlank { CatalogSources.FALLBACK_POSTER }
            val thumb = obj.optString("thumb").ifBlank { obj.optString("thumb_url") }.ifBlank { poster }
            val movie = Movie(
                id = key,
                slug = realSlug.ifBlank { rawSlug },
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
                fallbackEmbed = obj.optString("fallbackEmbed"),
                source = obj.optString("source").ifBlank { "KKPhim" },
                playbackEnabled = true,
                addedAt = obj.optLong("addedAt", System.currentTimeMillis())
            )
            current.removeAll { it.effectiveKey == key || it.id == key || (realSlug.isNotBlank() && it.slug == realSlug) }
            current.add(0, movie)
            importedCount++
        }
        saveFavorites(current.take(MAX_FAVORITES))
        return importedCount
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
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val rawSlug = obj.optString("slug")
                val rawId = obj.optString("id").ifBlank { rawSlug }
                val realSlug = sanitizeApiSlug(rawSlug)
                val key = normalizeMovieKey(realSlug, rawId)
                if (key.isBlank()) continue
                val title = obj.optString("title").ifBlank { obj.optString("name") }.ifBlank { "Phim" }
                val poster = obj.optString("poster").ifBlank { obj.optString("poster_url") }.ifBlank { CatalogSources.FALLBACK_POSTER }
                val thumb = obj.optString("thumb").ifBlank { obj.optString("thumb_url") }.ifBlank { poster }
                val movie = Movie(
                    id = key,
                    slug = realSlug.ifBlank { rawSlug },
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
                    fallbackEmbed = obj.optString("fallbackEmbed"),
                    source = obj.optString("source").ifBlank { "KKPhim" },
                    playbackEnabled = true,
                    addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                )
                list.add(movie)
            }
            _favorites.value = list
            _favoriteKeys.value = extractAllKeys(list)
        } catch (_: Exception) {
            _favorites.value = emptyList()
            _favoriteKeys.value = emptySet()
        }
    }

    private fun extractAllKeys(list: List<Movie>): Set<String> {
        val keys = mutableSetOf<String>()
        for (m in list) {
            if (m.effectiveKey.isNotBlank()) keys.add(m.effectiveKey)
            if (m.slug.isNotBlank()) keys.add(m.slug)
            if (m.id.isNotBlank()) keys.add(m.id)
            val cleanSlug = sanitizeApiSlug(m.slug)
            if (cleanSlug.isNotBlank()) keys.add(cleanSlug)
            val normKey = normalizeMovieKey(cleanSlug, m.id.ifBlank { m.effectiveKey })
            if (normKey.isNotBlank()) keys.add(normKey)
        }
        return keys
    }

    private fun saveFavorites(list: List<Movie>) {
        _favorites.value = list
        _favoriteKeys.value = extractAllKeys(list)
        try {
            val arr = JSONArray()
            for (movie in list) {
                arr.put(
                    JSONObject().apply {
                        put("slug", movie.slug)
                        put("id", movie.effectiveKey)
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
                        put("fallbackEmbed", movie.fallbackEmbed)
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
