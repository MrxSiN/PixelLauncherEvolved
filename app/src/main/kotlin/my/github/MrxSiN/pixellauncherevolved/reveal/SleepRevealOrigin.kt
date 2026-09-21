package my.github.MrxSiN.pixellauncherevolved.reveal

import my.github.MrxSiN.pixellauncherevolved.bridge.Bridge

/** A point on the screen, in pixels. */
data class Origin(val x: Int, val y: Int)

/**
 * The point the running screen off should be drawn around, while it is still
 * this gesture's.
 *
 * A point is given when a double tap asks for the screen off and is kept until
 * the reveal that used it has finished, or until it is too old to belong to
 * the gesture that sent it — a tap that never reached a screen off, because
 * root refused it or the user woke the screen again, must not colour the next
 * screen off, which the power button or a timeout may have started somewhere
 * else entirely.
 *
 * Read and written on the main thread: the broadcast is delivered there, and
 * the reveal is drawn there.
 */
class SleepRevealOrigin(
    private val freshForMillis: Long = Bridge.POINT_FRESH_FOR_MILLIS,
    private val now: () -> Long,
) {

    private var origin: Origin? = null
    private var takenAt = 0L

    fun remember(x: Int, y: Int) {
        origin = Origin(x, y)
        takenAt = now()
    }

    /** The point to draw around, or null when there is none left to use. */
    fun current(): Origin? {
        if (origin != null && now() - takenAt > freshForMillis) forget()

        return origin
    }

    fun forget() {
        origin = null
    }
}
