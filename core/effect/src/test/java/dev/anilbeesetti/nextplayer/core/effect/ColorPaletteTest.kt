package dev.anilbeesetti.nextplayer.core.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPaletteTest {

    @Test
    fun everyPaletteIsDistinct() {
        val signatures = ColorPalette.entries.map { it.settings().toString() }
        assertEquals(
            ColorPalette.entries.size,
            signatures.toSet().size,
        )
    }

    @Test
    fun naturalChangesNothing() {
        val s = ColorPalette.NATURAL.settings()
        assertEquals(1f, s.contrast, 0f)
        assertEquals(1f, s.saturation, 0f)
        assertEquals(0f, s.brightness, 0f)
    }

    @Test
    fun punchIsStrongerThanNatural() {
        val punch = ColorPalette.PUNCH.settings()
        val natural = ColorPalette.NATURAL.settings()
        assertTrue(punch.contrast > natural.contrast)
        assertTrue(punch.saturation > natural.saturation)
    }

    @Test
    fun monoHasNoColourAtAll() {
        assertEquals(0f, ColorPalette.MONO.settings().saturation, 0f)
    }

    @Test
    fun cinemaIsWarmerAndFlatterThanPunch() {
        val cinema = ColorPalette.CINEMA.settings()
        val punch = ColorPalette.PUNCH.settings()
        assertEquals(ColorTemperature.WARM, cinema.temperature)
        assertTrue(cinema.saturation < punch.saturation)
    }

    @Test
    fun warmAndCoolDifferOnlyInTemperature() {
        val warm = ColorPalette.WARM.settings()
        val cool = ColorPalette.COOL.settings()
        assertEquals(ColorTemperature.WARM, warm.temperature)
        assertEquals(ColorTemperature.COOL, cool.temperature)
    }

    @Test
    fun noPaletteAsksForAnImpossibleValue() {
        for (palette in ColorPalette.entries) {
            val s = palette.settings()
            assertTrue(palette.name, s.contrast > 0f)
            assertTrue(palette.name, s.saturation >= 0f)
            assertTrue(palette.name, s.sharpness in 0f..1f)
            assertTrue(palette.name, s.clarity in 0f..1f)
            assertTrue(palette.name, s.brightness in -0.2f..0.2f)
        }
    }

    @Test
    fun unknownSavedPaletteFallsBackToNatural() {
        assertEquals(
            ColorPalette.NATURAL,
            ColorPalette.of(null),
        )
        assertEquals(
            ColorPalette.NATURAL,
            ColorPalette.of("nope"),
        )
    }

    @Test
    fun savedPaletteIsCaseInsensitive() {
        assertEquals(
            ColorPalette.PUNCH,
            ColorPalette.of("punch"),
        )
    }

    @Test
    fun everyPaletteHasALabel() {
        for (palette in ColorPalette.entries) {
            assertTrue(palette.name, palette.label.isNotBlank())
        }
    }
}
