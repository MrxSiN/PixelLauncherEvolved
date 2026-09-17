package my.github.MrxSiN.pixellauncherevolved.feature.wallpaper

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature
import my.github.MrxSiN.pixellauncherevolved.wallpaper.HomeBlurDepth

/**
 * Blurs the wallpaper while the home screen is showing.
 *
 * The home state is told to report a depth and the launcher does the rest, so
 * the tweak inherits the launcher's own conditions for free: no blur where the
 * platform has switched cross-window blurs off, none behind a fully opaque
 * scrim, and a deeper state deepens the blur rather than stacking a second one
 * on top of it. No other state is touched, because the answer is changed for
 * the home state alone.
 *
 * Three things are hooked and each answers one question. What home reports is
 * where the answer is given. Where a state's depth is applied is where the
 * controllers become reachable, so a change can be pushed to them. Coming back
 * to the front is where a change made in Home settings is picked up, because
 * Home settings is the activity a person leaves to get here.
 */
class HomeWallpaperBlurFeature : ToggleFeature(Settings.HOME_BLUR_WALLPAPER) {

    override val compatibility = CompatibilityFeature.BLUR_WALLPAPER

    override fun install(context: FeatureContext) {
        val depth = LauncherDepth.of(context) ?: run {
            context.logger.warn("Wallpaper blur is not installed")
            return
        }

        val blur = HomeBlurDepth()
        blur.isEnabled = isEnabled(context.settings)
        blur.strength = context.settings[Settings.HOME_BLUR_STRENGTH]

        context.xposed.hook(depth.stateDepth).intercept { chain ->
            val asked = chain.proceed() as Float
            val out = if (chain.thisObject === depth.home) blur.depthForHome(asked) else asked
            out
        }

        // Applying a state is the only place a depth controller is handed to
        // this module, and the state that came with it is the one to apply
        // again when the answer changes.
        context.xposed.hook(depth.applyState).intercept { chain ->
            val controller = chain.thisObject
            val state = chain.getArg(0)
            if (controller != null && state != null) blur.remember(controller, state)
            chain.proceed()
        }

        depth.applyStateOverTime?.let { overTime ->
            context.xposed.hook(overTime).intercept { chain ->
                val controller = chain.thisObject
                val state = chain.getArg(0)
                if (controller != null && state != null) blur.remember(controller, state)
                chain.proceed()
            }
        }

        installPausedBlur(context, depth, blur)
        installSettled(context, depth, blur)
        installResume(context, depth, blur)

        context.logger.info("Wallpaper blur ready, ${onOff(blur.isEnabled)} at ${blur.strength}%")
    }

    /**
     * Holds the wallpaper's blur through the animation home.
     *
     * Arriving home from an app, the launcher switches its own window blurs off
     * for the length of the animation, and on a blurred home screen that reads
     * as the wallpaper snapping sharp until it lands. Skipping the pause keeps
     * the blur, and that is what this does: while the tweak is on, a request to
     * pause is answered without being passed on.
     *
     * **This is the approach `HOOK_NOTES.md` records as failing, recovered from
     * a build rather than written afresh, and it is kept behind that warning
     * rather than because the warning was answered.** A back gesture follows an
     * app's window off the screen, which puts the launcher's own content under
     * the transition leash, and `setBackgroundBlurRadius` blurs everything
     * behind the surface it is set on — so the icons, their labels and the
     * search bar blur along with the wallpaper. Only the status bar, a window
     * of its own, stays sharp. Read that file's three rejected gates before
     * trying to narrow this: the obvious ones were measured and none held.
     *
     * [LauncherDepth.revealHome] is watched so the module knows a pause raised
     * inside the animation home from any other, which is what such a gate would
     * be built on. Nothing is gated on it here — the skip is unconditional
     * while the tweak is on, exactly as recovered.
     *
     * When the pause is not skipped it still leaves the workspace effect
     * behind, so that clearing stays: pausing applies depth and blur again,
     * which would recompute what else is blurred with `mCurrentBlur` at zero —
     * but the launcher applies a depth only when the depth has moved, and
     * resting at one is the case where it has not. Without it the workspace
     * keeps the `RenderEffect` it was given while blurs were on, and the icons
     * stay smeared for the length of the animation.
     */
    private fun installPausedBlur(context: FeatureContext, depth: LauncherDepth, blur: HomeBlurDepth) {
        val pause = depth.pauseBlurs ?: return

        // Read by the pause below, which runs on the same thread the animation
        // is built on, so the two never overlap.
        var revealing = false

        depth.revealHome?.let { reveal ->
            context.xposed.hook(reveal).intercept { chain ->
                revealing = true
                try {
                    chain.proceed()
                } finally {
                    revealing = false
                }
            }
        }

        context.xposed.hook(pause).intercept { chain ->
            val isPausing = chain.getArg(0) == true
            context.logger.info("Blur pause: pausing=$isPausing revealing=$revealing")

            // Answered without being passed on, so the launcher's blurs stay on
            // and the wallpaper keeps its blur through the animation.
            if (blur.isEnabled && isPausing) return@intercept null

            chain.proceed().also {
                if (blur.isEnabled && isPausing) {
                    blur.remembered().forEach { (controller, _) -> depth.refreshBlur(controller) }
                }
            }
        }
    }

