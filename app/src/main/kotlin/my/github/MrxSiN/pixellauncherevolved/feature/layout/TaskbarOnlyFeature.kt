package my.github.MrxSiN.pixellauncherevolved.feature.layout

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.ToggleFeature

/**
 * Gives the launcher a taskbar without giving it the tablet grid.
 *
 * Four things have to hold for that to look right, and each is one class below:
 * the launcher has to believe a taskbar exists, the taskbar has to align onto a
 * hotseat measured by a different profile than its own, it has to carry enough
 * icons for every hotseat slot to have something to morph out of, and those
 * icons have to leave the stashed pill whole.
 */
class TaskbarOnlyFeature : ToggleFeature(Settings.TASKBAR_ONLY) {

    override val compatibility = CompatibilityFeature.TASKBAR_ONLY
    // Device profiles are built once at startup. Both transitions need a restart.
    override val isLive: Boolean = false

    override fun install(context: FeatureContext) {
        TaskbarPresence(context).install()
        HotseatHandoff(context).install()
        TaskbarIconCount(context).install()
        TaskbarRevealShape(context).install()
    }
}

/**
 * Reports a taskbar on every device profile the process builds.
 *
 * The launcher decides the taskbar and the tablet grid from one call:
 *
 * ```
 * isTaskbarPresent = isLargeScreen(windowBounds) && isTaskbarDrawnInProcess()
 * ```
 *
 * so the two cannot be told apart at `isLargeScreen`, which is what tablet mode
 * changes. They separate one step later, where the answer is recorded as the
 * single boolean of `TaskbarConfiguration`; rewriting that leaves the grid, the
 * app drawer and Recents on their phone measurements while every taskbar
 * dimension is derived from it as the launcher intends.
 *
 * ```
 * com.android.launcher3.deviceprofile.DeviceProperties
 *   public TaskbarConfiguration taskbarConfiguration
 * com.android.launcher3.deviceprofile.TaskbarConfiguration
 *   public boolean isTaskbarPresent
 * ```
 *
 * The boolean is rewritten on the properties the factory has just answered.
 * `DeviceProfileBuilder.build()` asks for those properties and only then reads
 * `taskbarConfiguration` back out of them to derive the taskbar profile, so the
 * rewrite lands between the two and every taskbar dimension is derived from it.
 *
 * On `CP2A.260805.005` this hooked `TaskbarConfiguration`'s own one-argument
 * constructor instead. `CP3A.260905.009` has no constructor to hook: the
 * shrinker inlined it into the factory, which now writes the field directly.
 *
 * The rewrite applies to every profile. A taskbar the launcher itself does not
 * know about is worse than no taskbar: hotseat alignment reads the taskbar's
 * height, icon size and bottom margin from the launcher's own profile, so a
 * profile that reports none animates the icons towards zero, and
 * `TaskbarActivityContext.isDeviceProfileForPhoneMode` — `isPhone &&
 * !isTaskbarPresent` — throws the alignment controller away entirely.
 */
private class TaskbarPresence(private val context: FeatureContext) {

    fun install() {
        val factory = requireNotNull(DeviceProfiles.propertiesFactory(context))
        val properties = requireNotNull(
            context.findClass("com.android.launcher3.deviceprofile.DeviceProperties"),
        )
        val configurationOf = requireNotNull(Reflect.field(properties, "taskbarConfiguration"))
        val presentOf = requireNotNull(
            Reflect.field(configurationOf.type, "isTaskbarPresent"),
        )

        val create = requireNotNull(
            factory.declaredMethods.firstOrNull { it.name == DeviceProfiles.CREATE_PROPERTIES }
                ?.apply { isAccessible = true },
        )

        context.xposed.hook(create).intercept { chain ->
            chain.proceed()?.also { built ->
                runCatching { presentOf.setBoolean(configurationOf.get(built), true) }
                    .onFailure { context.logger.warn("Unable to report a taskbar on this profile", it) }
            }
        }

        context.logger.info("Taskbar only: taskbar reported present on every device profile")
    }
}

