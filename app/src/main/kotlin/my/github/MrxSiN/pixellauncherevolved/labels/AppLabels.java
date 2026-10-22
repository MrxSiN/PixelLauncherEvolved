package my.github.MrxSiN.pixellauncherevolved.labels;

import my.github.MrxSiN.pixellauncherevolved.icons.ComponentHash;

/**
 * The labels a person chose by hand, compiled for lookup.
 *
 * The launcher binds an app's label every time an icon is built or recycled:
 * Home, the dock, folders, every app drawer row and every search result. So the
 * lookup is the same shape as {@link my.github.MrxSiN.pixellauncherevolved.icons.IconOverrides}:
 * a binary search over sorted 64-bit keys of component and profile together,
 * folded over the strings the launcher already holds, with nothing allocated.
 * The strings handed back are the stored ones, so a bind creates none.
 *
 * {@link #current} is swapped whole when the stored labels change, and only
 * then, so a bind reads one volatile reference and never a preference.
 */
public final class AppLabels {

    /** The label is left off this app's icon on the Home screen, the dock and Home folders. */
    public static final int HIDE_HOME = 1;
    /** The label is left off this app's icon in the app drawer and its suggestions row. */
    public static final int HIDE_DRAWER = 2;

    public static final AppLabels NONE = new AppLabels(
            new long[0], new String[0], new int[0], new String[0], new String[0], new int[0]);

    private static volatile AppLabels current = NONE;
    private static volatile boolean homeOnly;
    /** Surfaces ({@link #HIDE_HOME}, {@link #HIDE_DRAWER}, {@link #HIDE_FOLDER}) where every label is left off. */
    private static volatile int hiddenSurfaces;

    /** For {@link #hiddenSurfaces()} only: Home folders, apart from the Home screen itself. */
    public static final int HIDE_FOLDER = 4;

    private final long[] keys;
    /** The chosen name, or null where only a label's visibility was chosen. */
    private final String[] names;
    private final int[] flags;
    private final String[] packages;
    private final String[] classes;
    private final int[] users;
    private final boolean hasNames;

    AppLabels(long[] keys, String[] names, int[] flags, String[] packages, String[] classes, int[] users) {
        this.keys = keys;
        this.names = names;
        this.flags = flags;
        this.packages = packages;
        this.classes = classes;
        this.users = users;
        boolean any = false;
        for (String name : names) {
            if (name != null) {
                any = true;
                break;
            }
        }
        this.hasNames = any;
    }

    public static AppLabels current() {
        return current;
    }

    public static void publish(AppLabels labels) {
        current = labels;
    }

    /**
     * Whether a chosen name shows on the Home screen, the dock and Home folders
     * only, leaving the app drawer, its order and search to the app's own name.
     */
    public static int hiddenSurfaces() {
        int mode = modeSurfaces;
        return mode >= 0 ? mode : hiddenSurfaces;
    }

    /** The winning Mode's surfaces, replacing the everywhere switches while it is on; -1 to follow them. */
    private static volatile int modeSurfaces = -1;

    public static void setModeSurfaces(int value) {
        modeSurfaces = value;
    }

    public static void setHiddenSurfaces(int value) {
        hiddenSurfaces = value;
    }

    public static boolean homeOnly() {
        return homeOnly;
    }

    public static void setHomeOnly(boolean value) {
        homeOnly = value;
    }

    public boolean isEmpty() {
        return keys.length == 0;
    }

    /** Whether any entry renames its app, which is when sorting and search have work to do. */
    public boolean hasNames() {
        return hasNames;
    }

    public int size() {
        return keys.length;
    }

    /** Where {@code packageName/className} in profile {@code userId} sits, or -1. */
    public int find(String packageName, String className, int userId) {
        return ComponentHash.find(keys, ComponentHash.ofUser(packageName, className, userId));
    }

    public String name(int at) {
        return names[at];
    }

    public int flags(int at) {
        return flags[at];
    }

    public String packageName(int at) {
        return packages[at];
    }

    public String className(int at) {
        return classes[at];
    }

    public int userId(int at) {
        return users[at];
    }
}
