package my.github.MrxSiN.pixellauncherevolved.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Calls a method reflectively without allocating an argument array.
 *
 * Kotlin passes a fresh empty array (or a copy of a spread one) to every
 * vararg call, which is one allocation per call on paths that run once per app,
 * per search result, per layout or per drag move. Java hands the array over as it is.
 */
public final class Invoke {

    private static final Object[] NONE = new Object[0];

    private Invoke() {
    }

    public static Object noArgs(Method method, Object target)
            throws IllegalAccessException, InvocationTargetException {
        return method.invoke(target, NONE);
    }

    /** Calls {@code method} with {@code args} as they are: one array kept per call site, refilled. */
    public static Object with(Method method, Object target, Object[] args)
            throws IllegalAccessException, InvocationTargetException {
        return method.invoke(target, args);
    }
}
