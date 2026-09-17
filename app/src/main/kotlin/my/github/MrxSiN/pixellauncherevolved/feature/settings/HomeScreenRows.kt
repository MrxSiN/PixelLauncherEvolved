package my.github.MrxSiN.pixellauncherevolved.feature.settings

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPagesDialog
import my.github.MrxSiN.pixellauncherevolved.feature.focus.LauncherPagePreviewSource
import my.github.MrxSiN.pixellauncherevolved.feature.pages.ReorganizePagesScreen
import my.github.MrxSiN.pixellauncherevolved.focus.ProviderFocusSource
import my.github.MrxSiN.pixellauncherevolved.focus.SharedPreferencesFocusStore
import my.github.MrxSiN.pixellauncherevolved.focus.forgetModesMissingFrom
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * How strong the home screen blur is, from weakest to the launcher's own
 * deepest. The middle is what the tweak did before it could be changed.
 *
 * Not a switch, so not in the catalogue: it says how the switch above it
 * behaves, and is greyed out while that switch is off to say what it would
 * change.
 */
internal object BlurStrengthRow : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        if (!scope.api.hasSlider) {
            scope.environment.logger.warn("Home settings has no slider row in this launcher; blur strength is not offered")
            return
        }

        val strength = Settings.HOME_BLUR_STRENGTH
        lateinit var row: Any
        row = scope.api.createSlider(
            context = scope.context,
            key = SettingsKeys.row(strength.key),
            title = scope.string(R.string.feature_home_blur_strength_title),
            summary = summary(scope, scope.settings[strength]),
            value = scope.settings[strength],
            isEnabled = scope.isOn(FeatureCatalog.HOME_BLUR_WALLPAPER),
            onChange = { value ->
                scope.settings.put(strength, value)
                scope.api.setSummary(row, summary(scope, value))
            },
        )
        scope.api.add(group, row)
        scope.dependOn(FeatureCatalog.HOME_BLUR_WALLPAPER, row)
        scope.requires(CompatibilityFeature.BLUR_WALLPAPER, row)
    }

    private fun summary(scope: RowScope, strength: Int): String =
        scope.string(R.string.feature_home_blur_strength_summary, strength)
}

/**
 * Opens the screen for putting every home screen page in order.
 *
 * Offered whatever else is on, because pages are the launcher's own; the Modes
 * are only read, to name the Mode a page belongs to, when a page is given to one.
 */
internal object OrganizePagesRow : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val context = scope.context
        val store = SharedPreferencesFocusStore(LauncherSettings.preferences(context))

        val row = scope.link(
            key = SettingsKeys.row("reorganize_pages"),
            title = R.string.pages_reorganize_title,
            summary = scope.string(R.string.pages_reorganize_summary),
        ) {
            val modes = if (scope.settings[Settings.FOCUS_HOME_SCREENS] && store.assignments().isNotEmpty()) {
                val source = ProviderFocusSource(context.contentResolver, scope.environment.modulePackage, scope.environment.logger)
                source.snapshot().also(store::forgetModesMissingFrom).modes
            } else {
                emptyList()
            }
            ReorganizePagesScreen(context, scope.resources, store, scope.environment.logger)
                .show(LauncherPagePreviewSource(context), modes)
        }
        scope.api.add(group, row)
        scope.requires(CompatibilityFeature.ORGANIZE_PAGES, row)
    }
}

/**
 * Says which pages each Mode shows.
 *
 * Not a switch, so it is not in the catalogue: the catalogue is one key per
 * on/off setting, and this is a set of pages per Mode. It follows the switch
 * that turns the whole thing on, and is greyed out while that is off.
 */
internal object FocusPagesRow : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val context = scope.context
        val store = SharedPreferencesFocusStore(LauncherSettings.preferences(context))
        val source = ProviderFocusSource(context.contentResolver, scope.environment.modulePackage, scope.environment.logger)

        val row = scope.link(
            key = SettingsKeys.row("focus_pages"),
            title = R.string.feature_focus_pages_title,
            summary = scope.string(R.string.feature_focus_pages_summary),
        ) {
            FocusPagesDialog.show(context, scope.resources, store, source, LauncherPagePreviewSource(context))
        }
        scope.api.add(group, row)
        scope.dependOn(FeatureCatalog.FOCUS_HOME_SCREENS, row)
        scope.requires(CompatibilityFeature.FOCUS_HOME_SCREENS, row)
    }
}
