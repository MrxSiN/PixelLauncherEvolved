package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.apps.HideAppsSelection
import my.github.MrxSiN.pixellauncherevolved.feature.apps.SharedPreferencesHiddenAppsStore
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveType
import my.github.MrxSiN.pixellauncherevolved.focus.FocusFailure
import my.github.MrxSiN.pixellauncherevolved.focus.FocusMode
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages
import my.github.MrxSiN.pixellauncherevolved.focus.FocusSnapshot
import my.github.MrxSiN.pixellauncherevolved.focus.FocusSource
import my.github.MrxSiN.pixellauncherevolved.focus.FocusStore
import my.github.MrxSiN.pixellauncherevolved.focus.Orphan
import my.github.MrxSiN.pixellauncherevolved.focus.forgetModesMissingFrom
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

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
        // A deleted Mode's pages follow the Modes as one more row, under the name it had.
        val orphans = store.orphans()
        val orphanRows = orphans.map { (id, orphan) ->
            FocusMode(ORPHAN_ROW + id, resources.getString(R.string.feature_focus_orphan, orphan.name ?: resources.getString(R.string.feature_focus_orphan_unnamed)), false)
        }
        val owned = store.assignments() + orphans.map { (id, orphan) -> ORPHAN_ROW + id to orphan.screens }
        val rows = list.build(modes + orphanRows, owned, previews) { mode ->
            dialog.dismiss()
            val orphan = orphans[mode.id.removePrefix(ORPHAN_ROW)]
            if (mode.id.startsWith(ORPHAN_ROW) && orphan != null) {
                giveOrphan(context, resources, store, mode.id.removePrefix(ORPHAN_ROW), orphan, modes, list, previews)
            } else {
                choosePages(context, resources, store, mode, previews)
            }
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

        // Off keeps the Mode to its own pages, as before; on keeps the ordinary pages beside them.
        val withOrdinary = Switch(context).apply {
            text = resources.getString(R.string.feature_focus_with_ordinary)
            isChecked = store.withOrdinary(mode.id)
            ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
            minHeight = (48f * context.resources.displayMetrics.density).roundToInt()
            // In line with the page grid under it, which keeps the dialog's inset.
            val inset = (24f * context.resources.displayMetrics.density).roundToInt()
            setPadding(inset, 0, inset, 0)
        }
        lateinit var dialog: Dialog
        val hiddenApps = hiddenAppsRow(context, resources, mode) { dialog.dismiss() }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(withOrdinary)
            addView(hiddenApps)
            addView(gallery)
        }

        dialog = ExpressiveDialog(context)
            .icon(FocusModeIcons.load(context, mode.icon))
            .title(mode.name)
            .message(resources.getString(R.string.feature_focus_choose_pages_summary))
            .content(content)
            .dismiss(context.getString(android.R.string.cancel))
            .confirm(resources.getString(R.string.feature_focus_save)) {
                val chosen = grid.selectedScreens()
                if (leavesOrdinaryPage(FocusPages.order, store.assignments(), mode.id, chosen)) {
                    store.setWithOrdinary(mode.id, withOrdinary.isChecked)
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

    /**
     * Gives a deleted Mode's pages to a Mode, on top of the pages it has, or forgets them.
     * Meanwhile they are on the ordinary home screen, as any page no Mode has.
     */
    private fun giveOrphan(
        context: Context,
        resources: Resources,
        store: FocusStore,
        id: String,
        orphan: Orphan,
        modes: List<FocusMode>,
        list: FocusModeList,
        previews: Map<Int, FocusPagePreview>,
    ) {
        lateinit var dialog: Dialog
        val rows = list.build(modes, store.assignments(), previews) { mode ->
            dialog.dismiss()
            val chosen = store.assignments()[mode.id].orEmpty() + orphan.screens
            if (leavesOrdinaryPage(FocusPages.order, store.assignments(), mode.id, chosen)) {
                apply(store, mode, chosen)
                store.keepOrphan(id, orphan.copy(screens = emptySet()))
            } else {
                ExpressiveDialog(context)
                    .title(mode.name)
                    .message(resources.getString(R.string.feature_focus_keep_one_page))
                    .dismiss(context.getString(android.R.string.ok))
                    .show()
            }
        }
        dialog = ExpressiveDialog(context)
            .title(resources.getString(R.string.feature_focus_orphan, orphan.name ?: resources.getString(R.string.feature_focus_orphan_unnamed)))
            .message(resources.getString(R.string.feature_focus_orphan_summary))
            .content(rows)
            .confirm(resources.getString(R.string.feature_focus_orphan_forget)) { store.keepOrphan(id, orphan.copy(screens = emptySet())) }
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    /**
     * Opens the drawer to tick the apps [mode] hides on top of the everywhere
     * list, the way Hidden apps does: settings steps aside and the launcher
     * finishes the choosing ([HideAppsSelection]).
     */
    private fun hiddenAppsRow(context: Context, resources: Resources, mode: FocusMode, close: () -> Unit): TextView {
        val store = SharedPreferencesHiddenAppsStore.forMode(LauncherSettings.preferences(context), mode.id)
        val count = store.hidden().size
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = resources.getString(R.string.feature_focus_hidden_apps) + "\n" +
                if (count == 0) resources.getString(R.string.feature_hidden_apps_summary_none)
                else resources.getQuantityString(R.plurals.feature_hidden_apps_summary, count, count)
            ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
            minHeight = (48f * density).roundToInt()
            gravity = android.view.Gravity.CENTER_VERTICAL
            val inset = (24f * density).roundToInt()
            setPadding(inset, 0, inset, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                close()
                HideAppsSelection.begin(store)
                context.startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                (context as? Activity)?.finish()
            }
        }
    }

    /** Gives [screens] to [mode] and takes them off every other Mode. */
    private fun apply(store: FocusStore, mode: FocusMode, screens: Set<Int>) {
        store.rememberName(mode.id, mode.name)
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

/** Marks a row standing for a deleted Mode's pages; Mode ids never contain a colon at the start. */
private const val ORPHAN_ROW = ":orphan:"
