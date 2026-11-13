package my.github.MrxSiN.pixellauncherevolved.glance

import my.github.MrxSiN.pixellauncherevolved.focus.FocusProvider
import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log

import java.util.TimeZone

import my.github.MrxSiN.pixellauncherevolved.core.AndroidLogger
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards.GlanceCardTimes

/**
 * Answers the launcher's At a Glance calendar sources with today's and tomorrow's events.
 *
 * Pixel Launcher does not hold the Calendar permission and a module cannot give it one, so this
 * app holds it (granted once, from the At a Glance settings) and reads here. Exported, so every
 * call is checked against the one caller with any business asking: a calendar is personal.
 * Changes are not announced from here, and the launcher cannot watch the calendar provider either
 * (that needs the permission too); the sources read again on screen on, unlock and event times.
 */
class CalendarProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (callingPackage != FocusProvider.LAUNCHER_PACKAGE || (method != CalendarWire.METHOD_EVENTS && method != CalendarWire.METHOD_CALENDARS)) return null
        val context = context ?: return null
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return CalendarWire.denied()
        }
        return if (method == CalendarWire.METHOD_CALENDARS) calendars(context) else events(context, CalendarWire.filterOf(extras))
    }

    /** The visible calendars, for the launcher's settings to offer as a choice. */
    private fun calendars(context: android.content.Context): Bundle {
        val ids = ArrayList<Long>()
        val names = ArrayList<String>()
        val accounts = ArrayList<String>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME),
                "${CalendarContract.Calendars.VISIBLE}=1", null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use {
                while (it.moveToNext() && ids.size < LIMIT) {
                    ids += it.getLong(0); names += it.getString(1).orEmpty(); accounts += it.getString(2).orEmpty()
                }
            }
        }.onFailure { Log.w(AndroidLogger.TAG, "The calendars could not be listed", it) }
        return CalendarWire.calendars(ids.toLongArray(), names.toTypedArray(), accounts.toTypedArray())
    }

    /** Today's and tomorrow's events; [calendars] empty reads every visible calendar, otherwise only those ids. */
    private fun events(context: android.content.Context, calendars: LongArray): Bundle {
        val begin = GlanceCardTimes.startOfDay(System.currentTimeMillis(), TimeZone.getDefault())
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendEncodedPath(begin.toString())
            .appendEncodedPath((begin + 2 * GlanceCardTimes.DAY).toString())
            .build()
        var ids = LongArray(CAPACITY)
        var begins = LongArray(CAPACITY)
        var ends = LongArray(CAPACITY)
        var titles = arrayOfNulls<String>(CAPACITY)
        var n = 0
        runCatching {
            context.contentResolver.query(
                uri, PROJECTION,
                "${CalendarContract.Instances.VISIBLE}=1 AND ${CalendarContract.Instances.ALL_DAY}=0 AND " +
                    // An event with no status (one made on the phone, say) is not cancelled; SQL's NULL != 2 is not true.
                    "(${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS}!=${CalendarContract.Events.STATUS_CANCELED})" +
                    // Longs only, so inlining them cannot inject anything.
                    if (calendars.isEmpty()) "" else " AND ${CalendarContract.Instances.CALENDAR_ID} IN (${calendars.joinToString(",")})",
                null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use {
                while (it.moveToNext() && n < LIMIT) {
                    if (n == ids.size) {
                        ids = ids.copyOf(n * 2); begins = begins.copyOf(n * 2); ends = ends.copyOf(n * 2); titles = titles.copyOf(n * 2)
                    }
                    ids[n] = it.getLong(0); titles[n] = it.getString(1); begins[n] = it.getLong(2); ends[n] = it.getLong(3)
                    n++
                }
            }
        }.onFailure { Log.w(AndroidLogger.TAG, "The calendar could not be read", it) }
        return CalendarWire.events(ids.copyOf(n), Array(n) { titles[it].orEmpty() }, begins.copyOf(n), ends.copyOf(n))
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val CAPACITY = 32
        const val LIMIT = 512

        // The four columns the launcher uses.
        val PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
        )
    }
}
