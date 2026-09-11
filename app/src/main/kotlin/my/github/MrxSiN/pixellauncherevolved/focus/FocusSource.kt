package my.github.MrxSiN.pixellauncherevolved.focus

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.Settings

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/** Where the launcher learns which Modes exist and which are on. */
interface FocusSource {

    /** One coherent read, avoiding duplicate work for IPC or root-backed sources. */
    fun snapshot(): FocusSnapshot = FocusSnapshot(isReadable(), modes())

    /**
     * Every mode on the device, each saying whether it is on now.
     *
     * Empty when the modes cannot be read at all, which is not the same as no
     * mode being on: see [isReadable].
     */
    fun modes(): List<FocusMode>

    /** Whether this source can read modes at all, so a failure can be explained. */
    fun isReadable(): Boolean
}

/**
 * The modes as this module's own app reports them.
 *
 * Reading Modes needs Do Not Disturb access, which the launcher does not have
 * and cannot be given without changing what a Google app is allowed to do. This
 * module's own app can hold it, granted once by the person in Settings, so the
 * reading happens there and the launcher asks it through a content provider.
 * The launcher holds `QUERY_ALL_PACKAGES`, so it can see that provider.
 *
 * Every call starts this module's app if it is not running. That is why nothing
 * here polls: [FocusWatcher] decides when to ask.
 */
class ProviderFocusSource(
    private val resolver: ContentResolver,
    private val modulePackage: String,
    private val logger: Logger,
) : FocusSource {

    override fun snapshot(): FocusSnapshot = query(FocusContract.snapshot(modulePackage)) { cursor ->
        val id = cursor.getColumnIndexOrThrow(FocusContract.COLUMN_ID)
        val name = cursor.getColumnIndexOrThrow(FocusContract.COLUMN_NAME)
        val active = cursor.getColumnIndexOrThrow(FocusContract.COLUMN_ACTIVE)
        val granted = cursor.getColumnIndexOrThrow(FocusContract.COLUMN_GRANTED)
        var readable = false
        val modes = buildList {
            while (cursor.moveToNext()) {
                readable = readable || cursor.getInt(granted) != 0
                val modeId = cursor.getString(id) ?: continue
                add(
                    FocusMode(
                        id = modeId,
                        name = cursor.getString(name),
                        isActive = cursor.getInt(active) != 0,
                    ),
                )
            }
        }
        FocusSnapshot(readable, modes)
    } ?: FocusSnapshot(isReadable = false, modes = emptyList())

    override fun modes(): List<FocusMode> = query(FocusContract.modes(modulePackage)) { cursor ->
        val id = cursor.getColumnIndexOrThrow(FocusContract.COLUMN_ID)
        val name = cursor.getColumnIndexOrThrow(FocusContract.COLUMN_NAME)
        val active = cursor.getColumnIndexOrThrow(FocusContract.COLUMN_ACTIVE)

        buildList {
            while (cursor.moveToNext()) {
                add(
                    FocusMode(
                        id = cursor.getString(id),
                        name = cursor.getString(name),
                        isActive = cursor.getInt(active) != 0,
                    ),
                )
            }
        }
    }.orEmpty()

    override fun isReadable(): Boolean = query(FocusContract.access(modulePackage)) { cursor ->
        cursor.moveToFirst() &&
            cursor.getInt(cursor.getColumnIndexOrThrow(FocusContract.COLUMN_GRANTED)) != 0
    } == true

    private fun <T> query(uri: Uri, read: (android.database.Cursor) -> T): T? = runCatching {
        resolver.query(uri, null, null, null, null)?.use(read)
    }.onFailure { logger.warn("Modes could not be read from this module's app", it) }.getOrNull()
}

data class FocusSnapshot(
    val isReadable: Boolean,
    val modes: List<FocusMode>,
)

/**
 * Tells the launcher when to look at the modes again.
 *
 * Nothing pushes a mode change to a process that does not own the rule, so the
 * launcher has to notice for itself.
 *
 * Two settings are watched, and neither of them is a timer. `zen_mode` is the
 * interruption filter, which catches every Mode that silences the phone. On its
 * own it is not enough, and the Modes that prove it are the ones people notice
 * most: Driving and Transit come on without touching Do Not Disturb, so the
 * filter never moves and an observer on it never reports. Those used to be
 * caught only when the launcher next came to the front, which is why a Mode
 * switched by hand applied at once and a Mode that switched itself appeared to
 * hang until the home screen was touched.
 *
 * `zen_mode_config_etag` is what closes that gap. The notification service
 * rewrites it whenever the zen configuration changes at all, and a rule turning
 * itself on is such a change whether or not it filters anything, so every
 * automatic Mode reports through it.
 *
 * Coming back to the front is still watched elsewhere, as the backstop for a
 * Mode that somehow moved while nothing was listening.
 */
class FocusWatcher(
    private val context: Context,
    private val onChanged: () -> Unit,
) {

    private var observer: ContentObserver? = null

    /** Kept for as long as the feature is: an observer reports only while referenced. */
    fun start(handler: android.os.Handler) {
        if (observer != null) return

        val watcher = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = onChanged()
        }
        observer = watcher
        for (setting in WATCHED) {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(setting),
                false,
                watcher,
            )
        }
    }

    private companion object {

        /**
         * The platform's own names for these are not public API, so they are
         * written out. Watching a setting that does not exist costs an observer
         * that never reports, which is the same as not watching it.
         */
        const val ZEN_MODE = "zen_mode"
        const val ZEN_MODE_CONFIG_ETAG = "zen_mode_config_etag"

        val WATCHED = listOf(ZEN_MODE, ZEN_MODE_CONFIG_ETAG)
    }
}
