package com.bacuflix.app.download

import com.bacuflix.app.player.SegmentInspector
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/**
 * High-speed HLS and Progressive MP4 downloader.
 * Streams segments, strips disguised PNG headers via [SegmentInspector],
 * concatenates transport stream packets, and remuxes into a single MP4 file.
 */
object HlsMp4Downloader {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; BACUFlix/3.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    fun downloadToMp4(
        streamUrl: String,
        outputMp4File: File,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long, speedText: String) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val cleanUrl = streamUrl.trim()
        if (cleanUrl.isBlank()) throw IllegalArgumentException("Đường dẫn stream trống.")

        outputMp4File.parentFile?.mkdirs()

        // 1. Direct progressive MP4 / MKV stream
        val isDirectMp4 = !cleanUrl.contains(".m3u8", ignoreCase = true) &&
            !cleanUrl.contains("streamc.xyz", ignoreCase = true) &&
            (cleanUrl.contains(".mp4", ignoreCase = true) || cleanUrl.contains(".mkv", ignoreCase = true))

        if (isDirectMp4) {
            downloadDirectVideo(cleanUrl, outputMp4File, onProgress, isCancelled)
            return
        }

        // 2. HLS stream (.m3u8)
        downloadHlsToMp4(cleanUrl, outputMp4File, onProgress, isCancelled)
    }

    private fun downloadDirectVideo(
        urlStr: String,
        outputFile: File,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long, speedText: String) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val conn = openConnection(urlStr)
        try {
            val totalBytes = conn.contentLengthLong.coerceAtLeast(1L)
            var downloadedBytes = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceLast = 0L
            var speedStr = ""

            conn.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) throw CancellationException("Tải phim đã bị hủy")
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        bytesSinceLast += read

                        val now = System.currentTimeMillis()
                        val diff = now - lastTime
                        if (diff >= 1000L) {
                            val speedBps = (bytesSinceLast * 1000f / diff).toLong()
                            speedStr = formatSpeed(speedBps)
                            lastTime = now
                            bytesSinceLast = 0L
                        }

                        val pct = ((downloadedBytes.toFloat() / totalBytes) * 100f).roundToInt().coerceIn(0, 100)
                        onProgress(pct, downloadedBytes, totalBytes, speedStr)
                    }
                }
            }
            onProgress(100, outputFile.length(), outputFile.length(), "Hoàn tất")
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadHlsToMp4(
        m3u8Url: String,
        outputMp4File: File,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long, speedText: String) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val tempTsFile = File(outputMp4File.parentFile, "${outputMp4File.nameWithoutExtension}.temp.ts")
        if (tempTsFile.exists()) tempTsFile.delete()

        // 1. Fetch playlist content
        val (finalMediaPlaylistUrl, mediaPlaylistText) = resolveMediaPlaylist(m3u8Url)

        // 2. Extract segments
        val segmentUrls = extractSegmentUrls(finalMediaPlaylistUrl, mediaPlaylistText)
        if (segmentUrls.isEmpty()) {
            throw IllegalStateException("Không tìm thấy phân đoạn video (.ts) trong danh sách phát.")
        }

        val totalSegments = segmentUrls.size
        var downloadedBytes = 0L
        var lastTime = System.currentTimeMillis()
        var bytesSinceLast = 0L
        var speedStr = ""

        // 3. Download and strip segments one by one
        FileOutputStream(tempTsFile).use { tsOutput ->
            for (index in segmentUrls.indices) {
                if (isCancelled()) {
                    tempTsFile.delete()
                    throw CancellationException("Tải phim đã bị hủy")
                }

                val segUrl = segmentUrls[index]
                val segBytes = downloadSegmentBytes(segUrl)

                // Inspect and strip disguised PNG header if present
                val payloadOffset = SegmentInspector.findPayloadStartOffsetInPrefix(segBytes, segBytes.size)
                if (payloadOffset > 0 && payloadOffset < segBytes.size) {
                    tsOutput.write(segBytes, payloadOffset, segBytes.size - payloadOffset)
                    downloadedBytes += (segBytes.size - payloadOffset)
                    bytesSinceLast += (segBytes.size - payloadOffset)
                } else {
                    tsOutput.write(segBytes)
                    downloadedBytes += segBytes.size
                    bytesSinceLast += segBytes.size
                }

                val now = System.currentTimeMillis()
                val diff = now - lastTime
                if (diff >= 1000L) {
                    val speedBps = (bytesSinceLast * 1000f / diff).toLong()
                    speedStr = formatSpeed(speedBps)
                    lastTime = now
                    bytesSinceLast = 0L
                }

                // Reserve 0%..92% for downloading segments, 92%..100% for MP4 remuxing
                val downloadPct = (((index + 1).toFloat() / totalSegments) * 92f).roundToInt().coerceIn(0, 92)
                val estimatedTotalBytes = (downloadedBytes.toFloat() / (index + 1) * totalSegments).toLong()
                onProgress(downloadPct, downloadedBytes, estimatedTotalBytes, speedStr)
            }
        }

        if (isCancelled()) {
            tempTsFile.delete()
            throw CancellationException("Tải phim đã bị hủy")
        }

        // 4. Remux pure MPEG-TS into MP4
        onProgress(95, downloadedBytes, downloadedBytes, "Đang đóng gói MP4...")
        val remuxSuccess = TsToMp4Remuxer.remux(tempTsFile, outputMp4File)
        if (!remuxSuccess || !outputMp4File.exists() || outputMp4File.length() == 0L) {
            // Fallback: copy/rename clean MPEG-TS stream directly as .mp4
            tempTsFile.copyTo(outputMp4File, overwrite = true)
        }
        tempTsFile.delete()

        onProgress(100, outputMp4File.length(), outputMp4File.length(), "Hoàn tất")
    }

    private fun resolveMediaPlaylist(initialUrl: String): Pair<String, String> {
        val initialContent = fetchText(initialUrl)
        if (!initialContent.contains("#EXT-X-STREAM-INF")) {
            return initialUrl to initialContent
        }

        // Master playlist: find variant playlist lines
        val lines = initialContent.lines()
        var bestVariantUrl: String? = null
        var maxBandwidth = -1L

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bwMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                val bandwidth = bwMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

                // Next non-empty, non-comment line is the playlist URL
                for (j in (i + 1) until lines.size) {
                    val candidate = lines[j].trim()
                    if (candidate.isNotEmpty() && !candidate.startsWith("#")) {
                        if (bandwidth > maxBandwidth) {
                            maxBandwidth = bandwidth
                            bestVariantUrl = resolveUrl(initialUrl, candidate)
                        }
                        break
                    }
                }
            }
        }

        val targetUrl = bestVariantUrl ?: initialUrl
        val targetContent = if (targetUrl == initialUrl) initialContent else fetchText(targetUrl)
        return targetUrl to targetContent
    }

    private fun extractSegmentUrls(mediaPlaylistUrl: String, playlistContent: String): List<String> {
        val list = mutableListOf<String>()
        val lines = playlistContent.lines()
        var isNextSegment = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("#EXTINF")) {
                isNextSegment = true
            } else if (isNextSegment && !trimmed.startsWith("#")) {
                list.add(resolveUrl(mediaPlaylistUrl, trimmed))
                isNextSegment = false
            }
        }
        return list
    }

    private fun resolveUrl(baseUrl: String, target: String): String {
        return try {
            URI.create(baseUrl).resolve(target).toString()
        } catch (_: Exception) {
            target
        }
    }

    private fun fetchText(urlStr: String): String {
        val conn = openConnection(urlStr)
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadSegmentBytes(urlStr: String): ByteArray {
        val conn = openConnection(urlStr)
        return try {
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(urlStr: String): HttpURLConnection {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 25000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")

            if (urlStr.contains("vsmov.com") || urlStr.contains("streamvsmov.com")) {
                setRequestProperty("Origin", "https://vsmov.com")
                setRequestProperty("Referer", "https://vsmov.com/")
            } else if (urlStr.contains("streamc.xyz") || urlStr.contains("nguonc.com")) {
                setRequestProperty("Origin", "https://phim.nguonc.com")
                setRequestProperty("Referer", "https://phim.nguonc.com/")
            }
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP $code khi tải phân đoạn video từ: $urlStr")
        }
        return conn
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB/s", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB/s", kb)
            else -> "$bytesPerSec B/s"
        }
    }
}
