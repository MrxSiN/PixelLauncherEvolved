package my.github.MrxSiN.pixellauncherevolved.feature.glance

import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more.WellbeingTally

import org.junit.Assert.assertEquals
import org.junit.Test

class WellbeingTallyTest {
    private val resumed = 1
    private val paused = 2

    @Test
    fun pairsResumeAndPauseAndSkipsExcluded() {
        val t = WellbeingTally(resumed, paused) { it == "home" }
        t.add(resumed, "a", 0)
        t.add(resumed, "a", 10)
        t.add(paused, "a", 100)
        t.add(resumed, "home", 100)
        t.add(paused, "home", 900)
        t.add(paused, "b", 150)
        t.add(resumed, "b", 200)
        t.add(resumed, "c", 250)
        t.add(paused, "c", 450)
        t.finish(1000)
        assertEquals(100L + 200L + 800L, t.totalMillis())
        assertEquals(listOf("b", "c"), t.top(2))
    }

    @Test
    fun formatsParts() {
        val ms = (2 * 60 + 14) * 60_000L + 5_000L
        assertEquals(2L, WellbeingTally.hours(ms))
        assertEquals(14L, WellbeingTally.minutes(ms))
    }
}
