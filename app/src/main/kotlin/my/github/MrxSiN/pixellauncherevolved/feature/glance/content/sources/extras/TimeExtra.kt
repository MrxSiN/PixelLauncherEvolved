package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.extras

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.text.format.DateFormat

import java.util.Date

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource

/**
 * The current time beside the date line. Rewritten from Smartspacer's `TimeComplication`; tapping
 * opens the clock's alarm list.
 *
 * Driven by the system's `ACTION_TIME_TICK` (once a minute, only delivered to a registered
 * receiver) while the source is started; no polling. The text is kept, so [content] only reads it.
 */
class TimeExtra : GlanceSource("time", Kind.EXTRA, R.string.glance_extra_time_title, R.string.glance_extra_time_description) {

    @Volatile private var text = ""

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun start() {
        val filter = IntentFilter(Intent.ACTION_TIME_TICK).apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        host.context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    override fun stop() {
        runCatching { host.context.unregisterReceiver(receiver) }
    }

    private fun refresh() {
        val shown = DateFormat.getTimeFormat(host.context).format(Date())
        if (shown == text) return
        text = shown
        host.changed()
    }

    override fun content(): GlanceItem? {
        val shown = text
        if (shown.isEmpty()) return null
        return GlanceItem(
            title = shown,
            icon = host.icon(R.drawable.ic_glance_extra_time),
            tapIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
