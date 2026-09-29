package my.github.MrxSiN.pixellauncherevolved.picker

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toolbar
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher

/**
 * A page of this module's, opened from Wallpaper & style's Home screen list the
 * way the picker opens its own: over the whole screen, under the picker's own
 * toolbar with its back button, closed by it or by going back.
 *
 * It opens and closes with Android 17's shared X axis motion
 * ([SharedAxisTransition]), the picker sliding away under it, and a back
 * gesture previews the close as predictive back does. Animator duration scale
 * and Remove animations apply as to any animator.
 *
 * [content] is whatever the page shows under its toolbar.
 */
internal class PickerPage(
    context: Context,
    private val colors: PickerColors,
    title: String,
    content: View,
    private val onClosed: () -> Unit = {},
    hero: View? = null,
    heroFrom: () -> Rect? = { null },
    rising: List<View> = emptyList(),
) {

    private val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
    private val root: LinearLayout
    private val transition: SharedAxisTransition
    private var closing = false
    private var applyButton: View? = null

    init {
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar(context, title))
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
        }
        val activity = activityOf(context)
        // The picker's own page colour until the previewed theme's arrives, so the page is never see-through.
        activity?.window?.decorView?.background?.constantState?.newDrawable()?.let { root.background = it }
        colors.paint(root, "colorSurfaceContainer", root::setBackgroundColor)
        dialog.setContentView(root)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setDecorFitsSystemWindows(false)
            // The page moves itself; its window stays still and clear, so the picker shows through as it goes.
            setWindowAnimations(0)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        val under = activity?.findViewById<View>(android.R.id.content)
        transition = SharedAxisTransition(root, under, hero, heroFrom, rising)
        dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, BackGesture())
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) close()
            true
        }
        dialog.setOnDismissListener {
            under?.apply {
                translationX = 0f
                alpha = 1f
            }
            onClosed()
        }
    }

    /** Going back, by gesture or button: previews the close as the gesture moves, and closes when it is let go. */
    private inner class BackGesture : OnBackAnimationCallback {
        override fun onBackStarted(event: BackEvent) = onBackProgressed(event)

        override fun onBackProgressed(event: BackEvent) {
            if (!closing) transition.follow(event.progress, event.swipeEdge == BackEvent.EDGE_LEFT)
        }

        override fun onBackCancelled() {
            if (!closing) transition.settle()
        }

        override fun onBackInvoked() = close()
    }

    fun close() {
        if (closing) return
        closing = true
        transition.close { dialog.dismiss() }
    }

    fun show() {
        root.alpha = 0f
        dialog.show()
        // Opened once laid out, when the page has the width it slides by.
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                transition.open()
                return true
            }
        })
    }

    /** The picker's own toolbar, titled [title], its back button closing the page and its Apply button hidden. */
    private fun toolbar(context: Context, title: String): View {
        val bar = runCatching {
            LayoutInflater.from(context).inflate(resource(context, "layout", "customization_picker2_toolbar"), null, false)
        }.getOrNull() ?: return TextView(context).apply {
            text = title
            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large)
            val padding = dp(context, 16f)
            setPadding(padding, padding, padding, padding)
            setOnClickListener { close() }
        }
        fun <T : View> find(name: String): T? = bar.findViewById(resource(context, "id", name))
        applyButton = find<View>("apply_button")?.apply { visibility = View.GONE }
        find<View>("nav_button")?.let { back ->
            // The picker closes with a cross; a page within it goes back, as the picker's own pages do.
            runCatching { setIcon(back, context.getDrawable(resource(context, "drawable", "ic_arrow_back_24dp"))) }
            runCatching { back.contentDescription = context.getString(resource(context, "string", "bottom_action_bar_back")) }
            back.setOnClickListener { close() }
        }
        find<Toolbar>("toolbar")?.let { toolbar ->
            toolbar.title = title
            colors.paint(toolbar, "colorOnSurface", toolbar::setTitleTextColor)
        }
        return bar
    }

    /**
     * Shows the toolbar's Apply button, as the picker's own option pages do, and
     * runs [onApply] when it is pressed while [ApplyState.READY].
     */
    fun offerApply(onApply: () -> Unit) {
        val button = applyButton ?: return
        button.visibility = View.VISIBLE
        button.setOnClickListener { if (it.isEnabled) onApply() }
        setApply(ApplyState.NOTHING)
    }

    /** The Apply button as the picker draws it: faint and disabled, ready, or spinning while it applies. */
    fun setApply(state: ApplyState) {
        val button = applyButton ?: return
        val context = button.context
        val background = button.findViewById<View>(resource(context, "id", "apply_button_background"))
        val text = button.findViewById<View>(resource(context, "id", "apply_button_text"))
        val progress = button.findViewById<View>(resource(context, "id", "apply_button_progress_indicator"))
        button.isEnabled = state == ApplyState.READY
        background?.isEnabled = state != ApplyState.NOTHING
        text?.visibility = if (state == ApplyState.APPLYING) View.INVISIBLE else View.VISIBLE
        progress?.visibility = if (state == ApplyState.APPLYING) View.VISIBLE else View.GONE
        // Material's button roles, in the previewed theme's colours: the picker binds these itself on
        // its own pages, and left unbound here the disabled label sat dark on a dark faint container.
        val enabled = state != ApplyState.NOTHING
        val container = if (enabled) colors.now("colorPrimary") else withAlpha(colors.now("colorOnSurface"), DISABLED_CONTAINER)
        val label = if (enabled) colors.now("colorOnPrimary") else withAlpha(colors.now("colorOnSurface"), DISABLED_LABEL)
        // The pill drawn is the button's own background; its alpha is left whole, the tint carries it.
        button.background?.mutate()?.apply {
            alpha = 255
            setTint(container)
        }
        (text as? TextView)?.setTextColor(label)
    }

    private fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)

    /** What the Apply button can do: nothing to apply, apply the choice, or applying it now. */
    enum class ApplyState { NOTHING, READY, APPLYING }

    /** Gives the picker's `MaterialButton` [icon]: its setters are shrunk away, so the field is set and the button redrawn. */
    private fun setIcon(button: View, icon: Drawable?) {
        val type = generateSequence(button.javaClass as Class<*>?) { it.superclass }
            .firstOrNull { type -> type.declaredFields.any { it.name == "icon" } } ?: return
        type.getDeclaredField("icon").apply { isAccessible = true }.set(button, icon?.mutate())
        type.getDeclaredMethod("updateIcon", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(button, true)
    }

    companion object {
        /** Material's disabled button: container and label in on-surface at 12% and 38%. */
        private const val DISABLED_CONTAINER = 0.12f
        private const val DISABLED_LABEL = 0.38f

        /** A resource of the picker's own, by type and name; 0 when it has none. */
        fun resource(context: Context, type: String, name: String): Int =
            context.resources.getIdentifier(name, type, context.packageName)

        fun dp(context: Context, value: Float): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

        /** The activity [context] belongs to, however many wrappers it is inside. */
        fun activityOf(context: Context): Activity? =
            generateSequence(context) { (it as? ContextWrapper)?.baseContext }.firstNotNullOfOrNull { it as? Activity }
    }
}
