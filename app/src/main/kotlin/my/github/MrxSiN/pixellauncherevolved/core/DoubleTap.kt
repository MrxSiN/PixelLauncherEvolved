package my.github.MrxSiN.pixellauncherevolved.core

import android.view.MotionEvent
import android.view.ViewConfiguration

/**
 * The platform's own idea of what counts as one tap following another.
 *
 * Fed the down of every tap that should count, by whichever surface is
 * counting them — the launcher's empty workspace, or the status bar.
 */
class DoubleTap(configuration: ViewConfiguration) {

    private val slop = configuration.scaledDoubleTapSlop.toFloat()
    private val timeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    private var lastTime = 0L
    private var lastX = 0f
    private var lastY = 0f

    /**
     * @return true when [event] completes a double tap, which also spends it:
     *   three taps are one double tap and one first tap, not two.
     */
    fun isSecond(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val time = event.eventTime

        val second = time - lastTime <= timeout &&
            Math.hypot((x - lastX).toDouble(), (y - lastY).toDouble()) <= slop

        lastTime = if (second) 0L else time
        lastX = x
        lastY = y

        return second
    }

    /** A tap that should not count breaks the sequence. */
    fun reset() {
        lastTime = 0L
    }
}
