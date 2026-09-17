package my.github.MrxSiN.pixellauncherevolved.feature.pages

import android.content.Context
import android.content.res.Resources
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusModeIcons
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPagePictures
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPagePreview
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPagePreviewSource
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPagePreviewView
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressivePage
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveType
import my.github.MrxSiN.pixellauncherevolved.focus.FocusMode
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages
import my.github.MrxSiN.pixellauncherevolved.focus.FocusStore
import my.github.MrxSiN.pixellauncherevolved.focus.renumber

/**
 * Every home screen page in one screen, arranged by dragging.
 *
 * Pages a Mode hides are shown too, marked with that Mode, because they are
 * pages of the same home screen and move with the rest. Nothing is written
 * while pages are being moved: the order is saved once, when the screen is left,
 * so a page dragged about and put back costs nothing.
 */
internal class ReorganizePagesScreen(
    private val context: Context,
    private val resources: Resources,
    private val focus: FocusStore,
    private val logger: Logger,
) {

    /**
     * @param modes the device's Modes, or empty when they are not read; only
     * used to name the Mode a page belongs to.
     */
    fun show(previewSource: FocusPagePreviewSource, modes: List<FocusMode>) {
        val pages = FocusPages.order
        if (pages.isEmpty()) {
            ExpressiveDialog(context)
                .title(resources.getString(R.string.pages_reorganize_title))
                .message(resources.getString(R.string.feature_focus_no_pages))
                .dismiss(context.getString(android.R.string.ok))
                .show()
            return
        }

        val previews = previewSource.pages(pages)
        val owners = ownersOf(modes)
        val order = pages.toMutableList()
        val labels = mutableMapOf<Int, TextView>()

        val grid = PageReorderGrid(
            context,
            PageReorderGrid.MoveLabels(
                earlier = resources.getString(R.string.pages_reorganize_move_earlier),
                later = resources.getString(R.string.pages_reorganize_move_later),
            ),
        ) { from, to ->
            order.add(to, order.removeAt(from))
            renumberLabels(order, labels)
        }.apply {
            val edge = dp(EDGE_DP)
            setPadding(edge, 0, edge, 0)
            setTiles(pages.map { screen -> tile(screen, previews.getValue(screen), owners[screen], labels) })
        }
        renumberLabels(order, labels)

        ExpressivePage(context)
            .title(resources.getString(R.string.pages_reorganize_title))
            .description(resources.getString(R.string.pages_reorganize_description))
            .backLabel(resources.getString(R.string.action_navigate_up))
            .content(grid)
            .onClose { save(pages, order) }
            .show()
    }

    private fun save(current: List<Int>, wanted: List<Int>) {
        val mapping = runCatching { PageOrder.renumbering(current, wanted) }
            .onFailure { logger.warn("The page order could not be worked out", it) }
            .getOrNull()
        if (mapping.isNullOrEmpty()) return

        LauncherPageRenumberer(context, logger).renumber(mapping) {
            // Everything kept by page id follows before the launcher binds the
            // new ids, so no Mode loses a page and no page loses its picture.
            focus.renumber(mapping)
            FocusPages.renumber(mapping)
            FocusPagePictures.renumber(mapping)
        }
    }

    /** The Mode each assigned page belongs to. */
    private fun ownersOf(modes: List<FocusMode>): Map<Int, FocusMode> {
        val byId = modes.associateBy(FocusMode::id)
        return focus.assignments()
            .flatMap { (mode, screens) -> byId[mode]?.let { owner -> screens.map { it to owner } }.orEmpty() }
            .toMap()
    }

    private fun tile(screen: Int, preview: FocusPagePreview, owner: FocusMode?, labels: MutableMap<Int, TextView>): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true

            addView(
                FocusPagePreviewView(context, preview, FocusPagePreviewView.Look.TILE),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(TextView(context).apply {
                ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
                gravity = Gravity.CENTER
                labels[screen] = this
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8f) })
            owner?.let { addView(ownerChip(it)) }
        }

    /** The Mode a page belongs to, with the icon Settings shows for it. */
    private fun ownerChip(mode: FocusMode) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        FocusModeIcons.load(context, mode.icon)?.let { icon ->
            addView(ImageView(context).apply {
                setImageDrawable(icon.mutate().apply { setTint(ExpressiveRole.ON_SURFACE_VARIANT.of(context)) })
            }, LinearLayout.LayoutParams(dp(14f), dp(14f)).apply { marginEnd = dp(4f) })
        }
        addView(TextView(context).apply {
            text = mode.name
            ExpressiveType.LABEL_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
    }

    /** Pages are named by where they now stand, so a moved page is renamed as it moves. */
    private fun renumberLabels(order: List<Int>, labels: Map<Int, TextView>) {
        order.forEachIndexed { index, screen ->
            val label = resources.getString(R.string.feature_focus_page, index + 1)
            labels[screen]?.text = label
        }
    }

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val EDGE_DP = 24f
    }
}
