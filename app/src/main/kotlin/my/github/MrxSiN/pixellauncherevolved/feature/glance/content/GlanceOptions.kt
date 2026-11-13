package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R

/**
 * One choice a source offers about its own content, stored as `glance_option_<source id>_<name>`.
 * Absent is the default, so a source with nothing set behaves as before. A [Type.CHOICE] picks one
 * of [choices]; [Type.TEXT] is free text; [Type.CALENDAR] is the calendar filter, whose choices are
 * read from the calendar app when settings opens it; [Type.APPS] is a set of launchable apps, stored as
 * comma-separated package names.
 */
class GlanceOption(
    val name: String,
    @param:StringRes val titleRes: Int,
    val type: Type = Type.CHOICE,
    /** What absent reads as, in the choice list. */
    @param:StringRes val defaultRes: Int = 0,
    val choices: List<Choice> = emptyList(),
    /**
     * A [Type.CHOICE] whose value is a number may also take any other whole number the person types:
     * this plural reads it ("%d minutes"); 0 means only the listed choices.
     */
    @param:PluralsRes val customRes: Int = 0,
) {
    /** [APPS] is a set of package names, comma-separated; [WIDGET] is an installed app widget, stored "component|id". */
    enum class Type { CHOICE, TEXT, CALENDAR, APPS, WIDGET }

    class Choice(val value: String, @param:StringRes val labelRes: Int)
}

/** Keys, shared options and the pure rules behind [GlanceOption]s; tested without a launcher. */
internal object GlanceOptions {

    const val KEY_PREFIX = "glance_option_"

    /** `glance_seen_<source id>`: Hide after's stored start ([GlanceHideAfter.encode]); state, never backed up. */
    const val SEEN_PREFIX = "glance_seen_"
    const val HIDE_AFTER = "hide_after"
    const val CALENDARS = "calendars"
    const val NAME = "name"

    fun key(sourceId: String, name: String): String = "$KEY_PREFIX${sourceId}_$name"

    /** Applies to every source: hide its content this many minutes after it last changed. */
    val hideAfter = GlanceOption(
        HIDE_AFTER, R.string.glance_option_hide_after_title, GlanceOption.Type.CHOICE, R.string.glance_option_hide_after_never,
        listOf(
            GlanceOption.Choice("5", R.string.glance_option_hide_after_5),
            GlanceOption.Choice("15", R.string.glance_option_hide_after_15),
            GlanceOption.Choice("30", R.string.glance_option_hide_after_30),
            GlanceOption.Choice("60", R.string.glance_option_hide_after_60),
        ),
        R.plurals.glance_unit_minutes,
    )

    /** Shared by the calendar card and the next-event extra. */
    val calendars = GlanceOption(CALENDARS, R.string.glance_option_calendars_title, GlanceOption.Type.CALENDAR, R.string.glance_option_calendars_all)

    /** Every option [source] shows in settings: its own, then Hide after. */
    fun of(source: GlanceSource): List<GlanceOption> = source.options + hideAfter

    /** A stored whole number, or [default] for absent, unreadable or not above 0. */
    fun number(value: String?, default: Int): Int = value?.toIntOrNull()?.takeIf { it > 0 } ?: default

    /** Minutes in a stored Hide after value; 0 (never) for absent or anything unreadable. */
    fun hideMinutes(value: String?): Int = value?.toIntOrNull()?.takeIf { it > 0 } ?: 0

    /** Splits a stored key into its source id and option name; the longest listed id that prefixes it wins. */
    fun split(key: String, ids: Collection<String>): Pair<String, String>? {
        if (!key.startsWith(KEY_PREFIX)) return null
        val rest = key.substring(KEY_PREFIX.length)
        val id = ids.filter { rest.startsWith(it + "_") && rest.length > it.length + 1 }.maxByOrNull { it.length } ?: return null
        return id to rest.substring(id.length + 1)
    }

    /**
     * What "same content" means for Hide after: a hash of the title and subtitle. Only the hash is
     * stored, so no shown text (a notification's, say) is written to disk.
     */
    fun fingerprint(item: GlanceItem): Int = item.title.toString().hashCode() * 31 + (item.subtitle?.toString()?.hashCode() ?: 0)
}

/**
 * Hide after's clock: per source, when the content it shows now first appeared (wall-clock time, so a
 * stored start survives a launcher restart). A different fingerprint restarts it; nothing shown clears
 * it. Each start or clear is handed to [persist] (`null` clears), only when it changes, never per call.
 * Called from whichever thread builds the list, so synchronized; there is no timer here, the feature
 * arms one for [check]'s earliest answer.
 */
internal class GlanceHideAfter(private val persist: (id: String, value: String?) -> Unit = { _, _ -> }) {

    private val fingerprints = HashMap<String, Int>()
    private val since = HashMap<String, Long>()

    /** When [id]'s content, [fingerprint] as of [now], expires after [minutes]; [NEVER] when [minutes] is 0. */
    @Synchronized
    fun check(id: String, fingerprint: Int, now: Long, minutes: Int): Long {
        if (minutes <= 0) {
            forget(id)
            return NEVER
        }
        if (fingerprints[id] != fingerprint) {
            fingerprints[id] = fingerprint
            since[id] = now
            persist(id, encode(now, fingerprint))
        }
        return since.getValue(id) + minutes * MINUTE
    }

    @Synchronized
    fun forget(id: String) {
        if (since.remove(id) == null) return
        fingerprints.remove(id)
        persist(id, null)
    }

    /** Takes [id]'s stored start back ([encode]d), unless it already has one; unreadable is ignored. */
    @Synchronized
    fun restore(id: String, value: String?) {
        if (id in since) return
        val (start, fingerprint) = decode(value) ?: return
        since[id] = start
        fingerprints[id] = fingerprint
    }

    /** Forgets every source not in [ids], stored starts included. */
    @Synchronized
    fun retain(ids: Set<String>) {
        for (id in since.keys.filter { it !in ids }) forget(id)
    }

    companion object {
        const val NEVER = Long.MAX_VALUE
        const val MINUTE = 60_000L

        fun encode(since: Long, fingerprint: Int): String = "$since:$fingerprint"

        fun decode(value: String?): Pair<Long, Int>? {
            val at = value?.indexOf(':') ?: return null
            if (at <= 0) return null
            val start = value.substring(0, at).toLongOrNull() ?: return null
            val fingerprint = value.substring(at + 1).toIntOrNull() ?: return null
            return start to fingerprint
        }
    }
}
