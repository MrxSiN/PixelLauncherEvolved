package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.view.View
import android.view.ViewGroup

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
 * Buttons are matched by resource id, which stays stable across translations.
 */
class OverviewActionsFeature : LauncherFeature {

    /** One button, as it can be recognised in the row. */
    private data class ActionButton(val idName: String)

    override val id: String = "overview_actions"

    /** Visibility each button had before this feature first hid it. */
    private val originalVisibility = WeakHashMap<View, Int>()

    override fun isEnabled(settings: SettingsSource): Boolean = hiddenButtons(settings).isNotEmpty()

    override fun install(context: FeatureContext) {
        val actionsView = OverviewActionsRow.find(context)
        if (actionsView == null) {
            context.logger.warn("Overview action row is not available in this launcher")
            return
        }

        val apply: (Any?, List<Any?>) -> Unit = { row, _ ->
            apply(row as ViewGroup, hiddenButtons(context.settings))
        }

        OverviewActionsRow.onRecomputed(context, actionsView, apply)
    }

    private fun hiddenButtons(settings: SettingsSource): List<ActionButton> = buildList {
        if (settings[Settings.OVERVIEW_HIDE_SCREENSHOT]) add(SCREENSHOT)
        if (settings[Settings.OVERVIEW_HIDE_SELECT]) add(SELECT)
    }

    private fun apply(actionsRow: ViewGroup, hidden: List<ActionButton>) {
        val resources = LauncherResources(actionsRow.context)

        val hiddenIds = hidden.map { it.idName }
            .map(resources::id)
            .filterTo(HashSet()) { it != View.NO_ID && it != 0 }

        val knownIds = ALL.map { it.idName }
            .map(resources::id)
            .filterTo(HashSet()) { it != View.NO_ID && it != 0 }

        walk(actionsRow) { view ->
            if (view.id !in knownIds) return@walk false

            if (view.id in hiddenIds) {
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
        val SCREENSHOT = ActionButton("action_screenshot")
        val SELECT = ActionButton("action_select")
        val ALL = listOf(SCREENSHOT, SELECT)
    }
}
