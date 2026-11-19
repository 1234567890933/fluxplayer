package dev.anilbeesetti.nextplayer.core.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ten states, one wire: whatever the device reports, the governor must answer with the budget
 * that keeps playback alive. This is the meter reading at ten points along the same line.
 */
class EffectGovernorTest {

    @Test
    fun aHealthyDeviceGetsTheFullGrade() {
        val governor = EffectGovernor()
        assertEquals(EffectBudget.FULL, governor.budget)
        assertEquals(1f, governor.hdrStrength, 0f)
        assertFalse(governor.isConstrained)
    }

    @Test
    fun batterySaverSwitchesThePipelineOff() {
        val governor = EffectGovernor()
        governor.onBatterySaver(true)
        assertEquals(EffectBudget.OFF, governor.budget)
        assertEquals(0f, governor.hdrStrength, 0f)
        assertTrue(governor.isConstrained)
    }

    @Test
    fun moderateHeatDropsToTheLightGrade() {
        val governor = EffectGovernor()
        governor.onThermalLevel(ThermalLevel.MODERATE)
        assertEquals(EffectBudget.LIGHT, governor.budget)
        assertEquals(0.5f, governor.hdrStrength, 0f)
    }

    @Test
    fun severeHeatKeepsTheGradeMinimal() {
        val governor = EffectGovernor()
        governor.onThermalLevel(ThermalLevel.SEVERE)
        assertEquals(EffectBudget.LIGHT, governor.budget)
        assertEquals(0.3f, governor.hdrStrength, 0f)
    }

    @Test
    fun coolingDownRestoresTheFullGrade() {
        val governor = EffectGovernor()
        governor.onThermalLevel(ThermalLevel.SEVERE)
        governor.onThermalLevel(ThermalLevel.NONE)
        assertEquals(EffectBudget.FULL, governor.budget)
        assertEquals(1f, governor.hdrStrength, 0f)
    }

    @Test
    fun lowRamDeviceStartsLightAndGentler() {
        val governor = EffectGovernor()
        governor.setLowRamDevice(true)
        assertEquals(EffectBudget.LIGHT, governor.budget)
        assertEquals(0.6f, governor.hdrStrength, 0f)
    }

    @Test
    fun oneRefusalMeansTryTheCheapGradeOnly() {
        val governor = EffectGovernor()
        governor.onApplyFailed()
        assertEquals(EffectBudget.LIGHT, governor.budget)
    }

    @Test
    fun twoRefusalsTakeThePipelineOutOfTheWay() {
        val governor = EffectGovernor()
        governor.onApplyFailed()
        governor.onApplyFailed()
        assertEquals(EffectBudget.OFF, governor.budget)
    }

    @Test
    fun oneSuccessClearsTheFailureCount() {
        val governor = EffectGovernor()
        governor.onApplyFailed()
        governor.onApplyFailed()
        governor.onApplySucceeded()
        assertEquals(EffectBudget.FULL, governor.budget)
    }

    @Test
    fun batterySaverWinsOverEverythingElse() {
        val governor = EffectGovernor()
        governor.setLowRamDevice(true)
        governor.onThermalLevel(ThermalLevel.CRITICAL)
        governor.onApplyFailed()
        governor.onBatterySaver(true)
        assertEquals(EffectBudget.OFF, governor.budget)
        assertEquals(0f, governor.hdrStrength, 0f)
    }

    // ---- fluctuation tests: the same wire read repeatedly must not drift ----

    @Test
    fun togglingBatterySaverTenTimesLeavesNoResidue() {
        val governor = EffectGovernor()
        repeat(10) {
            governor.onBatterySaver(true)
            governor.onBatterySaver(false)
        }
        assertEquals(EffectBudget.FULL, governor.budget)
        assertEquals(1f, governor.hdrStrength, 0f)
    }

    @Test
    fun harmlessHeatLevelsDoNotDisturbTheBudget() {
        val governor = EffectGovernor()
        repeat(10) {
            governor.onThermalLevel(ThermalLevel.LIGHT)
            governor.onThermalLevel(ThermalLevel.NONE)
        }
        assertEquals(EffectBudget.FULL, governor.budget)
    }

    @Test
    fun reportingTheSameStateRepeatedlyIsIdempotent() {
        val governor = EffectGovernor()
        governor.onThermalLevel(ThermalLevel.MODERATE)
        val first = governor.budget
        repeat(50) { governor.onThermalLevel(ThermalLevel.MODERATE) }
        assertEquals(first, governor.budget)
        assertEquals(0.5f, governor.hdrStrength, 0f)
    }

    @Test
    fun strengthFallsAsTheDeviceGetsHotter() {
        val governor = EffectGovernor()
        val cool = governor.hdrStrength
        governor.onThermalLevel(ThermalLevel.MODERATE)
        val warm = governor.hdrStrength
        governor.onThermalLevel(ThermalLevel.SEVERE)
        val hot = governor.hdrStrength
        assertTrue(cool > warm)
        assertTrue(warm > hot)
    }

    @Test
    fun recoveryAfterTrippingRestoresTheFullGrade() {
        val governor = EffectGovernor()
        governor.onApplyFailed()
        governor.onApplyFailed()
        assertEquals(EffectBudget.OFF, governor.budget)
        governor.onApplySucceeded()
        assertEquals(EffectBudget.FULL, governor.budget)
        assertEquals(1f, governor.hdrStrength, 0f)
    }

    @Test
    fun manyFailuresAndSuccessesStayConsistent() {
        val governor = EffectGovernor()
        repeat(5) {
            governor.onApplyFailed()
            governor.onApplyFailed()
            assertEquals(EffectBudget.OFF, governor.budget)
            governor.onApplySucceeded()
            assertEquals(EffectBudget.FULL, governor.budget)
        }
    }
}
