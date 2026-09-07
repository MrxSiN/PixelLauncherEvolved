package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.content.Context
import android.graphics.drawable.Drawable

/**
 * Reads launcher resources by name.
 *
 * Borrowing the host's own colours, icons, and ids keeps injected views
 * consistent with the surrounding chrome and keeps this module free of
 * duplicated assets. Every lookup has a caller-supplied fallback, so a renamed
 * resource degrades one detail instead of breaking a feature.
 */
class LauncherResources(context: Context) {

    private val resources = context.resources
    private val packageName = context.packageName

    fun color(name: String, fallback: Int): Int =
        identifier(name, "color").let { if (it == 0) fallback else resources.getColor(it, null) }

    fun drawable(name: String): Drawable? =
        identifier(name, "drawable").let { if (it == 0) null else resources.getDrawable(it, null) }

    fun string(name: String, fallback: String): String =
        identifier(name, "string").let { if (it == 0) fallback else resources.getString(it) }

    fun id(name: String): Int = identifier(name, "id")

    fun layout(name: String): Int = identifier(name, "layout")

    fun dimensionPixels(name: String, fallbackPixels: Int): Int =
        identifier(name, "dimen").let { if (it == 0) fallbackPixels else resources.getDimensionPixelSize(it) }

    private fun identifier(name: String, type: String): Int =
        resources.getIdentifier(name, type, packageName)
}
