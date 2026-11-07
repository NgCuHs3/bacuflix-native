package com.bacuflix.app.player

import java.util.zip.CRC32
import kotlin.math.min

/**
 * Inspects HLS media segments and extracts trailing MPEG-TS payloads from PNG-disguised segments.
 * Ported from js/player/segment-inspector.js.
 */
object SegmentInspector {
    private val PNG_SIGNATURE = byteArrayOf(
        137.toByte(), 80, 78, 71, 13, 10, 26, 10
    )
    const val TS_PACKET_SIZE = 188

    enum class SegmentKind {
        MPEG_TS,
        FRAGMENTED_MP4,
        PNG_WRAPPED_MPEG_TS,
        UNKNOWN
    }

    data class InspectionResult(
        val kind: SegmentKind,
        val data: ByteArray,
        val pngBytes: Int = 0,
        val transportStreamBytes: Int = 0
    )

    fun inspectSegment(bytes: ByteArray?): InspectionResult {
        if (bytes == null || bytes.isEmpty()) {
            return InspectionResult(SegmentKind.UNKNOWN, bytes ?: ByteArray(0))
        }

        if (validateTransportStream(bytes, 0, bytes.size)) {
            return InspectionResult(SegmentKind.MPEG_TS, bytes)
        }

        if (bytes.size >= 8) {
            val boxType = chunkName(bytes, 4)
            if (boxType == "ftyp" || boxType == "styp" || boxType == "moof") {
                return InspectionResult(SegmentKind.FRAGMENTED_MP4, bytes)
            }
        }

        if (!hasSignature(bytes, PNG_SIGNATURE)) {
            val syncStart = findAlignedTsSyncOffset(bytes, 0, bytes.size)
            if (syncStart > 0) {
                val recovered = bytes.copyOfRange(syncStart, bytes.size)
                return InspectionResult(
                    kind = SegmentKind.PNG_WRAPPED_MPEG_TS,
                    data = recovered,
                    pngBytes = syncStart,
                    transportStreamBytes = recovered.size
                )
            }
            return InspectionResult(SegmentKind.UNKNOWN, bytes)
        }

        val trailerStart = try {
            findPngTrailerStart(bytes, bytes.size)
        } catch (e: Exception) {
            val fallbackSync = findAlignedTsSyncOffset(bytes, PNG_SIGNATURE.size, bytes.size)
            if (fallbackSync > 0) fallbackSync else throw e
        }

        val trailerLength = bytes.size - trailerStart
        if (!validateTransportStream(bytes, trailerStart, trailerLength)) {
            val alignedSync = findAlignedTsSyncOffset(bytes, trailerStart, bytes.size)
            if (alignedSync >= trailerStart) {
                val recovered = bytes.copyOfRange(alignedSync, bytes.size)
                return InspectionResult(
                    kind = SegmentKind.PNG_WRAPPED_MPEG_TS,
                    data = recovered,
                    pngBytes = alignedSync,
                    transportStreamBytes = recovered.size
                )
            }
            throw IllegalArgumentException("PNG segment has no valid trailing MPEG-TS payload.")
        }

        val recovered = bytes.copyOfRange(trailerStart, bytes.size)
        return InspectionResult(
            kind = SegmentKind.PNG_WRAPPED_MPEG_TS,
            data = recovered,
            pngBytes = trailerStart,
            transportStreamBytes = recovered.size
        )
    }

    /**
     * Inspects an initial peek buffer (e.g. first 64 KB of a segment) without loading the entire
     * segment into RAM, returning the byte offset where the actual media payload begins (0 if no
     * disguised header needs stripping).
     */
    fun findPayloadStartOffsetInPrefix(buffer: ByteArray, length: Int): Int {
        if (length < 8) return 0

        // Standard MPEG-TS starting with 0x47
        if ((buffer[0].toInt() and 0xFF) == 0x47 &&
            (length < TS_PACKET_SIZE + 1 || (buffer[TS_PACKET_SIZE].toInt() and 0xFF) == 0x47)
        ) {
            return 0
        }

        // Standard HLS playlist (#EXTM3U) or WebVTT (WEBVTT) or ID3
        if ((buffer[0] == '#'.code.toByte() && buffer[1] == 'E'.code.toByte()) ||
            (buffer[0] == 'W'.code.toByte() && buffer[1] == 'E'.code.toByte()) ||
            (buffer[0] == 'I'.code.toByte() && buffer[1] == 'D'.code.toByte() && buffer[2] == '3'.code.toByte())
        ) {
            return 0
        }

        // Standard Fragmented MP4 / MP4 box
        val boxType = chunkName(buffer, 4)
        if (boxType == "ftyp" || boxType == "styp" || boxType == "moof" || boxType == "mdat") {
            return 0
        }

        // PNG-wrapped MPEG-TS segment
        if (hasSignature(buffer, PNG_SIGNATURE, length)) {
            try {
                val iendEnd = findPngTrailerStart(buffer, length)
                if (iendEnd in 1 until length) {
                    if ((buffer[iendEnd].toInt() and 0xFF) == 0x47) {
                        return iendEnd
                    }
                    val aligned = findAlignedTsSyncOffset(buffer, iendEnd, length)
                    if (aligned >= iendEnd) {
                        return aligned
                    }
                }
            } catch (_: Exception) {
                // Fall through to TS sync alignment scan
            }
        }

        // Fallback: scan prefix for 3 consecutive 188-byte-aligned 0x47 MPEG-TS sync bytes
        val syncOffset = findAlignedTsSyncOffset(buffer, 0, length)
        return if (syncOffset > 0) syncOffset else 0
    }

