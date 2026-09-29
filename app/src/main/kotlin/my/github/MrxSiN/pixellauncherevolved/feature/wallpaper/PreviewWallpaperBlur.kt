package my.github.MrxSiN.pixellauncherevolved.feature.wallpaper

import android.content.Context
import android.os.Bundle
import android.view.SurfaceControl
import android.view.View
import android.view.ViewTreeObserver

import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.wallpaper.HomeBlurRadius

/**
 * Blurs the wallpaper under Wallpaper & style's home screen preview as home
 * blurs it.
 *
 * ```
 * PreviewSurfaceRenderer.<init>(Context, RunnableList, Bundle, int, boolean)   one live preview (`get_preview`)
 * ```
 *
 * The picker draws the wallpaper itself, on a surface under the one the
 * launcher draws the preview on, so the launcher's own depth never reaches it.
 * The preview's surface is given the background blur the launcher gives its
 * window at home ([HomeBlurRadius]), shrunk with the preview, and the compositor
 * blurs the picker's wallpaper under it, inside the card's rounded corners.
 *
 * A picture (`get_preview_bitmap`) has no wallpaper under it to blur; the
 * picker blurs its own copy ([my.github.MrxSiN.pixellauncherevolved.picker.LauncherPreview]).
 */
internal class PreviewWallpaperBlur(private val feature: FeatureContext) {

    private val viewRootImpl: Method? by lazy { runCatching { View::class.java.getMethod("getViewRootImpl") }.getOrNull() }
    private val surfaceOf: Method? by lazy {
        runCatching { Class.forName("android.view.ViewRootImpl").getMethod("getSurfaceControl") }.getOrNull()
    }
    private val blurBehind: Method? by lazy {
        runCatching {
            SurfaceControl.Transaction::class.java.getMethod(
                "setBackgroundBlurRadius",
                SurfaceControl::class.java,
                Int::class.javaPrimitiveType,
            )
        }.getOrNull()
    }

    fun install() {
        val renderer = feature.findClass(RENDERER) ?: return warn()
        val tracker = feature.findClass(RUNNABLE_LIST) ?: return warn()
        val create = runCatching {
            renderer.getDeclaredConstructor(
                Context::class.java,
                tracker,
                Bundle::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
        }.getOrNull() ?: return warn()
        val root = Reflect.field(renderer, VIEW_ROOT) ?: return warn()
        val width = Reflect.field(renderer, WIDTH) ?: return warn()

        feature.xposed.hook(create).intercept { chain ->
            val result = chain.proceed()
            if (chain.getArg(BITMAP_ARGUMENT) != true) {
                runCatching { blur(chain.thisObject, root, width) }
                    .onFailure { feature.logger.warn("The home preview's wallpaper could not be blurred", it) }
            }
            result
        }
    }

    private fun blur(renderer: Any?, rootField: Field, widthField: Field) {
        renderer ?: return
        val full = HomeBlurRadius.of(
            feature.appContext,
            feature.settings[Settings.HOME_BLUR_WALLPAPER],
            feature.settings[Settings.HOME_BLUR_STRENGTH],
        )
        if (full <= 0) return
        val root = rootField.get(renderer) as? View ?: return
        val screen = feature.appContext.resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val radius = (full.toLong() * widthField.getInt(renderer) / screen).toInt()
        if (radius <= 0) return

        // Set on the preview's thread, once its surface exists: the first draw after this.
        root.post {
            root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    root.viewTreeObserver.removeOnPreDrawListener(this)
                    runCatching { apply(root, radius) }
                        .onFailure { feature.logger.warn("The home preview's wallpaper could not be blurred", it) }
                    return true
                }
            })
            root.invalidate()
        }
    }

    private fun apply(root: View, radius: Int) {
        val impl = viewRootImpl?.invoke(root) ?: return
        val surface = surfaceOf?.invoke(impl) as? SurfaceControl ?: return
        if (!surface.isValid) return
        val transaction = SurfaceControl.Transaction()
        blurBehind?.invoke(transaction, surface, radius) ?: return
        root.rootSurfaceControl?.applyTransactionOnDraw(transaction) ?: transaction.apply()
    }

    private fun warn() = feature.logger.warn("The launcher's home preview is built differently; its wallpaper stays sharp")

    private companion object {
        const val RENDERER = "com.android.launcher3.preview.PreviewSurfaceRenderer"
        const val RUNNABLE_LIST = "com.android.launcher3.util.RunnableList"
        const val VIEW_ROOT = "mViewRoot"
        const val WIDTH = "mWidth"

        /** `PreviewSurfaceRenderer(Context, RunnableList, Bundle, int callingPid, boolean bitmap)`. */
        const val BITMAP_ARGUMENT = 4
    }
}
