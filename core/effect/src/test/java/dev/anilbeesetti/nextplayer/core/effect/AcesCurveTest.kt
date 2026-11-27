package dev.anilbeesetti.nextplayer.core.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcesCurveTest {

    private fun assertClose(expected: Float, actual: Float) {
        assertEquals(
            expected,
            actual,
            0.001f,
        )
    }

    @Test
    fun blackStaysBlack() {
        assertClose(0f, AcesCurve.tone(0f))
    }

    @Test
    fun whiteIsPulledDownNotClipped() {
        // The whole point of the curve: 1.0 in comes out below 1.0, so highlights keep detail.
        val out = AcesCurve.tone(1f)
        assertTrue("expected < 1.0 but was $out", out < 1f)
        assertTrue("expected > 0.7 but was $out", out > 0.7f)
    }

    @Test
    fun theCurveNeverDecreases() {
        var previous = -1f
        var x = 0f
        while (x <= 1f) {
            val y = AcesCurve.tone(x)
            assertTrue("curve dipped at $x", y >= previous - 0.0001f)
            previous = y
            x += 0.01f
        }
    }

    @Test
    fun valuesAboveOneAreClampedNotWrapped() {
        assertClose(1f, AcesCurve.tone(9f))
    }

    @Test
    fun negativeInputIsTreatedAsBlack() {
        assertClose(0f, AcesCurve.tone(-3f))
    }

    @Test
    fun strengthZeroLeavesTheFrameUntouched() {
        assertClose(0.5f, AcesCurve.mix(0.5f, 0f))
        assertClose(1f, AcesCurve.mix(1f, 0f))
    }

    @Test
    fun strengthOneIsTheFullCurve() {
        assertClose(AcesCurve.tone(0.8f), AcesCurve.mix(0.8f, 1f))
    }

    @Test
    fun strengthSitsBetweenTheTwoEnds() {
        val original = 0.8f
        val full = AcesCurve.tone(original)
        val half = AcesCurve.mix(original, 0.5f)
        assertTrue(half < original)
        assertTrue(half > full)
    }

    @Test
    fun strengthIsClamped() {
        assertClose(AcesCurve.mix(0.8f, 1f), AcesCurve.mix(0.8f, 5f))
        assertClose(0.8f, AcesCurve.mix(0.8f, -2f))
    }
}
