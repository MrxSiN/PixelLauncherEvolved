package my.github.MrxSiN.pixellauncherevolved.picker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView

import java.lang.reflect.Field
import java.lang.reflect.Proxy

/**
 * Wallpaper & style's own views, inflated from its own layouts, for what this
 * module shows in it: option entries in rounded groups, and option tiles with
 * the picker's selection shape.
 */
internal class PickerViews(private val colors: PickerColors) {

    /** One of the picker's option entries: the row and its two lines of text. */
    class Entry(val view: ViewGroup, val title: TextView, val description: TextView, val icon: ImageView?)

    /**
     * An option entry from the picker's [layout], titled [title], not yet added.
     * [layout] is `customization_option_entry_app_icons` for an entry with a
     * picture at its end, or the plain two-line entry.
     */
    fun entry(parent: ViewGroup, layout: String, title: String, description: String?): Entry? {
        val context = parent.context
        val view = runCatching {
            LayoutInflater.from(context).inflate(PickerPage.resource(context, "layout", layout), parent, false) as? ViewGroup
        }.getOrNull() ?: return null
        // The native entry's own id stays with the native entry.
        view.id = View.generateViewId()
        val titleView = view.findViewById<TextView>(PickerPage.resource(context, "id", "option_entry_title")) ?: return null
        val descriptionView = view.findViewById<TextView>(PickerPage.resource(context, "id", "option_entry_description")) ?: return null
        view.findViewById<View>(PickerPage.resource(context, "id", "downloading_icon"))?.visibility = View.GONE
        val icon = view.findViewById<ImageView>(PickerPage.resource(context, "id", "option_entry_icon"))
        titleView.text = title
        colors.paint(titleView, "colorOnSurface", titleView::setTextColor)
        colors.paint(descriptionView, "colorOnSurfaceVariant", descriptionView::setTextColor)
        view.findViewById<View>(PickerPage.resource(context, "id", "option_entry_icon_container"))?.let { box ->
            colors.paint(box, "colorSurfaceContainerHigh") { box.background?.mutate()?.setTint(it) }
        }
        return Entry(view, titleView, descriptionView, icon).also { describe(it, description) }
    }

