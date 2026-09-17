package my.github.MrxSiN.pixellauncherevolved.feature.apps

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout

import java.lang.ref.WeakReference

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Chooses the apps to hide in the app drawer itself.
 *
 * A list of names is a poor way to pick apps: a phone has a few hundred of
 * them, and the thing a person recognises is the icon. So Home settings sends
 * them to the drawer instead, every icon grows a tick, and tapping one takes it
 * rather than opening it. A button in the corner ends it.
 *
 * Three hooks carry that. The icon draws its own tick, because the drawer's
 * views are recycled and anything hung on a particular view would be handed to
 * the wrong app on the next scroll. The tap is taken at `performClick`, which
 * is where a view decides to act on being clicked, so the launcher's own
 * listener is never reached and never has to be replaced. And the state the
 * launcher settles into says when the drawer has been left, which is a person
 * changing their mind.
 *
 * Nothing is written until the button is pressed. Leaving any other way keeps
 * what was already hidden.
 */
class HideAppsPickerFeature : LauncherFeature {

    override val compatibility = CompatibilityFeature.HIDDEN_APPS

    override val id: String = "app_drawer_hide_apps_picker"

    /** Nothing gates this: the hooks do nothing at all unless someone is choosing. */
    override fun isEnabled(settings: SettingsSource): Boolean = true

    private var launcher: WeakReference<Activity>? = null
    private var reachedDrawer = false
    private lateinit var hidden: HiddenAppsStore
    private var stateManagerOf: java.lang.reflect.Method? = null
    private var goToState: java.lang.reflect.Method? = null

    override fun install(context: FeatureContext) {
        val icon = context.findClass(ICON_VIEW)
        val itemInfo = context.findClass(ITEM_INFO)
        val appInfo = context.findClass(APP_INFO)
        val launcherClass = context.findClass(LAUNCHER)
        val state = context.findClass(LAUNCHER_STATE)

        val onDraw = icon?.let { Reflect.method(it, "onDraw", Canvas::class.java) }
        val iconBounds = icon?.let { Reflect.method(it, "getIconBounds", Rect::class.java) }
        val performClick = Reflect.method(View::class.java, "performClick")
        val packageOf = itemInfo?.let { Reflect.method(it, "getTargetPackage") }
        val allApps = state?.let { runCatching { Reflect.field(it, ALL_APPS)?.get(null) }.getOrNull() }

        val baseState = context.findClass(BASE_STATE)
        val stateful = context.findClass(STATEFUL_CONTAINER)
        val manager = context.findClass(STATE_MANAGER)
        stateManagerOf = stateful?.let { Reflect.method(it, "getStateManager") }
        goToState = if (manager == null || baseState == null) {
            null
        } else {
            Reflect.method(manager, "goToState", baseState)
        }

        if (onDraw == null || iconBounds == null || performClick == null ||
            packageOf == null || appInfo == null || launcherClass == null || allApps == null
        ) {
            context.logger.warn("Apps cannot be chosen from the app drawer in this launcher")
            return
        }

        val checkmark = AppIconCheckmark(context.appContext)
        hidden = SharedPreferencesHiddenAppsStore(LauncherSettings.preferences(context.appContext))
        val bounds = Rect()

        context.xposed.hook(onDraw).intercept { chain ->
            chain.proceed().also {
                val view = chain.thisObject as? View
                val name = view?.let { drawer -> chosenPackage(drawer, appInfo, packageOf) }
                if (view != null && name != null) {
                    runCatching {
                        iconBounds.invoke(view, bounds)
                        checkmark.draw(
                            (chain.getArg(0) as Canvas),
                            bounds,
                            HideAppsSelection.isChosen(name),
                        )
                    }.onFailure { error -> context.logger.warn("Unable to mark an app icon", error) }
                }
            }
        }

        context.xposed.hook(performClick).intercept { chain ->
            val view = chain.thisObject as? View
            val name = view?.let { chosenPackage(it, appInfo, packageOf) }

            if (name == null) {
                chain.proceed()
            } else {
                // Taken instead of opened, and the icon is asked to draw the
                // tick it has just been given.
                HideAppsSelection.toggle(name)
                view.invalidate()
                true
            }
        }

        context.hookAfter(launcherClass, ON_RESUME) { activity, _ ->
            if (activity is Activity) launcher = WeakReference(activity)
            if (HideAppsSelection.isSelecting) {
                runCatching { openDrawer(activity, allApps, context.logger) }
                    .onFailure { context.logger.warn("Unable to open the app drawer to choose apps", it) }
            }
        }

        val settled = baseState?.let {
            Reflect.method(context.findClass(QUICKSTEP_LAUNCHER) ?: launcherClass, "onStateSetEnd", it)
        }
        if (settled == null) {
            context.logger.warn("Leaving the app drawer cannot be noticed; choosing will not end itself")
        } else {
            context.xposed.hook(settled).intercept { chain ->
                chain.proceed().also {
                    if (HideAppsSelection.isSelecting) {
                        onSettled(chain.getArg(0) === allApps, context.logger)
                    }
                }
            }
        }

        context.logger.info("App drawer: apps are chosen from the drawer itself")
    }

