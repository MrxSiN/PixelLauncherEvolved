package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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

    @Volatile
    var onWifi = false
        private set

    @Volatile
    var onMobile = false
        private set

    @Volatile
    var headphones = false
        private set

    private var used: Set<String> = emptySet()
    private var connectivity: ConnectivityManager? = null
    private var deviceAudio: AudioManager? = null

    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            setNetwork(
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            )
        }

        override fun onLost(network: Network) = setNetwork(wifi = false, mobile = false)
    }

    /** Runs on the main thread: the callback is registered with [main]. */
    private fun setNetwork(wifi: Boolean, mobile: Boolean) {
        if (wifi == onWifi && mobile == onMobile) return
        onWifi = wifi
        onMobile = mobile
        onChange()
    }

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = readHeadphones()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = readHeadphones()
    }

    private fun readHeadphones() {
        val now = deviceAudio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any { it.type in HEADPHONE_TYPES } == true
        if (now != headphones) {
            headphones = now
            onChange()
        }
    }
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

        val wantNetwork = GlanceWhen.WIFI in used || GlanceWhen.MOBILE in used
        if (wantNetwork && connectivity == null) {
            connectivity = context.getSystemService(ConnectivityManager::class.java)?.also { it.registerDefaultNetworkCallback(network, main) }
        } else if (!wantNetwork && connectivity != null) {
            runCatching { connectivity?.unregisterNetworkCallback(network) }
            connectivity = null
            onWifi = false
            onMobile = false
        }

        val wantHeadphones = GlanceWhen.HEADPHONES in used
        if (wantHeadphones && deviceAudio == null) {
            deviceAudio = context.getSystemService(AudioManager::class.java)?.also { it.registerAudioDeviceCallback(devices, main) }
            readHeadphones()
        } else if (!wantHeadphones && deviceAudio != null) {
            deviceAudio?.unregisterAudioDeviceCallback(devices)
            deviceAudio = null
            headphones = false
        }

        val wantClock = used.any { it !in GlanceWhen.stateValues }
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

    /**
     * The phone's state now, read afresh for the settings preview: conditions no source uses are not
     * watched, so their fields would read false. A few Binder calls on the main thread, only when the
     * person opens Try on the preview; never on a launcher path.
     */
    fun moment(): GlanceMoment {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivityManager?.let { manager -> manager.activeNetwork?.let { manager.getNetworkCapabilities(it) } }
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return GlanceMoment(
            minuteOfDay = minuteOfDay(),
            dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY,
            music = audioManager?.isMusicActive == true,
            headphones = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any { it.type in HEADPHONE_TYPES } == true,
            charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
            wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            mobile = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
        )
    }

    fun holds(value: String?): Boolean {
        // Most sources show always, and the rest mostly on a state: neither needs the clock, which
        // costs a Calendar (several objects) per source per At a Glance update.
        if (value == null || value in GlanceWhen.stateValues) {
            return GlanceWhen.holds(value, 0, musicPlaying, charging, onWifi, onMobile, headphones)
        }
        val now = Calendar.getInstance()
        return GlanceWhen.holds(
            value, now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE), musicPlaying, charging, onWifi, onMobile, headphones,
            now.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY,
        )
    }

    private fun schedule() {
        main.removeCallbacks(boundary)
        val minutes = GlanceWhen.minutesToNextBoundary(used, minuteOfDay()) ?: return
        val now = Calendar.getInstance()
        val intoMinute = now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND)
        main.postAtTime(boundary, SystemClock.uptimeMillis() + minutes * 60_000L - intoMinute)
    }

    private companion object {
        /** Output devices that count as headphones: wired, USB and Bluetooth listening devices. */
        val HEADPHONE_TYPES = intArrayOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }

    private fun minuteOfDay(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
}
