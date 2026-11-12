package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * The launcher's own `NotificationListener`, which the system binds only while notification dots are
 * on (the person allowed Pixel Launcher notification access).
 *
 * ```
 * com.android.launcher3.notification.NotificationListener extends NotificationListenerService
 *   onListenerConnected() / onListenerDisconnected()
 *   onNotificationPosted(StatusBarNotification) / onNotificationRemoved(StatusBarNotification)
 * ```
 * `CP4A.261105.040.A1` keeps no static instance and no listener list (`getInstanceIfConnected` and
 * `addNotificationsChangeListener` are gone), so the content feature hooks these four callbacks once
 * ([install]); each only remembers the running service and tells the registered sources. While no
 * source is registered a hook does one empty-list check.
 */
internal object NotificationFeed {

    @Volatile
    private var service: WeakReference<NotificationListenerService>? = null

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** Hooks the launcher's listener; missing members leave the feed empty, never broken. */
    fun install(context: FeatureContext) {
        val type = context.findClass(LISTENER) ?: return context.logger.warn("The launcher's notification listener is unavailable; the notification card shows nothing")
        val connected = Reflect.declared(type, CONNECTED) { it.parameterTypes.isEmpty() }
        val disconnected = Reflect.declared(type, DISCONNECTED) { it.parameterTypes.isEmpty() }
        val posted = Reflect.declared(type, POSTED) { it.parameterTypes.size == 1 }
        val removed = Reflect.declared(type, REMOVED) { it.parameterTypes.size == 1 }
        if (connected == null || disconnected == null || posted == null || removed == null) {
            context.logger.warn("The launcher's notification callbacks are unavailable; the notification card shows nothing")
            return
        }
        context.xposed.hook(connected).intercept { chain ->
            val result = chain.proceed()
            service = (chain.thisObject as? NotificationListenerService)?.let(::WeakReference)
            tell()
            result
        }
        context.xposed.hook(disconnected).intercept { chain ->
            val result = chain.proceed()
            service = null
            tell()
            result
        }
        for (method in arrayOf(posted, removed)) {
            context.xposed.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (service == null) service = (chain.thisObject as? NotificationListenerService)?.let(::WeakReference)
                tell()
                result
            }
        }
    }

    fun register(listener: () -> Unit) {
        listeners += listener
    }

    fun unregister(listener: () -> Unit) {
        listeners -= listener
    }

    /** The active notifications while the launcher's listener is bound; null otherwise. Binder: not for the main thread. */
    fun active(): Array<StatusBarNotification>? {
        val running = service?.get() ?: return null
        return runCatching { running.activeNotifications }.getOrNull()
    }

    private fun tell() {
        if (listeners.isEmpty()) return
        for (listener in listeners) runCatching { listener() }
    }

    private const val LISTENER = "com.android.launcher3.notification.NotificationListener"
    private const val CONNECTED = "onListenerConnected"
    private const val DISCONNECTED = "onListenerDisconnected"
    private const val POSTED = "onNotificationPosted"
    private const val REMOVED = "onNotificationRemoved"
}
