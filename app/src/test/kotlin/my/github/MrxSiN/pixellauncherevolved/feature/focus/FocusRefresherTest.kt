package my.github.MrxSiN.pixellauncherevolved.feature.focus

import java.util.ArrayDeque
import java.util.concurrent.Executor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRefresherTest {

    private val reads = mutableListOf<Thread>()
    private val applied = mutableListOf<Pair<FocusChange, Boolean>>()

    @Test
    fun readsAwayFromTheThreadThatAsked() {
        val background = Executor { runnable -> Thread(runnable, "test-background").apply { start() }.join() }
        val refresher = refresher(
            changes = ArrayDeque(listOf(MODE_CHANGED)),
            main = Executor { it.run() },
            background = background,
        )

        refresher.request(waitForHomeTransition = true)

        assertEquals(1, reads.size)
        assertTrue(reads.single() !== Thread.currentThread())
    }

    @Test
    fun appliesTheChangeAndTheTransitionFlag() {
        val refresher = refresher(changes = ArrayDeque(listOf(MODE_CHANGED)))

        refresher.request(waitForHomeTransition = true)

        assertEquals(listOf(MODE_CHANGED to true), applied)
    }

    @Test
    fun anUnchangedReadAppliesNothing() {
        val refresher = refresher(changes = ArrayDeque(listOf(FocusChange.NONE)))

        refresher.request(waitForHomeTransition = false)

        assertEquals(emptyList<Pair<FocusChange, Boolean>>(), applied)
    }

    /** Two prompts about the same Modes are one read, not two. */
    @Test
    fun requestsThatArriveTogetherReadOnce() {
        val queued = ArrayDeque<Runnable>()
        val refresher = refresher(
            changes = ArrayDeque(listOf(MODE_CHANGED)),
            background = Executor { queued.add(it) },
        )

        refresher.request(waitForHomeTransition = false)
        refresher.request(waitForHomeTransition = false)
        refresher.request(waitForHomeTransition = false)

        assertEquals(1, queued.size)
        queued.remove().run()
        assertEquals(1, reads.size)
    }

    /** Waiting for the transition is the safer answer, so it survives folding. */
    @Test
    fun foldedRequestsKeepTheWaitForTheHomeTransition() {
        val queued = ArrayDeque<Runnable>()
        val refresher = refresher(
            changes = ArrayDeque(listOf(MODE_CHANGED)),
            background = Executor { queued.add(it) },
        )

        refresher.request(waitForHomeTransition = false)
        refresher.request(waitForHomeTransition = true)
        queued.remove().run()

        assertEquals(listOf(MODE_CHANGED to true), applied)
    }

    /** A prompt during a read is about a later state, so it reads again. */
    @Test
    fun aRequestDuringAReadIsReadAfterIt() {
        val queued = ArrayDeque<Runnable>()
        val refresher = refresher(
            changes = ArrayDeque(listOf(FocusChange.NONE, MODE_CHANGED)),
            background = Executor { queued.add(it) },
            onRead = { it.request(waitForHomeTransition = false) },
        )

        refresher.request(waitForHomeTransition = false)
        queued.remove().run()

        assertEquals(1, queued.size)
        queued.remove().run()
        assertEquals(2, reads.size)
        assertEquals(listOf(MODE_CHANGED to false), applied)
    }

    private fun refresher(
        changes: ArrayDeque<FocusChange>,
        main: Executor = Executor { it.run() },
        background: Executor = Executor { it.run() },
        onRead: (FocusRefresher) -> Unit = {},
    ): FocusRefresher {
        lateinit var refresher: FocusRefresher
        refresher = FocusRefresher(
            read = {
                reads += Thread.currentThread()
                onRead(refresher)
                changes.poll() ?: FocusChange.NONE
            },
            apply = { change, wait -> applied += change to wait },
            main = main,
            background = background,
        )
        return refresher
    }

    private companion object {
        val MODE_CHANGED = FocusChange(workspaceChanged = true, modeChanged = true)
    }
}
