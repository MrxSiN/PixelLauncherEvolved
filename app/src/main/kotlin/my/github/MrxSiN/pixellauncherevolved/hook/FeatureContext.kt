package my.github.MrxSiN.pixellauncherevolved.hook

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsSource

/**
 * Everything a feature needs to install itself into the launcher process.
 *
 * Features receive this rather than the module, so nothing below this package
 * depends on the Xposed entry class.
 */
class FeatureContext(
    val xposed: XposedInterface,
    val classLoader: ClassLoader,
    val settings: SettingsSource,
    val logger: Logger,
) {

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
