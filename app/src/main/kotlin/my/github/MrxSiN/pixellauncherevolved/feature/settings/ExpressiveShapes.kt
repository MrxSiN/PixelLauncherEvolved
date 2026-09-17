package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

/**
 * The shapes Android 17's Material 3 Expressive surfaces are built from.
 *
 * Settings rows, dialogs and the buttons in them all take their corners and
 * their press ripple from here, so a card in Home settings and a card in a
 * dialog this module opens cannot drift apart.
 */
object ExpressiveShapes {

    /** Settings cards: wide corners on the ends of a run, tight ones between. */
    const val CARD_RADIUS_DP = 24f
    const val ROW_RADIUS_DP = 8f
    const val ROW_GAP_DP = 2f

    /** Material 3's extra-large shape, the corner a dialog is drawn with. */
    const val DIALOG_RADIUS_DP = 28f

    private const val RIPPLE_ALPHA = 0x1F

    /** One row of a grouped card, rounded on the ends of the card it belongs to. */
    fun card(context: Context, placement: RowPlacement): Drawable {
        val large = context.dp(CARD_RADIUS_DP)
        val small = context.dp(ROW_RADIUS_DP)
        val top = if (placement.startsTheCard) large else small
        val bottom = if (placement.endsTheCard) large else small
        return pressable(context, ExpressiveRole.SURFACE_BRIGHT.of(context), corners(top, bottom))
    }

    /** A fully rounded shape, as every Expressive button is. */
    fun pill(context: Context, color: Int): Drawable =
        pressable(context, color, corners(context.dp(PILL_RADIUS_DP), context.dp(PILL_RADIUS_DP)))

    /** A filled shape with the same corner on every side. */
    fun rounded(color: Int, radius: Float): Drawable = filled(color, corners(radius, radius))

    /**
     * [color] filled into [corners], with a press ripple kept inside them.
     *
     * A mask with the shape's own corners keeps a press inside the shape rather
     * than letting it square off a rounded end.
     */
    private fun pressable(context: Context, color: Int, corners: FloatArray): Drawable = RippleDrawable(
        ColorStateList.valueOf(ExpressiveRole.ON_SURFACE.of(context).withAlpha(RIPPLE_ALPHA)),
        filled(color, corners),
        filled(Color.WHITE, corners),
    )

    private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

    private fun filled(color: Int, corners: FloatArray): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadii = corners
    }

    /** Clockwise from the top left, as the x and y radius of each corner. */
    private fun corners(top: Float, bottom: Float) =
        floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)

    private val RowPlacement.startsTheCard: Boolean
        get() = this == RowPlacement.TOP || this == RowPlacement.SINGLE

    private val RowPlacement.endsTheCard: Boolean
        get() = this == RowPlacement.BOTTOM || this == RowPlacement.SINGLE

    /** Larger than any button is tall, so both ends are always half circles. */
    private const val PILL_RADIUS_DP = 100f
}
