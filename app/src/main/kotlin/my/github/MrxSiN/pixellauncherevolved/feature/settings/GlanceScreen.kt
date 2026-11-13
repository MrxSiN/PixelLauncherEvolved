package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlancePrefs
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSources
import my.github.MrxSiN.pixellauncherevolved.glance.CalendarAccessActivity
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsStore

/** What the At a Glance screen and its sheets need: a themed activity context and this module's strings. */
internal class GlanceUi(val context: Context, val resources: Resources, val modulePackage: String, val logger: Logger) {
    fun string(@StringRes id: Int, vararg args: Any): String = resources.getString(id, *args)

    fun drawable(id: Int): Drawable? = runCatching { resources.getDrawable(id, context.theme) }.getOrNull()
}

/**
 * At a Glance's settings, built the way Android's Material 3 Expressive settings are:
 *
 * - a live, swipeable preview at the top, with **Live** and **Try** ([GlancePreviewView]);
 * - **Cards** and **Extras** as toggle tiles, two to a row, each with its icon and a short state
 *   ("Off", "On", "Charging"). A tap turns one on or off; its sliders button opens its own sheet
 *   ([GlanceSourceDialog]) with when it shows and its options. **Reorder** sits beside each heading;
 * - **Every home screen**, the placement switch, as a card of its own;
 * - a calendar note only while a calendar source is on and this module's app cannot read it.
 *
 * Every change is stored at once, the content feature applies it live, and the preview follows.
 * Opened from Home settings and from **Customize** in the menu a long press on At a Glance opens.
 */
internal class GlanceScreen(private val ui: GlanceUi, private val settings: SettingsStore) {

    private val context = ui.context
    private val preferences = LauncherSettings.preferences(context)
    private lateinit var column: LinearLayout
    private lateinit var calendarNote: View
    private val counters = HashMap<GlanceSource.Kind, TextView>()
    private lateinit var preview: GlancePreviewView

    /** The preview inside a strip as wide as the screen, which is what pins and what the band fills. */
    private lateinit var holder: android.widget.FrameLayout

