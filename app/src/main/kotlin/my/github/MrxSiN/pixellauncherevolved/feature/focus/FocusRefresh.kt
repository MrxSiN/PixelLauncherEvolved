package my.github.MrxSiN.pixellauncherevolved.feature.focus

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Reads the Modes away from the launcher's UI thread.
 *
 * Asking which Modes are on is a content provider call into this module's own
 * app, and that app answers by shelling out as root. Measured on a Pixel 8 Pro
 * running Android 17, one answer takes about 130ms with the app already up and
 * about 490ms when the call has to start it first.
 *
 * Every one of those milliseconds used to be spent on the launcher's UI thread,
 * because the read ran straight inside the window focus hook. The launcher
 * regains window focus part way through the predictive back animation home, so
 * the stall landed inside a running animation and showed as a stutter. Swiping
 * home never stuttered for the same reason it never looked wrong: focus arrives
 * there once the animation has already finished, so the identical stall fell
 * out of sight.
 *
 * So the reading happens on [background] and only the acting on it happens on
 * [main]. While a read is in flight a further request does not start a second
 * one, because both would read the same Modes; it is remembered and read once
 * the first is done. Asking to wait for the home transition wins over not
 * asking, since a reveal played during that transition is the thing being
 * avoided.
 */
internal class FocusRefresher(
    private val read: () -> FocusChange,
    private val apply: (FocusChange, Boolean) -> Unit,
    private val main: Executor,
    private val background: Executor = defaultBackground(),
) {

    private val lock = Any()
    private var reading = false
    private var waitForHomeTransition = false

    /**
     * Asks for a read.
     *
     * @param waitForHomeTransition whether the change was noticed with the
     * launcher coming to the front, so a reveal has an animation to wait for.
     */
    fun request(waitForHomeTransition: Boolean) {
        synchronized(lock) {
            this.waitForHomeTransition = this.waitForHomeTransition || waitForHomeTransition
            if (reading) return
            reading = true
        }
        background.execute(::readAndApply)
    }

    /**
     * Clears the in-flight mark before reading, not after.
     *
     * A request that arrives while this read is running is asking about a state
     * this read has already passed, so it has to queue a further read rather
     * than be folded into this one. Clearing the mark first is what lets it,
     * and the mark then bounds the queue at one waiting read.
     */
    private fun readAndApply() {
        val wait = synchronized(lock) {
            reading = false
            waitForHomeTransition.also { waitForHomeTransition = false }
        }

        val change = read()
        if (change.workspaceChanged) main.execute { apply(change, wait) }
    }

    private companion object {

        /** One thread, named so a launcher thread dump says whose it is. */
        fun defaultBackground(): Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ple-focus-modes").apply { isDaemon = true }
        }
    }
}
