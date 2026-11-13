package my.github.MrxSiN.pixellauncherevolved.feature.glance

import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceWhen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlanceWhenTest {

    private fun holds(value: String?, hour: Int, minute: Int = 0, music: Boolean = false, charging: Boolean = false) =
        GlanceWhen.holds(value, hour * 60 + minute, music, charging)

    @Test
    fun absentAndUnknownMeanAlways() {
        assertTrue(holds(null, 3))
        assertTrue(holds("someday", 3))
    }

    @Test
    fun periodsCoverTheirHoursOnly() {
        assertTrue(holds(GlanceWhen.MORNING, 5))
        assertFalse(holds(GlanceWhen.MORNING, 12))
        assertTrue(holds(GlanceWhen.AFTERNOON, 16, 59))
        assertTrue(holds(GlanceWhen.EVENING, 17))
        assertTrue(holds(GlanceWhen.NIGHT, 23))
        assertTrue(holds(GlanceWhen.NIGHT, 2))
        assertFalse(holds(GlanceWhen.NIGHT, 5))
    }

    @Test
    fun musicAndChargingFollowTheirState() {
        assertTrue(holds(GlanceWhen.MUSIC, 9, music = true))
        assertFalse(holds(GlanceWhen.MUSIC, 9))
        assertTrue(holds(GlanceWhen.CHARGING, 9, charging = true))
    }

    @Test
    fun networkAndHeadphonesFollowTheirState() {
        assertTrue(GlanceWhen.holds(GlanceWhen.WIFI, 0, false, false, onWifi = true))
        assertFalse(GlanceWhen.holds(GlanceWhen.WIFI, 0, false, false, onMobile = true))
        assertTrue(GlanceWhen.holds(GlanceWhen.MOBILE, 0, false, false, onMobile = true))
        assertTrue(GlanceWhen.holds(GlanceWhen.HEADPHONES, 0, false, false, headphones = true))
        assertNull(GlanceWhen.minutesToNextBoundary(listOf(GlanceWhen.WIFI, GlanceWhen.HEADPHONES), 0))
    }

    @Test
    fun nextBoundaryIsTheNearestStartOrEnd() {
        assertEquals(60, GlanceWhen.minutesToNextBoundary(listOf(GlanceWhen.MORNING), 11 * 60))
        assertEquals(7 * 60, GlanceWhen.minutesToNextBoundary(listOf(GlanceWhen.NIGHT), 22 * 60))
        assertEquals(7 * 60, GlanceWhen.minutesToNextBoundary(listOf(GlanceWhen.MORNING), 5 * 60))
        assertNull(GlanceWhen.minutesToNextBoundary(listOf(GlanceWhen.MUSIC), 0))
    }

    @Test
    fun customTimesHoldInsideTheirRangeAndDays() {
        val nineToFive = GlanceWhen.custom(9 * 60, 17 * 60, 0)
        assertTrue(GlanceWhen.holds(nineToFive, 9 * 60, false, false, dayOfWeek = 3))
        assertFalse(GlanceWhen.holds(nineToFive, 17 * 60, false, false, dayOfWeek = 3))
        // Weekdays only (Monday to Friday): not on Sunday.
        val weekdays = GlanceWhen.custom(9 * 60, 17 * 60, 0b0111110)
        assertTrue(GlanceWhen.holds(weekdays, 10 * 60, false, false, dayOfWeek = 1))
        assertFalse(GlanceWhen.holds(weekdays, 10 * 60, false, false, dayOfWeek = 0))
        // Past midnight counts as the day it started: Friday 23:00 to 02:00 holds early Saturday.
        val lateFriday = GlanceWhen.custom(23 * 60, 2 * 60, 1 shl 5)
        assertTrue(GlanceWhen.holds(lateFriday, 60, false, false, dayOfWeek = 6))
        assertFalse(GlanceWhen.holds(lateFriday, 60, false, false, dayOfWeek = 5))
        // Unreadable custom values read as always, like any unknown value.
        assertTrue(GlanceWhen.holds("time:9-x:0", 0, false, false))
        assertEquals(-1, GlanceWhen.parseCustom("time:1500-60:0"))
    }

    @Test
    fun customTimesScheduleTheirEdgesAndMidnightWhenDaysMatter() {
        assertEquals(60, GlanceWhen.minutesToNextBoundary(listOf(GlanceWhen.custom(9 * 60, 17 * 60, 0)), 8 * 60))
        assertEquals(60, GlanceWhen.minutesToNextBoundary(listOf(GlanceWhen.custom(9 * 60, 17 * 60, 0b0111110)), 23 * 60))
    }
}
