package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.pm.PackageManager

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPagesDialog
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSources
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceWhen
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPagePreviewSource
import my.github.MrxSiN.pixellauncherevolved.feature.pages.ReorganizePagesScreen
import my.github.MrxSiN.pixellauncherevolved.focus.FocusMode
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
 * One switch per At a Glance source of [kind] ([GlanceSources]), in the order they show.
 *
 * A switch writes `glance_source_<id>`; the content feature starts or stops that source as it
 * changes, with no launcher restart.
 */
internal class GlanceSourceRows(private val kind: GlanceSource.Kind) : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val preferences = LauncherSettings.preferences(scope.context)
        for (source in GlanceSources.all) {
            if (source.kind != kind) continue
            val key = GlanceSources.key(source)
            // Shown but greyed out when Pixel Launcher lacks what the source reads, so its absence is explained.
            val allowed = source.permission?.let { scope.context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } ?: true
            val row = scope.api.createSwitch(
                context = scope.context,
                key = SettingsKeys.row(key),
                title = scope.string(source.titleRes),
                summary = if (allowed) scope.string(source.descriptionRes) else scope.string(R.string.glance_source_no_permission),
                checked = allowed && preferences.getBoolean(key, false),
                onChange = { on -> preferences.edit().putBoolean(key, on).apply() },
            )
            scope.api.add(group, row)
            if (!allowed) scope.api.setEnabled(row, false)
            scope.requires(CompatibilityFeature.AT_A_GLANCE, row)
        }
    }
}

/**
 * "When to show" for every At a Glance source: one row each, its summary the current choice, a tap
 * picks another. Stored as `glance_when_<id>` ([GlanceWhen]); absent is always.
 */
internal object GlanceWhenRows : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val preferences = LauncherSettings.preferences(scope.context)
        val choices = listOf<Pair<String?, CharSequence>>(null to scope.string(R.string.glance_when_always)) +
            GlanceWhen.values.map { it to scope.string(label(it)) }
        for (source in GlanceSources.all) {
            val key = GlanceWhen.key(source)
            fun summary() = preferences.getString(key, null).let { current -> choices.firstOrNull { it.first == current }?.second ?: choices[0].second }
            lateinit var row: Any
            row = scope.link(key = SettingsKeys.row(key), title = source.titleRes, summary = summary()) {
                FocusPagesDialog.choose(scope.context, scope.resources, scope.string(source.titleRes), choices, preferences.getString(key, null)) { value, label ->
                    preferences.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
                    scope.api.setSummary(row, label)
                }
            }
            scope.api.add(group, row)
            scope.requires(CompatibilityFeature.AT_A_GLANCE, row)
        }
    }

    private fun label(value: String): Int = when (value) {
        GlanceWhen.MUSIC -> R.string.glance_when_music
        GlanceWhen.CHARGING -> R.string.glance_when_charging
        GlanceWhen.MORNING -> R.string.glance_when_morning
        GlanceWhen.AFTERNOON -> R.string.glance_when_afternoon
        GlanceWhen.EVENING -> R.string.glance_when_evening
        else -> R.string.glance_when_night
    }
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
            val show = { modes: List<FocusMode> ->
                ReorganizePagesScreen(context, scope.resources, store, scope.environment.logger)
                    .show(FocusPagePreviewSource(context, scope.settings), modes)
            }
            if (scope.settings[Settings.FOCUS_HOME_SCREENS] && store.assignments().isNotEmpty()) {
                val source = ProviderFocusSource(context.contentResolver, scope.environment.modulePackage, scope.environment.logger)
                FocusPagesDialog.readThen(context, source::snapshot) { snapshot ->
                    store.forgetModesMissingFrom(snapshot)
                    show(snapshot.modes)
                }
            } else {
                show(emptyList())
            }
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
            FocusPagesDialog.show(context, scope.resources, store, source, FocusPagePreviewSource(context, scope.settings))
        }
        scope.api.add(group, row)
        scope.dependOn(FeatureCatalog.FOCUS_HOME_SCREENS, row)
        scope.requires(CompatibilityFeature.FOCUS_HOME_SCREENS, row)
    }
}
