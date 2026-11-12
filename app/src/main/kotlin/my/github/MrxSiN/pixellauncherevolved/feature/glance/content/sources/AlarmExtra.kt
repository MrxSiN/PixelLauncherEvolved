package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.text.format.DateFormat

import java.util.Date

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource

/**
 * The next alarm's time beside the date, when it rings within 12 hours. Rewritten from Smartspacer's
 * `AlarmComplication`; tapping opens the alarm the way its clock app asks to.
 *
 * Changes arrive by `ACTION_NEXT_ALARM_CLOCK_CHANGED` and time or zone changes. One callback is
 * scheduled for the moment the alarm comes within 12 hours, and one for when it rings; there is no
 * polling.
 */
class AlarmExtra : GlanceSource("alarm", Kind.EXTRA, R.string.glance_source_alarm_title, R.string.glance_source_alarm_description) {

    @Volatile private var manager: AlarmManager? = null

    private val changed = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reschedule()
    }

    private val recheck = Runnable { reschedule() }

    override fun start() {
        manager = host.context.getSystemService(AlarmManager::class.java)
        val filter = IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED).apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        host.context.registerReceiver(changed, filter, Context.RECEIVER_NOT_EXPORTED)
        reschedule()
    }

    override fun stop() {
        host.main.removeCallbacks(recheck)
        runCatching { host.context.unregisterReceiver(changed) }
    }

    /** Schedules the next moment this could change, and says it may have changed now. */
    private fun reschedule() {
        host.main.removeCallbacks(recheck)
        val next = manager?.nextAlarmClock
        if (next != null) {
            val now = System.currentTimeMillis()
            val shows = next.triggerTime - WINDOW_MILLIS
            val at = if (now < shows) shows else next.triggerTime
            if (at > now) host.main.postAtTime(recheck, SystemClock.uptimeMillis() + (at - now))
        }
        host.changed()
    }

    override fun content(): GlanceItem? {
        val next = manager?.nextAlarmClock ?: return null
        val now = System.currentTimeMillis()
        if (next.showIntent == null || kotlin.math.abs(next.triggerTime - now) > WINDOW_MILLIS) return null
        return GlanceItem(
            title = DateFormat.getTimeFormat(host.context).format(Date(next.triggerTime)),
            icon = host.icon(R.drawable.ic_glance_alarm),
            tap = next.showIntent,
            feature = "FEATURE_UPCOMING_ALARM",
        )
    }

    private companion object {
        const val WINDOW_MILLIS = 12L * 60 * 60 * 1000
    }
}
