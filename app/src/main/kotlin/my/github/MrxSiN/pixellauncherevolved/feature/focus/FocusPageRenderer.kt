package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.content.Context
import android.graphics.Bitmap
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.Executor
import java.util.concurrent.Executors

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * Pictures of single home screen pages, drawn by the launcher's own preview.
 *
 * ```
 * LauncherComponentProvider.get(Context).getGridCustomizationsProxy()
 *   Bundle call("get_preview_bitmap", null, Bundle)      // "image": the picture
 * PreviewSurfaceRenderer(Context, RunnableList, Bundle, int, boolean bitmap)
 *   Bundle "workspace_page_id"                            // the one page it lays out
 *   RunnableList mLifeCycleTracker                        // executeAllAndDestroy() lets it go
 * ```
 *
 * This is what Wallpaper & style shows, asked for one page at a time. The
 * preview builds its own workspace from the launcher's database, with its own
 * icon cache and widget host, so nothing on the home screen in front of the
 * person is touched, measured or photographed: no page is caught part way
 * through a swipe or a Mode's reveal, a page a Mode hides is drawn like any
 * other, and every icon, label, folder and widget is the launcher's own view,
 * bound before the picture is taken. The picture has no wallpaper behind it;
 * [FocusPagePreviewView] puts the home wallpaper under it.
 *
 * Pictures are drawn one at a time on a thread of their own, because the
 * launcher answers by building the preview on its main thread and waiting.
 * Each is tagged with the request that asked for it, and only the newest
 * request's picture for a page is kept, so an older drawing finishing late
 * can never replace a newer one. A workspace bind makes every picture stale
 * ([invalidate]); stale pictures are still shown until their replacement is
 * ready, so a page never blinks out.
 */
internal object FocusPageRenderer {

    /** Where a page's picture stands. */
    enum class State { NONE, PENDING, READY, FAILED }

    private val main = Handler(Looper.getMainLooper())
    /** Main thread only. */
    private val book = RenderBook<Bitmap>()
    private val listeners = ArrayList<Runnable>()

    /** Bumped by every workspace bind, from whichever thread binds. */
    @Volatile
    private var generation = 0

    @Volatile
    private var bridge: Bridge? = null

