package my.github.MrxSiN.pixellauncherevolved.icons

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser

import org.xmlpull.v1.XmlPullParser

/**
 * The icon pack conventions this module knows about.
 *
 * There is no Android icon pack standard. What exists is a set of conventions
 * that grew around third-party launchers: a pack declares itself by answering
 * one of a handful of intents, and describes itself in an `appfilter` document
 * that is either an asset or an XML resource. Every pack on the Play Store
 * follows one or more of them, and they agree on the parts that matter.
 *
 * Kept as data in one versioned place, so supporting another convention is a
 * line here rather than a change to the parser, the catalogue or the hooks.
 * [VERSION] is stored beside a compiled index: raising it rebuilds every index,
 * which is what makes a widened convention reach packs already indexed.
 */
object IconPackFormat {

    /**
     * Raised whenever a change here would produce a different index for the same
     * pack, or a different icon from the same index: it is part of every icon's
     * freshness identifier, so raising it regenerates the launcher's stored icons.
     */
    const val VERSION: Int = 3

    /**
     * What a pack answers to say it is one.
     *
     * A theme-engine action, in practice, rather than anything about drawables:
     * an app that merely contains images answers none of these, which is what
     * keeps the selector from offering every app on the device.
     */
    val ACTIONS: List<String> = listOf(
        "org.adw.launcher.THEMES",
        "org.adw.launcher.icons.ACTION_PICK_ICON",
        "com.gau.go.launcherex.theme",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.dlto.atom.launcher.THEME",
        "ch.deletescape.lawnchair.ICONPACK",
        "com.teslacoilsw.launcher.THEME",
    )

    /** Categories some packs use in place of an action of their own. */
    val CATEGORIES: List<String> = listOf(
        "com.anddoes.launcher.THEME",
        "com.novalauncher.THEME",
    )

    /** The documents that map components to drawables, in the order they are tried. */
    private val APP_FILTER_ASSETS = listOf("appfilter.xml")
    private val APP_FILTER_RESOURCES = listOf("appfilter")

    /** The documents that list every drawable a pack offers, for the per-app editor. */
    private val DRAWABLE_ASSETS = listOf("drawable.xml", "icon_pack.xml")
    private val DRAWABLE_RESOURCES = listOf("drawable", "icon_pack", "iconpack")

    /** Every package that answers one of [ACTIONS] or carries one of [CATEGORIES]. */
    fun installedPacks(packageManager: PackageManager, only: String? = null): Set<String> {
        val found = LinkedHashSet<String>()
        for (action in ACTIONS) collect(packageManager, Intent(action).setPackage(only), found)
        for (category in CATEGORIES) {
            collect(packageManager, Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(only), found)
        }
        return found
    }

    /** The pack's component map, or null when it ships none this module can read. */
    fun openAppFilter(resources: Resources, packageName: String): XmlPullParser? =
        open(resources, packageName, APP_FILTER_ASSETS, APP_FILTER_RESOURCES)

    /** The pack's list of every drawable it offers, or null when it ships none. */
    fun openDrawableList(resources: Resources, packageName: String): XmlPullParser? =
        open(resources, packageName, DRAWABLE_ASSETS, DRAWABLE_RESOURCES)

    private fun collect(packageManager: PackageManager, probe: Intent, into: MutableSet<String>) {
        runCatching { packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL) }
            .getOrNull()
            ?.forEach { into += it.activityInfo.packageName }
    }

    /**
     * An asset first, then an XML resource.
     *
     * Assets are tried first because a pack that ships both keeps the asset
     * authoritative: the resource copy is usually the one its build tool
     * generated, and shrinking can leave it behind a version.
     */
    private fun open(
        resources: Resources,
        packageName: String,
        assets: List<String>,
        names: List<String>,
    ): XmlPullParser? {
        for (asset in assets) {
            val parser = runCatching {
                android.util.Xml.newPullParser().apply {
                    setInput(resources.assets.open(asset), null)
                }
            }.getOrNull()
            if (parser != null) return parser
        }
        for (name in names) {
            val id = runCatching { resources.getIdentifier(name, "xml", packageName) }.getOrDefault(0)
            if (id == 0) continue
            val parser: XmlResourceParser? = runCatching { resources.getXml(id) }.getOrNull()
            if (parser != null) return parser
        }
        return null
    }
}
