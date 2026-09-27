package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
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

    override val compatibility = CompatibilityFeature.OVERVIEW_ACTIONS

    override val id: String = "overview_actions"

    /** Visibility each button had before this feature first hid it. */
    private val originalVisibility = WeakHashMap<View, Int>()

    /**
     * Each button's resource id, 0 when this launcher has none, or
     * [UNRESOLVED] until first asked.
     *
     * Looked up by name once and remembered, because the row is checked before
     * every frame it draws and a resource id does not change within a process.
     */
    private var screenshotId = UNRESOLVED
    private var selectId = UNRESOLVED

    /** Rows already checked before they draw, so each is watched once. */
    private val watchedRows: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

    override fun isEnabled(settings: SettingsSource): Boolean =
        settings[Settings.OVERVIEW_HIDE_SCREENSHOT] || settings[Settings.OVERVIEW_HIDE_SELECT]

    override fun install(context: FeatureContext) {
        val actionsView = OverviewActionsRow.find(context)
        if (actionsView == null) {
            context.logger.warn("Overview action row is not available in this launcher")
            return
        }

        val apply: (Any?, List<Any?>) -> Unit = { row, _ ->
            val group = row as ViewGroup
            apply(group, context.settings)
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
                !row.isShown || !apply(row, settings)
            },
        )
    }

    /** Hides and restores the row's buttons, answering whether any of them changed. */
    private fun apply(actionsRow: ViewGroup, settings: SettingsSource): Boolean {
        if (screenshotId == UNRESOLVED) screenshotId = idOf(actionsRow, SCREENSHOT_ID)
        if (selectId == UNRESOLVED) selectId = idOf(actionsRow, SELECT_ID)

        return walk(
            actionsRow,
            settings[Settings.OVERVIEW_HIDE_SCREENSHOT],
            settings[Settings.OVERVIEW_HIDE_SELECT],
            false,
        )
    }

    /** A button's resource id, or 0 when this launcher has none. */
    private fun idOf(row: View, name: String): Int =
        LauncherResources(row.context).id(name).let { if (it == View.NO_ID) 0 else it }

    /**
     * Visits the tree, stopping at either button, and answers whether any
     * button changed; [changed] is what earlier branches already answered.
     */
    private fun walk(view: View, hideScreenshot: Boolean, hideSelect: Boolean, changed: Boolean): Boolean {
        val id = view.id
        val isScreenshot = screenshotId != 0 && id == screenshotId
        if (isScreenshot || (selectId != 0 && id == selectId)) {
            if (if (isScreenshot) hideScreenshot else hideSelect) {
                if (view.visibility != View.GONE) {
                    originalVisibility.putIfAbsent(view, view.visibility)
                    view.visibility = View.GONE
                    return true
                }
            } else {
                val original = originalVisibility.remove(view)
                if (original != null) {
                    val restored = changed || view.visibility != original
                    view.visibility = original
                    return restored
                }
            }
            return changed
        }

        var result = changed
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                result = walk(view.getChildAt(index), hideScreenshot, hideSelect, result)
            }
        }
        return result
    }

    private companion object {
        const val SCREENSHOT_ID = "action_screenshot"
        const val SELECT_ID = "action_select"
        const val UNRESOLVED = Int.MIN_VALUE
    }
}