    /**
     * Has the launcher decide again what the depth blurs, once a state settles.
     *
     * The launcher blurs its own workspace as well as the wallpaper while the
     * app drawer is the state being left, and it makes that decision every time
     * it applies a depth. It applies one whenever the depth moves, and with
     * this tweak on the depth stops moving on the last frame of the transition
     * — while the drawer is still the state being left — so the workspace is
     * left blurred with nothing to move the depth again and put it right. On a
     * stock home screen the depth carries on to zero after that frame, which is
     * what makes the decision again.
     *
     * Nothing here decides anything: the launcher is asked to look again, once,
     * at the moment its own answer has changed.
     */
    private fun installSettled(context: FeatureContext, depth: LauncherDepth, blur: HomeBlurDepth) {
        val launcher = context.findClass(QUICKSTEP_LAUNCHER) ?: context.findClass(LAUNCHER)
        val baseState = context.findClass(BASE_STATE)
        val settled = if (launcher == null || baseState == null) {
            null
        } else {
            Reflect.method(launcher, ON_STATE_SET_END, baseState)
        }

        if (settled == null) {
            context.logger.warn("The launcher does not report a settled state; the workspace may stay blurred")
            return
        }

        context.xposed.hook(settled).intercept { chain ->
            chain.proceed().also {
                if (blur.isEnabled) {
                    blur.remembered().forEach { (controller, _) -> depth.refreshBlur(controller) }
                }
            }
        }
    }

    /**
     * Picks up a change made in Home settings, and puts it on the screen.
     *
     * Reading the settings here rather than on every call keeps a preference
     * lookup out of each state change. Applying the state again is what makes
     * the change visible without waiting for the next one, and it is the
     * launcher's own path to the wallpaper rather than a second one.
     */
    private fun installResume(context: FeatureContext, depth: LauncherDepth, blur: HomeBlurDepth) {
        val launcher = context.findClass(QUICKSTEP_LAUNCHER) ?: context.findClass(LAUNCHER)
        val resumed = launcher?.let { Reflect.method(it, ON_RESUME) }

        if (resumed == null) {
            context.logger.warn("The launcher does not report onResume; wallpaper blur needs a restart to change")
            return
        }

        context.xposed.hook(resumed).intercept { chain ->
            chain.proceed().also {
                val wanted = isEnabled(context.settings)
                val strength = context.settings[Settings.HOME_BLUR_STRENGTH]

                if (wanted != blur.isEnabled || strength != blur.strength) {
                    blur.isEnabled = wanted
                    blur.strength = strength
                    blur.remembered().forEach { (controller, state) -> depth.reapply(controller, state) }
                    context.logger.info("Wallpaper blur ${onOff(wanted)} at $strength%")
                }
            }
        }
    }

    private fun onOff(isEnabled: Boolean): String = if (isEnabled) "on" else "off"

    private companion object {
        const val LAUNCHER = "com.android.launcher3.Launcher"
        const val QUICKSTEP_LAUNCHER = "com.android.launcher3.uioverrides.QuickstepLauncher"
        const val BASE_STATE = "com.android.launcher3.statemanager.BaseState"
        const val ON_STATE_SET_END = "onStateSetEnd"
        const val ON_RESUME = "onResume"
    }
}
