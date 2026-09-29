package my.github.MrxSiN.pixellauncherevolved.feature.icons

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.appwidget.AppWidgetHostView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator

import java.lang.ref.WeakReference
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * Brings the home screen's icons in with their new artwork, rather than
 * swapping them in a cut.
 *
 * After a change the launcher reloads, binds its items from what it has stored
 * and then regenerates each icon whose freshness no longer matches, putting the
 * new picture into the icon already on screen. That takes a few seconds for a
 * few hundred apps, so the new icons arrive one by one, in place:
 *
 * - an icon updated while the home screen is in sight grows and fades in from
 *   a little smaller as its new picture lands ([pop]);
 * - icons updated while the home screen was out of sight (the change is made
 *   in Wallpaper & style) come in as one wave, in reading order, when it
 *   returns ([wave]); any still to come pop as they land.
 *
 * The reload itself rebinds the home screen with the pictures it had stored,
 * which look exactly as before, so nothing is animated for it.
 *
 * It also tells [expect]'s caller when the new pictures have reached the home
 * screen ([LANDED_QUIET_MS] after the last of them), which is when the change is
 * really done for the person looking at it.
 *
 * ```
 * Launcher.onResume / onPause                     whether the home screen is in sight
 * BubbleTextView.setIcon(FastBitmapDrawable)      an icon's picture changed in place
 * Launcher.mWorkspace, PagedView.mCurrentPage, CellLayout.mShortcutsAndWidgets, Launcher.mHotseat
 * ```
 *
 * Only for [WINDOW_MS] after a change is asked for, so an icon updated for any
 * other reason (a badge, an app update) is left alone. Every view gets back its
 * own scale and alpha. With animations off nothing moves.
 */
internal class IconChangeReveal(private val feature: FeatureContext) {

    private val main = Handler(Looper.getMainLooper())
    private var launcher: WeakReference<Any>? = null
    private var resumed = false

    /** Until when icon updates belong to a change; 0 when none is under way. */
    private var until = 0L

    /** Icons changed while the home screen was out of sight, to be waved in when it returns. */
    private var changedUnseen = false
    private var waving = false

    /** Each popping icon's own scale and alpha, so a second update mid-pop animates back to them, not to a mid value. */
    private val bases = WeakHashMap<View, FloatArray>()
    private var onLanded: (() -> Unit)? = null
    private val landed = Runnable {
        // Out of sight, the launcher holds its redraw until home returns; the
        // change's window stays open for it rather than closing unseen.
        if (resumed) until = minOf(until, SystemClock.uptimeMillis() + LINGER_MS) else awaitingHome = true
        onLanded?.invoke()
        onLanded = null
    }

    /** A change landed while home was out of sight; its icons are redrawn, and brought in, when home returns. */
    private var awaitingHome = false

    /** When the last pop started, and how many started close behind each other, to stagger a burst into a wave. */
    private var lastPop = 0L
    private var burst = 0

    fun install() {
        val launcherClass = feature.findClass(LAUNCHER) ?: return
        feature.hookAfter(launcherClass, ON_RESUME) { activity, _ ->
            launcher = activity?.let(::WeakReference)
            resumed = true
            if (awaitingHome) {
                awaitingHome = false
                until = SystemClock.uptimeMillis() + LINGER_MS
            }
            // Not bound to the change's window: the person may browse Wallpaper &
            // style for a while before coming home to see the new icons.
            if (changedUnseen) {
                changedUnseen = false
                wave()
            }
        }
        feature.hookAfter(launcherClass, ON_PAUSE) { _, _ -> resumed = false }
        val itemsClass = feature.findClass(ITEMS_CONTAINER)
        feature.findClass(BUBBLE_TEXT_VIEW)?.let { icon ->
            feature.findClass(FAST_BITMAP_DRAWABLE)?.let { drawable ->
                feature.hookAfter(icon, SET_ICON, drawable) { view, _ ->
                    if (!active()) return@hookAfter
                    val shown = view as? View ?: return@hookAfter
                    if (!shown.isAttachedToWindow) return@hookAfter
                    // Home screen and hotseat only: the app drawer binds recycled views as it scrolls.
                    if (shown.parent?.javaClass != itemsClass) return@hookAfter
                    // Stopped, the launcher's window is hidden and nothing in it is shown,
                    // which is exactly when the change must wait for home to come back.
                    if (!resumed) changedUnseen = true else if (shown.isShown) pop(shown)
                    if (onLanded != null) {
                        main.removeCallbacks(landed)
                        main.postDelayed(landed, LANDED_QUIET_MS)
                    }
                }
            }
        }
    }

