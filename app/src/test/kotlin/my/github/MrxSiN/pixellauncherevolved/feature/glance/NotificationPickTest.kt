package my.github.MrxSiN.pixellauncherevolved.feature.glance

import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more.NotificationPick

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPickTest {
    private val pkgs = listOf("a", "b", "c", "b")
    private val times = longArrayOf(10, 30, 20, 40)

    private fun pick(apps: Set<String>, skip: Set<Int> = emptySet()) =
        NotificationPick.newest(4, apps, { pkgs[it] }, { times[it] }, { it in skip })

    @Test
    fun emptyAppsMeansAllAndNewestWins() = assertEquals(3, pick(emptySet()))

    @Test
    fun onlyChosenAppsCount() = assertEquals(2, pick(setOf("a", "c")))

    @Test
    fun skippedAreIgnored() = assertEquals(1, pick(setOf("b"), setOf(3)))

    @Test
    fun nothingEligibleIsMinusOne() = assertEquals(-1, pick(setOf("z")))

    @Test
    fun storedAppsRoundTrip() {
        assertEquals(setOf("a.b", "c"), NotificationPick.apps(" c, a.b,,"))
        assertEquals("a.b,c", NotificationPick.encode(setOf("c", "a.b")))
        assertEquals(emptySet<String>(), NotificationPick.apps(null))
    }
}
