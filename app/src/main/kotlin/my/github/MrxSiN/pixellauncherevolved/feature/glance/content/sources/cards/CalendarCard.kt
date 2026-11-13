package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.CalendarContract
import android.text.format.DateFormat

import java.util.Date
import java.util.TimeZone

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOption
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOptions
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.CalendarBridge

/**
 * The current or next calendar event. Rewritten from Smartspacer's `CalendarTarget` with its default
 * settings: every visible calendar, events starting within 15 minutes or already started and not yet
 * over, no all-day or cancelled events. Title (with "in 12m" for the last hour), the time range as
 * subtitle, and a tap that opens the event. The calendars option narrows it to one calendar (Smartspacer
 * allows several). Without dismissing, location
 * line and several events at once: the earliest event that qualifies is shown.
 *
 * The launcher's process holds no Calendar permission, so the events come from this module's app
 * ([CalendarBridge]), which the person lets read the calendar from the At a Glance settings; without
 * that the card shows nothing. The calendar provider cannot be watched from the launcher (an observer on
 * it needs the Calendar permission too, measured on CP4A), so the card reads again when the screen
 * comes on or the phone is unlocked.
 *
 * The events are read on one background thread owned by the card, for today and
 * tomorrow, on screen on and unlock (debounced), when the time or zone changes, or the day turns. The events are kept in `@Volatile` arrays; [content] only picks
 * from them. A callback is scheduled for the next moment the card can differ (an event coming
 * within 15 minutes, starting or ending, and each minute while its countdown shows); there is no
 * polling.
 */
class CalendarCard : GlanceSource("calendar", Kind.CARD, R.string.glance_card_calendar_title, R.string.glance_card_calendar_description) {

    private class Events(
        val day: Long,
        val ids: LongArray,
        val titles: Array<String>,
        val starts: LongArray,
        val ends: LongArray,
    )

    override val options = listOf(
        GlanceOptions.calendars,
        GlanceOption(
            LEAD, R.string.glance_option_lead_title, GlanceOption.Type.CHOICE, R.string.glance_option_lead_default,
            listOf(
                GlanceOption.Choice("30", R.string.glance_option_lead_30),
                GlanceOption.Choice("60", R.string.glance_option_lead_60),
                GlanceOption.Choice("120", R.string.glance_option_lead_120),
            ),
            R.plurals.glance_unit_minutes_before,
        ),
    )

    /** How long before an event the card shows it ([LEAD]; 15 minutes by default). */
    @Volatile private var lead = LEAD_MILLIS

    /** The stored calendar ids ([GlanceOptions.CALENDARS]); null reads every calendar. */
    @Volatile private var calendars: String? = null

    @Volatile private var events: Events? = null
    private var thread: HandlerThread? = null
    @Volatile private var worker: Handler? = null

    private val timeChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    private val load = Runnable { read() }

    private val recheck = Runnable {
        val loaded = events
        if (loaded == null || loaded.day != GlanceCardTimes.startOfDay(System.currentTimeMillis(), TimeZone.getDefault())) {
            reload()
        } else {
            reschedule()
        }
    }

    override fun onOptionsChanged() {
        val ahead = GlanceOptions.number(option(LEAD), (LEAD_MILLIS / GlanceCardTimes.MINUTE).toInt()) * GlanceCardTimes.MINUTE
        val chosen = option(GlanceOptions.CALENDARS)
        if (ahead != lead) {
            lead = ahead
            reload()
            return
        }
        if (chosen == calendars) return
        calendars = chosen
        reload()
    }

    override fun start() {
        val context = host.context
        calendars = option(GlanceOptions.CALENDARS)
        lead = GlanceOptions.number(option(LEAD), (LEAD_MILLIS / GlanceCardTimes.MINUTE).toInt()) * GlanceCardTimes.MINUTE
        val looper = HandlerThread("ple-glance-calendar").also { it.start() }
        thread = looper
        worker = Handler(looper.looper)
        val filter = IntentFilter(Intent.ACTION_TIME_CHANGED).apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(timeChanged, filter, Context.RECEIVER_NOT_EXPORTED)
        reload()
    }

