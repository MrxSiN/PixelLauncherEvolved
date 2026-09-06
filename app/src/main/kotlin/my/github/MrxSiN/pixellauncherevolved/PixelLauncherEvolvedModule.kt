package my.github.MrxSiN.pixellauncherevolved

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.core.AndroidLogger
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureRegistry
import my.github.MrxSiN.pixellauncherevolved.settings.DefaultSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource
import my.github.MrxSiN.pixellauncherevolved.settings.SharedPreferencesSettings

/**
 * Module entry point.
 *
 * Its only job is to recognise the launcher process, read the stored settings,
 * and hand over to the feature registry. Every decision about what to change in
 * the launcher lives in a feature class.
 */
class PixelLauncherEvolvedModule : XposedModule() {

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != LAUNCHER_PACKAGE || !param.isFirstPackage) return

        val logger = AndroidLogger
        logger.info("Loading in ${param.packageName}")

        FeatureRegistry.installEnabled(
            FeatureContext(
                xposed = this,
                classLoader = param.defaultClassLoader,
                settings = settings(logger),
                logger = logger,
            ),
        )
    }

    /** Falls back to defaults so a settings outage disables tweaks, not the launcher. */
    private fun settings(logger: Logger): SettingsSource = try {
        SharedPreferencesSettings(getRemotePreferences(FeatureCatalog.SETTINGS_GROUP))
    } catch (error: Throwable) {
        logger.warn("Stored settings are unreachable; using defaults", error)
        DefaultSettings
    }

    private companion object {
        const val LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"
    }
}
