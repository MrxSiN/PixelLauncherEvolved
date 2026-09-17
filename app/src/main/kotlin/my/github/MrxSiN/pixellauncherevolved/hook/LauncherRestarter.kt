package my.github.MrxSiN.pixellauncherevolved.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Ends the launcher process the next time the screen goes off.
 *
 * Hook code loads once, when the launcher starts, and cannot be swapped inside
 * a running launcher: its views and callbacks belong to the build that made
 * them. So whatever needs a fresh launcher asks for one here, and the system
 * starts the launcher again on its own, loading what is installed.
 *
 * The screen going off is the only moment nobody sees it. The home screen being
 * behind an app is not: the launcher also draws the taskbar and handles the
 * navigation gestures over every app, and the system keeps the process at
 * foreground importance throughout, so there is no quieter moment to find.
 */
class LauncherRestarter(
    private val context: Context,
    private val logger: Logger,
) {

    private var pending = false

    init {
        register(IntentFilter(Intent.ACTION_SCREEN_OFF)) {
            if (!pending) return@register
            logger.info("Screen off; ending the launcher process to restart it")
            Process.killProcess(Process.myPid())
        }
    }

    fun restartOnScreenOff(reason: String) {
        logger.info("Launcher restarts when the screen goes off: $reason")
        pending = true
    }

    /**
     * Restarts once the module is replaced, including a replacement that landed
     * while this launcher was starting and left it on the previous build.
     *
     * @param loadedApk the module APK this process loaded its hook code from.
     */
    fun watchModule(packageName: String, loadedApk: String) {
        val filter = IntentFilter(Intent.ACTION_PACKAGE_REPLACED).apply {
            addDataScheme("package")
            addDataSchemeSpecificPart(packageName, 0)
        }
        register(filter) { restartOnScreenOff("module updated") }

        val installed = runCatching { context.packageManager.getApplicationInfo(packageName, 0).sourceDir }.getOrNull()
        if (installed != null && installed != loadedApk) restartOnScreenOff("loaded $loadedApk, installed is $installed")
    }

    /** Only the system sends these broadcasts, so exporting the receiver opens nothing. */
    private fun register(filter: IntentFilter, action: () -> Unit) {
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = action()
            },
            filter,
            Context.RECEIVER_EXPORTED,
        )
    }
}
