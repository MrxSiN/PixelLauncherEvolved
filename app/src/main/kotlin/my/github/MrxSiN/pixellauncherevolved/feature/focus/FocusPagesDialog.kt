package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.Resources

import java.util.concurrent.Executor
import java.util.concurrent.Executors

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.focus.FocusFailure
import my.github.MrxSiN.pixellauncherevolved.focus.FocusMode
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages
import my.github.MrxSiN.pixellauncherevolved.focus.FocusSnapshot
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

    private val reader: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ple-focus-modes").apply { isDaemon = true }
    }

    /** Main thread only. */
    private var reading = false

    /**
     * Runs [read] off the main thread, then [then] on it, unless the screen
     * that asked has gone. The Modes are read from this module's app, which
     * may ask a root shell, so the launcher's main thread never waits on it.
     * A second tap while one read is out is dropped, as the dialog it opens
     * would have covered it.
     */
    fun <T> readThen(context: Context, read: () -> T, then: (T) -> Unit) {
        if (reading) return
        reading = true
        reader.execute {
            val value = read()
            context.mainExecutor.execute {
                reading = false
                val activity = context as? Activity
                if (activity == null || !(activity.isFinishing || activity.isDestroyed)) then(value)
            }
        }
    }

    fun show(
        context: Context,
        resources: Resources,
        store: FocusStore,
        source: FocusSource,
        previewSource: FocusPagePreviewSource,
    ) {
        readThen(context, source::snapshot) { snapshot -> show(context, resources, store, snapshot, previewSource) }
    }

    private fun show(
        context: Context,
        resources: Resources,
        store: FocusStore,
        snapshot: FocusSnapshot,
        previewSource: FocusPagePreviewSource,
    ) {
        store.forgetModesMissingFrom(snapshot)
        // A Mode switched off cannot come on, so it is not offered pages.
        val modes = snapshot.modes.filter(FocusMode::isEnabled)
        val problem = when {
            !snapshot.isReadable && snapshot.failure == FocusFailure.REFUSED -> R.string.feature_focus_needs_access
            !snapshot.isReadable -> R.string.feature_focus_read_failed
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

        val previews = previewSource.pages(FocusPages.order, store.assignments())
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
                val chosen = grid.selectedScreens()
                if (leavesOrdinaryPage(FocusPages.order, store.assignments(), mode.id, chosen)) {
                    apply(store, mode, chosen)
                } else {
                    ExpressiveDialog(context)
                        .title(mode.name)
                        .message(resources.getString(R.string.feature_focus_keep_one_page))
                        .dismiss(context.getString(android.R.string.ok))
                        .show()
                }
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

/**
 * Whether giving [chosen] to [modeId] still leaves a page that is no Mode's,
 * for the home screen to show while no Mode is on.
 */
internal fun leavesOrdinaryPage(
    pages: List<Int>,
    assignments: Map<String, Set<Int>>,
    modeId: String,
    chosen: Set<Int>,
): Boolean {
    val reserved = assignments.filterKeys { it != modeId }.values.flatten().toSet() + chosen
    return pages.any { it !in reserved }
}
