package my.github.MrxSiN.pixellauncherevolved.feature.icons

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.bridge.IconsBridge
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.icons.IconSource

/**
 * Home screen previews drawn with an icon pack that is not chosen yet.
 *
 * ```
 * PreviewSurfaceRenderer.<init>(Context, RunnableList, Bundle, int, boolean)   the request, on the Binder thread
 * PreviewSurfaceRenderer.recreatePreviewRenderer()                              before the preview loads, on the main thread
 * ```
 *
 * Every preview the launcher draws has a context of its own, and with it its
 * own icon provider, icon factory and icon cache. Wallpaper & style names the
 * pack to preview in its request ([IconsBridge.PREVIEW_PACK], empty for System),
 * the choice is remembered against that preview's context, and the icon hooks
 * ask [choiceOf] with the context they are drawing for. The launcher's own home
 * screen and a preview asked for without the key are never matched, so they
 * keep the chosen pack.
 *
 * `get_preview_bitmap` never releases the preview it draws. The ones this
 * module asks for are released once their picture is taken ([render]).
 */
internal class IconPreviews(private val feature: FeatureContext, private val controller: IconPackController) {

    /** What one preview draws: [source], or the system's icons when it is null. */
    class Choice(val source: IconSource?)

    private val byRenderer: MutableMap<Any, Choice> = Collections.synchronizedMap(WeakHashMap())
    private val byContext: MutableMap<Any, Choice> = Collections.synchronizedMap(WeakHashMap())
    private val drawing = ThreadLocal<Any?>()
    private val main = Handler(Looper.getMainLooper())

    private var previewContext: Field? = null
    private var lifeCycle: Field? = null
    private var destroy: Method? = null

    /** Whether any preview has asked for a pack; false keeps the icon hooks on their launcher-only path. */
    @Volatile
    var any: Boolean = false
        private set

    fun install() {
        val renderer = feature.findClass(RENDERER) ?: return warn("the launcher's preview renderer is gone")
        val tracker = feature.findClass(RUNNABLE_LIST)
        val create = runCatching {
            renderer.getDeclaredConstructor(
                Context::class.java,
                tracker,
                Bundle::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
        }.getOrNull() ?: return warn("the launcher's preview renderer is built differently")
        val recreate = Reflect.method(renderer, RECREATE) ?: return warn("the launcher's preview is loaded differently")
        previewContext = Reflect.field(renderer, PREVIEW_CONTEXT) ?: return warn("a preview's context is unreachable")
        lifeCycle = Reflect.field(renderer, LIFE_CYCLE)
        destroy = tracker?.let { Reflect.method(it, DESTROY) }

        feature.xposed.hook(create).intercept { chain ->
            val request = chain.getArg(REQUEST_ARGUMENT) as? Bundle
            val pack = request?.getString(IconsBridge.PREVIEW_PACK)
            val self = chain.thisObject
            if (pack != null && self != null) {
                val source = if (pack.isEmpty()) null else runCatching { controller.preview(pack) }
                    .onFailure { feature.logger.warn("Icons: $pack cannot be previewed", it) }
                    .getOrNull()
                byRenderer[self] = Choice(source)
                any = true
                if (chain.getArg(BITMAP_ARGUMENT) == true) drawing.set(self)
            }
            chain.proceed()
        }

        feature.xposed.hook(recreate).intercept { chain ->
            val self = chain.thisObject
            val choice = self?.let { byRenderer[it] }
            if (choice != null) {
                runCatching { previewContext?.get(self) }.getOrNull()?.let { byContext[it] = choice }
            }
            chain.proceed()
        }
    }

    /** The preview [context] draws, or null for the launcher's own icons. */
    fun choiceOf(context: Any?): Choice? = if (!any || context == null) null else byContext[context]

    /** Runs a `get_preview_bitmap` [call] of this module's and releases the preview it drew. */
    fun <T> render(call: () -> T): T {
        drawing.set(null)
        try {
            return call()
        } finally {
            drawing.get()?.let(::release)
            drawing.remove()
        }
    }

    /** The picture is taken; its preview surface, context and preferences are let go on the main thread. */
    private fun release(renderer: Any) {
        val tracker = runCatching { lifeCycle?.get(renderer) }.getOrNull() ?: return
        val end = destroy ?: return
        main.post {
            runCatching { end.invoke(tracker) }
                .onFailure { feature.logger.warn("Icons: a home screen preview could not be released", it) }
        }
    }

    private fun warn(what: String) = feature.logger.warn("Icons: $what; Wallpaper & style previews only the chosen pack")

    private companion object {
        const val RENDERER = "com.android.launcher3.preview.PreviewSurfaceRenderer"
        const val RUNNABLE_LIST = "com.android.launcher3.util.RunnableList"
        const val RECREATE = "recreatePreviewRenderer"
        const val PREVIEW_CONTEXT = "mPreviewContext"
        const val LIFE_CYCLE = "mLifeCycleTracker"
        const val DESTROY = "executeAllAndDestroy"

        /** `PreviewSurfaceRenderer(Context, RunnableList lifecycle, Bundle request, int callingPid, boolean bitmap)`. */
        const val REQUEST_ARGUMENT = 2
        const val BITMAP_ARGUMENT = 4
    }
}
