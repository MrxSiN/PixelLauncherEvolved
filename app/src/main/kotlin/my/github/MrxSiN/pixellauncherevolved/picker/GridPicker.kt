package my.github.MrxSiN.pixellauncherevolved.picker

import android.app.AlertDialog
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

import io.github.libxposed.api.XposedInterface

import java.lang.reflect.Field
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.bridge.GridBridge
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.grid.GridSpec

/**
 * Grid & size in Wallpaper & style.
 *
 * ```
 * DefaultShapeGridManager … sortedByDescending comparator   orders the Layout tiles
 * ThemePickerCustomizationOptionsBinder.bind                 binds every floating sheet
 * FloatingToolbarTabAdapter.submitList(List)                 the Icons sheet's tabs
 * ```
 *
 * Layout: the launcher lists a Custom tile ([GridBridge.CUSTOM]); it is kept
 * after XL, and under the tiles this adds sliders for Columns, Rows and the
 * spacing across and down. Picking a count stores it in the launcher, selects
 * the Custom tile so the preview shows it, and Apply makes it the launcher's
 * grid, as with any tile. Spacing suits every grid, so it is applied as it is
 * picked, as the icon size is.
 *
 * Icons: a Size tab beside Shape, with the four icon sizes as option tiles.
 * A size is applied as it is picked; the preview is drawn again with it.
 */
