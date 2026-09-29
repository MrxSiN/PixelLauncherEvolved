package my.github.MrxSiN.pixellauncherevolved.picker

import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.RoundedCorner
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

import java.util.concurrent.Executors

import my.github.MrxSiN.pixellauncherevolved.bridge.IconsBridge
import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * The home screen, as the launcher itself draws it, for the icon pack page.
 *
 * The launcher is asked through `grid_control` for a picture of its home screen
 * (`get_preview_bitmap`, at this view's size) drawn with the icons of the pack
 * being looked at ([show]), which need not be the chosen one
 * ([IconsBridge.PREVIEW_PACK]). It sits over the home wallpaper, blurred as home
 * blurs it ([blur]), and shaped like the picker's own card: the display's
 * corners, shrunk with it.
 *
 * A picture rather than the live surface (`get_preview`) the picker's main page
 * embeds: a `SurfaceView` is a layer of its own that does not follow the page's
 * slide, scale and fade. A picture is an ordinary view and moves with the page.
 *
 * Pictures are kept per pack for the picker's session ([pictures]), so going
 * back to a pack, or opening the page again, shows it at once; [prefetch] draws
 * the chosen pack's before the page is opened. A new picture crossfades over the
 * old one, so a change of pack is seen rather than cut.
 */
internal class LauncherPreview(context: Context, private val logger: Logger) : FrameLayout(context) {

    private val main = Handler(Looper.getMainLooper())
    private val wallpaper = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private var shown = picture()
    private var incoming = picture()
    private var wanted: String? = null
    private var blurRadius = 0

    /** The part of the wallpaper home shows, as the picker's own card crops it; null to centre it. */
    private val crop: Rect? = homeCrop(context)

    init {
        addView(wallpaper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(shown, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(incoming, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, corner(view.height))
            }
        }
        runCatching { WallpaperManager.getInstance(context).getDrawable(WallpaperManager.FLAG_SYSTEM) }
            .onFailure { logger.warn("Picker: the home wallpaper cannot be read for the icon preview", it) }
            .getOrNull()?.let(wallpaper::setImageDrawable)
    }

    private fun picture() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        alpha = 0f
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** The display's own corner radius, shrunk to a preview [height] pixels tall, as the picker shapes its card. */
    private fun corner(height: Int): Float {
        val screen = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val radius = display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius
            ?: PickerPage.dp(context, FALLBACK_CORNER_DP)
        return radius.toFloat() * height / screen
    }

    /**
     * Shows the wallpaper as home's first page, and the picker's card, show it.
     *
     * The crop is wider than the screen when the wallpaper scrolls with the
     * pages; the first page is its leading edge, one screen wide at the crop's
     * height (the picker's `FullResImageViewUtil`). The system then draws the
     * wallpaper zoomed in by `config_wallpaperMaxScale` about the screen's centre,
     * easing back only while something zooms it out, as the picker's card shows.
     */
    private fun placeWallpaper() {
        val all = crop ?: return
        if (wallpaper.drawable == null || all.isEmpty) return
        val screen = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val pageWidth = all.height().toFloat() * screen.width() / screen.height()
        val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) all.right - pageWidth else all.left.toFloat()
        val scale = maxOf(width / pageWidth, height.toFloat() / all.height()) * maxZoom
        wallpaper.scaleType = ImageView.ScaleType.MATRIX
        wallpaper.imageMatrix = Matrix().apply {
            setTranslate(-(left + pageWidth / 2f), -all.exactCenterY())
            postScale(scale, scale)
            postTranslate(width / 2f, height / 2f)
        }
    }

    /** The system's resting wallpaper zoom, `config_wallpaperMaxScale`; 1 where the platform has none. */
    private val maxZoom: Float by lazy {
        runCatching {
            val system = android.content.res.Resources.getSystem()
            system.getFloat(system.getIdentifier("config_wallpaperMaxScale", "dimen", "android"))
        }.getOrDefault(1f)
    }

    /** Blurs the wallpaper as home rests it, [fullRadius] being the radius on the whole screen; 0 for none. */
    fun blur(fullRadius: Int) {
        blurRadius = fullRadius
        applyBlur()
    }

    private fun applyBlur() {
        val screen = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val radius = blurRadius.toFloat() * width / screen
        wallpaper.setRenderEffect(if (radius > 0f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null)
    }

    /**
     * Shows the home screen drawn with [pack]'s icons, empty for System: at once
     * when it was drawn before, else as soon as the launcher has drawn it.
     */
    fun show(pack: String) {
        wanted = pack
        if (width == 0 || height == 0) return
        val known = pictures.get(key(pack, width, height))
        if (known != null && shown.drawable == null) {
            // Drawn before: in place from the first frame, as the card it grows out of shows it.
            shown.setImageBitmap(known)
            shown.alpha = 1f
            return
        }
        if (known != null) {
            crossfade(known)
            return
        }
        render(context, pack, width, height, logger) { image ->
            if (wanted == pack && image != null) crossfade(image)
        }
    }

    private fun crossfade(image: Bitmap) {
        if ((shown.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap === image && shown.alpha == 1f) return
        incoming.animate().cancel()
        incoming.setImageBitmap(image)
        val first = shown.drawable == null
        incoming.alpha = 0f
        incoming.animate()
            .alpha(1f)
            .setDuration(if (first) FIRST_MS else CROSSFADE_MS)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .withEndAction {
                // The new picture becomes the one shown; the old one is let go.
                shown.setImageDrawable(null)
                shown.alpha = 0f
                val previous = shown
                shown = incoming
                incoming = previous
                bringChildToFront(incoming)
            }
            .start()
        if (!ValueAnimator.areAnimatorsEnabled()) incoming.alpha = 1f
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return
        applyBlur()
        placeWallpaper()
        invalidateOutline()
        wanted?.let(::show)
    }

    companion object {
        private const val GET_BITMAP = "get_preview_bitmap"
        private const val IMAGE = "image"
        private const val FALLBACK_CORNER_DP = 28f
        private const val FIRST_MS = 200L
        private const val CROSSFADE_MS = 450L
        private const val KEPT = 4

        /** The preview's share of the screen's height; its width follows the screen's shape. */
        private const val SHARE = 0.5f

        private val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

        private val renderer = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ple-picker-preview").apply { isDaemon = true }
        }

        /** Pictures drawn this session, by pack and size. Touched on the main thread only. */
        private val pictures = LruCache<String, Bitmap>(KEPT)

        /**
         * Where the home wallpaper is cropped for this screen, in the picture
         * [WallpaperManager.getDrawable] hands back: `getBitmapCrops`, which the
         * picker positions its own card with. Hidden API the picker itself calls.
         */
        private fun homeCrop(context: Context): Rect? = runCatching {
            val screen = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val crops = WallpaperManager::class.java
                .getMethod("getBitmapCrops", List::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .invoke(WallpaperManager.getInstance(context), listOf(Point(screen.width(), screen.height())), WallpaperManager.FLAG_SYSTEM, false)
            (crops as? List<*>)?.firstOrNull() as? Rect
        }.getOrNull()

        private fun key(pack: String, width: Int, height: Int) = "$pack|${width}x$height"

        /** The preview's size on the page: half the screen's height, shaped like the screen. */
        fun size(context: Context): IntArray {
            val metrics = context.resources.displayMetrics
            val height = (metrics.heightPixels * SHARE).toInt()
            return intArrayOf(height * metrics.widthPixels / metrics.heightPixels, height)
        }

        /** Draws [pack]'s home screen ahead of the page opening, so the page shows it at once. */
        fun prefetch(context: Context, pack: String, logger: Logger) {
            val (width, height) = size(context)
            if (pictures.get(key(pack, width, height)) == null) render(context, pack, width, height, logger) {}
        }

        /** Every picture drawn so far is stale: the icons the launcher draws have changed. */
        fun forget() = pictures.evictAll()

        private fun render(context: Context, pack: String, width: Int, height: Int, logger: Logger, done: (Bitmap?) -> Unit) {
            val main = Handler(Looper.getMainLooper())
            val request = Bundle().apply {
                putBinder("host_token", Binder())
                putInt("display_id", context.display?.displayId ?: 0)
                putInt("width", width)
                putInt("height", height)
                putString(IconsBridge.PREVIEW_PACK, pack)
            }
            renderer.execute {
                val answer = runCatching {
                    context.contentResolver.call(Uri.parse("content://${IconsBridge.AUTHORITY}/preview"), GET_BITMAP, null, request)
                }.onFailure { logger.warn("Picker: the launcher's home preview is unavailable", it) }.getOrNull()
                @Suppress("DEPRECATION")
                val image = answer?.getParcelable<Bitmap>(IMAGE)
                main.post {
                    if (image != null) pictures.put(key(pack, width, height), image)
                    done(image)
                }
            }
        }
    }
}
