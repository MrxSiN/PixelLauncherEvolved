package my.github.MrxSiN.pixellauncherevolved.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Host symbols that survive a launcher update.
 *
 * Every lookup is by name first, exactly as before; this class is consulted
 * only after a name misses, and a start costs one small file read. A name the running build no longer
 * has is matched once by DexKit ({@link HostDex}) against fingerprints recorded
 * from a build where it resolved, and the answer, found or not, is written to
 * {@code host.map} under a key naming this launcher build and this module
 * build. Later starts read the answer back without DexKit.
 *
 * Map keys: {@code c<orig>} class, {@code m<start>#<name>(<params>)} method
 * (a trailing {@code !} when only {@code start} itself is searched),
 * {@code f<start>#<name>} field, {@code n<owner>#<name>} a name any overload
 * answers to. Values are {@code <current>} for a class, {@code <owner>#<name>}
 * for a member, {@code ""} for none.
 */
public final class Host {

    static final int FORMAT = 2;

    static boolean on;
    /** A lookup reached {@link HostDex} this process. */
    static boolean dex;
    static ClassLoader loader;
    static Logger log;
    static String apk;
    static String packageName;
    static String moduleApk;
    static String launcherKey;
    static File dir;

    /** Persisted answers for this build. */
    static final HashMap<String, String> map = new HashMap<>();
    /** Current class name to the name the sources use, for moved classes only. */
    static final HashMap<String, String> rev = new HashMap<>();

    private Host() {
    }

    /** Reads this build's answers: one small file. */
    public static void init(Context app, ClassLoader classLoader, String moduleSourceDir, Logger logger) {
        try {
            ApplicationInfo info = app.getApplicationInfo();
            loader = classLoader;
            log = logger;
            apk = info.sourceDir;
            packageName = info.packageName;
            moduleApk = moduleSourceDir;
            // Every install gets a fresh code path, and an OTA a fresh fingerprint.
            launcherKey = info.sourceDir + '|' + Build.FINGERPRINT + '|' + FORMAT;
            String key = launcherKey + '|' + moduleSourceDir;
            // Device protected, like the settings: the launcher starts before the first unlock.
            dir = new File(info.deviceProtectedDataDir, "ple_host");
            File file = new File(dir, "host.map");
            boolean current = false;
            String text = read(file);
            if (text != null && text.length() > key.length() && text.charAt(key.length()) == '\n' && text.startsWith(key)) {
                current = true;
                for (int at = key.length() + 1, end; at < text.length(); at = end + 1) {
                    end = text.indexOf('\n', at);
                    if (end < 0) end = text.length();
                    int tab = text.indexOf('\t', at);
                    if (tab > at && tab < end) put(text.substring(at, tab), text.substring(tab + 1, end));
                }
            }
            if (!current) {
                dir.mkdirs();
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write((key + '\n').getBytes(StandardCharsets.UTF_8));
                }
                logger.info("Host: new launcher or module build; symbols are matched again as they are missed");
            }
            on = true;
        } catch (Throwable error) {
            logger.warn("Host: symbol map unavailable; lookups stay by name only", error);
        }
    }

    /**
     * Install finished: release DexKit and refresh fingerprints once per launcher
     * build. A start that needed neither never loads {@link HostDex}.
     */
    public static void settle() {
        if (!on || !dex && answer("r") != null && !new File(dir, "selftest").exists()) return;
        HostDex.settle();
    }

    private static String read(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[in.available()];
            int n = 0;
            for (int r; n < bytes.length && (r = in.read(bytes, n, bytes.length - n)) > 0; ) n += r;
            return new String(bytes, 0, n, StandardCharsets.UTF_8);
        } catch (Throwable missing) {
            return null;
        }
    }

    public static Class<?> cls(ClassLoader classLoader, String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException | LinkageError ignored) {
        }
        if (!on) return null;
        String current = answer('c' + name);
        if (current == null) {
            synchronized (HostDex.LOCK) {
                dex = true;
                current = answer('c' + name);
                if (current == null) current = remember('c' + name, HostDex.findClass(name));
            }
        }
        if (current.isEmpty()) return null;
        try {
            return Class.forName(current, false, classLoader);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    /** {@link #cls} that throws, for callers written around {@code Class.forName}. */
    public static Class<?> clsOrThrow(ClassLoader classLoader, String name) throws ClassNotFoundException {
        Class<?> type = cls(classLoader, name);
        if (type == null) throw new ClassNotFoundException(name);
        return type;
    }

    /** A method {@code type} or a superclass declared under {@code name}, found after the name missed. */
    public static Method method(Class<?> type, String name, Class<?>[] parameters, boolean declaredOnly) {
        if (!on) return null;
        StringBuilder key = new StringBuilder(96).append('m').append(type.getName()).append('#').append(name).append('(');
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) key.append(',');
            key.append(parameters[i].getName());
        }
        key.append(')');
        if (declaredOnly) key.append('!');
        String k = key.toString();
        String found = answer(k);
        if (found == null) {
            synchronized (HostDex.LOCK) {
                dex = true;
                found = answer(k);
                if (found == null) found = remember(k, HostDex.findMethod(type, name, parameters, declaredOnly));
            }
        }
        if (found.isEmpty()) return null;
        int hash = found.indexOf('#');
        Class<?> owner = within(type, found.substring(0, hash));
        if (owner == null) return null;
        try {
            Method method = owner.getDeclaredMethod(found.substring(hash + 1), parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException | LinkageError ignored) {
            return null;
        }
    }

    /** A field {@code type} or a superclass declared under {@code name}, found after the name missed. */
    public static Field field(Class<?> type, String name) {
        if (!on) return null;
        String k = 'f' + type.getName() + '#' + name;
        String found = answer(k);
        if (found == null) {
            synchronized (HostDex.LOCK) {
                dex = true;
                found = answer(k);
                if (found == null) found = remember(k, HostDex.findField(type, name));
            }
        }
        if (found.isEmpty()) return null;
        int hash = found.indexOf('#');
        Class<?> owner = within(type, found.substring(0, hash));
        if (owner == null) return null;
        try {
            Field field = owner.getDeclaredField(found.substring(hash + 1));
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * The name {@code owner} now declares under {@code name}, for callers that
     * pick an overload by shape. {@code name} itself whenever it is declared.
     */
    public static String name(Class<?> owner, String name) {
        if (!on) return name;
        String k = 'n' + owner.getName() + '#' + name;
        String found = answer(k);
        if (found != null) return found.isEmpty() ? name : found.substring(found.indexOf('#') + 1);
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) return name;
        }
        synchronized (HostDex.LOCK) {
                dex = true;
            found = answer(k);
            if (found == null) found = remember(k, HostDex.findMethod(owner, name, null, true));
        }
        return found.isEmpty() ? name : found.substring(found.indexOf('#') + 1);
    }

    /** The name the sources use for a class the running build calls {@code current}. */
    static String original(String current) {
        synchronized (map) {
            String original = rev.get(current);
            return original != null ? original : current;
        }
    }

    /** The name the running build uses for a class the sources call {@code original}; null when unknown. */
    static String currentIfKnown(String original) {
        synchronized (map) {
            String current = map.get('c' + original);
            return current == null || current.isEmpty() ? null : current;
        }
    }

    static String answer(String key) {
        synchronized (map) {
            return map.get(key);
        }
    }

    private static void put(String key, String value) {
        synchronized (map) {
            map.put(key, value);
            if (key.charAt(0) == 'c' && !value.isEmpty()) rev.put(value, key.substring(1));
        }
    }

    /** Persists a flag, such as {@code r}: this launcher build's fingerprints are recorded. */
    static void mark(String key, String value) {
        remember(key, value);
    }

    private static String remember(String key, String value) {
        if (value == null) value = "";
        put(key, value);
        try (FileOutputStream out = new FileOutputStream(new File(dir, "host.map"), true)) {
            out.write((key + '\t' + value + '\n').getBytes(StandardCharsets.UTF_8));
        } catch (Throwable error) {
            log.warn("Host: answer not saved for " + key, error);
        }
        if (!value.isEmpty() && key.length() > 1) log.info("Host: " + key.substring(1) + " -> " + value);
        return value;
    }

    private static Class<?> within(Class<?> type, String owner) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(owner)) return c;
        }
        return null;
    }
}