    fun hasPngSignature(bytes: ByteArray, length: Int = bytes.size): Boolean {
        return hasSignature(bytes, PNG_SIGNATURE, length)
    }

    private fun hasSignature(bytes: ByteArray, signature: ByteArray, limit: Int = bytes.size): Boolean {
        if (limit < signature.size || bytes.size < signature.size) return false
        for (i in signature.indices) {
            if (bytes[i] != signature[i]) return false
        }
        return true
    }

    private fun findAlignedTsSyncOffset(bytes: ByteArray, startOffset: Int, limit: Int): Int {
        val safeLimit = min(limit, bytes.size)
        val safeStart = startOffset.coerceAtLeast(0)
        // Require at least 2 full TS packets (376 bytes) to verify 0x47 sync byte alignment
        val maxSearch = safeLimit - TS_PACKET_SIZE * 2
        if (maxSearch < safeStart) return -1

        for (pos in safeStart..maxSearch) {
            if ((bytes[pos].toInt() and 0xFF) == 0x47 &&
                (bytes[pos + TS_PACKET_SIZE].toInt() and 0xFF) == 0x47
            ) {
                val thirdPos = pos + TS_PACKET_SIZE * 2
                if (thirdPos >= safeLimit || (bytes[thirdPos].toInt() and 0xFF) == 0x47) {
                    return pos
                }
            }
        }
        return -1
    }

    private fun readUint32(bytes: ByteArray, offset: Int): Long {
        val b0 = (bytes[offset].toInt() and 0xFF).toLong()
        val b1 = (bytes[offset + 1].toInt() and 0xFF).toLong()
        val b2 = (bytes[offset + 2].toInt() and 0xFF).toLong()
        val b3 = (bytes[offset + 3].toInt() and 0xFF).toLong()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun chunkName(bytes: ByteArray, offset: Int): String {
        if (offset < 0 || offset + 4 > bytes.size) return ""
        return String(
            charArrayOf(
                (bytes[offset].toInt() and 0xFF).toChar(),
                (bytes[offset + 1].toInt() and 0xFF).toChar(),
                (bytes[offset + 2].toInt() and 0xFF).toChar(),
                (bytes[offset + 3].toInt() and 0xFF).toChar()
            )
        )
    }

    private fun crc32(bytes: ByteArray, start: Int, end: Int): Long {
        val crc = CRC32()
        crc.update(bytes, start, end - start)
        return crc.value
    }

    private fun validateTransportStream(bytes: ByteArray, startOffset: Int, length: Int): Boolean {
        if (startOffset < 0 || length < TS_PACKET_SIZE * 2 || startOffset + length > bytes.size) {
            return false
        }
        if (length % TS_PACKET_SIZE != 0) {
            return false
        }
        val packetCount = min(5, length / TS_PACKET_SIZE)
        for (packet in 0 until packetCount) {
            val syncByte = bytes[startOffset + packet * TS_PACKET_SIZE].toInt() and 0xFF
            if (syncByte != 0x47) {
                return false
            }
        }
        return true
    }

    private fun findPngTrailerStart(bytes: ByteArray, limit: Int): Int {
        val safeLimit = min(limit, bytes.size)
        var offset = PNG_SIGNATURE.size
        var foundHeader = false

        while (offset + 12 <= safeLimit) {
            val lengthLong = readUint32(bytes, offset)
            val typeOffset = offset + 4
            val payloadOffset = typeOffset + 4
            if (lengthLong > (safeLimit - payloadOffset - 4).toLong() || lengthLong < 0) {
                throw IllegalArgumentException("PNG chunk extends beyond the segment boundary.")
            }
            val length = lengthLong.toInt()
            val name = chunkName(bytes, typeOffset)
            val crcOffset = payloadOffset + length
            val expectedCrc = readUint32(bytes, crcOffset)
            if (crc32(bytes, typeOffset, crcOffset) != expectedCrc) {
                throw IllegalArgumentException("PNG chunk checksum is invalid.")
            }

            if (!foundHeader) {
                if (name != "IHDR" || length != 13) {
                    throw IllegalArgumentException("PNG does not begin with a valid IHDR chunk.")
                }
                foundHeader = true
            } else if (name == "IHDR") {
                throw IllegalArgumentException("PNG contains a duplicate IHDR chunk.")
            }

            offset = crcOffset + 4
            if (name == "IEND") {
                if (length != 0) {
                    throw IllegalArgumentException("PNG IEND chunk must have an empty payload.")
                }
                return offset
            }
        }

        throw IllegalArgumentException("PNG IEND chunk is missing or truncated.")
    }
}
