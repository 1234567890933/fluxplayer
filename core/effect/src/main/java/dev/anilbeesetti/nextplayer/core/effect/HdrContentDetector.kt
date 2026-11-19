package dev.anilbeesetti.nextplayer.core.effect

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks

/**
 * Reads the real colour metadata of the playing video.
 * ST2084 (PQ) is HDR10, HLG is broadcast HDR; everything else is treated as SDR.
 */
object HdrContentDetector {

    fun isHdr(format: Format?): Boolean {
        val colorInfo = format?.colorInfo ?: return false
        return colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
            colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
    }

    /** First selected video format of the current tracks, or null when nothing is selected. */
    fun selectedVideoFormat(tracks: Tracks?): Format? {
        tracks ?: return null
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            for (index in 0 until group.length) {
                if (group.isTrackSelected(index)) return group.getTrackFormat(index)
            }
        }
        return null
    }
}
