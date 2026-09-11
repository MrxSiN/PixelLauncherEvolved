package my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble

import android.view.View

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.overview.OverviewCloser
import my.github.MrxSiN.pixellauncherevolved.feature.overview.TaskTargetResolver

/** Reopens the app behind a task card as a floating bubble. */
class BubbleAction(
    private val targetResolver: TaskTargetResolver,
    private val overviewCloser: OverviewCloser,
    private val bubbleLauncher: BubbleLauncher,
    private val logger: Logger,
) {

    /** Whether this card carries an app there is any way to bubble. */
    fun canBubble(taskView: View): Boolean = targetResolver.resolve(taskView) != null

    /**
     * Overview is put away first so the bubble settles over whatever it
     * covered; a bubble raised over a collapsing Overview would be hidden by it.
     */
    fun run(taskView: View) {
        val target = targetResolver.resolve(taskView) ?: return
        val context = taskView.context

        overviewCloser.close(taskView) {
            if (bubbleLauncher.launch(context, target)) {
                logger.info("Requested bubble for ${target.intent.component}")
            }
        }
    }
}
