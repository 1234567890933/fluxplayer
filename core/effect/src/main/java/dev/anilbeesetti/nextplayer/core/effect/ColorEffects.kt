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
import androidx.media3.effect.SingleColorLut

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

        // 4. HDR look. On an HDR panel ExoPlayer already passes HDR through, so no
        //    effect is added. On an SDR panel the ACES curve is baked into a 3D LUT.
        if (hdrEnabled && !deviceHasHdrDisplay) {
            effects += AcesToneMap.create()
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

    /**
     * ACES filmic tone curve baked into a 3D LUT:
     * f(x) = x(2.51x + 0.03) / (x(2.43x + 0.59) + 0.14), clamped to 0..1.
     */
    private object AcesToneMap {
        private const val SIZE = 17

        fun create(): SingleColorLut {
            val last = SIZE - 1
            val cube = Array(SIZE) { r ->
                Array(SIZE) { g ->
                    IntArray(SIZE) { b ->
                        val red = to8(curve(r / last.toFloat()))
                        val green = to8(curve(g / last.toFloat()))
                        val blue = to8(curve(b / last.toFloat()))
                        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                    }
                }
            }
            return SingleColorLut.createFromCube(cube)
        }

        fun curve(x: Float): Float {
            val value = (x * (2.51f * x + 0.03f)) / (x * (2.43f * x + 0.59f) + 0.14f)
            return value.coerceIn(
                0f,
                1f,
            )
        }

        private fun to8(value: Float): Int = (value * 255f).toInt().coerceIn(
            0,
            255,
        )
    }
}
