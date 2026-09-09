package my.github.MrxSiN.pixellauncherevolved.feature.wallpaper

import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * The launcher's wallpaper depth, as this module reaches it.
 *
 * One class knows where the depth is asked for and how to make the launcher ask
 * again; what the answer should be is decided elsewhere. See `HOOK_NOTES.md`
 * for the signatures this was read off.
 */
class LauncherDepth private constructor(
    /** `LauncherState.getDepth`: what a state answers when asked how deep it is. */
    val stateDepth: Method,
    /** `DepthController.setState`: where a state's depth reaches the wallpaper. */
    val applyState: Method,
    /** `DepthController.setStateWithAnimation`: the same, over a transition. */
    val applyStateOverTime: Method?,
    /**
     * `LauncherDepthController.blurWorkspaceDepthTargets`: decides again
     * whether the workspace is blurred along with the wallpaper, and puts that
     * answer on the workspace.
     */
    private val blurWorkspace: Method?,
    /** The home state, recognised by identity rather than by name. */
    val home: Any,
    /**
     * `BaseDepthControllerImpl.pauseBlursOnWindows`: the launcher switching its
     * own window blurs off for the length of an animation home.
     */
    val pauseBlurs: Method?,
    private val logger: Logger,
) {

    /** Asks the launcher to apply [state] again, for an answer that has changed. */
    fun reapply(controller: Any, state: Any) {
        runCatching { applyState.invoke(controller, state) }
            .onFailure { logger.warn("Unable to apply the wallpaper depth", it) }
    }

    /**
     * Asks the launcher to decide again whether the workspace is blurred.
     *
     * The launcher blurs its own workspace, not only the wallpaper, while the
     * app drawer is the state being left, and it puts a `RenderEffect` on the
     * workspace and the hotseat to do it. It makes that decision only when it
     * has applied a depth, and it applies one only when the depth has moved.
     * With this tweak on the depth stops moving on the last frame of the
     * transition, while the drawer is still the state being left, so the answer
     * that reaches the workspace is the one from that frame and nothing
     * afterwards corrects it.
     *
     * This decides nothing. It calls the launcher's own answer again, once the
     * state it reads has settled.
     */
    fun refreshBlur(controller: Any) {
        val decide = blurWorkspace ?: return

        runCatching { decide.invoke(controller) }
            .onFailure { logger.warn("Unable to settle the workspace blur", it) }
    }

    companion object {

        fun of(context: FeatureContext): LauncherDepth? {
            val state = context.findClass(LAUNCHER_STATE)
            val activityContext = context.findClass(ACTIVITY_CONTEXT)
            val controller = context.findClass(DEPTH_CONTROLLER)

            val stateDepth = if (state == null || activityContext == null) {
                null
            } else {
                Reflect.method(state, GET_DEPTH, activityContext)
            }
            val applyState = controller?.let { Reflect.method(it, SET_STATE, Any::class.java) }
            val home = state?.let { runCatching { Reflect.field(it, NORMAL)?.get(null) }.getOrNull() }

            if (stateDepth == null || applyState == null || home == null) {
                context.logger.warn("The launcher's wallpaper depth is unavailable")
                return null
            }

            keepTheAskReal(context, controller)

            val blurWorkspace = context.findClass(LAUNCHER_DEPTH_CONTROLLER)
                ?.let { Reflect.method(it, BLUR_WORKSPACE) }
            if (blurWorkspace == null) {
                context.logger.warn("The launcher's workspace blur cannot be settled; it may stay blurred")
            }

            val pauseBlurs = context.findClass(DEPTH_CONTROLLER_BASE)
                ?.let { Reflect.method(it, PAUSE_BLURS, Boolean::class.javaPrimitiveType!!) }

            if (pauseBlurs == null) {
                context.logger.warn("The launcher's blur pause is unreachable; the workspace may stay smeared")
            }

            return LauncherDepth(
                stateDepth = stateDepth,
                applyState = applyState,
                applyStateOverTime = animatedSetState(context, controller),
                blurWorkspace = blurWorkspace,
                pauseBlurs = pauseBlurs,
                home = home,
                logger = context.logger,
            )
        }

        /**
         * Deoptimizes the two methods that ask a state how deep it is.
         *
         * Both reach the state through an interface call to a method five code
         * units long, which ART is free to inline past a hook. These two decide
         * the depth the launcher rests at and the depth it animates towards;
         * the launcher asks elsewhere as well, but only to decide whether to
         * round the answer off.
         */
        private fun keepTheAskReal(context: FeatureContext, controller: Class<*>?) {
            val asked = listOfNotNull(
                controller?.let { Reflect.method(it, SET_STATE, Any::class.java) },
                animatedSetState(context, controller),
            )

            if (asked.size < 2) {
                context.logger.warn("Not every depth caller could be deoptimized; a hook may be inlined past")
            }
            asked.forEach(context.xposed::deoptimize)
        }

        private fun animatedSetState(context: FeatureContext, controller: Class<*>?): Method? {
            val baseState = context.findClass(BASE_STATE) ?: return null
            val config = context.findClass(ANIMATION_CONFIG) ?: return null
            val animation = context.findClass(PENDING_ANIMATION) ?: return null

            return controller?.let {
                Reflect.method(it, SET_STATE_WITH_ANIMATION, baseState, config, animation)
            }
        }

        private const val LAUNCHER_STATE = "com.android.launcher3.LauncherState"
        private const val ACTIVITY_CONTEXT = "com.android.launcher3.views.ActivityContext"
        private const val DEPTH_CONTROLLER = "com.android.launcher3.statehandlers.DepthController"
        private const val DEPTH_CONTROLLER_BASE = "com.android.quickstep.util.BaseDepthControllerImpl"
        private const val LAUNCHER_DEPTH_CONTROLLER =
            "com.android.launcher3.statehandlers.LauncherDepthController"
        private const val BASE_STATE = "com.android.launcher3.statemanager.BaseState"
        private const val ANIMATION_CONFIG = "com.android.launcher3.states.StateAnimationConfig"
        private const val PENDING_ANIMATION = "com.android.launcher3.anim.PendingAnimation"

        private const val GET_DEPTH = "getDepth"
        private const val SET_STATE = "setState"
        private const val SET_STATE_WITH_ANIMATION = "setStateWithAnimation"
        private const val BLUR_WORKSPACE = "blurWorkspaceDepthTargets"
        private const val PAUSE_BLURS = "pauseBlursOnWindows"
        private const val NORMAL = "NORMAL"
    }
}
