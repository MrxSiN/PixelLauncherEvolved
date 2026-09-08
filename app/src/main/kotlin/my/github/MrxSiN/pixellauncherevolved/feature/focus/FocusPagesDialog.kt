package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.app.AlertDialog
import android.content.Context
import android.content.res.Resources
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

import kotlin.math.min
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.focus.FocusMode
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages
import my.github.MrxSiN.pixellauncherevolved.focus.FocusSource
import my.github.MrxSiN.pixellauncherevolved.focus.FocusStore

/**
 * Asks which pages belong to which Mode, from the launcher's Home settings.
 *
 * This was going to be a row in the menu a long press opens, which is where the
 * page being assigned would already be on screen. That menu turned out to be a
 * Compose dialog in classes the launcher's shrinker renames every release, and
 * a Compose screen has no list of child views to add a row to. This dialog uses
 * the launcher's unfiltered page order and model snapshot to show the same
 * pages as selectable miniature home screens.
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
        if (!snapshot.isReadable) {
            message(context, resources.getString(R.string.feature_focus_needs_access))
            return
        }

        val modes = snapshot.modes
        if (modes.isEmpty()) {
            message(context, resources.getString(R.string.feature_focus_no_modes))
            return
        }

        if (FocusPages.order.isEmpty()) {
            message(context, resources.getString(R.string.feature_focus_no_pages))
            return
        }

        val pages = FocusPages.order
        val previews = previewSource.pages(pages)
        val assignments = store.assignments()
        val adapter = FocusModeAdapter(
            context = context,
            modes = modes,
            assignments = assignments,
            previews = previews,
            pageLabel = { number -> resources.getString(R.string.feature_focus_page, number) },
            summary = { screens -> summary(resources, screens) },
            activeLabel = resources.getString(R.string.feature_focus_active),
        )
        val list = ListView(context).apply {
            divider = null
            isVerticalScrollBarEnabled = false
            this.adapter = adapter
        }
        val content = column(
            context,
            resources.getString(R.string.feature_focus_choose_mode_summary),
            list,
            min(modes.size * MODE_ROW_DP, MAX_MODE_LIST_DP),
        )
        val dialog = AlertDialog.Builder(context)
            .setTitle(resources.getString(R.string.feature_focus_pages_title))
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        list.setOnItemClickListener { _, _, position, _ ->
            val mode = modes.getOrNull(position) ?: return@setOnItemClickListener
            dialog.dismiss()
            choosePages(context, resources, store, mode, previews)
        }
    }

    /**
     * Ticks the pages this Mode should show.
     *
     * A page belongs to one Mode at a time, so anything ticked here is taken off
     * whichever other Mode had it. Ticking nothing gives the Mode's pages back
     * to the ordinary home screen, which is how an assignment is undone.
     */
    private fun choosePages(
        context: Context,
        resources: Resources,
        store: FocusStore,
        mode: FocusMode,
        previews: Map<Int, FocusPagePreview>,
    ) {
        val pages = FocusPages.order
        val owned = store.assignments()[mode.id].orEmpty()
        val previewWidth = dp(context, PREVIEW_WIDTH_DP)
        val adapter = FocusPageAdapter(
            context = context,
            pages = pages,
            previews = previews,
            selected = owned,
            pageLabel = { number -> resources.getString(R.string.feature_focus_page, number) },
        )
        val grid = GridView(context).apply {
            numColumns = 2
            // A stretched column is wider than the width the row height is
            // measured for, which squashes every preview by that difference.
            columnWidth = previewWidth
            stretchMode = GridView.NO_STRETCH
            gravity = Gravity.CENTER
            horizontalSpacing = dp(context, PAGE_GAP_DP)
            verticalSpacing = dp(context, PAGE_GAP_DP)
            setPadding(dp(context, 12), 0, dp(context, 12), dp(context, 8))
            clipToPadding = false
            this.adapter = adapter
        }
        val rows = (pages.size + 1) / 2
        val rowHeightDp = pages.mapNotNull(previews::get).maxOfOrNull { preview ->
            FocusPagePreviewView.heightForWidth(context, preview, previewWidth, compact = false)
        }?.let { height -> (height / context.resources.displayMetrics.density).roundToInt() + PAGE_GAP_DP }
            ?: DEFAULT_PAGE_ROW_DP
        val content = column(
            context,
            resources.getString(R.string.feature_focus_choose_pages_summary),
            grid,
            min(rows * rowHeightDp, MAX_PAGE_GRID_DP),
        )

        AlertDialog.Builder(context)
            .setTitle(mode.name)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                apply(store, mode, adapter.selectedScreens())
            }
            .setNegativeButton(android.R.string.cancel, null)
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

    private fun message(context: Context, text: String) {
        AlertDialog.Builder(context)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun column(context: Context, message: String, content: android.view.View, heightDp: Int) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(context, 24)
            setPadding(padding, dp(context, 8), padding, 0)
            addView(TextView(context).apply {
                text = message
                alpha = 0.72f
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(
                content,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp)).apply {
                    topMargin = dp(context, 8)
                },
            )
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private const val MODE_ROW_DP = 108
    private const val MAX_MODE_LIST_DP = 460
    private const val PREVIEW_WIDTH_DP = 148
    private const val DEFAULT_PAGE_ROW_DP = 360
    private const val MAX_PAGE_GRID_DP = 600
    private const val PAGE_GAP_DP = 8
}
