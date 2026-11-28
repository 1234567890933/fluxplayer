package dev.anilbeesetti.nextplayer.core.effect

import androidx.media3.effect.SingleColorLut

/**
 * ACES filmic tone curve baked into a 3D LUT:
 * f(x) = x(2.51x + 0.03) / (x(2.43x + 0.59) + 0.14), clamped to 0..1.
 */
internal object AcesToneMap {
    private const val SIZE = 17

    fun create(strength: Float = 1f): SingleColorLut {
        val last = SIZE - 1
        val cube = Array(SIZE) { r ->
            Array(SIZE) { g ->
                IntArray(SIZE) { b ->
                    val red = to8(AcesCurve.mix(r / last.toFloat(), strength))
                    val green = to8(AcesCurve.mix(g / last.toFloat(), strength))
                    val blue = to8(AcesCurve.mix(b / last.toFloat(), strength))
                    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }
        }
        return SingleColorLut.createFromCube(cube)
    }

    fun curve(x: Float): Float = AcesCurve.tone(x)

    private fun to8(value: Float): Int = (value * 255f).toInt().coerceIn(
        0,
        255,
    )
}
