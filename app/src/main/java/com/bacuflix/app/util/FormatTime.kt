package com.bacuflix.app.util

import kotlin.math.floor

object FormatTime {
    fun formatTime(seconds: Double): String {
        if (!seconds.isFinite() || seconds < 0.0) {
            return "0:00"
        }
        val total = floor(seconds).toLong()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        val paddedMinutes = if (hours > 0 && minutes < 10) "0$minutes" else minutes.toString()
        val paddedSeconds = if (secs < 10) "0$secs" else secs.toString()
        return if (hours > 0) {
            "$hours:$paddedMinutes:$paddedSeconds"
        } else {
            "$paddedMinutes:$paddedSeconds"
        }
    }

    fun formatTime(seconds: Int): String = formatTime(seconds.toDouble())
    fun formatTime(seconds: Long): String = formatTime(seconds.toDouble())
}
