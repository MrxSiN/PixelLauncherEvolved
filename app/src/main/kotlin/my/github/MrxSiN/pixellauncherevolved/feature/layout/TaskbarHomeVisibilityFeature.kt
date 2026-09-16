package my.github.MrxSiN.pixellauncherevolved.feature.layout

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings as Tweaks
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Keeps the taskbar's answer to whether the launcher is in front from going stale.
 *
 * The taskbar asks that constantly — `isInLauncher(mState)` decides whether its
 * icons belong on the hotseat, whether the hotseat draws its own, and whether the
 * taskbar stashes itself away in an app — and the answer reaches it from the
 * shell rather than from the launcher:
 *
 * ```
 * IHomeTransitionListener.onHomeVisibilityChanged(isVisible, ...)
 *   -> HomeVisibilityState -> LauncherTaskbarUIController.onLauncherVisibilityChanged(boolean)
 *   -> TaskbarLauncherStateController.updateStateForFlag(FLAG_RESUMED, isVisible)
 * ```
 *
 * The shell reports changes, so an answer that was right when it was sent and
 * wrong a moment later is never taken back. That happens at both ends of a
 * launcher's life, and each has a moment where the launcher itself can prove it.
 *
 * **A restart.** The taskbar is built with the launcher already on screen and
 * resumed, records that during its own initialisation, and is then handed a
 * report belonging to the launcher it has just replaced — measured at 45 ms:
 *
 * ```
 * updateStateForFlag(1, true)  <- LauncherTaskbarUIController.init
 * updateStateForFlag(1, false) <- HomeVisibilityState$init$1$onHomeVisibilityChanged
 * ```
 *
 * The taskbar then spends the session believing the launcher is behind
 * something, and the hotseat starts empty: a launcher with a taskbar stops
 * drawing its own hotseat icons while it believes the taskbar's are covering
 * them, and the taskbar those were handed to is stashed, as a transient taskbar
 * is on home.
 *
 * **A quick switch between two apps.** The launcher is in front for the length
 * of the gesture and the shell says so; an app arrives instead, and no further
 * report is made, because home's visibility has not changed again:
 *
 * ```
 * stash.updateStateForFlag(FLAG_IN_APP, false)  <- the taskbar is told it is not in an app
 * reveal called                                 <- so its icons come out of the pill
 * state flag=1 value=true                       <- FLAG_RESUMED, left set
 * ```
 *
 * The taskbar is then drawn across the app that is in front, and stays there.
 *
 * **The Google feed and back.** That is an activity in the launcher's own task,
 * so home's visibility never changes as far as the shell is concerned, and the
 * taskbar is left with the launcher behind something. The hotseat stays empty,
 * for the same reason as after a restart.
 *
 * So: a report that the launcher is behind something is dropped while the
 * launcher is resumed and was only just built, and a launcher that has paused or
 * resumed says so when the taskbar disagrees. Everything else is the shell's to
 * report, because this answer also drives the taskbar's animation between home
 * and an app, and a launcher that states it whenever it likes animates the
 * taskbar at moments it never intended.
 */
class TaskbarHomeVisibilityFeature : LauncherFeature {

    override val id: String = "taskbar_home_visibility"

    /** The hooks are placed once, and only where a taskbar exists at all. */
    override val isLive: Boolean = false

    override fun isEnabled(settings: SettingsSource): Boolean =
        settings[Tweaks.TABLET_MODE] || settings[Tweaks.TASKBAR_ONLY]

    override fun install(context: FeatureContext) {
        val launcher = context.findClass(LAUNCHER)
        val taskbar = TaskbarLauncherView.of(context)

        if (launcher == null || taskbar == null) {
            context.logger.warn("The launcher is unavailable; the taskbar's view of it is left alone")
            return
        }

        StaleVisibilityReport(context, taskbar).install()
        LauncherLifecycle(context, taskbar, launcher).install()
    }

    private companion object {
        const val LAUNCHER = "com.android.launcher3.Launcher"
    }
}

