package my.github.MrxSiN.pixellauncherevolved.picker

import android.app.AlertDialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.bridge.IconsBridge
import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Wallpaper & style → Home screen → Icon pack.
 *
 * Laid out as the picker's own style pages are: the home screen on top, drawn
 * by the launcher itself ([LauncherPreview]) and grown out of the picker's home
 * card as the page opens, and the choices under it as the picker's option
 * tiles, System first and set apart as Theme pack sets apart No theme, then each
 * installed pack with its own icon and name. Choosing a tile previews it: the
 * launcher draws the home screen with that pack's icons without choosing it.
 * The toolbar's Apply, as on the picker's own option pages, makes it the home
 * screen's. The applied pack's per-app icons and their reset follow, as the
 * picker's option entries.
 *
 * Everything the page knows comes from the launcher ([IconsClient]); nothing
 * is stored here.
 */
internal class IconPackPage(
    private val context: Context,
    private val text: Resources,
    private val colors: PickerColors,
    private val client: IconsClient,
    private val logger: Logger,
    private val onClosed: () -> Unit,
    /**
     * What the launcher last said ([IconsBridge.STATE]), so the page opens complete
     * (preview, blur and tiles) and its options can rise into place with it.
     */
    private val known: Bundle? = null,
) {

    private val views = PickerViews(colors)
    private val preview = LauncherPreview(context, logger)
    private val tiles = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val message = TextView(context).apply {
        visibility = View.GONE
        setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
        gravity = Gravity.CENTER
        colors.paint(this, "colorOnSurfaceVariant", ::setTextColor)
    }
    private val group = views.group(context)
    private var perApp: PickerViews.Entry? = null
    private var reset: PickerViews.Entry? = null
    private val shown = LinkedHashMap<String, PickerViews.Tile>()

    /** The pack the home screen draws from, and the one being looked at; null until the launcher says. */
    private var applied: String? = null
    private var chosen: String? = null
    private var busy = false
    private var overrides = 0
    private val strip = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
    }
    private lateinit var page: PickerPage

    fun show() {
        val padding = runCatching {
            context.resources.getDimensionPixelSize(PickerPage.resource(context, "dimen", "customization_option_container_horizontal_padding"))
        }.getOrDefault(PickerPage.dp(context, 16f))

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, PickerPage.dp(context, 24f))
            addView(previewFrame(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(
                capsule(),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = PickerPage.dp(context, 24f)
                },
            )
            addView(message, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = PickerPage.dp(context, 16f)
            })
            addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = PickerPage.dp(context, 24f)
            })
        }
        addEntries()

        page = PickerPage(
            context,
            colors,
            text.getString(R.string.feature_icon_pack_title),
            ScrollView(context).apply { addView(column) },
            onClosed,
            hero = preview,
            heroFrom = ::homeCard,
            rising = listOf(strip.parent as View, message, group),
        )
        page.offerApply(::apply)
        known?.let(::take)
        page.show()
        load()
    }

    /** Where the picker's own home screen card is on screen, for the preview to grow out of; null when out of sight. */
    private fun homeCard(): Rect? {
        val activity = PickerPage.activityOf(context) ?: return null
        val card = activity.findViewById<View>(PickerPage.resource(context, "id", HOME_CARD)) ?: return null
        if (!card.isShown || card.width == 0) return null
        val at = IntArray(2)
        card.getLocationOnScreen(at)
        val bounds = Rect(at[0], at[1], at[0] + card.width, at[1] + card.height)
        val screen = Rect(0, 0, card.rootView.width, card.rootView.height)
        return bounds.takeIf { screen.contains(it) }
    }

    /**
     * The pack tiles in the picker's own option sheet capsule
     * (`floating_sheet_content_background`, 28dp corners in surface-bright), padded
     * as its Icons and Layout pages pad theirs; the tiles scroll inside it.
     */
    private fun capsule(): View {
        val inset = PickerPage.dp(context, SHEET_PADDING_DP)
        val gap = PickerPage.dp(context, TILE_GAP_DP)
        tiles.setPadding(inset, 0, inset - gap, 0)
        strip.addView(tiles)
        return FrameLayout(context).apply {
            background = runCatching { context.getDrawable(PickerPage.resource(context, "drawable", SHEET_BACKGROUND)) }.getOrNull()
                ?: android.graphics.drawable.GradientDrawable().apply { cornerRadius = PickerPage.dp(context, SHEET_CORNER_DP).toFloat() }
            colors.paint(this, "colorSurfaceBright") { background?.mutate()?.setTint(it) }
            clipToOutline = true
            setPadding(0, inset, 0, inset)
            addView(strip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** The preview, as tall as half the screen and shaped like it, centred. */
    private fun previewFrame(): View {
        val (width, height) = LauncherPreview.size(context)
        return FrameLayout(context).apply {
            setPadding(0, PickerPage.dp(context, 8f), 0, 0)
            addView(preview, FrameLayout.LayoutParams(width, height, Gravity.CENTER_HORIZONTAL))
        }
    }

    private fun addEntries() {
        perApp = views.entry(group, PLAIN_ENTRY, text.getString(R.string.feature_icon_per_app_title), null)?.also { entry ->
            group.addView(entry.view)
            entry.view.setOnClickListener {
                if (!entry.view.isEnabled) return@setOnClickListener
                PerAppIconsPage(context, text, colors, client, logger) {
                    LauncherPreview.forget()
                    load()
                }.show()
            }
        }
        reset = views.entry(
            group,
            PLAIN_ENTRY,
            text.getString(R.string.feature_icon_reset_title),
            text.getString(R.string.feature_icon_reset_summary),
        )?.also { entry ->
            group.addView(entry.view)
            entry.view.setOnClickListener { if (entry.view.isEnabled) confirmReset() }
        }
        views.shape(group)
    }

    private fun load() = client.call(IconsBridge.STATE, answer = ::take)

    private fun take(state: Bundle?) {
        if (state == null || !state.getBoolean(IconsBridge.AVAILABLE)) {
            say(text.getString(R.string.feature_icon_pack_unavailable))
            group.visibility = View.GONE
            return
        }
        val now = if (state.getBoolean(IconsBridge.USES_PACK)) state.getString(IconsBridge.PACK) ?: SYSTEM else SYSTEM
        applied = now
        val looking = chosen ?: now
        chosen = looking
        overrides = state.getInt(IconsBridge.OVERRIDES)
        preview.blur(state.getInt(IconsBridge.BLUR_RADIUS))
        preview.show(looking)
        fill(state)
        describeRows()
        offerApply()
    }

    private fun fill(state: Bundle) {
        val packages = state.getStringArray(IconsBridge.PACKAGES).orEmpty()
        val labels = state.getStringArray(IconsBridge.LABELS).orEmpty()
        tiles.removeAllViews()
        shown.clear()

        views.tile(tiles, null, text.getString(R.string.feature_icon_pack_system))?.let { tile ->
            tile.glyph(NoneGlyph(context))
            add(SYSTEM, tile)
        }
        if (packages.isNotEmpty()) tiles.addView(divider())
        packages.forEachIndexed { at, pack ->
            val picture = IconsClient.drawable(context, state.getByteArray(IconsBridge.TILE_PREFIX + pack))
            views.tile(tiles, picture, labels.getOrNull(at) ?: pack)?.let { add(pack, it) }
        }
        shown.forEach { (key, tile) -> tile.select(key == chosen, animate = false) }
        // The chosen pack in view, as the picker opens its own option lists at the chosen option.
        chosen?.let { shown[it] }?.view?.let { tile -> strip.post { strip.scrollTo((tile.left - strip.width / 2 + tile.width / 2).coerceAtLeast(0), 0) } }
        if (packages.isEmpty()) say(text.getString(R.string.feature_icon_pack_none)) else message.visibility = View.GONE
    }

    private fun add(key: String, tile: PickerViews.Tile) {
        tiles.addView(tile.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = PickerPage.dp(context, TILE_GAP_DP)
        })
        shown[key] = tile
        tile.view.setOnClickListener { choose(key) }
    }

    /** Looks at [key]: the preview shows the home screen with its icons, and nothing is applied yet. */
    private fun choose(key: String) {
        if (busy || key == chosen) return
        chosen = key
        shown.forEach { (other, tile) -> tile.select(other == key, animate = true) }
        shown[key]?.view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        preview.show(key)
        offerApply()
    }

    private fun offerApply() = page.setApply(
        when {
            busy -> PickerPage.ApplyState.APPLYING
            chosen != null && chosen != applied -> PickerPage.ApplyState.READY
            else -> PickerPage.ApplyState.NOTHING
        },
    )

    /** Makes the pack being looked at the home screen's; the home screen brings its new icons in as they land. */
    private fun apply() {
        val key = chosen ?: return
        if (busy || key == applied) return
        busy = true
        offerApply()
        client.call(IconsBridge.APPLY, key) { answer ->
            busy = false
            if (answer?.getBoolean(IconsBridge.OK) != true) {
                offerApply()
                shown[key]?.view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
                return@call
            }
            applied = key
            shown[key]?.view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            describeRows()
            offerApply()
        }
    }

    private fun describeRows() {
        val usesPack = applied != null && applied != SYSTEM
        perApp?.let { entry ->
            views.setEnabled(entry, usesPack)
            when {
                !usesPack -> views.describe(entry, text.getString(R.string.feature_icon_per_app_system_only))
                overrides == 0 -> views.describe(entry, text.getString(R.string.feature_icon_per_app_summary_none))
                else -> views.describe(entry, text.getQuantityString(R.plurals.feature_icon_per_app_summary, overrides, overrides))
            }
        }
        reset?.let { views.setEnabled(it, usesPack && overrides > 0) }
    }

    private fun confirmReset() {
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(text.getString(R.string.feature_icon_reset_confirm_title))
            .setMessage(text.getString(R.string.feature_icon_reset_confirm_message))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(text.getString(R.string.feature_icon_reset_confirm)) { _, _ ->
                client.call(IconsBridge.RESET) {
                    overrides = 0
                    LauncherPreview.forget()
                    chosen?.let(preview::show)
                    describeRows()
                }
            }
            .show()
    }

    private fun say(line: String) {
        message.text = line
        message.visibility = View.VISIBLE
    }

    /** The thin line Theme pack draws between No theme and the themes. */
    private fun divider(): View = View(context).apply {
        colors.paint(this, "colorOutline", ::setBackgroundColor)
        layoutParams = LinearLayout.LayoutParams(PickerPage.dp(context, 1f), PickerPage.dp(context, 40f)).apply {
            marginEnd = PickerPage.dp(context, TILE_GAP_DP)
            bottomMargin = PickerPage.dp(context, 20f)
        }
    }

    /** A circle struck through, the picker's mark for "none", as Theme pack draws No theme. */
    private class NoneGlyph(context: Context) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = PickerPage.dp(context, 2.5f).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        private val size = PickerPage.dp(context, 24f)

        override fun draw(canvas: Canvas) {
            val box = bounds
            val radius = minOf(box.width(), box.height()) / 2f - paint.strokeWidth
            val cx = box.exactCenterX()
            val cy = box.exactCenterY()
            paint.color = tint
            canvas.drawCircle(cx, cy, radius, paint)
            val d = radius * DIAGONAL
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
        }

        private var tint = 0xFF000000.toInt()

        override fun setTintList(tint: android.content.res.ColorStateList?) {
            this.tint = tint?.defaultColor ?: this.tint
            invalidateSelf()
        }

        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Drawable API")
        override fun getOpacity() = PixelFormat.TRANSLUCENT

        private companion object {
            const val DIAGONAL = 0.7071f
        }
    }

    private companion object {
        const val SYSTEM = ""
        const val PLAIN_ENTRY = "customization_option_entry_lock_screen_notifications"
        const val HOME_CARD = "home_preview_card"
        /** The picker's option sheet: its tile spacing, padding, background and corners. */
        const val TILE_GAP_DP = 8f
        const val SHEET_PADDING_DP = 20f
        const val SHEET_CORNER_DP = 28f
        const val SHEET_BACKGROUND = "floating_sheet_content_background"
    }
}
