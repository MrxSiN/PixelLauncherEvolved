package my.github.MrxSiN.pixellauncherevolved.feature.search

import android.content.Intent
import android.view.View

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Opens a tapped Web Search result in the app the person chose.
 *
 * The launcher answers that tap with one intent of its own — a Google app
 * action it builds nowhere else — so the tap is recognised by that action
 * rather than by which row was under the finger, and the same recognition
 * covers pressing enter in the search box, which the launcher treats as
 * tapping the first suggestion.
 *
 * The redirect happens at the launcher's own launch call, which is handed the
 * result as well as the intent. That matters: the suggestion someone tapped is
 * rarely what they typed — typing "weather" and tapping "weather tomorrow"
 * must search for the second — and the result's title is the only place the
 * chosen words are still readable, the query itself being packed into an
 * extra meant for the Google app.
 *
 * What replaces the intent is a link to the results, not a query handed over,
 * so the app opens them on the first tap rather than offering to search again.
 *
 * Live: the choice is read as the tap is handled, so changing it needs no
 * launcher restart. Nothing chosen, or an app since uninstalled, leaves the
 * launcher's own answer exactly as it was.
 */
class WebSearchAppFeature : LauncherFeature {

    override val compatibility = CompatibilityFeature.APP_DRAWER_SEARCH

    override val id: String = "app_drawer_search_web_app"

    /** Nothing gates this: each tap asks the store, and an empty store is a no. */
    override fun isEnabled(settings: SettingsSource): Boolean = true

    override fun install(context: FeatureContext) {
        val launcher = context.findClass(LAUNCHER)
        val itemInfo = context.findClass(ITEM_INFO)
        val start = if (launcher == null || itemInfo == null) {
            null
        } else {
            Reflect.method(launcher, "startActivitySafely", View::class.java, Intent::class.java, itemInfo)
        }
        val titleOf = itemInfo?.let { Reflect.field(it, "title") }

        if (start == null || titleOf == null) {
            context.logger.warn("Web Search results cannot be redirected in this launcher")
            return
        }

        val store = SharedPreferencesWebSearchAppStore(LauncherSettings.preferences(context.appContext))
        val apps = WebSearchApps(context.appContext.packageManager)

        context.xposed.hook(start).intercept { chain ->
            val replacement = runCatching {
                redirect(chain.getArg(INTENT_ARGUMENT), chain.getArg(ITEM_ARGUMENT), store, apps, titleOf)
            }.onFailure {
                context.logger.warn("Unable to open the Web Search result in the chosen app", it)
            }.getOrNull()

            if (replacement == null) {
                chain.proceed()
            } else {
                context.logger.info("Web Search opened in ${replacement.`package`}")
                chain.proceed(arrayOf(chain.getArg(VIEW_ARGUMENT), replacement, chain.getArg(ITEM_ARGUMENT)))
            }
        }

        context.logger.info("App drawer: Web Search results follow the chosen app")
    }

    /** The intent to open instead, or null to leave the launcher's own alone. */
    private fun redirect(
        intent: Any?,
        item: Any?,
        store: WebSearchAppStore,
        apps: WebSearchApps,
        titleOf: java.lang.reflect.Field,
    ): Intent? {
        if ((intent as? Intent)?.action != WEB_SEARCH_ACTION) return null

        val chosen = store.chosen() ?: return null
        val query = (item?.let { titleOf.get(it) } as? CharSequence)?.takeIf { it.isNotBlank() }
            ?: return null

        return apps.searchIn(chosen, query)
    }

    private companion object {
        const val LAUNCHER = "com.android.launcher3.uioverrides.QuickstepLauncher"
        const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"

        /**
         * The action the launcher builds for a tapped Web Search result, and
         * for nothing else in the app drawer.
         */
        const val WEB_SEARCH_ACTION = "com.google.android.PIXEL_SEARCH"

        /** `startActivitySafely(View v, Intent intent, ItemInfo item)`. */
        const val VIEW_ARGUMENT = 0
        const val INTENT_ARGUMENT = 1
        const val ITEM_ARGUMENT = 2
    }
}
