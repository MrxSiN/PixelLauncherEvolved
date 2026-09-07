package my.github.MrxSiN.pixellauncherevolved.lock

import android.net.Uri

/**
 * The one thing the launcher cannot do for itself: turn the screen off.
 *
 * `PowerManager.goToSleep` needs `DEVICE_POWER`, a signature permission the
 * Pixel Launcher does not hold. The module app therefore invokes the same
 * power-key event through `su`.
 *
 * So the gesture is recognised in the launcher and carried out in the app. The
 * call goes over a content provider rather than a broadcast because a provider
 * knows its caller and can refuse anyone but the launcher.
 */
object ScreenLock {

    /** Appended to the module's own package name, as the manifest does. */
    const val AUTHORITY_SUFFIX: String = ".screenlock"

    /** The only method the provider answers. */
    const val LOCK: String = "lock"

    /** Whether the screen was actually turned off, so a caller can log it. */
    const val LOCKED: String = "locked"

    fun uri(modulePackage: String): Uri = Uri.parse("content://$modulePackage$AUTHORITY_SUFFIX")
}
