package my.github.MrxSiN.pixellauncherevolved.ui

import android.content.Context
import android.content.Intent

/**
 * The launcher's own settings screen, which is where every tweak now lives.
 *
 * A person normally reaches it by long pressing an empty part of the home
 * screen. This is the same screen, opened directly, so the app that installs
 * the module can hand them straight to it.
 *
 * It is asked for by the action the launcher publishes rather than by class
 * name, so a renamed activity still resolves.
 */
object HomeSettings {

    private const val LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"

    fun open(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES)
            .setPackage(LAUNCHER_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