/**
 * What the taskbar has been told about the launcher, and how to tell it.
 *
 * Every member is looked up once, so a launcher build that renames one of them
 * leaves the feature uninstalled rather than half working.
 */
private class TaskbarLauncherView private constructor(
    private val context: FeatureContext,
    val visibilityChanged: Method,
    val init: Method,
    private val stateControllerOf: Field,
    private val stateFlags: Field,
    private val isInLauncher: Method,
    private val interactorOf: Field,
    private val activityOf: Field,
    private val hasBeenResumed: Method,
    private val controllersOf: Field,
    private val stashControllerOf: Field,
    private val stashFlags: Field,
    private val updateStashFlag: Method,
    private val applyStashState: Method,
) {

    /** The controller built for the taskbar attached to the running launcher. */
    @Volatile
    private var controller: WeakReference<Any>? = null

    fun remember(instance: Any?) {
        controller = instance?.let(::WeakReference)
    }

    /** Whether the taskbar believes the launcher is in front, or null if unreadable. */
    fun believesLauncherIsInFront(instance: Any? = controller?.get()): Boolean? = runCatching {
        val state = stateControllerOf.get(instance ?: return null)
        isInLauncher.invoke(null, stateFlags.getInt(state)) as? Boolean
    }.getOrNull()

    /** Whether the launcher activity is between resume and pause. */
    fun launcherIsResumed(instance: Any? = controller?.get()): Boolean = runCatching {
        hasBeenResumed.invoke(activityOf.get(interactorOf.get(instance ?: return false))) == true
    }.getOrDefault(false)

    /**
     * States [inFront] to the taskbar, through the launcher's own entry point.
     *
     * Any argument past the first is passed false, which is what the launcher
     * itself passes for an ordinary report: the second says the launcher is
     * only visible behind a desktop, which suppresses `FLAG_RESUMED`, and the
     * third asks for the change without its animation. Both are exactly what
     * the one-argument entry point of `CP2A.260805.005` did.
     */
    fun tell(inFront: Boolean) {
        val instance = controller?.get() ?: return
        val arguments = Array<Any?>(visibilityChanged.parameterTypes.size) { it == 0 && inFront }

        runCatching { visibilityChanged.invoke(instance, *arguments) }
            .onFailure { context.logger.warn("Unable to tell the taskbar the launcher paused", it) }
    }

    /** Whether the taskbar counts itself as being in an app, or null if unreadable. */
    fun believesItIsInAnApp(): Boolean? = runCatching {
        stashFlags.getLong(stashController() ?: return null) and FLAG_IN_APP != 0L
    }.getOrNull()

    /** States that the taskbar is in an app, the way the launcher states it. */
    fun tellItIsInAnApp() {
        val stash = stashController() ?: return
        runCatching {
            updateStashFlag.invoke(stash, FLAG_IN_APP, true)
            applyStashState.invoke(stash)
        }.onFailure { context.logger.warn("Unable to tell the taskbar it is in an app", it) }
    }

    private fun stashController(): Any? = runCatching {
        stashControllerOf.get(controllersOf.get(controller?.get() ?: return null))
    }.getOrNull()

    companion object {

        /**
         * The entry point the shell's report of home visibility arrives through.
         *
         * `CP2A.260805.005` declared one, `onLauncherVisibilityChanged(boolean
         * isVisible)`. `CP3A.260905.009` takes two more booleans and has two
         * further overloads beside it — one with no arguments and one returning
         * an `Animator` — so the shape is asked for rather than the arity: the
         * void one that takes booleans and nothing else. Its first argument is
         * still the report, which is the only one this feature reads.
         */
        private fun visibilityChangedOn(controller: Class<*>): Method? = controller.declaredMethods
            .firstOrNull { method ->
                method.name == VISIBILITY_CHANGED &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isNotEmpty() &&
                    method.parameterTypes.all { it == Boolean::class.javaPrimitiveType }
            }
            ?.apply { isAccessible = true }

        fun of(context: FeatureContext): TaskbarLauncherView? {
            val controller = context.findClass(UI_CONTROLLER)
            val stateController = context.findClass(STATE_CONTROLLER)
            val interactor = context.findClass(INTERACTOR)
            val activity = context.findClass(BASE_ACTIVITY)

            val visibilityChanged = controller?.let(::visibilityChangedOn)
            val init = controller?.declaredMethods?.firstOrNull { it.name == INIT }
            val stateControllerOf = controller?.let { Reflect.field(it, STATE_CONTROLLER_FIELD) }
            val stateFlags = stateController?.let { Reflect.field(it, STATE_FLAGS) }
            val isInLauncher = stateController?.let {
                Reflect.method(it, IS_IN_LAUNCHER, Int::class.javaPrimitiveType!!)
            }
            val interactorOf = controller?.let { Reflect.field(it, LAUNCHER_FIELD) }
            val activityOf = interactor?.let { Reflect.field(it, ACTIVITY_FIELD) }
            val hasBeenResumed = activity?.let { Reflect.method(it, HAS_BEEN_RESUMED) }

            val controllers = context.findClass(CONTROLLERS)
            val stashController = context.findClass(STASH_CONTROLLER)
            val controllersOf = controller?.let { Reflect.field(it, CONTROLLERS_FIELD) }
            val stashControllerOf = controllers?.let { Reflect.field(it, STASH_CONTROLLER_FIELD) }
            val stashFlags = stashController?.let { Reflect.field(it, STASH_FLAGS) }
            val updateStashFlag = stashController?.let {
                Reflect.method(
                    it,
                    UPDATE_STASH_FLAG,
                    Long::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!,
                )
            }
            val applyStashState = stashController?.declaredMethods?.firstOrNull { method ->
                method.name == APPLY_STASH_STATE && method.parameterTypes.isEmpty()
            }

            if (visibilityChanged == null || init == null || stateControllerOf == null ||
                stateFlags == null || isInLauncher == null || interactorOf == null ||
                activityOf == null || hasBeenResumed == null || controllersOf == null ||
                stashControllerOf == null || stashFlags == null || updateStashFlag == null ||
                applyStashState == null
            ) {
                context.logger.warn(
                    "The taskbar's launcher visibility cannot be followed; the hotseat may start " +
                        "empty and the taskbar may stay out over an app",
                )
                return null
            }

            return TaskbarLauncherView(
                context,
                visibilityChanged,
                init,
                stateControllerOf,
                stateFlags,
                isInLauncher,
                interactorOf,
                activityOf,
                hasBeenResumed,
                controllersOf,
                stashControllerOf,
                stashFlags,
                updateStashFlag,
                applyStashState,
            )
        }

        private const val UI_CONTROLLER = "com.android.launcher3.taskbar.LauncherTaskbarUIController"
        private const val STATE_CONTROLLER =
            "com.android.launcher3.taskbar.TaskbarLauncherStateController"
        private const val INTERACTOR = "com.android.launcher3.LauncherInteractor"
        private const val BASE_ACTIVITY = "com.android.launcher3.BaseActivity"
        private const val VISIBILITY_CHANGED = "onLauncherVisibilityChanged"
        private const val INIT = "init"
        private const val STATE_CONTROLLER_FIELD = "mTaskbarLauncherStateController"
        private const val STATE_FLAGS = "mState"
        private const val IS_IN_LAUNCHER = "isInLauncher"
        private const val LAUNCHER_FIELD = "mLauncher"
        private const val ACTIVITY_FIELD = "launcher"
        private const val HAS_BEEN_RESUMED = "hasBeenResumed"
        private const val CONTROLLERS = "com.android.launcher3.taskbar.TaskbarControllers"
        private const val STASH_CONTROLLER = "com.android.launcher3.taskbar.TaskbarStashController"
        private const val CONTROLLERS_FIELD = "mControllers"
        private const val STASH_CONTROLLER_FIELD = "taskbarStashController"
        private const val STASH_FLAGS = "mState"
        private const val UPDATE_STASH_FLAG = "updateStateForFlag"
        private const val APPLY_STASH_STATE = "applyState"

        /** `TaskbarStashController.FLAG_IN_APP`, the first of its state bits. */
        private const val FLAG_IN_APP = 1L
    }
}

