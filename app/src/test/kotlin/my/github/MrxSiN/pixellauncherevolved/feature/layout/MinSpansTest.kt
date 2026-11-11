package my.github.MrxSiN.pixellauncherevolved.feature.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class MinSpansTest {

    /** CP3A: every item carries its own. */
    @Suppress("unused")
    class EarlierItem(@JvmField var minSpanX: Int, @JvmField var minSpanY: Int)

    @Suppress("unused")
    class ResizeParams(@JvmField val minSpanX: Int, @JvmField val minSpanY: Int)

    /** CP4A: a migration entry holds them in resizeParams. */
    @Suppress("unused")
    class DbEntry(@JvmField var resizeParams: ResizeParams?)

    /** CP4A: a model widget holds only its id. */
    @Suppress("unused")
    class WidgetItem(@JvmField var appWidgetId: Int)

    class Shortcut

    private val spans = MinSpans(javaClass.classLoader!!)

    private fun of(item: Any) = spans.of(null, item).let { MinSpans.x(it) to MinSpans.y(it) }

    @Test
    fun anItemsOwnFieldsAnswer() {
        assertEquals(3 to 2, of(EarlierItem(3, 2)))
        // Zero, as an unset field reads, is one cell.
        assertEquals(1 to 1, of(EarlierItem(0, 0)))
    }

    @Test
    fun aMigrationEntryAnswersFromItsResizeParams() {
        assertEquals(4 to 3, of(DbEntry(ResizeParams(4, 3))))
        assertEquals(1 to 1, of(DbEntry(null)))
    }

    @Test
    fun aWidgetWithNoProviderToAskAndAnythingElseTakeOneCell() {
        assertEquals(1 to 1, of(WidgetItem(7)))
        assertEquals(1 to 1, of(Shortcut()))
    }

    @Test
    fun theReaderIsKeptPerClassNotPerItem() {
        assertEquals(2 to 5, of(EarlierItem(2, 5)))
        assertEquals(6 to 1, of(EarlierItem(6, 1)))
        assertEquals(5 to 4, of(DbEntry(ResizeParams(5, 4))))
    }
}
