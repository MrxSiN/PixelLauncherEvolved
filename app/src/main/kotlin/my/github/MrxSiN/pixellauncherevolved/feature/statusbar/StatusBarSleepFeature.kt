package my.github.MrxSiN.pixellauncherevolved.feature.statusbar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences

import my.github.MrxSiN.pixellauncherevolved.bridge.Bridge
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * Says whether two taps on the status bar should turn the screen off.
 *
 * The gesture itself is not here: the status bar belongs to SystemUI, and so
 * does the code that watches it — see
 * [my.github.MrxSiN.pixellauncherevolved.statusbar.StatusBarSleep]. What is
 * here is the half only the launcher can do, because the switch and the file
 * behind it are the launcher's: saying what the switch says, at startup, on
 * every change, and whenever SystemUI comes back having forgotten.
 *
 * Installed whether the switch is on or off, because SystemUI has to be told
 * when it goes off just as much as when it goes on.
 *
 * The feature is its own change listener rather than registering a lambda.
 * `SharedPreferences` keeps only a weak reference to a listener, so one has to
 * be held by something that outlives the call registering it. This class is —
 * `FeatureRegistry` holds every feature for the life of the process — where a
 * field holding a lambda is not: a field written and never read is removed by
 * the shrinker, which leaves the lambda reachable from nothing and collected at
 * the next GC. The switch then goes on working in settings and silently stops
 * reaching SystemUI.
 */
class StatusBarSleepFeature :
    ToggleFeature(Settings.STATUS_BAR_DOUBLE_TAP_TO_SLEEP),
    SharedPreferences.OnSharedPreferenceChangeListener {

    /** Read by [onSharedPreferenceChanged], so it stays a field the shrinker keeps. */
    private var launcher: FeatureContext? = null

    override fun install(context: FeatureContext) {
        launcher = context

        tell()
        LauncherSettings.preferences(context.appContext).registerOnSharedPreferenceChangeListener(this)
        answerSystemUi(context.appContext)
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (key == toggle.key) tell()
    }

    /** SystemUI restarts on its own sometimes, and comes back asking. */
    private fun answerSystemUi(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = tell()
        }

        context.registerReceiver(
            receiver,
            IntentFilter(Bridge.ASK),
            Bridge.PERMISSION,
            null,
            Context.RECEIVER_EXPORTED,
        )
    }

    private fun tell() {
        val context = launcher ?: return
        val on = isEnabled(context.settings)

        val intent = Intent(Bridge.STATUS_BAR_SLEEP)
            .setPackage(Bridge.SYSTEM_UI_PACKAGE)
            .putExtra(Bridge.EXTRA_ON, on)

        runCatching { context.appContext.sendBroadcast(intent) }
            .onSuccess { context.logger.info("Told SystemUI the status bar gesture is ${if (on) "on" else "off"}") }
            .onFailure { context.logger.warn("SystemUI was not told about the status bar gesture", it) }
    }
}
