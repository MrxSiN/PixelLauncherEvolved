package my.github.MrxSiN.pixellauncherevolved.feature.icons

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Icon

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Tells the person, in a notification, that the home screen's icons are changing
 * and then that they have changed.
 *
 * Posted by the launcher, whose home screen it is, on a silent channel of its
 * own ("Icons pack") that a person can turn off like any other: the change is
 * made from Wallpaper & style, and the home screen is where it lands, so the
 * notice is what connects the two. "Changing" shows an indeterminate progress
 * bar; "changed" replaces it without alerting again and goes away on its own.
 */
internal class IconChangeNotice(
    private val context: Context,
    private val text: Resources?,
    private val logger: Logger,
) {

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val icon = context.resources.getIdentifier(SMALL_ICON, "drawable", context.packageName)

    /** The icons are being changed to [pack]'s, or to the system's when it is null. */
    fun changing(pack: String?) = post(
        title = string(R.string.feature_icon_notice_changing, "Changing icons pack"),
        body = pack ?: string(R.string.feature_icon_notice_system, "System icons"),
        ongoing = true,
    )

    /** The icons are now [pack]'s, or the system's when it is null. */
    fun changed(pack: String?) = post(
        title = string(R.string.feature_icon_notice_changed, "Icons pack changed"),
        body = if (pack == null) {
            string(R.string.feature_icon_notice_changed_system, "Your home screen uses system icons")
        } else {
            text?.getString(R.string.feature_icon_notice_changed_pack, pack) ?: "Your home screen uses $pack"
        },
        ongoing = false,
    )

    private fun post(title: String, body: String, ongoing: Boolean) {
        val notifications = manager ?: return
        runCatching {
            // Created, or renamed when the title changed: the platform keeps the person's own settings.
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL, string(R.string.feature_icon_pack_title, "Icons pack"), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
            val builder = Notification.Builder(context, CHANNEL)
                .setContentTitle(title)
                .setContentText(body)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setCategory(if (ongoing) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_STATUS)
                .setLocalOnly(true)
            if (icon != 0) builder.setSmallIcon(Icon.createWithResource(context, icon)) else builder.setSmallIcon(android.R.drawable.stat_notify_sync)
            // "Changing" also times out, so a launcher restarted mid-change never leaves it spinning.
            if (ongoing) builder.setProgress(0, 0, true).setTimeoutAfter(CHANGING_AT_MOST_MS) else builder.setTimeoutAfter(DISMISS_AFTER_MS)
            notifications.notify(ID, builder.build())
        }.onFailure { logger.warn("Icons: the icon pack notice could not be posted", it) }
    }

    private fun string(id: Int, fallback: String): String = text?.let { runCatching { it.getString(id) }.getOrNull() } ?: fallback

    private companion object {
        const val CHANNEL = "pixel_launcher_evolved_icon_pack"
        const val ID = 0x1c0ba
        const val SMALL_ICON = "ic_palette"
        const val DISMISS_AFTER_MS = 4_000L

        /** Past [IconChangeReveal]'s longest wait for a change to land. */
        const val CHANGING_AT_MOST_MS = 30_000L
    }
}
