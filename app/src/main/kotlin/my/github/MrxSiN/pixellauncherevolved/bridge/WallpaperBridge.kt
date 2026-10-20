package my.github.MrxSiN.pixellauncherevolved.bridge

import android.app.WallpaperManager
import android.content.ContentProvider
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Point
import android.graphics.Rect
import android.os.Binder
import android.os.Bundle
import android.os.Process

import io.github.libxposed.api.XposedInterface

import java.io.ByteArrayOutputStream

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * How the launcher gets the home wallpaper itself, for its page previews.
 *
 * The launcher may not read the wallpaper (`READ_WALLPAPER_INTERNAL`); it
 * used to photograph the display with its own window hidden, which blanked the
 * home screen, kept home's blur, and showed one page's part of the wallpaper.
 * Wallpaper & style holds that permission, and the launcher already holds the
 * permission of one of its providers (`RecentWallpapersProvider`), so the
 * launcher asks that provider and this module answers on its behalf: the part
 * of the wallpaper file the home screen crops for the display, every page of
 * it, as JPEG bytes, sharp.
 *
 * Only the launcher, the shell and root may ask ([callerAllowed]); nothing of
 * the provider's own is touched.
 */
object WallpaperBridge {

    const val AUTHORITY: String = "com.google.android.apps.wallpaper.recents"
    const val METHOD: String = "ple_home_wallpaper"

    /** The display the crop is for, in pixels. */
    const val WIDTH: String = "width"
    const val HEIGHT: String = "height"

    /** The tallest picture worth sending; it is scaled down to this. */
    const val MAX_HEIGHT: String = "max_height"

    /** JPEG bytes of the display's crop of the wallpaper; absent for a live wallpaper. */
    const val IMAGE: String = "image"

    private const val PROVIDER = "com.google.android.apps.wallpaper.provider.RecentWallpapersProvider"
    private const val QUALITY = 90

    /** Answers [METHOD] on Wallpaper & style's recent wallpapers provider. */
    fun install(xposed: XposedInterface, classLoader: ClassLoader, logger: Logger) {
        val call = runCatching {
            Class.forName(PROVIDER, false, classLoader).getMethod(
                "call",
                String::class.java,
                String::class.java,
                String::class.java,
                Bundle::class.java,
            )
        }.getOrElse { return logger.warn("Picker: no recent wallpapers provider; Modes pages show no wallpaper", it) }

        xposed.hook(call).intercept { chain ->
            if (chain.getArg(1) != METHOD) return@intercept chain.proceed()
            val provider = chain.thisObject as? ContentProvider ?: return@intercept chain.proceed()
            if (provider.javaClass.name != PROVIDER) return@intercept chain.proceed()
            val context = provider.context ?: return@intercept null
            if (!callerAllowed(context)) return@intercept null
            runCatching { answer(context, chain.getArg(3) as? Bundle ?: Bundle.EMPTY) }
                .onFailure { logger.warn("Picker: the home wallpaper could not be read", it) }
                .getOrNull()
        }
    }

    private fun callerAllowed(context: Context): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.SHELL_UID || uid == Process.ROOT_UID) return true
        return context.packageManager.getPackagesForUid(uid)?.contains(Bridge.LAUNCHER_PACKAGE) == true
    }

    private fun answer(context: Context, request: Bundle): Bundle? {
        val manager = WallpaperManager.getInstance(context)
        // Null for a live wallpaper: it has no file.
        val file = manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM) ?: return null
        val bitmap = file.use { descriptor ->
            val decoder = BitmapRegionDecoder.newInstance(descriptor)
            try {
                val whole = Rect(0, 0, decoder.width, decoder.height)
                val crop = crop(manager, request.getInt(WIDTH), request.getInt(HEIGHT))
                    ?.takeIf { !it.isEmpty && whole.contains(it) } ?: whole
                val maxHeight = request.getInt(MAX_HEIGHT).takeIf { it > 0 } ?: crop.height()
                var sample = 1
                while (crop.height() / (sample * 2) >= maxHeight) sample *= 2
                decoder.decodeRegion(crop, BitmapFactory.Options().apply { inSampleSize = sample })
            } finally {
                decoder.recycle()
            }
        } ?: return null

        val maxHeight = request.getInt(MAX_HEIGHT)
        val sent = if (maxHeight in 1 until bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * maxHeight.toFloat() / bitmap.height).roundToInt(), maxHeight, true)
        } else {
            bitmap
        }
        val bytes = ByteArrayOutputStream().use { out ->
            sent.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        if (sent !== bitmap) sent.recycle()
        bitmap.recycle()
        return Bundle().apply { putByteArray(IMAGE, bytes) }
    }

    /**
     * The part of the wallpaper file the display shows across all its pages:
     * `WallpaperManager.getBitmapCrops(List<Point>, int, boolean)`, hidden, in
     * the file's own pixels. Null where it is missing, and the whole file is sent.
     */
    private fun crop(manager: WallpaperManager, width: Int, height: Int): Rect? {
        if (width <= 0 || height <= 0) return null
        return runCatching {
            val method = WallpaperManager::class.java.getMethod(
                "getBitmapCrops",
                List::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            (method.invoke(manager, listOf(Point(width, height)), WallpaperManager.FLAG_SYSTEM, false) as? List<*>)
                ?.firstOrNull() as? Rect
        }.getOrNull()
    }
}