    private val worker: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ple-focus-render").apply { isDaemon = true }
    }

    /** Whether the launcher's preview can be asked for a page at all. */
    val isAvailable: Boolean get() = bridge != null

    /** Finds the launcher's preview and lets each picture it draws go once taken. */
    fun install(feature: FeatureContext) {
        bridge = runCatching { Bridge(feature) }
            .onFailure { feature.logger.warn("The launcher's page preview is unavailable; Focus pages are drawn from the model", it) }
            .getOrNull()
    }

    /** The model changed: every picture is stale, though still shown until redrawn. */
    fun invalidate() {
        generation++
    }

    /**
     * Asks for [screens] to be drawn at [width] by [height], unless a picture
     * of the current model is already in hand or on its way. Main thread.
     */
    fun request(context: Context, screens: List<Int>, width: Int, height: Int) {
        val bridge = bridge ?: return
        if (width <= 0 || height <= 0) return
        val drawingFor = generation
        for (screen in screens) {
            if (screen < 0) continue
            val request = book.begin(screen, drawingFor) ?: continue
            worker.execute {
                val started = SystemClock.uptimeMillis()
                val picture = bridge.draw(context, screen, width, height)
                val took = SystemClock.uptimeMillis() - started
                main.post {
                    if (!book.finish(screen, request, drawingFor, picture)) return@post
                    if (picture == null) {
                        bridge.logger.warn("Focus page $screen could not be drawn by the launcher's preview")
                    } else {
                        bridge.logger.info("Focus page $screen drawn in ${took}ms")
                    }
                    for (index in listeners.indices) listeners[index].run()
                }
            }
        }
    }

    /** The newest picture of [screen], current or stale; null before one is drawn. Main thread. */
    fun picture(screen: Int): Bitmap? = book.picture(screen)

    fun state(screen: Int): State = book.state(screen)

    /** Runs [listener] on the main thread whenever a picture arrives. */
    fun addListener(listener: Runnable) {
        listeners += listener
    }

    fun removeListener(listener: Runnable) {
        listeners -= listener
    }

    /** Pages given new ids carry their pictures with them. Any thread. */
    fun renumber(mapping: Map<Int, Int>) {
        if (mapping.isEmpty()) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { renumber(mapping) }
            return
        }
        book.renumber(mapping)
    }

    /** Lets go of every picture of a page the launcher no longer has. Main thread. */
    fun retain(screens: Collection<Int>) {
        if (screens.isNotEmpty()) book.retain(screens)
    }

    /** The launcher's preview, reached the way the Wallpaper & style provider reaches it. */
    private class Bridge(feature: FeatureContext) {

        val logger: Logger = feature.logger
        private val proxy: Method
        private val call: Method
        private val componentOf: Method
        private val lifeCycle: Field
        private val destroy: Method
        private val drawing = ThreadLocal<Any?>()

        init {
            val provider = requireNotNull(feature.findClass(COMPONENT_PROVIDER)) { "no component provider" }
            componentOf = requireNotNull(Reflect.method(provider, GET, Context::class.java)) { "no component" }
            val component = requireNotNull(feature.findClass(APP_COMPONENT)) { "no app component" }
            proxy = requireNotNull(Reflect.method(component, GRID_PROXY)) { "no grid customizations" }
            call = requireNotNull(
                Reflect.method(proxy.returnType, CALL, String::class.java, String::class.java, Bundle::class.java),
            ) { "no preview call" }

            val renderer = requireNotNull(feature.findClass(RENDERER)) { "no preview renderer" }
            val tracker = requireNotNull(feature.findClass(RUNNABLE_LIST)) { "no preview lifecycle" }
            lifeCycle = requireNotNull(Reflect.field(renderer, LIFE_CYCLE)) { "no preview lifecycle field" }
            destroy = requireNotNull(Reflect.method(tracker, DESTROY)) { "no preview release" }
            val create = renderer.getDeclaredConstructor(
                Context::class.java,
                tracker,
                Bundle::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )

            // The preview is built on the thread that asked, so the one this
            // module asked for is the one built here while [draw] waits.
            feature.xposed.hook(create).intercept { chain ->
                val request = chain.getArg(REQUEST_ARGUMENT) as? Bundle
                if (request?.containsKey(MARKER) == true) drawing.set(chain.thisObject)
                chain.proceed()
            }
        }

        fun draw(context: Context, screen: Int, width: Int, height: Int): Bitmap? {
            drawing.set(null)
            try {
                val request = Bundle().apply {
                    putBinder(HOST_TOKEN, Binder())
                    putInt(DISPLAY_ID, context.display?.displayId ?: 0)
                    putInt(WIDTH, width)
                    putInt(HEIGHT, height)
                    putInt(PAGE_ID, screen)
                    putBoolean(MARKER, true)
                }
                val customizations = proxy.invoke(componentOf.invoke(null, context.applicationContext))
                val answer = call.invoke(customizations, GET_BITMAP, null, request) as? Bundle
                @Suppress("DEPRECATION")
                return answer?.getParcelable(IMAGE) as? Bitmap
            } catch (error: Throwable) {
                logger.warn("Focus page $screen: the launcher's preview failed", error)
                return null
            } finally {
                drawing.get()?.let(::release)
                drawing.remove()
            }
        }

        /** `get_preview_bitmap` never lets its preview go; its surface, context and preferences are released here. */
        private fun release(renderer: Any) {
            val tracker = runCatching { lifeCycle.get(renderer) }.getOrNull() ?: return
            Handler(Looper.getMainLooper()).post {
                runCatching { destroy.invoke(tracker) }
                    .onFailure { logger.warn("A Focus page preview could not be released", it) }
            }
        }
    }

    private const val COMPONENT_PROVIDER = "com.android.launcher3.dagger.LauncherComponentProvider"
    private const val APP_COMPONENT = "com.android.launcher3.dagger.LauncherBaseAppComponent"
    private const val RENDERER = "com.android.launcher3.preview.PreviewSurfaceRenderer"
    private const val RUNNABLE_LIST = "com.android.launcher3.util.RunnableList"
    private const val GET = "get"
    private const val GRID_PROXY = "getGridCustomizationsProxy"
    private const val CALL = "call"
    private const val LIFE_CYCLE = "mLifeCycleTracker"
    private const val DESTROY = "executeAllAndDestroy"
    private const val GET_BITMAP = "get_preview_bitmap"
    private const val IMAGE = "image"
    private const val HOST_TOKEN = "host_token"
    private const val DISPLAY_ID = "display_id"
    private const val WIDTH = "width"
    private const val HEIGHT = "height"
    private const val PAGE_ID = "workspace_page_id"

    /** Marks a request as this module's, so its preview is let go once drawn. */
    private const val MARKER = "ple_focus_page"

    /** `PreviewSurfaceRenderer(Context, RunnableList lifecycle, Bundle request, int callingPid, boolean bitmap)`. */
    private const val REQUEST_ARGUMENT = 2
}

