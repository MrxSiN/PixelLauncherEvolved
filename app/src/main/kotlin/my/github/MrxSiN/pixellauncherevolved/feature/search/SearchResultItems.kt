package my.github.MrxSiN.pixellauncherevolved.feature.search

import java.lang.reflect.Field
import java.util.WeakHashMap

/**
 * Reads the search result behind one of the app drawer's list entries.
 *
 * By the time the launcher hands its results to the list they are adapter
 * items, and the item class that carries a search target is one the launcher's
 * shrinker renames. The field is not: it is the only one on that class whose
 * type is `android.app.search.SearchTarget`, so it is found by type rather than
 * by name, and the answer is remembered per item class.
 *
 * An entry that carries no target — the launcher's own rows — reads as null and
 * is therefore never hidden.
 */
class SearchResultItems(private val targets: SearchTargets) {

    private val fields = WeakHashMap<Class<*>, Optional>()

    private class Optional(val field: Field?)

    fun read(item: Any?): SearchResult? {
        if (item == null) return null
        val field = fields.getOrPut(item.javaClass) { Optional(targetField(item.javaClass)) }.field
            ?: return null

        return targets.read(runCatching { field.get(item) }.getOrNull())
    }

    private fun targetField(type: Class<*>): Field? {
        var current: Class<*>? = type
        while (current != null) {
            current.declaredFields
                .firstOrNull { targets.isTarget(it.type) }
                ?.let { return it.apply { isAccessible = true } }
            current = current.superclass
        }
        return null
    }
}
