package my.github.MrxSiN.pixellauncherevolved

import android.app.Application
import android.content.SharedPreferences
import android.os.SystemClock

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.core.AndroidLogger
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.hook.ApplicationStartup
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureRegistry
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherRestarter
import my.github.MrxSiN.pixellauncherevolved.bridge.Bridge
import my.github.MrxSiN.pixellauncherevolved.reveal.SleepRevealOrigin
import my.github.MrxSiN.pixellauncherevolved.reveal.SleepRevealScrim
import my.github.MrxSiN.pixellauncherevolved.statusbar.StatusBarSleep
import my.github.MrxSiN.pixellauncherevolved.safemode.CrashGuard
import my.github.MrxSiN.pixellauncherevolved.safemode.SharedPreferencesSafeModeStore
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsMigration
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsStorageMove
import my.github.MrxSiN.pixellauncherevolved.settings.SharedPreferencesStore

/**
 * Module entry point.
 *
 * Its only job is to route each scoped package to its installer. Every
 * decision about what to change in the launcher lives in a feature class.
 *
 * Two packages are scoped. Every tweak is installed in the launcher; SystemUI
 * is entered for the two things the launcher cannot do — draw the screen off
 * ([SleepRevealScrim]) and see a touch on the status bar ([StatusBarSleep]).
 *
 * A new build of the module is not hot reloaded into a running launcher, whose
 * views and callbacks belong to the build that made them. The launcher restarts
 * itself the next time the screen goes off instead ([LauncherRestarter]).
 */
class PixelLauncherEvolvedModule : XposedModule() {

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return

        val logger = AndroidLogger
        when (param.packageName) {
            LAUNCHER_PACKAGE -> {
                logger.info("Loading in ${param.packageName}")
                ApplicationStartup(this, param.defaultClassLoader, logger, LAUNCHER_APPLICATION)
                    .onApplicationCreated { application ->
                        installLauncher(application, param.defaultClassLoader, logger)
                    }
            }

            Bridge.SYSTEM_UI_PACKAGE -> {
                logger.info("Loading in ${param.packageName}")
                ApplicationStartup(this, param.defaultClassLoader, logger, SYSTEM_UI_APPLICATION)
                    .onApplicationCreated { application ->
                        installSystemUi(application, param.defaultClassLoader, logger)
                    }
            }
        }
    }

    private fun installLauncher(application: Application, classLoader: ClassLoader, logger: Logger) {
        val restarter = LauncherRestarter(application, logger)
        restarter.watchModule(moduleApplicationInfo.packageName, moduleApplicationInfo.sourceDir)
        SettingsStorageMove(application, logger).apply {
            restarter.restartOnScreenOff("settings moved out of credential encrypted storage")
        }

        val preferences = LauncherSettings.preferences(application)
        SettingsMigration(logger).apply(preferences, frameworkPreferences(logger))

        val settings = SharedPreferencesStore(preferences)
        // Before any feature installs: a crash loop's tweak must not be installed again.
        CrashGuard(SharedPreferencesSafeModeStore(preferences), settings, logger).apply {
            watch()
            recover()
        }

        FeatureRegistry.install(
            FeatureContext(
                xposed = this,
                classLoader = classLoader,
                appContext = application,
                settings = settings,
                logger = logger,
            ),
        )
    }

    /**
     * Both halves share one point: whichever gesture asked for the screen off
     * leaves the spot it was taken at, and the reveal draws around it.
     */
    private fun installSystemUi(application: Application, classLoader: ClassLoader, logger: Logger) {
        val origin = SleepRevealOrigin(now = SystemClock::uptimeMillis)

        SleepRevealScrim(this, classLoader, application, logger, origin).install()
        StatusBarSleep(this, classLoader, application, logger, origin).install()
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
        const val LAUNCHER_APPLICATION = "com.android.launcher3.LauncherApplication"
        // Android 17 QPR1's SystemUI application. An older or later name falls
        // back to Application.onCreate, which is the same moment.
        const val SYSTEM_UI_APPLICATION = "com.android.systemui.application.impl.SystemUIApplicationImpl"
    }
}