    fun show() {
        column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), 0, dp(16f), 0)
            preview = GlancePreviewView(ui)
            holder = android.widget.FrameLayout(context).apply {
                setPadding(dp(16f), 0, dp(16f), 0)
                // Drawn above the rows it is pinned over while the screen scrolls.
                translationZ = dp(4f).toFloat()
                addView(preview)
            }
            preview.bandOn(holder)
            // Out over this column's side padding, so the strip spans the screen: children are
            // clipped to the padding unless told otherwise.
            clipChildren = false
            clipToPadding = false
            addView(holder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = -dp(16f)
                marginEnd = -dp(16f)
            })
            calendarNote = buildCalendarNote()
            addView(calendarNote, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f) })
            section(this, GlanceSource.Kind.CARD, R.string.glance_section_cards)
            section(this, GlanceSource.Kind.EXTRA, R.string.glance_section_extras)
            addView(heading(R.string.glance_section_where, null))
            addView(placementCard())
            addView(resetButton(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(28f)
            })
        }
        updateCalendarNote(animate = false)

        ExpressivePage(context)
            .title(ui.string(R.string.settings_page_glance))
            .description(ui.string(R.string.glance_screen_description))
            .backLabel(ui.string(R.string.app_labels_back))
            .content(column)
            .show()
        enter()
        pinPreview()
    }

    /**
     * Keeps the preview's panel in view while the screen scrolls: once its top would pass under the
     * status bar, it is moved down by as much, so the tiles scroll beneath it and every change can be
     * watched as it is made. It lets go again when scrolled back up.
     */
    private fun pinPreview() {
        var scroll: View? = column
        while (scroll != null && scroll !is android.widget.ScrollView) scroll = scroll.parent as? View
        val pager = scroll as? android.widget.ScrollView ?: return
        pager.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            runCatching {
                var top = 0
                var at: View? = holder
                while (at != null && at !== pager) {
                    top += at.top
                    at = at.parent as? View
                }
                val edge = scrollY + pager.paddingTop
                // Never past the end of the screen's own column.
                val room = (column.height - holder.top - preview.panelHeight).coerceAtLeast(0)
                val shift = (edge - top).coerceIn(0, room)
                holder.translationY = shift.toFloat()
                preview.setPinned(shift > 0)
            }.onFailure { ui.logger.warn("The At a Glance preview could not stay in view", it) }
        }
    }

    /** "Reset At a Glance": every card and extra off, every choice back to its default, after asking. */
    private fun resetButton(): View = TextView(context).apply {
        text = ui.string(R.string.glance_reset)
        ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.PRIMARY)
        gravity = Gravity.CENTER
        minHeight = dp(48f)
        setPadding(dp(24f), 0, dp(24f), 0)
        background = Surface(context, 0, dp(24f).toFloat(), ExpressiveRole.OUTLINE_VARIANT.of(context)).drawable
        isClickable = true
        isFocusable = true
        GlanceMotion.pressable(this, 0.95f)
        setOnClickListener {
            ExpressiveDialog(context)
                .icon(ui.drawable(R.drawable.ic_glance_tune))
                .title(ui.string(R.string.glance_reset_title))
                .message(ui.string(R.string.glance_reset_message))
                .confirm(ui.string(R.string.glance_reset_confirm)) {
                    GlancePrefs(preferences).replace(my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceChoices())
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    rebuild(GlanceSource.Kind.CARD)
                    rebuild(GlanceSource.Kind.EXTRA)
                    updateCalendarNote(animate = true)
                }
                .dismiss(context.getString(android.R.string.cancel))
                .show()
        }
    }

    /** Each block rises into place one after another as the screen opens. */
    private fun enter() {
        for (i in 0 until column.childCount) {
            val child = column.getChildAt(i)
            child.alpha = 0f
            if (child !== holder) child.translationY = dp(28f).toFloat()
            child.animate().alpha(1f).apply { if (child !== holder) translationY(0f) }.setStartDelay(120L + i * 45L)
                .setDuration(GlanceMotion.LONG).setInterpolator(GlanceMotion.ENTER).start()
        }
    }

    // ---- Sections ----------------------------------------------------------------------------

    private fun heading(@StringRes title: Int, kind: GlanceSource.Kind?): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8f), dp(28f), 0, dp(12f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = ui.string(title)
                ExpressiveType.TITLE_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
            })
            if (kind != null) {
                addView(TextView(context).apply {
                    ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
                    counters[kind] = this
                })
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (kind != null) addView(reorderButton(kind))
    }

    /** A tonal pill: icon and "Reorder". */
    private fun reorderButton(kind: GlanceSource.Kind): View {
        val surface = Surface(context, ExpressiveRole.SECONDARY_CONTAINER.of(context), dp(20f).toFloat())
        val ink = ExpressiveRole.ON_SECONDARY_CONTAINER.of(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = surface.drawable
            setPadding(dp(14f), 0, dp(18f), 0)
            minimumHeight = dp(40f)
            isClickable = true
            isFocusable = true
            addView(ImageView(context).apply {
                setImageDrawable(ui.drawable(R.drawable.ic_glance_reorder)?.mutate())
                imageTintList = ColorStateList.valueOf(ink)
            }, LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(8f) })
            addView(TextView(context).apply {
                text = ui.string(R.string.glance_reorder)
                ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
                setTextColor(ink)
            })
            setOnClickListener { order(kind) }
            GlanceMotion.pressable(this, 0.94f)
        }
    }

    private fun section(column: LinearLayout, kind: GlanceSource.Kind, @StringRes title: Int) {
        column.addView(heading(title, kind))
        val tiles = GlancePrefs.ordered(GlanceSources.all, GlancePrefs(preferences).order()) { it.id }.filter { it.kind == kind }.map { tile(it) }
        column.addView(tileGrid(context, tiles))
        count(kind)
    }

    private fun count(kind: GlanceSource.Kind) {
        val on = GlanceSources.all.count { it.kind == kind && preferences.getBoolean(GlanceSources.key(it), false) }
        counters[kind]?.text = if (on == 0) ui.string(R.string.glance_count_none) else ui.resources.getQuantityString(R.plurals.glance_count_on, on, on)
    }

    /** A source's tile: tap turns it on or off, the sliders open its sheet. */
    private fun tile(source: GlanceSource): GlanceTile {
        val key = GlanceSources.key(source)
        // Shown but unavailable when Pixel Launcher lacks what the source reads.
        val allowed = source.permission?.let { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } ?: true
        val icon = ui.drawable(iconOf(source.id))
        lateinit var tile: GlanceTile

        fun status(on: Boolean): CharSequence = when {
            !allowed -> ui.string(R.string.glance_tile_unavailable)
            !on -> ui.string(R.string.glance_sheet_off)
            else -> GlanceSourceDialog.summary(ui, source) ?: ui.string(R.string.glance_sheet_on)
        }

        fun refresh(animate: Boolean) {
            val on = allowed && preferences.getBoolean(key, false)
            tile.set(on, status(on), animate)
            count(source.kind)
            updateCalendarNote(animate)
        }

        tile = GlanceTile(context, icon, ui.string(source.titleRes)) {
            GlanceSourceDialog.open(ui, source, ui.drawable(iconOf(source.id))) { refresh(animate = true) }
        }
        tile.tuneIcon(ui.drawable(R.drawable.ic_glance_tune), ui.string(R.string.glance_tile_settings, ui.string(source.titleRes)))
        tile.setOnClickListener {
            if (!allowed) {
                GlanceSourceDialog.open(ui, source, ui.drawable(iconOf(source.id))) { refresh(animate = true) }
                return@setOnClickListener
            }
            val on = !preferences.getBoolean(key, false)
            preferences.edit().putBoolean(key, on).apply()
            tile.performHapticFeedback(if (on) android.view.HapticFeedbackConstants.TOGGLE_ON else android.view.HapticFeedbackConstants.TOGGLE_OFF)
            refresh(animate = true)
        }
        tile.setOnLongClickListener {
            tile.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            GlanceSourceDialog.open(ui, source, ui.drawable(iconOf(source.id))) { refresh(animate = true) }
            true
        }
        if (!allowed) tile.alpha = UNAVAILABLE
        refresh(animate = false)
        return tile
    }

    /** "Every home screen": the placement switch, as a card with a short line under its name. */
    private fun placementCard(): View {
        val entry = FeatureCatalog.GLANCE_ALL_SCREENS
        val surface = Surface(context, ExpressiveRole.SURFACE_BRIGHT.of(context), dp(28f).toFloat())
        val toggle = Switch(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = surface.drawable
            setPadding(dp(20f), dp(18f), dp(16f), dp(18f))
            isClickable = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = ui.string(R.string.glance_where_all_title)
                    ExpressiveType.TITLE_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE)
                })
                addView(TextView(context).apply {
                    text = ui.string(R.string.glance_where_all_summary)
                    ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle.apply {
                isChecked = entry.shown(settings[entry.setting])
                setOnCheckedChangeListener { _, on -> settings.put(entry.setting, entry.stored(on)) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(12f) })
            setOnClickListener { toggle.toggle() }
            GlanceMotion.pressable(this, 0.98f)
        }
    }

    // ---- Calendar ----------------------------------------------------------------------------

    /** A tertiary note, shown only while a calendar source is on and the module app may not read the calendar. */
    private fun buildCalendarNote(): View {
        val surface = Surface(context, ExpressiveRole.TERTIARY_CONTAINER.of(context), dp(28f).toFloat())
        val ink = ExpressiveRole.ON_TERTIARY_CONTAINER.of(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = surface.drawable
            setPadding(dp(20f), dp(16f), dp(20f), dp(16f))
            isClickable = true
            addView(ImageView(context).apply {
                setImageDrawable(ui.drawable(R.drawable.ic_glance_card_calendar)?.mutate())
                imageTintList = ColorStateList.valueOf(ink)
            }, LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { marginEnd = dp(16f) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = ui.string(R.string.glance_calendar_note_title)
                    ExpressiveType.TITLE_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE)
                    setTextColor(ink)
                })
                addView(TextView(context).apply {
                    text = ui.string(R.string.glance_calendar_note_summary)
                    ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE)
                    setTextColor(ink)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener {
                context.startActivity(
                    Intent().setComponent(ComponentName(ui.modulePackage, CalendarAccessActivity::class.java.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            GlanceMotion.pressable(this, 0.97f)
        }
    }

    private fun updateCalendarNote(animate: Boolean) {
        if (!::calendarNote.isInitialized) return
        val wanted = CALENDAR_SOURCES.any { id: String -> preferences.getBoolean(GlanceSources.KEY_PREFIX + id, false) }
        val granted = context.packageManager.checkPermission(Manifest.permission.READ_CALENDAR, ui.modulePackage) == PackageManager.PERMISSION_GRANTED
        val shown = wanted && !granted
        if ((calendarNote.visibility == View.VISIBLE) == shown) return
        if (animate) TransitionManager.beginDelayedTransition(column, AutoTransition().setDuration(GlanceMotion.MEDIUM).setInterpolator(GlanceMotion.MOVE))
        calendarNote.visibility = if (shown) View.VISIBLE else View.GONE
    }

    // ---- Order -------------------------------------------------------------------------------

    /**
     * The sources of [kind] as a list to drag into order: hold a row by its handle and move it; the others slide out of its way. Done stores `glance_order` and
     * slides the tiles into their new places. TalkBack gets Move up and Move down on each row.
     */
    private fun order(kind: GlanceSource.Kind) {
        val prefs = GlancePrefs(preferences)
        val all = GlancePrefs.ordered(GlanceSources.all, prefs.order()) { it.id }
        val mine = all.filter { it.kind == kind }.toMutableList()
        val list = DragList(context, mine.map { reorderRow(it) }, ui.string(R.string.glance_order_up), ui.string(R.string.glance_order_down)) { from, to -> mine.add(to, mine.removeAt(from)) }

        ExpressiveDialog(context)
            .icon(ui.drawable(R.drawable.ic_glance_reorder))
            .title(ui.string(R.string.glance_reorder))
            .message(ui.string(R.string.glance_reorder_hint))
            .content(list)
            .confirm(ui.string(R.string.glance_order_done)) {
                // The other kind keeps its own order; only this kind's places change.
                prefs.setOrder((mine + all.filter { it.kind != kind }).map { it.id })
                rebuild(kind)
            }
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    /** One row to drag: a handle, the source's icon and name, and whether it is on. */
    private fun reorderRow(source: GlanceSource): View {
        val on = preferences.getBoolean(GlanceSources.key(source), false)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64f)
            setPadding(dp(8f), 0, dp(20f), 0)
            background = Surface(context, ExpressiveRole.SURFACE_BRIGHT.of(context), dp(20f).toFloat()).drawable
            addView(ImageView(context).apply {
                setImageDrawable(ui.drawable(R.drawable.ic_glance_drag)?.mutate())
                imageTintList = ColorStateList.valueOf(ExpressiveRole.ON_SURFACE_VARIANT.of(context))
                setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
                tag = DragList.HANDLE
            }, LinearLayout.LayoutParams(dp(48f), dp(48f)))
            addView(ImageView(context).apply {
                setImageDrawable(ui.drawable(iconOf(source.id))?.mutate())
                imageTintList = ColorStateList.valueOf(if (on) ExpressiveRole.PRIMARY.of(context) else ExpressiveRole.ON_SURFACE_VARIANT.of(context))
            }, LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { marginEnd = dp(16f) })
            addView(TextView(context).apply {
                text = ui.string(source.titleRes)
                ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = ui.string(if (on) R.string.glance_sheet_on else R.string.glance_sheet_off)
                ExpressiveType.LABEL_MEDIUM.applyTo(this, if (on) ExpressiveRole.PRIMARY else ExpressiveRole.ON_SURFACE_VARIANT)
            })
            contentDescription = ui.string(source.titleRes)
        }
    }

    /** Lays [kind]'s tiles out again in the stored order, sliding them into their new places. */
    private fun rebuild(kind: GlanceSource.Kind) {
        val heading = counters[kind]?.parent?.parent as? View ?: return
        val at = column.indexOfChild(heading) + 1
        TransitionManager.beginDelayedTransition(column, AutoTransition().setDuration(GlanceMotion.MEDIUM).setInterpolator(GlanceMotion.MOVE))
        column.removeViewAt(at)
        val tiles = GlancePrefs.ordered(GlanceSources.all, GlancePrefs(preferences).order()) { it.id }.filter { it.kind == kind }.map { tile(it) }
        column.addView(tileGrid(context, tiles), at)
    }

    private fun dp(value: Float): Int = context.dpOf(value)

    companion object {
        private const val UNAVAILABLE = 0.55f
        private val CALENDAR_SOURCES = setOf("calendar", "calendar_next")

        /** Each source's tile icon, by its stable id; a source without one shows the date icon. */
        fun iconOf(id: String): Int = when (id) {
            "torch" -> R.drawable.ic_glance_torch
            "calendar", "calendar_next" -> R.drawable.ic_glance_card_calendar
            "date", "date_short" -> R.drawable.ic_glance_card_date
            "clock", "time" -> R.drawable.ic_glance_card_clock
            "greeting" -> R.drawable.ic_glance_greeting
            "notifications" -> R.drawable.ic_glance_notification
            "widget" -> R.drawable.ic_glance_widget
            "alarm" -> R.drawable.ic_glance_alarm
            "battery" -> R.drawable.ic_glance_extra_battery
            else -> if (id.startsWith("wellbeing")) R.drawable.ic_glance_more_wellbeing else R.drawable.ic_glance_card_date
        }
    }
}
