package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.os.SystemClock

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.icons.IconReloader
import my.github.MrxSiN.pixellauncherevolved.feature.labels.AppLabelsScreen
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabelStore
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * App labels: the per-app editor, and the way back to every app's own label.
 *
 * Pixel Launcher shows Home screen, folder and app drawer labels on a phone and
 * offers no setting for them; the three switches here leave them off a whole
 * surface, and an app's own entry can still hide its label where they show.
 */
internal object AppLabelRows : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val store = AppLabelStore(LauncherSettings.preferences(scope.context))
        lateinit var edit: Any
        lateinit var reset: Any
        fun refresh() {
            scope.api.setSummary(edit, summary(scope, store))
            scope.api.setEnabled(reset, store.labels().isNotEmpty())
        }

        edit = scope.link(SettingsKeys.row("app_labels"), R.string.app_labels_per_app_title, summary(scope, store)) {
            AppLabelsScreen(scope.context, scope.resources, store, scope.environment.logger, ::refresh).show()
        }
        reset = scope.link(
            SettingsKeys.row("app_labels_reset"),
            R.string.app_labels_reset_title,
            scope.string(R.string.app_labels_reset_summary),
        ) {
            ExpressiveDialog(scope.context)
                .title(scope.string(R.string.app_labels_reset_confirm_title))
                .message(scope.string(R.string.app_labels_reset_confirm_message))
                .dismiss(scope.context.getString(android.R.string.cancel))
                .confirm(scope.string(R.string.app_labels_reset_confirm)) {
                    store.clear()
                    IconReloader(scope.context, scope.environment.logger).reload("app labels reset", SystemClock.elapsedRealtime(), null)
                    refresh()
                }
                .show()
        }
        val homeOnly = FeatureCatalog.APP_LABELS_HOME_ONLY
        val homeOnlyRow = scope.api.createSwitch(
            context = scope.context,
            key = SettingsKeys.row(homeOnly.setting.key),
            title = scope.string(homeOnly.titleRes),
            summary = scope.string(homeOnly.summaryRes),
            checked = scope.isOn(homeOnly),
            onChange = { on ->
                scope.switch(homeOnly, on)
                store.publish()
                IconReloader(scope.context, scope.environment.logger).reload("app labels", SystemClock.elapsedRealtime(), null)
            },
        )
        // Every app's label on one surface; an app's own entry can still hide it where these show it.
        val surfaces = listOf(FeatureCatalog.LABELS_HOME, FeatureCatalog.LABELS_FOLDERS, FeatureCatalog.LABELS_DRAWER).map { entry ->
            scope.api.createSwitch(
                context = scope.context,
                key = SettingsKeys.row(entry.setting.key),
                title = scope.string(entry.titleRes),
                summary = scope.string(entry.summaryRes),
                checked = scope.isOn(entry),
                onChange = { on ->
                    scope.switch(entry, on)
                    store.publish()
                    IconReloader(scope.context, scope.environment.logger).reload("labels", SystemClock.elapsedRealtime(), null)
                },
            )
        }
        surfaces.forEach { scope.api.add(group, it) }
        scope.api.add(group, edit)
        scope.api.add(group, homeOnlyRow)
        scope.api.add(group, reset)
        surfaces.forEach { scope.requires(CompatibilityFeature.APP_LABELS, it) }
        refresh()
        scope.requires(CompatibilityFeature.APP_LABELS, edit)
        scope.requires(CompatibilityFeature.APP_LABELS, homeOnlyRow)
        scope.requires(CompatibilityFeature.APP_LABELS, reset)
    }

    private fun summary(scope: RowScope, store: AppLabelStore): String {
        val count = store.labels().size
        if (count == 0) return scope.string(R.string.app_labels_per_app_summary_none)
        return scope.resources.getQuantityString(R.plurals.app_labels_per_app_summary, count, count)
    }
}
