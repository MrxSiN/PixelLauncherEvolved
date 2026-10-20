package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display

import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

import my.github.MrxSiN.pixellauncherevolved.bridge.WallpaperBridge
import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Keeps the home wallpaper, as the display crops it, for as long as it is the one set.
 *
 * The launcher may not read the wallpaper, so Wallpaper & style is asked for
 * it ([WallpaperBridge]); that costs Wallpaper & style a start, so the answer
 * is written down, named after the wallpaper it is of ([keyOf]), and a later
 * launcher start uses it while that wallpaper is still set. Pictures from
 * earlier versions — photographs of the display, some with home's blur baked
 * in — are deleted on start.
 */
internal class FocusWallpaperStore(private val context: Context, private val logger: Logger) {

    private val dir = File(context.filesDir, DIRECTORY)
    private val io: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ple-focus-previews").apply { isDaemon = true }
    }

    /**
     * Reads which wallpaper is set and its picture: the one kept, or one asked
     * for. [onLoaded] runs on the main thread, once, with a null picture for a
     * live wallpaper or when Wallpaper & style cannot answer.
     */
    fun load(onLoaded: (key: Int, wallpaper: Bitmap?) -> Unit) {
        io.execute {
            val key = runCatching {
                keyOf(WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM))
            }.getOrDefault(NO_KEY)
            val kept = File(dir, name(key))
            for (file in dir.listFiles().orEmpty()) if (file != kept) file.delete()
            val wallpaper = runCatching { decode(kept) }.getOrNull()
                ?: runCatching { fetch(kept) }.onFailure { logger.warn("Modes pages: the home wallpaper is unavailable", it) }.getOrNull()
            context.mainExecutor.execute { onLoaded(key, wallpaper) }
        }
    }

    /** Asks Wallpaper & style for the wallpaper, and writes its answer to [file]. */
    private fun fetch(file: File): Bitmap? {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.getRealMetrics(metrics)
        val request = Bundle().apply {
            putInt(WallpaperBridge.WIDTH, metrics.widthPixels)
            putInt(WallpaperBridge.HEIGHT, metrics.heightPixels)
            putInt(WallpaperBridge.MAX_HEIGHT, MAX_HEIGHT_PX)
        }
        val answer = context.contentResolver.call(
            Uri.parse("content://${WallpaperBridge.AUTHORITY}"),
            WallpaperBridge.METHOD,
            null,
            request,
        ) ?: return null
        val bytes = answer.getByteArray(WallpaperBridge.IMAGE) ?: return null
        if (dir.isDirectory || dir.mkdirs()) {
            runCatching { file.writeBytes(bytes) }.onFailure { file.delete() }
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, OPTIONS)
    }

    private fun decode(file: File): Bitmap? = if (file.isFile) BitmapFactory.decodeFile(file.path, OPTIONS) else null

    private fun name(key: Int) = "$PREFIX${Integer.toHexString(key)}$EXTENSION"

    private companion object {
        /** No wallpaper could be told apart; nothing kept under it is ever used. */
        const val NO_KEY = 0
        const val DIRECTORY = "focus-page-previews"
        const val PREFIX = "wallpaper-"
        const val EXTENSION = ".jpg"

        /** Taller than any page picture is drawn ([FocusPagePreviewSource]), so nothing is upscaled. */
        const val MAX_HEIGHT_PX = 1_400
        val OPTIONS = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
    }
}
