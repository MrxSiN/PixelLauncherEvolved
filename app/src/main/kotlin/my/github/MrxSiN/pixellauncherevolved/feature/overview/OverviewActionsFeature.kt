package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

import java.util.Collections
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
 * The recompute is not the only thing that writes these buttons. On Android 17
 * `CP3A.260905.009` the Pixel subclass, `NexusOverviewActionsView`, shows or
 * hides Select itself from a shrinker-named method that each task card's
 * overlay calls as it binds, which lands after the recompute and puts the
 * button back. Rather than chase a name the shrinker picks, the row is checked
 * again before it draws: whoever last set a button's visibility, a hidden one
 * is hidden before the frame shows it, and that frame is skipped so it never
 * flickers.
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

    /** Resource ids by button, resolved on first use. */
    private val resolvedIds = HashMap<ActionButton, Int>()

    /** Rows already checked before they draw, so each is watched once. */
    private val watchedRows: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

    override fun isEnabled(settings: SettingsSource): Boolean = hiddenButtons(settings).isNotEmpty()

    override fun install(context: FeatureContext) {
        val actionsView = OverviewActionsRow.find(context)
        if (actionsView == null) {
            context.logger.warn("Overview action row is not available in this launcher")
            return
        }

        val apply: (Any?, List<Any?>) -> Unit = { row, _ ->
            val group = row as ViewGroup
            apply(group, hiddenButtons(context.settings))
            watchBeforeDraw(group, context.settings)
        }

        OverviewActionsRow.onRecomputed(context, actionsView, apply)
    }

    /**
     * Applies the settings again before each frame the row is on screen for.
     *
     * The observer belongs to the launcher's whole window, where the row stays
     * attached even with Overview closed, so a row that is not shown costs one
     * check and nothing more. A frame in which a button had to change is
     * cancelled, and the next one lays the row out without it.
     */
    private fun watchBeforeDraw(row: ViewGroup, settings: SettingsSource) {
        if (!watchedRows.add(row)) return

        row.viewTreeObserver.addOnPreDrawListener(
            ViewTreeObserver.OnPreDrawListener {
                !row.isShown || !apply(row, hiddenButtons(settings))
            },
        )
    }

    private fun hiddenButtons(settings: SettingsSource): List<ActionButton> = buildList {
        if (settings[Settings.OVERVIEW_HIDE_SCREENSHOT]) add(SCREENSHOT)
        if (settings[Settings.OVERVIEW_HIDE_SELECT]) add(SELECT)
    }

    /** Hides and restores the row's buttons, answering whether any of them changed. */
    private fun apply(actionsRow: ViewGroup, hidden: List<ActionButton>): Boolean {
        val hiddenIds = hidden.mapNotNullTo(HashSet()) { idOf(actionsRow, it) }
        val knownIds = ALL.mapNotNullTo(HashSet()) { idOf(actionsRow, it) }

        var changed = false
        walk(actionsRow) { view ->
            if (view.id !in knownIds) return@walk false

            if (view.id in hiddenIds) {
                if (view.visibility != View.GONE) {
                    originalVisibility.putIfAbsent(view, view.visibility)
                    view.visibility = View.GONE
                    changed = true
                }
            } else {
                originalVisibility.remove(view)?.let {
                    changed = changed || view.visibility != it
                    view.visibility = it
                }
            }

            true
        }
        return changed
    }

    /**
     * A button's resource id, or null when this launcher has none.
     *
     * Looked up by name once and remembered, because the row is checked before
     * every frame it draws and a resource id does not change within a process.
     */
    private fun idOf(row: View, button: ActionButton): Int? =
        resolvedIds.getOrPut(button) { LauncherResources(row.context).id(button.idName) }
            .takeIf { it != View.NO_ID && it != 0 }

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
