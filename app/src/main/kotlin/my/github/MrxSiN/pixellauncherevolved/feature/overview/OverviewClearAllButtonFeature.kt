package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/**
 * Adds a Clear all button to the Overview action row.
 *
 * The stock Android 17 row offers only Screenshot and Select; clearing every
 * task is a gesture with no button. This adds one beside them.
 *
 * The button carries no style of its own. Those in the row have none either:
 * their appearance comes from the theme the action row is inflated with, so a
 * plain `Button` built from the row's own context matches them and keeps
 * matching when the theme changes.
 */
class OverviewClearAllButtonFeature : ToggleFeature(Settings.OVERVIEW_CLEAR_ALL_IN_ACTIONS) {

    override val compatibility = CompatibilityFeature.OVERVIEW_ACTIONS

    override fun install(context: FeatureContext) {
        val actionsView = OverviewActionsRow.find(context)
        if (actionsView == null) {
            context.logger.warn("Overview action row is not available in this launcher")
            return
        }

        val action = ClearAllAction(context.classLoader, context.logger)

        val apply: (Any?, List<Any?>) -> Unit = { view, _ ->
            // Read here rather than at install time so the toggle reaches a
            // running launcher on its next recompute.
            apply(view as ViewGroup, context.settings[toggle], action)
        }

        OverviewActionsRow.onRecomputed(context, actionsView, apply)
    }

    private fun apply(actionsView: ViewGroup, wanted: Boolean, action: ClearAllAction) {
        val resources = LauncherResources(actionsView.context)
        val row = actionsView.findViewById<ViewGroup>(resources.id(BUTTON_ROW_ID)) ?: return
        val existing = row.findViewWithTag<View>(VIEW_TAG)

        if (!wanted) {
            existing?.let(row::removeView)
            return
        }

        if (existing == null) row.addView(button(row, resources, action))
    }

    private fun button(row: ViewGroup, resources: LauncherResources, action: ClearAllAction): View =
        Button(row.context).apply {
            tag = VIEW_TAG
            text = resources.string(LABEL, "Clear all")
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                resources.drawable(ICON),
                null,
                null,
                null,
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = resources.dimensionPixels(BUTTON_SPACING, 0)
            }
            setOnClickListener { clicked -> action.run(clicked) }
        }

    companion object {
        /** Marks the injected button so other features can leave it alone. */
        const val VIEW_TAG: String = "pixellauncherevolved:clear_all_button"

        private const val BUTTON_ROW_ID = "action_buttons"
        private const val BUTTON_SPACING = "overview_actions_button_spacing"
        private const val ICON = "ic_remove_task_option"
        private const val LABEL = "recents_clear_all"
    }
}
