package my.github.MrxSiN.pixellauncherevolved.feature.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle

import java.io.ByteArrayOutputStream
import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * Draws a drawable the way the home screen draws an app icon, as PNG bytes.
 *
 * ```
 * LauncherIcons.Companion.obtain(Context)                        a pooled icon factory
 * BaseIconFactory.createBadgedIconBitmap$default(factory, icon)  normalise, wrap, colour
 * BitmapInfo.newIcon(Context, 0, null)                           the drawable, in the home shape
 * ```
 *
 * The launcher applies its icon shape when it draws, not when it stores, so
 * the stored bitmap is drawn through `newIcon` rather than sent as it is. Flags
 * 0 ask for the full-colour icon, never the themed one.
 *
 * For Wallpaper & style's tiles and lists, on the Binder thread that asked:
 * never on the UI thread, never on an icon or frame path.
 */
internal class LauncherIconRenderer private constructor(
    private val context: Context,
    /** The companion object, or null where R8 made `obtain` static. */
    private val companion: Any?,
    private val obtain: Method,
    private val create: Method,
    private val newIcon: Method,
    private val recycle: Method,
) {

    /** [icon] in the home screen's shape, [size] pixels square, badged for [user]; null if it cannot be drawn. */
    fun png(icon: Drawable, size: Int, user: UserHandle? = null): ByteArray? {
        val factory = obtain.invoke(companion, context) ?: return null
        try {
            val info = create.invoke(null, factory, icon) ?: return null
            val shaped = newIcon.invoke(info, context, 0, null) as? Drawable ?: return null
            // The launcher's icons are hardware bitmaps, which a software canvas
            // refuses; a recorded picture is drawn by the GPU into a readable copy.
            val picture = Picture()
            shaped.setBounds(0, 0, size, size)
            shaped.draw(picture.beginRecording(size, size))
            picture.endRecording()
            val bitmap = Bitmap.createBitmap(picture, size, size, Bitmap.Config.ARGB_8888).let {
                if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
            }

            val badged = user?.let {
                runCatching { context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, bitmap), it) }.getOrNull()
            }
            val out = if (badged != null && badged !is BitmapDrawable) {
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                    badged.setBounds(0, 0, size, size)
                    badged.draw(Canvas(it))
                }
            } else {
                (badged as? BitmapDrawable)?.bitmap ?: bitmap
            }

            return ByteArrayOutputStream(size * size / 4).use { stream ->
                out.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        } finally {
            runCatching { recycle.invoke(factory) }
        }
    }

    companion object {

        fun of(context: Context, classLoader: ClassLoader, logger: Logger): LauncherIconRenderer? = runCatching {
            val icons = Class.forName(LAUNCHER_ICONS, false, classLoader)
            val companionType = Class.forName("$LAUNCHER_ICONS\$Companion", false, classLoader)
            val companion = icons.declaredFields.firstOrNull { it.type == companionType }?.apply { isAccessible = true }?.get(null)
            val info = Class.forName(BITMAP_INFO, false, classLoader)
            val shape = Class.forName(ICON_SHAPE, false, classLoader)
            LauncherIconRenderer(
                context = context,
                companion = companion,
                obtain = requireNotNull(Reflect.method(companionType, "obtain", Context::class.java)),
                create = requireNotNull(
                    Reflect.method(Class.forName(ICON_FACTORY, false, classLoader), CREATE, icons, Drawable::class.java),
                ),
                newIcon = requireNotNull(
                    Reflect.method(info, "newIcon", Context::class.java, Int::class.javaPrimitiveType!!, shape),
                ),
                recycle = requireNotNull(Reflect.method(icons, "recycle")),
            )
        }.onFailure { logger.warn("Icons: the launcher's icon factory is unreachable; Wallpaper & style shows no pictures", it) }
            .getOrNull()

        private const val LAUNCHER_ICONS = "com.android.launcher3.icons.LauncherIcons"
        private const val ICON_FACTORY = "com.android.launcher3.icons.BaseIconFactory"
        private const val BITMAP_INFO = "com.android.launcher3.icons.BitmapInfo"
        private const val ICON_SHAPE = "com.android.launcher3.icons.IconShape"
        private const val CREATE = "createBadgedIconBitmap\$default"
    }
}
