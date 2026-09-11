package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator

import my.github.MrxSiN.pixellauncherevolved.core.ExpressiveMotion

/**
 * How the Overview action buttons arrive.
 *
 * The launcher fades the whole row up while Overview opens, so Screenshot,
 * Select and everything beside them are already in place, already lit, well
 * before the task cards settle — three labels that came up with the background
 * rather than a row of buttons that arrived. Material 3 Expressive would have
 * them enter: rising into place, sprung rather than eased, each one a moment
 * behind the one before it, and landing as the rest of the screen lands.
 *
 * The entrance is therefore driven by how far the opening has got rather than
 * by a clock of its own. The launcher's fade is that progress — it runs from
 * nothing to full across exactly the transition the cards are flying in on — so
 * a button placed from it finishes when the transition finishes, whether that
 * took a flung gesture's two hundred milliseconds or a slow drag's two seconds.
 * A clock cannot promise that: it would have to know in advance how long the
 * launcher intends to take, and be wrong every time a person scrubs the gesture
 * rather than throwing it.
 *
 * Coming to Overview from an app there is no such fade: the row is put up
 * already opaque, after the app has finished shrinking into its card. Nothing is
 * still arriving for the buttons to arrive with, so [animator] runs the same
 * placement off a clock instead. The look is identical; only what advances it
 * differs.
 *
 * Only the buttons are touched. The row's own opacity belongs to the launcher,
 * which drives it from several state transitions at once, so nothing here
 * writes it: a button's opacity, offset and scale are properties the launcher
 * leaves alone.
 */
object OverviewActionsEntrance {

    /**
     * Places every button in [row] for an opening [progress] of the way
     * through, and says whether it could.
     *
     * A row the launcher has not laid out yet has nowhere to rise from, and
     * saying so lets the caller ask again on the next frame rather than count
     * an entrance nobody saw.
     */
    fun applyTo(row: ViewGroup, progress: Float): Boolean {
        val buttons = row.visibleChildren()
        if (buttons.isEmpty() || row.width == 0) return false

        val rise = row.resources.displayMetrics.density * RISE_DP

        buttons.forEachIndexed { index, button ->
            val own = progressOf(index, buttons.size, progress)
            val spatial = SPATIAL.getInterpolation(own)

            button.alpha = FADE.getInterpolation(minOf(own * FADE_LEAD, 1f))
            button.translationY = rise * (1f - spatial)
            button.scaleX = ENTER_SCALE + (1f - ENTER_SCALE) * spatial
            button.scaleY = button.scaleX
        }

        return true
    }

    /**
     * An arrival on a clock, for an opening that offers no progress to follow.
     *
     * The caller owns it: starting it, and cancelling it if the row goes away
     * or a fade turns up after all.
     */
    fun animator(row: ViewGroup): ValueAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ENTRANCE_MS
            // The curves live in the placement, so this only has to walk
            // evenly from one end of it to the other.
            interpolator = LinearInterpolator()
            addUpdateListener { applyTo(row, it.animatedValue as Float) }
        }

    /** Gives the buttons their resting state, which is where an arrival ends. */
    fun settle(row: ViewGroup) {
        for (button in row.children()) {
            button.alpha = 1f
            button.translationY = 0f
            button.scaleX = 1f
            button.scaleY = 1f
        }
    }

    /**
     * How far one button of [count] is through its own arrival.
     *
     * Each button begins a little after the one before it, so the row reads
     * left to right, and the spans are cut so the last one still reaches its
     * end exactly at the end of the opening — which is the whole point of
     * driving this from the opening rather than from a clock.
     *
     * With enough buttons to fill the run the stagger is tightened rather than
     * letting the last one arrive after everything else has stopped moving.
     */
    internal fun progressOf(index: Int, count: Int, progress: Float): Float {
        if (count <= 1) return progress.coerceIn(0f, 1f)

        val stagger = minOf(STAGGER, MAX_STAGGERED_SPAN / (count - 1))
        val start = index * stagger

        return ((progress - start) / (1f - (count - 1) * stagger)).coerceIn(0f, 1f)
    }

    private fun ViewGroup.children(): List<View> = (0 until childCount).map(::getChildAt)

    private fun ViewGroup.visibleChildren(): List<View> =
        children().filter { it.visibility == View.VISIBLE }

    /**
     * How much of its own arrival a button spends fading in.
     *
     * A short part of it. Coming from the home screen the launcher is fading
     * the row these sit in at the same time, so opacity is already being taken
     * care of; a button that spends its whole arrival fading as well is a
     * button whose movement happens while it is too faint to be seen, which is
     * how an entrance ends up reading as a plain fade.
     */
    private const val FADE_LEAD = 2.5f

    /** How far below its place a button starts. */
    private const val RISE_DP = 12f
    private const val ENTER_SCALE = 0.9f

    /** How much of the opening separates one button's arrival from the next. */
    private const val STAGGER = 0.12f

    /** At most this much of the opening is given over to the stagger. */
    private const val MAX_STAGGERED_SPAN = 0.4f

    /** The run the spring's shape is written against. */
    private const val SPRING_REFERENCE_MS = 400L

    /**
     * How long an arrival takes when it has no transition to land with.
     *
     * The spring's own length plus the stagger, so the last button settles as
     * the spring settles rather than after it.
     */
    private const val ENTRANCE_MS = 500L

    /**
     * The curves the arrival is drawn on.
     *
     * The spring is Material 3 Expressive's spatial one, written against the
     * length it would run at on its own; driving it from the launcher's own
     * progress stretches or compresses that curve without changing its shape.
     * Opacity is not sprung, because an overshoot past opaque is not something
     * a screen can show.
     */
    private val SPATIAL = ExpressiveMotion.spatialSpring(SPRING_REFERENCE_MS)
    private val FADE = ExpressiveMotion.EMPHASIZED_DECELERATE
}
