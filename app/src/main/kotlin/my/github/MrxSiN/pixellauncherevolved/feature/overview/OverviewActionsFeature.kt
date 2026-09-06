package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Hides individual buttons from the Overview action row.
 *
 * The launcher recomputes that row whenever the selected task changes, so the
 * hook reads the settings on every recompute and both hides and un-hides. That
 * is what lets a toggle take effect on a running launcher: switching one off
 * restores the button rather than waiting for a restart.
 *
 * Restoring puts back the visibility the button had before this feature touched
 * it, not a blanket `VISIBLE`. The launcher hides some of these buttons itself
 * depending on the selected task, and that decision has to survive.
 *
 * Buttons are matched by resource id where they have one, and otherwise by the
 * launcher's own label: this build adds Clear all to the row programmatically
 * without an id, so an id-only rule would miss it. Reading the label out of the
 * launcher's resources keeps the match correct in every language.
 */
class OverviewActionsFeature : LauncherFeature {

    /** One button, as it can be recognised in the row. */
    private data class ActionButton(val idName: String?, val labelName: String?)

    override val id: String = "overview_actions"

    /** Visibility each button had before this feature first hid it. */
    private val originalVisibility = WeakHashMap<View, Int>()

    override fun isEnabled(settings: SettingsSource): Boolean = hiddenButtons(settings).isNotEmpty()

    override fun install(context: FeatureContext) {
        val actionsView = context.findClass(ACTIONS_VIEW_CLASS)
        if (actionsView == null) {
            context.logger.warn("Overview action row is not available in this launcher")
            return
        }

        val apply: (Any?, List<Any?>) -> Unit = { row, _ ->
            apply(row as ViewGroup, hiddenButtons(context.settings))
        }

        context.hookAfter(actionsView, "onFinishInflate", after = apply)
        context.hookAfter(actionsView, "updateActionButtonsVisibility", after = apply)
    }

    private fun hiddenButtons(settings: SettingsSource): List<ActionButton> = buildList {
        if (settings[Settings.OVERVIEW_HIDE_SCREENSHOT]) add(SCREENSHOT)
        if (settings[Settings.OVERVIEW_HIDE_SELECT]) add(SELECT)
        if (settings[Settings.OVERVIEW_HIDE_CLEAR_ALL]) add(CLEAR_ALL)
    }

    private fun apply(actionsRow: ViewGroup, hidden: List<ActionButton>) {
        val resources = LauncherResources(actionsRow.context)

        val hiddenIds = hidden.mapNotNull { it.idName }
            .map(resources::id)
            .filterTo(HashSet()) { it != View.NO_ID && it != 0 }

        val hiddenLabels = hidden.mapNotNull { it.labelName }
            .mapNotNullTo(HashSet()) { resources.string(it, "").takeIf(String::isNotEmpty) }

        val knownIds = ALL.mapNotNull { it.idName }
            .map(resources::id)
            .filterTo(HashSet()) { it != View.NO_ID && it != 0 }

        val knownLabels = ALL.mapNotNull { it.labelName }
            .mapNotNullTo(HashSet()) { resources.string(it, "").takeIf(String::isNotEmpty) }

        walk(actionsRow) { view ->
            val isKnown = view.id in knownIds || (view is TextView && view.text?.toString() in knownLabels)
            if (!isKnown) return@walk false

            val shouldHide =
                view.id in hiddenIds || (view is TextView && view.text?.toString() in hiddenLabels)

            if (shouldHide) {
                originalVisibility.putIfAbsent(view, view.visibility)
                view.visibility = View.GONE
            } else {
                originalVisibility.remove(view)?.let { view.visibility = it }
            }

            true
        }
    }

    /** Visits the tree, stopping at any branch [onView] claims. */
    private fun walk(view: View, onView: (View) -> Boolean) {
        if (onView(view)) return

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) walk(view.getChildAt(index), onView)
        }
    }

    private companion object {
        const val ACTIONS_VIEW_CLASS = "com.android.quickstep.views.OverviewActionsView"

        val SCREENSHOT = ActionButton(idName = "action_screenshot", labelName = null)
        val SELECT = ActionButton(idName = "action_select", labelName = null)

        /** This build adds the clear-all button without an id, so its label is the anchor. */
        val CLEAR_ALL = ActionButton(idName = "clear_all", labelName = "recents_clear_all")

        val ALL = listOf(SCREENSHOT, SELECT, CLEAR_ALL)
    }
}
