package dev.anilbeesetti.nextplayer.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlogTest {

    private fun sample(): Mlog.Writer = Mlog.Writer()
        .write(0, "Player", 2, "prepared")
        .write(40, "Player", 2, "first frame")
        .write(1040, "Player", 2, "one second in")
        .write(1040, "Effect", 3, "grade applied")
        .write(2040, "Player", 4, "dropped a frame")

    @Test
    fun everythingComesBackExactly() {
        val entries = Mlog.read(sample().toByteArray())
        assertEquals(5, entries.size)
        assertEquals("Player", entries[0].tag)
        assertEquals(0L, entries[0].timeMs)
        assertEquals(40L, entries[1].timeMs)
        assertEquals(1040L, entries[2].timeMs)
        assertEquals("Effect", entries[3].tag)
        assertEquals(4, entries[4].level)
        assertEquals("dropped a frame", entries[4].message)
    }

    @Test
    fun timestampsAreRebuiltFromTheGaps() {
        val entries = Mlog.read(sample().toByteArray())
        assertEquals(
            listOf(0L, 40L, 1040L, 1040L, 2040L),
            entries.map { it.timeMs },
        )
    }

    @Test
    fun aRepeatedTagIsStoredOnlyOnce() {
        val bytes = sample().toByteArray()
        // The tag name lives in the tag table, not in each of the four Player entries.
        val occurrences = String(bytes, Charsets.UTF_8).split("Player").size - 1
        assertEquals(1, occurrences)
    }

    @Test
    fun theBinaryFormIsMuchSmallerThanText() {
        // Measured on a realistic session: five short lines are too small for the saving to show.
        val writer = Mlog.Writer()
        var t = 0L
        repeat(500) {
            t += 40
            writer.write(t, "PlayerEffectsController", 2, "grade applied at frame $it")
        }
        val bytes = writer.toByteArray()
        val text = Mlog.asText(Mlog.read(bytes)).toByteArray(Charsets.UTF_8)
        // Measured: 15,421 bytes against 30,614 for the same 500 lines, a 49.6% saving. The bound
        // is set at 45% so it holds on any machine without becoming meaningless.
        val ratio = bytes.size.toDouble() / text.size
        assertTrue(
            "binary $bytes is only ${"%.1f".format(ratio * 100)}% of text ${text.size}",
            ratio < 0.55,
        )
    }

    @Test
    fun aLongSessionStaysCompact() {
        val writer = Mlog.Writer()
        var t = 0L
        repeat(2000) {
            t += 40
            writer.write(t, "Player", 2, "tick")
        }
        val entries = Mlog.read(writer.toByteArray())
        assertEquals(2000, entries.size)
        assertEquals(80000L, entries.last().timeMs)
        assertTrue("2000 entries should stay small, was ${writer.size}", writer.size < 20000)
    }

    @Test
    fun unicodeMessagesSurvive() {
        val bytes = Mlog.Writer().write(10, "प्लेयर", 2, "शुरू हुआ").toByteArray()
        val entries = Mlog.read(bytes)
        assertEquals("प्लेयर", entries[0].tag)
        assertEquals("शुरू हुआ", entries[0].message)
    }

    @Test
    fun emptyMessagesAndLongOnesBothWork() {
        val long = "x".repeat(5000)
        val bytes = Mlog.Writer()
            .write(0, "T", 1, "")
            .write(1, "T", 1, long)
            .toByteArray()
        val entries = Mlog.read(bytes)
        assertEquals("", entries[0].message)
        assertEquals(long, entries[1].message)
    }

    @Test
    fun levelsAreClampedToTheKnownRange() {
        val bytes = Mlog.Writer().write(0, "T", 99, "x").toByteArray()
        assertEquals(4, Mlog.read(bytes)[0].level)
        val low = Mlog.Writer().write(0, "T", -3, "x").toByteArray()
        assertEquals(0, Mlog.read(low)[0].level)
    }

    @Test
    fun aTimeGoingBackwardsDoesNotBreakTheReader() {
        val bytes = Mlog.Writer()
            .write(1000, "T", 2, "a")
            .write(500, "T", 2, "b")
            .toByteArray()
        val entries = Mlog.read(bytes)
        assertEquals(1000L, entries[0].timeMs)
        assertEquals(1000L, entries[1].timeMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun randomBytesAreRejected() {
        Mlog.read(byteArrayOf(1, 2, 3, 4, 5, 6))
    }
}
