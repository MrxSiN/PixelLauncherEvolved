package my.github.MrxSiN.pixellauncherevolved.hook

import java.lang.reflect.Proxy

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookBuilder
import io.github.libxposed.api.XposedInterface.HookHandle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HookRecorderTest {

    private val unhooked = mutableListOf<String>()

    /** A framework whose every hook answers a handle that notes being unhooked. */
    private val framework = proxy<XposedInterface> { method, args ->
        when (method.name) {
            "hook" -> proxy<HookBuilder> { built, _ ->
                when (built.name) {
                    "intercept" -> proxy<HookHandle> { handle, _ ->
                        if (handle.name == "unhook") unhooked += (args!![0] as java.lang.reflect.Method).name
                        null
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    private val first = String::class.java.getMethod("length")
    private val second = String::class.java.getMethod("isEmpty")

    @Test
    fun aFeatureThatInstallsKeepsItsHooks() {
        val hooks = HookRecorder(framework)

        assertNull(hooks.install { hooks.hook(first).intercept { it.proceed() } })
        assertEquals(emptyList<String>(), unhooked)
    }

    @Test
    fun aFeatureThatFailsHalfwayTakesBackWhatItHadSet() {
        val hooks = HookRecorder(framework)
        val boom = IllegalStateException("a member moved")

        val failure = hooks.install {
            hooks.hook(first).intercept { it.proceed() }
            hooks.hook(second).setPriority(1).intercept { it.proceed() }
            throw boom
        }

        assertSame(boom, failure?.error)
        assertEquals(2, failure?.undone)
        assertEquals(listOf("length", "isEmpty"), unhooked)
    }

    @Test
    fun hooksSetOutsideAnInstallAreNeverTakenBack() {
        val hooks = HookRecorder(framework)
        hooks.hook(first).intercept { it.proceed() }

        hooks.install { throw IllegalStateException() }

        assertEquals(emptyList<String>(), unhooked)
    }

    private inline fun <reified T> proxy(crossinline answer: (java.lang.reflect.Method, Array<Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> answer(method, args) } as T
}
