package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.os.SystemClock
import android.provider.CalendarContract

import java.util.Date
import java.util.TimeZone

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource

/**
 * Today's date, such as "Fri, Nov 13", in the user's language; tapping opens the calendar at today.
 * Rewritten from Smartspacer's `DateTarget` (without its custom pattern option): the same ICU
 * skeleton `EEEMMMd`, capitalised for the start of a sentence.
 *
 * One callback is scheduled for the next midnight; time, zone and language changes re-read the
 * format. There is no polling.
 */
class DateCard : GlanceSource("date", Kind.CARD, R.string.glance_card_date_title, R.string.glance_card_date_description) {

    @Volatile private var format: DateFormat? = null

    private val changed = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            format = null
            reschedule()
        }
    }

    private val recheck = Runnable { reschedule() }

    override fun start() {
        val filter = IntentFilter(Intent.ACTION_TIME_CHANGED).apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        host.context.registerReceiver(changed, filter, Context.RECEIVER_NOT_EXPORTED)
        reschedule()
    }

    override fun stop() {
        host.main.removeCallbacks(recheck)
        runCatching { host.context.unregisterReceiver(changed) }
        format = null
    }

    private fun reschedule() {
        host.main.removeCallbacks(recheck)
        val now = System.currentTimeMillis()
        host.main.postAtTime(recheck, SystemClock.uptimeMillis() + (GlanceCardTimes.nextMidnight(now, TimeZone.getDefault()) - now))
        host.changed()
    }

    override fun content(): GlanceItem {
        val now = System.currentTimeMillis()
        val format = format ?: DateFormat.getInstanceForSkeleton(SKELETON).apply {
            setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
        }.also { format = it }
        val today = ContentUris.appendId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time"), now).build()
        return GlanceItem(
            title = synchronized(format) { format.format(Date(now)) },
            icon = host.icon(R.drawable.ic_glance_card_date),
            tapIntent = Intent(Intent.ACTION_VIEW, today).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            ),
        )
    }

    private companion object {
        const val SKELETON = "EEEMMMd"
    }
}
