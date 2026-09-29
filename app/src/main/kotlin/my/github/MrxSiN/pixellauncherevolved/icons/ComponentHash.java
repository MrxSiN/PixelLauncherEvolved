package my.github.MrxSiN.pixellauncherevolved.icons;

/**
 * The 64-bit key a component is looked up under.
 *
 * An icon pack maps around 25,000 components, and a lookup happens while the
 * launcher is generating an icon. Keeping the map as sorted primitive arrays
 * makes that lookup a binary search over longs with nothing allocated, where a
 * {@code HashMap<ComponentKey, Integer>} would cost a key object, a string
 * concatenation and a boxed answer per icon.
 *
 * FNV-1a over {@code package/class}, folded over the characters of both strings
 * rather than over a string built from them, so no lookup builds one. The
 * indexer checks its own keys for collisions and drops any pair that shares
 * one, because an icon pack showing the wrong app's artwork is worse than
 * showing the stock icon.
 */
public final class ComponentHash {

    private static final long BASIS = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    /** Reserved for "no entry", so a real key is never zero. */
    public static final long NONE = 0L;

    private ComponentHash() {
    }

    public static long of(String packageName, String className) {
        long hash = fold(BASIS, packageName);
        hash = (hash ^ '/') * PRIME;
        hash = fold(hash, className);
        return hash == NONE ? PRIME : hash;
    }

    /** The key a package alone is looked up under, for a pack that names only one. */
    public static long ofPackage(String packageName) {
        long hash = fold(BASIS, packageName);
        hash = (hash ^ '/') * PRIME;
        return hash == NONE ? PRIME : hash;
    }

    /** [of] with a profile mixed in, for state that must not cross users. */
    public static long ofUser(String packageName, String className, int userId) {
        long hash = of(packageName, className);
        hash = (hash ^ (userId + 1L)) * PRIME;
        return hash == NONE ? PRIME : hash;
    }

    /** `ComponentInfo{pkg/cls}` or `pkg/cls`, as an icon pack writes it. */
    public static long ofDescriptor(String descriptor) {
        int start = descriptor.indexOf('{');
        int end = descriptor.lastIndexOf('}');
        String body = start >= 0 && end > start ? descriptor.substring(start + 1, end) : descriptor;
        int slash = body.indexOf('/');
        if (slash <= 0) return NONE;

        String packageName = body.substring(0, slash);
        String className = body.substring(slash + 1);
        if (className.isEmpty()) return NONE;
        // A pack may abbreviate a class in the package it belongs to.
        if (className.charAt(0) == '.') className = packageName + className;
        return of(packageName, className);
    }

    /** The package half of a descriptor, or null when it names none. */
    public static String packageOfDescriptor(String descriptor) {
        int start = descriptor.indexOf('{');
        int end = descriptor.lastIndexOf('}');
        String body = start >= 0 && end > start ? descriptor.substring(start + 1, end) : descriptor;
        int slash = body.indexOf('/');
        return slash > 0 ? body.substring(0, slash) : null;
    }

    private static long fold(long hash, String text) {
        for (int i = 0, n = text.length(); i < n; i++) {
            hash = (hash ^ text.charAt(i)) * PRIME;
        }
        return hash;
    }

    /** Where [key] sits in a sorted array, or -1. */
    public static int find(long[] keys, long key) {
        int low = 0;
        int high = keys.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long at = keys[mid];
            if (at < key) {
                low = mid + 1;
            } else if (at > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
    }
}
