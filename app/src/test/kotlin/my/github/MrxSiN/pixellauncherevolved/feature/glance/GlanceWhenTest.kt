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
}
