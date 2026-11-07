package com.bacuflix.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Memory + Disk cached asynchronous image loader with automatic fallback to local asset icon.png.
 */
object ImageLoader {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 6

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private var fallbackBitmap: Bitmap? = null

    fun getCached(url: String): Bitmap? {
        if (url.isBlank()) return null
        return memoryCache.get(url)
    }

    suspend fun loadFallback(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        fallbackBitmap?.let { return@withContext it }
        try {
            context.assets.open("icon.png").use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                fallbackBitmap = bmp
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadBitmap(
        context: Context,
        url: String,
        alternateUrl: String? = null,
        maxDimension: Int = 960
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank() || url == "icon.png") {
            return@withContext loadFallback(context)
        }
        memoryCache.get(url)?.let { return@withContext it }

        val diskResult = fetchOrReadDisk(context, url, maxDimension)
        if (diskResult != null) {
            memoryCache.put(url, diskResult)
            return@withContext diskResult
        }

        if (!alternateUrl.isNullOrBlank() && alternateUrl != url && alternateUrl != "icon.png") {
            memoryCache.get(alternateUrl)?.let { return@withContext it }
            val altResult = fetchOrReadDisk(context, alternateUrl, maxDimension)
            if (altResult != null) {
                memoryCache.put(alternateUrl, altResult)
                return@withContext altResult
            }
        }

        loadFallback(context)
    }

    private fun fetchOrReadDisk(context: Context, urlString: String, maxDimension: Int): Bitmap? {
        val cacheDir = File(context.cacheDir, "poster_cache").apply { if (!exists()) mkdirs() }
        val cacheFile = File(cacheDir, sha256(urlString))

        if (cacheFile.exists() && cacheFile.length() > 64) {
            decodeSampledBitmap(cacheFile.readBytes(), maxDimension)?.let { return it }
        }

        return try {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 9000
                readTimeout = 9000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BACUFlix-Android/3.0")
            }
            try {
                if (connection.responseCode in 200..299) {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        try {
                            FileOutputStream(cacheFile).use { it.write(bytes) }
                        } catch (_: Exception) {}
                        decodeSampledBitmap(bytes, maxDimension)
                    } else null
                } else null
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            var inSampleSize = 1
            while (
                boundsOptions.outWidth / inSampleSize > maxDimension ||
                boundsOptions.outHeight / inSampleSize > maxDimension
            ) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
