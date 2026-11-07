package com.bacuflix.app.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import kotlin.math.min

/**
 * Streaming Media3 [DataSource] that wraps an upstream HTTP [DataSource] and inspects only
 * the leading header bytes of HLS segments with [SegmentInspector], transparently stripping
 * PNG/image headers from disguised MPEG-TS segments while streaming the remainder directly
 * from the network without buffering entire segments in RAM.
 */
@OptIn(UnstableApi::class)
class PngStrippingDataSource(
    private val upstream: DataSource
) : BaseDataSource(/* isNetwork = */ true) {

    companion object {
        private const val INITIAL_PEEK_BYTES = 16 * 1024
        private const val MAX_HEADER_PEEK_BYTES = 256 * 1024
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; BACUFlix/3.0) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return PngStrippingDataSource(upstreamFactory.createDataSource())
        }
    }

    private var currentUri: Uri? = null
    private var passthroughMode = false
    private var peekBuffer: ByteArray? = null
    private var peekLength = 0
    private var peekReadPos = 0
    private var upstreamEof = false
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        currentUri = dataSpec.uri
        val path = dataSpec.uri.path?.lowercase().orEmpty()

        // Playlists (.m3u8), subtitles (.vtt/.srt), encryption keys (.key), and progressive MP4/MKV
        // pass through directly without header inspection.
        if (
            path.endsWith(".m3u8") ||
            path.endsWith(".vtt") ||
            path.endsWith(".srt") ||
            path.endsWith(".key") ||
            path.endsWith(".mp4") ||
            path.endsWith(".mkv")
        ) {
            passthroughMode = true
            val length = upstream.open(dataSpec)
            opened = true
            transferStarted(dataSpec)
            return length
        }

        passthroughMode = false
        upstreamEof = false
        val upstreamLength = upstream.open(dataSpec)
        opened = true
        transferStarted(dataSpec)

        var buf = ByteArray(INITIAL_PEEK_BYTES)
        var totalRead = 0

        // Read initial 16 KB prefix
        while (totalRead < INITIAL_PEEK_BYTES) {
            val r = upstream.read(buf, totalRead, INITIAL_PEEK_BYTES - totalRead)
            if (r == C.RESULT_END_OF_INPUT || r == -1) {
                upstreamEof = true
                break
            }
            if (r > 0) {
                totalRead += r
                bytesTransferred(r)
            }
        }

        var payloadStart = SegmentInspector.findPayloadStartOffsetInPrefix(buf, totalRead)

        // If the segment starts with a PNG header larger than 16 KB and we haven't reached IEND yet,
        // expand the header peek up to MAX_HEADER_PEEK_BYTES (256 KB).
        if (
            payloadStart == 0 &&
            !upstreamEof &&
            SegmentInspector.hasPngSignature(buf, totalRead)
        ) {
            val expanded = ByteArray(MAX_HEADER_PEEK_BYTES)
            System.arraycopy(buf, 0, expanded, 0, totalRead)
            buf = expanded

            while (totalRead < MAX_HEADER_PEEK_BYTES) {
                val r = upstream.read(buf, totalRead, min(16 * 1024, MAX_HEADER_PEEK_BYTES - totalRead))
                if (r == C.RESULT_END_OF_INPUT || r == -1) {
                    upstreamEof = true
                    break
                }
                if (r > 0) {
                    totalRead += r
                    bytesTransferred(r)
                    payloadStart = SegmentInspector.findPayloadStartOffsetInPrefix(buf, totalRead)
                    if (payloadStart > 0) break
                }
            }
        }

        peekBuffer = buf
        peekLength = totalRead
        peekReadPos = payloadStart.coerceIn(0, totalRead)

        return if (payloadStart == 0 && upstreamLength != C.LENGTH_UNSET.toLong()) {
            upstreamLength
        } else if (payloadStart > 0 && upstreamLength > payloadStart) {
            upstreamLength - payloadStart
        } else {
            C.LENGTH_UNSET.toLong()
        }
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

        val peek = peekBuffer
        if (peek != null && peekReadPos < peekLength) {
            val available = peekLength - peekReadPos
            val toCopy = min(length, available)
            System.arraycopy(peek, peekReadPos, buffer, offset, toCopy)
            peekReadPos += toCopy
            if (peekReadPos >= peekLength) {
                peekBuffer = null
            }
            return toCopy
        }

        if (upstreamEof) {
            return C.RESULT_END_OF_INPUT
        }

        val r = upstream.read(buffer, offset, length)
        if (r == C.RESULT_END_OF_INPUT || r == -1) {
            upstreamEof = true
            return C.RESULT_END_OF_INPUT
        }
        if (r > 0) {
            bytesTransferred(r)
        }
        return r
    }

    override fun getUri(): Uri? = upstream.uri ?: currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        peekBuffer = null
        peekLength = 0
        peekReadPos = 0
        upstreamEof = false
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