    /**
     * A change is on its way; the icons it brings are brought in as they land,
     * and [whenLanded] runs once they have reached the home screen, or at the
     * latest when the window closes.
     */
    fun expect(whenLanded: () -> Unit) = main.post {
        until = SystemClock.uptimeMillis() + WINDOW_MS
        changedUnseen = false
        awaitingHome = false
        main.removeCallbacks(landed)
        // A change overtaken by this one lands with it, and its word comes first,
        // so the last one said is this one's. Said at once, it would announce a
        // change as done while the next was still being drawn.
        val overtaken = onLanded
        onLanded = if (overtaken == null) whenLanded else { { overtaken(); whenLanded() } }
        main.postDelayed(landed, WINDOW_MS)
    }

    /**
     * The launcher has reloaded for the change. Icons it redraws now arrive
     * within moments, each pushing the landing back; with none to redraw (the
     * home screen holds no app the change touches) the change has landed.
     */
    fun reloaded() = main.post {
        if (onLanded == null) return@post
        main.removeCallbacks(landed)
        main.postDelayed(landed, LANDED_QUIET_MS)
    }

    private fun active() = SystemClock.uptimeMillis() < until

    /** One icon whose picture just changed, in sight: it grows and fades in from a little smaller. */
    private fun pop(view: View) {
        if (waving || !ValueAnimator.areAnimatorsEnabled()) return
        val base = bases.getOrPut(view) { floatArrayOf(view.scaleX.takeIf { it > 0f } ?: 1f, view.alpha.takeIf { it > 0f } ?: 1f) }
        val scale = base[0]
        val alpha = base[1]
        // Icons redrawn together (home returning to a new pack) come in one after another.
        val now = SystemClock.uptimeMillis()
        burst = if (now - lastPop < BURST_MS) burst + 1 else 0
        lastPop = now
        view.animate().cancel()
        view.scaleX = scale * FROM_SCALE
        view.scaleY = scale * FROM_SCALE
        view.alpha = alpha * FROM_ALPHA
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .alpha(alpha)
            .setStartDelay(minOf(burst * STAGGER_MS, SPREAD_MS))
            .setDuration(ICON_MS)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withEndAction {
                // The view's animator is the launcher's too; it gets it back without the delay.
                view.animate().startDelay = 0
                bases.remove(view)
            }
            .start()
    }

    /** Every icon in sight, in reading order, one after another. */
    private fun wave() {
        val activity = launcher?.get() ?: return
        val views = runCatching { icons(activity) }.getOrElse { emptyList() }
        if (views.isEmpty()) return
        if (!ValueAnimator.areAnimatorsEnabled()) return
        runCatching { animate(views) }.onFailure {
            views.forEach { view -> view.alpha = 1f }
            feature.logger.warn("Icons: the new icons could not be brought in", it)
        }
    }