    override fun stop() {
        val looper = thread ?: return
        host.main.removeCallbacks(recheck)
        runCatching { host.context.unregisterReceiver(timeChanged) }
        worker?.removeCallbacksAndMessages(null)
        looper.quitSafely()
        thread = null
        worker = null
        events = null
    }

    /** Reads the events again shortly, off the main thread; bursts of provider changes read once. */
    private fun reload() {
        val handler = worker ?: return
        handler.removeCallbacks(load)
        handler.postDelayed(load, DEBOUNCE_MILLIS)
    }

    /** On the worker thread. */
    private fun read() {
        val now = System.currentTimeMillis()
        val zone = TimeZone.getDefault()
        val begin = GlanceCardTimes.startOfDay(now, zone)
        val read = CalendarBridge.read(host.context.contentResolver, host.modulePackage, host.logger, calendars)
        events = if (read == null) {
            Events(begin, LongArray(0), emptyArray(), LongArray(0), LongArray(0))
        } else {
            Events(begin, read.ids, read.titles, read.begins, read.ends)
        }
        host.main.post { reschedule() }
    }

    /** On the main thread: schedules the next moment this could change, and says it may have changed now. */
    private fun reschedule() {
        host.main.removeCallbacks(recheck)
        val loaded = events
        if (loaded != null) {
            val now = System.currentTimeMillis()
            val at = GlanceCardTimes.nextCalendarChange(
                loaded.starts, loaded.ends, now, lead, GlanceCardTimes.nextMidnight(now, TimeZone.getDefault()),
            )
            host.main.postAtTime(recheck, SystemClock.uptimeMillis() + (at - now))
        }
        host.changed()
    }

    override fun content(): GlanceItem? {
        val loaded = events ?: return null
        val now = System.currentTimeMillis()
        val zone = TimeZone.getDefault()
        for (i in loaded.starts.indices) {
            val start = loaded.starts[i]
            val end = loaded.ends[i]
            if (!GlanceCardTimes.shows(start, end, now, lead)) continue
            val time = DateFormat.getTimeFormat(host.context)
            val title = loaded.titles[i].ifBlank { host.string(R.string.glance_card_calendar_untitled) }
            val minutes = GlanceCardTimes.countdown(start, now)
            return GlanceItem(
                title = if (minutes > 0) host.string(R.string.glance_card_calendar_in_minutes, shorten(title), minutes) else title,
                subtitle = subtitle(time.format(Date(start)), time.format(Date(end)), start, end, now, zone),
                icon = host.icon(R.drawable.ic_glance_card_calendar),
                tapIntent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, loaded.ids[i]))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                // Left out while Pixel's own calendar card shows, as the torch card is.
                feature = "FEATURE_CALENDAR",
            )
        }
        return null
    }

    private fun shorten(title: String): String = if (title.length <= TITLE_LENGTH) title else title.substring(0, TITLE_LENGTH - 1) + "…"

    /** Smartspacer's wording: add the date to whichever end is not today. */
    private fun subtitle(from: String, to: String, start: Long, end: Long, now: Long, zone: TimeZone): String {
        val s = GlanceCardTimes.dayOffset(now, start, zone)
        val e = GlanceCardTimes.dayOffset(now, end, zone)
        return when {
            s == 0 && e == 0 -> "$from – $to"
            s == -1 -> host.string(R.string.glance_card_calendar_yesterday, from, to)
            e == 1 -> host.string(R.string.glance_card_calendar_tomorrow, from, to)
            s == 0 -> host.string(R.string.glance_card_calendar_date_for_end, from, date(end), to)
            e == 0 -> host.string(R.string.glance_card_calendar_date_for_start, date(start), from, to)
            else -> "$from – $to"
        }
    }

    private fun date(time: Long): String = DateFormat.getDateFormat(host.context).format(Date(time))

    private companion object {
        const val LEAD_MILLIS = 15 * GlanceCardTimes.MINUTE
        const val LEAD = "lead"
        const val DEBOUNCE_MILLIS = 250L
        const val TITLE_LENGTH = 30
    }
}
