package my.github.MrxSiN.pixellauncherevolved.feature.glance

import java.time.ZoneId

import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.extras.GlanceExtraLogic

class GlanceExtraLogicTest {

    @Test
    fun percentRoundsDownAndRejectsNoLevel() {
        assertEquals(50, GlanceExtraLogic.percent(50, 100))
        assertEquals(33, GlanceExtraLogic.percent(1, 3))
        assertEquals(-1, GlanceExtraLogic.percent(-1, 100))
        assertEquals(-1, GlanceExtraLogic.percent(10, 0))
    }

    @Test
    fun nextMidnightIsTheStartOfTomorrowInThatZone() {
        val noon = 12L * 3600_000 // 1970-01-01T12:00Z
        assertEquals(24L * 3600_000, GlanceExtraLogic.nextMidnight(noon, ZoneId.of("UTC")))
        // Tokyo is 21:00 on the 1st, so its midnight is 15:00Z
        assertEquals(15L * 3600_000, GlanceExtraLogic.nextMidnight(noon, ZoneId.of("Asia/Tokyo")))
    }
}
