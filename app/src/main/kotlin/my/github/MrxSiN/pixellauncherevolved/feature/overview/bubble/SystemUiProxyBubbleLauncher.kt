package my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble

import android.content.Context
import android.content.Intent
import android.os.UserHandle

import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Reaches the shell through the launcher's own `SystemUiProxy`.
 *
 * This is the same binder call Android 17 uses for the "Bubble" item in the app
 * icon long-press menu, so bubbles created here are indistinguishable from the
 * platform ones.
 */
class SystemUiProxyBubbleLauncher(
    private val classLoader: ClassLoader,
    private val logger: Logger,
) : BubbleLauncher {

    private class Binding(
        val singleton: Field,
        val singletonGet: Method,
        val showAppBubble: Method,
        val entryPoint: Any,
    )

    private var resolved = false
    private var binding: Binding? = null

    override fun launch(context: Context, target: BubbleTarget): Boolean {
        val binding = binding() ?: return false

        return try {
            val proxy = binding.singletonGet.invoke(binding.singleton.get(null), context)
            if (proxy == null) {
                logger.info("SystemUiProxy unavailable; bubble request dropped")
                return false
            }

            // The trailing bubble bar location is optional; null lets the shell
            // place the bubble with its own default rules.
            binding.showAppBubble.invoke(proxy, target.intent, target.user, binding.entryPoint, null)
            true
        } catch (error: Throwable) {
            logger.warn("Unable to request bubble for $target", error)
            false
        }
    }

    @Synchronized
    private fun binding(): Binding? {
        if (resolved) return binding
        resolved = true

        binding = try {
            val proxyClass = Class.forName(PROXY_CLASS, false, classLoader)
            val singletonClass = Class.forName(SINGLETON_CLASS, false, classLoader)
            val entryPointClass = Class.forName(ENTRY_POINT_CLASS, false, classLoader)
            val locationClass = Class.forName(BUBBLE_BAR_LOCATION_CLASS, false, classLoader)

            Binding(
                singleton = proxyClass.getDeclaredField("INSTANCE").apply { isAccessible = true },
                singletonGet = singletonClass.getDeclaredMethod("get", Context::class.java),
                showAppBubble = proxyClass.getDeclaredMethod(
                    "showAppBubble",
                    Intent::class.java,
                    UserHandle::class.java,
                    entryPointClass,
                    locationClass,
                ),
                entryPoint = readEntryPoint(entryPointClass),
            ).also { logger.info("Bubble entry point resolved on $PROXY_CLASS") }
        } catch (error: Throwable) {
            logger.warn("Launcher does not expose a bubble entry point", error)
            null
        }

        return binding
    }

    private companion object {
        const val PROXY_CLASS = "com.android.quickstep.SystemUiProxy"
        const val SINGLETON_CLASS = "com.android.launcher3.util.DaggerSingletonObject"
        const val ENTRY_POINT_CLASS = "com.android.wm.shell.shared.bubbles.logging.EntryPoint"
        const val BUBBLE_BAR_LOCATION_CLASS = "com.android.wm.shell.shared.bubbles.BubbleBarLocation"

        /** Closest existing bucket: the bubble was started from a launcher surface. */
        const val ENTRY_POINT_NAME = "LAUNCHER_ICON_MENU"

        /** Falls back to the first constant when the expected name is gone. */
        fun readEntryPoint(entryPointClass: Class<*>): Any =
            runCatching {
                entryPointClass.getDeclaredField(ENTRY_POINT_NAME).apply { isAccessible = true }.get(null)!!
            }.getOrElse {
                entryPointClass.enumConstants?.firstOrNull() ?: throw it
            }
    }
}
