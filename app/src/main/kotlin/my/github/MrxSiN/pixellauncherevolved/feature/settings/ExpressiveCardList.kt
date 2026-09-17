package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

import kotlin.math.roundToInt

/**
 * A run of tappable rows drawn as one grouped card, as Android 17 settings lists
 * draw them inside a dialog.
 *
 * The rows bring their own content; this gives each one its place in the card,
 * the gap between them and the tap.
 */
object ExpressiveCardList {

    fun build(context: Context, rows: List<View>, onClick: (index: Int) -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val inset = context.dp(CARD_INSET_DP)
            setPadding(inset, 0, inset, 0)
            rows.forEachIndexed { index, row ->
                row.background = ExpressiveShapes.card(context, ExpressiveGrouping.placementOf(index, rows.size))
                row.minimumHeight = context.dp(ROW_HEIGHT_DP)
                row.isClickable = true
                row.isFocusable = true
                row.setOnClickListener { onClick(index) }
                addView(
                    row,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { if (index < rows.lastIndex) bottomMargin = context.dp(ExpressiveShapes.ROW_GAP_DP) },
                )
            }
        }

    private fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private const val CARD_INSET_DP = 16f
    private const val ROW_HEIGHT_DP = 72f
}
