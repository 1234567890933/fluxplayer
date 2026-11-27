package dev.anilbeesetti.nextplayer.core.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the parts of the engine the build guide spells out: the six preset states, the thermal
 * mapping, and the AUTO strength per device tier.
 */
class GuideParityTest {

    @Test
    fun thePresetCycleCoversAllSixStatesAndReturnsHome() {
        var preset = EnhancePreset.OFF
        val seen = linkedSetOf(preset)
        repeat(EnhancePreset.entries.size - 1) {
            preset = preset.next()
            seen.add(preset)
        }
        assertEquals(
            6,
            seen.size,
        )
        assertEquals(
            EnhancePreset.OFF,
            preset.next(),
        )
    }

    @Test
    fun maxIsTheLastStepBeforeOff() {
        assertEquals(
            EnhancePreset.MAX,
            EnhancePreset.VIVID.next(),
        )
        assertEquals(
            EnhancePreset.OFF,
            EnhancePreset.MAX.next(),
        )
    }

    @Test
    fun maxGradesHarderThanVivid() {
        val vivid = EnhanceTuning.settingsFor(
            EnhancePreset.VIVID,
            DeviceTier.ULTRA,
        )
        val max = EnhanceTuning.settingsFor(
            EnhancePreset.MAX,
            DeviceTier.ULTRA,
        )
        assertTrue(max.contrast > vivid.contrast)
        assertTrue(max.saturation > vivid.saturation)
        assertTrue(max.sharpness > vivid.sharpness)
    }

    @Test
    fun everyPresetProducesASaneGrade() {
        for (preset in EnhancePreset.entries) {
            for (tier in DeviceTier.entries) {
                val s = EnhanceTuning.settingsFor(preset, tier)
                assertTrue("$preset/$tier contrast", s.contrast in 0.5f..2f)
                assertTrue("$preset/$tier saturation", s.saturation in 0f..2f)
                assertTrue("$preset/$tier brightness", s.brightness in -1f..1f)
                assertTrue("$preset/$tier sharpness", s.sharpness in 0f..1f)
            }
        }
    }

    @Test
    fun offTouchesNothing() {
        val off = EnhanceTuning.settingsFor(
            EnhancePreset.OFF,
            DeviceTier.ULTRA,
        )
        assertEquals(1f, off.contrast, 0.001f)
        assertEquals(1f, off.saturation, 0.001f)
        assertEquals(0f, off.sharpness, 0.001f)
    }

    @Test
    fun autoGetsStrongerOnBetterPhones() {
        val lite = EnhanceTuning.settingsFor(
            EnhancePreset.AUTO,
            DeviceTier.LITE,
        )
        val balanced = EnhanceTuning.settingsFor(
            EnhancePreset.AUTO,
            DeviceTier.BALANCED,
        )
        val ultra = EnhanceTuning.settingsFor(
            EnhancePreset.AUTO,
            DeviceTier.ULTRA,
        )
        assertTrue(ultra.contrast > balanced.contrast)
        assertTrue(balanced.contrast > lite.contrast)
    }

    @Test
    fun thePlatformThermalStepsMapOntoFiveLevels() {
        assertEquals(ThermalLevel.NONE, ThermalStatus.toThermalLevel(ThermalStatus.NONE))
        assertEquals(ThermalLevel.LIGHT, ThermalStatus.toThermalLevel(ThermalStatus.LIGHT))
        assertEquals(ThermalLevel.MODERATE, ThermalStatus.toThermalLevel(ThermalStatus.MODERATE))
        assertEquals(ThermalLevel.SEVERE, ThermalStatus.toThermalLevel(ThermalStatus.SEVERE))
        assertEquals(ThermalLevel.CRITICAL, ThermalStatus.toThermalLevel(ThermalStatus.CRITICAL))
        // Emergency and shutdown both mean "stop adding work".
        assertEquals(ThermalLevel.CRITICAL, ThermalStatus.toThermalLevel(ThermalStatus.EMERGENCY))
        assertEquals(ThermalLevel.CRITICAL, ThermalStatus.toThermalLevel(ThermalStatus.SHUTDOWN))
    }

    @Test
    fun anUnknownStatusNeverCrashes() {
        assertEquals(ThermalLevel.NONE, (-5).toThermalLevel())
        assertEquals(ThermalLevel.CRITICAL, 99.toThermalLevel())
    }

    @Test
    fun hotterLevelsAllowLessWork() {
        assertTrue(ThermalLevel.NONE.effectScale > ThermalLevel.MODERATE.effectScale)
        assertEquals(0f, ThermalLevel.CRITICAL.effectScale, 0.001f)
    }

    @Test
    fun theAutoStrengthsDifferPerTier() {
        assertNotEquals(
            DeviceTier.LITE,
            DeviceTier.ULTRA,
        )
        val lite = EnhanceTuning.settingsFor(
            EnhancePreset.AUTO,
            DeviceTier.LITE,
        )
        val ultra = EnhanceTuning.settingsFor(
            EnhancePreset.AUTO,
            DeviceTier.ULTRA,
        )
        assertTrue(ultra.saturation > lite.saturation)
    }
}