internal class GridPicker(
    private val xposed: XposedInterface,
    private val classLoader: ClassLoader,
    private val text: Resources,
    private val logger: Logger,
) {

    /** Each Icons sheet's Size tab, by the sheet's tab list. */
    private val sizeTabs = WeakHashMap<View, SizeTab>()

    fun install() {
        runCatching { installOrder() }.onFailure { logger.warn("Picker: the Custom grid tile keeps the picker's order", it) }
        runCatching { installSheets() }.onFailure { logger.warn("Picker: Grid & size is not shown in Wallpaper & style", it) }
        runCatching { installTabs() }.onFailure { logger.warn("Picker: the icon Size tab is not shown", it) }
    }

    /** Keeps the Custom tile last; the picker orders Google's by cell count, largest first. */
    private fun installOrder() {
        val comparator = Class.forName(ORDER, false, classLoader)
        val compare = comparator.declaredMethods.first { it.name == "compare" && it.parameterCount == 2 }
        val keyOf = Class.forName(GRID_OPTION, false, classLoader).getDeclaredField("key").apply { isAccessible = true }
        xposed.hook(compare).intercept { chain ->
            val first = chain.args[0]?.let { keyOf.get(it) }
            val second = chain.args[1]?.let { keyOf.get(it) }
            when {
                first == GridBridge.CUSTOM && second != GridBridge.CUSTOM -> 1
                second == GridBridge.CUSTOM && first != GridBridge.CUSTOM -> -1
                else -> chain.proceed()
            }
        }
    }

    private fun installSheets() {
        val binder = Class.forName(SHEETS_BINDER, false, classLoader)
        val bind = binder.declaredMethods.first { it.name == "bind" && it.parameterTypes.any { type -> type == View::class.java } }
        xposed.hook(bind).intercept { chain ->
            chain.proceed().also {
                runCatching {
                    val roots = ArrayList<View>()
                    for (arg in chain.args) when (arg) {
                        is View -> roots += arg
                        is Map<*, *> -> arg.values.filterIsInstanceTo(roots)
                        is List<*> -> arg.filterIsInstanceTo(roots)
                    }
                    val colorModel = chain.args.firstOrNull { it?.javaClass?.name == COLOR_VIEW_MODEL }
                    val lifecycle = chain.args.firstOrNull { it?.javaClass?.name?.endsWith(LIFECYCLE_OWNER) == true } ?: return@runCatching
                    val context = roots.firstOrNull()?.context ?: return@runCatching
                    val colors = PickerColors(context, classLoader, colorModel, lifecycle, logger)
                    for (root in roots) {
                        root.findViewById<ViewGroup>(PickerPage.resource(context, "id", GRID_OPTIONS))?.let { LayoutPanel(it, colors).add() }
                        root.findViewById<View>(PickerPage.resource(context, "id", SHAPE_CONTAINER))?.let { attachSizeTab(it, colors) }
                    }
                }.onFailure { logger.warn("Picker: Grid & size could not be added to the sheets", it) }
            }
        }
    }

    private fun installTabs() {
        val adapter = Class.forName(TAB_ADAPTER, false, classLoader)
        val submit = adapter.getDeclaredMethod("submitList", List::class.java)
        xposed.hook(submit).intercept { chain ->
            val tab = synchronized(sizeTabs) { sizeTabs.entries.firstOrNull { adapterOf(it.key) === chain.thisObject }?.value }
            val list = chain.args[0] as? List<*>
            if (tab == null || list == null || tab.resubmitting) return@intercept chain.proceed()
            tab.original = list
            val decorated = runCatching { tab.decorate(list) }
                .onFailure { logger.warn("Picker: the Size tab could not be added", it) }
                .getOrNull() ?: return@intercept chain.proceed()
            chain.proceed(arrayOf<Any?>(decorated))
        }

        // static bindViewHolder(holder, icon, text, isSelected, onClick): the tab list is the holder's owner,
        // which RecyclerView sets before it binds (the view itself is not attached yet).
        val bindHolder = adapter.declaredMethods.first { it.name == "bindViewHolder" && it.parameterCount == 5 }
        val ownerOf = Class.forName(VIEW_HOLDER, false, classLoader).getDeclaredField("mOwnerRecyclerView").apply { isAccessible = true }
        xposed.hook(bindHolder).intercept { chain ->
            val list = chain.args[0]?.let { ownerOf.get(it) } as? View
            val tab = list?.let { synchronized(sizeTabs) { sizeTabs[it] } }
            val onClick = chain.args[4]
            val label = chain.args[2] as? String
            if (tab == null || label == null || tab.isOwn(onClick)) return@intercept chain.proceed()
            val args = chain.args.toTypedArray()
            args[4] = runCatching { tab.wrapped(label) }.getOrDefault(onClick)
            chain.proceed(args)
        }
    }

    // ---- Layout: Columns, Rows and spacing under the tiles ------------------------------------

    private inner class LayoutPanel(private val options: ViewGroup, private val colors: PickerColors) {

        private val context: Context = options.context
        private val client = IconsClient(context, logger)
        private val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = PANEL_TAG
            visibility = View.GONE
        }

        fun add() {
            val content = options.parent as? ViewGroup ?: return
            val sheet = content.parent as? ViewGroup ?: return
            if (sheet.findViewWithTag<View>(PANEL_TAG) != null) return
            sheet.addView(panel, sheet.indexOfChild(content) + 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            client.call(GridBridge.STATE) { state -> state?.let(::fill) }
        }

        private fun fill(state: Bundle) {
            panel.removeAllViews()
            if (!state.getBoolean(GridBridge.AVAILABLE)) return
            val padding = dp(HORIZONTAL_DP)
            panel.setPadding(padding, dp(TOP_DP), padding, 0)
            fun count(title: Int, plural: Int, key: String, from: String, to: String, current: String) {
                val range = state.getInt(from)..state.getInt(to)
                if (range.isEmpty()) return
                slider(text.getString(title), range, state.getInt(current), { it.toString() }, { text.getQuantityString(plural, it, it) }) { count, done -> set(key, count, done) }
            }
            count(R.string.feature_grid_columns_title, R.plurals.feature_grid_columns_count, Settings.GRID_COLUMNS.key, GridBridge.COLUMNS_FROM, GridBridge.COLUMNS_TO, GridBridge.COLUMNS)
            count(R.string.feature_grid_rows_title, R.plurals.feature_grid_rows_count, Settings.GRID_ROWS.key, GridBridge.ROWS_FROM, GridBridge.ROWS_TO, GridBridge.ROWS)
            fun spacing(title: Int, key: String, current: String) {
                val label = { step: Int -> text.getString(SPACING_LABELS[step - GridSpec.COMPACT]) }
                slider(text.getString(title), GridSpec.COMPACT..GridSpec.RELAXED, state.getInt(current), label, label) { step, done -> set(key, step, done) }
            }
            spacing(R.string.feature_grid_spacing_x_title, Settings.GRID_SPACING_X.key, GridBridge.SPACING_X)
            spacing(R.string.feature_grid_spacing_y_title, Settings.GRID_SPACING_Y.key, GridBridge.SPACING_Y)
            panel.addView(label(text.getString(R.string.feature_grid_custom_note), CAPTION_SP))
            shown = null
            follow()
            // The tiles are the picker's; a tap or the picker selecting one redraws them.
            panel.viewTreeObserver.addOnPreDrawListener {
                follow()
                reveal()
                true
            }
        }

        /** Whether the panel is shown; null until the first look at the tiles. */
        private var shown: Boolean? = null
        private var heightAnimator: ValueAnimator? = null

        /**
         * Shows the panel while the Custom tile is selected and hides it
         * otherwise: its sliders only ever change the custom grid. It opens
         * and closes as the sheet changes height for its own tabs.
         */
        private fun follow() {
            // A tile scrolled out of the list says nothing either way.
            val selected = custom()?.isSelected ?: shown ?: false
            if (selected == shown) return
            val first = shown == null
            shown = selected
            heightAnimator?.cancel()
            fun settle() {
                panel.visibility = if (selected) View.VISIBLE else View.GONE
                panel.alpha = 1f
                panel.layoutParams = panel.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            }
            if (first || switchMs == 0L) return settle()
            panel.measure(
                View.MeasureSpec.makeMeasureSpec((panel.parent as View).width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val from = if (panel.visibility == View.VISIBLE) panel.height else 0
            panel.visibility = View.VISIBLE
            heightAnimator = ease(panel, from, if (selected) panel.measuredHeight else 0, { panel.alpha = if (selected) it else 1f - it }) { if (it) settle() }
        }

        /** Until when a pick keeps the Custom tile in view; 0 when none does. */
        private var revealUntil = 0L

        /**
         * Brings the Custom tile back into view after a pick. A pick changes the
         * Custom tile, the picker lists its tiles again, and the list starts
         * over from the first tile, which can leave Custom half off the screen.
         */
        private fun reveal() {
            if (revealUntil == 0L) return
            if (SystemClock.uptimeMillis() > revealUntil) {
                revealUntil = 0L
                return
            }
            val tile = custom() ?: return
            val left = options.paddingLeft
            val right = options.width - options.paddingRight
            val dx = when {
                tile.right > right -> tile.right - right
                tile.left < left -> tile.left - left
                else -> return
            }
            options.scrollBy(dx, 0)
        }

        /** The Custom tile last found, checked again on each look: the picker's list rebinds its views. */
        private var customTile: View? = null

        /** The Custom tile among the picker's; read on every frame of the sheet, so it allocates nothing. */
        private fun custom(): View? {
            customTile?.let { if (it.parent === options && findText(it, customTitle)) return it }
            customTile = null
            for (index in 0 until options.childCount) {
                val tile = options.getChildAt(index)
                if (findText(tile, customTitle)) {
                    customTile = tile
                    return tile
                }
            }
            return null
        }

        private val customTitle: String by lazy { text.getString(R.string.feature_grid_custom) }

        /**
         * A heading with the chosen value at its end, over a stepped slider.
         * [pick] gets a new step once the finger lifts (or at once from a
         * keyboard or TalkBack) and answers whether it was kept; a refused one
         * puts the slider back.
         */
        private fun slider(
            title: String,
            range: IntRange,
            current: Int,
            label: (Int) -> String,
            spoken: (Int) -> String,
            pick: (Int, (Boolean) -> Unit) -> Unit,
        ) {
            val value = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADING_SP)
                colors.paint(this, "colorOnSurface", ::setTextColor)
            }
            panel.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(label(title, HEADING_SP), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(value)
            })
            var kept = current.coerceIn(range)
            lateinit var control: PickerSlider
            fun show(step: Int) {
                value.text = label(step)
                control.view.stateDescription = spoken(step)
            }
            control = PickerSlider.create(
                context, classLoader, colors, logger, range, kept,
                moved = { step, dragging ->
                    show(step)
                    if (dragging) control.view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                },
                released = released@{ step ->
                    if (step == kept) return@released
                    pick(step) { ok ->
                        if (ok) {
                            kept = step
                            control.view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        } else {
                            control.set(kept)
                            show(kept)
                        }
                    }
                },
            )
            control.view.contentDescription = title
            show(kept)
            panel.addView(control.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        private fun set(key: String, value: Int, done: (Boolean) -> Unit) {
            val extras = Bundle().apply {
                putString(GridBridge.KEY, key)
                putInt(GridBridge.VALUE, value)
            }
            client.call(GridBridge.SET, extras = extras) { answer ->
                val ok = answer?.getBoolean(GridBridge.OK) == true
                if (ok) revealUntil = SystemClock.uptimeMillis() + REVEAL_MS
                if (!ok) {
                    options.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    answer?.getString(GridBridge.MESSAGE)?.let { message ->
                        AlertDialog.Builder(context)
                            .setTitle(text.getString(R.string.feature_grid_misfit_title))
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
                done(ok)
            }
        }

        private fun label(label: String, sp: Float) = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            colors.paint(this, "colorOnSurfaceVariant", ::setTextColor)
            setPadding(0, dp(HEADING_GAP_DP), 0, dp(HEADING_GAP_DP))
        }

        private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    }

    // ---- Icons: a Size tab beside Shape ---------------------------------------------------------

    /**
     * The Size tab of one Icons sheet.
     *
     * The sheet's tabs are the picker's own view models
     * (`FloatingToolbarTabViewModel(icon, text, isSelected, onClick)`), so the
     * Size tab is one more, built the same way and put after Shape. While it is
     * selected the sheet's own panes are hidden and the size tiles shown, every
     * other tab reads unselected, and tapping one gives the sheet back.
     */
    private inner class SizeTab(private val content: ViewGroup, private val tabList: ViewGroup, private val colors: PickerColors) {

        private val context: Context = content.context
        var original: List<*> = emptyList<Any>()
        var resubmitting = false
        private var selected = false
        private val hidden = HashMap<View, Int>()
        private val pane = sizePane()
        private val tabModel = Class.forName(TAB_MODEL, false, classLoader)
        private val iconOf: Field = tabModel.field("icon")
        private val textOf: Field = tabModel.field("text")
        private val selectedOf: Field = tabModel.field("isSelected")
        private val clickOf: Field = tabModel.field("onClick")
        // The picker's own Function0, from the field that holds one (a class name string
        // would be rewritten by R8 to this module's renamed copy).
        private val function0: Class<*> = clickOf.type

        fun decorate(list: List<*>): List<Any?> {
            val tabs = ArrayList<Any?>(list.size + 1)
            for (tab in list) tabs += if (selected && tab != null) copy(tab, false, clickOf.get(tab)) else tab
            tabs.add(minOf(SIZE_TAB_INDEX, tabs.size), ownTab())
            return tabs
        }

        private fun ownTab(): Any {
            val icon = Class.forName(LOADED_ICON, false, classLoader)
            val label = text.getString(R.string.feature_grid_size_tab)
            val description = Class.forName(LOADED_TEXT, false, classLoader).getConstructor(String::class.java).newInstance(label)
            val glyph = text.getDrawable(R.drawable.ic_grid_icon_size, null)
            val model = icon.declaredConstructors.first { it.parameterCount == 2 }.newInstance(glyph, description)
            return tabModel.declaredConstructors.first { it.parameterCount == 4 }
                .newInstance(model, label, selected, ownClick)
        }

        private val ownClick: Any by lazy { function { enter() } }

        private fun copy(tab: Any, isSelected: Boolean, onClick: Any?): Any =
            tabModel.declaredConstructors.first { it.parameterCount == 4 }
                .newInstance(iconOf.get(tab), textOf.get(tab), isSelected, onClick)

        /** Whether [onClick] is the Size tab's own. */
        fun isOwn(onClick: Any?): Boolean = onClick != null && onClick === ownClick

        /**
         * A stock tab's action as bound to its view: gives the sheet back first
         * while Size is shown, then runs the tab's action as the picker lists it
         * now. The tab list rebinds a view only when its label or selection
         * changes, never its click, so a click kept from an earlier list can be
         * stale: the selected tab's is null, and a tab shown unselected while
         * Size is up is never rebound once the picker gives it an action.
         */
        fun wrapped(label: String): Any = function {
            val tab = original.firstOrNull { it != null && textOf.get(it) == label }
            if (selected) leave(toOwnPane = tab == null || selectedOf.getBoolean(tab))
            // The picker's selected tab has no action of its own.
            tab?.let { clickOf.get(it) }?.let { it.javaClass.getMethod("invoke").invoke(it) }
        }

        private var savedHeight = ViewGroup.LayoutParams.WRAP_CONTENT
        private var heightAnimator: ValueAnimator? = null

        /**
         * Shows the size tiles in place of the sheet's panes, as the sheet
         * moves between its own tabs (`FloatingSheetHeightAnimationBinder`):
         * the height eases to the new pane's over [SWITCH_MS] while the old
         * pane fades out, and the new pane appears as it ends.
         */
        private fun enter() {
            if (selected) return
            selected = true
            heightAnimator?.cancel()
            pane.animate().cancel()
            val shown = ArrayList<View>()
            for (index in 0 until content.childCount) {
                val child = content.getChildAt(index)
                if (child === pane) continue
                hidden[child] = child.visibility
                if (child.visibility == View.VISIBLE) shown += child
            }
            savedHeight = content.layoutParams.height
            pane.measure(
                View.MeasureSpec.makeMeasureSpec(content.width - content.paddingLeft - content.paddingRight, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            switch(pane.measuredHeight + content.paddingTop + content.paddingBottom, shown) {
                for (view in hidden.keys) view.visibility = View.GONE
                pane.alpha = 1f
                pane.visibility = View.VISIBLE
            }
            resubmit()
        }

        /**
         * Gives the sheet back.
         *
         * To another of the sheet's tabs, the picker animates on its own, from
         * the pane it showed before Size to the one picked: that pane is shown
         * again but kept unseen ([View.setTransitionAlpha], which the picker's
         * fade does not touch) while Size fades out in its place. Back to the
         * tab the picker still has selected ([toOwnPane]) nothing moves in the
         * picker, so the same animation is run here.
         */
        private fun leave(toOwnPane: Boolean) {
            selected = false
            heightAnimator?.cancel()
            pane.animate().cancel()
            val restore = HashMap(hidden)
            hidden.clear()
            if (toOwnPane) {
                switch(savedHeight, listOf(pane)) {
                    pane.visibility = View.GONE
                    pane.alpha = 1f
                    restore.forEach { (view, visibility) -> view.visibility = visibility }
                }
            } else {
                val previous = restore.filterValues { it == View.VISIBLE }.keys
                for (view in previous) view.transitionAlpha = 0f
                restore.forEach { (view, visibility) -> view.visibility = visibility }
                pane.animate().alpha(0f).setDuration(switchMs).withEndAction {
                    pane.visibility = View.GONE
                    pane.alpha = 1f
                }.start()
                // After the picker's own switch has hidden that pane again.
                content.postDelayed({ for (view in previous) view.transitionAlpha = 1f }, switchMs + SETTLE_MS)
            }
            resubmit()
        }

        /** Eases the content's height to [to], fading [from] out on the way, then runs [done]. */
        private fun switch(to: Int, from: List<View>, done: () -> Unit) {
            val start = content.height
            if (switchMs == 0L || start == 0 || to < 0) {
                content.layoutParams = content.layoutParams.apply { height = to }
                done()
                return
            }
            heightAnimator = ease(content, start, to, { fraction -> for (view in from) view.alpha = 1f - fraction }) { completed ->
                for (view in from) view.alpha = 1f
                if (completed) done()
            }
        }

        fun resubmit() {
            val adapter = adapterOf(tabList) ?: return
            resubmitting = true
            try {
                adapter.javaClass.getMethod("submitList", List::class.java).invoke(adapter, decorate(original))
            } finally {
                resubmitting = false
            }
        }

        /** The four icon sizes as the picker's option tiles, each a glyph of that size. */
        private fun sizePane(): View {
            val views = PickerViews(colors)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val labels = intArrayOf(
                R.string.feature_grid_icon_small,
                R.string.feature_grid_icon_default,
                R.string.feature_grid_icon_large,
                R.string.feature_grid_icon_extra_large,
            )
            val tiles = HashMap<Int, PickerViews.Tile>()
            val client = IconsClient(context, logger)
            GridSpec.ICON_SIZES.forEachIndexed { index, size ->
                val tile = views.tile(row, null, text.getString(labels[index])) ?: return@forEachIndexed
                tile.glyph(SizeDrawable(size.toFloat() / GridSpec.ICON_SIZES.last(), colors.now("colorOnSurface"), STROKE_DP * context.resources.displayMetrics.density))
                tile.view.setOnClickListener { view ->
                    val extras = Bundle().apply {
                        putString(GridBridge.KEY, Settings.GRID_ICON_SIZE.key)
                        putInt(GridBridge.VALUE, size)
                    }
                    client.call(GridBridge.SET, extras = extras) { answer ->
                        if (answer?.getBoolean(GridBridge.OK) != true) return@call
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        tiles.forEach { (each, other) -> other.select(each == size, animate = true) }
                    }
                }
                tiles[size] = tile
                row.addView(tile.view)
            }
            client.call(GridBridge.STATE) { state ->
                val current = state?.getInt(GridBridge.ICON_SIZE) ?: GridSpec.ICON_DEFAULT
                tiles.forEach { (each, tile) -> tile.select(each == current, animate = false) }
            }
            row.visibility = View.GONE
            content.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return row
        }

        private fun function(body: () -> Unit): Any = pickerProxy(function0) { name, _ -> if (name == "invoke") body(); null }
    }

    /** Adds the Size tab to the Icons sheet holding [shapes], once per sheet. */
    private fun attachSizeTab(shapes: View, colors: PickerColors) {
        val context = shapes.context
        val contentId = PickerPage.resource(context, "id", CONTENT)
        var content: View? = shapes
        while (content != null && content.id != contentId) content = content.parent as? View
        content ?: return
        // The sheet's toolbar sits beside its content, a level or two up.
        val tabListId = PickerPage.resource(context, "id", TAB_LIST)
        var ancestor = content.parent as? ViewGroup
        var tabList: ViewGroup? = null
        while (ancestor != null && tabList == null) {
            tabList = ancestor.findViewById(tabListId)
            ancestor = ancestor.parent as? ViewGroup
        }
        tabList ?: return
        if (synchronized(sizeTabs) { sizeTabs.containsKey(tabList) }) return
        val tab = SizeTab(content as ViewGroup, tabList, colors)
        synchronized(sizeTabs) { sizeTabs[tabList] = tab }
        // The picker may have listed its tabs already; list them again with Size.
        adapterOf(tabList)?.let { adapter ->
            val current = runCatching { adapter.javaClass.getMethod("getCurrentList").invoke(adapter) as? List<*> }.getOrNull()
            if (!current.isNullOrEmpty()) {
                tab.original = current
                tab.resubmit()
            }
        }
    }

    /** The sheet's own tab switch, or none while animations are off. */
    private val switchMs: Long get() = if (ValueAnimator.areAnimatorsEnabled()) SWITCH_MS else 0L

    /**
     * Eases [view]'s height from [from] to [to] as the sheet does between its
     * tabs, handing [fade] the progress, then [done] whether it ran to the end.
     */
    private fun ease(view: View, from: Int, to: Int, fade: (Float) -> Unit, done: (Boolean) -> Unit): ValueAnimator =
        ValueAnimator.ofInt(from, to).apply {
            duration = SWITCH_MS
            addUpdateListener { animator ->
                view.layoutParams = view.layoutParams.apply { height = animator.animatedValue as Int }
                fade(animator.animatedFraction)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) = done(!cancelled)
            })
            start()
        }

    /** A RecyclerView's adapter; the picker's build keeps the field but not `getAdapter`. */
    private fun adapterOf(list: View): Any? = runCatching {
        generateSequence(list.javaClass as Class<*>?) { it.superclass }
            .firstNotNullOf { type -> runCatching { type.getDeclaredField("mAdapter") }.getOrNull() }
            .apply { isAccessible = true }
            .get(list)
    }.getOrNull()

    private fun Class<*>.field(name: String): Field = getDeclaredField(name).apply { isAccessible = true }

    private fun findText(view: View, label: String): Boolean {
        if (view is TextView) return android.text.TextUtils.equals(view.text, label)
        if (view is ViewGroup) for (index in 0 until view.childCount) if (findText(view.getChildAt(index), label)) return true
        return false
    }

    /**
     * The glyph of an icon size tile: the icon as a filled circle [share] of
     * the largest size, inside that largest size's outline, so each tile shows
     * its size against the same frame.
     */
    private class SizeDrawable(private val share: Float, color: Int, private val stroke: Float) : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = FRAME_ALPHA
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }

        override fun draw(canvas: Canvas) {
            val box = bounds
            val largest = minOf(box.width(), box.height()) / 2f - stroke
            canvas.drawCircle(box.exactCenterX(), box.exactCenterY(), largest, frame)
            canvas.drawCircle(box.exactCenterX(), box.exactCenterY(), largest * share, fill)
        }

        override fun setTintList(tint: android.content.res.ColorStateList?) {
            tint?.let {
                fill.color = it.defaultColor
                frame.color = it.defaultColor
                frame.alpha = FRAME_ALPHA
                invalidateSelf()
            }
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private companion object {
        const val ORDER =
            "com.android.customization.model.grid.DefaultShapeGridManager\$getGridOptions\$lambda\$7\$\$inlined\$sortedByDescending\$1"
        const val GRID_OPTION = "com.android.customization.model.grid.GridOptionModel"
        const val SHEETS_BINDER = "com.android.wallpaper.customization.ui.binder.ThemePickerCustomizationOptionsBinder"
        const val COLOR_VIEW_MODEL = "com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel"
        const val LIFECYCLE_OWNER = "LifecycleOwner"
        const val TAB_ADAPTER = "com.android.wallpaper.picker.customization.ui.view.adapter.FloatingToolbarTabAdapter"
        const val TAB_MODEL = "com.android.wallpaper.picker.customization.ui.viewmodel.FloatingToolbarTabViewModel"
        const val LOADED_ICON = "com.android.wallpaper.picker.common.icon.ui.viewmodel.Icon\$Loaded"
        const val LOADED_TEXT = "com.android.wallpaper.picker.common.text.ui.viewmodel.Text\$Loaded"
        const val VIEW_HOLDER = "androidx.recyclerview.widget.RecyclerView\$ViewHolder"
        const val GRID_OPTIONS = "grid_options"
        const val SHAPE_CONTAINER = "app_shape_container"
        const val CONTENT = "floating_sheet_content_container"
        const val TAB_LIST = "tab_list"
        const val PANEL_TAG = "pixel_launcher_evolved_grid_panel"

        /** Size goes after Style and Shape. */
        const val SIZE_TAB_INDEX = 2

        /** The sheet's own tab switch (`FloatingSheetHeightAnimationBinder`), and a frame or two after it. */
        const val SWITCH_MS = 200L
        const val SETTLE_MS = 50L

        /** How long after a pick the Custom tile is kept in view, while the picker lists its tiles again. */
        const val REVEAL_MS = 1_500L

        /** The size glyphs' outline of the largest size, and its opacity. */
        const val STROKE_DP = 1.5f
        const val FRAME_ALPHA = 0x66

        /** Compact, Default and Relaxed, by spacing step from [GridSpec.COMPACT]. */
        val SPACING_LABELS = intArrayOf(
            R.string.feature_grid_spacing_compact,
            R.string.feature_grid_spacing_default,
            R.string.feature_grid_spacing_relaxed,
        )

        const val HORIZONTAL_DP = 24
        const val TOP_DP = 8
        const val HEADING_GAP_DP = 8
        const val HEADING_SP = 14f
        const val CAPTION_SP = 12f
    }
}
