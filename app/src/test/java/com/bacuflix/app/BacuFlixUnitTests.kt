package com.bacuflix.app

import com.bacuflix.app.data.KkphimApiService
import com.bacuflix.app.data.MlRecommendationService
import com.bacuflix.app.data.MovieMarkdownParser
import com.bacuflix.app.model.CatalogFilterState
import com.bacuflix.app.model.FilterSortOrder
import com.bacuflix.app.model.Movie
import com.bacuflix.app.player.SeekController
import com.bacuflix.app.player.SegmentInspector
import com.bacuflix.app.player.SubtitleParser
import com.bacuflix.app.util.BackupRestoreManager
import com.bacuflix.app.util.FormatTime
import com.bacuflix.app.util.UrlUtils
import org.json.JSONArray
import org.json.JSONObject
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

        val similarActionMovie = Movie(
            id = "sat-thu-bong-dem",
            slug = "sat-thu-bong-dem",
            title = "Sát Thủ Bóng Đêm",
            originTitle = "Shadow Assassin Action",
            year = "2026",
            categories = listOf("Hành Động", "Hình Sự")
        )
        val romanceMovie = Movie(
            id = "mua-he-nam-ay",
            slug = "mua-he-nam-ay",
            title = "Mùa Hè Năm Ấy",
            originTitle = "Our Beloved Summer",
            year = "2022",
            categories = listOf("Tình Cảm")
        )
        val simAction = MlRecommendationService.computeMovieSimilarity(actionMovie, similarActionMovie)
        val simRomance = MlRecommendationService.computeMovieSimilarity(actionMovie, romanceMovie)
        assertTrue(simAction.score > simRomance.score)
        assertTrue(simAction.matchPercent in 80..99)
    }

    @Test
    fun testMultiSourceServerParsingAndBackupRestore() {
        val ophimJson = JSONArray().put(
            JSONObject().apply {
                put("server_name", "Vietsub #1")
                put(
                    "server_data",
                    JSONArray().put(
                        JSONObject().apply {
                            put("name", "Tập 1")
                            put("slug", "tap-1")
                            put("link_m3u8", "https://ophim.example.com/ep1.m3u8")
                            put("link_embed", "https://ophim.example.com/embed1")
                        }
                    )
                )
            }
        )
        val vsmovJson = JSONArray().put(
            JSONObject().apply {
                put("server_name", "Vietsub #1")
                put(
                    "server_data",
                    JSONArray().put(
                        JSONObject().apply {
                            put("name", "Tập 1")
                            put("slug", "tap-1")
                            put("link_m3u8", "")
                            put("link_embed", "https://v15.streamvsmov.com/video/56a9cd2c-c556-41c3-8db1-f67ea531d0d9")
                        }
                    )
                )
            }
        )
        val vsmovServers = KkphimApiService.parseStandardServers(vsmovJson, "Phim4K")
        assertEquals(1, vsmovServers.size)
        assertEquals("Phim4K · Vietsub #1", vsmovServers[0].serverName)
        assertEquals(
            "https://v15.streamvsmov.com/stream/56a9cd2c-c556-41c3-8db1-f67ea531d0d9/master.m3u8",
            vsmovServers[0].episodes[0].m3u8
        )

        val nguoncJson = JSONArray().put(
            JSONObject().apply {
                put("server_name", "Thuyết Minh")
                put(
                    "items",
                    JSONArray().put(
                        JSONObject().apply {
                            put("name", "Full")
                            put("slug", "full")
                            put("m3u8", "https://nguonc.example.com/full.m3u8")
                            put("embed", "https://nguonc.example.com/embed")
                        }
                    )
                )
            }
        )
        val nguoncServers = KkphimApiService.parseNguoncServers(nguoncJson, "Nguonc")
        assertEquals(1, nguoncServers.size)
        assertEquals("Nguonc · Thuyết Minh", nguoncServers[0].serverName)
        assertEquals("https://nguonc.example.com/full.m3u8", nguoncServers[0].episodes[0].m3u8)

        // Backup & Restore JSON round-trip
        val favArr = JSONArray().put(JSONObject().put("slug", "lat-mat-7").put("title", "Lật Mặt 7"))
        val histArr = JSONArray().put(JSONObject().put("slug", "mua-do").put("title", "Mưa Đỏ"))
        val backupStr = BackupRestoreManager.createBackupJsonString(favArr, histArr)
        val parsed = BackupRestoreManager.parseBackupJsonString(backupStr)
        assertEquals(1, parsed.favorites.length())
        assertEquals(1, parsed.watchHistory.length())
        assertEquals("lat-mat-7", parsed.favorites.getJSONObject(0).getString("slug"))

        val defaultFilter = CatalogFilterState()
        assertFalse(defaultFilter.isActive)
        val activeFilter = CatalogFilterState(countrySlug = "han-quoc", sortOrder = FilterSortOrder.YEAR_DESC)
        assertTrue(activeFilter.isActive)
    }

    @Test
    fun testVsmovApiItemNormalizationAndCache() {
        // Sample item from https://vsmov.com/api-document
        val vsmovItem = JSONObject().apply {
            put("_id", 54305)
            put("name", "Thợ Săn Giác Thú Omegahorn")
            put("origin_name", "Kakuseihunter Omegahorn")
            put("slug", "tho-san-giac-thu-omegahorn")
            put("poster_url", "https://vsmov.com/storage/images/mH0oCFVu4YleTJYq77mrUgLrFoR.jpg")
            put("thumb_url", "AwAmCvdNBUuu0TriEmBb3ud6K1q.jpg")
            put("year", 2026)
        }

        val movie = KkphimApiService.normalizeMovie(vsmovItem, KkphimApiService.VSMOV_STORAGE_CDN, "Phim4K")
        assertNotNull(movie)
        assertEquals("tho-san-giac-thu-omegahorn", movie!!.slug)
        assertEquals("Thợ Săn Giác Thú Omegahorn", movie.title)
        assertEquals("https://vsmov.com/storage/images/mH0oCFVu4YleTJYq77mrUgLrFoR.jpg", movie.poster)
        assertEquals("https://vsmov.com/storage/images/AwAmCvdNBUuu0TriEmBb3ud6K1q.jpg", movie.thumb)
        assertEquals("2026", movie.year)
        assertEquals("Phim4K", movie.source)

        // Test VSMov Direct Stream Translation
        val vsmovEmbed = "https://v8.streamvsmov.com/video/629cc440-6ac6-4bcf-bb68-82f6d229313a"
        val translatedM3u8 = KkphimApiService.extractDirectM3u8FromEmbed(vsmovEmbed)
        assertEquals(
            "https://v8.streamvsmov.com/stream/629cc440-6ac6-4bcf-bb68-82f6d229313a/master.m3u8",
            translatedM3u8
        )
    }

    @Test
    fun testSmartSearchRankingAccuracyAndYearBoost() {
        val batman2019 = Movie(
            id = "batman-2019",
            slug = "batman-2019",
            title = "Lego Batman",
            originTitle = "Lego Batman: Family Matters",
            year = "2019"
        )
        val batman2022 = Movie(
            id = "the-batman-2022",
            slug = "the-batman-2022",
            title = "The Batman",
            originTitle = "The Batman 2022",
            year = "2022"
        )

        // Query contains year 2022
        val score2019 = com.bacuflix.app.data.SmartSearchEngine.scoreMovieMatch(batman2019, "batman 2022", "batman 2022")
        val score2022 = com.bacuflix.app.data.SmartSearchEngine.scoreMovieMatch(batman2022, "batman 2022", "batman 2022")
        assertTrue("Batman 2022 should score significantly higher when searching 'batman 2022'", score2022 > score2019)

        // Unaccented query matches Vietnamese movie
        val spiderMan = Movie(
            id = "nguoi-nhen-khong-con-nha",
            slug = "nguoi-nhen-khong-con-nha",
            title = "Người Nhện: Không Còn Nhà",
            originTitle = "Spider-Man: No Way Home",
            year = "2021"
        )
        val scoreUnaccented = com.bacuflix.app.data.SmartSearchEngine.scoreMovieMatch(spiderMan, "nguoi nhen", "nguoi nhen")
        assertTrue("Unaccented search 'nguoi nhen' must strongly match 'Người Nhện'", scoreUnaccented >= 400.0)
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
