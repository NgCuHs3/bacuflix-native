package com.bacuflix.app.data

import com.bacuflix.app.model.CatalogDiagnostic
import com.bacuflix.app.model.Movie
import java.net.URI

/**
 * Safe Markdown movie catalog parser ported from js/data/movie-markdown-parser.js.
 */
object MovieMarkdownParser {
    private val AUTOLINK_REGEX = Regex("^<(https://[^>\\s]+)>$", RegexOption.IGNORE_CASE)
    private val MD_LINK_REGEX = Regex(
        "^\\[[^\\]]*]\\(\\s*<?(https://[^)\\s>]+)>?(?:\\s+[\"'][^\"']*[\"'])?\\s*\\)$",
        RegexOption.IGNORE_CASE
    )
    private val FIELD_LINE_REGEX = Regex(
        "^\\s*-\\s*(title|poster|m3u8|subtitle|source)\\s*:\\s*(.*?)\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val MOVIE_HEADER_REGEX = Regex(
        "^##\\s+Movie\\s*$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    data class ParseOptions(
        val catalogSourceId: String = "unknown",
        val fallbackPoster: String = CatalogSources.FALLBACK_POSTER,
        val playbackEnabled: Boolean = false
    )

    data class ParseResult(
        val movies: List<Movie>,
        val diagnostics: List<CatalogDiagnostic>
    )

    fun extractLinkDestination(value: String): String {
        val trimmed = value.trim()
        AUTOLINK_REGEX.find(trimmed)?.let { return it.groupValues[1].trim() }
        MD_LINK_REGEX.find(trimmed)?.let { return it.groupValues[1].trim() }
        return trimmed.removeSurrounding("\"").removeSurrounding("'").trim()
    }

    fun parseHttpsUrl(value: String): URI? {
        if (value.isBlank()) return null
        return try {
            val uri = URI(value.trim())
            if (uri.scheme?.lowercase() != "https" || uri.host.isNullOrBlank() || !uri.userInfo.isNullOrBlank()) {
                return null
            }
            URI(uri.scheme.lowercase(), null, uri.host, uri.port, uri.rawPath, uri.rawQuery, null)
        } catch (_: Exception) {
            null
        }
    }

    fun makeStableId(value: String): String {
        var hash = 2166136261u
        for (ch in value) {
            hash = hash xor ch.code.toUInt()
            hash *= 16777619u
        }
        return "movie-" + hash.toLong().toString(36)
    }

    fun parseMovieMarkdown(
        markdown: String?,
        options: ParseOptions = ParseOptions()
    ): ParseResult {
        val diagnostics = mutableListOf<CatalogDiagnostic>()
        val movies = mutableListOf<Movie>()
        val text = (markdown ?: "").removePrefix("\uFEFF")
        val sections = MOVIE_HEADER_REGEX.split(text)

        if (sections.size < 2) {
            diagnostics.add(
                CatalogDiagnostic(
                    sourceId = options.catalogSourceId,
                    entry = 0,
                    message = "No movie entries were found."
                )
            )
            return ParseResult(movies = movies, diagnostics = diagnostics)
        }

        sections.drop(1).forEachIndexed { index, block ->
            val entryNumber = index + 1
            val movie = parseMovieBlock(block, entryNumber, options, diagnostics)
            if (movie != null) {
                movies.add(movie)
            }
        }

        return ParseResult(movies = movies, diagnostics = diagnostics)
    }

    private fun parseMovieBlock(
        block: String,
        entryNumber: Int,
        options: ParseOptions,
        diagnostics: MutableList<CatalogDiagnostic>
    ): Movie? {
        val fields = mutableMapOf<String, String>()
        block.lineSequence().forEach { line ->
            val match = FIELD_LINE_REGEX.matchEntire(line)
            if (match != null) {
                fields[match.groupValues[1].lowercase()] = match.groupValues[2].trim()
            }
        }

        val title = (fields["title"] ?: "").replace(Regex("\\s+"), " ").trim()
        val streamUrl = parseHttpsUrl(extractLinkDestination(fields["m3u8"] ?: ""))
        if (title.isEmpty()) {
            diagnostics.add(
                CatalogDiagnostic(
                    sourceId = options.catalogSourceId,
                    entry = entryNumber,
                    message = "Movie entry has no title."
                )
            )
            return null
        }
        if (streamUrl == null) {
            diagnostics.add(
                CatalogDiagnostic(
                    sourceId = options.catalogSourceId,
                    entry = entryNumber,
                    message = "Movie entry has no valid HTTPS M3U8 URL."
                )
            )
            return null
        }

        val rawPoster = fields["poster"] ?: ""
        val posterUrl = parseHttpsUrl(extractLinkDestination(rawPoster))
        if (rawPoster.isNotEmpty() && posterUrl == null) {
            diagnostics.add(
                CatalogDiagnostic(
                    sourceId = options.catalogSourceId,
                    entry = entryNumber,
                    message = "Movie poster URL is invalid; using the fallback poster."
                )
            )
        }

        val rawSubtitle = fields["subtitle"]?.let { extractLinkDestination(it) } ?: ""
        val subtitleUrl = if (rawSubtitle.isNotEmpty()) parseHttpsUrl(rawSubtitle) else null
        if (!fields["subtitle"].isNullOrEmpty() && subtitleUrl == null) {
            diagnostics.add(
                CatalogDiagnostic(
                    sourceId = options.catalogSourceId,
                    entry = entryNumber,
                    message = "Movie subtitle URL is invalid."
                )
            )
        }

        val source = (fields["source"] ?: "").replace(Regex("\\s+"), " ").trim()
            .ifEmpty { streamUrl.host ?: "Catalog" }
        val posterHref = posterUrl?.toString() ?: options.fallbackPoster

        return Movie(
            id = makeStableId(streamUrl.toString()),
            slug = "",
            title = title,
            seriesTitle = title,
            poster = posterHref,
            thumb = posterHref,
            m3u8 = streamUrl.toString(),
            subtitle = subtitleUrl?.toString(),
            source = source,
            catalogSourceId = options.catalogSourceId,
            playbackEnabled = options.playbackEnabled
        )
    }
}
