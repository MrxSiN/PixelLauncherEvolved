package my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.overview.OverviewCloser
import my.github.MrxSiN.pixellauncherevolved.feature.overview.TaskTargetResolver
import my.github.MrxSiN.pixellauncherevolved.feature.overview.TaskViewGeometry

/**
 * Owns the bubble button of a single Overview task card.
 *
 * The card is a `FrameLayout`, so the button is added as its last child and
 * then positioned by hand against the thumbnail rectangle. Doing the placement
 * explicitly keeps the button pinned to the visible snapshot corner in every
 * Overview layout: grid, carousel, and split.
 */
class OverviewBubbleDecorator(
    private val buttonFactory: BubbleButtonFactory,
    private val targetResolver: TaskTargetResolver,
    private val geometry: TaskViewGeometry,
    private val overviewCloser: OverviewCloser,
    private val bubbleLauncher: BubbleLauncher,
    private val logger: Logger,
) {

    private val thumbnailBounds = Rect()

    /** Adds the button as soon as the card finishes inflating. */
    fun onTaskViewInflated(taskView: ViewGroup) = attach(taskView)

    /** Re-anchors the button and hides it on cards with nothing to bubble. */
    fun onTaskViewLaidOut(taskView: ViewGroup) {
        val button = findButton(taskView)
        if (button == null) {
            // Adding a child during layout is not allowed; retry next frame.
            taskView.post { attach(taskView) }
            return
        }

        if (targetResolver.resolve(taskView) == null) {
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
        if (findButton(taskView) != null) return

        try {
            taskView.addView(buttonFactory.create(taskView.context) { launch(taskView) })
        } catch (error: Throwable) {
            logger.warn("Unable to add a bubble button to an Overview card", error)
        }
    }

    /**
     * Overview is put away first so the bubble settles over whatever it
     * covered; a bubble raised over a collapsing Overview would be hidden by it.
     */
    private fun launch(taskView: ViewGroup) {
        val target = targetResolver.resolve(taskView) ?: return
        val context = taskView.context

        overviewCloser.close(taskView) {
            if (bubbleLauncher.launch(context, target)) {
                logger.info("Requested bubble for ${target.intent.component}")
            }
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

        val margin = buttonFactory.marginPixels(taskView.context)
        val right = thumbnailBounds.right - margin
        val bottom = thumbnailBounds.bottom - margin
        button.layout(right - width, bottom - height, right, bottom)
    }

    private fun findButton(taskView: ViewGroup): View? =
        (taskView.childCount - 1 downTo 0)
            .map(taskView::getChildAt)
            .firstOrNull { it.tag == BubbleButtonFactory.VIEW_TAG }
}
