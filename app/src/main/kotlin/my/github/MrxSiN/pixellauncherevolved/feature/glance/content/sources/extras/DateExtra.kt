package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.extras

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.provider.CalendarContract
import android.text.format.DateFormat

import java.time.ZoneId
import java.util.Date
import java.util.Locale

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource

/**
 * Today's short date beside the date line. Rewritten from Smartspacer's `DateComplication`; tapping
 * opens the calendar on today.
 *
 * The text is formatted once per change and kept; [content] only reads it. It is refreshed at
 * midnight (one scheduled callback) and on date, time, zone and locale change broadcasts.
 */
class DateExtra : GlanceSource("date_short", Kind.EXTRA, R.string.glance_extra_date_title, R.string.glance_extra_date_description) {

    @Volatile private var text = ""

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    private val midnight = Runnable { refresh() }

    override fun start() {
        val filter = IntentFilter(Intent.ACTION_DATE_CHANGED).apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        host.context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    override fun stop() {
        host.main.removeCallbacks(midnight)
        runCatching { host.context.unregisterReceiver(receiver) }
    }

    private fun refresh() {
        host.main.removeCallbacks(midnight)
        val now = System.currentTimeMillis()
        val shown = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"), Date(now)).toString()
        host.main.postAtTime(midnight, SystemClock.uptimeMillis() + (GlanceExtraLogic.nextMidnight(now, ZoneId.systemDefault()) - now))
        if (shown == text) return
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
                .setData(ContentUris.appendId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time"), System.currentTimeMillis()).build())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