/**
 * Which picture of each page is wanted, and which answers to keep.
 *
 * Every drawing is a numbered request; only the newest request's answer for a
 * page is kept, so a drawing that finishes after a newer one was asked for is
 * dropped. A page already drawn from the current model, or being drawn, is not
 * asked for again. No Android in here, so the rules can be tested on their own.
 */
internal class RenderBook<T : Any> {

    private class Page<T>(
        var picture: T? = null,
        var state: FocusPageRenderer.State = FocusPageRenderer.State.NONE,
        /** The request whose answer is wanted; any other answer is dropped. */
        var request: Int = 0,
        /** The model generation [picture] was drawn from. */
        var drawnAt: Int = -1,
    )

    private val pages = HashMap<Int, Page<T>>()
    private var requests = 0

    /** A new request number for [screen] at model [generation], or null when none is needed. */
    fun begin(screen: Int, generation: Int): Int? {
        val page = pages.getOrPut(screen) { Page() }
        if (page.state == FocusPageRenderer.State.PENDING) return null
        if (page.state == FocusPageRenderer.State.READY && page.drawnAt == generation) return null
        page.request = ++requests
        page.state = FocusPageRenderer.State.PENDING
        return page.request
    }

    /** Takes [picture] as the answer to [request]; false when the answer is not wanted any more. */
    fun finish(screen: Int, request: Int, generation: Int, picture: T?): Boolean {
        val page = pages[screen] ?: return false
        if (page.request != request || page.state != FocusPageRenderer.State.PENDING) return false
        if (picture == null) {
            // A page that was drawn before keeps its last picture.
            page.state = if (page.picture == null) FocusPageRenderer.State.FAILED else FocusPageRenderer.State.READY
        } else {
            page.picture = picture
            page.drawnAt = generation
            page.state = FocusPageRenderer.State.READY
        }
        return true
    }

    fun picture(screen: Int): T? = pages[screen]?.picture

    fun state(screen: Int): FocusPageRenderer.State = pages[screen]?.state ?: FocusPageRenderer.State.NONE

    /**
     * Moves each page's picture to its new id. A drawing on its way is
     * forgotten: it was asked for under the old id, and is drawn again.
     */
    fun renumber(mapping: Map<Int, Int>) {
        val moved = HashMap<Int, Page<T>>(pages.size)
        for ((screen, page) in pages) moved[mapping[screen] ?: screen] = page
        pages.clear()
        pages.putAll(moved)
        for (page in pages.values) {
            if (page.state == FocusPageRenderer.State.PENDING) {
                page.state = if (page.picture == null) FocusPageRenderer.State.NONE else FocusPageRenderer.State.READY
                page.drawnAt = -1
                page.request = 0
            }
        }
    }

    fun retain(screens: Collection<Int>) {
        pages.keys.retainAll(screens.toHashSet())
    }
}
