package my.github.MrxSiN.pixellauncherevolved.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which Mode's pages show when several Modes are on: the precedence, rule by rule. */
class FocusPlanTest {

    private val pages = mapOf("work" to setOf(2), "driving" to setOf(3), "sleep" to setOf(4))

    @Test
    fun onlyModesThatAreOnAndHavePagesCompete() {
        // "gym" is on but has no pages: it inherits the ordinary home and wins nothing.
        assertNull(FocusPlan.winner(pages, priority = emptyList(), active = mapOf("gym" to 9L)))
        assertEquals("work", FocusPlan.winner(pages, priority = emptyList(), active = mapOf("gym" to 9L, "work" to 1L)))
    }

    @Test
    fun theModeThatCameOnLastWins() {
        val active = mapOf("work" to 100L, "driving" to 200L)
        assertEquals("driving", FocusPlan.winner(pages, priority = listOf("work", "driving"), active = active))
    }

    @Test
    fun aTieOrUnknownTimesFallBackOnTheOrderThenTheUnrankedThenTheId() {
        val tied = mapOf("work" to 0L, "driving" to 0L, "sleep" to 0L)
        assertEquals("sleep", FocusPlan.winner(pages, priority = listOf("sleep", "work"), active = tied))
        // Ranked ahead of unranked, whatever the ids say.
        assertEquals("work", FocusPlan.winner(pages, priority = listOf("work"), active = tied))
        // Nothing ranked: the id decides, so the same Modes always give the same answer.
        assertEquals("driving", FocusPlan.winner(pages, priority = emptyList(), active = tied))
    }

    @Test
    fun whenTheWinnerEndsTheNextOneTakesOverThenTheOrdinaryHome() {
        val both = mapOf("work" to 100L, "driving" to 200L)
        assertEquals("driving", FocusPlan.winner(pages, emptyList(), both))
        assertEquals("work", FocusPlan.winner(pages, emptyList(), both - "driving"))
        assertNull(FocusPlan.winner(pages, emptyList(), emptyMap()))
        assertEquals(listOf(0, 1), FocusPlan.screens(listOf(0, 1, 2, 3, 4), pages, chosen = null))
    }

    @Test
    fun withOrdinaryAddsTheOrdinaryPagesInOrderAndNeverAnotherModes() {
        val all = listOf(0, 1, 2, 3, 4)
        assertEquals(listOf(2), FocusPlan.screens(all, pages, chosen = "work"))
        assertEquals(listOf(0, 1, 2), FocusPlan.screens(all, pages, chosen = "work", withOrdinary = true))
        // Off by default, and nothing changes with no Mode chosen.
        assertEquals(listOf(0, 1), FocusPlan.screens(all, pages, chosen = null, withOrdinary = true))
    }
}
