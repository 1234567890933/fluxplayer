package dev.anilbeesetti.nextplayer.core.effect

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.ConvolutionFunction1D
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.SeparableConvolution

/**
 * Builds the GPU effect pipeline for the current colour grade and HDR state.
 *
 * Everything here is a Media3 `GlEffect`, so the work happens on the GPU inside
 * ExoPlayer's video compositor. No frame is touched on the CPU.
 */
object ColorEffects {

    private const val HDR_TYPE_HDR10 = 2
    private const val HDR_TYPE_HLG = 3

    /** Rec.709 luma weights, used by the saturation matrix. */
    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    /**
     * @param settings colour grade to apply
     * @param hdrEnabled whether the user switched HDR on
     * @param deviceHasHdrDisplay true when the panel reports HDR10 or HLG support
     */
    fun build(
        settings: ColorSettings,
        hdrEnabled: Boolean,
        deviceHasHdrDisplay: Boolean,
        hdrContent: Boolean = false,
        hdrStrength: Float = 1f,
    ): List<Effect> {
        val effects = mutableListOf<Effect>()

        // 1. Noise reduction first, so the sharpening step does not amplify grain.
        if (settings.noiseReduce > 0.01f) {
            effects += GaussianBlur(
                0.3f + 2.4f * settings.noiseReduce.coerceIn(
                    0f,
                    0.5f,
                ),
            )
        }

        // 2. Gentle unsharp mask. Sharpness is capped at 0.5 by the specification.
        val sharpenAmount = (settings.sharpness + settings.clarity).coerceIn(
            0f,
            0.5f,
        )
        if (sharpenAmount > 0.01f) {
            effects += UnsharpConvolution(sharpenAmount)
        }

        // 3. Tone and colour.
        if (settings.brightness != 0f) {
            effects += Brightness(
                settings.brightness.coerceIn(
                    -1f,
                    1f,
                ),
            )
        }
        if (settings.contrast != 1f) {
            effects += Contrast(
                settings.contrast.coerceIn(
                    0.5f,
                    2f,
                ),
            )
        }
        if (settings.saturation != 1f) {
            effects += SaturationMatrix(
                settings.saturation.coerceIn(
                    0f,
                    2f,
                ),
            )
        }
        if (settings.temperature != ColorTemperature.NEUTRAL) {
            effects += TemperatureMatrix(settings.temperature)
        }

        // 4. HDR. Three different jobs depending on the panel and the source:
        //    - HDR panel with HDR video: ExoPlayer already passes HDR through, add nothing.
        //    - SDR panel with HDR video: compress the highlights with the ACES curve.
        //    - SDR panel with SDR video: there is no dynamic range to compress, so the switch
        //      applies the HDR *look* instead (a small contrast and colour lift). Without this
        //      the toggle did nothing at all on ordinary videos, which is why it felt broken.
        if (hdrEnabled) {
            if (deviceHasHdrDisplay) {
                // pass-through
            } else if (hdrContent) {
                effects += AcesToneMap.create(hdrStrength)
            } else {
                // Strength comes from the governor: a low-RAM or warm device gets a gentler
                // grade, so the frame processor is never asked for more than it can afford.
                val strength = hdrStrength.coerceIn(
                    minimumValue = 0f,
                    maximumValue = 1f,
                )
                effects += Contrast(1f + 0.18f * strength)
                effects += SaturationMatrix(1f + 0.15f * strength)
            }
        }

        return effects
    }

    /** True when the current display reports HDR10 or HLG support. */
    fun deviceHasHdrDisplay(context: Context): Boolean {
        val display: Display = displayOf(context) ?: return false
        val types = display.hdrCapabilities?.supportedHdrTypes ?: return false
        return types.any { it == HDR_TYPE_HDR10 || it == HDR_TYPE_HLG }
    }

    /**
     * Resolve the display to ask about HDR support.
     *
     * `Context.getDisplay()` only works for visual contexts (an Activity, or one created with
     * `createWindowContext`/`createDisplayContext`). `PlayerService` is a Service, so on API 30+
     * the call does not return null — it throws `UnsupportedOperationException`, which used to
     * take the whole service down during `onCreate`. The DisplayManager answers for any context,
     * so it is the primary path now and the legacy WindowManager is the last resort.
     */
    @Suppress("DEPRECATION")
    private fun displayOf(context: Context): Display? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.display }.getOrNull()?.let { return it }
        }
        runCatching {
            (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
        }.getOrNull()?.let { return it }
        return (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
    }

    /**
     * Separable unsharp mask. The kernel is (-a/2, 1+a, -a/2), which sums to 1, so
     * overall brightness is unchanged and only edge contrast is raised.
     */
    private class UnsharpConvolution(private val amount: Float) : SeparableConvolution() {
        override fun getConvolution(presentationTimeUs: Long): ConvolutionFunction1D =
            object : ConvolutionFunction1D {
                override fun domainStart(): Float = -1.5f

                override fun domainEnd(): Float = 1.5f

                override fun value(x: Float): Float = when {
                    x < -0.5f -> -amount / 2f
                    x < 0.5f -> 1f + amount
                    else -> -amount / 2f
                }
            }
    }

    /** Luma preserving saturation matrix. s = 0 is greyscale, s = 1 is untouched. */
    private class SaturationMatrix(private val s: Float) : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val inv = 1f - s
            val r = LR * inv
            val g = LG * inv
            val b = LB * inv
            // Column-major 4x4.
            return floatArrayOf(
                r + s,
                r,
                r,
                0f,
                g,
                g + s,
                g,
                0f,
                b,
                b,
                b + s,
                0f,
                0f,
                0f,
                0f,
                1f,
            )
        }
    }

    /** Simple channel gain white balance: warm lifts red and drops blue, cool the reverse. */
    private class TemperatureMatrix(private val temperature: ColorTemperature) : RgbMatrix {
        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val (redGain, blueGain) = when (temperature) {
                ColorTemperature.WARM -> 1.08f to 0.92f
                ColorTemperature.COOL -> 0.92f to 1.08f
                ColorTemperature.NEUTRAL -> 1f to 1f
            }
            return floatArrayOf(
                redGain,
                0f,
                0f,
                0f,
                0f,
                1f,
                0f,
                0f,
                0f,
                0f,
                blueGain,
                0f,
                0f,
                0f,
                0f,
                1f,
            )
        }
    }

}
