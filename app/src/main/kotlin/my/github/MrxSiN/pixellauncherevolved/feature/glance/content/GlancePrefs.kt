package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import android.content.SharedPreferences

/**
 * At a Glance content's stored choices: which sources are on (`glance_source_<id>`), when each
 * shows (`glance_when_<id>`), and their order (`glance_order`, ids comma-separated).
 *
 * The whole set as one portable value ([GlanceChoices]) for backups and reset, keyed by source id,
 * which never changes and names the same source on any phone.
 */
class GlancePrefs(
    private val preferences: SharedPreferences,
    /** Source ids, to tell an option key's id from its name; ids may hold underscores. */
    private val ids: () -> Collection<String> = { GlanceSources.all.map { it.id } },
) {

    fun choices(): GlanceChoices {
        val on = ArrayList<String>()
        val whens = LinkedHashMap<String, String>()
        val options = sortedMapOf<String, MutableMap<String, String>>()
        for ((key, value) in preferences.all) {
            if (key.startsWith(GlanceSources.KEY_PREFIX) && value == true) on += key.removePrefix(GlanceSources.KEY_PREFIX)
            if (key.startsWith(GlanceWhen.KEY_PREFIX) && value is String) whens[key.removePrefix(GlanceWhen.KEY_PREFIX)] = value
            if (key.startsWith(GlanceOptions.KEY_PREFIX) && value is String) {
                GlanceOptions.split(key, ids())?.let { (id, name) -> options.getOrPut(id) { sortedMapOf() }[name] = value }
            }
        }
        return GlanceChoices(on.sorted().toSet(), whens.toSortedMap(), order(), options)
    }

    /** Replaces every stored choice with [choices]'s. */
    fun replace(choices: GlanceChoices) {
        val editor = preferences.edit()
        for (key in preferences.all.keys) {
            if (key.startsWith(GlanceSources.KEY_PREFIX) || key.startsWith(GlanceWhen.KEY_PREFIX) ||
                key.startsWith(GlanceOptions.KEY_PREFIX) || key.startsWith(GlanceOptions.SEEN_PREFIX) || key == ORDER
            ) editor.remove(key)
        }
        for (id in choices.on) editor.putBoolean(GlanceSources.KEY_PREFIX + id, true)
        for ((id, value) in choices.whens) editor.putString(GlanceWhen.KEY_PREFIX + id, value)
        for ((id, named) in choices.options) for ((name, value) in named) editor.putString(GlanceOptions.key(id, name), value)
        if (choices.order.isNotEmpty()) editor.putString(ORDER, choices.order.joinToString(","))
        editor.apply()
    }

    /** Source ids in the order chosen; ids never ordered are not listed. */
    fun order(): List<String> = preferences.getString(ORDER, null).orEmpty().split(',').filter { it.isNotBlank() }

    fun setOrder(ids: List<String>) {
        preferences.edit().putString(ORDER, ids.joinToString(",")).apply()
    }

    companion object {
        const val ORDER = "glance_order"

        /** [sources] in the chosen [order]; any not in it follow in their own order. */
        fun <T : Any> ordered(sources: List<T>, order: List<String>, id: (T) -> String): List<T> {
            if (order.isEmpty()) return sources
            val rank = order.withIndex().associate { it.value to it.index }
            return sources.withIndex().sortedWith(compareBy({ rank[id(it.value)] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
        }
    }
}

/** At a Glance content's choices, as a backup carries them. */
data class GlanceChoices(
    val on: Set<String> = emptySet(),
    val whens: Map<String, String> = emptyMap(),
    val order: List<String> = emptyList(),
    /** Per source id, per option name ([GlanceOptions]); an older backup has none. */
    val options: Map<String, Map<String, String>> = emptyMap(),
)
