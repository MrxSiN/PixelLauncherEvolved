package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup

import my.github.MrxSiN.pixellauncherevolved.R

/**
 * Draws a bound settings row the way Android 17 settings draws one.
 *
 * Recent Android turned a settings list from a flat run of rows separated by
 * dividers into a stack of cards: a run of rows shares one rounded container,
 * each row inside it is its own filled shape, and the shapes are held apart by
 * a hairline gap. Only the first and last row of a run carry the large corners,
 * which is what makes the run read as a single card rather than as a pile of
 * separate ones.
 *
 * The launcher's Home settings still draws the older flat rows, so this module
 * draws its own section the new way rather than leaving it looking a version
 * behind the settings app beside it.
 *
 * The shape and fill come from [ExpressiveShapes], which the dialogs this
 * module opens are drawn with too.
 */
object ExpressiveRowView {

    /**
     * Draws a bound row, or hands the view back the way the launcher draws it.
     *
     * Both halves matter: the settings list hands one view on to whichever row
     * needs it next, so a view drawn here once has to be undone when it is
     * reused by a row that has no placement rather than merely left alone.
     */
    fun applyTo(view: View, placement: RowPlacement?) {
        if (placement == null) {
            reset(view)
            return
        }

        original(view) ?: remember(view)
        val context = view.context

        view.background = ExpressiveShapes.card(context, placement)
        view.minimumHeight = context.dp(ROW_HEIGHT_DP)
        view.setMargins(
            start = context.dp(CARD_INSET_DP),
            end = context.dp(CARD_INSET_DP),
            top = 0,
            bottom = if (placement.endsTheCard) 0 else context.dp(ExpressiveShapes.ROW_GAP_DP),
        )
    }

    private fun reset(view: View) {
        val original = original(view) ?: return

        view.background = original.background
        view.minimumHeight = original.minHeight
        view.setMargins(original.start, original.end, original.top, original.bottom)
        view.setTag(R.id.ple_row_original, null)
    }

    /**
     * How this row was drawn before this module first touched it.
     *
     * Held on the view itself because the view outlives the row: the adapter
     * hands the same one to another preference later, and by then the row that
     * was restyled is gone and cannot say what it changed.
     */
    private fun remember(view: View): OriginalRow {
        val margins = view.layoutParams as? ViewGroup.MarginLayoutParams

        return OriginalRow(
            background = view.background,
            start = margins?.marginStart ?: 0,
            end = margins?.marginEnd ?: 0,
            top = margins?.topMargin ?: 0,
            bottom = margins?.bottomMargin ?: 0,
            minHeight = view.minimumHeight,
        ).also { view.setTag(R.id.ple_row_original, it) }
    }

    private fun original(view: View): OriginalRow? =
        view.getTag(R.id.ple_row_original) as? OriginalRow

    private fun View.setMargins(start: Int, end: Int, top: Int, bottom: Int) {
        val margins = layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (margins.marginStart == start && margins.marginEnd == end &&
            margins.topMargin == top && margins.bottomMargin == bottom
        ) {
            return
        }

        margins.marginStart = start
        margins.marginEnd = end
        margins.topMargin = top
        margins.bottomMargin = bottom
        layoutParams = margins
    }

    private fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private val RowPlacement.endsTheCard: Boolean
        get() = this == RowPlacement.BOTTOM || this == RowPlacement.SINGLE

    /** Android 17 settings: wide rows inset from the screen edge. */
    private const val CARD_INSET_DP = 16f
    private const val ROW_HEIGHT_DP = 72f
}

/** How a row was drawn before this module restyled it. */
private class OriginalRow(
    val background: Drawable?,
    val start: Int,
    val end: Int,
    val top: Int,
    val bottom: Int,
    val minHeight: Int,
)
