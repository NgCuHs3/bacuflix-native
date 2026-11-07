package com.bacuflix.app.data

import android.content.Context
import com.bacuflix.app.model.CatalogDiagnostic
import com.bacuflix.app.model.CatalogError
import com.bacuflix.app.model.CatalogResult
import com.bacuflix.app.model.CatalogSource
import com.bacuflix.app.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches, caches (10-minute TTL), merges, and deduplicates Markdown movie catalogs.
 * Ported from js/services/catalog-service.js.
 */
class CatalogRepository(
    private val context: Context,
    private val sources: List<CatalogSource> = CatalogSources.DEFAULT_SOURCES
) {
    companion object {
        private const val PREFS_NAME = "bacuflix_catalog_prefs"
        private const val CACHE_KEY = "bacuflix.movie-catalog.v1"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private const val REQUEST_TIMEOUT_MS = 12000
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var lastResult: CatalogResult? = null

    init {
        bootstrapFromCacheOrAssets()
    }

    fun getLastResult(): CatalogResult? = lastResult

    private fun bootstrapFromCacheOrAssets() {
        try {
            val cached = readCache()
            val initialLists = sources.map { src ->
                val saved = cached.optJSONObject(src.id)
                val md = if (saved != null && saved.optString("url") == src.url) {
                    saved.optString("markdown")
                } else {
                    readBundledAssetMarkdown()
                }
                if (md.isNotBlank()) {
                    MovieMarkdownParser.parseMovieMarkdown(
                        md,
                        MovieMarkdownParser.ParseOptions(
                            catalogSourceId = src.id,
                            fallbackPoster = CatalogSources.FALLBACK_POSTER,
                            playbackEnabled = src.playbackEnabled
                        )
                    ).movies
                } else {
                    emptyList()
                }
            }
            val merged = mergeMovies(initialLists)
            if (merged.isNotEmpty()) {
                lastResult = CatalogResult(
                    movies = merged,
                    cached = true
                )
            }
        } catch (_: Exception) {}
    }

    private fun readBundledAssetMarkdown(): String {
        return try {
            context.assets.open("data/movies.md").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun load(forceRefresh: Boolean = false): CatalogResult = withContext(Dispatchers.IO) {
        val cached = readCache()
        val now = System.currentTimeMillis()
        val allMovies = mutableListOf<List<Movie>>()
        val allDiagnostics = mutableListOf<CatalogDiagnostic>()
        val allErrors = mutableListOf<CatalogError>()
        var allCached = true

        for (source in sources) {
            val saved = cached.optJSONObject(source.id)
            val isFresh = !forceRefresh &&
                saved != null &&
                saved.optString("url") == source.url &&
                (now - saved.optLong("savedAt", 0L)) < CACHE_TTL_MS

            if (isFresh && saved != null) {
                val parsed = MovieMarkdownParser.parseMovieMarkdown(
                    saved.optString("markdown"),
                    MovieMarkdownParser.ParseOptions(
                        catalogSourceId = source.id,
                        fallbackPoster = CatalogSources.FALLBACK_POSTER,
                        playbackEnabled = source.playbackEnabled
                    )
                )
                allMovies.add(parsed.movies)
                allDiagnostics.addAll(parsed.diagnostics)
                continue
            }

            try {
                val markdown = fetchText(source.url, forceRefresh)
                val parsed = MovieMarkdownParser.parseMovieMarkdown(
                    markdown,
                    MovieMarkdownParser.ParseOptions(
                        catalogSourceId = source.id,
                        fallbackPoster = CatalogSources.FALLBACK_POSTER,
                        playbackEnabled = source.playbackEnabled
                    )
                )
                allDiagnostics.addAll(parsed.diagnostics)
                if (parsed.movies.isEmpty()) {
                    allCached = false
                    allErrors.add(CatalogError(source.id, "Catalog contains no valid movie entries."))
                    allMovies.add(emptyList())
                } else {
                    allCached = false
                    allMovies.add(parsed.movies)
                    cached.put(
                        source.id,
                        JSONObject().apply {
                            put("url", source.url)
                            put("markdown", markdown)
                            put("savedAt", System.currentTimeMillis())
                        }
                    )
                }
            } catch (e: Exception) {
                val staleMarkdown = saved?.optString("markdown").orEmpty().ifBlank { readBundledAssetMarkdown() }
                if (staleMarkdown.isNotBlank()) {
                    val parsedStale = MovieMarkdownParser.parseMovieMarkdown(
                        staleMarkdown,
                        MovieMarkdownParser.ParseOptions(
                            catalogSourceId = source.id,
                            fallbackPoster = CatalogSources.FALLBACK_POSTER,
                            playbackEnabled = source.playbackEnabled
                        )
                    )
                    allMovies.add(parsedStale.movies)
                    allDiagnostics.addAll(parsedStale.diagnostics)
                    allErrors.add(CatalogError(source.id, e.message ?: "Catalog request failed."))
                } else {
                    allCached = false
                    allMovies.add(emptyList())
                    allErrors.add(CatalogError(source.id, e.message ?: "Catalog request failed."))
                }
            }
        }

        writeCache(cached)
        val merged = mergeMovies(allMovies)
        val result = CatalogResult(
            movies = merged,
            errors = allErrors,
            diagnostics = allDiagnostics,
            failedAll = merged.isEmpty() && allErrors.isNotEmpty(),
            cached = allCached
        )
        lastResult = result
        result
    }

    private fun mergeMovies(sourceLists: List<List<Movie>>): List<Movie> {
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<Movie>()
        for (list in sourceLists) {
            for (movie in list) {
                if (seen.add(movie.m3u8)) {
                    merged.add(movie)
                }
            }
        }
        return merged
    }

    private fun fetchText(urlString: String, forceRefresh: Boolean): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MS
            readTimeout = REQUEST_TIMEOUT_MS
            useCaches = !forceRefresh
            if (forceRefresh) {
                setRequestProperty("Cache-Control", "no-cache")
            }
        }
        try {
            val status = conn.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Catalog request returned HTTP $status.")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun readCache(): JSONObject {
        return try {
            val raw = prefs.getString(CACHE_KEY, null)
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun writeCache(obj: JSONObject) {
        try {
            prefs.edit().putString(CACHE_KEY, obj.toString()).apply()
        } catch (_: Exception) {}
    }
}