    /**
     * The package this view stands for, or null when it is not an app being
     * offered for hiding.
     *
     * Only the drawer's own icons answer. A workspace icon carries a different
     * kind of item, so the home screen behind the drawer is left alone.
     */
    private fun chosenPackage(view: View, appInfo: Class<*>, packageOf: java.lang.reflect.Method): String? {
        if (!HideAppsSelection.isSelecting) return null

        val item = view.tag
        if (!appInfo.isInstance(item)) return null

        return runCatching { packageOf.invoke(item) as? String }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Brings the drawer up, once, when settings has asked for it. */
    private fun openDrawer(activity: Any?, allApps: Any, logger: Logger) {
        val container = activity ?: return
        val dragLayer = dragLayerOf(container) ?: run {
            logger.warn("The launcher has no drag layer to put the confirm button in")
            return
        }
        if (dragLayer.findViewWithTag<View>(HideAppsButton.VIEW_TAG) != null) return

        reachedDrawer = false
        HideAppsButton().addTo(dragLayer) { confirm() }
        HideAppsSelection.onChanged = { dragLayer.invalidate() }

        // The apps already hidden have to come back for the length of this, or
        // there would be no way to un-hide one.
        AppDrawerList.rebuild()

        val stateManager = stateManagerOf?.invoke(container)
        if (stateManager == null || goToState == null) {
            logger.warn("The app drawer cannot be opened; swipe up to choose apps")
            return
        }
        goToState!!.invoke(stateManager, allApps)
    }

    /**
     * Choosing ends when the drawer does.
     *
     * The drawer being reached has to be seen first: the settle that opens it
     * would otherwise read as the settle that closed it.
     */
    private fun onSettled(isDrawer: Boolean, logger: Logger) {
        if (isDrawer) {
            reachedDrawer = true
            return
        }
        if (!reachedDrawer) return

        logger.info("Choosing apps to hide was left; nothing changed")
        finish(keep = null)
    }

    private fun confirm() = finish(keep = HideAppsSelection.confirm())

    /** [keep] is the answer to write, or null when there is no answer to write. */
    private fun finish(keep: Set<String>?) {
        HideAppsSelection.cancel()
        reachedDrawer = false

        val activity = launcher?.get()
        activity?.let { dragLayerOf(it)?.let(HideAppsButton()::removeFrom) }

        if (keep != null) hidden.hide(keep)
        // Either way the list is wrong: it has been showing every app, hidden
        // ones included, for as long as the choosing lasted.
        AppDrawerList.rebuild()
        // Whatever is still on screen has ticks on it that no longer apply.
        activity?.window?.decorView?.invalidate()
    }

    private fun dragLayerOf(activity: Any): FrameLayout? = runCatching {
        Reflect.method(activity.javaClass, "getDragLayer")?.invoke(activity) as? FrameLayout
    }.getOrNull()

    private companion object {
        const val ICON_VIEW = "com.android.launcher3.BubbleTextView"
        const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"
        const val APP_INFO = "com.android.launcher3.model.data.AppInfo"
        const val LAUNCHER = "com.android.launcher3.Launcher"
        const val QUICKSTEP_LAUNCHER = "com.android.launcher3.uioverrides.QuickstepLauncher"
        const val LAUNCHER_STATE = "com.android.launcher3.LauncherState"
        const val BASE_STATE = "com.android.launcher3.statemanager.BaseState"
        const val STATEFUL_CONTAINER = "com.android.launcher3.statemanager.StatefulContainer"
        const val STATE_MANAGER = "com.android.launcher3.statemanager.StateManager"

        const val ALL_APPS = "ALL_APPS"
        const val ON_RESUME = "onResume"
    }
}
