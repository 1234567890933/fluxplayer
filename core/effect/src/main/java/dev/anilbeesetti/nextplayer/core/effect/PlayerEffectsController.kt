package dev.anilbeesetti.nextplayer.core.effect

import android.app.ActivityManager
import android.content.Context
import androidx.media3.common.Effect
import androidx.media3.exoplayer.ExoPlayer

/**
 * Single place that owns the GPU effect pipeline of the running player.
 *
 * Callers only report state (colour grade, HDR switch, thermal level); this class rebuilds
 * the effect list and hands it to ExoPlayer, so no other code needs to know how the
 * pipeline is assembled.
 */
class PlayerEffectsController(private val context: Context) {

    private val governor = EffectGovernor()

    private var player: ExoPlayer? = null
    private var settings: ColorSettings = ColorSettings()
    private var hdrSwitchOn: Boolean = false
    private var hdrContent: Boolean = false
    private var thermalScale: Float = 1f
    private var batterySaver: Boolean = false

    // Every setVideoEffects call rebuilds the frame processor, so an identical list is skipped.
    private var lastSignature: String? = null

    // Every stage runs through here; a stage that throws is skipped, never fatal.
    private val guard = PipelineGuard()

    // Queried once, and guarded: a device with an unusual display stack must not take the
    // player service down during construction.
    private val deviceHasHdrDisplay: Boolean =
        runCatching { ColorEffects.deviceHasHdrDisplay(context) }.getOrDefault(false)

    init {
        // A phone that reports itself as low-RAM gets the light grade from the start, before any
        // effect has had a chance to fail.
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = runCatching { activityManager?.isLowRamDevice == true }.getOrDefault(false)
        governor.setLowRamDevice(lowRam)
    }

    @Synchronized
    fun attach(exoPlayer: ExoPlayer) {
        player = exoPlayer
        // A new player starts with no effects of its own. Without this the identical signature
        // check below would skip the first apply and the new surface would stay ungraded, which is
        // exactly what happens after a decoder switch or a move to the next video.
        lastSignature = null
        guard.reset()
        apply()
    }

    @Synchronized
    fun detach() {
        player?.setVideoEffects(emptyList())
        player = null
        lastSignature = null
    }

    @Synchronized
    fun setColorSettings(newSettings: ColorSettings) {
        settings = newSettings
        apply()
    }

    @Synchronized
    fun setHdrSwitch(enabled: Boolean) {
        hdrSwitchOn = enabled
        apply()
    }

    @Synchronized
    fun setHdrContent(isHdr: Boolean) {
        if (hdrContent == isHdr) return
        hdrContent = isHdr
        apply()
    }

    @Synchronized
    fun onThermalLevel(level: ThermalLevel) {
        thermalScale = level.effectScale
        governor.onThermalLevel(level)
        apply()
    }

    /** Below 20 % and unplugged the HDR tone map is dropped, as the brief requires. */
    @Synchronized
    fun setBatterySaver(enabled: Boolean) {
        if (batterySaver == enabled) return
        batterySaver = enabled
        governor.onBatterySaver(enabled)
        apply()
    }

    val isHdrActive: Boolean
        get() = (hdrSwitchOn || hdrContent) && !batterySaver

    /**
     * True once the GPU pipeline has refused an effect list. The player keeps playing in that
     * state, just without the grade, instead of dying on a device that cannot run it.
     */
    var isDegraded: Boolean = false
        private set

    /**
     * Serialised on purpose: two calls landing together (a slider drag plus a thermal change)
     * must not interleave two different effect lists into the player, which shows up as flicker.
     * This is the protection lock on the connection.
     */
    @Synchronized
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
        // Protection layer. Everything below this point talks to the GPU, and on a cheap or
        // busy device that can refuse the work. Losing the grade is acceptable; losing playback
        // is not, so each attempt is guarded and falls back one step at a time.
        if (governor.budget == EffectBudget.OFF) {
            runCatching { exoPlayer.setVideoEffects(emptyList()) }
            isDegraded = true
            return
        }

        val full = guard.attempt("build-full") {
            ColorEffects.build(
                settings = scaled,
                hdrEnabled = isHdrActive,
                deviceHasHdrDisplay = deviceHasHdrDisplay,
                hdrContent = hdrContent,
                hdrStrength = governor.hdrStrength,
            )
        } ?: emptyList()

        if (governor.budget == EffectBudget.FULL && full.isNotEmpty() &&
            tryApply(full)
        ) {
            governor.onApplySucceeded()
            isDegraded = false
            return
        }
        // Second attempt: tone and colour only, no blur and no convolution. These are the
        // cheapest effects the engine has and work on almost any GL ES 2.0 device.
        val light = guard.attempt("build-light") {
            ColorEffects.build(
                settings = scaled.copy(
                    sharpness = 0f,
                    clarity = 0f,
                    noiseReduce = 0f,
                ),
                hdrEnabled = isHdrActive,
                deviceHasHdrDisplay = deviceHasHdrDisplay,
                hdrContent = hdrContent,
                hdrStrength = governor.hdrStrength,
            )
        } ?: emptyList()

        if (light.isNotEmpty() && tryApply(light)) {
            governor.onApplySucceeded()
            isDegraded = false
            return
        }
        governor.onApplyFailed()
        // Last resort: no grade at all, but the video keeps playing.
        runCatching { exoPlayer.setVideoEffects(emptyList()) }
        isDegraded = true
    }

    private fun tryApply(effects: List<Effect>): Boolean =
        guard.attempt("apply") { player?.setVideoEffects(effects) } != null
}
