package my.github.MrxSiN.pixellauncherevolved.feature.glance

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GlancePlacementTest {

    private fun screens(vararg shown: Int, blocked: Set<Int> = emptySet(), original: Int? = null, every: Boolean = false, selected: Int? = null) =
        GlancePlacement.screens(shown, blocked, original, every, selected)

    @Test
    fun theOrdinaryHomeScreenKeepsOnlyTheLaunchersOwn() {
        assertArrayEquals(intArrayOf(0), screens(1, 6, original = 0))
    }

    @Test
    fun aModeThatHidesScreenZeroGetsItOnItsFirstPage() {
        assertArrayEquals(intArrayOf(6), screens(7, 6))
    }

    @Test
    fun aTakenTopRowGetsNoCopy() {
        assertArrayEquals(intArrayOf(), screens(6, 7, blocked = setOf(6)))
        assertArrayEquals(intArrayOf(7), screens(6, 7, blocked = setOf(6), every = true))
    }

    @Test
    fun everyScreenSkipsTakenOnes() {
        assertArrayEquals(intArrayOf(0, 2, 6), screens(6, 1, 2, blocked = setOf(1), original = 0, every = true))
        assertArrayEquals(intArrayOf(1, 2), screens(2, 1, every = true))
    }

    @Test
    fun aSelectedScreenReplacesTheFirstOne() {
        assertArrayEquals(intArrayOf(7), screens(7, original = 0, selected = 7))
        assertArrayEquals(intArrayOf(7), screens(6, 7, selected = 7))
    }

    @Test
    fun aSelectedScreenThatIsNotShownFallsBackToTheFirst() {
        assertArrayEquals(intArrayOf(6), screens(6, 7, selected = 9))
        assertArrayEquals(intArrayOf(0), screens(1, original = 0, selected = 9))
    }

    @Test
    fun noPagesMeansNoCopy() {
        assertArrayEquals(intArrayOf(), screens(every = true))
    }

    @Test
    fun copyIdsAreNegativeAndDistinct() {
        assert(GlancePlacement.copyId(0) < -1)
        assertNotEquals(GlancePlacement.copyId(1), GlancePlacement.copyId(2))
    }
}
