package my.github.MrxSiN.pixellauncherevolved.icons

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser

import android.os.SystemClock

import java.io.File

import org.xmlpull.v1.XmlPullParser

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Turns an icon pack's `appfilter` document into an [IconPackIndex].
 *
 * Everything expensive happens here, once per pack version: the XML is parsed,
 * every drawable name is resolved to a resource id, and the result is written
 * beside the settings so later launcher starts read arrays instead of markup.
 * Nothing in this class runs on the UI thread, and nothing the launcher draws
 * with calls into it.
 *
 * Two safety rules shape the output. A component whose 64-bit key collides with
 * another's is left out of the index entirely, because an icon pack showing the
 * wrong app's artwork is worse than one showing the stock icon. And a
 * package-only entry is written only where every component of that package
 * pointed at the same drawable, so the fallback for a renamed launcher activity
 * cannot pick an unrelated icon.
 */
class IconPackIndexer(private val context: Context, private val logger: Logger) {

    /**
     * The index for [packageName], read from the cache or built and cached.
     *
     * Null when the pack is gone. A pack that ships no document this module can
     * read, or maps nothing, gets an empty index, cached like any other so it is
     * not parsed again on every start.
     */
    fun indexOf(packageName: String): IconPackIndex? {
        val version = IconPacks.versionOf(context, packageName) ?: return null
        val file = fileFor(packageName)

        IconPackIndex.read(file, packageName, version)?.let { return it }

        val resources = IconPacks.resourcesOf(context, packageName) ?: return null
        val started = SystemClock.elapsedRealtime()
        val built = build(resources, packageName, version)
        logger.info("Icons: indexing $packageName took ${SystemClock.elapsedRealtime() - started} ms")

        runCatching {
            file.parentFile?.mkdirs()
            built.write(file)
            // An index of an earlier format is never read again.
            directory().listFiles { stale -> stale.name != file.name && stale.name.removePrefix("$packageName.").let { rest ->
                    rest.length != stale.name.length && rest.endsWith(".idx") && rest.removeSuffix(".idx").all(Char::isDigit)
                } }
                ?.forEach(File::delete)
        }.onFailure { logger.warn("Icons: the index for $packageName was not cached", it) }
        return built
    }

    /**
     * The index already compiled for [packageName], without compiling one.
     *
     * The selector uses this: a pack whose map is on disk can be previewed
     * against real apps for nothing, and one that has never been chosen is
     * previewed from its own artwork instead rather than costing a
     * multi-megabyte parse for a page that is merely being looked at.
     */
    fun cachedIndexOf(packageName: String): IconPackIndex? {
        val version = IconPacks.versionOf(context, packageName) ?: return null
        return IconPackIndex.read(fileFor(packageName), packageName, version)
    }

    fun forget(packageName: String) {
        runCatching { fileFor(packageName).delete() }
    }

