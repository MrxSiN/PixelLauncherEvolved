package my.github.MrxSiN.pixellauncherevolved.feature.overview.card

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.overview.TaskViewGeometry

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
    }

    /** Fades the button out while a card grows into a full screen app. */
    fun onFullscreenProgress(taskView: ViewGroup, progress: Float) {
        findButton(taskView)?.alpha = (1f - progress).coerceIn(0f, 1f)
    }

    private fun attach(taskView: ViewGroup) {
        if (!isEnabled() || findButton(taskView) != null) return

        try {
            taskView.addView(factory.create(taskView.context) { onClick(taskView) })
        } catch (error: Throwable) {
            logger.warn("Unable to add the ${factory.tag} button to an Overview card", error)
        }
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
