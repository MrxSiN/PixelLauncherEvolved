package my.github.MrxSiN.pixellauncherevolved.feature.layout

import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * Where the launcher decides what kind of device it is laying out for.
 *
 * ```
 * com.android.launcher3.deviceprofile.DeviceProfileBuilder
 *   public DeviceProfile build()
 * com.android.launcher3.deviceprofile.DeviceProperties$Factory
 *   public static DeviceProperties createDeviceProperties(
 *       boolean, WindowBounds, DeviceConfiguration, boolean)
 * ```
 *
 * Every layout tweak hangs off one of these two, so their names live here
 * rather than in each of them. On `CP2A.260805.005` the builder was the nested
 * `com.android.launcher3.DeviceProfile$Builder`; `CP3A.260905.009` moved it out
 * to a class of its own.
 *
 * `build()` calls `createDeviceProperties` and then derives the workspace, app
 * drawer, hotseat and taskbar profiles from what it answered, so a hook on the
 * factory reaches the properties before anything has been measured from them
 * and a hook on `build` reaches the finished profile after everything has.
 */
internal object DeviceProfiles {

    const val PROFILE = "com.android.launcher3.DeviceProfile"
    const val BUILDER = "com.android.launcher3.deviceprofile.DeviceProfileBuilder"
    const val PROPERTIES_FACTORY = "com.android.launcher3.deviceprofile.DeviceProperties\$Factory"

    const val BUILD = "build"
    const val CREATE_PROPERTIES = "createDeviceProperties"

    fun profile(context: FeatureContext): Class<*>? = context.findClass(PROFILE)

    fun builder(context: FeatureContext): Class<*>? = context.findClass(BUILDER)

    fun propertiesFactory(context: FeatureContext): Class<*>? =
        context.findClass(PROPERTIES_FACTORY)
}
