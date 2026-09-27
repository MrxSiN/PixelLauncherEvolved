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
 * member should disable one tweak, not crash the process it runs in; a
 * renamed member is matched by [Host] before giving up.
 */
object Reflect {

    fun method(type: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        runCatching { return type.getMethod(name, *parameterTypes) }

        var current: Class<*>? = type
        while (current != null) {
            runCatching {
                return current.getDeclaredMethod(name, *parameterTypes)
                    .apply { isAccessible = true }
            }
            current = current.superclass
        }

        return Host.method(type, name, parameterTypes, false)
    }

    fun field(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            runCatching {
                return current.getDeclaredField(name).apply { isAccessible = true }
            }
            current = current.superclass
        }

        return Host.field(type, name)
    }

    /** [Class.getDeclaredMethod], or the method [Host] matched a missing name to. */
    fun declaredMethod(type: Class<*>, name: String, vararg parameterTypes: Class<*>): Method = try {
        type.getDeclaredMethod(name, *parameterTypes)
    } catch (missing: NoSuchMethodException) {
        Host.method(type, name, parameterTypes, true) ?: throw missing
    }

    /** [Class.getDeclaredField], accessible, or the field [Host] matched a missing name to. */
    fun declaredField(type: Class<*>, name: String): Field = try {
        type.getDeclaredField(name)
    } catch (missing: NoSuchFieldException) {
        Host.field(type, name)?.takeIf { it.declaringClass == type } ?: throw missing
    }.apply { isAccessible = true }

    /** The first method [type] declares under [name], or the name [Host] matched it to, that passes [test]. */
    inline fun declared(type: Class<*>, name: String, test: (Method) -> Boolean): Method? {
        val current = Host.name(type, name)
        return type.declaredMethods.firstOrNull { it.name == current && test(it) }
    }
}
