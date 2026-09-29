package my.github.MrxSiN.pixellauncherevolved.icons;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * One icon pack's component-to-drawable map, compiled.
 *
 * The pack ships this as XML — three megabytes of it, for 24,000 components —
 * which is not something to parse while the launcher is drawing. It is read
 * once per pack version on a worker, turned into sorted primitive arrays, and
 * written beside the settings. A later start reads those arrays back; a lookup
 * is then a binary search with nothing allocated and no resource enumeration.
 *
 * Drawable names are resolved to resource ids while the index is built, so a
 * lookup never calls {@code getIdentifier} either.
 */
public final class IconPackIndex {

    static final int FORMAT = 1;

    /** How many drawables a dynamic calendar entry holds, one per day of the month. */
    public static final int CALENDAR_DAYS = 31;

    public final String packageName;
    public final long version;

    /** Component key to drawable resource id, keys ascending. */
    final long[] keys;
    final int[] ids;
    /** Package key to drawable resource id, for a package the pack maps unambiguously. */
    final long[] packageKeys;
    final int[] packageIds;
    /** Component key to a run of [CALENDAR_DAYS] ids at {@code calendarIds[i * CALENDAR_DAYS]}. */
    final long[] calendarKeys;
    final int[] calendarIds;

    IconPackIndex(
            String packageName,
            long version,
            long[] keys,
            int[] ids,
            long[] packageKeys,
            int[] packageIds,
            long[] calendarKeys,
            int[] calendarIds) {
        this.packageName = packageName;
        this.version = version;
        this.keys = keys;
        this.ids = ids;
        this.packageKeys = packageKeys;
        this.packageIds = packageIds;
        this.calendarKeys = calendarKeys;
        this.calendarIds = calendarIds;
    }

    public int size() {
        return keys.length;
    }

    public int calendarCount() {
        return calendarKeys.length;
    }

    /**
     * The drawable this pack gives {@code packageName/className}, or 0 for none.
     *
     * The exact component first, then the package on its own, which is what
     * keeps a pack working for an app that renamed its launcher activity. The
     * package-only entry exists only where every component of that package
     * pointed at the same drawable, so the fallback cannot show another app's
     * artwork.
     */
    public int drawableFor(String packageName, String className) {
        int at = ComponentHash.find(keys, ComponentHash.of(packageName, className));
        if (at >= 0) return ids[at];

        return drawableForPackage(packageName);
    }

    public int drawableForPackage(String packageName) {
        int at = ComponentHash.find(packageKeys, ComponentHash.ofPackage(packageName));
        return at >= 0 ? packageIds[at] : 0;
    }

    /**
     * The drawable for day {@code dayIndex} (0 based) of a dynamic calendar
     * component, or 0 when this pack does not treat it as one.
     */
    public int calendarFor(String packageName, String className, int dayIndex) {
        int at = ComponentHash.find(calendarKeys, ComponentHash.of(packageName, className));
        if (at < 0) {
            at = ComponentHash.find(calendarKeys, ComponentHash.ofPackage(packageName));
            if (at < 0) return 0;
        }
        return calendarIds[at * CALENDAR_DAYS + dayIndex];
    }

    /** Whether any component of {@code packageName} varies with the date. */
    public boolean hasCalendar(String packageName) {
        return ComponentHash.find(calendarKeys, ComponentHash.ofPackage(packageName)) >= 0;
    }

    void write(File file) throws IOException {
        File temporary = new File(file.getPath() + ".new");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
            out.writeInt(FORMAT);
            out.writeUTF(packageName);
            out.writeLong(version);
            writeLongs(out, keys);
            writeInts(out, ids);
            writeLongs(out, packageKeys);
            writeInts(out, packageIds);
            writeLongs(out, calendarKeys);
            writeInts(out, calendarIds);
        }
        if (!temporary.renameTo(file)) {
            temporary.delete();
            throw new IOException("index not replaced");
        }
    }

    /**
     * The index in {@code file}, or null when it is missing, stale or unreadable.
     *
     * One read of the whole file and bulk copies out of it: this runs on the
     * launcher's main thread while it starts, and reading 24,000 keys a value at
     * a time through a stream costs a hundred milliseconds there.
     */
    static IconPackIndex read(File file, String packageName, long version) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                int count = in.read(bytes, read, bytes.length - read);
                if (count < 0) return null;
                read += count;
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            if (buffer.getInt() != FORMAT) return null;

            // writeUTF: an unsigned length, then the bytes, which for a package name are ASCII.
            int nameLength = buffer.getShort() & 0xffff;
            String name = new String(bytes, buffer.position(), nameLength, StandardCharsets.UTF_8);
            buffer.position(buffer.position() + nameLength);
            if (!packageName.equals(name)) return null;
            if (buffer.getLong() != version) return null;

            long[] keys = readLongs(buffer);
            int[] ids = readInts(buffer);
            long[] packageKeys = readLongs(buffer);
            int[] packageIds = readInts(buffer);
            long[] calendarKeys = readLongs(buffer);
            int[] calendarIds = readInts(buffer);
            if (keys.length != ids.length || packageKeys.length != packageIds.length
                    || calendarKeys.length * CALENDAR_DAYS != calendarIds.length) {
                return null;
            }
            return new IconPackIndex(packageName, version, keys, ids, packageKeys, packageIds, calendarKeys, calendarIds);
        } catch (Throwable unreadable) {
            return null;
        }
    }

    private static void writeLongs(DataOutputStream out, long[] values) throws IOException {
        out.writeInt(values.length);
        for (long value : values) out.writeLong(value);
    }

    private static void writeInts(DataOutputStream out, int[] values) throws IOException {
        out.writeInt(values.length);
        for (int value : values) out.writeInt(value);
    }

    private static long[] readLongs(ByteBuffer buffer) {
        long[] values = new long[buffer.getInt()];
        buffer.asLongBuffer().get(values);
        buffer.position(buffer.position() + values.length * 8);
        return values;
    }

    private static int[] readInts(ByteBuffer buffer) {
        int[] values = new int[buffer.getInt()];
        buffer.asIntBuffer().get(values);
        buffer.position(buffer.position() + values.length * 4);
        return values;
    }
}
