package my.github.MrxSiN.pixellauncherevolved.feature.search

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.feature.apps.HiddenAppsStore
import my.github.MrxSiN.pixellauncherevolved.feature.apps.SharedPreferencesHiddenAppsStore
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Keeps parts of the app drawer's search results off the screen.
 *
 * The results are not the launcher's own work: it asks for them and draws what
 * comes back, in the grouping it was given. So there is no view to hide and no
 * adapter position to skip — the results are taken out on the way in, and the
 * launcher then lays out a shorter list as if that is all there ever was.
 *
 * They arrive over two channels, the platform's search service for what is on
 * the device and the Google app's own for web suggestions, and the two are
 * merged before anything is drawn. The merge is what this hooks: the one call
 * that hands the finished list to the app drawer. Both channels are covered by
 * it, and it is a launcher class rather than a Google one, so its name survives
 * the shrinker.
 *
 * Apps hidden from the drawer are taken out here too. An app that is hidden
 * from the list but comes back the moment its name is typed is not hidden.
 *
 * Live: the settings are read as each list arrives, so switching one changes
 * the next keystroke's results.
 */
class AppDrawerSearchFeature : LauncherFeature {

    override val id: String = "app_drawer_search_results"

    override fun isEnabled(settings: SettingsSource): Boolean =
        hiddenSearchResults(settings).isNotEmpty()

    override fun install(context: FeatureContext) {
        val appsView = context.findClass(ALL_APPS_VIEW)
        val show = appsView?.let {
            Reflect.method(it, "setSearchResults", ArrayList::class.java)
        }
        val targets = SearchTargets(context)

        if (show == null || targets == null) {
            context.logger.warn("The app drawer's search results cannot be filtered on this device")
            return
        }

        val items = SearchResultItems(targets)
        val apps = SharedPreferencesHiddenAppsStore(LauncherSettings.preferences(context.appContext))

        context.xposed.hook(show).intercept { chain ->
            val results = chain.getArg(RESULTS_ARGUMENT) as? ArrayList<*>
            val kept = runCatching { keep(results, items, context.settings, apps) }
                .onFailure { context.logger.warn("Unable to filter the app drawer's search results", it) }
                .getOrDefault(results)

            // Passed on whole whenever nothing was taken out, so a person who
            // has switched none of this on gets the launcher's own list back.
            if (kept === results) chain.proceed() else chain.proceed(arrayOf(kept))
        }

        context.logger.info("App drawer: search results are filtered as they arrive")
    }

    private fun keep(
        results: ArrayList<*>?,
        items: SearchResultItems,
        settings: SettingsSource,
        apps: HiddenAppsStore,
    ): ArrayList<*>? {
        val hidden = HiddenSearchResults(hiddenSearchResults(settings), apps.hidden())
        if (results == null || hidden.isEmpty) return results

        val kept = results.filterNotTo(ArrayList()) { item ->
            items.read(item)?.let(hidden::hides) == true
        }

        return if (kept.size == results.size) results else kept
    }

    private companion object {
        const val ALL_APPS_VIEW = "com.android.launcher3.allapps.ActivityAllAppsContainerView"

        /** `setSearchResults(ArrayList<AdapterItem> results)`. */
        const val RESULTS_ARGUMENT = 0
    }
}
