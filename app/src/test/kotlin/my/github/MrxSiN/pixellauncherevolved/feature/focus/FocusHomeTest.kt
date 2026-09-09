package my.github.MrxSiN.pixellauncherevolved.feature.focus

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.focus.FocusMode
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages
import my.github.MrxSiN.pixellauncherevolved.focus.FocusSource
import my.github.MrxSiN.pixellauncherevolved.focus.FocusStore

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
        assertFalse(focus.hasChanged())

        store.assign("bedtime", setOf(5))

        assertEquals(FocusChange(workspaceChanged = true, modeChanged = false), focus.change())
        assertTrue(focus.hasChanged())
    }

    @Test
    fun enablingFeatureInvalidatesUnfilteredHome() {
        store.assign("bedtime", setOf(5))
        enabled = false
        val focus = focus()
        assertEquals(listOf(0, 5), focus.screens(listOf(0, 5)))

        enabled = true

        assertTrue(focus.hasChanged())
    }

    @Test
    fun modeChangeToSingleAssignedPageInvalidatesOrdinaryHome() {
        store.assign("bedtime", setOf(5))
        val focus = focus()
        assertEquals(listOf(0), focus.screens(listOf(0, 5)))

        source.current = listOf(FocusMode("bedtime", "Bedtime", true))

        assertEquals(FocusChange(workspaceChanged = true, modeChanged = true), focus.change())
        assertTrue(focus.hasChanged())
        assertEquals(listOf(5), focus.screens(listOf(0, 5)))
    }

    @Test
    fun modeDeactivationIsAlsoAWorkspaceTransition() {
        store.assign("bedtime", setOf(5))
        source.current = listOf(FocusMode("bedtime", "Bedtime", true))
        val focus = focus()
        assertEquals(listOf(5), focus.screens(listOf(0, 5)))

        source.current = emptyList()

        assertEquals(FocusChange(workspaceChanged = true, modeChanged = true), focus.change())
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

    override fun modes(): List<FocusMode> = current

    override fun isReadable(): Boolean = true
}

private object SilentLogger : Logger {
    override fun info(message: String) = Unit

    override fun warn(message: String, error: Throwable?) = Unit
}
