package com.bacuflix.app.data

import com.bacuflix.app.model.CatalogSource

object CatalogSources {
    const val FALLBACK_POSTER = "icon.png"

    val DEFAULT_SOURCES = listOf(
        CatalogSource(
            id = "bacuflix-main",
            url = "https://raw.githubusercontent.com/NgCuHs3/bacuflix/refs/heads/main/data/movies.md",
            playbackEnabled = true
        )
    )
}
