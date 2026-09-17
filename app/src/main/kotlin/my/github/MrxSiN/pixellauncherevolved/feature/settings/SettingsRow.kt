package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources

import java.util.Collections
import java.util.IdentityHashMap

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R

import my.github.MrxSiN.pixellauncherevolved.catalog.CatalogEntry
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.diagnostics.LauncherAnalysis
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsStore

/**
 * One row, or a few rows that belong together, on one of this module's pages.
 *
 * A page is a list of these, so a new kind of row is a new class and no page
 * builder learns what it does.
 */
internal fun interface SettingsRow {

    /** Adds this row's preferences to [group], which is a page or a heading on one. */
    fun addTo(group: Any, scope: RowScope)
}

/** What rows need from outside Home settings, fixed for as long as the launcher runs. */
internal class SettingsEnvironment(
    val logger: Logger,
    /** This module's own package, which its providers are addressed by. */
    val modulePackage: String,
    /** What this launcher build offers each tweak; worked out on first use. */
    val analysis: () -> LauncherAnalysis,
    val xposedApiVersion: Int,
    val frameworkName: String,
    val frameworkVersion: String,
    val onRestart: () -> Unit,
)

/**
 * Everything a row is built with, for the one page being built.
 *
 * It also carries what rows on one page tell each other: a row that only means
 * something while a switch is on is greyed out with that switch, wherever on the
 * page either of them sits.
 */
internal class RowScope(
    val api: PreferenceApi,
    val resources: Resources,
    val settings: SettingsStore,
    val environment: SettingsEnvironment,
    val context: Context,
    private val accessories: RowAccessories,
) {

    private val dependents = mutableMapOf<CatalogEntry, MutableList<Any>>()
    private val unavailable = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

    fun string(@StringRes id: Int, vararg arguments: Any): String = resources.getString(id, *arguments)

    /** Whether the switch for [entry] reads on. */
    fun isOn(entry: CatalogEntry): Boolean = entry.shown(settings[entry.setting])

    /** Stores what the switch for [entry] now reads, and greys the rows that follow it. */
    fun switch(entry: CatalogEntry, on: Boolean) {
        settings.put(entry.setting, entry.stored(on))
        dependents[entry]?.forEach { enable(it, on) }
    }

    /** Greys [row] out whenever the switch for [entry] is off. */
    fun dependOn(entry: CatalogEntry, row: Any) {
        enable(row, isOn(entry))
        dependents.getOrPut(entry) { mutableListOf() } += row
    }

    /**
     * Greys [row] out for good, and says why, when this launcher build lacks
     * what [feature] stands on.
     *
     * The registry does not install such a tweak, so a switch left working
     * would be a switch that does nothing. Nothing stored changes: the tweak
     * comes back the moment a launcher or module update brings its members back.
     */
    fun requires(feature: CompatibilityFeature, row: Any) {
        val analysis = environment.analysis()
        if (analysis.isAvailable(feature)) return

        unavailable += row
        api.setEnabled(row, false)
        api.setSummary(row, string(R.string.compatibility_unavailable, analysis.launcherVersion))
    }

    private fun enable(row: Any, on: Boolean) = api.setEnabled(row, on && row !in unavailable)

    /** Draws [accessory] at the end of the row keyed [key] each time it binds. */
    fun accessory(key: String, accessory: () -> RowAccessory) = accessories.register(key, accessory)

    /** A row that opens another screen or dialog. */
    fun link(key: String, @StringRes title: Int, summary: CharSequence, onClick: () -> Unit): Any =
        api.createAction(context, key, string(title), summary, onClick)
}

/** Keys of this module's rows, which share a namespace with the launcher's own. */
internal object SettingsKeys {
    const val PREFIX = "ple_"
    const val PAGE_PREFIX = PREFIX + "page_"

    fun row(name: String): String = PREFIX + name

    fun page(page: SettingsPage): String = PAGE_PREFIX + page.key
}

/** The activity behind a themed preference context. */
internal fun Context.activityOrNull(): Activity? {
    var current: Context? = this
    while (current != null) {
        if (current is Activity) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}
