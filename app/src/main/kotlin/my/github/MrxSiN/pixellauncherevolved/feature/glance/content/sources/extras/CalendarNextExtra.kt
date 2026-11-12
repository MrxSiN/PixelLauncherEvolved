package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.extras

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.CalendarContract
import android.text.format.DateFormat

import java.time.ZoneId
import java.util.Date

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource

/**
 * The start time of the next calendar event within 24 hours. Rewritten from the behaviour of
 * Smartspacer's calendar target, as a short item.
 *
 * Needs `READ_CALENDAR`, which the launcher process may not hold (a module cannot grant it); without
 * it the source shows nothing. The query runs on one `HandlerThread` owned by the source (started in
 * [start], quit in [stop]), never the main thread. It reruns when the calendar provider changes
 * (`ContentObserver`), when the next event starts, and at midnight when no event is in the window;
 * there is no polling. All-day events are skipped.
 */
class CalendarNextExtra : GlanceSource("calendar_next", Kind.EXTRA, R.string.glance_extra_calendar_title, R.string.glance_extra_calendar_description) {

    @Volatile private var begin = 0L
    @Volatile private var text = ""

    private var thread: HandlerThread? = null
    @Volatile private var worker: Handler? = null

    private val query = Runnable { load() }

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            worker?.let { it.removeCallbacks(query); it.post(query) }
        }
    }

    override fun start() {
        val started = HandlerThread("ple-glance-calendar").also { it.start() }
        thread = started
        worker = Handler(started.looper).also { it.post(query) }
        host.context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
    }

    override fun stop() {
        runCatching { host.context.contentResolver.unregisterContentObserver(observer) }
        worker?.removeCallbacks(query)
        worker = null
        thread?.quitSafely()
        thread = null
    }

    /** Runs on the worker thread. */
    private fun load() {
        val handler = worker ?: return
        handler.removeCallbacks(query)
        val now = System.currentTimeMillis()
        var found = 0L
        if (host.context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .also { ContentUris.appendId(it, now); ContentUris.appendId(it, now + DAY_MILLIS) }
                .build()
            runCatching {
                host.context.contentResolver.query(
                    uri, PROJECTION, "${CalendarContract.Instances.ALL_DAY}=0", null, "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val start = c.getLong(0)
                        if (start > now) { found = start; break }
                    }
                }
            }
        }
        val shown = if (found == 0L) "" else DateFormat.getTimeFormat(host.context).format(Date(found))
        val at = if (found == 0L) GlanceExtraLogic.nextMidnight(now, ZoneId.systemDefault()) else found
        handler.postAtTime(query, SystemClock.uptimeMillis() + (at - now))
        if (found == begin && shown == text) return
        begin = found
        text = shown
        host.changed()
    }

    override fun content(): GlanceItem? {
        val shown = text
        if (shown.isEmpty()) return null
        return GlanceItem(
            title = shown,
            icon = host.icon(R.drawable.ic_glance_extra_date),
            tapIntent = Intent(Intent.ACTION_VIEW)
                .setData(ContentUris.appendId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time"), begin).build())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        val PROJECTION = arrayOf(CalendarContract.Instances.BEGIN)
    }
}
