package my.github.MrxSiN.pixellauncherevolved.feature.stacks

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackGraphTest {

    private fun at(vararg placements: Pair<Int, StackGraph.Placement>): (Int) -> StackGraph.Placement? {
        val map = placements.toMap()
        return { map[it] }
    }

    private fun alone(screen: Int) = StackGraph.Placement(screen, aloneOnScreen = true)

    @Test
    fun `round trips and rejects what it cannot read`() {
        val graph = StackGraph.parse("2;1:10@0,11@3,12@0|0:20@1,21@0")
        assertEquals("2;1:10@0,11@3,12@0|0:20@1,21@0", graph.format())
        assertTrue(StackGraph.parse("3;0:1,2").stacks.isEmpty())
        assertTrue(StackGraph.parse("garbage").stacks.isEmpty())
        assertTrue(StackGraph.parse(null).stacks.isEmpty())
        // A stack of one, or one whose shown index is out of range, is dropped.
        assertEquals("2;0:3@0,4@0", StackGraph.parse("2;0:1@0|5:1@0,2@0|0:3@0,4@0").format())
        // A slot it does not know reads as none.
        assertEquals("2;0:1@0,2@0", StackGraph.parse("2;0:1@9,2@x").format())
    }

    @Test
    fun `version 1 still reads, with no slots`() {
        assertEquals("2;1:10@0,11@0,12@0", StackGraph.parse("1;1:10,11,12").format())
    }

    @Test
    fun `hides only pages a waiting widget has to itself`() {
        val graph = StackGraph.parse("2;0:10@0,11@2,12@0")
        val resolved = graph.validate(
            at(
                10 to StackGraph.Placement(0, aloneOnScreen = false),
                11 to alone(7),
                12 to StackGraph.Placement(8, aloneOnScreen = false),
            ),
        )
        assertEquals("2;0:10@0,11@2", resolved.graph.format())
        assertArrayEquals(intArrayOf(7), resolved.hiddenScreens)
        assertArrayEquals(intArrayOf(10), resolved.anchors)
    }

    @Test
    fun `a stack whose widget on show is gone comes apart and hides nothing`() {
        val resolved = StackGraph.parse("2;1:10@0,11@0,12@0").validate(at(10 to alone(5), 12 to alone(6)))
        assertTrue(resolved.graph.stacks.isEmpty())
        assertEquals(0, resolved.hiddenScreens.size)
    }

    @Test
    fun `a widget is in one stack at most`() {
        val resolved = StackGraph.parse("2;0:1@0,2@0|0:3@0,2@0").validate(
            at(1 to alone(0), 2 to alone(9), 3 to alone(1)),
        )
        assertEquals("2;0:1@0,2@0", resolved.graph.format())
        assertArrayEquals(intArrayOf(9), resolved.hiddenScreens)
    }

    @Test
    fun `no stack holds more than ten`() {
        val ids = (1..12).joinToString(",") { "$it@0" }
        val placements = (1..12).map { it to alone(100 + it) }.toTypedArray()
        val resolved = StackGraph.parse("2;0:$ids").validate(at(*placements))
        assertEquals(StackGraph.MAX_MEMBERS, resolved.graph.stacks.single().members.size)
        assertTrue(resolved.graph.stacks.single().isFull)
    }

    @Test
    fun `taking a member out keeps the one on show and the others' slots`() {
        val stack = StackGraph.Stack(intArrayOf(1, 2, 3), 2, intArrayOf(1, 2, 3))
        val left = stack.without(2)!!
        assertArrayEquals(intArrayOf(1, 3), left.members)
        assertArrayEquals(intArrayOf(1, 3), left.slots)
        assertEquals(3, left.shownId)
        assertEquals(null, StackGraph.Stack(intArrayOf(1, 2), 0).without(2))
    }

    @Test
    fun `moving a member keeps the one on show and carries its slot`() {
        val stack = StackGraph.Stack(intArrayOf(1, 2, 3), 1, intArrayOf(0, 4, 2))
        val moved = stack.moved(2, 0)
        assertArrayEquals(intArrayOf(3, 1, 2), moved.members)
        assertArrayEquals(intArrayOf(2, 0, 4), moved.slots)
        assertEquals(2, moved.shownId)
        assertArrayEquals(intArrayOf(3, 1, 2, 9), moved.plus(9).members)
        assertEquals(StackGraph.Slot.EVENING, moved.withSlot(0, StackGraph.Slot.EVENING).slots[0])
    }

    @Test
    fun `parts of the day`() {
        assertEquals(StackGraph.Slot.NIGHT, StackGraph.Slot.at(3))
        assertEquals(StackGraph.Slot.MORNING, StackGraph.Slot.at(5))
        assertEquals(StackGraph.Slot.AFTERNOON, StackGraph.Slot.at(12))
        assertEquals(StackGraph.Slot.EVENING, StackGraph.Slot.at(21))
        assertEquals(StackGraph.Slot.NIGHT, StackGraph.Slot.at(22))
    }
    @Test
    fun `sameAs agrees with comparing the stored text`() {
        val texts = listOf(
            "", "2;0:1@0,2@0", "2;1:1@0,2@0", "2;0:1@0,2@1", "2;0:2@0,1@0", "2;0:1@0,2@0,3@0",
            "2;0:1@0,2@0|0:3@0,4@0", "2;0:3@0,4@0|0:1@0,2@0", "1;0:1,2",
        )
        for (a in texts) for (b in texts) {
            val x = StackGraph.parse(a)
            val y = StackGraph.parse(b)
            assertEquals("$a vs $b", x.format() == y.format(), x.sameAs(y))
        }
    }

    @Test
    fun `stackOf finds the stack holding an id`() {
        val graph = StackGraph.parse("2;0:1@0,2@0|1:3@0,4@0")
        assertEquals(3, graph.stackOf(4)?.members?.get(0))
        assertEquals(1, graph.stackOf(2)?.members?.get(0))
        assertEquals(null, graph.stackOf(5))
    }
}
