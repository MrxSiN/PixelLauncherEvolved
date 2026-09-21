package my.github.MrxSiN.pixellauncherevolved.lock

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Turns the screen off without exposing how the privileged action is performed. */
fun interface ScreenLocker {
    fun lock(): Boolean
}

/**
 * Sends Android's sleep-key event through the device's root shell.
 *
 * Deliberately not the power key. The power key is a toggle that the platform
 * also counts: two of them inside the multi-press window are the "press power
 * twice for the camera" gesture, so a gesture that fired twice — or a tap the
 * user repeated because the root shell had not answered yet — opened the
 * camera instead of turning the screen off. `KEYCODE_SLEEP` carries no such
 * gesture and only ever sleeps: sent while the screen is already off it does
 * nothing, where a second power key would have turned the screen back on.
 * Nothing here touches the power button itself, so double pressing it still
 * opens the camera.
 *
 * It also ends the screen the way a tap on the always-on display begins it.
 * `KEYCODE_SLEEP` sleeps for `GO_TO_SLEEP_REASON_SLEEP_BUTTON`, which leaves
 * SystemUI on its lift reveal — light drains down the screen, the reverse of
 * the ambient-display tap that fills it from the bottom up — rather than the
 * power button's own reveal, which collapses sideways into the button's edge.
 */
class RootScreenLocker : ScreenLocker {

    override fun lock(): Boolean {
        val process = runCatching {
            ProcessBuilder("su", "-c", SLEEP_KEY_COMMAND)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return false

        return try {
            process.outputStream.close()
            val completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) process.destroy()
            completed && process.exitValue() == 0
        } catch (_: Throwable) {
            process.destroy()
            false
        }
    }

    private companion object {
        const val SLEEP_KEY_COMMAND = "input keyevent KEYCODE_SLEEP"
        const val TIMEOUT_SECONDS = 15L
    }
}

/**
 * Lets one screen off run at a time.
 *
 * Reaching the root shell takes long enough to be seen, and the home screen
 * keeps taking touches meanwhile, so a second gesture can arrive before the
 * first has ended the screen. Running it too would spend a root shell on a
 * screen that is already going dark, and land its key late enough to catch a
 * screen the user has since woken.
 *
 * A dropped request is reported as a success because the screen is being
 * turned off either way — by the call already in flight.
 */
class SingleFlightScreenLocker(private val locker: ScreenLocker) : ScreenLocker {

    private val locking = AtomicBoolean(false)

    override fun lock(): Boolean {
        if (!locking.compareAndSet(false, true)) return true

        return try {
            locker.lock()
        } finally {
            locking.set(false)
        }
    }
}
