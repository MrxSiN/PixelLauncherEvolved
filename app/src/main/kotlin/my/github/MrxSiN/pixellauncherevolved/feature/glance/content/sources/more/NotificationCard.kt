package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more

import android.app.Notification
import android.os.Handler
import android.os.HandlerThread
import android.service.notification.StatusBarNotification

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceItem
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOption
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource

/**
 * The newest notification from the apps picked, rewritten from Smartspacer's `NotificationTarget`:
 * its title and text, the app's small icon, a tap opening its content intent. Secret, ongoing,
 * group-summary and this launcher's own notifications are skipped, as Smartspacer does by default.
 *
 * Notifications come from the launcher's own `NotificationListener` ([NotificationFeed]), which
 * exists only while notification dots are on; with it unbound there is nothing to show. A callback
 * (main thread) wakes one background thread that filters `getActiveNotifications()` and keeps the
 * answer in a `@Volatile` field. Not cleared when dots go off until the next callback.
 */
class NotificationCard : GlanceSource("notifications", Kind.CARD, R.string.glance_notification_title, R.string.glance_notification_description) {

    override val options = listOf(
        GlanceOption(APPS, R.string.glance_notification_apps_title, GlanceOption.Type.APPS, R.string.glance_notification_apps_all),
        GlanceOption(
            TEXT, R.string.glance_option_message_title, GlanceOption.Type.CHOICE, R.string.glance_option_message_shown,
            listOf(GlanceOption.Choice(HIDDEN, R.string.glance_option_message_hidden)),
        ),
    )

    @Volatile private var item: GlanceItem? = null
    @Volatile private var apps: Set<String> = emptySet()
    @Volatile private var worker: Handler? = null
    private var shown: String? = null
    private var thread: HandlerThread? = null
    private val changed: () -> Unit = { schedule() }

    private val read = Runnable { recompute() }

    override fun start() {
        hideText = option(TEXT) == HIDDEN
        apps = NotificationPick.apps(option(APPS))
        val handlerThread = HandlerThread("ple-glance-notifications").apply { start() }
        thread = handlerThread
        worker = Handler(handlerThread.looper)
        NotificationFeed.register(changed)
        schedule()
    }

    override fun stop() {
        NotificationFeed.unregister(changed)
        worker = null
        thread?.quitSafely()
        thread = null
        item = null
        shown = null
    }

    /** Whether the card leaves the message text out, showing only who or what it is from. */
    @Volatile private var hideText = false

    override fun onOptionsChanged() {
        hideText = option(TEXT) == HIDDEN
        shown = null
        apps = NotificationPick.apps(option(APPS))
        schedule()
    }

    override fun content(): GlanceItem? = item

    private fun schedule() {
        val handler = worker ?: return
        handler.removeCallbacks(read)
        handler.post(read)
    }

    /** Worker thread. */
    private fun recompute() {
        val list = NotificationFeed.active()
        val own = host.context.packageName
        val at = if (list == null) -1 else NotificationPick.newest(
            list.size, apps, { list[it].packageName }, { list[it].postTime },
            { i ->
                val n = list[i].notification
                list[i].isOngoing || list[i].packageName == own || n == null ||
                    n.visibility == Notification.VISIBILITY_SECRET || n.flags and Notification.FLAG_GROUP_SUMMARY != 0
            },
        )
        val next = if (at < 0) null else build(list!![at])
        val key = if (next == null) null else "${list!![at].key}\u0000${next.title}\u0000${next.subtitle}"
        if (key == shown) return
        shown = key
        item = next
        host.changed()
    }

    private fun build(sbn: StatusBarNotification): GlanceItem? {
        val n = sbn.notification
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.takeIf { it.isNotEmpty() } ?: return null
        return GlanceItem(
            title = title,
            subtitle = if (hideText) null else extras.getCharSequence(Notification.EXTRA_TEXT)?.takeIf { it.isNotEmpty() },
            icon = n.smallIcon,
            tap = n.contentIntent,
        )
    }

    companion object {
        const val APPS = "apps"
        const val TEXT = "text"
        const val HIDDEN = "hidden"
    }
}
