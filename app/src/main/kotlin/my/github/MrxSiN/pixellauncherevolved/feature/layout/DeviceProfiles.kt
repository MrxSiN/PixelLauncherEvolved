package my.github.MrxSiN.pixellauncherevolved.feature.layout

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.pixellauncherevolved.core.Host
import my.github.MrxSiN.pixellauncherevolved.core.Invoke
import my.github.MrxSiN.pixellauncherevolved.core.Reflect

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
 *
 * Two launcher generations are known, and each lookup below prefers the
 * `CP3A` one (Pixel 8 Pro, Android 17 QPR1, launcher 907) and falls back to the
 * earlier one only by its exact name and only when its types agree. The earlier
 * one is the shape of `CP2A.260805.005` and of AOSP `android-17.0.0_r1`, and
 * it is what Pixel 11 Pro `CD1A.261005.003` reports missing the `CP3A` names
 * of (GitHub issue 1); see HOOK_NOTES.md, "Two launcher generations". A
 * fallback is never looked up through [Host]'s fuzzy matching: a renamed
 * member there is the `CP3A` name's business, not a guess at an older one.
 *
 * Everything here runs once, as a feature installs.
 */
internal object DeviceProfiles {

    const val PROFILE = "com.android.launcher3.DeviceProfile"
    const val BUILDER = "com.android.launcher3.deviceprofile.DeviceProfileBuilder"
    /** `CP2A.260805.005`, `android-17.0.0_r1`. */
    const val NESTED_BUILDER = "com.android.launcher3.DeviceProfile\$Builder"
    const val PROPERTIES = "com.android.launcher3.deviceprofile.DeviceProperties"
    const val PROPERTIES_FACTORY = "com.android.launcher3.deviceprofile.DeviceProperties\$Factory"
    const val TASKBAR_CONFIGURATION = "com.android.launcher3.deviceprofile.TaskbarConfiguration"
    const val GRID = "com.android.launcher3.InvariantDeviceProfile"
    const val SPEC = "com.android.launcher3.deviceprofile.parser.DisplayOptionSpec"
    /** `android-17.0.0_r1`: the spec nested in the grid, made by a constructor rather than `mapTypeIndex`. */
    const val NESTED_SPEC = "com.android.launcher3.InvariantDeviceProfile\$DisplayOptionSpec"

    const val BUILD = "build"
    const val CREATE_PROPERTIES = "createDeviceProperties"
    const val MAP_TYPE_INDEX = "mapTypeIndex"
    const val VERTICAL_BAR = "isVerticalBarLayout"

    private val BOOLEAN = Boolean::class.javaPrimitiveType!!

    /** The device profile builder, top-level (`CP3A`) or nested in `DeviceProfile` (earlier). */
    fun builder(loader: ClassLoader): Class<*>? = Host.cls(loader, BUILDER) ?: Reflect.exactClass(loader, NESTED_BUILDER)

    /** The builder's `build()`: no arguments, answering a `DeviceProfile`. */
    fun build(loader: ClassLoader): Method? {
        val profile = Host.cls(loader, PROFILE) ?: return null
        val builder = builder(loader) ?: return null
        val name = Host.name(builder, BUILD)
        return builder.declaredMethods
            .singleOrNull { it.name == name && it.parameterTypes.isEmpty() && it.returnType == profile }
            ?.apply { isAccessible = true }
    }

    /** One of a `DeviceProfile`'s parts, as [Reflect.field] finds it, by the part's class. */
    fun part(loader: ClassLoader, partClass: String, name: String, vararg earlier: String): Field? {
        val profile = Host.cls(loader, PROFILE) ?: return null
        val type = Host.cls(loader, partClass) ?: return null
        return Reflect.field(profile, type, name, *earlier)
    }

    /**
     * Whether a profile lays its dock down the side.
     *
     * `CP3A` asks the profile's `DeviceProperties`; `android-17.0.0_r1` asks
     * the `DeviceProfile` itself. Both take nothing and answer a boolean. Null
     * when neither is there.
     */
    fun verticalBar(loader: ClassLoader): ((profile: Any) -> Boolean)? {
        val profile = Host.cls(loader, PROFILE) ?: return null
        val propertiesOf = part(loader, PROPERTIES, "deviceProperties", "mDeviceProperties")
        propertiesOf?.let { Reflect.method(it.type, VERTICAL_BAR) }
            ?.takeIf { it.returnType == BOOLEAN }
            ?.let { method -> return { Invoke.noArgs(method, propertiesOf.get(it)) as Boolean } }
        Reflect.exactMethod(profile, VERTICAL_BAR)
            ?.takeIf { it.returnType == BOOLEAN }
            ?.let { method -> return { Invoke.noArgs(method, it) as Boolean } }
        return null
    }

