package my.github.MrxSiN.pixellauncherevolved.glance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarWireTest {

    @Test
    fun arraysOfOneLengthAreOneSetOfEvents() {
        assertTrue(CalendarWire.consistent(longArrayOf(1, 2), arrayOf("a", "b"), longArrayOf(3, 4), longArrayOf(5, 6)))
        assertTrue(CalendarWire.consistent(LongArray(0), emptyArray(), LongArray(0), LongArray(0)))
    }

    @Test
    fun arraysOfDifferentLengthsAreRefused() {
        assertFalse(CalendarWire.consistent(longArrayOf(1, 2), arrayOf("a"), longArrayOf(3, 4), longArrayOf(5, 6)))
        assertFalse(CalendarWire.consistent(longArrayOf(1), arrayOf("a"), longArrayOf(3), LongArray(0)))
    }
}
