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

class CalendarEvents(val ids: LongArray, val titles: Array<String>, val begins: LongArray, val ends: LongArray)
