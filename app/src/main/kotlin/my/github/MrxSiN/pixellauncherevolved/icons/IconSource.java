package my.github.MrxSiN.pixellauncherevolved.icons;

import android.content.res.Resources;

/**
 * Everything an icon lookup needs, as one object that never changes.
 *
 * Built on a cold path — a setting changed, a pack installed, the launcher
 * starting — and published once. A lookup reads the fields and nothing else, so
 * it never sees half of a change and never parses a preference, a resource name
 * or a line of XML.
 *
 * {@link #plainToken} is the freshness identifier for an app with no override of
 * its own and no date-varying icon, which is almost every app: precomputing it
 * keeps the launcher's per-app freshness check free of string building.
 */
public final class IconSource {

    public final String packPackage;
    public final long packVersion;
    public final int generation;
    /** Null when the pack ships no component map this module can read. */
    public final IconPackIndex index;
    public final IconOverrides overrides;
    /** Null when the pack's resources cannot be opened, which disables it. */
    public final Resources resources;

    /** The freshness identifier for an app this feature treats plainly. */
    public final String plainToken;

    public IconSource(
            String packPackage,
            long packVersion,
            int generation,
            IconPackIndex index,
            IconOverrides overrides,
            Resources resources) {
        this.packPackage = packPackage;
        this.packVersion = packVersion;
        this.generation = generation;
        this.index = index;
        this.overrides = overrides;
        this.resources = resources;
        this.plainToken = "ple" + IconPackFormat.VERSION + ":" + packPackage + ':' + packVersion + ':' + generation;
    }
}
