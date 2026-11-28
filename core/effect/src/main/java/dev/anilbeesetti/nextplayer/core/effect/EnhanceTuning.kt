package dev.anilbeesetti.nextplayer.core.effect

/**
 * Turns a preset and a device tier into the concrete grade the renderer applies. Kept pure so the
 * whole table can be tested without a GPU.
 */
object EnhanceTuning {

    fun settingsFor(preset: EnhancePreset, tier: DeviceTier): ColorSettings = when (preset) {
        EnhancePreset.OFF -> ColorSettings(
            brightness = 0f,
            contrast = 1f,
            saturation = 1f,
            sharpness = 0f,
            clarity = 0f,
            noiseReduce = 0f,
            preset = ColorPreset.NATURAL,
        )

        EnhancePreset.AUTO -> when (tier) {
            DeviceTier.LITE -> grade(0.42f)
            DeviceTier.BALANCED -> grade(0.62f)
            DeviceTier.ULTRA -> grade(0.90f)
        }

        EnhancePreset.SUBTLE -> grade(0.35f)

        EnhancePreset.BALANCED -> grade(0.65f)

        EnhancePreset.VIVID -> grade(1.00f)

        // MAX is the top of the curve: the renderer clamps sharpness and contrast on its own.
        EnhancePreset.MAX -> grade(1.25f)
    }

    /** One curve, four strengths: every knob moves together so no setting fights another. */
    private fun grade(strength: Float): ColorSettings = ColorSettings(
        brightness = 0.04f * strength,
        contrast = 1f + 0.38f * strength,
        saturation = 1f + 0.42f * strength,
        sharpness = 0.65f * strength,
        clarity = 0.58f * strength,
        noiseReduce = 0.28f * strength,
        preset = ColorPreset.CUSTOM,
    )
}
