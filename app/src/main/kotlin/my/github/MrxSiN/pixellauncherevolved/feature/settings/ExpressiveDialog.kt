package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A Material 3 Expressive dialog, drawn the way Android 17 QPR1 draws one.
 *
 * The launcher's `AlertDialog` is the platform's older one: square-ish buttons
 * in a flat row and no place for an icon. Android 17's own dialogs are an
 * extra-large rounded surface, with an optional icon heading a centred title,
 * and pill-shaped buttons where the action that commits is filled.
 *
 * Only what this module's dialogs use is here: an icon, a title, a line of
 * supporting text, one scrolling content view and up to two buttons.
 */
class ExpressiveDialog(private val context: Context) {

    private var icon: Drawable? = null
    private var title: CharSequence? = null
    private var message: CharSequence? = null
    private var content: View? = null
    private var confirm: Pair<CharSequence, () -> Unit>? = null
    private var dismiss: Pair<CharSequence, () -> Unit>? = null

    fun icon(drawable: Drawable?) = apply { icon = drawable }

    fun title(text: CharSequence) = apply { title = text }

    fun message(text: CharSequence) = apply { message = text }

    /** Scrolls on its own when taller than the screen allows. */
    fun content(view: View) = apply { content = view }

    /** The filled button, for the action that commits. */
    fun confirm(label: CharSequence, action: () -> Unit = {}) = apply { confirm = label to action }

    /** The text button, for leaving without a change. */
    fun dismiss(label: CharSequence, action: () -> Unit = {}) = apply { dismiss = label to action }

    fun show(): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val surface = surface(dialog)
        dialog.setContentView(surface)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Android 17's own dialogs arrive and leave on the window, not on
            // their content: `Animation.DeviceDefault.Dialog` is this style,
            // scaling between 90% and full size over 220ms on a decelerating
            // quint while fading over 150ms, both ways. Set explicitly, because
            // the activity this opens over need not carry a DeviceDefault theme.
            setWindowAnimations(android.R.style.Animation_Dialog)
            setLayout(width(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        return dialog
    }

    private fun surface(dialog: Dialog): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = ExpressiveShapes.rounded(
            ExpressiveRole.SURFACE_CONTAINER_HIGH.of(context),
            dp(ExpressiveShapes.DIALOG_RADIUS_DP).toFloat(),
        )
        setPadding(0, dp(PADDING_DP), 0, dp(PADDING_DP))

        val centred = icon != null
        icon?.let { drawable ->
            addView(ImageView(context).apply {
                setImageDrawable(drawable.mutate().apply { setTint(ExpressiveRole.SECONDARY.of(context)) })
            }, LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16f)
            })
        }
        title?.let { text ->
            addView(header(text, ExpressiveType.HEADLINE_SMALL, ExpressiveRole.ON_SURFACE, centred))
        }
        message?.let { text ->
            addView(header(text, ExpressiveType.BODY_MEDIUM, ExpressiveRole.ON_SURFACE_VARIANT, centred).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(16f)
            })
        }
        content?.let { view ->
            addView(
                BoundedScrollView(context, maxHeight()).apply {
                    isVerticalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    addView(view)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(16f) },
            )
        }
        if (confirm != null || dismiss != null) addView(buttons(dialog))
    }

    private fun header(text: CharSequence, type: ExpressiveType, role: ExpressiveRole, centred: Boolean) =
        TextView(context).apply {
            this.text = text
            type.applyTo(this, role)
            gravity = if (centred) Gravity.CENTER_HORIZONTAL else Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(PADDING_DP)
                marginEnd = dp(PADDING_DP)
            }
        }

    private fun buttons(dialog: Dialog) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(dp(PADDING_DP), dp(20f), dp(PADDING_DP), 0)
        dismiss?.let { (label, action) ->
            addView(button(label, filled = false) { dialog.dismiss(); action() })
        }
        confirm?.let { (label, action) ->
            addView(button(label, filled = true) { dialog.dismiss(); action() }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(8f) })
        }
    }

    private fun button(label: CharSequence, filled: Boolean, onClick: () -> Unit) = TextView(context).apply {
        text = label
        ExpressiveType.LABEL_LARGE.applyTo(this, if (filled) ExpressiveRole.ON_PRIMARY else ExpressiveRole.PRIMARY)
        gravity = Gravity.CENTER
        minHeight = dp(BUTTON_HEIGHT_DP)
        minWidth = dp(BUTTON_MIN_WIDTH_DP)
        val horizontal = dp(if (filled) 24f else 16f)
        setPadding(horizontal, 0, horizontal, 0)
        background = ExpressiveShapes.pill(
            context,
            if (filled) ExpressiveRole.PRIMARY.of(context) else Color.TRANSPARENT,
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    /** Material 3's dialog width: the screen less its margins, never wider than 560dp. */
    private fun width(): Int {
        val screen = context.resources.displayMetrics.widthPixels
        return min(screen - dp(SCREEN_MARGIN_DP) * 2, dp(MAX_WIDTH_DP))
    }

    private fun maxHeight(): Int = (context.resources.displayMetrics.heightPixels * CONTENT_HEIGHT_SHARE).roundToInt()

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val PADDING_DP = 24f
        const val ICON_DP = 24f
        const val BUTTON_HEIGHT_DP = 40f
        const val BUTTON_MIN_WIDTH_DP = 64f
        const val SCREEN_MARGIN_DP = 24f
        const val MAX_WIDTH_DP = 560f
        const val CONTENT_HEIGHT_SHARE = 0.6f
    }
}

/** A scroll view that grows with its content up to [maxHeight], then scrolls. */
private class BoundedScrollView(context: Context, private val maxHeight: Int) : ScrollView(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
    }
}
