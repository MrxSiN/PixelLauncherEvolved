package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveCardList
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveShapes
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveType
import my.github.MrxSiN.pixellauncherevolved.focus.FocusMode
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages

/**
 * The Modes, as one grouped card of rows, the way Settings → Modes lists them.
 *
 * Each row leads with the icon Settings shows for that Mode, says which pages
 * it has, marks the Mode that is on with a badge, and ends with a stack of the
 * pages it owns.
 */
internal class FocusModeList(
    private val context: Context,
    private val text: Text,
) {

    /** What the rows say, read from this module's resources by the caller. */
    class Text(
        val active: String,
        val summary: (Set<Int>?) -> String,
    )

    fun build(
        modes: List<FocusMode>,
        assignments: Map<String, Set<Int>>,
        previews: Map<Int, FocusPagePreview>,
        onChosen: (FocusMode) -> Unit,
    ): View = ExpressiveCardList.build(
        context,
        modes.map { mode -> row(mode, assignments[mode.id].orEmpty(), previews) },
    ) { index -> onChosen(modes[index]) }

    private fun row(mode: FocusMode, owned: Set<Int>, previews: Map<Int, FocusPagePreview>) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20f), dp(12f), dp(16f), dp(12f))

            FocusModeIcons.load(context, mode.icon)?.let { icon ->
                addView(ImageView(context).apply {
                    setImageDrawable(icon.mutate().apply { setTint(ExpressiveRole.ON_SURFACE_VARIANT.of(context)) })
                }, LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply { marginEnd = dp(20f) })
            }

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                // Wrapped rather than stretched, so the badge follows the name,
                // and the weighted name is what shrinks when both do not fit.
                addView(titleLine(mode), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
                addView(TextView(context).apply {
                    this.text = this@FocusModeList.text.summary(owned)
                    ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            pageStack(owned, previews)?.let { stack ->
                addView(stack, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(12f) })
            }
        }

    private fun titleLine(mode: FocusMode) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            this.text = mode.name
            ExpressiveType.TITLE_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (mode.isActive) {
            addView(TextView(context).apply {
                this.text = this@FocusModeList.text.active
                ExpressiveType.LABEL_MEDIUM.applyTo(this, ExpressiveRole.ON_PRIMARY_CONTAINER)
                background = ExpressiveShapes.rounded(ExpressiveRole.PRIMARY_CONTAINER.of(context), dp(BADGE_HEIGHT_DP) / 2f)
                gravity = Gravity.CENTER
                minHeight = dp(BADGE_HEIGHT_DP)
                setPadding(dp(8f), 0, dp(8f), 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(8f) })
        }
    }

    /** Up to three of the Mode's pages, fanned so each overlaps the one before it. */
    private fun pageStack(owned: Set<Int>, previews: Map<Int, FocusPagePreview>): View? {
        val pages = owned
            .mapNotNull { screen -> FocusPages.numberOf(screen)?.let { it to previews[screen] } }
            .sortedBy { it.first }
            .mapNotNull { it.second }
            .take(MAX_STACKED)
        if (pages.isEmpty()) return null

        val width = dp(FocusPagePreviewView.COMPACT_WIDTH_DP)
        val step = width - dp(STACK_OVERLAP_DP)
        return FrameLayout(context).apply {
            pages.forEachIndexed { index, preview ->
                addView(
                    FocusPagePreviewView(context, preview, FocusPagePreviewView.Look.THUMBNAIL),
                    FrameLayout.LayoutParams(width, FocusPagePreviewView.heightForWidth(preview, width)).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        leftMargin = step * index
                    },
                )
            }
        }
    }

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val ICON_DP = 24f
        const val BADGE_HEIGHT_DP = 20f
        const val MAX_STACKED = 3
        const val STACK_OVERLAP_DP = 14f
    }
}
