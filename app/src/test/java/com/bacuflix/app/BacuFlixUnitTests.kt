package com.bacuflix.app

import com.bacuflix.app.data.KkphimApiService
import com.bacuflix.app.data.MlRecommendationService
import com.bacuflix.app.data.MovieMarkdownParser
import com.bacuflix.app.model.Movie
import com.bacuflix.app.player.SeekController
import com.bacuflix.app.player.SegmentInspector
import com.bacuflix.app.player.SubtitleParser
import com.bacuflix.app.util.FormatTime
import com.bacuflix.app.util.UrlUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.zip.CRC32

class BacuFlixUnitTests {

    @Test
    fun testMovieMarkdownParser() {
        val sampleMarkdown = """
            ## Movie
            - title: Lật Mặt 7: Một Điều Ước
            - poster: https://phimimg.com/upload/poster-lat-mat-7.jpg
            - m3u8: https://stream.example.com/lat-mat-7/index.m3u8
            - subtitle: https://stream.example.com/lat-mat-7/vi.vtt
            - source: KKPhim

            ## Movie
            - title: Phim Lỗi Thiếu M3U8
            - poster: https://phimimg.com/upload/broken.jpg
        """.trimIndent()

        val result = MovieMarkdownParser.parseMovieMarkdown(
            sampleMarkdown,
            MovieMarkdownParser.ParseOptions(
                catalogSourceId = "unit-test",
                playbackEnabled = true
            )
        )
        assertEquals(1, result.movies.size)

        val validMovie = result.movies[0]
        assertEquals("Lật Mặt 7: Một Điều Ước", validMovie.title)
        assertTrue(validMovie.playbackEnabled)
        assertEquals("https://stream.example.com/lat-mat-7/index.m3u8", validMovie.m3u8)
        assertEquals("https://stream.example.com/lat-mat-7/vi.vtt", validMovie.subtitle)
        assertTrue(result.diagnostics.isNotEmpty())
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

        val inspection = SegmentInspector.inspectSegment(combined)
        assertEquals(SegmentInspector.SegmentKind.PNG_WRAPPED_MPEG_TS, inspection.kind)
        assertEquals(pngSig.size + ihdrChunk.size + iendChunk.size, inspection.pngBytes)
        assertArrayEquals(tsPayload, inspection.data)
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

        val cues = SubtitleParser.parseSrt(srtText)
        assertEquals(2, cues.size)
        assertEquals(1.0, cues[0].startSeconds, 0.001)
        assertEquals(4.5, cues[0].endSeconds, 0.001)
        assertEquals("Xin chào khán giả BACUFlix!", SubtitleParser.getActiveSubtitleText(cues, 2.5))
        assertEquals("Tập phim bắt đầu ngay bây giờ.", SubtitleParser.getActiveSubtitleText(cues, 6.0))
        assertEquals("", SubtitleParser.getActiveSubtitleText(cues, 10.0))
    }

    @Test
    fun testSeekControllerAndFormatTime() {
        assertEquals(0.0, SeekController.computeRelativeSeekTarget(5.0, 100.0, -10.0), 0.001)
        assertEquals(95.0, SeekController.computeRelativeSeekTarget(85.0, 100.0, 10.0), 0.001)
        assertEquals(100.0, SeekController.computeRelativeSeekTarget(95.0, 100.0, 10.0), 0.001)

        assertEquals("0:00", FormatTime.formatTime(0.0))
        assertEquals("1:05", FormatTime.formatTime(65.0))
        assertEquals("1:01:01", FormatTime.formatTime(3661.0))
    }

    @Test
    fun testUrlNormalizationAndShareUrl() {
        assertEquals(
            "https://phimimg.com/upload/vod/poster.jpg",
            KkphimApiService.buildImageUrl("upload/vod/poster.jpg")
        )
        assertEquals(
            "https://ngcuhs3.github.io/bacuflix/?movie=mua-do&ep=3",
            UrlUtils.buildEpisodeUrl("mua-do", 3)
        )
        assertEquals(
            "Một bộ phim hành động gay cấn.",
            UrlUtils.stripHtmlTags("<p>Một bộ phim <b>hành động</b> gay cấn.</p>")
        )
    }

    @Test
    fun testMlRecommendationTokenizerAndClassifier() {
        val tokens = MlRecommendationService.tokenizeMovieText("Phim hành động võ thuật Hàn Quốc 2026")
        assertTrue(tokens.unigrams.contains("hành"))
        assertTrue(tokens.unigrams.contains("động"))
        assertTrue(tokens.bigrams.contains("hành động"))

        val actionMovie = Movie(
            id = "sat-thu-vo-cuc",
            slug = "sat-thu-vo-cuc",
            title = "Sát Thủ Vô Cực",
            originTitle = "Infinite Assassin Action",
            year = "2026",
            categories = listOf("Hành Động", "Hình Sự")
        )
        val latentGenres = MlRecommendationService.inferLatentGenres(actionMovie)
        assertTrue(latentGenres.any { it.slug == "hanh-dong" && it.weight > 0.5 })
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
