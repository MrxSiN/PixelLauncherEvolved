package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.view.ViewGroup

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble.BubbleButtonFactory
import my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble.OverviewBubbleDecorator
import my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble.SystemUiProxyBubbleLauncher
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/**
 * Adds a bubble button to every Overview task card.
 *
 * Android 17 can bubble any app, but the only entry point is a long-press on an
 * app icon. Once an app is already running, Recents is where users reach for
 * it, so the same action belongs on the task card.
 */
class OverviewBubbleFeature : ToggleFeature(Settings.OVERVIEW_BUBBLE_BUTTON) {

    override fun install(context: FeatureContext) {
        val taskView = context.findClass(TASK_VIEW_CLASS)
        if (taskView == null) {
            context.logger.warn("Overview task cards are not available in this launcher")
            return
        }

        val decorator = OverviewBubbleDecorator(
            buttonFactory = BubbleButtonFactory(),
            targetResolver = TaskViewTargetResolver(context.logger),
            geometry = TaskViewGeometry(),
            overviewCloser = RecentsViewOverviewCloser(context.logger),
            bubbleLauncher = SystemUiProxyBubbleLauncher(context.classLoader, context.logger),
            logger = context.logger,
        )

        context.hookAfter(taskView, "onFinishInflate") { card, _ ->
            decorator.onTaskViewInflated(card as ViewGroup)
        }

        context.hookAfter(
            taskView,
            "onLayout",
            Boolean::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
        ) { card, _ -> decorator.onTaskViewLaidOut(card as ViewGroup) }

        context.hookAfter(
            taskView,
            "setFullscreenProgress",
            Float::class.javaPrimitiveType!!,
        ) { card, args -> decorator.onFullscreenProgress(card as ViewGroup, args[0] as Float) }
    }

    private companion object {
        const val TASK_VIEW_CLASS = "com.android.quickstep.views.TaskView"
    }
}
