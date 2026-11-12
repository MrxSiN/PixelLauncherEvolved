package my.github.MrxSiN.pixellauncherevolved.glance

import android.net.Uri
import android.os.Bundle

/**
 * What the module's app and the launcher agree on for calendar events: both sides are built from
 * this file. The events travel as parallel arrays in one [Bundle] from `ContentResolver.call`.
 */
object CalendarWire {
    const val AUTHORITY_SUFFIX: String = ".calendar"
    const val METHOD_EVENTS: String = "events"

    const val METHOD_CALENDARS: String = "calendars"

    private const val FILTER = "filter"
    private const val NAMES = "names"
    private const val ACCOUNTS = "accounts"
    private const val GRANTED = "granted"
    private const val IDS = "ids"
    private const val TITLES = "titles"
    private const val BEGINS = "begins"
    private const val ENDS = "ends"

    fun authority(modulePackage: String): Uri = Uri.parse("content://$modulePackage$AUTHORITY_SUFFIX")

    /** The module's app holds no Calendar permission. */
    fun denied(): Bundle = Bundle(1).apply { putBoolean(GRANTED, false) }

    fun events(ids: LongArray, titles: Array<String>, begins: LongArray, ends: LongArray): Bundle = Bundle(5).apply {
        putBoolean(GRANTED, true)
        putLongArray(IDS, ids)
        putStringArray(TITLES, titles)
        putLongArray(BEGINS, begins)
        putLongArray(ENDS, ends)
    }

    /** The calendar ids in a stored option value (comma-separated); empty for none or unreadable, meaning every calendar. */
    fun parseIds(text: String?): LongArray = text.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.toLongArray()

    /** The `events` call's arguments restricting it to [ids]; null for every calendar. */
    fun filter(ids: LongArray): Bundle? = if (ids.isEmpty()) null else Bundle(1).apply { putLongArray(FILTER, ids) }

    /** The calendar ids an `events` call asks for; empty for every calendar. */
    fun filterOf(extras: Bundle?): LongArray = extras?.getLongArray(FILTER) ?: LongArray(0)

    fun calendars(ids: LongArray, names: Array<String>, accounts: Array<String>): Bundle = Bundle(4).apply {
        putBoolean(GRANTED, true)
        putLongArray(IDS, ids)
        putStringArray(NAMES, names)
        putStringArray(ACCOUNTS, accounts)
    }

    /** Null when access is denied or the answer is malformed; otherwise the visible calendars. */
    fun decodeCalendars(bundle: Bundle?): CalendarList? {
        if (bundle == null || !bundle.getBoolean(GRANTED)) return null
        val ids = bundle.getLongArray(IDS) ?: return null
        val names = bundle.getStringArray(NAMES) ?: return null
        val accounts = bundle.getStringArray(ACCOUNTS) ?: return null
        if (names.size != ids.size || accounts.size != ids.size) return null
        return CalendarList(ids, Array(ids.size) { names[it].orEmpty() }, Array(ids.size) { accounts[it].orEmpty() })
    }

    /** True when the four arrays describe the same events. */
    fun consistent(ids: LongArray, titles: Array<String>, begins: LongArray, ends: LongArray): Boolean =
        titles.size == ids.size && begins.size == ids.size && ends.size == ids.size

    /** Null when access is denied or the answer is malformed; otherwise the events. */
    fun decode(bundle: Bundle?): CalendarEvents? {
        if (bundle == null || !bundle.getBoolean(GRANTED)) return null
        val ids = bundle.getLongArray(IDS) ?: return null
        val raw = bundle.getStringArray(TITLES) ?: return null
        val begins = bundle.getLongArray(BEGINS) ?: return null
        val ends = bundle.getLongArray(ENDS) ?: return null
        val titles = Array(raw.size) { raw[it].orEmpty() }
        return if (consistent(ids, titles, begins, ends)) CalendarEvents(ids, titles, begins, ends) else null
    }
}

/** The visible calendars: id, display name and the account each belongs to. */
class CalendarList(val ids: LongArray, val names: Array<String>, val accounts: Array<String>)

class CalendarEvents(val ids: LongArray, val titles: Array<String>, val begins: LongArray, val ends: LongArray)
