package my.github.MrxSiN.pixellauncherevolved.feature.safemode

import android.app.Activity
import android.content.res.Resources
import android.os.Process

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.LayoutMode
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
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
            val disabled = store.disabled().mapNotNull(LayoutMode::ofKey)
            if (disabled.isEmpty()) return@hookAfter

            val resources = context.moduleResources ?: return@hookAfter
            asked = true
            ask(activity, resources, context, store, disabled)
        }
    }

    private fun ask(
        activity: Activity,
        resources: Resources,
        context: FeatureContext,
        store: SafeModeStore,
        disabled: List<LayoutMode>,
    ) {
        val names = disabled.joinToString(resources.getString(R.string.safe_mode_name_separator)) {
            resources.getString(it.titleRes)
        }

        ExpressiveDialog(activity)
            .title(resources.getString(R.string.safe_mode_title))
            .message(resources.getString(R.string.safe_mode_message, names))
            .dismiss(resources.getString(R.string.safe_mode_keep_disabled)) {
                store.setDisabled(emptyList())
            }
            .confirm(resources.getString(R.string.safe_mode_restore)) {
                disabled.forEach { mode -> mode.setting?.let { context.settings.put(it, true) } }
                store.setDisabled(emptyList())
                context.logger.info("Safe Mode: $disabled restored; restarting the launcher to apply it")
                Process.killProcess(Process.myPid())
            }
            .show()
    }

    private companion object {
        const val LAUNCHER = "com.android.launcher3.Launcher"
        const val ON_RESUME = "onResume"
    }
}
