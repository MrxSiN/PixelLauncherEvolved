package my.github.MrxSiN.pixellauncherevolved.feature.overview.card

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

import my.github.MrxSiN.pixellauncherevolved.feature.overview.LauncherResources

/**
 * Builds a button this module injects into an Overview task card.
 *
 * A button is sized like a Material 3 medium floating action button so it reads
 * as a primary action on the card rather than as a corner affordance: a 56dp
 * circle around a 24dp glyph. Its colours, icon and label come from the
 * launcher, so it tracks the system theme and the device's language.
 *
 * What differs between one of these buttons and the next is only which glyph
 * and which label, which is why they share this and are told apart by [tag].
 */
class TaskCardButtonFactory(
    /** Marks the injected button, so a card is only decorated once with it. */
    val tag: String,
    /** Launcher drawables to try, in order; the first one it has is used. */
    private val iconResources: List<String>,
    private val labelResource: String,
    private val fallbackLabel: String,
) {

    fun create(context: Context, listener: View.OnClickListener): ImageView {
        val launcherResources = LauncherResources(context)

        val circleSize = context.pixels(CIRCLE_DP)
        val inset = context.pixels((CIRCLE_DP - GLYPH_DP) / 2)

        return ImageView(context).apply {
            tag = this@TaskCardButtonFactory.tag
            layoutParams = FrameLayout.LayoutParams(circleSize, circleSize)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(iconResources.firstNotNullOfOrNull(launcherResources::drawable))
            imageTintList = ColorStateList.valueOf(
                launcherResources.color(ICON_TINT_RESOURCE, Color.WHITE),
            )
            contentDescription = launcherResources.string(labelResource, fallbackLabel)
            isClickable = true
            isFocusable = true
            setOnClickListener(listener)

            launcherResources.drawable(BACKGROUND_RESOURCE)?.let { background = it }
            // Set after the background: a background drawable can install
            // padding of its own, and the glyph has to stay inset in the circle.
            setPadding(inset, inset, inset, inset)
        }
    }

    /** Gap kept between the button and the thumbnail edge. */
    fun marginPixels(context: Context): Int = context.pixels(MARGIN_DP)

    companion object {

        private const val BACKGROUND_RESOURCE = "circle_dismiss_background"
        private const val ICON_TINT_RESOURCE = "materialColorOnPrimary"

        /** Material 3 medium floating action button container. */
        private const val CIRCLE_DP = 56

        /** Material 3 medium floating action button icon. */
        private const val GLYPH_DP = 24

        /** Gap kept between the button and the thumbnail edge. */
        private const val MARGIN_DP = 12

        private fun Context.pixels(dp: Int): Int = Math.round(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                resources.displayMetrics,
            ),
        )
    }
}
