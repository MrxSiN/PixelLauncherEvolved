package my.github.MrxSiN.pixellauncherevolved.lock

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Ends the screen from inside SystemUI, which needs no help to do it.
 *
 * `PowerManager.goToSleep` needs `DEVICE_POWER`, which is why the launcher has
 * to go through this module's app and a root shell ([RootScreenLocker]).
 * SystemUI holds that permission itself, so a gesture SystemUI sees can end the
 * screen directly — no root, and no shell to wait for.
 *
 * The one-argument call is used because the reason it fills in is not the power
 * button's: the power button's reason is what makes SystemUI sweep the screen
 * away from the button's own edge, and this gesture is drawn around the finger
 * instead. The method is hidden, and reached by reflection; SystemUI is a
 * platform app, which the hidden API restrictions do not apply to.
 */
class SystemUiScreenLocker(private val context: Context, private val logger: Logger) : ScreenLocker {

    private val goToSleep by lazy {
        runCatching { PowerManager::class.java.getMethod("goToSleep", Long::class.javaPrimitiveType) }
            .onFailure { logger.warn("SystemUI cannot end the screen on this build", it) }
            .getOrNull()
    }

    override fun lock(): Boolean {
        val method = goToSleep ?: return false
        val power = context.getSystemService(PowerManager::class.java) ?: return false

        return runCatching { method.invoke(power, SystemClock.uptimeMillis()) }
            .onFailure { logger.warn("The screen refused to end", it) }
            .isSuccess
    }
}