    /**
     * Where the properties of every profile are made, answering them:
     * `DeviceProperties$Factory.createDeviceProperties` (`CP3A`), or the same
     * call as a static method of `DeviceProperties`, which is where the
     * shrinker leaves a companion object's function when it folds the
     * companion away. Null when neither is there.
     */
    fun createProperties(loader: ClassLoader): Method? {
        val properties = Host.cls(loader, PROPERTIES) ?: return null
        Host.cls(loader, PROPERTIES_FACTORY)?.let { factory ->
            val name = Host.name(factory, CREATE_PROPERTIES)
            factory.declaredMethods.firstOrNull { it.name == name && it.returnType == properties }
                ?.let { return it.apply { isAccessible = true } }
        }
        return properties.declaredMethods
            .singleOrNull { it.name == CREATE_PROPERTIES && Modifier.isStatic(it.modifiers) && it.returnType == properties }
            ?.apply { isAccessible = true }
    }

    /** `TaskbarConfiguration(boolean isTaskbarPresent)`, which `CP2A.260805.005` built it through; null when the shrinker inlined it. */
    fun taskbarConfiguration(loader: ClassLoader): Constructor<*>? =
        Host.cls(loader, TASKBAR_CONFIGURATION)?.declaredConstructors
            ?.singleOrNull { it.parameterTypes.size == 1 && it.parameterTypes[0] == BOOLEAN }
            ?.apply { isAccessible = true }

    /**
     * Keeps the calls a profile build makes from being inlined past a hook:
     * `build()` itself and the static helpers of the builder and of
     * `DeviceProperties` that answer [made]. A small constructor reached by a
     * direct call is exactly what ART inlines, and the hook would then never run.
     */
    fun keepCallsReal(xposed: XposedInterface, loader: ClassLoader, made: Class<*>) {
        build(loader)?.let(xposed::deoptimize)
        for (owner in listOfNotNull(builder(loader), Host.cls(loader, PROPERTIES))) {
            for (method in owner.declaredMethods) {
                if (Modifier.isStatic(method.modifiers) && method.returnType == made) xposed.deoptimize(method)
            }
        }
    }

    /** The class [onProfileSpec] hands its copies as: `CP3A`'s, else the earlier nested one. */
    fun specClass(loader: ClassLoader): Class<*>? = Host.cls(loader, SPEC) ?: Reflect.exactClass(loader, NESTED_SPEC)

    /**
     * Calls [edit] with each device profile's own copy of the grid spec as it
     * is made; the copy is a [specClass].
     *
     * - `CP3A`: the answer of `DisplayOptionSpec.mapTypeIndex(boolean, boolean)`.
     * - `android-17.0.0_r1`: each `InvariantDeviceProfile$DisplayOptionSpec`
     *   made by `DisplayOptionSpec(InvariantDeviceProfile, boolean isTwoPanels,
     *   boolean isLandscape)`, which the builder's
     *   `createDefaultDisplayOptionSpec` calls once per profile, as
     *   `mapTypeIndex` is. The one made from a `DisplayOption` for a
     *   secondary display is left alone, as `mapTypeIndex` never sees it either.
     *
     * Throws when neither is there, which leaves the part that asked uninstalled.
     */
    fun onProfileSpec(xposed: XposedInterface, loader: ClassLoader, edit: (spec: Any) -> Unit) {
        Host.cls(loader, SPEC)?.let { spec ->
            val map = Reflect.declaredMethod(spec, MAP_TYPE_INDEX, BOOLEAN, BOOLEAN)
            check(map.returnType == spec) { "$MAP_TYPE_INDEX answers ${map.returnType.name}" }
            xposed.hook(map).intercept { chain -> chain.proceed()?.also(edit) }
            return
        }

        val grid = requireNotNull(Host.cls(loader, GRID)) { "No $GRID" }
        val spec = requireNotNull(specClass(loader)) { "No $SPEC nor $NESTED_SPEC" }
        val make = spec.getDeclaredConstructor(grid, BOOLEAN, BOOLEAN).apply { isAccessible = true }
        keepCallsReal(xposed, loader, spec)
        xposed.hook(make).intercept { chain -> chain.proceed().also { chain.thisObject?.let(edit) } }
    }
}
