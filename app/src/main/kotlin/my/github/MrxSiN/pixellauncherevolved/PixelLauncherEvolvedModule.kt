package my.github.MrxSiN.pixellauncherevolved

import android.app.Application
import android.content.SharedPreferences

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.core.AndroidLogger
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureRegistry
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherStartup
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsMigration
import my.github.MrxSiN.pixellauncherevolved.settings.SharedPreferencesStore

/**
 * Module entry point.
 *
 * Its only job is to route the scoped package to its feature installer. Every
 * decision about what to change in the launcher lives in a feature class.
 */
class PixelLauncherEvolvedModule : XposedModule() {

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return

        val logger = AndroidLogger
        when (param.packageName) {
            LAUNCHER_PACKAGE -> {
                logger.info("Loading in ${param.packageName}")
                LauncherStartup(this, param.defaultClassLoader, logger)
                    .onApplicationCreated { application ->
                        installLauncher(application, param.defaultClassLoader, logger)
                    }
            }
        }
    }

    private fun installLauncher(application: Application, classLoader: ClassLoader, logger: Logger) {
        val preferences = LauncherSettings.preferences(application)
        SettingsMigration(logger).apply(preferences, frameworkPreferences(logger))

        FeatureRegistry.install(
            FeatureContext(
                xposed = this,
                classLoader = classLoader,
                appContext = application,
                settings = SharedPreferencesStore(preferences),
                logger = logger,
            ),
        )
    }

    /**
     * The store earlier versions wrote to, read once so those choices survive.
     *
     * Null when no framework answers, which only costs the one-time copy.
     */
    private fun frameworkPreferences(logger: Logger): SharedPreferences? = try {
        getRemotePreferences(FeatureCatalog.SETTINGS_GROUP)
    } catch (error: Throwable) {
        logger.warn("Earlier settings are unreachable; starting from the defaults", error)
        null
    }

    private companion object {
        const val LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"
    }
}
