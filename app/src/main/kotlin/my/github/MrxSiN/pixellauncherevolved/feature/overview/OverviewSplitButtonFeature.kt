package my.github.MrxSiN.pixellauncherevolved.feature.overview

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.overview.card.TaskCardButtonDecorator
import my.github.MrxSiN.pixellauncherevolved.feature.overview.card.TaskCardButtonFactory
import my.github.MrxSiN.pixellauncherevolved.feature.overview.card.TaskCardCorner
import my.github.MrxSiN.pixellauncherevolved.feature.overview.card.decorateTaskCards
import my.github.MrxSiN.pixellauncherevolved.feature.overview.split.SplitScreenAction
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/**
 * Adds a split screen button to every Overview task card.
 *
 * Splitting the screen from Recents is already possible, behind a long press on
 * a card and then a menu — far enough in that most people never find it. The
 * card is where an app is picked to pair with another, so the action belongs on
 * the card.
 *
 * It sits in the trailing bottom corner of the thumbnail, and moves to the
 * leading one while the bubble button is switched on, so the two are at
 * opposite ends of the card rather than crowding one corner. Which corner is
 * decided every time a card is laid out, so switching the bubble button off
 * brings this one back across on the next frame.
 */
class OverviewSplitButtonFeature : ToggleFeature(Settings.OVERVIEW_SPLIT_BUTTON) {

    override val compatibility = CompatibilityFeature.SPLIT_SCREEN

    override fun install(context: FeatureContext) {
        val taskView = context.findClass(TASK_VIEW_CLASS)
        if (taskView == null) {
            context.logger.warn("Overview task cards are not available in this launcher")
            return
        }

        val action = SplitScreenAction(context.logger)

        val decorator = TaskCardButtonDecorator(
            // Read on every layout pass, so switching the feature on or off
            // reaches a running launcher without a restart.
            isEnabled = { context.settings[toggle] },
            isAvailable = action::canSplit,
            corner = {
                if (context.settings[Settings.OVERVIEW_BUBBLE_BUTTON]) {
                    TaskCardCorner.START
                } else {
                    TaskCardCorner.END
                }
            },
            factory = TaskCardButtonFactory(
                tag = VIEW_TAG,
                iconResources = ICON_RESOURCES,
                labelResource = LABEL_RESOURCE,
                fallbackLabel = "Split screen",
            ),
            geometry = TaskViewGeometry(),
            onClick = { card -> action.run(card) },
            logger = context.logger,
        )

        context.decorateTaskCards(taskView, decorator)
    }

    private companion object {
        const val TASK_VIEW_CLASS = "com.android.quickstep.views.TaskView"
        const val VIEW_TAG = "pixellauncherevolved:split_button"

        /**
         * The launcher's own split glyphs. The horizontal one is what its card
         * menu shows on a phone; the vertical one is the tablet's, taken when a
         * build has only that.
         */
        val ICON_RESOURCES = listOf("ic_split_horizontal", "ic_split_vertical")
        const val LABEL_RESOURCE = "recent_task_option_split_screen"
    }
}
