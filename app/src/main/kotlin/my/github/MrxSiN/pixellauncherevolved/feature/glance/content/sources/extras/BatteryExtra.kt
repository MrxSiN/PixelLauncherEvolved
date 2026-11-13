package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.extras

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOption
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOptions
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource

/**
 * The battery percentage beside the date, with a bolt icon while charging. Rewritten from the
 * behaviour of Smartspacer's battery complication.
 *
 * One sticky `ACTION_BATTERY_CHANGED` receiver; At a Glance is only told when the shown percent or
 * the charging state changes, not on every voltage or temperature tick.
 */
class BatteryExtra : GlanceSource("battery", Kind.EXTRA, R.string.glance_extra_battery_title, R.string.glance_extra_battery_description) {

    @Volatile private var percent = -1
    @Volatile private var charging = false

    override val options = listOf(
        GlanceOption(
            BELOW, R.string.glance_option_battery_title, GlanceOption.Type.CHOICE, R.string.glance_option_battery_always,
            listOf(
                GlanceOption.Choice("50", R.string.glance_option_battery_50),
                GlanceOption.Choice("30", R.string.glance_option_battery_30),
                GlanceOption.Choice("20", R.string.glance_option_battery_20),
            ),
            R.plurals.glance_unit_percent_below,
        ),
    )

    /** Shows only at or below this percent ([BELOW]); 100 is always. */
    @Volatile private var below = 100

    override fun onOptionsChanged() {
        below = GlanceOptions.number(option(BELOW), 100)
        host.changed()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = read(intent)
    }

    override fun start() {
        below = GlanceOptions.number(option(BELOW), 100)
        val sticky = host.context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        if (sticky != null) read(sticky)
    }

    override fun stop() {
        runCatching { host.context.unregisterReceiver(receiver) }
    }

    private fun read(intent: Intent) {
        val now = GlanceExtraLogic.percent(
            intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
        )
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (now == percent && plugged == charging) return
        percent = now
        charging = plugged
        host.changed()
    }

    override fun content(): GlanceItem? {
        val level = percent
        if (level < 0 || level > below) return null
        return GlanceItem(
            title = "$level%",
            icon = host.icon(if (charging) R.drawable.ic_glance_extra_battery_charging else R.drawable.ic_glance_extra_battery),
            tapIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        const val BELOW = "below"
    }
}
