package my.github.MrxSiN.pixellauncherevolved.hook

import android.content.Context
import android.content.res.Resources

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.diagnostics.LauncherAnalysis
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsStore

/**
 * Everything a feature needs to install itself into the launcher process.
 *
 * Features receive this rather than the module, so nothing below this package
 * depends on the Xposed entry class.
 *
 * [settings] is writable because one feature draws the settings section inside
 * the launcher's own Home settings. Every other feature only reads it.
 */
class FeatureContext(
    val xposed: XposedInterface,
    val classLoader: ClassLoader,
    val appContext: Context,
    val settings: SettingsStore,
    val logger: Logger,
) {

    /**
     * This module's own resources, read from inside the launcher.
     *
     * Titles and summaries are described once, in this module's `strings.xml`,
     * and drawn in another app's process. `getResourcesForApplication` opens
     * them from the module's own APK, so no text has to be duplicated as a
     * constant. Null when that APK cannot be opened.
     */
    val moduleResources: Resources? by lazy {
        runCatching { appContext.packageManager.getResourcesForApplication(xposed.moduleApplicationInfo) }
            .onFailure { logger.warn("Module resources are unreachable", it) }
            .getOrNull()
    }

    /** What this launcher build offers each tweak, checked once for the life of the process. */
    val analysis: LauncherAnalysis by lazy { LauncherAnalysis.run(appContext, classLoader, logger) }

    fun findClass(name: String): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    /**
     * Hooks one method, running [after] once the original has returned.
     *
     * A missing method is reported and skipped: a launcher update that renames
     * one member must not take the rest of the module down with it.
     */
    fun hookAfter(
        owner: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
        after: (thisObject: Any?, args: List<Any?>) -> Unit,
    ) {
        try {
            val method = owner.getDeclaredMethod(methodName, *parameterTypes)
            xposed.hook(method).intercept { chain ->
                val result = chain.proceed()
                runCatching { after(chain.thisObject, chain.args) }
                    .onFailure { logger.warn("Hook body failed for ${owner.simpleName}.$methodName", it) }
                result
            }
            logger.info("Hooked ${owner.simpleName}.$methodName")
        } catch (error: Throwable) {
            logger.warn("Unable to hook ${owner.name}.$methodName", error)
        }
    }
}
