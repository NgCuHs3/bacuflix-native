package com.bacuflix.app.model

data class Movie(
    val id: String,
    val slug: String = "",
    val title: String,
    val seriesTitle: String = title,
    val originTitle: String = "",
    val poster: String = "icon.png",
    val thumb: String = poster,
    val year: String = "",
    val quality: String = "FHD",
    val lang: String = "Vietsub",
    val episodeCurrent: String = "",
    val episodeTotal: String = "",
    val time: String = "",
    val type: String = "",
    val status: String = "",
    val content: String = "",
    val categories: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val m3u8: String = "",
    val fallbackEmbed: String = "",
    val subtitle: String? = null,
    val source: String = "KKPhim",
    val catalogSourceId: String = "kkphim",
    val playbackEnabled: Boolean = true,
    val mlScore: Double? = null,
    val mlMatchPercent: Int? = null,
    val googleQueryMatched: String? = null,
    val currentEpisodeIndex: Int = 0,
    val episodeName: String = "",
    val serverName: String = "Vietsub",
    val resumeTime: Int = 0,
    val episodesList: List<EpisodeItem> = emptyList(),
    val serversList: List<EpisodeServer> = emptyList(),
    val currentTime: Int = 0,
    val duration: Int = 0,
    val progressPercent: Int = 0,
    val remainingText: String = "",
    val updatedAt: Long = 0L,
    val addedAt: Long = 0L
) {
    val effectiveKey: String
        get() = slug.ifBlank { id }
}

data class EpisodeItem(
    val index: Int,
    val name: String,
    val slug: String,
    val m3u8: String,
    val fallbackEmbed: String = ""
)

data class EpisodeServer(
    val serverName: String,
    val episodes: List<EpisodeItem>
)

data class MovieDetailResponse(
    val movie: Movie,
    val servers: List<EpisodeServer>
)

data class EpisodeProgressEntry(
    val episodeIndex: Int,
    val episodeName: String,
    val currentTime: Int,
    val duration: Int,
    val progress: Int,
    val updatedAt: Long
)

data class EpisodeResumeInfo(
    val slug: String,
    val episodeIndex: Int,
    val episodeName: String,
    val currentTime: Int,
    val duration: Int,
    val progressPercent: Int
)

data class CatalogSource(
    val id: String,
    val url: String,
    val playbackEnabled: Boolean
)

data class CatalogDiagnostic(
    val sourceId: String = "unknown",
    val entry: Int,
    val message: String
)

data class CatalogError(
    val sourceId: String,
    val message: String
)

data class CatalogResult(
    val movies: List<Movie>,
    val errors: List<CatalogError> = emptyList(),
    val diagnostics: List<CatalogDiagnostic> = emptyList(),
    val failedAll: Boolean = false,
    val cached: Boolean = false,
    val notice: String? = null
)

data class GenreCategory(
    val slug: String,
    val name: String,
    val subtitle: String,
    val accentHex: Long
)

object GenreCategories {
    val ALL = listOf(
        GenreCategory(
            slug = "hanh-dong",
            name = "Hành động",
            subtitle = "Kịch tính & mãn nhãn",
            accentHex = 0xFFE50920
        ),
        GenreCategory(
            slug = "tinh-cam",
            name = "Tình cảm",
            subtitle = "Lãng mạn & sâu lắng",
            accentHex = 0xFFEC4899
        ),
        GenreCategory(
            slug = "co-trang",
            name = "Cổ trang",
            subtitle = "Kiếm hiệp & tiên hiệp",
            accentHex = 0xFFF59E0B
        ),
        GenreCategory(
            slug = "hai-huoc",
            name = "Hài hước",
            subtitle = "Giải trí sảng khoái",
            accentHex = 0xFF10B981
        ),
        GenreCategory(
            slug = "kinh-di",
            name = "Kinh dị",
            subtitle = "Bí ẩn & rùng rợn",
            accentHex = 0xFF8B5CF6
        ),
        GenreCategory(
            slug = "vien-tuong",
            name = "Khoa học viễn tưởng",
            subtitle = "Thế giới tương lai",
            accentHex = 0xFF06B6D4
        )
    )
}

enum class HomeTab(val id: String, val label: String) {
    NEW("new", "Trang chủ"),
    MOVIES("movies", "Phim"),
    TVSHOWS("tvshows", "TV Show"),
    FAVORITES("favorites", "Danh sách của tôi"),
    TRENDING("trending", "Thịnh hành"),
    HISTORY("history", "Lịch sử"),
    RECOMMENDED("recommended", "Phim đề xuất"),
    GENRE("genre", "Thể loại"),
    SEARCH("search", "Tìm kiếm")
}

data class SubtitleCue(
    val id: Int,
    val startSeconds: Double,
    val endSeconds: Double,
    val text: String
)

data class UserInteraction(
    val type: String, // "view" | "search"
    val slug: String = "",
    val title: String = "",
    val originTitle: String = "",
    val query: String = "",
    val categories: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val movieType: String = "",
    val year: String = "",
    val lang: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class CountryOption(
    val slug: String,
    val name: String
)

object CountryOptions {
    val ALL = listOf(
        CountryOption(slug = "", name = "Tất cả quốc gia"),
        CountryOption(slug = "trung-quoc", name = "Trung Quốc"),
        CountryOption(slug = "han-quoc", name = "Hàn Quốc"),
        CountryOption(slug = "au-my", name = "Âu Mỹ"),
        CountryOption(slug = "nhat-ban", name = "Nhật Bản"),
        CountryOption(slug = "viet-nam", name = "Việt Nam"),
        CountryOption(slug = "thai-lan", name = "Thái Lan"),
        CountryOption(slug = "dai-loan", name = "Đài Loan"),
        CountryOption(slug = "hong-kong", name = "Hồng Kông")
    )
}

enum class FilterSortOrder(val id: String, val label: String) {
    DEFAULT("default", "Mới cập nhật"),
    YEAR_DESC("year_desc", "Năm mới nhất"),
    ML_MATCH("ml_match", "% Phù hợp cao"),
    TITLE_ASC("title_asc", "Tên A → Z")
}

data class CatalogFilterState(
    val genreSlug: String = "",
    val countrySlug: String = "",
    val year: String = "",
    val langFilter: String = "", // "" | "vietsub" | "thuyet-minh"
    val sortOrder: FilterSortOrder = FilterSortOrder.DEFAULT
) {
    val isActive: Boolean
        get() = genreSlug.isNotBlank() ||
            countrySlug.isNotBlank() ||
            year.isNotBlank() ||
            langFilter.isNotBlank() ||
            sortOrder != FilterSortOrder.DEFAULT
}

