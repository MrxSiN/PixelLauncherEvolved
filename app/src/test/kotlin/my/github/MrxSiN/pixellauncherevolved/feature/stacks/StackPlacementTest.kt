package my.github.MrxSiN.pixellauncherevolved.feature.stacks

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StackPlacementTest {

    private fun grid(vararg taken: Pair<Int, Int>): (Int, Int) -> Boolean {
        val cells = taken.toSet()
        return { x, y -> (x to y) in cells }
    }

    @Test
    fun `first free cells row by row`() {
        // 4x6, the top two rows full: a 4x2 widget goes in row 2.
        val full = (0..3).flatMap { x -> (0..1).map { y -> x to y } }.toTypedArray()
        assertArrayEquals(intArrayOf(0, 2), StackPlacement.firstFree(4, 6, 4, 2, grid(*full)))
        // One cell taken in row 0: a 2x2 widget goes beside it.
        assertArrayEquals(intArrayOf(1, 0), StackPlacement.firstFree(4, 6, 2, 2, grid(0 to 0)))
    }

    @Test
    fun `no room is no cell`() {
        val all = (0..3).flatMap { x -> (0..5).map { y -> x to y } }.toTypedArray()
        assertNull(StackPlacement.firstFree(4, 6, 1, 1, grid(*all)))
        assertNull(StackPlacement.firstFree(4, 6, 5, 1, grid()))
    }
}
