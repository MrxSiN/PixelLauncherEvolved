package my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble

import android.content.Context

/**
 * Opens an application in an Android 17 bubble.
 *
 * Callers depend on this contract only, so the route to the shell can change
 * without touching the Overview code.
 */
interface BubbleLauncher {

    /** Returns true when the request reached the shell. */
    fun launch(context: Context, target: BubbleTarget): Boolean
}
