package dev.anilbeesetti.nextplayer.core.effect

/**
 * The tone curve itself, kept free of any Android or Media3 type so it can be tested directly.
 *
 * The fitted ACES curve (Narkowicz 2015, also in Golubev's write up) is the standard way to map a
 * high dynamic range signal onto a display that cannot show it: bright values are pulled down
 * smoothly instead of clipping, so highlight detail survives the trip to an ordinary panel.
 */
object AcesCurve {

    /** f(x) = x(2.51x + 0.03) / (x(2.43x + 0.59) + 0.14), clamped to the SDR range. */
    fun tone(x: Float): Float {
        val v = x.coerceAtLeast(0f)
        val mapped = (v * (2.51f * v + 0.03f)) / (v * (2.43f * v + 0.59f) + 0.14f)
        return mapped.coerceIn(0f, 1f)
    }

    /**
     * Blends the tone mapped value with the untouched one. Strength 0 leaves the frame exactly as
     * it was, 1 applies the curve in full; the governor lowers it on a warm or low battery device.
     */
    fun mix(original: Float, strength: Float): Float {
        val s = strength.coerceIn(0f, 1f)
        val o = original.coerceIn(0f, 1f)
        return o + (tone(o) - o) * s
    }
}
