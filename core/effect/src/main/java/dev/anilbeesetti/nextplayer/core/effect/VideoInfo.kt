package dev.anilbeesetti.nextplayer.core.effect

import androidx.media3.common.C
import androidx.media3.common.Format

/** The facts shown in the video info overlay, read from the real track metadata. */
data class VideoInfo(
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val codec: String?,
    val hdrLabel: String,
) {
    val isHdr: Boolean get() = hdrLabel != SDR
    val resolutionLabel: String get() = "${width}x$height"
    val frameRateLabel: String get() = "${frameRate.toInt()} fps"
    val codecLabel: String get() = codec ?: "unknown"

    companion object {
        const val SDR = "SDR"
        const val HDR10 = "HDR10"
        const val HLG = "HLG"

        fun of(format: Format?): VideoInfo? {
            format ?: return null
            val label = when (format.colorInfo?.colorTransfer) {
                C.COLOR_TRANSFER_ST2084 -> HDR10
                C.COLOR_TRANSFER_HLG -> HLG
                else -> SDR
            }
            return VideoInfo(
                width = format.width,
                height = format.height,
                frameRate = format.frameRate,
                codec = format.codecs,
                hdrLabel = label,
            )
        }
    }
}
