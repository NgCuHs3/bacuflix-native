package com.bacuflix.app

import com.bacuflix.app.data.MlRecommendationService
import com.bacuflix.app.data.MovieMarkdownParser
import com.bacuflix.app.model.Movie
import com.bacuflix.app.player.SegmentInspector
import com.bacuflix.app.player.SeekController
import com.bacuflix.app.player.SubtitleParser
import com.bacuflix.app.util.FormatTime
import com.bacuflix.app.util.UrlUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.zip.CRC32

class BacuFlixUnitTests {

    @Test
    fun testMovieMarkdownParser() {
        val sampleMarkdown = """
            ## Movie: Lật Mặt 7: Một Điều Ước
            - id: lat-mat-7
            - poster: https://phimimg.com/upload/poster-lat-mat-7.jpg
            - m3u8: https://stream.example.com/lat-mat-7/index.m3u8
            - subtitle: https://stream.example.com/lat-mat-7/vi.vtt
            - year: 2024
            - rating: 8.9
            - description: Câu chuyện cảm động về bà Hai và 5 người con.

            ## Movie: Phim Lỗi Thiếu M3U8
            - id: broken-movie
            - poster: https://phimimg.com/upload/broken.jpg
        """.trimIndent()

        val result = MovieMarkdownParser.parseMovieMarkdown(sampleMarkdown)
        assertEquals(2, result.movies.size)

        val validMovie = result.movies[0]
        assertEquals("lat-mat-7", validMovie.id)
        assertEquals("Lật Mặt 7: Một Điều Ước", validMovie.title)
        assertTrue(validMovie.playbackEnabled)
        assertEquals(2024, validMovie.year)

        val invalidMovie = result.movies[1]
        assertEquals("broken-movie", invalidMovie.id)
        assertFalse(invalidMovie.playbackEnabled)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun testSegmentInspectorPngWrappedMpegTs() {
        // Construct a minimal valid PNG (signature + IHDR + IEND) followed by 2 MPEG-TS 188-byte packets
        val pngSig = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        val ihdrData = ByteArray(13)
        val ihdrChunk = buildPngChunk("IHDR", ihdrData)
        val iendChunk = buildPngChunk("IEND", ByteArray(0))

        val tsPayload = ByteArray(188 * 2)
        tsPayload[0] = 0x47
        tsPayload[188] = 0x47
        tsPayload[1] = 0x1F

        val combined = pngSig + ihdrChunk + iendChunk + tsPayload

        val inspection = SegmentInspector.inspectMediaSegment(combined)
        assertEquals("png-wrapped-mpeg-ts", inspection.kind)
        assertEquals(pngSig.size + ihdrChunk.size + iendChunk.size, inspection.payloadOffset)

        val stripped = SegmentInspector.stripLeadingPngHeader(combined)
        assertArrayEquals(tsPayload, stripped)
    }

    @Test
    fun testSubtitleParserSrtAndVtt() {
        val srtText = """
            1
            00:00:01,000 --> 00:00:04,500
            Xin chào khán giả BACUFlix!

            2
            00:00:05,000 --> 00:00:08,200
            <i>Tập phim bắt đầu ngay bây giờ.</i>
        """.trimIndent()

        val cues = SubtitleParser.parseSubtitleText(srtText)
        assertEquals(2, cues.size)
        assertEquals(1.0, cues[0].start, 0.001)
        assertEquals(4.5, cues[0].end, 0.001)
        assertEquals("Xin chào khán giả BACUFlix!", SubtitleParser.getActiveSubtitleText(cues, 2.5))
        assertEquals("Tập phim bắt đầu ngay bây giờ.", SubtitleParser.getActiveSubtitleText(cues, 6.0))
        assertEquals("", SubtitleParser.getActiveSubtitleText(cues, 10.0))
    }

    @Test
    fun testSeekControllerAndFormatTime() {
        assertEquals(0.0, SeekController.computeRelativeSeekTarget(5.0, 100.0, -10.0), 0.001)
        assertEquals(95.0, SeekController.computeRelativeSeekTarget(85.0, 100.0, 10.0), 0.001)
        assertEquals(100.0, SeekController.computeRelativeSeekTarget(95.0, 100.0, 10.0), 0.001)

        assertEquals("00:00", FormatTime.formatTime(0.0))
        assertEquals("01:05", FormatTime.formatTime(65.0))
        assertEquals("01:01:01", FormatTime.formatTime(3661.0))
    }

    @Test
    fun testUrlNormalizationAndShareUrl() {
        assertEquals(
            "https://phimimg.com/upload/vod/poster.jpg",
            UrlUtils.normalizeImageUrl("upload/vod/poster.jpg")
        )
        assertEquals(
            "https://bacuflix.web.app/?movie=mua-do&ep=3",
            UrlUtils.buildShareUrl("mua-do", 3)
        )
        assertEquals(
            "Một bộ phim hành động gay cấn.",
            UrlUtils.stripHtmlTags("<p>Một bộ phim <b>hành động</b> gay cấn.</p>")
        )
    }

    @Test
    fun testMlRecommendationTokenizerAndClassifier() {
        val tokens = MlRecommendationService.extractTokens("Phim hành động võ thuật Hàn Quốc 2026")
        assertTrue(tokens.contains("hanh"))
        assertTrue(tokens.contains("dong"))
        assertTrue(tokens.contains("hanh_dong"))

        val actionMovie = Movie(
            id = "sat-thu-vo-cuc",
            slug = "sat-thu-vo-cuc",
            title = "Sát Thủ Vô Cực",
            originName = "Infinite Assassin Action",
            year = 2026,
            categories = listOf("hanh-dong", "hinh-su")
        )
        val genreDist = MlRecommendationService.inferMovieGenreDistribution(actionMovie)
        assertNotNull(genreDist["hanh-dong"])
        assertTrue((genreDist["hanh-dong"] ?: 0.0) > 0.25)
    }

    private fun buildPngChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val crcVal = crc.value.toInt()

        val buf = ByteBuffer.allocate(4 + 4 + data.size + 4)
        buf.putInt(data.size)
        buf.put(typeBytes)
        buf.put(data)
        buf.putInt(crcVal)
        return buf.array()
    }
}
