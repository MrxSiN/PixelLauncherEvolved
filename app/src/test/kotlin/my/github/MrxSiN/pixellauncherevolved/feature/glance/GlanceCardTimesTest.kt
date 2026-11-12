package my.github.MrxSiN.pixellauncherevolved.feature.glance

import java.util.TimeZone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards.GlanceCardTimes

class GlanceCardTimesTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val hour = 60 * GlanceCardTimes.MINUTE
    private val day0 = 20_000L * GlanceCardTimes.DAY // a UTC midnight

    @Test
    fun greetingPeriods() {
        assertEquals(3, GlanceCardTimes.greetingPeriod(2))
        assertEquals(0, GlanceCardTimes.greetingPeriod(3))
        assertEquals(1, GlanceCardTimes.greetingPeriod(12))
        assertEquals(2, GlanceCardTimes.greetingPeriod(21))
        assertEquals(3, GlanceCardTimes.greetingPeriod(23))
    }

    @Test
    fun boundaries() {
        assertEquals(day0 + 22 * hour, GlanceCardTimes.nextGreetingChange(day0 + 21 * hour, utc))
        assertEquals(day0 + 27 * hour, GlanceCardTimes.nextGreetingChange(day0 + 23 * hour, utc))
        assertEquals(day0 + 3 * hour, GlanceCardTimes.nextGreetingChange(day0 + 2 * hour, utc))
        assertEquals(day0 + 12 * hour, GlanceCardTimes.nextGreetingChange(day0 + 3 * hour, utc))
        assertEquals(day0 + 24 * hour, GlanceCardTimes.nextMidnight(day0 + 5 * hour, utc))
        assertEquals(1, GlanceCardTimes.dayOffset(day0 + 23 * hour, day0 + 25 * hour, utc))
        assertEquals(-1, GlanceCardTimes.dayOffset(day0 + hour, day0 - hour, utc))
    }

    @Test
    fun calendar() {
        val lead = 15 * GlanceCardTimes.MINUTE
        val now = day0 + 10 * hour
        assertTrue(GlanceCardTimes.shows(now + lead, now + hour, now, lead))
        assertFalse(GlanceCardTimes.shows(now + lead + 1, now + hour, now, lead))
        assertFalse(GlanceCardTimes.shows(now - hour, now, now, lead))
        assertEquals(0, GlanceCardTimes.countdown(now + 61 * GlanceCardTimes.MINUTE, now))
        assertEquals(10, GlanceCardTimes.countdown(now + 10 * GlanceCardTimes.MINUTE + 5, now))
        // Nothing shows yet: the next change is the lead-in at 10:45 for an 11:00 event.
        assertEquals(now + 45 * GlanceCardTimes.MINUTE, GlanceCardTimes.nextCalendarChange(longArrayOf(now + hour), longArrayOf(now + 2 * hour), now, lead, day0 + 24 * hour))
        // Counting down: the minute turns.
        assertEquals(now + GlanceCardTimes.MINUTE, GlanceCardTimes.nextCalendarChange(longArrayOf(now + 10 * GlanceCardTimes.MINUTE), longArrayOf(now + hour), now, lead, day0 + 24 * hour))
        // Nothing at all: midnight.
        assertEquals(day0 + 24 * hour, GlanceCardTimes.nextCalendarChange(longArrayOf(), longArrayOf(), now, lead, day0 + 24 * hour))
    }
}
