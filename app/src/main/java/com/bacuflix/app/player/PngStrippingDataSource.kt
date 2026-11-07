package com.bacuflix.app.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Custom Media3 [DataSource] that wraps an upstream HTTP [DataSource] and inspects
 * non-playlist HLS fragments with [SegmentInspector], transparently stripping PNG headers
 * from PNG-wrapped MPEG-TS segments while passing standard TS/MP4/playlist responses through.
 */
@OptIn(UnstableApi::class)
class PngStrippingDataSource(
    private val upstream: DataSource
) : BaseDataSource(/* isNetwork = */ true) {

    class Factory(
        private val upstreamFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; BACUFlix/3.0) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12000)
            .setReadTimeoutMs(15000)
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return PngStrippingDataSource(upstreamFactory.createDataSource())
        }
    }

    private var currentUri: Uri? = null
    private var passthroughMode = false
    private var bufferedBytes: ByteArray? = null
    private var readPosition = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        currentUri = dataSpec.uri
        val path = dataSpec.uri.path?.lowercase().orEmpty()

        // Playlists (.m3u8) and subtitles (.vtt/.srt) pass through directly without buffering
        if (path.endsWith(".m3u8") || path.endsWith(".vtt") || path.endsWith(".srt")) {
            passthroughMode = true
            val length = upstream.open(dataSpec)
            opened = true
            transferStarted(dataSpec)
            return length
        }

        passthroughMode = false
        upstream.open(dataSpec)
        opened = true
        transferStarted(dataSpec)

        val out = ByteArrayOutputStream(64 * 1024)
        val temp = ByteArray(16 * 1024)
        while (true) {
            val r = upstream.read(temp, 0, temp.size)
            if (r == C.RESULT_END_OF_INPUT || r == -1) break
            if (r > 0) {
                out.write(temp, 0, r)
                bytesTransferred(r)
            }
        }

        val rawBytes = out.toByteArray()
        val inspectedBytes = try {
            SegmentInspector.inspectSegment(rawBytes).data
        } catch (_: Exception) {
            rawBytes
        }

        bufferedBytes = inspectedBytes
        readPosition = 0
        return inspectedBytes.size.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (passthroughMode) {
            val r = upstream.read(buffer, offset, length)
            if (r > 0) {
                bytesTransferred(r)
            }
            return r
        }

        val data = bufferedBytes ?: return C.RESULT_END_OF_INPUT
        val remaining = data.size - readPosition
        if (remaining <= 0) return C.RESULT_END_OF_INPUT

        val toCopy = min(length, remaining)
        System.arraycopy(data, readPosition, buffer, offset, toCopy)
        readPosition += toCopy
        return toCopy
    }

    override fun getUri(): Uri? = upstream.uri ?: currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        bufferedBytes = null
        readPosition = 0
        try {
            upstream.close()
        } finally {
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
