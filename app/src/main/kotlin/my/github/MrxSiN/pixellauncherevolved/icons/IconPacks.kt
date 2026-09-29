package my.github.MrxSiN.pixellauncherevolved.icons

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources

/** An installed icon pack, as the selector lists it. */
data class IconPack(val packageName: String, val label: CharSequence, val version: Long)

/**
 * The icon packs this device has, and the way into one.
 *
 * The list is what [IconPackFormat] recognises as a pack rather than every app
 * that happens to contain drawables, and it is built on a cold path: the
 * selector asks for it when it opens, and the catalogue it draws from is thrown
 * away when a package is added, changed or removed rather than rebuilt per
 * frame. Nothing here is reached from an icon lookup.
 */
object IconPacks {

    /**
     * Every recognised pack, named as its app is named, in reading order.
     *
     * A pack whose version cannot be read is left out: without one there is
     * nothing to key a compiled index by, and an index that cannot go stale is
     * an index that never picks up a pack update.
     */
    fun installed(context: Context): List<IconPack> {
        val packageManager = context.packageManager
        return IconPackFormat.installedPacks(packageManager)
            .mapNotNull { name ->
                val version = versionOf(context, name) ?: return@mapNotNull null
                IconPack(name, labelOf(packageManager, name) ?: name, version)
            }
            .sortedBy { it.label.toString().lowercase() }
    }

    fun labelOf(packageManager: PackageManager, packageName: String): CharSequence? = runCatching {
        packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager)
    }.getOrNull()

    /** Null once the pack is gone, which is what makes a selection fall back. */
    fun versionOf(context: Context, packageName: String): Long? = runCatching {
        context.packageManager.getPackageInfo(packageName, 0).longVersionCode
    }.getOrNull()

    fun resourcesOf(context: Context, packageName: String): Resources? = runCatching {
        context.packageManager.getResourcesForApplication(packageName)
    }.getOrNull()

    /** Whether [packageName] declares itself an icon pack, whatever its state. */
    fun isPack(context: Context, packageName: String): Boolean =
        IconPackFormat.installedPacks(context.packageManager, packageName).isNotEmpty()

    /** Whether [packageName] is still a pack this module can use. */
    fun isUsable(context: Context, packageName: String): Boolean =
        versionOf(context, packageName) != null &&
            IconPackFormat.installedPacks(context.packageManager).contains(packageName)
}
