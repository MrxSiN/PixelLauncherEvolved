package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.extras

import android.content.ContentUris
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.CalendarContract
import android.text.format.DateFormat

import java.time.ZoneId
import java.util.Date

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOptions
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.CalendarBridge

/**
 * The start time of the next calendar event within 24 hours. Rewritten from the behaviour of
 * Smartspacer's calendar target, as a short item.
 *
 * The launcher process holds no Calendar permission, so the events come from this module's app
 * ([CalendarBridge]), which the person lets read the calendar from the At a Glance settings; without
 * that the source shows nothing. The read runs on one `HandlerThread` owned by the source (started in
 * [start], quit in [stop]), never the main thread. It reruns when the screen comes on or the phone is
 * unlocked, when the next event starts, and at midnight when no event is in the window; there is no
 * polling. The calendar provider cannot be watched from here: registering an observer on it needs the
 * Calendar permission too (measured on CP4A). All-day events are skipped.
 */
class CalendarNextExtra : GlanceSource("calendar_next", Kind.EXTRA, R.string.glance_extra_calendar_title, R.string.glance_extra_calendar_description) {

    override val options = listOf(GlanceOptions.calendars)

    /** The stored calendar ids ([GlanceOptions.CALENDARS]); null reads every calendar. */
    @Volatile private var calendars: String? = null

    @Volatile private var begin = 0L
    @Volatile private var text = ""

    private var thread: HandlerThread? = null
    @Volatile private var worker: Handler? = null

    private val query = Runnable { load() }

    private val looked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            worker?.let { it.removeCallbacks(query); it.post(query) }
        }
    }

    override fun onOptionsChanged() {
        val chosen = option(GlanceOptions.CALENDARS)
        if (chosen == calendars) return
        calendars = chosen
        worker?.let { it.removeCallbacks(query); it.post(query) }
    }

    override fun start() {
        calendars = option(GlanceOptions.CALENDARS)
        val started = HandlerThread("ple-glance-calendar").also { it.start() }
        thread = started
        worker = Handler(started.looper).also { it.post(query) }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) }
        host.context.registerReceiver(looked, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun stop() {
        runCatching { host.context.unregisterReceiver(looked) }
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
        val events = CalendarBridge.read(host.context.contentResolver, host.modulePackage, host.logger, calendars)
        if (events != null) {
            for (i in events.begins.indices) {
                val start = events.begins[i]
                if (start > now && start <= now + DAY_MILLIS) { found = start; break }
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
    }
}
