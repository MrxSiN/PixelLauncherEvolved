package my.github.MrxSiN.pixellauncherevolved.wallpaper

import android.content.Context
import android.net.Uri
import android.provider.Settings

/**
 * The one setting Wallpaper & Style writes and the Pixel Launcher reads.
 *
 * It is kept in `Settings.Secure` rather than in this module's own store
 * because the two processes that share it cannot reach each other. Wallpaper &
 * Style targets API 37 and does not hold `QUERY_ALL_PACKAGES`, so package
 * visibility filters this module's own content provider out of every lookup it
 * makes, and a provider neither app can resolve is a setting neither app can
 * store. The settings provider is visible to everything, Wallpaper & Style
 * already holds `WRITE_SECURE_SETTINGS`, and reading needs no permission at
 * all.
 *
 * The key carries the application id so it cannot collide with a platform key
 * or with another module's.
 */
object WallpaperBlur {

    const val KEY: String = "pixel_launcher_evolved_home_blur_wallpaper"

    /** Watchable with a [android.database.ContentObserver], so a change is live. */
    fun uri(): Uri = Settings.Secure.getUriFor(KEY)

    fun isEnabled(context: Context): Boolean =
        runCatching { Settings.Secure.getInt(context.contentResolver, KEY, OFF) }
            .getOrDefault(OFF) != OFF

    /**
     * Stores the choice.
     *
     * @return false when this process may not write secure settings, which is
     * every process but Wallpaper & Style's.
     */
    fun setEnabled(context: Context, enabled: Boolean): Boolean = runCatching {
        Settings.Secure.putInt(context.contentResolver, KEY, if (enabled) ON else OFF)
    }.getOrDefault(false)

    private const val OFF = 0
    private const val ON = 1
}