    fun describe(entry: Entry, description: String?) {
        entry.description.text = description.orEmpty()
        entry.description.visibility = if (description.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /** Greys out and stops [entry] while it cannot be used, as Material greys a disabled control. */
    fun setEnabled(entry: Entry, enabled: Boolean) {
        entry.view.alpha = if (enabled) 1f else DISABLED_ALPHA
        entry.view.isEnabled = enabled
    }

    /** Sets [view]'s background to the picker's entry background [name], tinted as the picker tints its entries. */
    fun background(view: View, name: String) {
        runCatching { view.setBackgroundResource(PickerPage.resource(view.context, "drawable", name)) }
        colors.paint(view, "colorSurfaceBright") { view.background?.mutate()?.setTint(it) }
    }

    /** Gives each visible entry of [group] the picker's rounded corners for where it sits. */
    fun shape(group: ViewGroup) {
        val rows = (0 until group.childCount).map(group::getChildAt).filter { it.visibility != View.GONE }
        rows.forEachIndexed { index, row ->
            background(
                row,
                when {
                    rows.size == 1 -> "customization_option_entry_singleton_background"
                    index == 0 -> "customization_option_entry_top_background"
                    index == rows.lastIndex -> "customization_option_entry_bottom_background"
                    else -> "customization_option_entry_background"
                },
            )
        }
    }

    /** A vertical group spaced as the picker spaces its lists. */
    fun group(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        runCatching { dividerDrawable = context.getDrawable(PickerPage.resource(context, "drawable", "customization_option_entry_divider")) }
    }

    /**
     * One of the picker's option tiles (`icon_style_option2`): [picture] on the
     * picker's option background, [label] under it.
     *
     * The background is the picker's `OptionItemBackground`, which morphs from
     * a circle to a rounded square, and between its two colours, as its
     * `progress` goes from 0 to 1; [Tile.select] animates it there and back,
     * as the picker's own tiles do.
     */
    fun tile(parent: ViewGroup, picture: Drawable?, label: String): Tile? {
        val context = parent.context
        val view = runCatching {
            LayoutInflater.from(context).inflate(PickerPage.resource(context, "layout", "icon_style_option2"), parent, false)
        }.getOrNull() ?: return null
        val image = view.findViewById<ImageView>(PickerPage.resource(context, "id", "app_icon")) ?: return null
        val glyph = view.findViewById<ImageView>(PickerPage.resource(context, "id", "foreground"))
        val background = view.findViewById<View>(PickerPage.resource(context, "id", "background"))
        val text = view.findViewById<TextView>(PickerPage.resource(context, "id", "text"))
        text?.text = label
        text?.let { colors.paint(it, "colorOnSurface", it::setTextColor) }
        glyph?.visibility = View.GONE
        image.setImageDrawable(picture)
        return Tile(view, image, glyph, background, label)
    }

    inner class Tile(val view: View, val image: ImageView, val glyph: ImageView?, private val background: View?, label: String) {

        private val progress: Field? = background?.let { field(it.javaClass, "progress") }
        private var selected = false
        private var animator: ValueAnimator? = null

        init {
            background?.let { back ->
                val selectedColor = field(back.javaClass, "colorSelected")
                val unselectedColor = field(back.javaClass, "colorUnselected")
                colors.paint(back, "colorPrimaryContainer") { runCatching { selectedColor?.setInt(back, it); back.invalidate() } }
                colors.paint(this, "colorSurfaceContainerHigh") { runCatching { unselectedColor?.setInt(back, it); back.invalidate() } }
            }
            view.contentDescription = label
            view.isFocusable = true
            view.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = RadioButton::class.java.name
                    info.isCheckable = true
                    info.isChecked = selected
                }
            }
        }

        /** Shows the glyph [picture] in the middle of the tile, in the picker's icon colour. */
        fun glyph(picture: Drawable) {
            image.visibility = View.GONE
            glyph?.apply {
                visibility = View.VISIBLE
                setImageDrawable(picture)
                colors.paint(this, "colorOnSurface") { imageTintList = android.content.res.ColorStateList.valueOf(it) }
            }
        }

        fun select(on: Boolean, animate: Boolean) {
            if (on == selected && animate) return
            selected = on
            view.isSelected = on
            val back = background ?: return
            val field = progress ?: return
            val from = runCatching { field.getFloat(back) }.getOrDefault(if (on) 0f else 1f)
            val to = if (on) 1f else 0f
            animator?.cancel()
            if (!animate || ValueAnimator.areAnimatorsEnabled().not()) {
                runCatching { field.setFloat(back, to) }
                back.invalidate()
                return
            }
            animator = ValueAnimator.ofFloat(from, to).apply {
                duration = SELECT_MS
                interpolator = EMPHASIZED_DECELERATE
                addUpdateListener {
                    runCatching { field.setFloat(back, it.animatedValue as Float) }
                    back.invalidate()
                }
                start()
            }
        }
    }

    private fun field(type: Class<*>, name: String): Field? =
        generateSequence(type as Class<*>?) { it.superclass }
            .firstNotNullOfOrNull { runCatching { it.getDeclaredField(name) }.getOrNull() }
            ?.apply { isAccessible = true }

    private companion object {
        const val DISABLED_ALPHA = 0.38f
        const val SELECT_MS = 350L
        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    }
}

/**
 * An implementation of one of the picker's own interfaces, [type], that
 * hands every call but `equals`, `hashCode` and `toString` to [body].
 */
internal fun pickerProxy(type: Class<*>, body: (name: String, args: Array<Any?>) -> Any?): Any =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.getOrNull(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> type.name
            else -> body(method.name, args ?: emptyArray())
        }
    }
