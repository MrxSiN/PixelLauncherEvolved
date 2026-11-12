package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards

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
 * The current time as a card; tapping shows the clock app's alarms. Rewritten from Smartspacer's
 * `TimeTarget`, which asks its broadcast provider for the same minute tick.
 *
 * `ACTION_TIME_TICK` is a system broadcast that arrives once a minute; the receiver is registered
 * only while the card is switched on, and each tick just says the content changed. The time is
 * formatted when At a Glance asks, in the 12 or 24 hour style the user chose.
 */
class ClockCard : GlanceSource("clock", Kind.CARD, R.string.glance_card_clock_title, R.string.glance_card_clock_description) {

    private val ticked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = host.changed()
    }

    override fun start() {
        val filter = IntentFilter(Intent.ACTION_TIME_TICK).apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        host.context.registerReceiver(ticked, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun stop() {
        runCatching { host.context.unregisterReceiver(ticked) }
    }

    override fun content(): GlanceItem = GlanceItem(
        title = DateFormat.getTimeFormat(host.context).format(Date()),
        icon = host.icon(R.drawable.ic_glance_card_clock),
        tapIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
