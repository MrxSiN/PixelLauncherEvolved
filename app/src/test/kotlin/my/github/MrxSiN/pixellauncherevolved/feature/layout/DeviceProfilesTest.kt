package my.github.MrxSiN.pixellauncherevolved.feature.layout

import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Proxy

import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.deviceprofile.DeviceProfileBuilder
import com.android.launcher3.deviceprofile.HotseatProfile
import com.android.launcher3.deviceprofile.WorkspaceProfile
import com.android.launcher3.deviceprofile.parser.DisplayOptionSpec

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookBuilder
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker

import my.github.MrxSiN.pixellauncherevolved.core.Reflect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How [DeviceProfiles] picks between the two launcher generations, against
 * the stand-ins in `com.android.launcher3` (test sources only). A test plays
 * the earlier launcher by hiding CP3A's classes from the class loader.
 *
 * These prove the module's choices — CP3A first, the earlier name only with
 * matching types, nothing on a guess — not what a real Pixel 11 Pro holds.
 */
class DeviceProfilesTest {

    private val cp3a = javaClass.classLoader!!
    private val earlier = Hiding(DeviceProfiles.BUILDER, DeviceProfiles.SPEC)

    @Test
    fun `CP3A's builder is preferred while it is there`() {
        assertEquals(DeviceProfileBuilder::class.java, DeviceProfiles.build(cp3a)?.declaringClass)
    }

    @Test
    fun `the earlier launcher's nested builder is used without CP3A's`() {
        assertEquals(DeviceProfile.Builder::class.java, DeviceProfiles.build(earlier)?.declaringClass)
    }

    @Test
    fun `no builder at all leaves nothing to hook`() {
        assertNull(DeviceProfiles.build(Hiding(DeviceProfiles.BUILDER, DeviceProfiles.NESTED_BUILDER)))
    }

    @Test
    fun `a part is found under its earlier name, and only with its own type`() {
        // DeviceProfile.workspaceProfile is a String in the stand-in: passed over for mWorkspaceProfile.
        val workspace = DeviceProfiles.part(cp3a, WorkspaceProfile::class.java.name, "workspaceProfile", "mWorkspaceProfile")
        assertEquals("mWorkspaceProfile", workspace?.name)

        val hotseat = DeviceProfiles.part(cp3a, HotseatProfile::class.java.name, "hotseatProfile", "mHotseatProfile")
        assertEquals("mHotseatProfile", hotseat?.name)
    }

    @Test
    fun `a part of the wrong type under every name is missing`() {
        assertNull(Reflect.field(DeviceProfile::class.java, WorkspaceProfile::class.java, "workspaceProfile"))
        assertNull(Reflect.field(DeviceProfile::class.java, HotseatProfile::class.java, "workspaceProfile", "mWorkspaceProfile"))
    }

    @Test
    fun `the vertical bar is asked of the profile when its properties have no answer`() {
        val verticalBar = DeviceProfiles.verticalBar(cp3a)
        assertNotNull(verticalBar)
        assertTrue(verticalBar!!(DeviceProfile()))
    }

    @Test
    fun `without a properties factory the configuration's constructor is offered`() {
        assertNull(DeviceProfiles.createProperties(cp3a))
        assertEquals(listOf(Boolean::class.javaPrimitiveType), DeviceProfiles.taskbarConfiguration(cp3a)?.parameterTypes?.toList())
    }

    @Test
    fun `CP3A's grid spec is edited through mapTypeIndex`() {
        val framework = Framework()
        val edited = mutableListOf<Any>()
        DeviceProfiles.onProfileSpec(framework.xposed, cp3a) { edited += it }

        val hooked = framework.hooks.keys.single() as Method
        assertEquals("mapTypeIndex", hooked.name)
        assertTrue(framework.deoptimized.isEmpty())

        val copy = DisplayOptionSpec(4)
        framework.hooks.getValue(hooked).intercept(chain(thisObject = DisplayOptionSpec(4), result = copy))
        assertSame(copy, edited.single())
    }

    @Test
    fun `the earlier grid spec is edited as each profile's is constructed`() {
        val framework = Framework()
        val edited = mutableListOf<Any>()
        DeviceProfiles.onProfileSpec(framework.xposed, earlier) { edited += it }

        val hooked = framework.hooks.keys.single() as Constructor<*>
        assertEquals(InvariantDeviceProfile.DisplayOptionSpec::class.java, hooked.declaringClass)
        assertEquals(
            listOf(InvariantDeviceProfile::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType),
            hooked.parameterTypes.toList(),
        )
        // build() is kept from inlining the small constructor past the hook.
        assertTrue(framework.deoptimized.any { it is Method && it.name == "build" && it.declaringClass == DeviceProfile.Builder::class.java })

        val made = InvariantDeviceProfile.DisplayOptionSpec(InvariantDeviceProfile(), false, false)
        framework.hooks.getValue(hooked).intercept(chain(thisObject = made, result = null))
        assertSame(made, edited.single())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `no grid spec of either generation refuses to install`() {
        DeviceProfiles.onProfileSpec(Framework().xposed, Hiding(DeviceProfiles.SPEC, DeviceProfiles.NESTED_SPEC)) {}
    }

    /** A class loader that has never heard of [hidden]. */
    private class Hiding(private vararg val hidden: String) : ClassLoader(DeviceProfilesTest::class.java.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name in hidden) throw ClassNotFoundException(name)
            return super.loadClass(name, resolve)
        }
    }

    /** A framework that keeps what was hooked, with its hooker, and what was deoptimized. */
    private class Framework {
        val hooks = LinkedHashMap<Executable, Hooker>()
        val deoptimized = mutableListOf<Executable>()

        val xposed: XposedInterface = proxy { method, args ->
            when (method.name) {
                "hook" -> {
                    val origin = args!![0] as Executable
                    proxy<HookBuilder> { built, builtArgs ->
                        when (built.name) {
                            "intercept" -> {
                                hooks[origin] = builtArgs!![0] as Hooker
                                proxy<HookHandle> { _, _ -> null }
                            }
                            else -> null
                        }
                    }
                }
                "deoptimize" -> {
                    deoptimized += args!![0] as Executable
                    true
                }
                else -> null
            }
        }
    }

    private fun chain(thisObject: Any, result: Any?): Chain = proxy { method, _ ->
        when (method.name) {
            "getThisObject" -> thisObject
            "proceed" -> result
            else -> null
        }
    }

    private companion object {
        inline fun <reified T> proxy(crossinline answer: (Method, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> answer(method, args) } as T
    }
}
