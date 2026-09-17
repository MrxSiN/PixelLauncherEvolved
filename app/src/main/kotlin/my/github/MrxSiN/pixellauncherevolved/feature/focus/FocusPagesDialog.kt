package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.app.Dialog
import android.content.Context
import android.content.res.Resources

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.focus.FocusMode
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages
import my.github.MrxSiN.pixellauncherevolved.focus.FocusSource
import my.github.MrxSiN.pixellauncherevolved.focus.FocusStore
import my.github.MrxSiN.pixellauncherevolved.focus.forgetModesMissingFrom

/**
 * Asks which pages belong to which Mode, from the launcher's Home settings.
 *
 * This was going to be a row in the menu a long press opens, which is where the
 * page being assigned would already be on screen. That menu turned out to be a
 * Compose dialog in classes the launcher's shrinker renames every release, and
 * a Compose screen has no list of child views to add a row to. This dialog uses
 * the launcher's unfiltered page order and model snapshot to show the same
 * pages as selectable miniature home screens.
 *
 * Both steps are Material 3 Expressive dialogs: the Modes as the grouped card
 * Settings → Modes draws, then the chosen Mode's pages as a gallery.
 */
internal object FocusPagesDialog {

    fun show(
        context: Context,
        resources: Resources,
        store: FocusStore,
        source: FocusSource,
        previewSource: FocusPagePreviewSource,
    ) {
        val snapshot = source.snapshot()
        store.forgetModesMissingFrom(snapshot)
        // A Mode switched off cannot come on, so it is not offered pages.
        val modes = snapshot.modes.filter(FocusMode::isEnabled)
        val problem = when {
            !snapshot.isReadable -> R.string.feature_focus_needs_access
            modes.isEmpty() -> R.string.feature_focus_no_modes
            FocusPages.order.isEmpty() -> R.string.feature_focus_no_pages
            else -> null
        }
        if (problem != null) {
            ExpressiveDialog(context)
                .title(resources.getString(R.string.feature_focus_pages_title))
                .message(resources.getString(problem))
                .dismiss(context.getString(android.R.string.ok))
                .show()
            return
        }

        val previews = previewSource.pages(FocusPages.order)
        val list = FocusModeList(
            context,
            FocusModeList.Text(
                active = resources.getString(R.string.feature_focus_active),
                summary = { screens -> summary(resources, screens) },
            ),
        )
        lateinit var dialog: Dialog
        val rows = list.build(modes, store.assignments(), previews) { mode ->
            dialog.dismiss()
            choosePages(context, resources, store, mode, previews)
        }
        dialog = ExpressiveDialog(context)
            .title(resources.getString(R.string.feature_focus_pages_title))
            .message(resources.getString(R.string.feature_focus_choose_mode_summary))
            .content(rows)
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    /**
     * Ticks the pages this Mode should show.
     *
     * A page belongs to one Mode at a time, so pages owned by another Mode are
     * not offered here. Ticking nothing gives the Mode's pages back to the
     * ordinary home screen, which is how an assignment is undone.
     */
    private fun choosePages(
        context: Context,
        resources: Resources,
        store: FocusStore,
        mode: FocusMode,
        previews: Map<Int, FocusPagePreview>,
    ) {
        val assignments = store.assignments()
        val grid = FocusPageGrid(context) { screen ->
            resources.getString(R.string.feature_focus_page, requireNotNull(FocusPages.numberOf(screen)))
        }
        val gallery = grid.build(
            pages = selectablePages(FocusPages.order, assignments, mode.id),
            previews = previews,
            selected = assignments[mode.id].orEmpty(),
        )

        ExpressiveDialog(context)
            .icon(FocusModeIcons.load(context, mode.icon))
            .title(mode.name)
            .message(resources.getString(R.string.feature_focus_choose_pages_summary))
            .content(gallery)
            .dismiss(context.getString(android.R.string.cancel))
            .confirm(resources.getString(R.string.feature_focus_save)) {
                apply(store, mode, grid.selectedScreens())
            }
            .show()
    }

    /** Gives [screens] to [mode] and takes them off every other Mode. */
    private fun apply(store: FocusStore, mode: FocusMode, screens: Set<Int>) {
        for ((other, owned) in store.assignments()) {
            if (other == mode.id) continue
            val left = owned - screens
            if (left != owned) store.assign(other, left)
        }
        store.assign(mode.id, screens)
    }

    /** What a Mode's row says underneath its name. */
    private fun summary(resources: Resources, screens: Set<Int>?): String {
        val numbers = screens.orEmpty().mapNotNull(FocusPages::numberOf).sorted()
        return if (numbers.isEmpty()) {
            resources.getString(R.string.feature_focus_no_pages_assigned)
        } else {
            resources.getString(R.string.feature_focus_pages_assigned, numbers.joinToString(", "))
        }
    }
}

/** Pages not already reserved for a different Mode, in launcher order. */
internal fun selectablePages(
    pages: List<Int>,
    assignments: Map<String, Set<Int>>,
    modeId: String,
): List<Int> {
    val reserved = assignments
        .filterKeys { it != modeId }
        .values
        .flatten()
        .toSet()
    return pages.filterNot(reserved::contains)
}
