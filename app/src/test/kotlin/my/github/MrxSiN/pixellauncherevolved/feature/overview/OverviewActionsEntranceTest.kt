package my.github.MrxSiN.pixellauncherevolved.feature.overview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewActionsEntranceTest {

    @Test
    fun `every button is at rest when the opening ends`() {
        for (index in 0 until 3) {
            assertEquals(1f, OverviewActionsEntrance.progressOf(index, count = 3, progress = 1f), 0f)
        }
    }

    @Test
    fun `nothing has begun when the opening has not`() {
        for (index in 0 until 3) {
            assertEquals(0f, OverviewActionsEntrance.progressOf(index, count = 3, progress = 0f), 0f)
        }
    }

    @Test
    fun `each button is behind the one before it`() {
        val half = (0 until 3).map { OverviewActionsEntrance.progressOf(it, count = 3, progress = 0.5f) }

        assertTrue("$half", half[0] > half[1])
        assertTrue("$half", half[1] > half[2])
    }

    @Test
    fun `a lone button follows the opening exactly`() {
        assertEquals(0.4f, OverviewActionsEntrance.progressOf(0, count = 1, progress = 0.4f), 0f)
    }

    @Test
    fun `a crowded row tightens its stagger rather than arriving late`() {
        assertEquals(1f, OverviewActionsEntrance.progressOf(7, count = 8, progress = 1f), 0f)
        assertTrue(OverviewActionsEntrance.progressOf(7, count = 8, progress = 0.8f) > 0f)
    }
}
