package my.github.MrxSiN.pixellauncherevolved.picker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.View
import android.view.animation.PathInterpolator

/**
 * How a page opens over Wallpaper & style and closes again.
 *
 * With a [hero] whose twin on the picker can be found ([heroFrom]), it is a
 * container transform, as the picker's own previews grow into their pages: the
 * hero starts exactly over its twin and grows into its place over 450ms on the
 * emphasized curve, while the rest of the page fades in over the picker in the
 * first 150ms. Closing plays it back, the page fading in the last 150ms as the
 * hero lands on its twin. The picker is never faded out underneath, so no frame
 * shows neither page.
 *
 * Without one, it is the shared X axis Android 17's Settings moves between a
 * page and the one it opens, read off its `shared_x_axis_activity_*`
 * animations: the new page comes in from a quarter of the width to the right,
 * over 450ms on the emphasized curve, fading in over 350ms after 100ms, while
 * the page under it goes a quarter to the left and fades out in 100ms; closing
 * is the same in mirror.
 *
 * A back gesture can also hold the page part closed ([follow]), as the system's
 * predictive back does: it shrinks a little and leans toward the edge the
 * gesture came from, then closes from there or springs back. The animator
 * duration scale, and Remove animations, apply as to any `ValueAnimator`.
 */
internal class SharedAxisTransition(
    private val page: View,
    private val under: View?,
    private val hero: View? = null,
    private val heroFrom: () -> Rect? = { null },
    /** The options under the hero, which rise into place from below as the picker's option sheets do. */
    private val rising: List<View> = emptyList(),
) {

    private var running: ValueAnimator? = null

    /** Where the hero starts (opening) or lands (closing), against where it lies; null for the shared axis. */
    private var morph: FloatArray? = null

    /** Opens the page. */
    fun open() = run(opening = true) {}

    /** Closes the page, then [onEnd]. */
    fun close(onEnd: () -> Unit) = run(opening = false) {
        reset(under)
        onEnd()
    }

    /**
     * Holds the page part closed by a back gesture, [progress] from 0 to 1, from the
     * left edge where [fromLeft], as predictive back shows what going back would do.
     */
    fun follow(progress: Float, fromLeft: Boolean) {
        running?.cancel()
        val shrink = 1f - BACK_SHRINK * progress
        page.scaleX = shrink
        page.scaleY = shrink
        page.translationX = page.width * BACK_LEAN * progress * if (fromLeft) 1f else -1f
    }

    /** Lets go of a back gesture that was cancelled: the page returns to its place. */
    fun settle() {
        val scale = page.scaleX
        val lean = page.translationX
        running?.cancel()
        running = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = SETTLE_MS
            interpolator = EMPHASIZED_DECELERATE
            addUpdateListener {
                val left = it.animatedValue as Float
                page.scaleX = 1f + (scale - 1f) * left
                page.scaleY = page.scaleX
                page.translationX = lean * left
            }
            start()
        }
    }

    private fun run(opening: Boolean, onEnd: () -> Unit) {
        running?.cancel()
        // A back gesture may have shrunk the page; it closes from there, easing back to full size as it goes.
        val scale = page.scaleX
        val lean = page.translationX
        morph = measureMorph()
        frame(0f, opening, scale, lean)
        running = ValueAnimator.ofFloat(0f, SLIDE_MS.toFloat()).apply {
            duration = SLIDE_MS
            interpolator = null
            addUpdateListener { frame(it.animatedValue as Float, opening, scale, lean) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    frame(SLIDE_MS.toFloat(), opening, scale, lean)
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    /** The hero's twin as offsets and scales from where the hero lies untransformed, or null. */
    private fun measureMorph(): FloatArray? {
        val view = hero ?: return null
        if (view.width == 0 || view.height == 0) return null
        val from = heroFrom() ?: return null
        if (from.isEmpty) return null
        view.pivotX = 0f
        view.pivotY = 0f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        val at = IntArray(2)
        view.getLocationOnScreen(at)
        return floatArrayOf(
            (from.left - at[0]).toFloat(),
            (from.top - at[1]).toFloat(),
            from.width().toFloat() / view.width,
            from.height().toFloat() / view.height,
        )
    }

    /** Places the views [elapsed] milliseconds into opening or closing. */
    private fun frame(elapsed: Float, opening: Boolean, scale: Float, lean: Float) {
        val slide = EMPHASIZED.getInterpolation((elapsed / SLIDE_MS).coerceIn(0f, 1f))
        val twin = morph
        if (twin != null) {
            // Share of the way from the twin to the hero's own place.
            val grown = if (opening) slide else 1f - slide
            hero?.apply {
                translationX = twin[0] * (1f - grown)
                translationY = twin[1] * (1f - grown)
                scaleX = twin[2] + (1f - twin[2]) * grown
                scaleY = twin[3] + (1f - twin[3]) * grown
            }
            // The options travel a share of the page's height and fade with the travel.
            val lift = page.height * RISE
            for (option in rising) {
                option.translationY = lift * (1f - grown)
                option.alpha = grown
            }
            if (opening) {
                page.translationX = 0f
                page.alpha = DECELERATE.getInterpolation((elapsed / CONTAINER_FADE_MS).coerceIn(0f, 1f))
            } else {
                page.translationX = lean * (1f - slide)
                page.scaleX = scale + (1f - scale) * slide
                page.scaleY = page.scaleX
                page.alpha = 1f - ACCELERATE.getInterpolation(((elapsed - (SLIDE_MS - CONTAINER_FADE_MS)) / CONTAINER_FADE_MS).coerceIn(0f, 1f))
            }
            return
        }

        val fadeOut = 1f - ACCELERATE.getInterpolation((elapsed / FADE_OUT_MS).coerceIn(0f, 1f))
        val fadeIn = DECELERATE.getInterpolation(((elapsed - FADE_IN_DELAY_MS) / FADE_IN_MS).coerceIn(0f, 1f))
        val shift = page.width * SHIFT
        if (opening) {
            page.translationX = shift * (1f - slide)
            page.alpha = fadeIn
            under?.translationX = -shift * slide
            under?.alpha = fadeOut
        } else {
            page.translationX = lean * (1f - slide) + shift * slide
            page.scaleX = scale + (1f - scale) * slide
            page.scaleY = page.scaleX
            page.alpha = fadeOut
            under?.translationX = -shift * (1f - slide)
            under?.alpha = fadeIn
        }
    }

    private fun reset(view: View?) {
        view ?: return
        view.translationX = 0f
        view.alpha = 1f
    }

    private companion object {
        /** How far each page moves, against the width. */
        const val SHIFT = 0.25f

        const val SLIDE_MS = 450L
        const val FADE_OUT_MS = 100f
        const val FADE_IN_DELAY_MS = 100f
        const val FADE_IN_MS = 350f
        const val SETTLE_MS = 300L

        /** How far the options rise into place, against the page's height. */
        const val RISE = 0.12f

        /** How long the rest of the page takes to fade in or out around a growing hero. */
        const val CONTAINER_FADE_MS = 150f

        /** How much a back gesture shrinks the page, and leans it toward the edge, at its end. */
        const val BACK_SHRINK = 0.1f
        const val BACK_LEAN = 0.05f

        /** `fast_out_extra_slow_in`, Material's emphasized curve. */
        val EMPHASIZED = PathInterpolator(
            android.graphics.Path().apply {
                moveTo(0f, 0f)
                cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
                cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
            },
        )
        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val ACCELERATE = PathInterpolator(0.3f, 0f, 1f, 1f)
        val DECELERATE = PathInterpolator(0f, 0f, 0f, 1f)
    }
}
