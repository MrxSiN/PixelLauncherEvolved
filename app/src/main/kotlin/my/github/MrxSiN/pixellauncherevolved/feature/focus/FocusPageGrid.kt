package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveType

/**
 * The pages a Mode can take, as a two-column gallery of selectable previews.
 *
 * Each tile is a page and its name. Tapping anywhere on it toggles the page, and
 * the preview answers with the spring its own selection runs on, while the name
 * turns primary so the choice reads without looking at the corner.
 */
internal class FocusPageGrid(
    private val context: Context,
    private val pageLabel: (screen: Int) -> String,
) {

    private val chosen = mutableSetOf<Int>()

    /** The pages ticked now. */
    fun selectedScreens(): Set<Int> = chosen.toSet()

    fun build(pages: List<Int>, previews: Map<Int, FocusPagePreview>, selected: Set<Int>): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(PADDING_DP), 0, dp(PADDING_DP), 0)
            pages.filter(previews::containsKey).chunked(COLUMNS).forEachIndexed { index, row ->
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        row.forEachIndexed { column, screen ->
                            addView(
                                tile(screen, previews.getValue(screen), screen in selected),
                                cell(first = column == 0),
                            )
                        }
                        repeat(COLUMNS - row.size) { addView(Space(context), cell(first = false)) }
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { if (index > 0) topMargin = dp(ROW_GAP_DP) },
                )
            }
        }

    private fun tile(screen: Int, preview: FocusPagePreview, selected: Boolean): View {
        val label = pageLabel(screen)
        val page = FocusPagePreviewView(context, preview)
        val name = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
        }

        fun show(checked: Boolean, animate: Boolean) {
            if (checked) chosen += screen else chosen -= screen
            page.setChecked(checked, animate)
            ExpressiveType.LABEL_LARGE.applyTo(name, if (checked) ExpressiveRole.PRIMARY else ExpressiveRole.ON_SURFACE)
        }
        show(selected, animate = false)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = label
            addView(page, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(LABEL_GAP_DP) })
            setOnClickListener {
                show(!page.checked, animate = true)
            }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = CheckBox::class.java.name
                    info.isCheckable = true
                    info.isChecked = page.checked
                }
            }
        }
    }

    private fun cell(first: Boolean) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        .apply { if (!first) marginStart = dp(COLUMN_GAP_DP) }

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val COLUMNS = 2
        const val PADDING_DP = 24f
        const val COLUMN_GAP_DP = 16f
        const val ROW_GAP_DP = 20f
        const val LABEL_GAP_DP = 8f
    }
}
