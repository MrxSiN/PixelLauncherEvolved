package my.github.MrxSiN.pixellauncherevolved.feature.layout

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.provider.Settings
import android.view.View
import android.view.ViewGroup

import java.lang.ref.WeakReference

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings as Tweaks
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Keeps the taskbar out of the app's own transition into Overview.
 *
 * Swiping from an app into Overview sometimes drew the taskbar across the
 * bottom of the shrinking app card, a few hundred pixels above where it
 * belongs, before it snapped back into place as the transition settled.
 *
 * The taskbar had not moved. The window manager had moved it: the taskbar is
 * the display's `TYPE_NAVIGATION_BAR` window, and on a phone
 * `RecentsAnimationController.attachNavigationBarToApp()` reparents that window
 * under the app's task for the length of a recents animation, so the navigation
 * buttons travel with the app. Measured on a Pixel 8 Pro, the taskbar window's
 * frame during such a transition is the app's own:
 *
 * ```
 * taskbar=[28 2409 1316 2894]   chrome=[28 27 1316 2894]     // reparented
 * taskbar=[0 2485 1344 2992]    chrome=[54 27 1328 2863]     // left alone
 * ```
 *
 * Real tablets never see it, because they turn that behaviour off with
 * `config_attachNavBarToAppDuringTransition`; a phone with gesture navigation
 * never sees it either, because that window is empty there. Give the phone a
 * taskbar and the same window is suddenly full of icons. It happens with tablet
 * mode as readily as with tablet taskbar only, so it is not a consequence of
 * how either builds its device profile.
 *
 * Nothing in the launcher is told that this happened — the recents animation
 * targets and the transition it is handed carry no navigation bar — so the
 * taskbar cannot be put back where it belongs. It can be left out of the
 * transition instead: hidden as the animation starts, and shown again once two
 * separate things have both happened.
 *
 * Both, because they are not the same moment. The launcher's own taskbar
 * animation for the transition ends first; the window manager gives the window
 * back a frame or two later. Showing on the first of them alone put a fully
 * drawn taskbar across the bottom of the settled Overview card for exactly that
 * gap, which is the flash this used to leave behind.
 *
 * The way home is deliberately untouched. Its taskbar icons travel to the
 * hotseat and that hand-off is worth watching, so the moment the launcher says
 * it is heading somewhere the taskbar aligns with, this stands down — measured
 * in the same millisecond the animation starts.
 */
class TaskbarTransitionFeature : LauncherFeature {

    override val id: String = "taskbar_transition"

    /** The hooks are placed once, and only where a taskbar exists at all. */
    override val isLive: Boolean = false

    override fun isEnabled(settings: SettingsSource): Boolean =
        settings[Tweaks.TABLET_MODE] || settings[Tweaks.TASKBAR_ONLY]

    override fun install(context: FeatureContext) {
        val window = TaskbarWindow(context)

        window.followRoot()
        window.hideWhileRecentsAnimates()
        window.followTheWindowHandBack()
        window.showWhenTheLauncherSettles()

        context.logger.info("Taskbar: left out of the app's transition into Overview")
    }
}

/**
 * The taskbar's own window, and whether it is currently worth drawing.
 *
 * Alpha is set on the window's root view rather than on anything inside it,
 * because everything inside is already animated by the launcher and would
 * fight for the same property.
 */
private class TaskbarWindow(private val context: FeatureContext) {

    /** Held weakly: the window is rebuilt on a configuration change. */
    @Volatile
    private var root: WeakReference<View>? = null

    /**
     * Set the moment the animation is reported, not when the view is touched.
     *
     * The report arrives off the UI thread, and the launcher's own transition
     * is applied on the UI thread immediately afterwards. Deciding to hide has
     * to be visible to that thread by then, or the transition it needs to wait
     * for will have gone past.
     */
    @Volatile
    private var hidden = false

    /** The launcher's own taskbar animation for this transition has finished. */
    @Volatile
    private var launcherSettled = false

    /** The window manager has taken the navigation bar back off the app. */
    @Volatile
    private var windowReturned = false

    /**
     * Whether the hand back can be observed at all.
     *
     * When it cannot, the taskbar is shown as soon as the launcher settles,
     * which is what this did before the hand back was followed. A launcher
     * build that renamed the call is worth one flash, not a taskbar that stays
     * invisible until the safety net fires.
     */
    private var followsTheWindow = false

    /** Counts transitions, so a late safety net knows it belongs to an old one. */
    @Volatile
    private var transitions = 0

    fun followRoot() {
        val taskbarView = requireNotNull(
            context.findClass("com.android.launcher3.taskbar.TaskbarView"),
        )

        context.hookAfter(taskbarView, "onAttachedToWindow") { host, _ ->
            var current: View? = host as View
            while (current?.parent is ViewGroup) current = current.parent as ViewGroup
            root = current?.let(::WeakReference)
        }
    }

