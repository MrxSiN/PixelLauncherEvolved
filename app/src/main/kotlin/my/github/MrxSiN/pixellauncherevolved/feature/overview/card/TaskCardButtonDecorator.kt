package my.github.MrxSiN.pixellauncherevolved.feature.overview.card

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.overview.LauncherResources
import my.github.MrxSiN.pixellauncherevolved.feature.overview.TaskViewGeometry

import java.util.WeakHashMap

/**
 * Owns one injected button on every Overview task card.
 *
 * A card is a `FrameLayout`, so a button is added as its last child and then
 * positioned by hand against the thumbnail rectangle. Doing the placement
 * explicitly keeps a button pinned to the visible snapshot corner in every
 * Overview layout: grid, carousel, and split.
 *
 * Which bottom corner a button takes is asked for on every layout rather than
 * fixed, so a button that moves aside for another when a setting is switched
 * moves on the next frame rather than on the next launcher start.
 *
 * A button is only ever as visible as the card's own app chip. The launcher
 * brings that chip up as Overview arrives and takes it away again as a card
 * grows back into an app, over animations of its own — so a button put up the
 * moment its card was laid out was there before the card had finished becoming
 * one. Following the chip rather than timing anything means the two arrive on
 * the same frame at whatever speed the gesture that opened Overview ran, and a
 * launcher that changes how it brings the chip in changes this with it.
 */
class TaskCardButtonDecorator(
    private val isEnabled: () -> Boolean,
    /** Whether this particular card can do the thing the button does. */
    private val isAvailable: (ViewGroup) -> Boolean,
    private val corner: () -> TaskCardCorner,
    private val factory: TaskCardButtonFactory,
    private val geometry: TaskViewGeometry,
    private val onClick: (ViewGroup) -> Unit,
    private val logger: Logger,
) {

    private val thumbnailBounds = Rect()

    /** How far each card has grown back into its app, by card. */
    private val fullscreen = WeakHashMap<ViewGroup, Float>()

    /** Adds the button as soon as the card finishes inflating. */
    fun onTaskViewInflated(taskView: ViewGroup) = attach(taskView)

    /**
     * Re-anchors the button, and hides it when the feature is switched off or
     * the card has nothing to act on.
     *
     * The setting is read here rather than at install time so that turning the
     * feature on or off reaches a running launcher on the next frame.
     */
    fun onTaskViewLaidOut(taskView: ViewGroup) {
        if (!isEnabled()) {
            findButton(taskView)?.visibility = View.GONE
            return
        }

        val button = findButton(taskView)
        if (button == null) {
            // Adding a child during layout is not allowed; retry next frame.
            taskView.post { attach(taskView) }
            return
        }

        if (!isAvailable(taskView)) {
            button.visibility = View.GONE
            return
        }

        button.visibility = View.VISIBLE
        place(taskView, button)
        follow(taskView, button)
    }

    /** Fades the button out while a card grows into a full screen app. */
    fun onFullscreenProgress(taskView: ViewGroup, progress: Float) {
        fullscreen[taskView] = progress.coerceIn(0f, 1f)
        findButton(taskView)?.let { follow(taskView, it) }
    }

    /**
     * Gives the button the opacity the card's app chip has.
     *
     * The chip's own opacity is the launcher's account of how far Overview has
     * arrived: it is written through several properties at once and composed
     * onto the view, so reading it off the view is the one place that sees the
     * result of all of them. A card with no chip — one the launcher draws
     * differently, or one still being built — leaves the button as opaque as
     * the card itself is.
     */
    private fun follow(taskView: ViewGroup, button: View) {
        val chip = chipOf(taskView)
        val shown = if (chip == null) 1f else chip.alpha.coerceIn(0f, 1f)

        button.alpha = shown * (1f - (fullscreen[taskView] ?: 0f))
    }

    private fun chipOf(taskView: ViewGroup): View? {
        val id = LauncherResources(taskView.context).id(CHIP_ID)

        return if (id == 0) null else taskView.findViewById(id)
    }

    private fun attach(taskView: ViewGroup) {
        if (!isEnabled() || findButton(taskView) != null) return

        try {
            val button = factory.create(taskView.context) { onClick(taskView) }
            // Nothing has laid it out yet, and a button at full opacity for the
            // frame before the chip is asked about is the flash this avoids.
            button.alpha = 0f
            taskView.addView(button)
            watch(taskView)
        } catch (error: Throwable) {
            logger.warn("Unable to add the ${factory.tag} button to an Overview card", error)
        }
    }

    /**
     * Keeps the button with the chip for as long as the card exists.
     *
     * The chip's opacity moves every frame of the Overview transition and no
     * layout accompanies it, so a draw listener is the only thing that sees all
     * of it. It costs one float read per card per frame, and only while the card
     * is part of the window.
     */
    private fun watch(taskView: ViewGroup) {
        val watcher = object : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
            override fun onPreDraw(): Boolean {
                findButton(taskView)?.let { if (it.visibility == View.VISIBLE) follow(taskView, it) }

                return true
            }

            override fun onViewAttachedToWindow(view: View) {
                // A re-attach gives the card a different observer to register
                // with; removing first keeps one listener rather than two.
                view.viewTreeObserver.removeOnPreDrawListener(this)
                view.viewTreeObserver.addOnPreDrawListener(this)
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        }

        taskView.viewTreeObserver.addOnPreDrawListener(watcher)
        taskView.addOnAttachStateChangeListener(watcher)
    }

    private fun place(taskView: ViewGroup, button: View) {
        geometry.thumbnailBounds(taskView, thumbnailBounds)

        val width = button.layoutParams.width
        val height = button.layoutParams.height
        button.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )

        val margin = factory.marginPixels(taskView.context)
        val bottom = thumbnailBounds.bottom - margin
        val left = when (corner()) {
            TaskCardCorner.START -> thumbnailBounds.left + margin
            TaskCardCorner.END -> thumbnailBounds.right - margin - width
        }

        button.layout(left, bottom - height, left + width, bottom)
    }

    private companion object {
        /** The launcher's own app chip on a task card. */
        const val CHIP_ID = "icon"
    }

    private fun findButton(taskView: ViewGroup): View? =
        (taskView.childCount - 1 downTo 0)
            .map(taskView::getChildAt)
            .firstOrNull { it.tag == factory.tag }
}

/**
 * The bottom corner of a task card's thumbnail a button sits in.
 *
 * Named for the reading direction rather than for a side, so a right-to-left
 * layout puts a button where a person there expects it.
 */
enum class TaskCardCorner { START, END }
