package my.github.MrxSiN.pixellauncherevolved.feature.layout

import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Proxy

import android.content.Context
import android.content.ContextWrapper

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookBuilder
import io.github.libxposed.api.XposedInterface.HookHandle

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.hook.HookRecorder
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsStore

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Full tablet layout on a launcher without `parseWeightedPredefinedDisplayOption`
 * (the stand-ins in `com.android.launcher3` are shaped like android-17.0.0_r1).
 *
 * The large-screen classification without the grid pick to wrap would let the
 * launcher choose the 6x5 tablet grid and delete the rows a phone grid has past
 * it, so nothing may be hooked at all: not even for a moment before the
 * install fails and its hooks are taken back.
 */
class TabletModeFeatureTest {

    private val hooked = mutableListOf<Executable>()

    private val framework: XposedInterface = proxy { method, args ->
        when (method.name) {
            "hook" -> {
                hooked += args!![0] as Executable
                proxy<HookBuilder> { built, _ -> if (built.name == "intercept") proxy<HookHandle> { _, _ -> null } else null }
            }
            else -> null
        }
    }

    @Test
    fun `without the grid pick nothing is hooked`() {
        val hooks = HookRecorder(framework)
        val context = FeatureContext(hooks, javaClass.classLoader!!, unconstructedContext(), settings, logger)

        val failure = hooks.install { TabletModeFeature().install(context) }

        assertNotNull("installed without the grid pick", failure)
        assertTrue("hooked ${hooked.map { it.name }} before finding everything", hooked.isEmpty())
    }

    private val settings: SettingsStore = proxy { method, _ -> if (method.returnType == Boolean::class.javaPrimitiveType) false else 0 }

    private val logger = object : Logger {
        override fun info(message: String) = Unit
        override fun warn(message: String, error: Throwable?) = Unit
    }

    /** A `Context` nothing here calls: android.jar's stubs cannot be constructed off a device. */
    private fun unconstructedContext(): Context {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val allocate = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, ContextWrapper::class.java) as Context
    }

    private companion object {
        inline fun <reified T> proxy(crossinline answer: (Method, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> answer(method, args) } as T
    }
}
