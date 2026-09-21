package my.github.MrxSiN.pixellauncherevolved.hook

import android.app.Application

import java.util.concurrent.atomic.AtomicBoolean

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Installs against a scoped app's [Application] before its startup code runs.
 *
 * The module is loaded as soon as the app's class loader exists, which is
 * before the process has a [android.content.Context] of any kind. The
 * launcher's settings live in a file in its own data directory, and opening
 * that file needs a context, so installation waits until the application has a
 * base context. Hooks are installed before `onCreate` can initialize device
 * profiles.
 */
class ApplicationStartup(
    private val xposed: XposedInterface,
    private val classLoader: ClassLoader,
    private val logger: Logger,
    private val applicationClass: String,
) {

    private val started = AtomicBoolean(false)

    /**
     * Calls [onCreated] once, on the scoped app's application.
     *
     * The app's own application class is preferred because hooking a method
     * that is actually declared cannot be missed. `Application.onCreate` is the
     * fallback, and reaches the same moment through the `super` call every
     * application subclass makes.
     */
    fun onApplicationCreated(onCreated: (Application) -> Unit) {
        val target = declaredCreate() ?: applicationCreate()

        if (target == null) {
            logger.warn("No application entry point to hook; no tweak can be installed")
            return
        }

        xposed.hook(target).intercept { chain ->
            val application = chain.thisObject as? Application

            if (application != null && started.compareAndSet(false, true)) {
                runCatching { onCreated(application) }
                    .onFailure { logger.warn("Installation failed", it) }
            }

            chain.proceed()
        }
    }

    private fun declaredCreate() = runCatching {
        Class.forName(applicationClass, false, classLoader).getDeclaredMethod("onCreate")
    }.getOrNull()

    private fun applicationCreate() = runCatching {
        Application::class.java.getDeclaredMethod("onCreate")
    }.getOrNull()
}
