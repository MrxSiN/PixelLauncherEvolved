package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.content.Context
import android.graphics.drawable.Drawable

/**
 * The icon Settings shows beside a Mode, loaded in the launcher's process.
 *
 * A Mode names its icon as `package:drawable/name`, and the package is whoever
 * owns the drawable: the framework for the built-in types, or Wellbeing, GMS or
 * Settings Intelligence for the Modes they create. The launcher can see every
 * package, so it reads the drawable out of that package's own resources.
 */
internal object FocusModeIcons {

    fun load(context: Context, name: String?): Drawable? =
        name?.let { runCatching { resolve(context, it) }.getOrNull() }
            ?: runCatching { resolve(context, FALLBACK) }.getOrNull()

    private fun resolve(context: Context, name: String): Drawable? {
        val packageName = name.substringBefore(':')
        val type = name.substringAfter(':').substringBefore('/')
        val entry = name.substringAfter('/')
        val resources = if (packageName == FRAMEWORK) {
            context.resources
        } else {
            context.packageManager.getResourcesForApplication(packageName)
        }
        val id = resources.getIdentifier(entry, type, packageName)
        if (id == 0) return null
        // A vector that reads its tint from a theme attribute needs a theme of
        // its own package, not the launcher's, to inflate at all.
        val theme = if (packageName == FRAMEWORK) context.theme else resources.newTheme()
        return resources.getDrawable(id, theme)
    }

    private const val FRAMEWORK = "android"
    private const val FALLBACK = "android:drawable/ic_zen_mode_type_other"
}
