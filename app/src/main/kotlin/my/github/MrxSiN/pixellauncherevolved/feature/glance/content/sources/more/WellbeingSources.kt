package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more

import android.Manifest
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock

import java.util.TimeZone

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.bridge.Bridge
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.cards.GlanceCardTimes

/**
 * Today's screen time, rewritten from Smartspacer's Digital Wellbeing target and complication.
 * Smartspacer copies the numbers out of Digital Wellbeing's own widget; the launcher's process
 * holds the usage-access permission, so this adds up `UsageStatsManager` events itself, from local
 * midnight, over every app but this launcher and System UI.
 *
 * One background thread per source reads the events: on screen on, on unlock, at midnight, after a
 * time or zone change, and every 15 minutes only while the screen is on. [content] reads one
 * `@Volatile` item; [GlanceHost.changed] fires only when its text changed.
 */
abstract class WellbeingSource(id: String, kind: Kind, title: Int, description: Int, private val withApps: Boolean) :
    GlanceSource(id, kind, title, description) {

    override val permission: String = Manifest.permission.PACKAGE_USAGE_STATS

    @Volatile private var item: GlanceItem? = null
    private var shown: String? = null
    private var thread: HandlerThread? = null
    @Volatile private var worker: Handler? = null
    private var screenOn = false
    private var skipped: Array<String> = emptyArray()
    private var tapIntent: Intent? = null

    private val midnight = Runnable {
        read()
        scheduleMidnight()
    }
    private val tick = Runnable {
        if (screenOn) {
            read()
            scheduleTick()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val handler = worker ?: return
            val action = intent.action
            handler.post {
                when (action) {
                    Intent.ACTION_SCREEN_ON -> {
                        screenOn = true
                        read()
                        scheduleTick()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        worker?.removeCallbacks(tick)
                    }
                    Intent.ACTION_USER_PRESENT -> read()
                    else -> {
                        read()
                        scheduleMidnight()
                    }
                }
            }
        }
    }

    /** What the item shows, from today's total and the top apps' labels; null for nothing. */
    protected abstract fun compose(total: Long, apps: String): GlanceItem?

    override fun start() {
        val context = host.context
        val looper = HandlerThread("ple-glance-wellbeing-$id").also { it.start() }
        thread = looper
        val handler = Handler(looper.looper)
        worker = handler
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        handler.post {
            screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive == true
            skipped = arrayOf(context.packageName, Bridge.SYSTEM_UI_PACKAGE)
            tapIntent = context.packageManager.getLaunchIntentForPackage(DASHBOARD_PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            read()
            scheduleMidnight()
            if (screenOn) scheduleTick()
        }
    }

    override fun stop() {
        val looper = thread ?: return
        runCatching { host.context.unregisterReceiver(receiver) }
        worker?.removeCallbacksAndMessages(null)
        looper.quitSafely()
        thread = null
        worker = null
        item = null
        shown = null
    }

    override fun content(): GlanceItem? = item

    private fun scheduleTick() {
        val handler = worker ?: return
        handler.removeCallbacks(tick)
        handler.postAtTime(tick, SystemClock.uptimeMillis() + REFRESH_MILLIS)
    }

    private fun scheduleMidnight() {
        val handler = worker ?: return
        handler.removeCallbacks(midnight)
        val now = System.currentTimeMillis()
        handler.postAtTime(midnight, SystemClock.uptimeMillis() + GlanceCardTimes.nextMidnight(now, TimeZone.getDefault()) - now)
    }

    /** On the worker thread. */
    private fun read() {
        val context = host.context
        val now = System.currentTimeMillis()
        val tally = WellbeingTally(UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.ACTIVITY_PAUSED) { it in skipped }
        runCatching {
            val events = context.getSystemService(UsageStatsManager::class.java)
                .queryEvents(GlanceCardTimes.startOfDay(now, TimeZone.getDefault()), now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                tally.add(event.eventType, event.packageName, event.timeStamp)
            }
        }.onFailure { host.logger.warn("Screen time could not be read", it) }
        tally.finish(now)
        val apps = if (withApps) {
            val pm = context.packageManager
            tally.top(TOP_APPS).joinToString(", ") { pkg ->
                runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
            }
        } else {
            ""
        }
        val next = compose(tally.totalMillis(), apps)?.let { if (tapIntent != null) it.copy(tapIntent = tapIntent) else it }
        item = next
        val key = "${next?.title}|${next?.subtitle}"
        if (key != shown) {
            shown = key
            host.changed()
        }
    }

    /** "2h 14m" or "14m". */
    protected fun duration(total: Long): String {
        val h = WellbeingTally.hours(total)
        val m = WellbeingTally.minutes(total)
        return if (h > 0) host.string(R.string.glance_more_wellbeing_hm, h, m) else host.string(R.string.glance_more_wellbeing_m, m)
    }

    private companion object {
        const val REFRESH_MILLIS = 15 * GlanceCardTimes.MINUTE
        const val TOP_APPS = 3

        // The Digital Wellbeing app, whose launcher activity is its dashboard.
        const val DASHBOARD_PACKAGE = "com.google.android.apps.wellbeing"
    }
}

/** Total screen time as the title, the three most used apps as the subtitle. */
class WellbeingCard : WellbeingSource(
    "wellbeing", Kind.CARD, R.string.glance_more_wellbeing_title, R.string.glance_more_wellbeing_description, withApps = true,
) {
    override fun compose(total: Long, apps: String) = GlanceItem(
        title = host.string(R.string.glance_more_wellbeing_screen_time, duration(total)),
        subtitle = apps.ifEmpty { null },
        icon = host.icon(R.drawable.ic_glance_more_wellbeing),
    )
}

/** Total screen time beside the date line. */
class WellbeingTimeExtra : WellbeingSource(
    "wellbeing_time", Kind.EXTRA, R.string.glance_more_wellbeing_time_title, R.string.glance_more_wellbeing_time_description, withApps = false,
) {
    override fun compose(total: Long, apps: String) = GlanceItem(
        title = duration(total),
        icon = host.icon(R.drawable.ic_glance_more_wellbeing),
    )
}
