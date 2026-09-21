package my.github.MrxSiN.pixellauncherevolved.reveal

import kotlin.math.max

/**
 * The circle SystemUI draws on its light reveal scrim, around a given point.
 *
 * SystemUI has this shape already — `com.android.systemui.statusbar.CircleReveal`
 * — and builds one from the point an always-on display tap was taken at. It is
 * restated here rather than borrowed because the shipped class has no
 * constructor left to call: its only call site is inlined, so the compiler
 * removed it.
 *
 * [amount] is how revealed the screen is: 1 is the screen fully lit, 0 the
 * screen dark. A wake runs it up, a sleep runs it down, so the same circle
 * played against a falling amount is the reverse of the tap that wakes.
 */
class CircleReveal(val centerX: Int, val centerY: Int, val endRadius: Int) {

    /** Left, top, right, bottom of the lit circle, as the scrim wants them. */
    fun bounds(amount: Float): FloatArray {
        val radius = endRadius * amount

        return floatArrayOf(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
        )
    }

    /**
     * How opaque the dark end of the gradient is.
     *
     * Held at full until the screen is half revealed, so the circle has shrunk
     * to half the screen before the rest of it starts to fade.
     */
    fun endColorAlpha(amount: Float): Float = 1f - 2f * max(amount - HALF, 0f)

    companion object {

        private const val HALF = 0.5f

        /**
         * A circle around ([x], [y]) big enough to cover a [width] by [height]
         * screen, so the reveal ends with no corner left unlit.
         */
        fun aroundPoint(x: Int, y: Int, width: Int, height: Int): CircleReveal = CircleReveal(
            centerX = x,
            centerY = y,
            endRadius = max(max(x, width - x), max(y, height - y)),
        )
    }
}
