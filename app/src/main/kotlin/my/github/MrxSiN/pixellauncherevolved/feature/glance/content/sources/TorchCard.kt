package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource

/**
 * "Torch on — Tap to turn off" while the torch is on. Rewritten from Smartspacer's
 * `FlashlightTarget` (without its low-light recommendation, which needs the light sensor).
 *
 * The torch state comes from `CameraManager.TorchCallback`; tapping turns the torch off through
 * `setTorchMode`, from a broadcast only this process can send itself.
 */
class TorchCard : GlanceSource("torch", Kind.CARD, R.string.glance_source_torch_title, R.string.glance_source_torch_description) {

    @Volatile private var camera: String? = null
    @Volatile private var on = false
    @Volatile private var manager: CameraManager? = null
    @Volatile private var tap: PendingIntent? = null

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            camera = cameraId
            if (on != enabled) {
                on = enabled
                host.changed()
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == camera && on) {
                on = false
                host.changed()
            }
        }
    }

    private val turnOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = camera ?: return
            runCatching { manager?.setTorchMode(id, false) }
                .onFailure { host.logger.warn("The torch could not be turned off", it) }
        }
    }

    override fun start() {
        val context = host.context
        manager = context.getSystemService(CameraManager::class.java)
        manager?.registerTorchCallback(callback, host.main)
        val action = context.packageName + ACTION_SUFFIX
        context.registerReceiver(turnOff, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        tap = PendingIntent.getBroadcast(
            context, 0, Intent(action).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun stop() {
        manager?.unregisterTorchCallback(callback)
        runCatching { host.context.unregisterReceiver(turnOff) }
        on = false
    }

    override fun content(): GlanceItem? {
        if (!on) return null
        return GlanceItem(
            title = host.string(R.string.glance_source_torch_on),
            subtitle = host.string(R.string.glance_source_torch_tap_off),
            icon = host.icon(R.drawable.ic_glance_torch),
            tap = tap,
            feature = "FEATURE_FLASHLIGHT",
        )
    }

    private companion object {
        const val ACTION_SUFFIX = ".PLE_GLANCE_TORCH_OFF"
    }
}
