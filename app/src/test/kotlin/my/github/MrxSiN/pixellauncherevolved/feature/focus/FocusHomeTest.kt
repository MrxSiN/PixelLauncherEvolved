package my.github.MrxSiN.pixellauncherevolved.feature.focus

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.focus.FocusMode
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages
import my.github.MrxSiN.pixellauncherevolved.focus.FocusSource
import my.github.MrxSiN.pixellauncherevolved.focus.FocusStore
import my.github.MrxSiN.pixellauncherevolved.focus.renumber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FocusHomeTest {

    private lateinit var store: FakeStore
    private lateinit var source: FakeSource
    private var enabled = true

    @Before
    fun setUp() {
        FocusPages.remember(emptyList())
        store = FakeStore()
        source = FakeSource()
        // Bedtime exists and is off unless a test says otherwise; a Mode the
        // source does not report is a deleted one, and loses its pages.
        source.current = listOf(FocusMode("bedtime", "Bedtime", false))
        enabled = true
    }

    @Test
    fun filteredFullBindDoesNotReplaceCompletePageCatalogue() {
        store.assign("bedtime", setOf(5))
        val focus = focus()

        assertEquals(listOf(0), focus.screens(listOf(0, 5)))
        assertEquals(listOf(0), focus.addedScreens(listOf(0)))

        assertEquals(listOf(0, 5), FocusPages.order)
    }

    @Test
    fun incrementalPageIsAddedToCatalogueAndOrdinaryHome() {
        store.assign("bedtime", setOf(5))
        val focus = focus()
        focus.screens(listOf(0, 5))

        assertEquals(listOf(9), focus.addedScreens(listOf(9)))
        assertEquals(listOf(0, 5, 9), FocusPages.order)
        assertEquals(9, FocusPages.screenAt(3))
    }

    @Test
    fun visibleWorkspaceUpdateKeepsHiddenFocusPages() {
        FocusPages.remember(listOf(0, 5, 9))

        FocusPages.include(listOf(0, 9, 12))

        assertEquals(listOf(0, 5, 9, 12), FocusPages.order)
    }

    @Test
    fun activeModeRejectsNewUnassignedPage() {
        store.assign("bedtime", setOf(5))
        source.current = listOf(FocusMode("bedtime", "Bedtime", true))
        val focus = focus()

        assertEquals(listOf(5), focus.screens(listOf(0, 5)))
        assertTrue(focus.addedScreens(listOf(9)).isEmpty())
        assertEquals(listOf(0, 5, 9), FocusPages.order)
    }

    @Test
    fun assignmentChangeInvalidatesCurrentHome() {
        val focus = focus()
        assertEquals(listOf(0, 5), focus.screens(listOf(0, 5)))
        assertFalse(focus.change().workspaceChanged)

        store.assign("bedtime", setOf(5))

        assertEquals(FocusChange(workspaceChanged = true, modeChanged = false), focus.change())
        assertTrue(focus.change().workspaceChanged)
    }

    @Test
    fun enablingFeatureInvalidatesUnfilteredHome() {
        store.assign("bedtime", setOf(5))
        enabled = false
        val focus = focus()
        assertEquals(listOf(0, 5), focus.screens(listOf(0, 5)))

        enabled = true

        assertTrue(focus.change().workspaceChanged)
    }

    @Test
    fun modeChangeToSingleAssignedPageInvalidatesOrdinaryHome() {
        store.assign("bedtime", setOf(5))
        val focus = focus()
        assertEquals(listOf(0), focus.screens(listOf(0, 5)))

        source.current = listOf(FocusMode("bedtime", "Bedtime", true))

        assertEquals(FocusChange(workspaceChanged = true, modeChanged = true), focus.change())
        assertTrue(focus.change().workspaceChanged)
        assertEquals(listOf(5), focus.screens(listOf(0, 5)))
    }

    @Test
    fun modeDeactivationIsAlsoAWorkspaceTransition() {
        store.assign("bedtime", setOf(5))
        source.current = listOf(FocusMode("bedtime", "Bedtime", true))
        val focus = focus()
        assertEquals(listOf(5), focus.screens(listOf(0, 5)))

        source.current = listOf(FocusMode("bedtime", "Bedtime", false))

        assertEquals(FocusChange(workspaceChanged = true, modeChanged = true), focus.change())
    }

    /**
     * Reading the Modes is a root-shell call into another app. Binding must not
     * pay for it, and must not pay for it twice.
     */
    @Test
    fun bindingDoesNotReadTheModesAgain() {
        store.assign("bedtime", setOf(5))
        val focus = focus()

        focus.change()
        val afterLooking = source.reads

        focus.screens(listOf(0, 5))
        focus.addedScreens(listOf(9))

        assertEquals(afterLooking, source.reads)
    }

    /** Nothing has been read at the launcher's first bind, so that one reads. */
    @Test
    fun theFirstBindReadsTheModesItself() {
        store.assign("bedtime", setOf(5))
        source.current = listOf(FocusMode("bedtime", "Bedtime", true))
        val focus = focus()

        assertEquals(listOf(5), focus.screens(listOf(0, 5)))
        assertEquals(1, source.reads)
    }

    /** A drag past the last page makes an id no Mode lists yet; it is not a hidden page. */
    @Test
    fun aPageMadeJustNowIsNotHidden() {
        store.assign("bedtime", setOf(5))
        val focus = focus()
        focus.screens(listOf(0, 5))

        assertTrue(focus.hides(5))
        assertFalse(focus.hides(0))
        assertFalse(focus.hides(6))
    }

    @Test
    fun aPageMadeOnTheOrdinaryHomeScreenStaysOrdinary() {
        store.assign("bedtime", setOf(5))
        val focus = focus()
        focus.screens(listOf(0, 5))

        focus.adopt(listOf(0, 6))

        assertEquals(listOf(0, 5, 6), FocusPages.order)
        assertEquals(setOf(5), store.assignments().getValue("bedtime"))
        assertFalse(focus.hides(6))
    }

    @Test
    fun aPageMadeWhileAModeIsShowingBelongsToThatMode() {
        store.assign("bedtime", setOf(5))
        source.current = listOf(FocusMode("bedtime", "Bedtime", true))
        val focus = focus()
        focus.screens(listOf(0, 5))

        focus.adopt(listOf(5, 6))

        assertEquals(setOf(5, 6), store.assignments().getValue("bedtime"))
        assertFalse(focus.hides(6))
        assertTrue(focus.hides(0))
    }

    @Test
    fun newAppsKeepOffEveryModesPagesOnlyWhileTheFeatureIsOn() {
        store.assign("bedtime", setOf(5))
        store.assign("driving", setOf(1, 2))
        val focus = focus()

        assertEquals(setOf(1, 2, 5), focus.reservedScreens())
        enabled = false
        assertTrue(focus.reservedScreens().isEmpty())
    }

    @Test
    fun aRemovedPageLeavesThePageListAndEveryMode() {
        store.assign("bedtime", setOf(5, 6))
        source.current = listOf(FocusMode("bedtime", "Bedtime", true))
        val focus = focus()
        focus.screens(listOf(0, 5, 6))

        focus.forget(listOf(6, -201))

        assertEquals(listOf(0, 5), FocusPages.order)
        assertEquals(setOf(5), store.assignments().getValue("bedtime"))
    }

    @Test
    fun renumberedPagesStayWithTheirModesAndKeepTheModeOrder() {
        store.assign("driving", setOf(5))
        store.assign("bedtime", setOf(1, 2))
        val order = store.priority()

        store.renumber(mapOf(5 to 1, 1 to 2, 2 to 5))

        assertEquals(setOf(1), store.assignments().getValue("driving"))
        assertEquals(setOf(2, 5), store.assignments().getValue("bedtime"))
        assertEquals(order, store.priority())
    }

    /** A Mode deleted in Settings gives its pages back to the ordinary home screen. */
    @Test
    fun aDeletedModesPagesComeBack() {
        store.assign("old-work", setOf(2))
        store.assign("driving", setOf(1))
        source.current = listOf(FocusMode("driving", "Driving", false))
        val focus = focus()

        focus.change()

        assertEquals(mapOf("driving" to setOf(1)), store.assignments())
        assertEquals(listOf("driving"), store.priority())
        assertEquals(listOf(0, 2), focus.screens(listOf(0, 1, 2)))
    }

    /** A Mode deleted after its pages were given back still leaves the Mode order. */
    @Test
    fun aDeletedModeWithNoPagesLeavesTheOrder() {
        store.assign("driving", setOf(1))
        store.reorder(listOf("old-work", "driving"))
        source.current = listOf(FocusMode("driving", "Driving", false))

        focus().change()

        assertEquals(listOf("driving"), store.priority())
    }

    /** A Mode switched off still exists, so its pages stay set aside for it. */
    @Test
    fun aModeSwitchedOffKeepsItsPages() {
        store.assign("work", setOf(2))
        source.current = listOf(FocusMode("work", "Work", isActive = false, isEnabled = false))
        val focus = focus()

        focus.change()

        assertEquals(setOf(2), store.assignments().getValue("work"))
    }

    /** A read that fails looks like no Modes at all, and must not give anything back. */
    @Test
    fun anUnreadableSourceForgetsNothing() {
        store.assign("work", setOf(2))
        source.readable = false
        val focus = focus()

        focus.change()

        assertEquals(setOf(2), store.assignments().getValue("work"))
    }

    private fun focus() = FocusHome(
        isEnabled = { enabled },
        store = store,
        source = source,
        logger = SilentLogger,
    )
}

private class FakeStore : FocusStore {
    private val pages = linkedMapOf<String, Set<Int>>()
    private var order = emptyList<String>()

    override fun assignments(): Map<String, Set<Int>> = pages.toMap()

    override fun priority(): List<String> = order

    override fun assign(modeId: String, screens: Set<Int>) {
        if (screens.isEmpty()) pages.remove(modeId) else pages[modeId] = screens
        order = listOf(modeId) + order.filterNot { it == modeId }
    }

    override fun reorder(modeIds: List<String>) {
        order = modeIds
    }
}

private class FakeSource : FocusSource {
    var current = emptyList<FocusMode>()
    var reads = 0
        private set

    override fun modes(): List<FocusMode> {
        reads++
        return current
    }

    var readable = true

    override fun isReadable(): Boolean = readable
}

private object SilentLogger : Logger {
    override fun info(message: String) = Unit

    override fun warn(message: String, error: Throwable?) = Unit
}