    private fun build(resources: Resources, packageName: String, version: Long): IconPackIndex {
        val parser = IconPackFormat.openAppFilter(resources, packageName) ?: run {
            logger.warn("Icons: $packageName ships no component map this module can read")
            return empty(packageName, version)
        }

        val resolver = DrawableIds(resources, packageName)
        val components = KeyedIds()
        val calendars = LinkedHashMap<Long, String>()
        // Which drawable each package's components agreed on, or 0 once two disagreed.
        val perPackage = HashMap<String, Int>()

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        TAG_ITEM -> readItem(parser, resolver, components, perPackage)
                        TAG_CALENDAR -> readCalendar(parser, calendars)
                    }
                }
                event = parser.next()
            }
        } catch (error: Throwable) {
            logger.warn("Icons: the component map of $packageName is malformed; using what was read", error)
        } finally {
            (parser as? XmlResourceParser)?.close()
        }

        val dropped = components.collisions()
        if (dropped > 0) {
            logger.warn("Icons: $dropped component(s) of $packageName share a key and were left out")
        }

        val calendar = calendarArrays(calendars, resolver)
        if (components.size == 0 && calendar.first.isEmpty()) {
            logger.warn("Icons: $packageName maps nothing")
            return empty(packageName, version)
        }

        val packages = KeyedIds()
        for ((name, id) in perPackage) {
            if (id != 0) packages.put(ComponentHash.ofPackage(name), id)
        }
        packages.collisions()

        logger.info(
            "Icons: indexed $packageName v$version, ${components.size} components, ${calendar.first.size} calendars",
        )
        return IconPackIndex(
            packageName,
            version,
            components.keys(),
            components.ids(),
            packages.keys(),
            packages.ids(),
            calendar.first,
            calendar.second,
        )
    }

    private fun empty(packageName: String, version: Long) =
        IconPackIndex(packageName, version, LongArray(0), IntArray(0), LongArray(0), IntArray(0), LongArray(0), IntArray(0))

    private fun readItem(
        parser: XmlPullParser,
        resolver: DrawableIds,
        into: KeyedIds,
        perPackage: MutableMap<String, Int>,
    ) {
        val component = parser.getAttributeValue(null, ATTRIBUTE_COMPONENT) ?: return
        val drawable = parser.getAttributeValue(null, ATTRIBUTE_DRAWABLE) ?: return
        val key = ComponentHash.ofDescriptor(component)
        if (key == ComponentHash.NONE) return

        val id = resolver.idOf(drawable)
        if (id == 0) return

        into.put(key, id)
        ComponentHash.packageOfDescriptor(component)?.let { name ->
            val agreed = perPackage[name]
            perPackage[name] = if (agreed == null || agreed == id) id else 0
        }
    }

    /**
     * `<calendar component="..." prefix="name_"/>`: the drawables are that
     * prefix followed by the day of the month, 1 to 31.
     */
    private fun readCalendar(parser: XmlPullParser, into: MutableMap<Long, String>) {
        val prefix = parser.getAttributeValue(null, ATTRIBUTE_PREFIX)
            ?: parser.getAttributeValue(null, ATTRIBUTE_DRAWABLE)
            ?: return
        val component = parser.getAttributeValue(null, ATTRIBUTE_COMPONENT) ?: return

        val key = ComponentHash.ofDescriptor(component)
        if (key != ComponentHash.NONE) into[key] = prefix
        // The package on its own too: a calendar app that renames its activity
        // is still the calendar app.
        ComponentHash.packageOfDescriptor(component)?.let { into[ComponentHash.ofPackage(it)] = prefix }
    }

    /** One key per calendar component, and [IconPackIndex.CALENDAR_DAYS] ids behind each. */
    private fun calendarArrays(prefixes: Map<Long, String>, resolver: DrawableIds): Pair<LongArray, IntArray> {
        val keyed = KeyedIds()
        val days = ArrayList<IntArray>(prefixes.size)

        for ((key, prefix) in prefixes) {
            val ids = IntArray(IconPackIndex.CALENDAR_DAYS)
            var any = false
            for (day in 0 until IconPackIndex.CALENDAR_DAYS) {
                ids[day] = resolver.idOf(prefix + (day + 1))
                if (ids[day] != 0) any = true
            }
            if (!any) continue

            // A day the pack left out falls back to the first one it did ship,
            // so a partial set never draws nothing.
            val first = ids.first { it != 0 }
            for (day in ids.indices) if (ids[day] == 0) ids[day] = first

            keyed.put(key, days.size)
            days += ids
        }
        keyed.collisions()

        val order = keyed.ids()
        val flat = IntArray(order.size * IconPackIndex.CALENDAR_DAYS)
        for (at in order.indices) {
            days[order[at]].copyInto(flat, at * IconPackIndex.CALENDAR_DAYS)
        }
        return keyed.keys() to flat
    }

    private fun directory(): File = File(deviceDirectory(), DIRECTORY)

    private fun fileFor(packageName: String) =
        File(directory(), "$packageName.${IconPackFormat.VERSION}.idx")

    /** Beside the settings: the launcher starts before the first unlock. */
    private fun deviceDirectory(): File =
        (if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()).filesDir

    private companion object {
        const val DIRECTORY = "ple_icons"

        const val TAG_ITEM = "item"
        const val TAG_CALENDAR = "calendar"
        const val ATTRIBUTE_COMPONENT = "component"
        const val ATTRIBUTE_DRAWABLE = "drawable"
        const val ATTRIBUTE_PREFIX = "prefix"
    }
}

/**
 * Drawable name to resource id, asked once per distinct name.
 *
 * A pack of 24,000 components uses far fewer distinct drawables, and
 * `getIdentifier` is the expensive part of indexing one.
 */
private class DrawableIds(private val resources: Resources, private val packageName: String) {

    private val known = HashMap<String, Int>()

    fun idOf(name: String): Int = known.getOrPut(name) {
        val drawable = runCatching { resources.getIdentifier(name, "drawable", packageName) }.getOrDefault(0)
        if (drawable != 0) {
            drawable
        } else {
            runCatching { resources.getIdentifier(name, "mipmap", packageName) }.getOrDefault(0)
        }
    }
}

/**
 * Keys and ids being collected, sorted and checked for collisions on the way out.
 *
 * A key claimed twice by the same drawable is one component listed twice, which
 * packs do; a key claimed twice by different drawables is either a duplicate
 * listing that disagrees with itself or a hash collision, and neither is safe to
 * pick a winner for.
 */
internal class KeyedIds {

    private val entries = HashMap<Long, Int>()
    private val conflicting = HashSet<Long>()

    val size: Int get() = entries.size

    fun put(key: Long, id: Int) {
        val existing = entries.put(key, id)
        if (existing != null && existing != id) conflicting += key
    }

    /** Drops every key claimed by two different drawables, and says how many. */
    fun collisions(): Int {
        conflicting.forEach(entries::remove)
        return conflicting.size.also { conflicting.clear() }
    }

    private fun sorted(): List<Map.Entry<Long, Int>> = entries.entries.sortedBy { it.key }

    fun keys(): LongArray = sorted().let { list -> LongArray(list.size) { list[it].key } }

    fun ids(): IntArray = sorted().let { list -> IntArray(list.size) { list[it].value } }
}
