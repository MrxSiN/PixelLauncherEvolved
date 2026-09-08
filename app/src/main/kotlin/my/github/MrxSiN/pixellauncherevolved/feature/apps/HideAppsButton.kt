package my.github.MrxSiN.pixellauncherevolved.feature.apps

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

import my.github.MrxSiN.pixellauncherevolved.feature.overview.LauncherResources

/**
 * The button that ends the choosing.
 *
 * A Material 3 medium floating action button in the bottom-right corner of the
 * launcher, above everything else, because it is the one thing on screen that
 * is not the drawer. It sits in the drag layer rather than inside the drawer so
 * that scrolling the apps does not carry it away.
 */
class HideAppsButton {

    fun addTo(dragLayer: FrameLayout, onConfirm: View.OnClickListener): View {
        val context = dragLayer.context
        val launcherResources = LauncherResources(context)

        val circle = context.pixels(CIRCLE_DP)
        val inset = context.pixels((CIRCLE_DP - GLYPH_DP) / 2)
        val margin = context.pixels(MARGIN_DP)

        val button = ImageView(context).apply {
            tag = VIEW_TAG
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(launcherResources.drawable(ICON) ?: checkFallback(context))
            imageTintList = ColorStateList.valueOf(launcherResources.color(TINT, Color.WHITE))
            isClickable = true
            isFocusable = true
            elevation = context.pixels(ELEVATION_DP).toFloat()
            setOnClickListener(onConfirm)

            launcherResources.drawable(BACKGROUND)?.let { background = it }
            // After the background: a background drawable can install padding
            // of its own, and the glyph has to stay inset in the circle.
            setPadding(inset, inset, inset, inset)
        }

        dragLayer.addView(button, FrameLayout.LayoutParams(circle, circle))

        // The drag layer keeps layout parameters of its own kind and builds
        // them from ours by copying the size alone, so where the button goes is
        // said on the ones it ends up with rather than the ones it was given.
        (button.layoutParams as? FrameLayout.LayoutParams)?.let { placed ->
            placed.gravity = Gravity.BOTTOM or Gravity.END
            placed.setMargins(margin, margin, margin, context.pixels(BOTTOM_MARGIN_DP))
            button.layoutParams = placed
        }

        return button
    }

    fun removeFrom(dragLayer: FrameLayout) {
        dragLayer.findViewWithTag<View>(VIEW_TAG)?.let(dragLayer::removeView)
    }

    /**
     * The platform's own tick, for a launcher that has renamed its glyph.
     *
     * Losing the icon would leave an unlabelled circle, which is the one part
     * of this that has to be readable.
     */
    private fun checkFallback(context: Context) =
        context.getDrawable(android.R.drawable.checkbox_on_background)

    companion object {
        /** Marks the button so the launcher is only given one. */
        const val VIEW_TAG: String = "pixellauncherevolved:hide_apps_button"

        private const val ICON = "ic_done"
        private const val BACKGROUND = "circle_dismiss_background"
        private const val TINT = "materialColorOnPrimary"

        /** Material 3 medium floating action button container and glyph. */
        private const val CIRCLE_DP = 56
        private const val GLYPH_DP = 24
        /** Clear of the gesture bar, which the drag layer reaches behind. */
        private const val MARGIN_DP = 24
        private const val BOTTOM_MARGIN_DP = 48
        private const val ELEVATION_DP = 6

        private fun Context.pixels(dp: Int): Int = Math.round(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                resources.displayMetrics,
            ),
        )
    }
}
