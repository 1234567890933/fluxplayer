package dev.anilbeesetti.nextplayer.core.effect

import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Turns dropped-frame counts into a decision: keep quality, reduce it, or switch effects off.
 * Counting happens in a two second sliding window, as the brief specifies.
 */
class FrameDropGuard(
    private val onQualityReduced: () -> Unit,
    private val onEffectsDisabled: () -> Unit,
) : AnalyticsListener {

    private var windowStartMs: Long = 0L
    private var droppedInWindow: Int = 0

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long,
    ) {
        val now = System.currentTimeMillis()
        if (now - windowStartMs > WINDOW_MS) {
            windowStartMs = now
            droppedInWindow = 0
        }
        droppedInWindow += droppedFrames
        when {
            droppedInWindow > DISABLE_THRESHOLD -> onEffectsDisabled()
            droppedInWindow > REDUCE_THRESHOLD -> onQualityReduced()
        }
    }

    companion object {
        const val WINDOW_MS = 2_000L
        const val REDUCE_THRESHOLD = 5
        const val DISABLE_THRESHOLD = 10
    }
}
