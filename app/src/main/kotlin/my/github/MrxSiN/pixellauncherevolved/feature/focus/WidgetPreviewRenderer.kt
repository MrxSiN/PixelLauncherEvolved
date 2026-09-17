package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews

/**
 * What a widget on a page with no picture of its own looks like.
 *
 * A widget that ships a preview layout is inflated from it, in [context]'s
 * configuration, at the size it takes on the home screen. That is the preview
 * the launcher's own widget picker shows, and unlike a preview image it takes
 * the wallpaper colours and the dark theme in force now. A widget with only a
 * preview image falls back to that image, which is fixed when the app is built.
 */
internal class WidgetPreviewRenderer(private val context: Context) {

    fun render(info: AppWidgetProviderInfo, width: Int, height: Int): Drawable? =
        runCatching { layout(info, width, height) }.getOrNull()
            ?: runCatching { info.loadPreviewImage(context, 0) }.getOrNull()

    private fun layout(info: AppWidgetProviderInfo, width: Int, height: Int): Drawable? {
        if (info.previewLayout == 0 || width <= 0 || height <= 0) return null
        val view = RemoteViews(info.provider.packageName, info.previewLayout)
            .apply(context, FrameLayout(context))
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = HardwarePicture.record(width, height, view::draw) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }
}
