package my.github.MrxSiN.pixellauncherevolved.feature.layout

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/**
 * Changes classification before the launcher derives its grid and taskbar dimensions.
 *
 * Everything but the choice of grid. The grid option is picked by
 * `DisplayOption.parseWeightedPredefinedDisplayOption`, which filters the
 * options by `LauncherDisplayInfo.getDeviceType()` — itself derived from
 * `isLargeScreen` — so a large screen picks `tablet_normal` (6x5,
 * `launcher_6_by_5.db`). The launcher cannot migrate a phone grid to it
 * ("Cannot migrate from source ... to destination"), keeps the phone's
 * `launcher_4_by_6.db`, and its loader then deletes every item in the rows the
 * 5-row grid lacks. During that one call the stock answer is given, so the
 * phone grid and its database are kept and nothing is out of bounds.
 */
class TabletModeFeature : ToggleFeature(Settings.TABLET_MODE) {

    override val compatibility = CompatibilityFeature.FULL_TABLET_LAYOUT

    /** Set on a thread while it picks a grid option, which gets the stock classification. */
    private val pickingGrid = ThreadLocal<Boolean>()

    override fun install(context: FeatureContext) {
        LiveLayout.install(context)
        val owner = requireNotNull(context.findClass("com.android.launcher3.display.LauncherDisplayInfo"))
        val bounds = requireNotNull(context.findClass("com.android.launcher3.util.WindowBounds"))
        val method = Reflect.declaredMethod(owner, "isLargeScreen", bounds)
        check(method.returnType == Boolean::class.javaPrimitiveType)
        context.xposed.hook(method).intercept { chain ->
            if (LiveLayout.tablet && pickingGrid.get() != true) true else chain.proceed()
        }

        val option = requireNotNull(context.findClass("com.android.launcher3.deviceprofile.parser.DisplayOption"))
        val pick = requireNotNull(Reflect.declared(option, "parseWeightedPredefinedDisplayOption") { true })
        context.xposed.hook(pick).intercept { chain ->
            if (pickingGrid.get() == true) return@intercept chain.proceed()
            pickingGrid.set(true)
            try {
                chain.proceed()
            } finally {
                pickingGrid.remove()
            }
        }
        context.logger.info("Tablet mode: launcher large-screen classification enabled")
    }
}