/** The report belonging to the launcher this one replaced, dropped. */
private class StaleVisibilityReport(
    private val context: FeatureContext,
    private val taskbar: TaskbarLauncherView,
) {

    /** When the taskbar's launcher controller was built, or zero. */
    @Volatile
    private var builtAt = 0L

    fun install() {
        context.xposed.hook(taskbar.init).intercept { chain ->
            chain.proceed().also {
                builtAt = SystemClock.uptimeMillis()
                taskbar.remember(chain.thisObject)
            }
        }

        context.xposed.hook(taskbar.visibilityChanged).intercept { chain ->
            val stale = chain.args.getOrNull(0) == false &&
                justBuilt() &&
                taskbar.launcherIsResumed(chain.thisObject)

            if (stale) null else chain.proceed()
        }

        context.logger.info("Taskbar: a launcher visibility report from the previous launcher is ignored")
    }

    private fun justBuilt(): Boolean {
        val built = builtAt
        return built != 0L && SystemClock.uptimeMillis() - built < GRACE_MILLIS
    }

    private companion object {
        /** The stale report was measured at 45 ms; this is room, not a guess at timing. */
        const val GRACE_MILLIS = 1_000L
    }
}

/**
 * The launcher's own lifecycle, stated to a taskbar that disagrees with it.
 *
 * Both directions, because a report the shell does not make leaves the taskbar
 * wrong either way round: on a quick switch it keeps a launcher that has gone in
 * front and draws itself over the app, and coming back from the Google feed —
 * which is an activity in the launcher's own task, so home's visibility never
 * changes as far as the shell is concerned — it keeps a launcher that is behind
 * something and the hotseat stays empty.
 *
 * Only where the taskbar disagrees, and only ever once per pause or resume, so a
 * launcher that agrees is left to animate its own transitions.
 */
