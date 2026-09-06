package my.github.MrxSiN.pixellauncherevolved.core

import android.util.Log

/**
 * Sink for diagnostics.
 *
 * Hook and feature code depends on this contract rather than on a concrete
 * logging framework, so the transport can change without touching behaviour.
 */
interface Logger {
    fun info(message: String)
    fun warn(message: String, error: Throwable? = null)
}

/** [Logger] backed by logcat, readable with `adb logcat -s PixelLauncherEvolved`. */
object AndroidLogger : Logger {

    const val TAG: String = "PixelLauncherEvolved"

    override fun info(message: String) {
        Log.i(TAG, message)
    }

    override fun warn(message: String, error: Throwable?) {
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }
}
