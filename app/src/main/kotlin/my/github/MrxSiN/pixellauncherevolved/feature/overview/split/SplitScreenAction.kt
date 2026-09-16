package my.github.MrxSiN.pixellauncherevolved.feature.overview.split

import android.content.Context
import android.view.View

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * Starts the launcher's own split-screen selection from a task card.
 *
 * The launcher already does this from the card's own menu:
 *
 * ```
 * com.android.quickstep.TaskShortcutFactory$SplitSelectSystemShortcut.onClick(View)
 *   container.getTaskView().getRecentsView().initiateSplitSelect(
 *       container, splitPositionOption.stagePosition, LauncherEvent…)
 * ```
 *
 * This takes the same path rather than inventing one, so what follows — the
 * other half being chosen, the toast when an app refuses to split, the
 * animation — is the launcher's own behaviour and stays right when it changes.
 *
 * Which half the first app takes has to be worked out and passed. The shorter
 * `initiateSplitSelect(TaskContainer)` asks the orientation handler for a
 * default position, and on a phone that throws: `Default position available
 * only for large screens`. The handler does answer which position it offers,
 * which is what the menu asks it, so that is what is asked here.
 *
 * ```
 * com.android.quickstep.orientation.RecentsPagedOrientationHandler
 *   SplitConfigurationOptions$SplitPositionOption getSplitPositionOption(DeviceProfile)
 * ```
 *
 * Up to Android 17 `CP2A.260805.005` that was `getSplitPositionOptions`,
 * answering a list to take the first of. `CP3A.260905.009` answers the one
 * position directly.
 *
 * Overview is not put away first, unlike the bubble: the second app is chosen
 * in Overview, so closing it would take away the screen the action needs.
 */
class SplitScreenAction(private val logger: Logger) {

    /** Whether this card offers a task that split selection can start from. */
    fun canSplit(taskView: View): Boolean = container(taskView) != null

    fun run(taskView: View): Boolean = try {
        val container = container(taskView)
        val recentsView = container?.let { recentsView(taskView) }

        if (recentsView == null) {
            logger.warn("Launcher exposes no split selection for this card")
            false
        } else {
            start(recentsView, container, taskView.context)
        }
    } catch (error: Throwable) {
        logger.warn("Unable to start split screen from an Overview card", error)
        false
    }

    /**
     * Asks for the half the launcher would put this app in, and takes it.
     *
     * A build that answers nothing useful is asked the short way instead, which
     * is what a large screen would have done with the answer anyway.
     */
    private fun start(recentsView: Any, container: Any, context: Context): Boolean {
        val stagePosition = stagePosition(recentsView, context)

        val method = recentsView.javaClass.methods.firstOrNull { candidate ->
            candidate.name == INITIATE_SPLIT_SELECT &&
                candidate.parameterTypes.size == (if (stagePosition == null) 1 else 3) &&
                candidate.parameterTypes[0].isInstance(container)
        }

        if (method == null) {
            logger.warn("Launcher exposes no split selection this module can call")
            return false
        }

        if (stagePosition == null) {
            method.invoke(recentsView, container)
        } else {
            method.invoke(recentsView, container, stagePosition, event(stagePosition, context))
        }

        return true
    }

    /**
     * The stage a first app is put in, as the launcher's own menu decides it.
     *
     * The orientation handler is asked which position it offers for this device
     * profile, which is the question the launcher's own menu asks it.
     */
    private fun stagePosition(recentsView: Any, context: Context): Int? = runCatching {
        val handler = Reflect.method(recentsView.javaClass, GET_ORIENTATION_HANDLER)
            ?.invoke(recentsView)
            ?: return null

        val profile = deviceProfile(context) ?: return null
        val option = handler.javaClass.methods
            .firstOrNull { it.name == GET_SPLIT_POSITION_OPTION && it.parameterTypes.size == 1 }
            ?.invoke(handler, profile)
            ?: return null

        Reflect.field(option.javaClass, STAGE_POSITION)?.getInt(option)
    }.getOrNull()

    private fun deviceProfile(context: Context): Any? = runCatching {
        val containerType = Class.forName(CONTAINER_CLASS, false, context.classLoader)
        val container = Reflect.method(containerType, CONTAINER_FROM_CONTEXT, Context::class.java)
            ?.invoke(null, context)
            ?: return null

        Reflect.method(container.javaClass, GET_DEVICE_PROFILE)?.invoke(container)
    }.getOrNull()

    /**
     * What the launcher logs for this action, which it insists on being given.
     *
     * The events are the ones its own menu reports, named for the half the app
     * lands in. They are read by name because this module cannot link against
     * the enum.
     */
    private fun event(stagePosition: Int, context: Context): Any? = runCatching {
        val name = if (stagePosition == STAGE_TOP_OR_LEFT) EVENT_LEFT_TOP else EVENT_RIGHT_BOTTOM

        @Suppress("UNCHECKED_CAST")
        val type = Class.forName(LAUNCHER_EVENT_CLASS, false, context.classLoader)
            as Class<out Enum<*>>

        type.enumConstants?.firstOrNull { it.name == name }
    }.getOrNull()

    /**
     * The first of the card's task containers.
     *
     * A card already showing a split pair has one container per app; the first
     * is the one the launcher's own menu acts on, and a pair that cannot be
     * split again is refused further along rather than here.
     */
    private fun container(taskView: View): Any? = runCatching {
        (Reflect.method(taskView.javaClass, GET_TASK_CONTAINERS)?.invoke(taskView) as? List<*>)
            ?.firstOrNull()
    }.getOrNull()

    private fun recentsView(taskView: View): Any? = runCatching {
        Reflect.method(taskView.javaClass, GET_RECENTS_VIEW)?.invoke(taskView)
    }.getOrNull()

    private companion object {
        const val GET_TASK_CONTAINERS = "getTaskContainers"
        const val GET_RECENTS_VIEW = "getRecentsView"
        const val INITIATE_SPLIT_SELECT = "initiateSplitSelect"
        const val GET_ORIENTATION_HANDLER = "getPagedOrientationHandler"
        const val GET_SPLIT_POSITION_OPTION = "getSplitPositionOption"
        const val STAGE_POSITION = "stagePosition"

        const val CONTAINER_CLASS = "com.android.quickstep.views.RecentsViewContainer"
        const val CONTAINER_FROM_CONTEXT = "containerFromContext"
        const val GET_DEVICE_PROFILE = "getDeviceProfile"

        const val LAUNCHER_EVENT_CLASS = "com.android.launcher3.logging.StatsLogManager\$LauncherEvent"
        const val EVENT_LEFT_TOP = "LAUNCHER_APP_ICON_MENU_SPLIT_LEFT_TOP"
        const val EVENT_RIGHT_BOTTOM = "LAUNCHER_APP_ICON_MENU_SPLIT_RIGHT_BOTTOM"

        /** `SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT`. */
        const val STAGE_TOP_OR_LEFT = 0
    }
}
