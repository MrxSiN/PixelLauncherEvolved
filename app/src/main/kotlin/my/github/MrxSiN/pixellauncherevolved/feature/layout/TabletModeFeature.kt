package my.github.MrxSiN.pixellauncherevolved.feature.layout

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/** Changes classification before the launcher derives its grid and taskbar dimensions. */
class TabletModeFeature : ToggleFeature(Settings.TABLET_MODE) {

    override val compatibility = CompatibilityFeature.FULL_TABLET_LAYOUT
    // Device profiles are cached. Both enabling and disabling require a process restart.
    override val isLive: Boolean = false

    override fun install(context: FeatureContext) {
        val owner = requireNotNull(context.findClass("com.android.launcher3.display.LauncherDisplayInfo"))
        val bounds = requireNotNull(context.findClass("com.android.launcher3.util.WindowBounds"))
        val method = owner.getDeclaredMethod("isLargeScreen", bounds)
        check(method.returnType == Boolean::class.javaPrimitiveType)
        context.xposed.hook(method).intercept { true }
        context.logger.info("Tablet mode: launcher large-screen classification enabled")
    }
}
