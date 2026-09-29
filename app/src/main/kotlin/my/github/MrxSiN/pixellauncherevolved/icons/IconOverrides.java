package my.github.MrxSiN.pixellauncherevolved.icons;

/**
 * The icons a person chose by hand, compiled for lookup.
 *
 * Keyed by component and profile together, so a package present in both a
 * personal and a work profile — or in a clone — keeps a choice made for one of
 * them out of the other. Sorted primitive arrays for the same reason the pack
 * index uses them: the lookup happens while the launcher is generating an icon,
 * and it allocates nothing.
 *
 * {@link #digestOf} is the other half. The launcher keys its stored icons by a
 * freshness identifier it asks for per app; adding this digest to it is what
 * makes one changed override regenerate that app's icon and no other.
 */
public final class IconOverrides {

    /** An override that asks for the stock icon rather than for pack artwork. */
    public static final int SYSTEM = -1;

    public static final IconOverrides NONE = new IconOverrides(new long[0], new int[0], new long[0], new int[0]);

    private final long[] keys;
    private final int[] ids;
    /** Package key to a digest of every override under that package, any profile. */
    private final long[] packageKeys;
    private final int[] packageDigests;

    IconOverrides(long[] keys, int[] ids, long[] packageKeys, int[] packageDigests) {
        this.keys = keys;
        this.ids = ids;
        this.packageKeys = packageKeys;
        this.packageDigests = packageDigests;
    }

    public boolean isEmpty() {
        return keys.length == 0;
    }

    public int size() {
        return keys.length;
    }

    /**
     * The chosen resource id, {@link #SYSTEM} for a deliberate stock icon, or 0
     * when this component was left to the pack.
     */
    public int drawableFor(String packageName, String className, int userId) {
        int at = ComponentHash.find(keys, ComponentHash.ofUser(packageName, className, userId));
        return at >= 0 ? ids[at] : 0;
    }

    /**
     * A number that changes whenever an override under {@code packageName}
     * changes, and stays the same otherwise. 0 for a package with none.
     */
    public int digestOf(String packageName) {
        int at = ComponentHash.find(packageKeys, ComponentHash.ofPackage(packageName));
        return at >= 0 ? packageDigests[at] : 0;
    }
}
