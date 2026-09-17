package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Keeps page snapshots for as long as the pages exist, not the process.
 *
 * A page given to a Mode is filtered out of the workspace binding, so it has no
 * view and no snapshot can be taken of it. Carrying the last one forward in
 * memory covers the launcher session it was assigned in; a launcher that starts
 * with the page already assigned has never seen it and would have nothing to
 * show but the drawn-from-model fallback. So the snapshots are written down.
 *
 * They are written rarely: only when the set of pages the workspace holds
 * changes, which is the moment an assignment is made or a Mode turns on or off.
 * A page whose snapshot is not being kept has its file removed with it.
 *
 * The reading and writing happen on [io]; the model itself is only ever changed
 * on the main thread, where the captures change it too, so neither can land
 * half of the other's map.
 */
internal class FocusPageSnapshotStore(private val context: Context) {

    private val dir = File(context.filesDir, DIRECTORY)
    private val io: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ple-focus-previews").apply { isDaemon = true }
    }

    /** Reads what was kept. [onRestored] runs on the main thread, once, if any. */
    fun restore(onRestored: (Map<Int, Bitmap>, Bitmap?) -> Unit) {
        io.execute {
            val stored = runCatching { read() }.getOrDefault(emptyMap())
            val wallpaper = runCatching { decode(File(dir, WALLPAPER)) }.getOrNull()
            if (stored.isEmpty() && wallpaper == null) return@execute
            context.mainExecutor.execute { onRestored(stored, wallpaper) }
        }
    }

    /**
     * Keeps the wallpaper as well as the pages.
     *
     * Reading it off the display waits for the launcher to have been in front
     * long enough that a notification cannot be on it, so on a launcher start
     * it is not there yet — and a page drawn from the model in the meantime had
     * no wallpaper behind it at all. Once read, it is kept, and every later
     * start has one from the beginning.
     */
    fun saveWallpaper(bitmap: Bitmap) {
        val encodable = runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return
        io.execute {
            runCatching {
                if (!dir.isDirectory && !dir.mkdirs()) return@runCatching
                val file = File(dir, WALLPAPER)
                val written = runCatching {
                    file.outputStream().use { encodable.compress(FORMAT, QUALITY, it) }
                }.getOrDefault(false)
                if (!written) file.delete()
            }
        }
    }

    /**
     * Renames kept pictures to the ids their pages were given.
     *
     * Through a temporary name first, because a mapping is a permutation and
     * one page's new name is another page's old one.
     */
    fun renumber(mapping: Map<Int, Int>) {
        if (mapping.isEmpty()) return
        io.execute {
            runCatching {
                val moving = mapping.keys.mapNotNull { old ->
                    File(dir, "$old$EXTENSION").takeIf(File::isFile)?.let { file ->
                        val parked = File(dir, "$old$EXTENSION$PARKED")
                        if (file.renameTo(parked)) old to parked else null
                    }
                }
                for ((old, parked) in moving) parked.renameTo(File(dir, "${mapping.getValue(old)}$EXTENSION"))
            }
        }
    }

    private fun decode(file: File): Bitmap? {
        if (!file.isFile) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
        return BitmapFactory.decodeFile(file.path, options)
    }

    /**
     * Writes [snapshots], and drops the files of pages the launcher no longer has.
     *
     * A file is dropped for its page being gone from [pages], never for being
     * missing from [snapshots]. The first capture of a launcher start can run
     * before the pictures on disk have been read back, and holds only the pages
     * on show; dropping whatever it lacked deleted the picture of every page a
     * Mode hides — the very pages that cannot be photographed again — and left
     * the Focus pages dialog drawing them from the model instead. An empty
     * [pages] is a launcher that has not bound yet, and drops nothing.
     */
    fun save(snapshots: Map<Int, Bitmap>, pages: Set<Int>) {
        // Copied off the hardware buffer here, on the thread that already has
        // the map, so the writing thread is handed something it can encode.
        val encodable = snapshots.mapNotNull { (screenId, bitmap) ->
            runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                ?.let { screenId to it }
        }
        io.execute { runCatching { write(encodable.toMap(), pages) } }
    }

    private fun read(): Map<Int, Bitmap> {
        val files = dir.listFiles().orEmpty()
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
        return buildMap {
            for (file in files) {
                val screenId = file.name.removeSuffix(EXTENSION).toIntOrNull() ?: continue
                val bitmap = runCatching {
                    BitmapFactory.decodeFile(file.path, options)
                }.getOrNull() ?: continue
                put(screenId, bitmap)
            }
        }
    }

    private fun write(snapshots: Map<Int, Bitmap>, pages: Set<Int>) {
        if (!dir.isDirectory && !dir.mkdirs()) return

        for ((screenId, bitmap) in snapshots) {
            val file = File(dir, "$screenId$EXTENSION")
            val written = runCatching {
                file.outputStream().use { bitmap.compress(FORMAT, QUALITY, it) }
            }.getOrDefault(false)
            if (!written) file.delete()
        }

        for (file in dir.listFiles().orEmpty()) {
            if (file.name == WALLPAPER) continue
            val screenId = file.name.removeSuffix(EXTENSION).toIntOrNull()
            if (screenId == null || (pages.isNotEmpty() && screenId !in pages)) file.delete()
        }
    }

    private companion object {
        const val DIRECTORY = "focus-page-previews"
        const val EXTENSION = ".webp"
        const val WALLPAPER = "wallpaper.webp"
        const val PARKED = ".renumbering"
        const val QUALITY = 80
        val FORMAT = Bitmap.CompressFormat.WEBP_LOSSY
    }
}
