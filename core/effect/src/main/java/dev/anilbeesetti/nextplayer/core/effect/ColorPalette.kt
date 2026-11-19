package dev.anilbeesetti.nextplayer.core.effect

/**
 * Ready made looks. Everyone sees colour differently: one viewer wants punch, another wants the
 * flat cinema look, another wants the original untouched. Each palette is a complete grade, so a
 * single tap gives a predictable result instead of asking people to tune four sliders by hand.
 */
enum class ColorPalette(val label: String) {
    NATURAL("Natural"),
    PUNCH("Punch"),
    CINEMA("Cinema"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    MONO("Mono"),
    ;

    /** The grade this palette stands for. Values stay inside what the renderer can take. */
    fun settings(): ColorSettings = when (this) {
        NATURAL -> ColorSettings(
            brightness = 0f,
            contrast = 1f,
            saturation = 1f,
            preset = ColorPreset.NATURAL,
        )

        PUNCH -> ColorSettings(
            brightness = 0.03f,
            contrast = 1.22f,
            saturation = 1.28f,
            sharpness = 0.45f,
            clarity = 0.40f,
            preset = ColorPreset.CUSTOM,
        )

        CINEMA -> ColorSettings(
            brightness = -0.04f,
            contrast = 1.14f,
            saturation = 0.94f,
            sharpness = 0.30f,
            clarity = 0.35f,
            temperature = ColorTemperature.WARM,
            preset = ColorPreset.CINEMA,
        )

        VIVID -> ColorSettings(
            brightness = 0.05f,
            contrast = 1.16f,
            saturation = 1.45f,
            sharpness = 0.40f,
            clarity = 0.30f,
            preset = ColorPreset.VIVID,
        )

        WARM -> ColorSettings(
            brightness = 0.02f,
            contrast = 1.06f,
            saturation = 1.10f,
            temperature = ColorTemperature.WARM,
            preset = ColorPreset.CUSTOM,
        )

        COOL -> ColorSettings(
            brightness = 0f,
            contrast = 1.08f,
            saturation = 1.04f,
            temperature = ColorTemperature.COOL,
            preset = ColorPreset.CUSTOM,
        )

        MONO -> ColorSettings(
            brightness = 0f,
            contrast = 1.18f,
            saturation = 0f,
            sharpness = 0.35f,
            preset = ColorPreset.CUSTOM,
        )
    }

    companion object {
        fun of(name: String?): ColorPalette =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NATURAL
    }
}
