package com.bacuflix.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.bacuflix.app.MainActivity
import com.bacuflix.app.R
import com.bacuflix.app.model.EpisodeItem
import com.bacuflix.app.model.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Central manager orchestrating video downloads, disk persistence,
 * wake lock retention, and Android notification updates.
 */
class MovieDownloadManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "bacuflix_downloads_prefs"
        private const val STORAGE_KEY = "saved_downloads"
        private const val CHANNEL_ID = "bacuflix_download_channel"
        private const val NOTIFICATION_ID_BASE = 50000

        @Volatile
        private var instance: MovieDownloadManager? = null

        fun getInstance(context: Context): MovieDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: MovieDownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    init {
        createNotificationChannel()
        initWakeLock()
        loadFromPrefs()
    }

    fun getAll(): List<DownloadItem> = _downloads.value

    fun isDownloaded(movieKey: String, episodeIndex: Int = 0): Boolean {
        val targetId = buildDownloadId(movieKey, episodeIndex)
        val item = _downloads.value.firstOrNull { it.id == targetId || (it.movieKey == movieKey && it.episodeIndex == episodeIndex) }
        return item != null && item.status == DownloadStatus.COMPLETED && File(item.localFilePath).exists()
    }

    fun isDownloading(movieKey: String, episodeIndex: Int = 0): Boolean {
        val targetId = buildDownloadId(movieKey, episodeIndex)
        val item = _downloads.value.firstOrNull { it.id == targetId || (it.movieKey == movieKey && it.episodeIndex == episodeIndex) }
        return item != null && (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.REMUXING)
    }

    fun getDownloadItem(movieKey: String, episodeIndex: Int = 0): DownloadItem? {
        val targetId = buildDownloadId(movieKey, episodeIndex)
        return _downloads.value.firstOrNull { it.id == targetId || (it.movieKey == movieKey && it.episodeIndex == episodeIndex) }
    }

    fun startDownload(movie: Movie, episode: EpisodeItem? = null): Boolean {
        val epIdx = episode?.index ?: movie.currentEpisodeIndex
        val epName = episode?.name ?: movie.episodeName
        val streamUrl = (episode?.m3u8?.takeIf { it.isNotBlank() }
            ?: movie.m3u8.takeIf { it.isNotBlank() }
            ?: episode?.fallbackEmbed?.takeIf { it.isNotBlank() }
            ?: movie.fallbackEmbed).trim()

        if (streamUrl.isBlank()) return false

        val id = buildDownloadId(movie.effectiveKey, epIdx)

        // If already completed and file exists, don't re-download
        val existing = _downloads.value.firstOrNull { it.id == id }
        if (existing != null && existing.status == DownloadStatus.COMPLETED && File(existing.localFilePath).exists()) {
            return true
        }

        val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        if (!destinationDir.exists()) destinationDir.mkdirs()

        val safeTitle = sanitizeFilename(movie.title)
        val safeEp = if (epName.isNotBlank()) "_${sanitizeFilename(epName)}" else ""
        val mp4File = File(destinationDir, "${safeTitle}${safeEp}_${movie.effectiveKey}.mp4")

        val newItem = DownloadItem(
            id = id,
            movieKey = movie.effectiveKey,
            movieTitle = movie.title,
            seriesTitle = movie.seriesTitle.ifBlank { movie.title },
            episodeName = epName,
            episodeIndex = epIdx,
            posterUrl = movie.poster,
            thumbUrl = movie.thumb,
            streamUrl = streamUrl,
            localFilePath = mp4File.absolutePath,
            status = DownloadStatus.QUEUED,
            progressPercent = 0
        )

        updateItemInList(newItem)

        val job = scope.launch {
            runDownload(newItem, mp4File)
        }
        activeJobs[id] = job
        return true
    }

    fun cancelDownload(id: String) {
        val job = activeJobs.remove(id)
        job?.cancel()
        val item = _downloads.value.firstOrNull { it.id == id } ?: return
        try {
            File(item.localFilePath).delete()
            val tempTs = File(item.localFilePath.substringBeforeLast(".") + ".temp.ts")
            if (tempTs.exists()) tempTs.delete()
        } catch (_: Exception) {}

        val updated = item.copy(status = DownloadStatus.CANCELLED, speedText = "")
        updateItemInList(updated)
        cancelNotification(id)
        checkLocks()
    }

    fun deleteDownload(id: String) {
        cancelDownload(id)
        val current = _downloads.value.toMutableList()
        current.removeAll { it.id == id }
        _downloads.value = current
        saveToPrefs()
    }

    private suspend fun runDownload(item: DownloadItem, mp4File: File) {
        acquireWakeLock()
        var currentItem = item.copy(status = DownloadStatus.DOWNLOADING)
        updateItemInList(currentItem)
        showNotification(currentItem)

        var lastNotifyTime = 0L

        try {
            HlsMp4Downloader.downloadToMp4(
                streamUrl = currentItem.streamUrl,
                outputMp4File = mp4File,
                onProgress = { percent, downloadedBytes, totalBytes, speed ->
                    val status = if (percent >= 92 && percent < 100) DownloadStatus.REMUXING else DownloadStatus.DOWNLOADING
                    currentItem = currentItem.copy(
                        status = status,
                        progressPercent = percent,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        speedText = speed
                    )
                    updateItemInList(currentItem)

                    val now = System.currentTimeMillis()
                    if (now - lastNotifyTime >= 1500L || percent >= 100) {
                        lastNotifyTime = now
                        showNotification(currentItem)
                    }
                },
                isCancelled = {
                    val j = activeJobs[currentItem.id]
                    j == null || j.isCancelled
                }
            )

            currentItem = currentItem.copy(
                status = DownloadStatus.COMPLETED,
                progressPercent = 100,
                speedText = "",
                downloadedBytes = mp4File.length(),
                totalBytes = mp4File.length()
            )
            updateItemInList(currentItem)
            showCompletionNotification(currentItem)
        } catch (e: CancellationException) {
            currentItem = currentItem.copy(status = DownloadStatus.CANCELLED, speedText = "")
            updateItemInList(currentItem)
            cancelNotification(currentItem.id)
        } catch (e: Exception) {
            currentItem = currentItem.copy(
                status = DownloadStatus.FAILED,
                errorMessage = e.message ?: "Lỗi khi tải phim",
                speedText = ""
            )
            updateItemInList(currentItem)
            showErrorNotification(currentItem)
        } finally {
            activeJobs.remove(currentItem.id)
            saveToPrefs()
            checkLocks()
        }
    }

    private fun updateItemInList(item: DownloadItem) {
        val list = _downloads.value.toMutableList()
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            list[idx] = item
        } else {
            list.add(0, item)
        }
        _downloads.value = list
    }

    private fun buildDownloadId(movieKey: String, episodeIndex: Int): String {
        return "${movieKey}_ep$episodeIndex"
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]+"), "_").take(40)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BACUFlix Tải phim",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tiến độ tải phim về máy để xem offline"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(item: DownloadItem) {
        try {
            val notifyId = NOTIFICATION_ID_BASE + (item.id.hashCode() and 0x7FFF)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (item.episodeName.isNotBlank()) {
                "Đang tải: ${item.movieTitle} (${item.episodeName})"
            } else {
                "Đang tải: ${item.movieTitle}"
            }

            val speedPart = if (item.speedText.isNotBlank()) " • ${item.speedText}" else ""
            val statusText = if (item.status == DownloadStatus.REMUXING) {
                "Đang đóng gói file MP4..."
            } else {
                "${item.progressPercent}%$speedPart"
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_play)
                .setContentTitle(title)
                .setContentText(statusText)
                .setProgress(100, item.progressPercent, item.status == DownloadStatus.REMUXING)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)

            notificationManager.notify(notifyId, builder.build())
        } catch (_: Exception) {}
    }

    private fun showCompletionNotification(item: DownloadItem) {
        try {
            val notifyId = NOTIFICATION_ID_BASE + (item.id.hashCode() and 0x7FFF)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_play)
                .setContentTitle("Tải hoàn tất: ${item.movieTitle}")
                .setContentText("Đã lưu thành file MP4. Bấm để xem offline ngay!")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)

            notificationManager.notify(notifyId, builder.build())
        } catch (_: Exception) {}
    }

    private fun showErrorNotification(item: DownloadItem) {
        try {
            val notifyId = NOTIFICATION_ID_BASE + (item.id.hashCode() and 0x7FFF)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_play)
                .setContentTitle("Tải thất bại: ${item.movieTitle}")
                .setContentText(item.errorMessage ?: "Không thể tải phim")
                .setAutoCancel(true)
                .setOngoing(false)

            notificationManager.notify(notifyId, builder.build())
        } catch (_: Exception) {}
    }

    private fun cancelNotification(id: String) {
        try {
            val notifyId = NOTIFICATION_ID_BASE + (id.hashCode() and 0x7FFF)
            notificationManager.cancel(notifyId)
        } catch (_: Exception) {}
    }

    private fun initWakeLock() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BACUFlix:MovieDownloadWakeLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (_: Exception) {}
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3 * 60 * 60 * 1000L) // up to 3 hours
            }
        } catch (_: Exception) {}
    }

    private fun checkLocks() {
        if (activeJobs.isEmpty()) {
            try {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            } catch (_: Exception) {}
        }
    }

    private fun loadFromPrefs() {
        try {
            val raw = prefs.getString(STORAGE_KEY, null)
            if (raw.isNullOrBlank()) {
                _downloads.value = emptyList()
                return
            }
            val arr = JSONArray(raw)
            val list = mutableListOf<DownloadItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val path = obj.optString("localFilePath")
                val file = File(path)
                // Check if file still exists on disk
                val statusStr = obj.optString("status")
                val isCompleted = statusStr == DownloadStatus.COMPLETED.name && file.exists()
                val status = if (isCompleted) {
                    DownloadStatus.COMPLETED
                } else if (statusStr == DownloadStatus.DOWNLOADING.name || statusStr == DownloadStatus.QUEUED.name) {
                    // Previous unfinished download interrupted by app kill
                    DownloadStatus.FAILED
                } else {
                    DownloadStatus.FAILED
                }

                if (file.exists() || isCompleted) {
                    list.add(
                        DownloadItem(
                            id = obj.optString("id"),
                            movieKey = obj.optString("movieKey"),
                            movieTitle = obj.optString("movieTitle"),
                            seriesTitle = obj.optString("seriesTitle"),
                            episodeName = obj.optString("episodeName"),
                            episodeIndex = obj.optInt("episodeIndex", 0),
                            posterUrl = obj.optString("posterUrl"),
                            thumbUrl = obj.optString("thumbUrl"),
                            streamUrl = obj.optString("streamUrl"),
                            localFilePath = path,
                            status = status,
                            progressPercent = if (isCompleted) 100 else obj.optInt("progressPercent", 0),
                            downloadedBytes = if (isCompleted) file.length() else obj.optLong("downloadedBytes", 0L),
                            totalBytes = if (isCompleted) file.length() else obj.optLong("totalBytes", 0L),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            _downloads.value = list
        } catch (_: Exception) {
            _downloads.value = emptyList()
        }
    }

    private fun saveToPrefs() {
        try {
            val arr = JSONArray()
            for (item in _downloads.value) {
                // Persist completed or failed downloads
                if (item.status == DownloadStatus.COMPLETED || item.status == DownloadStatus.FAILED) {
                    arr.put(
                        JSONObject().apply {
                            put("id", item.id)
                            put("movieKey", item.movieKey)
                            put("movieTitle", item.movieTitle)
                            put("seriesTitle", item.seriesTitle)
                            put("episodeName", item.episodeName)
                            put("episodeIndex", item.episodeIndex)
                            put("posterUrl", item.posterUrl)
                            put("thumbUrl", item.thumbUrl)
                            put("streamUrl", item.streamUrl)
                            put("localFilePath", item.localFilePath)
                            put("status", item.status.name)
                            put("progressPercent", item.progressPercent)
                            put("downloadedBytes", item.downloadedBytes)
                            put("totalBytes", item.totalBytes)
                            put("createdAt", item.createdAt)
                        }
                    )
                }
            }
            prefs.edit().putString(STORAGE_KEY, arr.toString()).apply()
        } catch (_: Exception) {}
    }
}
