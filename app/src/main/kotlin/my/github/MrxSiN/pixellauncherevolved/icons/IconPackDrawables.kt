package my.github.MrxSiN.pixellauncherevolved.icons

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser

import org.xmlpull.v1.XmlPullParser

/**
 * Every drawable an icon pack offers, by name, for the per-app editor.
 *
 * Read from the pack's own `drawable` document rather than by enumerating its
 * resources, because that document is the list the pack means to offer and is in
 * the order its author arranged. It is read once, while the editor is open, on
 * whatever thread the editor put the work on, and dropped when the editor
 * closes: nothing here is resident while the launcher is merely drawing, and
 * nothing here is reached from an icon lookup.
 *
 * Names, not bitmaps. A pack of 25,000 icons cannot have its artwork held in
 * memory, so the editor resolves and loads only the names it is about to draw.
 */
class IconPackDrawables private constructor(
    private val resources: Resources,
    val packageName: String,
    /** In the order the pack lists them. */
    val names: List<String>,
) {

    fun idOf(name: String): Int =
        runCatching { resources.getIdentifier(name, "drawable", packageName) }.getOrDefault(0)

    fun drawableFor(name: String, density: Int) = idOf(name).takeIf { it != 0 }?.let { id ->
        runCatching { resources.getDrawableForDensity(id, density, null) }.getOrNull()
    }

    /**
     * The first [limit] names that contain [query], ignoring case and the
     * underscores a pack separates words with.
     *
     * Bounded because a pack lists tens of thousands of drawables and a settings
     * screen draws what it lists. An empty query is the head of the pack's own
     * order, which is where its author put its newest icons.
     */
    fun search(query: String, limit: Int): List<String> {
        if (query.isBlank()) return names.take(limit)

        val wanted = query.trim().lowercase().replace(' ', '_')
        val found = ArrayList<String>(limit)
        for (name in names) {
            if (name.contains(wanted)) {
                found += name
                if (found.size == limit) break
            }
        }
        return found
    }

    companion object {

        /** Null when the pack is gone or ships no list this module can read. */
        fun of(context: Context, packageName: String): IconPackDrawables? {
            val resources = IconPacks.resourcesOf(context, packageName) ?: return null
            val parser = IconPackFormat.openDrawableList(resources, packageName) ?: return null

            val names = ArrayList<String>(INITIAL_CAPACITY)
            try {
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == TAG_ITEM) {
                        parser.getAttributeValue(null, ATTRIBUTE_DRAWABLE)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { names += it }
                    }
                    event = parser.next()
                }
            } catch (malformed: Throwable) {
                // What was read is still a usable list.
            } finally {
                (parser as? XmlResourceParser)?.close()
            }

            return if (names.isEmpty()) null else IconPackDrawables(resources, packageName, names)
        }

        private const val TAG_ITEM = "item"
        private const val ATTRIBUTE_DRAWABLE = "drawable"
        private const val INITIAL_CAPACITY = 4096
    }
}