    /** The icons on the page in view and in the hotseat, in reading order. */
    private fun icons(activity: Any, sorted: Boolean = true): List<View> {
        val found = ArrayList<View>()
        val workspace = Reflect.field(activity.javaClass, WORKSPACE)?.get(activity) as? ViewGroup
        val page = workspace?.let { pages ->
            val current = Reflect.field(pages.javaClass, CURRENT_PAGE)?.getInt(pages) ?: 0
            pages.getChildAt(current)
        }
        page?.let { collect(it, found) }
        (Reflect.field(activity.javaClass, HOTSEAT)?.get(activity) as? View)?.let { collect(it, found) }
        if (!sorted) return found
        val at = IntArray(2)
        return found.sortedWith(
            compareBy<View>({ it.getLocationOnScreen(at); at[1] / ROW_SLOP }, { it.getLocationOnScreen(at); at[0] }),
        )
    }

    private fun collect(cellLayout: View, into: MutableList<View>) {
        val items = Reflect.field(cellLayout.javaClass, SHORTCUTS)?.get(cellLayout) as? ViewGroup ?: return
        for (index in 0 until items.childCount) {
            val child = items.getChildAt(index)
            if (child.visibility == View.VISIBLE && child !is AppWidgetHostView) into += child
        }
    }

    private fun animate(views: List<View>) {
        views.forEach { it.animate().cancel() }
        val scales = FloatArray(views.size) { bases.remove(views[it])?.get(0) ?: views[it].scaleX.takeIf { scale -> scale > 0f } ?: 1f }
        val alphas = FloatArray(views.size) { bases.remove(views[it])?.get(1) ?: views[it].alpha.takeIf { alpha -> alpha > 0f } ?: 1f }
        val stagger = minOf(STAGGER_MS, SPREAD_MS / views.size)
        val total = ICON_MS + stagger * (views.size - 1)

        fun place(elapsed: Float) {
            for (index in views.indices) {
                val progress = ((elapsed - index * stagger) / ICON_MS).coerceIn(0f, 1f)
                val eased = EMPHASIZED_DECELERATE.getInterpolation(progress)
                val scale = scales[index] * (FROM_SCALE + (1f - FROM_SCALE) * eased)
                views[index].scaleX = scale
                views[index].scaleY = scale
                views[index].alpha = alphas[index] * eased
            }
        }

        waving = true
        place(0f)
        ValueAnimator.ofFloat(0f, total.toFloat()).apply {
            duration = total
            interpolator = null
            addUpdateListener { place(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    waving = false
                    for (index in views.indices) {
                        views[index].scaleX = scales[index]
                        views[index].scaleY = scales[index]
                        views[index].alpha = alphas[index]
                    }
                }
            })
            start()
        }
    }

    private companion object {
        const val LAUNCHER = "com.android.launcher3.Launcher"
        const val BUBBLE_TEXT_VIEW = "com.android.launcher3.BubbleTextView"
        const val FAST_BITMAP_DRAWABLE = "com.android.launcher3.icons.FastBitmapDrawable"
        const val ITEMS_CONTAINER = "com.android.launcher3.ShortcutAndWidgetContainer"
        const val ON_RESUME = "onResume"
        const val ON_PAUSE = "onPause"
        const val SET_ICON = "setIcon"
        const val WORKSPACE = "mWorkspace"
        const val HOTSEAT = "mHotseat"
        const val SHORTCUTS = "mShortcutsAndWidgets"
        const val CURRENT_PAGE = "mCurrentPage"

        /** How long after a change is asked for its icons may still be arriving. */
        const val WINDOW_MS = 20_000L

        /** The home screen's new pictures arrive together; this long without another means they have all landed. */
        const val LANDED_QUIET_MS = 700L

        /** How long after landing a straggler still pops. */
        const val LINGER_MS = 3_000L

        /** Each icon's own grow and fade, and the gap before the next one starts. */
        const val ICON_MS = 400L
        const val STAGGER_MS = 24L

        /** The whole wave never takes longer than this beyond one icon's own time. */
        const val SPREAD_MS = 400L
        const val FROM_SCALE = 0.8f

        /** Pops started within this of each other are one burst. */
        const val BURST_MS = 50L
        const val FROM_ALPHA = 0.3f

        /** Icons within this many pixels of each other vertically are one row. */
        const val ROW_SLOP = 48
        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    }
}
