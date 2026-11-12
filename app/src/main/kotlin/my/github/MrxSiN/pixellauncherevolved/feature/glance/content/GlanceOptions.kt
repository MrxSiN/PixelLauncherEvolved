package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

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
) {
    enum class Type { CHOICE, TEXT, CALENDAR, APPS }

    class Choice(val value: String, @param:StringRes val labelRes: Int)
}

/** Keys, shared options and the pure rules behind [GlanceOption]s; tested without a launcher. */
internal object GlanceOptions {

    const val KEY_PREFIX = "glance_option_"
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
    )

    /** Shared by the calendar card and the next-event extra. */
    val calendars = GlanceOption(CALENDARS, R.string.glance_option_calendars_title, GlanceOption.Type.CALENDAR, R.string.glance_option_calendars_all)

    /** Every option [source] shows in settings: its own, then Hide after. */
    fun of(source: GlanceSource): List<GlanceOption> = source.options + hideAfter

    /** Minutes in a stored Hide after value; 0 (never) for absent or anything unreadable. */
    fun hideMinutes(value: String?): Int = value?.toIntOrNull()?.takeIf { it > 0 } ?: 0

    /** Splits a stored key into its source id and option name; the longest listed id that prefixes it wins. */
    fun split(key: String, ids: Collection<String>): Pair<String, String>? {
        if (!key.startsWith(KEY_PREFIX)) return null
        val rest = key.substring(KEY_PREFIX.length)
        val id = ids.filter { rest.startsWith(it + "_") && rest.length > it.length + 1 }.maxByOrNull { it.length } ?: return null
        return id to rest.substring(id.length + 1)
    }

    /** What "same content" means for Hide after. */
    fun fingerprint(item: GlanceItem): String = "${item.title}\u0000${item.subtitle ?: ""}"
}

/**
 * Hide after's clock: per source, when the content it shows now first appeared. A different
 * fingerprint restarts it; nothing shown clears it. Called from whichever thread builds the list, so
 * synchronized; there is no timer here, the feature arms one for [check]'s earliest answer.
 */
internal class GlanceHideAfter {

    private val fingerprints = HashMap<String, String>()
    private val since = HashMap<String, Long>()

    /** When [id]'s content, [fingerprint] as of [now], expires after [minutes]; [NEVER] when [minutes] is 0. */
    @Synchronized
    fun check(id: String, fingerprint: String, now: Long, minutes: Int): Long {
        if (minutes <= 0) {
            forget(id)
            return NEVER
        }
        if (fingerprints[id] != fingerprint) {
            fingerprints[id] = fingerprint
            since[id] = now
        }
        return since.getValue(id) + minutes * MINUTE
    }

    @Synchronized
    fun forget(id: String) {
        fingerprints.remove(id)
        since.remove(id)
    }

    /** Forgets every source not in [ids]. */
    @Synchronized
    fun retain(ids: Set<String>) {
        fingerprints.keys.retainAll(ids)
        since.keys.retainAll(ids)
    }

    companion object {
        const val NEVER = Long.MAX_VALUE
        const val MINUTE = 60_000L
    }
}
