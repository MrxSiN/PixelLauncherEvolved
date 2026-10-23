package my.github.MrxSiN.pixellauncherevolved.feature.settings

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.CatalogEntry
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.stacks.WidgetStacks

/** The rows of the Widget stacks page, other than its main switch. */
internal object WidgetStackRows {

    /** A switch that only means something while widget stacks are on. */
    fun dependent(entry: CatalogEntry) = SettingsRow { group, scope ->
        val row = scope.api.createSwitch(
            context = scope.context,
            key = SettingsKeys.row(entry.setting.key),
            title = scope.string(entry.titleRes),
            summary = scope.string(entry.summaryRes),
            checked = scope.isOn(entry),
            onChange = { on -> scope.switch(entry, on) },
        )
        scope.api.add(group, row)
        scope.dependOn(FeatureCatalog.WIDGET_STACKS, row)
        scope.requires(CompatibilityFeature.WIDGET_STACKS, row)
    }

    /** How a stack's dots show, as one radio button each. */
    val Indicator = SettingsRow { group, scope ->
        val rows = mutableListOf<Any>()
        val choices = intArrayOf(INDICATOR_QUIET, INDICATOR_ALWAYS, INDICATOR_SWIPING)
        val titles = intArrayOf(
            R.string.widget_stacks_dots_quiet_title,
            R.string.widget_stacks_dots_always_title,
            R.string.widget_stacks_dots_swiping_title,
        )
        val summaries = intArrayOf(
            R.string.widget_stacks_dots_quiet_summary,
            R.string.widget_stacks_dots_always_summary,
            R.string.widget_stacks_dots_swiping_summary,
        )
        for (i in choices.indices) {
            val key = SettingsKeys.row("widget_stacks_dots_$i")
            val row = scope.api.createAction(scope.context, key, scope.string(titles[i]), scope.string(summaries[i])) {
                scope.settings.put(Settings.WIDGET_STACKS_INDICATOR, choices[i])
                rows.forEach(scope.api::refresh)
            }
            scope.accessory(key) { RowAccessory.Radio(scope.settings[Settings.WIDGET_STACKS_INDICATOR] == choices[i]) }
            scope.api.add(group, row)
            scope.dependOn(FeatureCatalog.WIDGET_STACKS, row)
            scope.requires(CompatibilityFeature.WIDGET_STACKS, row)
            rows += row
        }
    }

    /**
     * Puts every stacked widget back on the home screen on its own, asked first.
     *
     * Done by the launcher's home screen, which owns the widgets; with no home
     * screen running (the feature not installed), it says so instead.
     */
    val Restore = SettingsRow { group, scope ->
        val row = scope.link(
            SettingsKeys.row("widget_stacks_restore"),
            R.string.widget_stacks_restore_title,
            scope.string(R.string.widget_stacks_restore_summary),
        ) {
            ExpressiveDialog(scope.context)
                .title(scope.string(R.string.widget_stacks_restore_title))
                .message(scope.string(R.string.widget_stacks_restore_message))
                .dismiss(scope.context.getString(android.R.string.cancel))
                .confirm(scope.string(R.string.widget_stacks_restore_confirm)) {
                    val restored = WidgetStacks.restoreAll?.invoke()
                    ExpressiveDialog(scope.context)
                        .title(scope.string(R.string.widget_stacks_restore_title))
                        .message(
                            if (restored == null) scope.string(R.string.widget_stacks_restore_unavailable)
                            else scope.resources.getQuantityString(R.plurals.widget_stacks_restore_done, restored, restored),
                        )
                        .dismiss(scope.context.getString(android.R.string.ok))
                        .show()
                }
                .show()
        }
        scope.api.add(group, row)
        scope.requires(CompatibilityFeature.WIDGET_STACKS, row)
    }

    const val INDICATOR_QUIET = 0
    const val INDICATOR_ALWAYS = 1
    const val INDICATOR_SWIPING = 2
}
