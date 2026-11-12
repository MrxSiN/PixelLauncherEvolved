package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.BatteryManager
import android.os.Handler
import android.os.SystemClock

import java.util.Calendar

/**
 * The state "When to show" reads ([GlanceWhen]), watched only for the conditions in use.
 *
 * Music playing comes from `AudioManager.AudioPlaybackCallback`, charging from the sticky battery
 * broadcast, and a period of the day from one callback scheduled at its next start or end. Nothing
 * polls. [update] is called on the main thread whenever the stored conditions change; [onChange]
 * runs on the main thread when a condition in use may have flipped.
 */
internal class GlanceConditions(
    private val context: Context,
    private val main: Handler,
    private val onChange: () -> Unit,
) {
    @Volatile
    var musicPlaying = false
        private set

    @Volatile
    var charging = false
        private set

    private var used: Set<String> = emptySet()
    private var audio: AudioManager? = null
    private var watchingBattery = false

    private val playback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val now = configs.any { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }
            if (now != musicPlaying) {
                musicPlaying = now
                onChange()
            }
        }
    }

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (now != charging) {
                charging = now
                onChange()
            }
        }
    }

    private val boundary = Runnable {
        schedule()
        onChange()
    }

    private val clock = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            schedule()
            onChange()
        }
    }
    private var watchingClock = false

    /** Watches what [values] need and stops watching the rest. */
    fun update(values: Collection<String>) {
        used = values.toSet()

        val wantMusic = GlanceWhen.MUSIC in used
        if (wantMusic && audio == null) {
            audio = context.getSystemService(AudioManager::class.java)?.also {
                it.registerAudioPlaybackCallback(playback, main)
                musicPlaying = it.isMusicActive
            }
        } else if (!wantMusic && audio != null) {
            audio?.unregisterAudioPlaybackCallback(playback)
            audio = null
            musicPlaying = false
        }

        val wantBattery = GlanceWhen.CHARGING in used
        if (wantBattery && !watchingBattery) {
            watchingBattery = true
            context.registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
                ?.let { charging = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 }
        } else if (!wantBattery && watchingBattery) {
            watchingBattery = false
            runCatching { context.unregisterReceiver(battery) }
            charging = false
        }

        val wantClock = used.any { it != GlanceWhen.MUSIC && it != GlanceWhen.CHARGING }
        if (wantClock && !watchingClock) {
            watchingClock = true
            // Screen on as well: the scheduled callback runs on uptime, which stops while asleep.
            val filter = IntentFilter(Intent.ACTION_TIME_CHANGED).apply {
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            context.registerReceiver(clock, filter, Context.RECEIVER_NOT_EXPORTED)
        } else if (!wantClock && watchingClock) {
            watchingClock = false
            runCatching { context.unregisterReceiver(clock) }
        }
        schedule()
    }

    fun holds(value: String?): Boolean = GlanceWhen.holds(value, minuteOfDay(), musicPlaying, charging)

    private fun schedule() {
        main.removeCallbacks(boundary)
        val minutes = GlanceWhen.minutesToNextBoundary(used, minuteOfDay()) ?: return
        val now = Calendar.getInstance()
        val intoMinute = now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND)
        main.postAtTime(boundary, SystemClock.uptimeMillis() + minutes * 60_000L - intoMinute)
    }

    private fun minuteOfDay(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
}
