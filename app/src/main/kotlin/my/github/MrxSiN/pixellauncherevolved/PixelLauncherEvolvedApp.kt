package my.github.MrxSiN.pixellauncherevolved

import android.app.Application

import my.github.MrxSiN.pixellauncherevolved.settings.ModuleConnection

/**
 * Application entry point.
 *
 * The framework offers its binder while the process starts, so the listener is
 * registered here rather than from a screen: by the time any screen exists the
 * answer has usually already arrived.
 */
class PixelLauncherEvolvedApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ModuleConnection.register()
    }
}
