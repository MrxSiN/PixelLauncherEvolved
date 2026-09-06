package my.github.MrxSiN.pixellauncherevolved.feature.misc

import android.os.Process

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.LauncherFeature
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Restarts the launcher when the settings app asks it to.
 *
 * Tweaks apply to a running launcher on their own, but a hooked process
 * occasionally needs a clean slate, and a future tweak may change something the
 * launcher only reads at startup. Nothing outside the launcher can end its
 * process without a privileged permission, so the launcher ends its own: the
 * settings app raises a counter on the shared preferences, this feature notices
 * the change and exits.
 *
 * Killing the process is the restart. Android brings the home app straight back
 * up, which is what a force-stop and reopen would do by hand.
 *
 * The preference channel is used rather than a broadcast because it needs no
 * permission and exposes no receiver another app could poke.
 */
class RestartLauncherFeature : LauncherFeature {

    /**
     * Kept for the life of the process.
     *
     * The framework tracks preference listeners weakly, so anything that lets
     * this go out of scope lets the listener be collected and the launcher stop
     * answering restart requests.
     */
    private var observation: AutoCloseable? = null

    override val id: String = "restart_launcher"

    /** There is nothing to switch on: the feature only ever waits for a signal. */
    override fun isEnabled(settings: SettingsSource): Boolean = true

    override fun install(context: FeatureContext) {
        // The value at startup is the baseline; only a later change is a request.
        var lastSeen = context.settings[Settings.RESTART_REQUEST]

        observation = context.settings.observe {
            val requested = context.settings[Settings.RESTART_REQUEST]
            if (requested != lastSeen) {
                lastSeen = requested
                context.logger.info("Restart requested; ending the launcher process")
                Process.killProcess(Process.myPid())
            }
        }

        if (observation == null) {
            context.logger.warn("Settings cannot be observed; restart requests will be ignored")
        }
    }
}
