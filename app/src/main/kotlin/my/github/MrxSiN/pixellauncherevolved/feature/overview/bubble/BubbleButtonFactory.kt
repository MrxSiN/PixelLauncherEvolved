package my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

import my.github.MrxSiN.pixellauncherevolved.feature.overview.LauncherResources

/**
 * Builds the bubble button injected into an Overview task card.
 *
 * The button is sized like a Material 3 medium floating action button so it
 * reads as a primary action on the card rather than as a corner affordance: a
 * 56dp circle around a 24dp glyph. Its colours, icon, and label still come from
 * the launcher, so it tracks the system theme.
 */
class BubbleButtonFactory {

    fun create(context: Context, listener: View.OnClickListener): ImageView {
        val launcherResources = LauncherResources(context)

        val circleSize = context.pixels(CIRCLE_DP)
        val inset = context.pixels((CIRCLE_DP - GLYPH_DP) / 2)

        return ImageView(context).apply {
            tag = VIEW_TAG
            layoutParams = FrameLayout.LayoutParams(circleSize, circleSize)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(launcherResources.drawable(ICON_RESOURCE))
            imageTintList = ColorStateList.valueOf(
                launcherResources.color(ICON_TINT_RESOURCE, Color.WHITE),
            )
            contentDescription = launcherResources.string(LABEL_RESOURCE, "Bubble")
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
        /** Marks the injected button so a task card is only decorated once. */
        const val VIEW_TAG: String = "pixellauncherevolved:bubble_button"

        private const val ICON_RESOURCE = "ic_bubble_button"
        private const val BACKGROUND_RESOURCE = "circle_dismiss_background"
        private const val ICON_TINT_RESOURCE = "materialColorOnPrimary"
        private const val LABEL_RESOURCE = "bubble"

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
