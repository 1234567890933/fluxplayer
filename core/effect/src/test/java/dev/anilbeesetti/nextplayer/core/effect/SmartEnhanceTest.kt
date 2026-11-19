package dev.anilbeesetti.nextplayer.core.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEnhanceTest {

    private fun assertTier(expected: DeviceTier, ram: Long, gl: Int) {
        assertEquals(
            expected,
            DeviceTier.of(
                ram,
                gl,
            ),
        )
    }

    private fun assertContrast(expected: Float, settings: ColorSettings) {
        assertEquals(
            expected,
            settings.contrast,
            0.0001f,
        )
    }

    @Test
    fun twoGigabytePhoneIsLite() {
        assertTier(DeviceTier.LITE, 2048, 3)
    }

    @Test
    fun oldGlMeansLiteEvenWithPlentyOfRam() {
        assertTier(DeviceTier.LITE, 8192, 2)
    }

    @Test
    fun fourGigabytePhoneIsBalanced() {
        assertTier(DeviceTier.BALANCED, 4096, 3)
    }

    @Test
    fun eightGigabytePhoneIsUltra() {
        assertTier(DeviceTier.ULTRA, 8192, 3)
    }

    @Test
    fun exactLowBoundaryIsNotLite() {
        assertTier(DeviceTier.BALANCED, 3072, 3)
    }

    @Test
    fun exactHighBoundaryIsUltra() {
        assertTier(DeviceTier.ULTRA, 6144, 3)
    }

    @Test
    fun offLeavesTheFrameAlone() {
        val settings = EnhanceTuning.settingsFor(
            EnhancePreset.OFF,
            DeviceTier.ULTRA,
        )
        assertContrast(1f, settings)
        assertEquals(0f, settings.sharpness, 0f)
        assertEquals(0f, settings.noiseReduce, 0f)
    }

    @Test
    fun autoOnLiteIsGentlerThanAutoOnUltra() {
        val lite = EnhanceTuning.settingsFor(
            EnhancePreset.AUTO,
            DeviceTier.LITE,
        )
        val ultra = EnhanceTuning.settingsFor(
            EnhancePreset.AUTO,
            DeviceTier.ULTRA,
        )
        assertTrue(ultra.contrast > lite.contrast)
        assertTrue(ultra.sharpness > lite.sharpness)
    }

    @Test
    fun vividIsTheStrongestPreset() {
        val vivid = EnhanceTuning.settingsFor(
            EnhancePreset.VIVID,
            DeviceTier.BALANCED,
        )
        val balanced = EnhanceTuning.settingsFor(
            EnhancePreset.BALANCED,
            DeviceTier.BALANCED,
        )
        val subtle = EnhanceTuning.settingsFor(
            EnhancePreset.SUBTLE,
            DeviceTier.BALANCED,
        )
        assertTrue(vivid.contrast > balanced.contrast)
        assertTrue(balanced.contrast > subtle.contrast)
    }

    @Test
    fun everyPresetIsReachableByTappingTheChip() {
        var preset = EnhancePreset.OFF
        val seen = mutableSetOf(preset)
        repeat(EnhancePreset.entries.size - 1) {
            preset = preset.next()
            seen.add(preset)
        }
        assertEquals(
            EnhancePreset.entries.size,
            seen.size,
        )
        assertEquals(
            EnhancePreset.OFF,
            preset.next(),
        )
    }

    @Test
    fun unknownSavedValueFallsBackToAuto() {
        assertEquals(
            EnhancePreset.AUTO,
            EnhancePreset.of(null),
        )
        assertEquals(
            EnhancePreset.AUTO,
            EnhancePreset.of("nonsense"),
        )
    }

    @Test
    fun savedValueSurvivesInAnyCase() {
        assertEquals(
            EnhancePreset.VIVID,
            EnhancePreset.of("vivid"),
        )
    }

    @Test
    fun noPresetProducesANegativeSetting() {
        for (preset in EnhancePreset.entries) {
            for (tier in DeviceTier.entries) {
                val s = EnhanceTuning.settingsFor(preset, tier)
                assertTrue(preset.name, s.contrast > 0f)
                assertTrue(preset.name, s.saturation >= 0f)
                assertTrue(preset.name, s.sharpness >= 0f)
                assertTrue(preset.name, s.clarity >= 0f)
                assertTrue(preset.name, s.noiseReduce >= 0f)
            }
        }
    }

    @Test
    fun autoDiffersPerTierButPinnedPresetsDoNot() {
        val a = EnhanceTuning.settingsFor(
            EnhancePreset.AUTO,
            DeviceTier.LITE,
        )
        val b = EnhanceTuning.settingsFor(
            EnhancePreset.AUTO,
            DeviceTier.ULTRA,
        )
        assertNotEquals(a.contrast, b.contrast)
        val c = EnhanceTuning.settingsFor(
            EnhancePreset.VIVID,
            DeviceTier.LITE,
        )
        val d = EnhanceTuning.settingsFor(
            EnhancePreset.VIVID,
            DeviceTier.ULTRA,
        )
        assertEquals(c.contrast, d.contrast, 0f)
    }
}
