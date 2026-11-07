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
    private const val TS_PACKET_SIZE = 188

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
            return InspectionResult(SegmentKind.UNKNOWN, bytes)
        }

        val trailerStart = findPngTrailerStart(bytes)
        val trailerLength = bytes.size - trailerStart
        if (!validateTransportStream(bytes, trailerStart, trailerLength)) {
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

    private fun hasSignature(bytes: ByteArray, signature: ByteArray): Boolean {
        if (bytes.size < signature.size) return false
        for (i in signature.indices) {
            if (bytes[i] != signature[i]) return false
        }
        return true
    }

    private fun readUint32(bytes: ByteArray, offset: Int): Long {
        val b0 = (bytes[offset].toInt() and 0xFF).toLong()
        val b1 = (bytes[offset + 1].toInt() and 0xFF).toLong()
        val b2 = (bytes[offset + 2].toInt() and 0xFF).toLong()
        val b3 = (bytes[offset + 3].toInt() and 0xFF).toLong()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun chunkName(bytes: ByteArray, offset: Int): String {
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
        if (length < TS_PACKET_SIZE * 2 || length % TS_PACKET_SIZE != 0) {
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

    private fun findPngTrailerStart(bytes: ByteArray): Int {
        var offset = PNG_SIGNATURE.size
        var foundHeader = false

        while (offset + 12 <= bytes.size) {
            val lengthLong = readUint32(bytes, offset)
            val typeOffset = offset + 4
            val payloadOffset = typeOffset + 4
            if (lengthLong > (bytes.size - payloadOffset - 4).toLong() || lengthLong < 0) {
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
