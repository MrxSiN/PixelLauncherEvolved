package my.github.MrxSiN.pixellauncherevolved.icons;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageItemInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;

/**
 * What the two icon hooks read, and the answers they give.
 *
 * The launcher resolves every app icon through one method and keys every stored
 * icon by another. Both of those are hooked, and both start here. With the
 * feature switched off {@link #source} is null, so each hook is one volatile
 * reference read and a return: no preference is opened, no name is resolved,
 * nothing is allocated and nothing is logged.
 *
 * With a pack active the work is a binary search over two sorted primitive
 * arrays and, on a hit, one {@code getDrawableForDensity}. It runs on the
 * launcher's own icon worker, inside the miss path of the launcher's icon cache,
 * so scrolling a list of icons never reaches it: what a list binds is the bitmap
 * the launcher already cached.
 */
public final class IconPackState {

    /** The active source, or null for stock behaviour. Written on a cold path only. */
    public static volatile IconSource source;

    /**
     * Today, as 0 to 30.
     *
     * Kept as a number rather than read from a {@code Calendar} per icon, which
     * would allocate one. Written when the feature installs and whenever the
     * date, the time or the time zone changes.
     */
    public static volatile int dayIndex;

    /**
     * How the platform packs a profile into an application's uid.
     *
     * {@code UserHandle.getUserHandleForUid} would answer too, and would
     * allocate a {@code UserHandle} per icon to be asked for a number that is
     * already in the uid.
     */
    private static final int PER_USER_RANGE = 100000;

    private IconPackState() {
    }

    /**
     * The icon to use in place of {@code original}, or {@code original} itself.
     *
     * Only an activity is replaced. The launcher also asks for an icon for a
     * package as a whole — a widget section, a folder of an app's shortcuts —
     * and an {@code ApplicationInfo} names the application class rather than a
     * launchable component, so those are answered from the pack's package-level
     * entry rather than pretended to be a component.
     */
    public static Drawable iconFor(PackageItemInfo info, int uid, int density, Drawable original) {
        Drawable pack = packIcon(info, uid, density);
        return pack != null ? pack : original;
    }

    /**
     * The pack's artwork for {@code info}, or null when the stock icon stands.
     *
     * Asked before the launcher's own icon is loaded, so an app the pack draws
     * never has its own icon read out of its APK only to be thrown away: that
     * read is the launcher's most expensive step per icon.
     */
    public static Drawable packIcon(PackageItemInfo info, int uid, int density) {
        return packIcon(source, info, uid, density);
    }

    /** {@link #packIcon(PackageItemInfo, int, int)} from {@code active} rather than the published source. */
    public static Drawable packIcon(IconSource active, PackageItemInfo info, int uid, int density) {
        if (active == null) return null;

        Resources resources = active.resources;
        String packageName = info.packageName;
        if (resources == null || packageName == null) return null;

        int id;
        if (info instanceof ActivityInfo) {
            String className = info.name;
            if (className == null) return null;
            id = drawableFor(active, packageName, className, uid / PER_USER_RANGE);
        } else {
            IconPackIndex index = active.index;
            id = index == null ? 0 : index.drawableForPackage(packageName);
        }
        return loaded(resources, id, density, null);
    }

    /**
     * Whether {@code packageName/className} in profile {@code userId} wears pack
     * artwork, which the launcher's themed-icon pass must then leave alone.
     */
    public static boolean isReplaced(String packageName, String className, int userId) {
        return isReplaced(source, packageName, className, userId);
    }

    public static boolean isReplaced(IconSource active, String packageName, String className, int userId) {
        return active != null && active.resources != null
                && drawableFor(active, packageName, className, userId) > 0;
    }

    /**
     * The pack drawable for one component, or 0 for the stock icon: an override
     * first, then a date-varying calendar entry, then the pack's own entry.
     */
    private static int drawableFor(IconSource active, String packageName, String className, int userId) {
        int chosen = active.overrides.drawableFor(packageName, className, userId);
        if (chosen == IconOverrides.SYSTEM) return 0;
        if (chosen > 0) return chosen;

        IconPackIndex index = active.index;
        if (index == null) return 0;

        int calendar = index.calendarFor(packageName, className, dayIndex);
        return calendar != 0 ? calendar : index.drawableFor(packageName, className);
    }

    /**
     * What to add to the launcher's freshness identifier for {@code packageName},
     * or null to leave it alone.
     *
     * The launcher stores each icon beside an identifier it asks for per app and
     * regenerates the icon when the two disagree. Adding the active pack, its
     * version and this feature's generation makes a pack change, a pack update
     * or a reset regenerate exactly the icons it should. An app whose icon
     * varies with the date carries the date as well, and one with an override of
     * its own carries a digest of it, so one hand-picked icon does not reload
     * every app.
     */
    public static String freshnessFor(String packageName) {
        return freshnessFor(source, packageName);
    }

    public static String freshnessFor(IconSource active, String packageName) {
        if (active == null || packageName == null) return null;

        int digest = active.overrides.digestOf(packageName);
        IconPackIndex index = active.index;
        boolean dated = index != null && index.hasCalendar(packageName);

        if (digest == 0 && !dated) return active.plainToken;

        StringBuilder token = new StringBuilder(48).append(active.plainToken);
        if (digest != 0) token.append(':').append(digest);
        if (dated) token.append(":d").append(dayIndex);
        return token.toString();
    }

    private static Drawable loaded(Resources resources, int id, int density, Drawable original) {
        if (id == 0) return original;
        try {
            Drawable drawable = resources.getDrawableForDensity(id, density, null);
            if (drawable == null) return original;
            return drawable instanceof AdaptiveIconDrawable ? drawable : fullBleed(drawable);
        } catch (Throwable missing) {
            return original;
        }
    }

    /**
     * Pack artwork as an adaptive icon filling the whole visible shape.
     *
     * Almost every pack ships flat bitmaps, drawn edge to edge or as a glyph
     * on nothing. Handed over as they are, the launcher treats them as a legacy
     * icon: shrunk and set on a white plate, which hides a white glyph entirely.
     * An adaptive icon with a transparent background and the artwork in the
     * visible area is what a pack means: the launcher's own shape mask, shadow
     * and badges still apply, and nothing is added behind the artwork.
     */
    public static Drawable adaptive(Drawable artwork) {
        return artwork instanceof AdaptiveIconDrawable ? artwork : fullBleed(artwork);
    }

    private static Drawable fullBleed(Drawable artwork) {
        float extra = AdaptiveIconDrawable.getExtraInsetFraction();
        float inset = extra / (1 + 2 * extra);
        return new AdaptiveIconDrawable(
                new ColorDrawable(Color.TRANSPARENT), new InsetDrawable(artwork, inset));
    }
}
