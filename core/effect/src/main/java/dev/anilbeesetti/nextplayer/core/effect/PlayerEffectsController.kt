package dev.anilbeesetti.nextplayer.core.effect

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

/**
 * Single place that owns the GPU effect pipeline of the running player.
 *
 * Callers only report state (colour grade, HDR switch, thermal level); this class rebuilds
 * the effect list and hands it to ExoPlayer, so no other code needs to know how the
 * pipeline is assembled.
 */
class PlayerEffectsController(private val context: Context) {

    private var player: ExoPlayer? = null
    private var settings: ColorSettings = ColorSettings()
    private var hdrSwitchOn: Boolean = false
    private var hdrContent: Boolean = false
    private var thermalScale: Float = 1f
    private var batterySaver: Boolean = false

    private val deviceHasHdrDisplay: Boolean = ColorEffects.deviceHasHdrDisplay(context)

    fun attach(exoPlayer: ExoPlayer) {
        player = exoPlayer
        apply()
    }

    fun detach() {
        player?.setVideoEffects(emptyList())
        player = null
    }

    fun setColorSettings(newSettings: ColorSettings) {
        settings = newSettings
        apply()
    }

    fun setHdrSwitch(enabled: Boolean) {
        hdrSwitchOn = enabled
        apply()
    }

    fun setHdrContent(isHdr: Boolean) {
        if (hdrContent == isHdr) return
        hdrContent = isHdr
        apply()
    }

    fun onThermalLevel(level: ThermalLevel) {
        thermalScale = level.effectScale
        apply()
    }

    /** Below 20 % and unplugged the HDR tone map is dropped, as the brief requires. */
    fun setBatterySaver(enabled: Boolean) {
        if (batterySaver == enabled) return
        batterySaver = enabled
        apply()
    }

    val isHdrActive: Boolean
        get() = (hdrSwitchOn || hdrContent) && !batterySaver

    private fun apply() {
        val exoPlayer = player ?: return
        val scaled = ColorSettings(
            brightness = settings.brightness * thermalScale,
            contrast = 1f + (settings.contrast - 1f) * thermalScale,
            saturation = 1f + (settings.saturation - 1f) * thermalScale,
            sharpness = settings.sharpness * thermalScale,
            clarity = settings.clarity * thermalScale,
            noiseReduce = settings.noiseReduce * thermalScale,
            temperature = settings.temperature,
            preset = settings.preset,
        )
        exoPlayer.setVideoEffects(
            ColorEffects.build(
                settings = scaled,
                hdrEnabled = isHdrActive,
                deviceHasHdrDisplay = deviceHasHdrDisplay,
            ),
        )
    }
}
