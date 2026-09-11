package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
 * The fill comes from the platform's own Material palette — the same colours
 * the settings app is drawn from — rather than from a copy kept here, so the
 * section follows the wallpaper and the dark theme without knowing what either
 * of them currently is.
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

        view.background = card(context, placement)
        view.minimumHeight = context.dp(ROW_HEIGHT_DP)
        view.setMargins(
            start = context.dp(CARD_INSET_DP),
            end = context.dp(CARD_INSET_DP),
            top = 0,
            bottom = if (placement.endsTheCard) 0 else context.dp(ROW_GAP_DP),
        )
    }

    private fun reset(view: View) {
        val original = original(view) ?: return

        view.background = original.background
        view.minimumHeight = original.minHeight
        view.setMargins(original.start, original.end, original.top, original.bottom)
        view.setTag(R.id.ple_row_original, null)
    }

    /** One row's filled shape, rounded on the ends of the card it belongs to. */
    private fun card(context: Context, placement: RowPlacement): Drawable {
        val large = context.dp(CARD_RADIUS_DP).toFloat()
        val small = context.dp(ROW_RADIUS_DP).toFloat()

        val top = if (placement.startsTheCard) large else small
        val bottom = if (placement.endsTheCard) large else small
        // Clockwise from the top left, as the x and y radius of each corner.
        val corners = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)

        return RippleDrawable(
            ColorStateList.valueOf(context.systemColor(ON_SURFACE).withAlpha(RIPPLE_ALPHA)),
            shape(context.systemColor(CARD_FILL), corners),
            // A mask with the row's own corners keeps a press inside the shape
            // rather than letting it square off the rounded end.
            shape(Color.WHITE, corners),
        )
    }

    private fun shape(color: Int, corners: FloatArray): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadii = corners
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

    /**
     * A colour of the platform's Material palette, in the theme now in force.
     *
     * The palette is published as two sets of resources rather than as one that
     * follows the night setting, so the set is chosen here. These are framework
     * resources, so they read the same in the launcher's process as anywhere
     * else, and they already carry the wallpaper's colours.
     */
    private fun Context.systemColor(colors: SystemColor): Int {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

        return resources.getColor(if (night) colors.dark else colors.light, theme)
    }

    private fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha shl 24)

    private fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private val RowPlacement.startsTheCard: Boolean
        get() = this == RowPlacement.TOP || this == RowPlacement.SINGLE

    private val RowPlacement.endsTheCard: Boolean
        get() = this == RowPlacement.BOTTOM || this == RowPlacement.SINGLE

    /** Android 17 settings: wide rows, tight gaps, and one rounded card. */
    private const val CARD_INSET_DP = 16f
    private const val CARD_RADIUS_DP = 24f
    private const val ROW_RADIUS_DP = 8f
    private const val ROW_GAP_DP = 2f
    private const val ROW_HEIGHT_DP = 72f

    private const val RIPPLE_ALPHA = 0x1F

    /**
     * The fill a card is drawn with.
     *
     * The bright surface, which is the one the settings app fills its own cards
     * with: read off a card in Display & touch on a Pixel running Android 17,
     * it is `#2F2B27`, and so is this role in that device's palette. The
     * container roles are all darker — the plain container is the colour that
     * settings screen uses for its background — and against the launcher's
     * ground they leave a card barely distinguishable from the screen.
     */
    private val CARD_FILL = SystemColor(
        light = android.R.color.system_surface_bright_light,
        dark = android.R.color.system_surface_bright_dark,
    )
    private val ON_SURFACE = SystemColor(
        light = android.R.color.system_on_surface_light,
        dark = android.R.color.system_on_surface_dark,
    )
}

/** One Material colour role, in its light and its dark form. */
private class SystemColor(val light: Int, val dark: Int)

/** How a row was drawn before this module restyled it. */
private class OriginalRow(
    val background: Drawable?,
    val start: Int,
    val end: Int,
    val top: Int,
    val bottom: Int,
    val minHeight: Int,
)
