package com.bacuflix.app.download

import com.bacuflix.app.model.Movie

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    REMUXING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadItem(
    val id: String, // e.g. "${movieKey}_${episodeIndex}"
    val movieKey: String,
    val movieTitle: String,
    val seriesTitle: String = movieTitle,
    val episodeName: String = "",
    val episodeIndex: Int = 0,
    val posterUrl: String = "",
    val thumbUrl: String = "",
    val streamUrl: String = "",
    val localFilePath: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedSegments: Int = 0,
    val totalSegments: Int = 0,
    val speedText: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMovie(): Movie {
        val displayTitle = if (episodeName.isNotBlank() && !movieTitle.contains(episodeName)) {
            "$movieTitle - $episodeName"
        } else {
            movieTitle
        }
        return Movie(
            id = id,
            slug = movieKey,
            title = displayTitle,
            seriesTitle = seriesTitle.ifBlank { movieTitle },
            poster = posterUrl,
            thumb = thumbUrl.ifBlank { posterUrl },
            m3u8 = localFilePath,
            currentEpisodeIndex = episodeIndex,
            episodeName = episodeName,
            playbackEnabled = true,
            source = "Đã tải về (.mp4)"
        )
    }
}
