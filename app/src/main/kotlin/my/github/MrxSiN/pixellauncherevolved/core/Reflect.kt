package my.github.MrxSiN.pixellauncherevolved.core

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Lookups that reach launcher internals regardless of visibility.
 *
 * The launcher declares plenty of what this module needs as private, and often
 * on a base class rather than on the type actually in hand, so neither
 * `getMethod` nor a single `getDeclaredMethod` is enough on its own. Both
 * lookups return null instead of throwing: a launcher update that renames one
 * member should disable one tweak, not crash the process it runs in.
 */
object Reflect {

    fun method(type: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        runCatching { return type.getMethod(name, *parameterTypes) }

        var current: Class<*>? = type
        while (current != null) {
            runCatching {
                return current!!.getDeclaredMethod(name, *parameterTypes)
                    .apply { isAccessible = true }
            }
            current = current.superclass
        }

        return null
    }

    fun field(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            runCatching {
                return current!!.getDeclaredField(name).apply { isAccessible = true }
            }
            current = current.superclass
        }

        return null
    }
}
