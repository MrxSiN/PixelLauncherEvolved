package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Intent

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.apps.HiddenAppsStore
import my.github.MrxSiN.pixellauncherevolved.feature.apps.HideAppsSelection
import my.github.MrxSiN.pixellauncherevolved.feature.apps.SharedPreferencesHiddenAppsStore
import my.github.MrxSiN.pixellauncherevolved.feature.search.SharedPreferencesWebSearchAppStore
import my.github.MrxSiN.pixellauncherevolved.feature.search.WebSearchAppDialog
import my.github.MrxSiN.pixellauncherevolved.feature.search.WebSearchAppStore
import my.github.MrxSiN.pixellauncherevolved.feature.search.WebSearchApps
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * Says which apps the drawer leaves out.
 *
 * The apps are picked where they are drawn, so settings steps out of the way:
 * it starts what the launcher will finish, goes home, and closes itself rather
 * than waiting behind the drawer with a count that is about to be wrong.
 */
internal object HiddenAppsRow : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val context = scope.context
        val store = SharedPreferencesHiddenAppsStore(LauncherSettings.preferences(context))

        val row = scope.link(SettingsKeys.row("hidden_apps"), R.string.feature_hidden_apps_title, summary(scope, store)) {
            HideAppsSelection.begin(store.hidden())
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            context.activityOrNull()?.finish()
        }
        scope.api.add(group, row)
        scope.requires(CompatibilityFeature.HIDDEN_APPS, row)
    }

    private fun summary(scope: RowScope, store: HiddenAppsStore): String {
        val count = store.hidden().size
        if (count == 0) return scope.string(R.string.feature_hidden_apps_summary_none)

        return scope.resources.getQuantityString(R.plurals.feature_hidden_apps_summary, count, count)
    }
}

/**
 * Says which app opens a tapped Web Search result.
 *
 * One app out of what the device offers, so not a switch. Greyed out while Web
 * Search is off, because there is then no result to open.
 */
internal object WebSearchAppRow : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val store = SharedPreferencesWebSearchAppStore(LauncherSettings.preferences(scope.context))
        val apps = WebSearchApps(scope.context.packageManager)
        lateinit var row: Any

        row = scope.link(
            SettingsKeys.row("web_search_app"),
            R.string.feature_app_drawer_search_web_app_title,
            summary(scope, store, apps),
        ) {
            WebSearchAppDialog.show(scope.context, scope.resources, store, apps) {
                scope.api.setSummary(row, summary(scope, store, apps))
            }
        }
        scope.api.add(group, row)
        scope.dependOn(FeatureCatalog.WEB_SEARCH, row)
        scope.requires(CompatibilityFeature.APP_DRAWER_SEARCH, row)
    }

    /** An app that is gone reads as the launcher's own answer, which is what the tap then does. */
    private fun summary(scope: RowScope, store: WebSearchAppStore, apps: WebSearchApps): String {
        val label = store.chosen()?.let(apps::labelOf)
            ?: return scope.string(R.string.feature_app_drawer_search_web_app_default)

        return scope.string(R.string.feature_app_drawer_search_web_app_chosen, label)
    }
}
