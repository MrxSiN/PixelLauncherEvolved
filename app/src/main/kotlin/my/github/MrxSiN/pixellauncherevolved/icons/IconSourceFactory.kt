package my.github.MrxSiN.pixellauncherevolved.icons

import android.content.Context
import android.content.SharedPreferences

import java.util.Calendar

import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Which icons the launcher draws, as stored, and as the hooks read it.
 *
 * Not a switch, so the pack and the generation are not in
 * [my.github.MrxSiN.pixellauncherevolved.catalog.Settings]; the choice of
 * source is one, and lives there. All of it is in the same preference file, in
 * the launcher's own data directory.
 *
 * The generation is what the launcher's own stored icons are keyed by. It is
 * raised whenever anything here changes in a way that should redraw icons, which
 * is what lets a change reach a running launcher through the launcher's own
 * freshness check rather than through a restart.
 */
class IconSourceSettings(private val preferences: SharedPreferences) {

    fun usesPack(): Boolean =
        runCatching { preferences.getBoolean(Settings.ICONS_USE_PACK.key, Settings.ICONS_USE_PACK.default) }
            .getOrDefault(Settings.ICONS_USE_PACK.default)

    fun usePack(value: Boolean) {
        preferences.edit().putBoolean(Settings.ICONS_USE_PACK.key, value).apply()
    }

    /** Null when no pack was ever chosen. */
    fun pack(): String? = runCatching { preferences.getString(KEY_PACK, null) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }

    fun choose(packageName: String?) {
        val editor = preferences.edit()
        if (packageName == null) editor.remove(KEY_PACK) else editor.putString(KEY_PACK, packageName)
        editor.apply()
    }

    fun generation(): Int = runCatching { preferences.getInt(KEY_GENERATION, 0) }.getOrDefault(0)

    /** Raised after every change; the new value keys the launcher's stored icons. */
    fun bump(): Int = (generation() + 1).also { preferences.edit().putInt(KEY_GENERATION, it).apply() }

    private companion object {
        const val KEY_PACK = "home_icons_pack"
        const val KEY_GENERATION = "home_icons_generation"
    }
}

/**
 * Builds the object the icon hooks read, and publishes it.
 *
 * Everything here is a cold path: the launcher starting, a setting changed, a
 * package added or removed. A source is only ever published whole, with its
 * component map, because the launcher stores each icon against the freshness
 * identifier the source gives it: a source published before its map would have
 * stock icons stored under the pack's identifier, and they would never be
 * regenerated.
 */
class IconSourceFactory(
    private val context: Context,
    private val settings: IconSourceSettings,
    private val overrides: IconOverrideStore,
    private val indexer: IconPackIndexer,
    private val logger: Logger,
) {

    /** The pack the stored settings call for, or null for stock behaviour. */
    fun wantedPack(): String? = if (settings.usesPack()) settings.pack() else null

    /**
     * Publishes the source the stored settings call for, compiling the pack's
     * component map when it is not on disk yet.
     *
     * Compiling reads and parses several megabytes, so this runs on a worker.
     *
     * @return the published source, or null for stock behaviour.
     */
    fun publish(): IconSource? = publish(wantedPack()?.let(indexer::indexOf))

    /**
     * Publishes the source with a map already in hand, or with none when
     * [index] is null.
     */
    fun publish(index: IconPackIndex?): IconSource? {
        IconPackState.dayIndex = today()
        val source = build(index)
        IconPackState.source = source
        return source
    }

    /**
     * [pack]'s source as it would be published, for a preview only: nothing is
     * published or stored. Null for a pack that cannot be drawn from.
     */
    fun preview(pack: String): IconSource? = build(pack, indexer.cachedIndexOf(pack) ?: indexer.indexOf(pack))

    private fun build(index: IconPackIndex?): IconSource? = wantedPack()?.let { build(it, index) }

    private fun build(pack: String, index: IconPackIndex?): IconSource? {
        val version = IconPacks.versionOf(context, pack) ?: run {
            logger.warn("Icons: the chosen pack $pack is gone; using system icons")
            return null
        }
        val resources = IconPacks.resourcesOf(context, pack) ?: run {
            logger.warn("Icons: the resources of $pack cannot be opened; using system icons")
            return null
        }

        val compiled = IconOverrideFormat.compile(overrides.overrides(), pack) { name ->
            runCatching { resources.getIdentifier(name, "drawable", pack) }.getOrDefault(0)
        }
        return IconSource(pack, version, settings.generation(), index?.takeIf { it.version == version }, compiled, resources)
    }

    /** The day of the month, 0 based, which is how a pack numbers its calendar icons. */
    fun today(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1
}
