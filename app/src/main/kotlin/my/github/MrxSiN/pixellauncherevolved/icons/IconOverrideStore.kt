package my.github.MrxSiN.pixellauncherevolved.icons

import android.content.SharedPreferences

/**
 * One app's icon, chosen by hand.
 *
 * @property drawable the pack drawable's name, or null for the stock icon. A
 * component with no override at all is absent from the set rather than carrying
 * one of these, because absent means "whatever the pack says" and that is not
 * the same answer as either of these two.
 * @property pack the pack [drawable] belongs to. A drawable chosen from one pack
 * applies only while that pack is active: another pack may ship a drawable of
 * the same name that is different artwork. Null with a null [drawable].
 */
data class IconOverride(
    val packageName: String,
    val className: String,
    val userId: Int,
    val drawable: String?,
    val pack: String? = null,
)

/**
 * The overrides as one line of text, and as the arrays a lookup uses.
 *
 * Kept free of Android so the format can be read and tested on a desk. Names
 * rather than resource ids are stored, because a resource id belongs to one
 * build of one pack: a pack update renumbers them, and a name survives it.
 *
 * ```
 * 0|com.example/com.example.Main=com.example.pack:example_icon;10|com.example/com.example.Main=
 * ```
 *
 * An entry with nothing after the `=` is a deliberate stock icon; otherwise the
 * value is the pack and the drawable name, which a resource name cannot contain
 * a `:` in. Entries are
 * written sorted, so the same set is always the same text and a store can tell
 * "unchanged" from "changed" by comparing strings.
 */
object IconOverrideFormat {

    private const val ENTRY = ';'
    private const val USER = '|'
    private const val VALUE = '='
    private const val COMPONENT = '/'
    private const val PACK = ':'

    fun write(overrides: Collection<IconOverride>): String = overrides
        .map { "${it.userId}$USER${it.packageName}$COMPONENT${it.className}$VALUE${valueOf(it)}" }
        .sorted()
        .joinToString(ENTRY.toString())

    private fun valueOf(override: IconOverride): String =
        if (override.drawable == null) "" else "${override.pack.orEmpty()}$PACK${override.drawable}"

    /** Entries this version cannot read are skipped; the rest still load. */
    fun read(text: String): List<IconOverride> {
        if (text.isBlank()) return emptyList()

        val parsed = ArrayList<IconOverride>()
        for (entry in text.split(ENTRY)) {
            if (entry.isBlank()) continue

            val user = entry.indexOf(USER)
            val value = entry.indexOf(VALUE, startIndex = user + 1)
            if (user <= 0 || value < 0) continue

            val userId = entry.substring(0, user).toIntOrNull() ?: continue
            val component = entry.substring(user + 1, value)
            val slash = component.indexOf(COMPONENT)
            if (slash <= 0 || slash == component.length - 1) continue

            val chosen = entry.substring(value + 1)
            val pack = chosen.indexOf(PACK)
            // A drawable needs the pack it came from; one without is unusable.
            if (chosen.isNotEmpty() && pack <= 0) continue

            parsed += IconOverride(
                packageName = component.substring(0, slash),
                className = component.substring(slash + 1),
                userId = userId,
                drawable = if (chosen.isEmpty()) null else chosen.substring(pack + 1).takeIf { it.isNotBlank() },
                pack = if (chosen.isEmpty()) null else chosen.substring(0, pack),
            )
        }
        return parsed
    }

    /**
     * The arrays a lookup uses for [activePack], with every drawable name
     * resolved through [resolve].
     *
     * A drawable chosen from another pack is left out, so that app follows the
     * active pack; it applies again when its own pack is chosen again.
     *
     * An override naming a drawable the active pack no longer has is dropped
     * rather than kept as a hole: dropped, the app falls back to the pack's own
     * answer and then to stock, which is an icon either way.
     */
    fun compile(overrides: Collection<IconOverride>, activePack: String, resolve: (String) -> Int): IconOverrides {
        if (overrides.isEmpty()) return IconOverrides.NONE

        val keyed = KeyedIds()
        val digests = HashMap<String, Int>()

        for (override in overrides) {
            if (override.drawable != null && override.pack != activePack) continue

            val id = override.drawable?.let(resolve)?.takeIf { it != 0 } ?: IconOverrides.SYSTEM
            if (override.drawable != null && id == IconOverrides.SYSTEM) continue

            val key = ComponentHash.ofUser(override.packageName, override.className, override.userId)
            keyed.put(key, id)
            // Order-independent, so the digest of a set does not depend on how
            // the set was read.
            digests[override.packageName] = (digests[override.packageName] ?: 0) xor (key.hashCode() * 31 + id)
        }
        keyed.collisions()

        val perPackage = KeyedIds()
        for ((name, digest) in digests) {
            perPackage.put(ComponentHash.ofPackage(name), if (digest == 0) 1 else digest)
        }
        perPackage.collisions()
        if (keyed.size == 0) return IconOverrides.NONE

        return IconOverrides(keyed.keys(), keyed.ids(), perPackage.keys(), perPackage.ids())
    }
}

/**
 * Which apps carry an icon of their own.
 *
 * Not a switch, so it is not in
 * [my.github.MrxSiN.pixellauncherevolved.catalog.Settings]. It lives in the same
 * preference file, in the launcher's own data directory, because the launcher is
 * the only process that reads or writes it.
 */
class IconOverrideStore(
    private val preferences: SharedPreferences,
) {

    fun raw(): String =
        runCatching { preferences.getString(KEY, "") }.getOrNull().orEmpty()

    fun overrides(): List<IconOverride> = IconOverrideFormat.read(raw())

    fun replace(overrides: Collection<IconOverride>) {
        val editor = preferences.edit()
        if (overrides.isEmpty()) {
            editor.remove(KEY)
        } else {
            editor.putString(KEY, IconOverrideFormat.write(overrides))
        }
        editor.apply()
    }

    /** Removes every override, returning this feature to the pack's own answers. */
    fun clear() = replace(emptyList())

    private companion object {
        const val KEY = "home_icons_overrides"
    }
}
