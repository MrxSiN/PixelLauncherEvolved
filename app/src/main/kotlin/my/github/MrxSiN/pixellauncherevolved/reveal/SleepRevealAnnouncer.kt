package my.github.MrxSiN.pixellauncherevolved.reveal

import android.content.Context
import android.content.Intent

import my.github.MrxSiN.pixellauncherevolved.bridge.Bridge
import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Tells SystemUI where the gesture was taken, from inside the launcher.
 *
 * Sent before the screen is asked to end, and not waited on: the screen off
 * goes through a root shell, which takes long enough for a one-way broadcast
 * to be delivered well before the reveal it decorates begins.
 */
class SleepRevealAnnouncer(private val context: Context, private val logger: Logger) {

    fun announce(x: Int, y: Int) {
        val intent = Intent(Bridge.SLEEP_FROM)
            .setPackage(Bridge.SYSTEM_UI_PACKAGE)
            .putExtra(Bridge.EXTRA_X, x)
            .putExtra(Bridge.EXTRA_Y, y)

        runCatching { context.sendBroadcast(intent) }
            .onFailure { logger.warn("SystemUI was not told where the screen off began", it) }
    }
}
