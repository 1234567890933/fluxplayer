package dev.anilbeesetti.nextplayer.core.effect

/** Colour grade presets offered in the colour bottom sheet. */
enum class ColorPreset { CINEMA, VIVID, NATURAL, NIGHT, CUSTOM }

/** White balance feel of the colour grade. */
enum class ColorTemperature { WARM, NEUTRAL, COOL }

/**
 * One complete colour grade. Defaults are the values the specification asks for:
 * brightness 0, contrast 1, saturation 1, sharpness 0.25, clarity 0.25,
 * noise reduction 20 %, neutral temperature.
 */
data class ColorSettings(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val sharpness: Float = 0.25f,
    val clarity: Float = 0.25f,
    val noiseReduce: Float = 0.20f,
    val temperature: ColorTemperature = ColorTemperature.NEUTRAL,
    val preset: ColorPreset = ColorPreset.NATURAL,
) {
    companion object {
        fun of(preset: ColorPreset): ColorSettings = when (preset) {
            ColorPreset.CINEMA -> ColorSettings(
                brightness = -0.1f,
                contrast = 1.3f,
                saturation = 0.8f,
                preset = preset,
            )

            ColorPreset.VIVID -> ColorSettings(
                brightness = 0.1f,
                contrast = 1.4f,
                saturation = 1.6f,
                preset = preset,
            )

            ColorPreset.NATURAL -> ColorSettings(preset = preset)

            ColorPreset.NIGHT -> ColorSettings(
                brightness = -0.3f,
                contrast = 0.8f,
                saturation = 0.7f,
                preset = preset,
            )

            ColorPreset.CUSTOM -> ColorSettings(preset = preset)
        }
    }
}
