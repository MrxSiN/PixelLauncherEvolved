package my.github.MrxSiN.pixellauncherevolved.feature.diagnostics

import android.os.Build

import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Reports the launcher analysis on its own the first time a new version of the
 * launcher starts, or the same version on a new system build.
 *
 * A monthly update replaces the launcher without the module knowing, and the
 * first sign used to be a tweak quietly doing nothing. The registry already
 * leaves out a tweak whose members are gone; this writes down which ones, once
 * per `versionCode` and system build, to logcat — `adb logcat -s PixelLauncherEvolved` — where a
 * bug report can pick it up. The same report is on the Compatibility &
 * diagnostics page whenever it is opened.
 *
 * Installs no hook and changes nothing in the launcher.
 */
class CompatibilityWatchFeature : LauncherFeature {

    override val id: String = "compatibility_watch"

    override fun isEnabled(settings: SettingsSource): Boolean = true

    override fun install(context: FeatureContext) {
        val preferences = LauncherSettings.preferences(context.appContext)
        val analysis = context.analysis
        // A system update can replace the launcher's code under the same versionCode (CP3A to CP4A, both 907).
        val analyzed = "${analysis.launcherVersion}@${Build.FINGERPRINT}"
        if (preferences.getString(ANALYZED_VERSION, null) == analyzed) return

        val report = analysis.report
        val summary = "Pixel Launcher ${analysis.launcherVersion}: ${report.resolved} / ${report.total} contracts " +
            "resolved, ${report.fallbacks} by fallback, ${report.failed} failed"
        if (report.failed == 0) context.logger.info(summary) else context.logger.warn(summary)
        analysis.text.lineSequence().forEach(context.logger::info)
        preferences.edit().putString(ANALYZED_VERSION, analyzed).apply()
    }

    private companion object {
        const val ANALYZED_VERSION = "compatibility_analyzed_launcher_version"
    }
}
