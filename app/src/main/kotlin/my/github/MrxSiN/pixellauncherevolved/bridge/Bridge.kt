package my.github.MrxSiN.pixellauncherevolved.bridge

/**
 * The words the two processes this module lives in use to talk to each other.
 *
 * Everything the module offers is installed in the launcher. SystemUI is
 * entered for the two things the launcher cannot do: draw the screen off, and
 * see a touch on the status bar. Neither process can read the other's memory,
 * and the settings file belongs to the launcher, so what crosses is a
 * broadcast.
 *
 * Broadcasts rather than a bound service or a provider: each message is one
 * way, none is worth waiting on, and a message that arrives too late to matter
 * is dropped instead of queued. They are cosmetic or a gesture the person just
 * made, but not something any app should be able to say, so both receivers
 * take a message only from a sender holding [PERMISSION] — which the Pixel
 * Launcher and SystemUI hold as privileged apps, and an ordinary app cannot.
 */
object Bridge {

    const val LAUNCHER_PACKAGE: String = "com.google.android.apps.nexuslauncher"
    const val SYSTEM_UI_PACKAGE: String = "com.android.systemui"

    const val PERMISSION: String = "android.permission.STATUS_BAR"

    /**
     * Launcher to SystemUI: a screen off has been asked for, at this point on
     * the screen, in pixels.
     */
    const val SLEEP_FROM: String = "my.github.MrxSiN.pixellauncherevolved.action.SLEEP_FROM"
    const val EXTRA_X: String = "x"
    const val EXTRA_Y: String = "y"

    /**
     * How long a point stays usable.
     *
     * The launcher's screen off is a root shell away, so the reveal starts a
     * moment after the tap rather than with it. Long enough to cover that
     * shell, short enough that a point never outlives the gesture that gave it.
     */
    const val POINT_FRESH_FOR_MILLIS: Long = 2_000L

    /**
     * Launcher to SystemUI: whether a double tap on the status bar should end
     * the screen. The switch is in the launcher's own Home settings, and the
     * file behind it is the launcher's, so SystemUI is told rather than asked.
     */
    const val STATUS_BAR_SLEEP: String = "my.github.MrxSiN.pixellauncherevolved.action.STATUS_BAR_SLEEP"
    const val EXTRA_ON: String = "on"

    /**
     * SystemUI to the launcher: say it again.
     *
     * SystemUI can be restarted on its own — it crashes, or a theme change
     * takes it down — and comes back having forgotten what it was told. Rather
     * than keep a second copy of a setting the launcher owns, it asks.
     */
    const val ASK: String = "my.github.MrxSiN.pixellauncherevolved.action.ASK"
}
