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
            DeviceTier.LITE -> grade(0.28f)
            DeviceTier.BALANCED -> grade(0.55f)
            DeviceTier.ULTRA -> grade(0.85f)
        }

        EnhancePreset.SUBTLE -> grade(0.30f)

        EnhancePreset.BALANCED -> grade(0.60f)

        EnhancePreset.VIVID -> grade(1.00f)
    }

    /** One curve, four strengths: every knob moves together so no setting fights another. */
    private fun grade(strength: Float): ColorSettings = ColorSettings(
        brightness = 0.04f * strength,
        contrast = 1f + 0.30f * strength,
        saturation = 1f + 0.35f * strength,
        sharpness = 0.60f * strength,
        clarity = 0.55f * strength,
        noiseReduce = 0.25f * strength,
        preset = ColorPreset.CUSTOM,
    )
}
