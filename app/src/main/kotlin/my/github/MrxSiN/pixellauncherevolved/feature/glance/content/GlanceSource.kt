package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.os.Handler

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * One kind of content At a Glance can show, rewritten from a Smartspacer built-in.
 *
 * A source runs in the launcher's process while it is switched on: [start] registers whatever tells
 * it its content changed, and calls [GlanceHost.changed] each time; [content] answers what to show
 * right now, or null for nothing. [start] and [stop] run on the main thread; [content] may run on
 * any thread (the launcher hands At a Glance its targets from a background thread too), so it only
 * reads state kept in `@Volatile` fields. Nothing here may block: no Binder call that can wait, no
 * disk, no network.
 *
 * Smartspacer's terms: a [Kind.CARD] is a Target (its own page in At a Glance), a [Kind.EXTRA] a
 * Complication (a short item beside the date).
 */
abstract class GlanceSource(
    /** Stable and never shown; the stored switch is `glance_source_<id>`. */
    val id: String,
    val kind: Kind,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    enum class Kind { CARD, EXTRA }

    /** A permission the launcher's process must hold for this source to show anything, or null. */
    open val permission: String? = null

    /** What this source lets the person choose about its content; Hide after applies to every source besides. */
    open val options: List<GlanceOption> = emptyList()

    /** Set before [start]; valid until [stop]. */
    lateinit var host: GlanceHost

    abstract fun start()

    abstract fun stop()

    abstract fun content(): GlanceItem?

    /** Main thread: one of the `glance_option_` values changed. Cache what is needed in `@Volatile` fields. */
    open fun onOptionsChanged() {}

    /** This source's stored value for option [name], or null for the default. */
    protected fun option(name: String): String? = host.option(id, name)
}

/**
 * What a source shows.
 *
 * For a card, [title] and [subtitle] are its two lines. For an extra, only [title] (keep it short,
 * Smartspacer trims to 12 characters) and [icon] show. [feature] names a `SmartspaceTarget.FEATURE_*`
 * constant, resolved at run time; unknown names read as `FEATURE_UNDEFINED`.
 */
data class GlanceItem(
    val title: CharSequence,
    val subtitle: CharSequence? = null,
    val icon: Icon? = null,
    val tap: PendingIntent? = null,
    val tapIntent: Intent? = null,
    val feature: String = "FEATURE_UNDEFINED",
    /** Whether the icon takes the At a Glance text colour; false keeps a coloured icon as drawn. */
    val tintIcon: Boolean = true,
)

/** What a running source may use. */
interface GlanceHost {
    /** The launcher's application context. */
    val context: Context

    /** This module's resources, for strings; null when its APK cannot be opened. */
    val resources: Resources?

    /** This module's package, for icons drawn from its resources ([icon]). */
    val modulePackage: String

    val main: Handler

    val logger: Logger

    /** Tells At a Glance this source's [GlanceSource.content] may have changed. Main thread or not. */
    fun changed()

    /** The stored value of [source]'s option [name] (`glance_option_<source>_<name>`), or null. */
    fun option(source: String, name: String): String?

    /** One of this module's drawables as an Icon the launcher can draw. */
    fun icon(drawable: Int): Icon = Icon.createWithResource(modulePackage, drawable)

    fun string(@StringRes id: Int, vararg args: Any): String = resources?.getString(id, *args).orEmpty()
}
