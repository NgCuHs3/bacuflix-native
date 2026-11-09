package com.bacuflix.app.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Lossless remuxer converting concatenated MPEG-TS HLS streams into standard ISO Base Media File Format MP4.
 * Uses Android platform built-in [MediaExtractor] and [MediaMuxer] without external binaries or re-encoding.
 */
object TsToMp4Remuxer {

    fun remux(tsFile: File, mp4File: File): Boolean {
        if (!tsFile.exists() || tsFile.length() < 188) {
            return false
        }
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(tsFile.absolutePath)

            val trackCount = extractor.trackCount
            if (trackCount <= 0) return false

            muxer = MediaMuxer(mp4File.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = mutableMapOf<Int, Int>()

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val muxerTrack = muxer.addTrack(format)
                    trackMap[i] = muxerTrack
                }
            }

            if (trackMap.isEmpty()) return false

            muxer.start()
            val maxBufferSize = 1024 * 1024 // 1 MB buffer
            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            val lastPtsMap = mutableMapOf<Int, Long>()
            val offsetMap = mutableMapOf<Int, Long>()

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break
                val muxerTrack = trackMap[trackIndex]
                if (muxerTrack != null) {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize >= 0) {
                        val originalPts = extractor.sampleTime
                        val currentOffset = offsetMap[trackIndex] ?: 0L
                        val lastPts = lastPtsMap[trackIndex] ?: 0L

                        var adjustedPts = originalPts + currentOffset
                        if (adjustedPts < lastPts) {
                            val diff = (lastPts - adjustedPts) + 10_000L // Ensure monotonic timestamps
                            val newOffset = currentOffset + diff
                            offsetMap[trackIndex] = newOffset
                            adjustedPts = originalPts + newOffset
                        }
                        lastPtsMap[trackIndex] = adjustedPts

                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = adjustedPts
                        bufferInfo.flags = extractor.sampleFlags

                        muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    }
                }
                extractor.advance()
            }

            muxer.stop()
            return mp4File.exists() && mp4File.length() > 0
        } catch (_: Exception) {
            return false
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