private class LauncherLifecycle(
    private val context: FeatureContext,
    private val taskbar: TaskbarLauncherView,
    private val launcher: Class<*>,
) {

    private val handler = Handler(Looper.getMainLooper())

    private val paused = Runnable {
        if (taskbar.launcherIsResumed()) return@Runnable

        if (taskbar.believesLauncherIsInFront() == true) {
            context.logger.info("Taskbar: the launcher paused; taking back its place in front")
            taskbar.tell(false)
        }

        if (taskbar.believesItIsInAnApp() == false) {
            context.logger.info("Taskbar: the launcher paused; the taskbar is in an app")
            taskbar.tellItIsInAnApp()
        }
    }

    private val resumed = Runnable {
        if (!taskbar.launcherIsResumed()) return@Runnable

        if (taskbar.believesLauncherIsInFront() == false) {
            context.logger.info("Taskbar: the launcher resumed; giving it back its place in front")
            taskbar.tell(true)
        }
    }

    fun install() {
        context.hookAfter(launcher, ON_PAUSE) { _, _ -> state(paused) }
        context.hookAfter(launcher, ON_RESUME) { _, _ -> state(resumed) }

        context.logger.info("Taskbar: the launcher's own resumed state settles a taskbar that disagrees")
    }

    private fun state(repair: Runnable) {
        handler.removeCallbacks(repair)

        // On the next message, because the taskbar starts drawing itself out of
        // the pill 18 ms after a pause and a correction that arrives later is a
        // taskbar that appears and then collapses. Not inside the lifecycle call
        // itself, because applying the taskbar's state re-enters the launcher
        // that is calling it.
        handler.post(repair)

        // Again once the gesture has settled, for a state that changed after the
        // lifecycle call rather than before it.
        handler.postDelayed(repair, SETTLED_MILLIS)
    }

    private companion object {
        const val ON_PAUSE = "onPause"
        const val ON_RESUME = "onResume"
        const val SETTLED_MILLIS = 350L
    }
}