    /**
     * The moment the window manager may have taken the window away.
     *
     * `RecentsAnimationCallbacks.onAnimationStart` is the launcher's own first
     * word about a recents animation, and the reparenting has already happened
     * by then.
     */
    fun hideWhileRecentsAnimates() {
        val callbacks = requireNotNull(
            context.findClass("com.android.quickstep.RecentsAnimationCallbacks"),
        )

        for (method in callbacks.declaredMethods) {
            when (method.name) {
                "onAnimationStart" -> context.xposed.hook(method).intercept { chain ->
                    hide()
                    chain.proceed()
                }

                // Whatever else happens, the window is the launcher's again by
                // the time the animation it belonged to is over.
                "onAnimationCanceled", "onAnimationFinished" ->
                    context.xposed.hook(method).intercept { chain ->
                        show()
                        chain.proceed()
                    }
            }
        }
    }

    /**
     * The moment the window manager gives the window back.
     *
     * The launcher asks for it through
     * `RecentsAnimationController.detachNavigationBarFromApp(boolean)`, which
     * only posts the request to a background thread. What actually crosses to
     * the window manager is the compatibility wrapper below, and that one is a
     * plain binder call rather than a one-way: by the time it returns, the
     * navigation bar has been put back where it belongs and only its fade in is
     * still to come. That return is therefore the earliest moment the taskbar
     * can be drawn without being drawn on the app's card.
     */
    fun followTheWindowHandBack() {
        val compat = context.findClass(RECENTS_ANIMATION_COMPAT)
        val detach = compat?.let {
            Reflect.method(it, "detachNavigationBarFromApp", Boolean::class.javaPrimitiveType!!)
        }

        if (detach == null) {
            context.logger.warn(
                "The navigation bar hand back cannot be followed; the taskbar will be shown as " +
                    "soon as the launcher settles",
            )
            return
        }

        followsTheWindow = true
        context.xposed.hook(detach).intercept { chain ->
            chain.proceed().also {
                windowReturned = true
                settle()
            }
        }
    }

    /**
     * Shows the taskbar again as soon as it is safe.
     *
     * `TaskbarLauncherStateController.applyState` is called once per transition
     * with the animation that carries the taskbar through it, and answers two
     * questions at once: where the launcher is heading, and when it will have
     * arrived.
     */
    fun showWhenTheLauncherSettles() {
        val controller = requireNotNull(
            context.findClass("com.android.launcher3.taskbar.TaskbarLauncherStateController"),
        )
        val launcherState = requireNotNull(Reflect.field(controller, "mLauncherState"))
        val state = requireNotNull(context.findClass("com.android.launcher3.LauncherState"))
        val alignsWithHotseat = requireNotNull(
            Reflect.method(state, "isTaskbarAlignedWithHotseat"),
        )

        val applyState = controller.declaredMethods.firstOrNull {
            it.name == "applyState" && it.parameterTypes.size == 2
        }

        if (applyState == null) {
            context.logger.warn("Taskbar transitions cannot be followed; leaving the taskbar alone")
            return
        }

        context.xposed.hook(applyState).intercept { chain ->
            val result = chain.proceed()

            if (hidden) {
                runCatching {
                    val heading = launcherState.get(chain.thisObject)
                    val toHotseat = heading != null && alignsWithHotseat.invoke(heading) == true

                    when {
                        // Home. The icons travel to the hotseat, so stand down.
                        toHotseat -> show()

                        result is Animator -> result.addListener(
                            object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) = settled()
                            },
                        )

                        // No animation to wait for means there is nothing to hide from.
                        else -> settled()
                    }
                }.onFailure {
                    context.logger.warn("Unable to read the taskbar transition; showing it", it)
                    show()
                }
            }

            result
        }
    }

    /** The launcher has arrived. The window may still be the app's. */
    private fun settled() {
        launcherSettled = true
        settle()
    }

    private fun settle() {
        if (launcherSettled && windowReturned) show()
    }

    private fun hide() {
        val view = root?.get() ?: return
        if (hidden) return

        hidden = true
        launcherSettled = false
        windowReturned = !followsTheWindow
        val transition = ++transitions

        view.post { view.alpha = 0f }
        // Numbered, so a safety net left over from an earlier transition cannot
        // cut a later one short.
        view.postDelayed({ if (transition == transitions) show() }, safetyMillis(view))
    }

    private fun show() {
        if (!hidden) return
        hidden = false

        val view = root?.get() ?: return
        view.post { view.alpha = 1f }
    }

    /**
     * A last resort, so a missed signal cannot leave the taskbar invisible.
     *
     * Scaled by the animator setting, because everything it is racing is scaled
     * by it too, and a developer running animations at 20x would otherwise see
     * the taskbar reappear in the middle of the transition.
     */
    private fun safetyMillis(view: View): Long {
        val scale = runCatching {
            Settings.Global.getFloat(
                view.context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)

        return (SAFETY_MILLIS * maxOf(scale, 1f)).toLong()
    }

    private companion object {
        const val SAFETY_MILLIS = 2_000f

        const val RECENTS_ANIMATION_COMPAT =
            "com.android.systemui.shared.system.RecentsAnimationControllerCompat"
    }
}