/**
 * Aligns the taskbar onto the hotseat using the profile that lays the hotseat out.
 *
 * `TaskbarLauncherStateController.onIconAlignmentRatioChanged` translates the
 * taskbar icons by `-taskbarDp.getTaskbarOffsetY()`, where `taskbarDp` is
 * `TaskbarActivityContext.mDeviceProfile` — the profile built for the taskbar's
 * own window. The hotseat those icons are aiming at belongs to the launcher's
 * profile, and the two are not the same object.
 *
 * They agree on a tablet. They do not agree here, because the taskbar renders
 * its icons at taskbar size and its profile carries that size as the workspace
 * icon size:
 *
 * ```
 * taskbar window profile   iconSizePx=171   getTaskbarOffsetY()=107
 * launcher profile         iconSizePx=198   getTaskbarOffsetY()=129
 * ```
 *
 * So the icons settle 22px below the hotseat row and step onto it when the
 * morph hands over. Measured across a 20x slowed transition, the bottom edge of
 * the icon row held at 2860px and then jumped to 2837px in one frame.
 *
 * Answering with the launcher's profile removes the step. The launcher's own
 * arithmetic is untouched; only the profile it is asked is corrected.
 */
private class HotseatHandoff(private val context: FeatureContext) {

    /**
     * The profile the launcher lays its hotseat out with.
     *
     * Held weakly: a configuration change replaces it, and nothing here should
     * be the reason the old one stays alive.
     */
    @Volatile
    private var launcherProfile: java.lang.ref.WeakReference<Any>? = null

    fun install() {
        val profile = requireNotNull(DeviceProfiles.profile(context))
        val stateController = requireNotNull(
            context.findClass("com.android.launcher3.taskbar.TaskbarLauncherStateController"),
        )
        val offsetY = profile.getDeclaredMethod("getTaskbarOffsetY")
        val launcherProfileOf = requireNotNull(Reflect.method(stateController, "getDeviceProfile"))

        context.xposed.hook(launcherProfileOf).intercept { chain ->
            chain.proceed().also { if (it != null) launcherProfile = java.lang.ref.WeakReference(it) }
        }

        context.xposed.hook(offsetY).intercept { chain ->
            val known = launcherProfile?.get()
            // Re-entrant by design: asking the launcher's profile lands back
            // here, where it is the receiver and the original runs.
            if (known == null || known === chain.thisObject) {
                chain.proceed()
            } else {
                runCatching { offsetY.invoke(known) }.getOrElse {
                    context.logger.warn("Unable to read the launcher taskbar offset; keeping this profile's", it)
                    chain.proceed()
                }
            }
        }

        context.logger.info("Taskbar only: taskbar aligned with the launcher's own hotseat offset")
    }
}

/**
 * Lets the taskbar hold one icon per hotseat slot, so all of them morph.
 *
 * `TaskbarView.calculateMaxNumIcons()` divides the width of the taskbar window
 * by a slot of `mIconTouchSize + 2 * mItemMarginLeftRight` and adds the views
 * the taskbar always carries itself. Measured here it answers **5** for a slot
 * of `171 + 2 * 29 = 229px`, and the taskbar then shows three app icons: the
 * remaining two slots are the all apps button and the divider.
 *
 * A tablet hotseat holds six icons and a phone hotseat four, so on either the
 * taskbar mirrors fewer icons than the hotseat has. Those extra hotseat icons
 * have nothing to morph out of and appear at the end of the app-to-home
 * transition instead — on this grid the fourth icon simply arrived, fully
 * drawn, in the frame the morph finished.
 *
 * Raising the limit to one slot per hotseat icon plus those two lets every icon
 * travel. The stock answer is still the floor, so this only ever adds capacity.
 */
private class TaskbarIconCount(private val context: FeatureContext) {

