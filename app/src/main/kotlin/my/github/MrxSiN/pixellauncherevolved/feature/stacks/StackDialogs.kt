package my.github.MrxSiN.pixellauncherevolved.feature.stacks

import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveGrouping
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveShapes
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveType

/**
 * Create stack, Add widget and Edit stack, opened from a widget's long-press menu.
 *
 * The widget the menu was opened on is the main one: a widget joining it takes
 * its size. A new widget comes from the launcher's own widget picker; one
 * already on the home screen can be chosen from a list, which is also the way
 * to stack without dragging. Every change is made by [StackActions].
 */
internal class StackDialogs(
    private val host: StackHost,
    private val swipe: StackSwipe,
    private val actions: StackActions,
    private val strings: Resources,
    private val logger: Logger,
) {

    /** Create stack (a widget on its own) or Add widget (a stack): from the picker or the home screen. */
    fun addTo(main: View) {
        val context = main.context
        val launcher = host.launcher(main) ?: return
        val workspace = host.workspace(launcher) ?: return
        val stack = WidgetStacks.graph.stackOf(host.id.getInt(main.tag))
        val title = strings.getString(if (stack == null) R.string.widget_stacks_create else R.string.widget_stacks_add_widget)
        if (stack?.isFull == true) {
            notice(context, title, strings.getString(R.string.widget_stacks_full, StackGraph.MAX_MEMBERS))
            return
        }
        val candidates = ArrayList<Pair<View, Int>>()
        for (page in 0 until workspace.childCount) {
            val items = host.itemsOf(workspace.getChildAt(page)) ?: continue
            for (i in 0 until items.childCount) {
                val view = items.getChildAt(i)
                val item = view.tag
                if (view === main || !host.isWidget(item)) continue
                // A stack does not go into another, and a widget joins only at a size its app allows.
                if (WidgetStacks.graph.stackOf(host.id.getInt(item)) != null || !host.fits(item, main.tag)) continue
                candidates += view to page + 1
            }
        }
        var dialog: Dialog? = null
        val rows = ArrayList<Row>(candidates.size + 1)
        rows += Row(strings.getString(R.string.widget_stacks_from_picker), strings.getString(R.string.widget_stacks_from_picker_summary), null) {
            dialog?.dismiss()
            runCatching { actions.pickInto(launcher, main) }
                .onFailure { logger.warn("Widget stacks: the widget picker could not be opened", it) }
        }
        for ((view, page) in candidates) {
            val label = host.label(view) ?: strings.getString(R.string.widget_stacks_unnamed)
            rows += Row(label, strings.getString(R.string.widget_stacks_page, page), preview(context, view)) {
                dialog?.dismiss()
                val joined = runCatching { actions.stack(launcher, view, main) }
                    .onFailure { logger.warn("Widget stacks: the widget could not be stacked", it) }
                    .getOrDefault(false)
                main.performHapticFeedback(if (joined) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT)
            }
        }
        dialog = ExpressiveDialog(context)
            .title(title)
            .message(strings.getString(R.string.widget_stacks_choose))
            .content(list(context, rows))
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    /** The members of [anchor]'s stack, with what can be done to each, and to the whole stack. */
    fun edit(anchor: View) {
        val context = anchor.context
        val launcher = host.launcher(anchor) ?: return
        val stack = WidgetStacks.graph.stackOf(host.id.getInt(anchor.tag)) ?: return
        val manager = AppWidgetManager.getInstance(context)
        var dialog: Dialog? = null
        val rows = ArrayList<Row>(stack.members.size + 3)
        for ((index, id) in stack.members.withIndex()) {
            val info = host.modelItem(launcher, id)?.let { appWidgetId(it) }?.let { manager.getAppWidgetInfo(it) }
            val label = info?.loadLabel(context.packageManager) ?: strings.getString(R.string.widget_stacks_unnamed)
            val summary = listOfNotNull(
                strings.getString(R.string.widget_stacks_position, index + 1, stack.members.size),
                strings.getString(R.string.widget_stacks_showing).takeIf { index == stack.shown },
                slotName(stack.slots[index]),
            ).joinToString(" · ")
            rows += Row(label, summary, info?.let { preview(context, it) }, mover(context, stack, index) { dialog?.dismiss(); edit(anchor) }) {
                dialog?.dismiss()
                member(anchor, launcher, stack, index, label, info)
            }
        }
        rows += Row(strings.getString(R.string.widget_stacks_add_widget), null, null) {
            dialog?.dismiss()
            addTo(anchor)
        }
        rows += Row(strings.getString(R.string.widget_stacks_unstack), strings.getString(R.string.widget_stacks_unstack_summary), null) {
            dialog?.dismiss()
            for (id in stack.members) if (id != stack.shownId) actions.extract(launcher, anchor, id)
        }
        rows += Row(strings.getString(R.string.widget_stacks_remove_stack), null, null, danger = true) {
            dialog?.dismiss()
            confirm(context, strings.getString(R.string.widget_stacks_remove_stack), strings.getString(R.string.widget_stacks_remove_stack_message)) {
                runCatching { actions.deleteStack(launcher, anchor) }
                    .onFailure { logger.warn("Widget stacks: the stack could not be removed", it) }
            }
        }
        dialog = ExpressiveDialog(context)
            .title(strings.getString(R.string.widget_stacks_edit))
            .message(strings.getString(R.string.widget_stacks_edit_message))
            .content(list(context, rows))
            .confirm(strings.getString(R.string.widget_stacks_done))
            .show()
    }

    /** What can be done to member [index]. */
    private fun member(anchor: View, launcher: Any, stack: StackGraph.Stack, index: Int, label: CharSequence, info: AppWidgetProviderInfo?) {
        val context = anchor.context
        val id = stack.members[index]
        val rows = ArrayList<Row>()
        var dialog: Dialog? = null
        fun row(title: Int, summary: String? = null, danger: Boolean = false, run: () -> Unit) {
            rows += Row(strings.getString(title), summary, null, danger = danger) {
                dialog?.dismiss()
                runCatching(run).onFailure { logger.warn("Widget stacks: the change could not be made", it) }
            }
        }
        if (index != stack.shown) row(R.string.widget_stacks_show) { swipe.showNow(anchor, stack, index) }
        if (info?.configure != null) row(R.string.widget_stacks_configure) { configure(launcher, id) }
        row(R.string.widget_stacks_show_at, slotName(stack.slots[index]) ?: strings.getString(R.string.widget_stacks_slot_none)) {
            slot(anchor, stack, index, label)
        }
        row(R.string.widget_stacks_extract, strings.getString(R.string.widget_stacks_extract_summary)) { actions.extract(launcher, anchor, id) }
        row(R.string.widget_stacks_remove, danger = true) {
            confirm(context, strings.getString(R.string.widget_stacks_remove), strings.getString(R.string.widget_stacks_remove_message, label)) {
                actions.delete(launcher, anchor, id)
            }
        }
        dialog = ExpressiveDialog(context)
            .title(label)
            .content(list(context, rows))
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    /** Which part of the day Smart rotate brings member [index] forward in. */
    private fun slot(anchor: View, stack: StackGraph.Stack, index: Int, label: CharSequence) {
        val context = anchor.context
        val names = intArrayOf(
            R.string.widget_stacks_slot_none, R.string.widget_stacks_slot_morning, R.string.widget_stacks_slot_afternoon,
            R.string.widget_stacks_slot_evening, R.string.widget_stacks_slot_night,
        )
        var dialog: Dialog? = null
        val rows = names.indices.map { slot ->
            Row(strings.getString(names[slot]), strings.getString(R.string.widget_stacks_selected).takeIf { stack.slots[index] == slot }, null) {
                dialog?.dismiss()
                val current = WidgetStacks.graph.stackOf(stack.members[index]) ?: return@Row
                WidgetStacks.store(
                    WidgetStacks.graph.replace(current, current.withSlot(current.indexOf(stack.members[index]), slot)),
                    WidgetStacks.hiddenScreens, WidgetStacks.anchors,
                )
            }
        }
        dialog = ExpressiveDialog(context)
            .title(strings.getString(R.string.widget_stacks_show_at))
            .message(if (WidgetStacks.smartRotate) strings.getString(R.string.widget_stacks_show_at_message, label)
            else strings.getString(R.string.widget_stacks_show_at_off))
            .content(list(context, rows))
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    /** The provider's own settings for member [id], through the launcher's `LauncherWidgetHolder`. */
    private fun configure(launcher: Any, id: Int) {
        val item = host.modelItem(launcher, id) ?: return
        val appWidgetId = appWidgetId(item) ?: return
        val holder = Reflect.method(launcher.javaClass, "getAppWidgetHolder")?.invoke(launcher) ?: return
        val start = Reflect.declared(holder.javaClass, "startConfigActivity") { it.parameterTypes.size == 3 } ?: return
        start.invoke(holder, launcher, appWidgetId, REQUEST_CONFIGURE)
    }

    /** Move up and Move down for member [index], or null when it cannot move either way. */
    private fun mover(context: Context, stack: StackGraph.Stack, index: Int, done: () -> Unit): View? {
        if (stack.members.size < 2) return null
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(arrow(context, R.drawable.ic_stack_up, R.string.widget_stacks_move_up, index > 0) { reorder(stack, index, index - 1); done() })
            addView(arrow(context, R.drawable.ic_stack_down, R.string.widget_stacks_move_down, index < stack.members.lastIndex) {
                reorder(stack, index, index + 1)
                done()
            })
        }
    }

    private fun arrow(context: Context, icon: Int, label: Int, enabled: Boolean, onClick: () -> Unit) = ImageView(context).apply {
        setImageDrawable(strings.getDrawable(icon, null))
        imageTintList = android.content.res.ColorStateList.valueOf(ExpressiveRole.ON_SURFACE_VARIANT.of(context))
        contentDescription = strings.getString(label)
        val touch = dp(context, TOUCH_DP)
        layoutParams = LinearLayout.LayoutParams(touch, touch)
        scaleType = ImageView.ScaleType.CENTER
        isEnabled = enabled
        alpha = if (enabled) 1f else DISABLED_ALPHA
        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        if (ripple.resourceId != 0) background = context.getDrawable(ripple.resourceId)
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            onClick()
        }
    }

    private fun reorder(stack: StackGraph.Stack, from: Int, to: Int) {
        val current = WidgetStacks.graph.stackOf(stack.members[from]) ?: return
        WidgetStacks.store(WidgetStacks.graph.replace(current, current.moved(from, to)), WidgetStacks.hiddenScreens, WidgetStacks.anchors)
    }

    private fun slotName(slot: Int): String? = when (slot) {
        StackGraph.Slot.MORNING -> strings.getString(R.string.widget_stacks_slot_morning)
        StackGraph.Slot.AFTERNOON -> strings.getString(R.string.widget_stacks_slot_afternoon)
        StackGraph.Slot.EVENING -> strings.getString(R.string.widget_stacks_slot_evening)
        StackGraph.Slot.NIGHT -> strings.getString(R.string.widget_stacks_slot_night)
        else -> null
    }

    private fun notice(context: Context, title: String, message: String) {
        ExpressiveDialog(context).title(title).message(message).dismiss(context.getString(android.R.string.ok)).show()
    }

    private fun confirm(context: Context, title: String, message: String, run: () -> Unit) {
        ExpressiveDialog(context)
            .title(title)
            .message(message)
            .dismiss(context.getString(android.R.string.cancel))
            .confirm(strings.getString(R.string.widget_stacks_remove_confirm), run)
            .show()
    }

    private fun appWidgetId(item: Any): Int? = Reflect.field(item.javaClass, "appWidgetId")?.getInt(item)

    private fun preview(context: Context, view: View): Drawable? = (view as? android.appwidget.AppWidgetHostView)?.appWidgetInfo?.let { preview(context, it) }

    /** The widget's own preview picture, else its app's icon. */
    private fun preview(context: Context, info: AppWidgetProviderInfo): Drawable? =
        runCatching { info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi) ?: info.loadIcon(context, context.resources.displayMetrics.densityDpi) }
            .getOrNull()

    private class Row(
        val title: CharSequence,
        val summary: CharSequence?,
        val icon: Drawable?,
        val trailing: View? = null,
        val danger: Boolean = false,
        val onClick: () -> Unit,
    )

    /** A grouped card of tappable rows, as this module's settings draw one. */
    private fun list(context: Context, rows: List<Row>): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val inset = dp(context, CARD_INSET_DP)
        setPadding(inset, 0, inset, 0)
        for ((index, spec) in rows.withIndex()) {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(context, ROW_HEIGHT_DP)
                setPadding(dp(context, ROW_PADDING_DP), dp(context, 12f), dp(context, 8f), dp(context, 12f))
                background = ExpressiveShapes.card(context, ExpressiveGrouping.placementOf(index, rows.size))
                isClickable = true
                isFocusable = true
                setOnClickListener { spec.onClick() }
                spec.icon?.let { icon ->
                    addView(ImageView(context).apply {
                        setImageDrawable(icon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(dp(context, PREVIEW_DP), dp(context, PREVIEW_DP)).apply { marginEnd = dp(context, 16f) })
                }
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = spec.title
                        ExpressiveType.BODY_LARGE.applyTo(this, if (spec.danger) ExpressiveRole.ERROR else ExpressiveRole.ON_SURFACE)
                    })
                    spec.summary?.let { summary ->
                        addView(TextView(context).apply {
                            text = summary
                            ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
                        })
                    }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                spec.trailing?.let { addView(it) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (index < rows.size - 1) bottomMargin = dp(context, ExpressiveShapes.ROW_GAP_DP)
            })
        }
    }

    private fun dp(context: Context, value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val CARD_INSET_DP = 16f
        const val ROW_HEIGHT_DP = 56f
        const val ROW_PADDING_DP = 20f
        const val PREVIEW_DP = 40f
        const val TOUCH_DP = 48f
        const val DISABLED_ALPHA = 0.38f

        /** Not one of the launcher's own request codes, so it leaves the result to the widget's app. */
        const val REQUEST_CONFIGURE = 0x5157
    }
}
