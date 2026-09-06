package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Hides individual buttons from the Overview action row.
 *
 * The launcher recomputes that row whenever the selected task changes, so the
 * buttons are hidden again after each recompute rather than once at inflation.
 *
 * Buttons are matched by resource id where they have one, and otherwise by the
 * launcher's own label for them: this build adds "Clear all" to the row
 * programmatically, without an id, so an id-only rule would miss it. Reading
 * the label out of the launcher's resources keeps the match correct in every
 * language.
 */
class OverviewActionsFeature : LauncherFeature {

    /** One button, as it can be recognised in the row. */
    private data class ActionButton(val idName: String?, val labelName: String?)

    override val id: String = "overview_actions"

    override fun isEnabled(settings: SettingsSource): Boolean = hiddenButtons(settings).isNotEmpty()

    override fun install(context: FeatureContext) {
        val actionsView = context.findClass(ACTIONS_VIEW_CLASS)
        if (actionsView == null) {
            context.logger.warn("Overview action row is not available in this launcher")
            return
        }

        val hidden = hiddenButtons(context.settings)
        val hide: (Any?, List<Any?>) -> Unit = { row, _ -> hide(row as ViewGroup, hidden) }

        context.hookAfter(actionsView, "onFinishInflate", after = hide)
        context.hookAfter(actionsView, "updateActionButtonsVisibility", after = hide)
    }

    private fun hiddenButtons(settings: SettingsSource): List<ActionButton> = buildList {
        if (settings[Settings.OVERVIEW_HIDE_SCREENSHOT]) add(SCREENSHOT)
        if (settings[Settings.OVERVIEW_HIDE_SELECT]) add(SELECT)
        if (settings[Settings.OVERVIEW_HIDE_CLEAR_ALL]) add(CLEAR_ALL)
    }

    private fun hide(actionsRow: ViewGroup, buttons: List<ActionButton>) {
        val resources = LauncherResources(actionsRow.context)

        val ids = buttons.mapNotNull { it.idName }
            .map(resources::id)
            .filter { it != View.NO_ID && it != 0 }
            .toSet()

        val labels = buttons.mapNotNull { it.labelName }
            .mapNotNull { resources.string(it, "").takeIf(String::isNotEmpty) }
            .toSet()

        hideMatching(actionsRow, ids, labels)
    }

    private fun hideMatching(view: View, ids: Set<Int>, labels: Set<String>) {
        if (view.id in ids || (view is TextView && view.text?.toString() in labels)) {
            view.visibility = View.GONE
            return
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                hideMatching(view.getChildAt(index), ids, labels)
            }
        }
    }

    private companion object {
        const val ACTIONS_VIEW_CLASS = "com.android.quickstep.views.OverviewActionsView"

        val SCREENSHOT = ActionButton(idName = "action_screenshot", labelName = null)
        val SELECT = ActionButton(idName = "action_select", labelName = null)

        /** This build adds the clear-all button without an id, so its label is the anchor. */
        val CLEAR_ALL = ActionButton(idName = "clear_all", labelName = "recents_clear_all")
    }
}
