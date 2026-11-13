package com.bacuflix.app.player

import com.bacuflix.app.model.SubtitleCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor

/**
 * SRT / WebVTT subtitle parser and remote loader ported from
 * js/player/subtitle-parser.js and js/player/subtitle-service.js.
 */
object SubtitleParser {
    private val FULL_TIME_REGEX = Regex("^(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})")
    private val SHORT_TIME_REGEX = Regex("^(\\d{1,2}):(\\d{2})[,.](\\d{1,3})")

    fun parseTimestamp(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        FULL_TIME_REGEX.find(trimmed)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: return null
            val minutes = match.groupValues[2].toIntOrNull() ?: return null
            val seconds = match.groupValues[3].toIntOrNull() ?: return null
            val millis = match.groupValues[4].padEnd(3, '0').take(3).toIntOrNull() ?: 0
            return hours * 3600.0 + minutes * 60.0 + seconds + millis / 1000.0
        }
        SHORT_TIME_REGEX.find(trimmed)?.let { match ->
            val minutes = match.groupValues[1].toIntOrNull() ?: return null
            val seconds = match.groupValues[2].toIntOrNull() ?: return null
            val millis = match.groupValues[3].padEnd(3, '0').take(3).toIntOrNull() ?: 0
            return minutes * 60.0 + seconds + millis / 1000.0
        }
        return null
    }

    fun formatVttTimestamp(seconds: Double): String {
        if (!seconds.isFinite() || seconds < 0.0) return "00:00:00.000"
        val total = floor(seconds).toLong()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        val millis = floor((seconds - total) * 1000.0).toLong()
        return "%02d:%02d:%02d.%03d".format(hours, minutes, secs, millis)
    }

    fun stripSubtitleHtml(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return text
            .replace(Regex("\\{[^}]*}"), "")
            .replace(Regex("<script\\b[^<]*(?:(?!</script>)<[^<]*)*</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style\\b[^<]*(?:(?!</style>)<[^<]*)*</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }

    fun parseSrt(rawText: String?): List<SubtitleCue> {
        if (rawText.isNullOrBlank()) return emptyList()
        val clean = rawText
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val blocks = clean.split(Regex("\n\\s*\n+"))
        val cues = mutableListOf<SubtitleCue>()

        blocks.forEachIndexed { blockIndex, block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return@forEachIndexed

            val timeLineIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeLineIndex == -1) return@forEachIndexed

            val timeParts = lines[timeLineIndex].split("-->")
            if (timeParts.size != 2) return@forEachIndexed

            val start = parseTimestamp(timeParts[0])
            val endRaw = timeParts[1].trim().split(Regex("\\s+")).firstOrNull()
            val end = parseTimestamp(endRaw)

            if (start == null || end == null || end <= start) return@forEachIndexed

            val dialogueLines = lines.drop(timeLineIndex + 1)
            val dialogue = dialogueLines.map { stripSubtitleHtml(it) }.filter { it.isNotEmpty() }.joinToString("\n")
            if (dialogue.isNotEmpty()) {
                cues.add(
                    SubtitleCue(
                        id = blockIndex + 1,
                        startSeconds = start,
                        endSeconds = end,
                        text = dialogue
                    )
                )
            }
        }

        return cues.sortedBy { it.startSeconds }
    }

    fun findActiveCues(cues: List<SubtitleCue>, currentTimeSeconds: Double): List<SubtitleCue> {
        if (cues.isEmpty() || currentTimeSeconds < 0.0) return emptyList()
        val active = mutableListOf<SubtitleCue>()
        for (cue in cues) {
            if (cue.startSeconds <= currentTimeSeconds && currentTimeSeconds <= cue.endSeconds) {
                active.add(cue)
            } else if (cue.startSeconds > currentTimeSeconds) {
                break
            }
        }
        return active
    }

    fun getActiveSubtitleText(cues: List<SubtitleCue>, currentTimeSeconds: Double): String {
        val active = findActiveCues(cues, currentTimeSeconds)
        if (active.isEmpty()) return ""
        return active.joinToString("\n") { it.text }
    }

    suspend fun loadSubtitleFromUrl(subtitleUrl: String?): List<SubtitleCue> = withContext(Dispatchers.IO) {
        if (subtitleUrl.isNullOrBlank()) return@withContext emptyList()
        val targetUrl = com.bacuflix.app.data.KkphimApiService.resolveStreamUrl(subtitleUrl)
        try {
            val conn = (URL(targetUrl.trim()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            try {
                if (conn.responseCode !in 200..299) return@withContext emptyList()
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                parseSrt(text)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
