package my.github.MrxSiN.pixellauncherevolved.recovery

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import my.github.MrxSiN.pixellauncherevolved.R

/**
 * Views drawn the way Android 17 QPR Settings draws a page (Material 3 Expressive):
 * a back button and large title on the page color, then rows grouped into one
 * rounded card with small inner corners between them, and a footer note.
 *
 * Colors are the system's dynamic color roles (`colors_recovery.xml`, light and night), so
 * the page follows the wallpaper theme and dark mode the way Settings does.
 */
internal class RecoveryPage(private val activity: Activity) {

    /** The column the page's content goes in, set as the activity's content. */
    val column: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(GUTTER), 0, dp(GUTTER), dp(GUTTER))
        filterTouchesWhenObscured = true
    }

    init {
        activity.setContentView(ScrollView(activity).apply {
            addView(column)
            clipToPadding = false
            // Drawn edge to edge: keep the content clear of the system bars and cutout.
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        })
    }

    /** The back button and the large title Settings opens a page with. */
    fun header(title: CharSequence) {
        column.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_recovery_back)
            imageTintList = activity.getColorStateList(R.color.recovery_on_surface)
            setBackgroundResource(ripple())
            contentDescription = activity.getString(R.string.recovery_back)
            setOnClickListener { activity.finish() }
            layoutParams = LinearLayout.LayoutParams(dp(TOUCH), dp(TOUCH)).apply { topMargin = dp(8); marginStart = -dp(12) }
        })
        column.addView(text(title, TITLE_SP, R.color.recovery_on_surface).apply {
            setPadding(0, dp(28), 0, dp(24))
            isAccessibilityHeading = true
        })
    }

    /** Body text on the page itself, above the cards. */
    fun intro(value: CharSequence): TextView =
        text(value, BODY_SP, R.color.recovery_on_surface_variant).apply { setPadding(0, 0, 0, dp(24)) }.also(column::addView)

    /** A tonal card for what the page is doing now: checking, restoring, done or failed. */
    fun banner(value: CharSequence): TextView = text(value, BODY_SP, R.color.recovery_on_banner).apply {
        background = shape(R.color.recovery_banner, dp(OUTER).toFloat(), dp(OUTER).toFloat())
        setPadding(dp(20), dp(16), dp(20), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(24) }
    }.also(column::addView)

    /** A group's label, in the primary color, the way Settings labels a category. */
    fun groupTitle(value: CharSequence) {
        column.addView(text(value, LABEL_SP, R.color.recovery_primary).apply {
            setPadding(dp(16), 0, dp(16), dp(8))
            isAccessibilityHeading = true
        })
    }

    /**
     * One row of a group: icon, title, summary and a pill action button. Call
     * [shapeGroup] once the group's rows are all added, so its corners are drawn.
     */
    fun row(icon: Int, title: CharSequence, action: CharSequence, onClick: () -> Unit): Row {
        val summary = text("", BODY_SP, R.color.recovery_on_surface_variant)
        val button = Button(activity).apply {
            text = action
            isAllCaps = false
            isEnabled = false
            stateListAnimator = null
            minHeight = dp(40)
            minimumHeight = dp(40)
            setPadding(dp(16), 0, dp(16), 0)
            setTextColor(activity.getColorStateList(R.color.recovery_button_text))
            background = GradientDrawable().apply {
                cornerRadius = dp(PILL).toFloat()
                color = activity.getColorStateList(R.color.recovery_button_fill)
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(40)).apply { marginStart = dp(12) }
        }
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, ROW_SP, R.color.recovery_on_surface).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(summary)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(ImageView(activity).apply {
                setImageResource(icon)
                imageTintList = activity.getColorStateList(R.color.recovery_on_surface_variant)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) }
            })
            addView(texts)
            addView(button)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(GAP) }
        }
        column.addView(view)
        return Row(view, summary, button)
    }

    /** Large outer corners on the group's first and last rows, small ones between. */
    fun shapeGroup(rows: List<Row>) {
        rows.forEachIndexed { index, row ->
            val top = if (index == 0) dp(OUTER) else dp(INNER)
            val bottom = if (index == rows.lastIndex) dp(OUTER) else dp(INNER)
            row.view.background = shape(R.color.recovery_card, top.toFloat(), bottom.toFloat())
        }
        (rows.lastOrNull()?.view?.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(24)
    }

    /** The info note Settings closes a page with. */
    fun footer(value: CharSequence) {
        column.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_recovery_info)
                imageTintList = activity.getColorStateList(R.color.recovery_on_surface_variant)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { bottomMargin = dp(12) }
            })
            addView(text(value, BODY_SP, R.color.recovery_on_surface_variant))
        })
    }

    class Row(val view: View, val summary: TextView, val button: Button)

    private fun text(value: CharSequence, sp: Float, color: Int) = TextView(activity).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color(color))
    }

    private fun shape(color: Int, top: Float, bottom: Float) = GradientDrawable().apply {
        setColor(color(color))
        cornerRadii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
    }

    private fun ripple(): Int = TypedValue().also {
        activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
    }.resourceId

    private fun color(id: Int): Int = activity.getColor(id)

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics).toInt()

    private companion object {
        /** Material 3 Expressive list group: 24dp outside, 4dp between rows, 2dp gap. */
        const val OUTER = 24
        const val INNER = 4
        const val GAP = 2
        const val PILL = 20
        const val GUTTER = 16
        const val TOUCH = 48
        const val TITLE_SP = 36f
        const val ROW_SP = 16f
        const val BODY_SP = 14f
        const val LABEL_SP = 14f
    }
}
