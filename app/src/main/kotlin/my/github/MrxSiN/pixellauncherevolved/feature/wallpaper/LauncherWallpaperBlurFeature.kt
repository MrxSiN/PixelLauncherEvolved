package my.github.MrxSiN.pixellauncherevolved.feature.wallpaper

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource
import my.github.MrxSiN.pixellauncherevolved.wallpaper.WallpaperBlur

/**
 * Blurs the wallpaper while the home screen is showing.
 *
 * The launcher already blurs the wallpaper behind the app drawer and Recents,
 * through the depth its state handler asks for: a depth of zero on home, rising
 * as a state takes over the screen. That one number drives both the background
 * blur the launcher puts on the wallpaper surface and the slight zoom out that
 * goes with it, which together are the effect this tweak is asked for. So
 * rather than blurring the launcher's window from the outside — which would
 * stack a second blur on top of the launcher's own the moment the app drawer
 * opened — this raises the floor under that depth, and the launcher draws the
 * result itself.
 *
 * That also means the tweak inherits the launcher's own conditions for free: no
 * blur where the platform has switched cross-window blurs off, and none behind
 * a fully opaque scrim.
 */
class LauncherWallpaperBlurFeature : LauncherFeature {

    override val id: String = "home_blur_wallpaper"

    /**
     * Its setting is not one of the launcher's.
     *
     * The switch is in Wallpaper & Style, which stores the choice in secure
     * settings, so there is nothing in the launcher's own store to gate on.
     */
    override fun isEnabled(settings: SettingsSource): Boolean = true

    /**
     * Kept alive for as long as the feature is, because a content observer only
     * reports while something still holds it.
     */
    private var observer: ContentObserver? = null

    override fun install(context: FeatureContext) {
        val controller = context.findClass(DEPTH_CONTROLLER)
        val setDepth = controller?.let { Reflect.method(it, SET_DEPTH, FLOAT) }
        val depth = controller?.let { Reflect.field(it, DEPTH_FIELD) }

        if (controller == null || setDepth == null || depth == null) {
            context.logger.warn("The launcher's depth controller is unavailable; wallpaper blur is not installed")
            return
        }

        // The one call into the depth is short enough for ART to inline it past
        // the hook. Deoptimizing the caller keeps the call real; it is found by
        // its shape rather than its name, which the launcher's shrinker rewrites.
        bridgeTo(controller)?.let(context.xposed::deoptimize)

        val floor = WallpaperDepthFloor(setDepth, depth, context.logger)
        floor.isEnabled = WallpaperBlur.isEnabled(context.appContext)

        context.xposed.hook(setDepth).intercept { chain ->
            val wanted = chain.getArg(0) as Float
            chain.thisObject?.let { floor.remember(it, wanted) }
            chain.proceed(arrayOf<Any?>(floor.floor(wanted)))
        }

        watch(context, floor)
        context.logger.info("Wallpaper blur ready, currently ${if (floor.isEnabled) "on" else "off"}")
    }

    /** Applies the switch in Wallpaper & Style without waiting for a state change. */
    private fun watch(context: FeatureContext, floor: WallpaperDepthFloor) {
        val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                floor.isEnabled = WallpaperBlur.isEnabled(context.appContext)
                floor.reapply()
                context.logger.info("Wallpaper blur switched ${if (floor.isEnabled) "on" else "off"}")
            }
        }

        observer = watcher
        context.appContext.contentResolver.registerContentObserver(
            WallpaperBlur.uri(),
            false,
            watcher,
        )
    }

    /**
     * The static call that stands between the depth property and the setter.
     *
     * The shrinker renames it, so it is recognised by the only shape it can
     * have: static, returning nothing, taking a controller and a depth.
     */
    private fun bridgeTo(controller: Class<*>): Method? = controller.declaredMethods.singleOrNull {
        Modifier.isStatic(it.modifiers) &&
            it.returnType == Void.TYPE &&
            it.parameterTypes.size == 2 &&
            it.parameterTypes[0] == controller &&
            it.parameterTypes[1] == FLOAT
    }

    private companion object {
        const val DEPTH_CONTROLLER = "com.android.quickstep.util.BaseDepthControllerImpl"
        const val SET_DEPTH = "setDepth"
        const val DEPTH_FIELD = "mDepth"
        val FLOAT: Class<*> = Float::class.javaPrimitiveType!!
    }
}

/**
 * The lowest depth the launcher is allowed to settle on.
 *
 * It remembers what the launcher last asked each of its depth controllers for,
 * so that switching the tweak off puts back the depth the launcher wanted
 * rather than leaving the home screen blurred until the next state change.
 */
private class WallpaperDepthFloor(
    private val setDepth: Method,
    private val depth: Field,
    private val logger: Logger,
) {

    private val wanted = WeakHashMap<Any, Float>()

    /** Set while [reapply] drives the setter, so it is not mistaken for the launcher. */
    private var isReapplying = false

    @Volatile
    var isEnabled: Boolean = false

    fun floor(depth: Float): Float = if (isEnabled) maxOf(depth, HOME_DEPTH) else depth

    fun remember(controller: Any, depth: Float) {
        if (!isReapplying) wanted[controller] = depth
    }

    /** Runs the launcher's last depth through the setting as it now stands. */
    fun reapply() {
        isReapplying = true
        try {
            for ((controller, requested) in wanted.entries.map { it.key to it.value }) {
                runCatching {
                    // The setter returns early when the depth has not moved, and
                    // what it compares against is the floored value it already
                    // stored. A depth outside the range it accepts never matches.
                    depth.setFloat(controller, UNSET)
                    setDepth.invoke(controller, floor(requested))
                }.onFailure { logger.warn("Unable to apply the wallpaper blur", it) }
            }
        } finally {
            isReapplying = false
        }
    }

    private companion object {
        /**
         * Half of the blur the launcher draws behind its own surfaces.
         *
         * `BaseDepthControllerImpl.mapDepthToBlur` scales linearly up to the
         * full blur radius at a depth of 0.3, so half of it reads as a wallpaper
         * pushed back behind the icons rather than as the app drawer's frost.
         */
        const val HOME_DEPTH = 0.15f

        /** Outside the 0..1 a depth is bounded to, so it can never be a real one. */
        const val UNSET = -1f
    }
}
