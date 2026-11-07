package com.bacuflix.app.player

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Normalized seek preview, multi-tap acceleration, and direct scrub controller.
 * Ported from js/player/seek-controller.js.
 */
class SeekController(
    private val getDurationSeconds: () -> Double,
    private val getCurrentTimeSeconds: () -> Double,
    private val onPreview: (progress: Float, targetTimeSeconds: Double, meta: SeekMeta) -> Unit,
    private val onCommit: (targetTimeSeconds: Double, meta: SeekMeta) -> Unit
) {
    data class SeekMeta(
        val direction: Int,
        val isTap: Boolean = false,
        val tapCount: Int = 1,
        val percentage: Double,
        val deltaSeconds: Int,
        val targetTimeSeconds: Double,
        val isDirect: Boolean = false
    )

    private var scrubbing = false
    private var isDirectScrub = false
    private var previewProgress = 0.0
    private var startProgress = 0.0
    private var direction = 0
    private var lastTapTimestamp = 0L
    private var tapCount = 0

    fun isScrubbing(): Boolean = scrubbing

    fun seekBy(seconds: Double) {
        val duration = getDurationSeconds()
        if (duration <= 0.0) return
        val currentTime = getCurrentTimeSeconds()
        val targetTime = max(0.0, min(duration, currentTime + seconds))
        val progress = (targetTime / duration).coerceIn(0.0, 1.0)
        val meta = SeekMeta(
            direction = if (seconds < 0) -1 else 1,
            isTap = true,
            percentage = progress * 100.0,
            deltaSeconds = (targetTime - currentTime).roundToInt(),
            targetTimeSeconds = targetTime
        )
        onPreview(progress.toFloat(), targetTime, meta)
        onCommit(targetTime, meta)
    }

    fun stepKeyTap(stepDirection: Int) {
        val duration = getDurationSeconds()
        if (duration <= 0.0) return
        val currentTime = getCurrentTimeSeconds()
        val now = System.currentTimeMillis()
        if (now - lastTapTimestamp < 650L) {
            tapCount = min(5, tapCount + 1)
        } else {
            tapCount = 1
        }
        lastTapTimestamp = now

        val stepMultipliers = doubleArrayOf(0.01, 0.02, 0.04, 0.06, 0.08)
        val stepPct = stepMultipliers[min(tapCount - 1, stepMultipliers.size - 1)]
        val minStepSeconds = 10.0 * tapCount
        val effectiveSeconds = max(minStepSeconds, duration * stepPct)

        val dir = if (stepDirection < 0) -1 else 1
        val targetTime = max(0.0, min(duration, currentTime + dir * effectiveSeconds))
        val progress = (targetTime / duration).coerceIn(0.0, 1.0)
        val meta = SeekMeta(
            direction = dir,
            isTap = true,
            tapCount = tapCount,
            percentage = progress * 100.0,
            deltaSeconds = (targetTime - currentTime).roundToInt(),
            targetTimeSeconds = targetTime
        )
        onPreview(progress.toFloat(), targetTime, meta)
        onCommit(targetTime, meta)
    }

    fun setDirectPreview(progress: Float) {
        val duration = getDurationSeconds()
        if (duration <= 0.0) return
        val clamped = progress.toDouble().coerceIn(0.0, 1.0)
        if (!isDirectScrub) {
            startProgress = (getCurrentTimeSeconds() / duration).coerceIn(0.0, 1.0)
        }
        scrubbing = true
        isDirectScrub = true
        previewProgress = clamped
        direction = if (previewProgress >= startProgress) 1 else -1
        val finalTime = previewProgress * duration
        val meta = SeekMeta(
            direction = direction,
            percentage = previewProgress * 100.0,
            deltaSeconds = (finalTime - startProgress * duration).roundToInt(),
            targetTimeSeconds = finalTime,
            isDirect = true
        )
        onPreview(previewProgress.toFloat(), finalTime, meta)
    }

    fun commitPreview() {
        if (!scrubbing) return
        val duration = getDurationSeconds()
        if (duration <= 0.0) {
            cancel()
            return
        }
        val finalTime = previewProgress * duration
        val meta = SeekMeta(
            direction = direction,
            percentage = previewProgress * 100.0,
            deltaSeconds = (finalTime - startProgress * duration).roundToInt(),
            targetTimeSeconds = finalTime,
            isDirect = isDirectScrub
        )
        scrubbing = false
        isDirectScrub = false
        direction = 0
        onPreview(previewProgress.toFloat(), finalTime, meta)
        onCommit(finalTime, meta)
    }

    fun cancel() {
        scrubbing = false
        isDirectScrub = false
        direction = 0
    }

    companion object {
        fun computeRelativeSeekTarget(
            currentSeconds: Double,
            durationSeconds: Double,
            deltaSeconds: Double
        ): Double {
            val safeCurrent = if (currentSeconds.isFinite() && currentSeconds >= 0.0) currentSeconds else 0.0
            val target = max(0.0, safeCurrent + deltaSeconds)
            return if (durationSeconds.isFinite() && durationSeconds > 0.0) {
                min(durationSeconds, target)
            } else {
                target
            }
        }
    }
}
