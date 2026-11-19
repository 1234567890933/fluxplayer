package dev.anilbeesetti.nextplayer.core.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineGuardTest {

    @Test
    fun aWorkingStageReturnsItsValue() {
        val guard = PipelineGuard()
        assertEquals(
            7,
            guard.attempt("full") { 7 },
        )
    }

    @Test
    fun aThrowingStageNeverEscapes() {
        val guard = PipelineGuard()
        val result = guard.attempt<Int>("full") { error("gpu said no") }
        assertNull(result)
        assertEquals(
            1,
            guard.failureCount("full"),
        )
    }

    @Test
    fun aStageStopsBeingAskedAfterTwoFailures() {
        val guard = PipelineGuard()
        guard.attempt<Int>("full") { error("no") }
        guard.attempt<Int>("full") { error("no") }
        assertFalse(guard.isHealthy("full"))
        assertNull(guard.attempt("full") { 5 })
        assertEquals(
            2,
            guard.failureCount("full"),
        )
    }

    @Test
    fun otherStagesAreNotPunishedForOneBadStage() {
        val guard = PipelineGuard()
        guard.attempt<Int>("full") { error("no") }
        guard.attempt<Int>("full") { error("no") }
        assertFalse(guard.isHealthy("full"))
        assertTrue(guard.isHealthy("light"))
        assertEquals(
            3,
            guard.attempt("light") { 3 },
        )
    }

    @Test
    fun aNullResultIsNotCountedAsSuccess() {
        val guard = PipelineGuard()
        guard.attempt<Int>("full") { null }
        assertEquals(
            0,
            guard.successCount("full"),
        )
    }

    @Test
    fun successesAreCounted() {
        val guard = PipelineGuard()
        guard.attempt("full") { 1 }
        guard.attempt("full") { 2 }
        assertEquals(
            2,
            guard.successCount("full"),
        )
    }

    @Test
    fun resetGivesABrokenStageAnotherChance() {
        val guard = PipelineGuard()
        guard.attempt<Int>("full") { error("no") }
        guard.attempt<Int>("full") { error("no") }
        assertFalse(guard.isHealthy("full"))
        guard.reset()
        assertTrue(guard.isHealthy("full"))
        assertEquals(
            9,
            guard.attempt("full") { 9 },
        )
    }

    @Test
    fun aStageThatRecoversKeepsWorking() {
        val guard = PipelineGuard()
        guard.attempt<Int>("full") { error("no") }
        assertEquals(
            4,
            guard.attempt("full") { 4 },
        )
        assertTrue(guard.isHealthy("full"))
    }
}
