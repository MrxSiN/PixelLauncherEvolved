package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock

import java.util.Calendar
import java.util.TimeZone

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOption
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOptions
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource

/**
 * "Good morning", "Good afternoon", "Good evening" or "Good night". Rewritten from Smartspacer's
 * `GreetingTarget` (with its name option, "Good morning, Name"): morning from 03:00, afternoon from 12:00, evening from
 * 18:00, night from 22:00.
 *
 * One callback is scheduled for the next boundary, and again after a time or zone change; there is no
 * polling. Like Smartspacer's, the card has no icon and nothing to tap.
 */
class GreetingCard : GlanceSource("greeting", Kind.CARD, R.string.glance_card_greeting_title, R.string.glance_card_greeting_description) {

    override val options = listOf(GlanceOption(GlanceOptions.NAME, R.string.glance_option_greeting_name, GlanceOption.Type.TEXT))

    @Volatile private var name = ""

    private val changed = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reschedule()
    }

    private val recheck = Runnable { reschedule() }

    override fun onOptionsChanged() {
        name = option(GlanceOptions.NAME).orEmpty().trim()
        host.changed()
    }

    override fun start() {
        name = option(GlanceOptions.NAME).orEmpty().trim()
        val filter = IntentFilter(Intent.ACTION_TIME_CHANGED).apply { addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        host.context.registerReceiver(changed, filter, Context.RECEIVER_NOT_EXPORTED)
        reschedule()
    }

    override fun stop() {
        host.main.removeCallbacks(recheck)
        runCatching { host.context.unregisterReceiver(changed) }
    }

    private fun reschedule() {
        host.main.removeCallbacks(recheck)
        val now = System.currentTimeMillis()
        val at = GlanceCardTimes.nextGreetingChange(now, TimeZone.getDefault())
        host.main.postAtTime(recheck, SystemClock.uptimeMillis() + (at - now))
        host.changed()
    }

    override fun content(): GlanceItem {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val text = when (GlanceCardTimes.greetingPeriod(hour)) {
            0 -> R.string.glance_card_greeting_morning
            1 -> R.string.glance_card_greeting_afternoon
            2 -> R.string.glance_card_greeting_evening
            else -> R.string.glance_card_greeting_night
        }
        val greeting = host.string(text)
        val who = name
        return GlanceItem(title = if (who.isEmpty()) greeting else host.string(R.string.glance_option_greeting_named, greeting, who))
    }
}