    fun install() {
        val profile = requireNotNull(DeviceProfiles.profile(context))
        val taskbarView = requireNotNull(context.findClass("com.android.launcher3.taskbar.TaskbarView"))
        val activityContext = requireNotNull(
            context.findClass("com.android.launcher3.taskbar.TaskbarActivityContext"),
        )

        val maxNumIcons = requireNotNull(Reflect.method(taskbarView, "calculateMaxNumIcons"))
        val contextOf = requireNotNull(Reflect.field(taskbarView, "mActivityContext"))
        val profileOf = requireNotNull(Reflect.field(activityContext, "mDeviceProfile"))
        val hotseatOf = requireNotNull(Reflect.field(profile, "hotseatProfile"))

        context.xposed.hook(maxNumIcons).intercept { chain ->
            val stock = chain.proceed() as Int
            runCatching {
                val hotseat = hotseatOf.get(profileOf.get(contextOf.get(chain.thisObject)))
                val shown = requireNotNull(Reflect.field(hotseat.javaClass, "numShownIcons")).getInt(hotseat)
                maxOf(stock, shown + STATIC_TASKBAR_VIEWS)
            }.getOrElse {
                context.logger.warn("Unable to size the taskbar to the hotseat; keeping the stock limit", it)
                stock
            }
        }

        context.logger.info("Taskbar only: taskbar sized to hold every hotseat icon")
    }
}

/**
 * The all apps button and the divider.
 *
 * Not a guess: `calculateMaxNumIcons()` answered 5 on this device and the
 * taskbar showed three app icons, so two of its slots go to views it adds
 * itself.
 */
private const val STATIC_TASKBAR_VIEWS = 2

/**
 * Stops the icons splitting open from the middle as they leave the stashed pill.
 *
 * `TaskbarViewController.animateIconsForReveal` reveals each icon through a clip
 * rectangle that starts as a band across the middle of it:
 *
 * ```java
 * Rect iconRect = new Rect(0, 0, child.getWidth(), child.getHeight());
 * int centerY = iconRect.centerY();
 * int half    = mStashedHandleHeight / 2;
 * int top     = centerY - half;
 * int bottom  = centerY + half;
 * ```
 *
 * The band is `mStashedHandleHeight` tall against an icon of `mIconTouchSize`,
 * 96px against 171px here, so a little over half the icon shows and the rest
 * opens outwards. On a tablet the icons barely move while that happens and it
 * reads as the pill widening. On a phone hotseat they are also travelling a
 * third of the screen and growing, and the split becomes the thing you watch.
 *
 * Giving the band the icon's own height starts the reveal at full height, so
 * the icons emerge sideways out of the pill and are never cut across. Nothing
 * else reads this field — `TaskbarActivityContext` writes it once and only this
 * animation consumes it, and the identically named field on
 * `StashedHandleViewController`, which outlines the pill itself, is a different
 * one and is left alone.
 *
 * Replacing the reveal with a fade was tried and taken back out: the same call
 * carries the icons that travel to the hotseat on the way home, so fading them
 * left the hotseat row arriving half drawn.
 *
 * The height is restored around the call rather than overwritten, so the value
 * the launcher stored survives for anything that reads it in a later build.
 */
private class TaskbarRevealShape(private val context: FeatureContext) {

    fun install() {
        val controller = requireNotNull(
            context.findClass("com.android.launcher3.taskbar.TaskbarViewController"),
        )
        val taskbarView = requireNotNull(context.findClass("com.android.launcher3.taskbar.TaskbarView"))

        val reveal = requireNotNull(
            controller.declaredMethods.firstOrNull { it.name == "animateIconsForReveal" },
        ).apply { isAccessible = true }
        val bandHeightOf = requireNotNull(Reflect.field(controller, "mStashedHandleHeight"))
        val viewOf = requireNotNull(Reflect.field(controller, "mTaskbarView"))
        val touchSizeOf = requireNotNull(Reflect.field(taskbarView, "mIconTouchSize"))

        context.xposed.hook(reveal).intercept { chain ->
            val owner = chain.thisObject
            val stored = runCatching { bandHeightOf.getInt(owner) }.getOrNull()
            if (stored == null) {
                chain.proceed()
            } else {
                runCatching { bandHeightOf.setInt(owner, touchSizeOf.getInt(viewOf.get(owner))) }
                    .onFailure { context.logger.warn("Unable to widen the taskbar reveal band", it) }
                try {
                    chain.proceed()
                } finally {
                    runCatching { bandHeightOf.setInt(owner, stored) }
                }
            }
        }

        context.logger.info("Taskbar only: taskbar icons revealed whole rather than split open")
    }
}
