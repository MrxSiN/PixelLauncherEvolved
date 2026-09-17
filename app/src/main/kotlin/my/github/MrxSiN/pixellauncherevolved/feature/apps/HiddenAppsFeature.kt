package my.github.MrxSiN.pixellauncherevolved.feature.apps

import android.view.View

import java.util.function.Predicate

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Leaves the chosen apps out of the app drawer.
 *
 * The launcher already has the mechanism: each tab of the drawer is set up with
 * a predicate saying which apps belong in it — one for the personal profile,
 * one for work — and the list is rebuilt through it whenever the apps change.
 * This wraps that predicate rather than replacing it, so a hidden app is
 * hidden and a work app is still a work app.
 *
 * The predicate asks the store each time it is called rather than closing over
 * an answer, so nothing has to be rebuilt to change what is hidden. What does
 * have to happen is the rebuild itself, and the launcher only rebuilds when the
 * installed apps change; so a change made in settings is applied when the
 * launcher comes back to the front, which is the moment a person returns from
 * having made it.
 *
 * Hiding an app hides it from the drawer, not from the device. An icon already
 * on the home screen or in the hotseat stays where it was put.
 */
class HiddenAppsFeature : LauncherFeature {

    override val compatibility = CompatibilityFeature.HIDDEN_APPS

    override val id: String = "app_drawer_hidden_apps"

    /** Nothing gates this: the predicate asks the store, and an empty store is a no. */
    override fun isEnabled(settings: SettingsSource): Boolean = true

    /** What the drawer was last rebuilt for. */
    private var applied: Set<String> = emptySet()

    override fun install(context: FeatureContext) {
        val holder = context.findClass(ADAPTER_HOLDER)
        val setup = holder?.let {
            Reflect.method(it, "setup", View::class.java, Predicate::class.java)
        }
        val appsList = context.findClass(APPS_LIST)
        val itemInfo = context.findClass(ITEM_INFO)
        val packageOf = itemInfo?.let { Reflect.method(it, "getTargetPackage") }
        val launcher = context.findClass(LAUNCHER)

        if (setup == null || appsList == null || packageOf == null || launcher == null) {
            context.logger.warn("The app drawer's list cannot be filtered in this launcher")
            return
        }

        val store = SharedPreferencesHiddenAppsStore(LauncherSettings.preferences(context.appContext))
        applied = store.hidden()

        context.xposed.hook(setup).intercept { chain ->
            runCatching { remember(chain.thisObject, holder, appsList) }
                .onFailure { context.logger.warn("The app drawer's list cannot be rebuilt on demand", it) }

            val theirs = chain.getArg(FILTER_ARGUMENT) as? Predicate<*>
            chain.proceed(
                arrayOf(chain.getArg(VIEW_ARGUMENT), shown(theirs, store, packageOf, context)),
            )
        }

        // Returning from Home settings resumes the launcher, which is where a
        // change made there becomes a change on screen.
        context.hookAfter(launcher, ON_RESUME) { _, _ ->
            val hidden = store.hidden()
            if (hidden != applied) {
                applied = hidden
                runCatching { AppDrawerList.rebuild() }
                    .onFailure { context.logger.warn("Unable to rebuild the app drawer's list", it) }
            }
        }

        context.logger.info("App drawer: hidden apps are left out of the list")
    }

    /**
     * The launcher's own answer, with the hidden apps taken out.
     *
     * Their predicate is null for a drawer with nothing to separate, so the
     * wrapper stands alone in that case rather than refusing to filter.
     */
    private fun shown(
        theirs: Predicate<*>?,
        store: HiddenAppsStore,
        packageOf: java.lang.reflect.Method,
        context: FeatureContext,
    ): Predicate<Any?> = Predicate { info ->
        @Suppress("UNCHECKED_CAST")
        val belongsHere = (theirs as? Predicate<Any?>)?.test(info) ?: true

        // While the apps are being picked, every one of them has to be on
        // screen: a hidden app you cannot see is a hidden app you cannot get
        // back.
        belongsHere && (HideAppsSelection.isSelecting || runCatching {
            val name = packageOf.invoke(info) as? String
            name == null || name !in store.hidden()
        }.getOrElse { error ->
            context.logger.warn("Unable to read an app's package; showing it", error)
            true
        })
    }

    private fun remember(adapterHolder: Any?, holder: Class<*>, appsList: Class<*>) {
        val list = adapterHolder?.let { Reflect.field(holder, APPS_LIST_FIELD)?.get(it) } ?: return
        Reflect.field(appsList, STORE_FIELD)?.get(list)?.let(AppDrawerList::remember)
    }

    private companion object {
        const val ADAPTER_HOLDER =
            "com.android.launcher3.allapps.ActivityAllAppsContainerView\$AdapterHolder"
        const val APPS_LIST = "com.android.launcher3.allapps.AlphabeticalAppsList"
        const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"
        const val LAUNCHER = "com.android.launcher3.Launcher"

        const val APPS_LIST_FIELD = "mAppsList"
        const val STORE_FIELD = "mAllAppsStore"
        const val ON_RESUME = "onResume"

        /** `setup(View rv, Predicate<ItemInfo> filter)`. */
        const val VIEW_ARGUMENT = 0
        const val FILTER_ARGUMENT = 1
    }
}
