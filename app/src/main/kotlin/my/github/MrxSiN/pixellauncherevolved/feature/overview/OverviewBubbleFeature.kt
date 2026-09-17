package my.github.MrxSiN.pixellauncherevolved.feature.overview

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble.BubbleAction
import my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble.SystemUiProxyBubbleLauncher
import my.github.MrxSiN.pixellauncherevolved.feature.overview.card.TaskCardButtonDecorator
import my.github.MrxSiN.pixellauncherevolved.feature.overview.card.TaskCardButtonFactory
import my.github.MrxSiN.pixellauncherevolved.feature.overview.card.TaskCardCorner
import my.github.MrxSiN.pixellauncherevolved.feature.overview.card.decorateTaskCards
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/**
 * Adds a bubble button to every Overview task card.
 *
 * Android 17 can bubble any app, but the only entry point is a long-press on an
 * app icon. Once an app is already running, Recents is where users reach for
 * it, so the same action belongs on the task card.
 *
 * It keeps the trailing bottom corner of the thumbnail; the split screen
 * button, when that is switched on too, takes the leading one.
 */
class OverviewBubbleFeature : ToggleFeature(Settings.OVERVIEW_BUBBLE_BUTTON) {

    override val compatibility = CompatibilityFeature.BUBBLE_LAUNCHER

    override fun install(context: FeatureContext) {
        val taskView = context.findClass(TASK_VIEW_CLASS)
        if (taskView == null) {
            context.logger.warn("Overview task cards are not available in this launcher")
            return
        }

        val action = BubbleAction(
            targetResolver = TaskViewTargetResolver(context.logger),
            overviewCloser = RecentsViewOverviewCloser(context.logger),
            bubbleLauncher = SystemUiProxyBubbleLauncher(context.classLoader, context.logger),
            logger = context.logger,
        )

        val decorator = TaskCardButtonDecorator(
            // Read on every layout pass, so switching the feature on or off
            // reaches a running launcher without a restart.
            isEnabled = { context.settings[toggle] },
            isAvailable = action::canBubble,
            corner = { TaskCardCorner.END },
            factory = TaskCardButtonFactory(
                tag = VIEW_TAG,
                iconResources = listOf(ICON_RESOURCE),
                labelResource = LABEL_RESOURCE,
                fallbackLabel = "Bubble",
            ),
            geometry = TaskViewGeometry(),
            onClick = action::run,
            logger = context.logger,
        )

        context.decorateTaskCards(taskView, decorator)
    }

    private companion object {
        const val TASK_VIEW_CLASS = "com.android.quickstep.views.TaskView"
        const val VIEW_TAG = "pixellauncherevolved:bubble_button"

        const val ICON_RESOURCE = "ic_bubble_button"
        const val LABEL_RESOURCE = "bubble"
    }
}
