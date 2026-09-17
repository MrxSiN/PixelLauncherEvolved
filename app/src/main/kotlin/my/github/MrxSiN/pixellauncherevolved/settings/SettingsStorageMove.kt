package my.github.MrxSiN.pixellauncherevolved.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Carries the settings file out of credential encrypted storage.
 *
 * That storage opens only on the first unlock, and the launcher starts before
 * it. Unlocked, the file is moved before anything reads it. Locked, the tweaks
 * install from the defaults and the file is moved on unlock, which leaves this
 * launcher reading a stale copy: [onMovedWhileRunning] is how it asks for a
 * fresh one. That happens at most once per device, on the first locked boot
 * after updating from v0.0.9 or earlier.
 */
class SettingsStorageMove(
    private val context: Context,
    private val logger: Logger,
) {

    fun apply(onMovedWhileRunning: () -> Unit) {
        if (context.getSystemService(UserManager::class.java).isUserUnlocked) {
            move()
            return
        }

        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    context.unregisterReceiver(this)
                    if (move()) onMovedWhileRunning()
                }
            },
            IntentFilter(Intent.ACTION_USER_UNLOCKED),
            // Only the system sends it, so exporting the receiver opens nothing.
            Context.RECEIVER_EXPORTED,
        )
    }

    private fun move(): Boolean = runCatching { LauncherSettings.moveFromCredentialStorage(context) }
        .onSuccess { moved -> if (moved) logger.info("Settings moved to device protected storage") }
        .onFailure { logger.warn("Settings could not leave credential encrypted storage", it) }
        .getOrDefault(false)
}
