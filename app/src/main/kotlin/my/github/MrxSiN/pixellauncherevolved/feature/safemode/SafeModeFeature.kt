package my.github.MrxSiN.pixellauncherevolved.feature.safemode

import android.app.Activity
import android.content.res.Resources
import android.os.Process

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.LayoutMode
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.safemode.CrashGuard
import my.github.MrxSiN.pixellauncherevolved.safemode.SafeModeStore
import my.github.MrxSiN.pixellauncherevolved.safemode.SharedPreferencesSafeModeStore
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Tells the person what Safe Mode switched off, over the home screen it saved.
 *
 * [my.github.MrxSiN.pixellauncherevolved.safemode.CrashGuard] does the
 * switching before anything else installs. This only asks, once the launcher is
 * showing, whether to bring the tweak back or leave it off. Bringing it back
 * restarts the launcher, because a layout mode is read once at startup.
 */
class SafeModeFeature : LauncherFeature {

    override val id: String = "safe_mode"

    override fun isEnabled(settings: SettingsSource): Boolean = true

    override fun install(context: FeatureContext) {
        val launcher = context.findClass(LAUNCHER)
        if (launcher == null) {
            context.logger.warn("The launcher activity is unavailable; Safe Mode cannot report what it switched off")
            return
        }
        val store = SharedPreferencesSafeModeStore(LauncherSettings.preferences(context.appContext))
        var asked = false

        context.hookAfter(launcher, ON_RESUME) { activity, _ ->
            if (asked || activity !is Activity) return@hookAfter
            val entries = store.disabled()
            val disabled = entries.mapNotNull(LayoutMode::ofKey)
            val grid = gridValues(entries)
            if (disabled.isEmpty() && grid.isEmpty()) return@hookAfter

            val resources = context.moduleResources ?: return@hookAfter
            asked = true
            ask(activity, resources, context, store, disabled, grid)
        }
    }

    private fun ask(
        activity: Activity,
        resources: Resources,
        context: FeatureContext,
        store: SafeModeStore,
        disabled: List<LayoutMode>,
        grid: Map<IntSetting, Int>,
    ) {
        val titles = disabled.map { resources.getString(it.titleRes) } +
            if (grid.isEmpty()) emptyList() else listOf(resources.getString(R.string.settings_page_grid))
        val names = titles.joinToString(resources.getString(R.string.safe_mode_name_separator))

        ExpressiveDialog(activity)
            .title(resources.getString(R.string.safe_mode_title))
            .message(resources.getString(R.string.safe_mode_message, names))
            .dismiss(resources.getString(R.string.safe_mode_keep_disabled)) {
                store.setDisabled(emptyList())
            }
            .confirm(resources.getString(R.string.safe_mode_restore)) {
                disabled.forEach { mode -> mode.setting?.let { context.settings.put(it, true) } }
                grid.forEach { (setting, value) -> context.settings.put(setting, value) }
                store.setDisabled(emptyList())
                context.logger.info("Safe Mode: $disabled $grid restored; restarting the launcher to apply it")
                Process.killProcess(Process.myPid())
            }
            .show()
    }

    /** The Grid & size values Safe Mode put back to default, from its `key=value` entries. */
    private fun gridValues(entries: List<String>): Map<IntSetting, Int> = entries.mapNotNull { entry ->
        val key = entry.substringBefore(CrashGuard.GRID_VALUE, "")
        val setting = Settings.GRID.firstOrNull { it.key == key } ?: return@mapNotNull null
        entry.substringAfter(CrashGuard.GRID_VALUE).toIntOrNull()?.let { setting to it }
    }.toMap()

    private companion object {
        const val LAUNCHER = "com.android.launcher3.Launcher"
        const val ON_RESUME = "onResume"
    }
}
