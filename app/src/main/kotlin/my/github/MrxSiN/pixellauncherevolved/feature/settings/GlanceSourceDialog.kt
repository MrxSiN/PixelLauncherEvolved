package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPagesDialog
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOption
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOptions
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSources
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceWhen
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.CalendarBridge
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more.NotificationPick
import my.github.MrxSiN.pixellauncherevolved.glance.CalendarList
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * One At a Glance source's own sheet: its icon, name and what it shows; a main switch; "When to
 * show" as a row of chips ([GlanceWhen], `glance_when_<id>`, absent is always); then its options
 * ([GlanceOption]; Hide after is on every source), stored as `glance_option_<id>_<name>`, absent the
 * default. Everything is stored the moment it changes, so the preview behind follows along. The
 * calendar names are read from this module's app off the main thread before the sheet opens.
 */
internal object GlanceSourceDialog {

    /** Opens [source]'s sheet; [onClosed] runs when it is dismissed. */
    fun open(ui: GlanceUi, source: GlanceSource, icon: Drawable?, onClosed: () -> Unit) {
        val preferences = LauncherSettings.preferences(ui.context)
        val options = GlanceOptions.of(source)
        if (options.any { it.type == GlanceOption.Type.CALENDAR }) {
            FocusPagesDialog.readThen(ui.context, { CalendarBridge.calendars(ui.context.contentResolver, ui.modulePackage, ui.logger) }) { calendars ->
                show(ui, preferences, source, icon, options, calendars, onClosed)
            }
        } else {
            show(ui, preferences, source, icon, options, null, onClosed)
        }
    }

    /** What is set for [source] besides the defaults, in a few words: when it shows, then options. */
    fun summary(ui: GlanceUi, source: GlanceSource): String? {
        val preferences = LauncherSettings.preferences(ui.context)
        val set = ArrayList<String>()
        preferences.getString(GlanceWhen.key(source), null)?.let { set += whenText(ui, it) }
        for (option in GlanceOptions.of(source)) {
            val value = preferences.getString(GlanceOptions.key(source.id, option.name), null)?.takeIf { it.isNotEmpty() } ?: continue
            set += when (option.type) {
                GlanceOption.Type.TEXT -> value
                GlanceOption.Type.CALENDAR -> ui.string(R.string.glance_option_calendars_one)
                GlanceOption.Type.APPS -> ui.string(R.string.glance_notification_apps_count, NotificationPick.apps(value).size)
                GlanceOption.Type.WIDGET -> GlanceWidgetOption.label(ui.context, value)?.toString()?.substringBefore(" (") ?: continue
                GlanceOption.Type.CHOICE -> choiceText(ui, option, value)?.let { if (option.name == GlanceOptions.HIDE_AFTER) ui.string(R.string.glance_short_hide, it) else it } ?: continue
            }
        }
        return if (set.isEmpty()) null else set.joinToString(" · ")
    }

    /** A "When to show" value as shown: a word or two, or a custom time's range and days. */
    fun whenText(ui: GlanceUi, value: String?): String {
        val custom = GlanceWhen.parseCustom(value)
        if (custom < 0) return ui.string(shortLabel(value))
        val range = ui.string(R.string.glance_custom_range, clock(ui.context, GlanceWhen.customStart(custom)), clock(ui.context, GlanceWhen.customEnd(custom) % (24 * 60)))
        val days = daysText(ui, GlanceWhen.customDays(custom)) ?: return range
        return ui.string(R.string.glance_custom_range_days, range, days)
    }

    /** A minute of the day in the phone's 12- or 24-hour format. */
    fun clock(context: Context, minute: Int): String {
        val time = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, minute / 60)
            set(java.util.Calendar.MINUTE, minute % 60)
        }
        return android.text.format.DateFormat.getTimeFormat(context).format(time.time)
    }

    /** Days as words: null for every day, "Weekdays", "Weekends", or the short day names. */
    private fun daysText(ui: GlanceUi, days: Int): String? = when (days) {
        GlanceWhen.EVERY_DAY -> null
        WEEKDAYS -> ui.string(R.string.glance_days_weekdays)
        WEEKENDS -> ui.string(R.string.glance_days_weekends)
        else -> (0 until 7).filter { days and (1 shl it) != 0 }.joinToString(", ") { dayName(it, short = true) }
    }

    private fun dayName(day: Int, short: Boolean): String {
        val symbols = java.text.DateFormatSymbols.getInstance()
        return (if (short) symbols.shortWeekdays else symbols.weekdays)[java.util.Calendar.SUNDAY + day]
    }

    /** A choice's words: its label, or the custom number read through its plural. */
    private fun choiceText(ui: GlanceUi, option: GlanceOption, value: String?): String? {
        if (value == null) return null
        option.choices.firstOrNull { it.value == value }?.let { return ui.string(it.labelRes) }
        val number = value.toIntOrNull()?.takeIf { it > 0 && option.customRes != 0 } ?: return null
        return ui.resources.getQuantityString(option.customRes, number, number)
    }

    /** A "When to show" value in a word or two, for chips and tiles. */
    fun shortLabel(value: String?): Int = when (value) {
        null -> R.string.glance_short_always
        GlanceWhen.MUSIC -> R.string.glance_short_music
        GlanceWhen.CHARGING -> R.string.glance_short_charging
        GlanceWhen.WIFI -> R.string.glance_short_wifi
        GlanceWhen.MOBILE -> R.string.glance_short_mobile
        GlanceWhen.HEADPHONES -> R.string.glance_short_headphones
        GlanceWhen.MORNING -> R.string.glance_short_morning
        GlanceWhen.AFTERNOON -> R.string.glance_short_afternoon
        GlanceWhen.EVENING -> R.string.glance_short_evening
        GlanceWhen.NIGHT -> R.string.glance_short_night
        else -> R.string.glance_short_always
    }

    private fun show(
        ui: GlanceUi,
        preferences: SharedPreferences,
        source: GlanceSource,
        icon: Drawable?,
        options: List<GlanceOption>,
        calendars: CalendarList?,
        onClosed: () -> Unit,
    ) {
        val context = ui.context
        val dp = { v: Float -> (v * context.resources.displayMetrics.density).roundToInt() }
        val values = options.map { TextView(context) }

        fun store(option: GlanceOption, value: String?) {
            val key = GlanceOptions.key(source.id, option.name)
            preferences.edit().apply { if (value.isNullOrEmpty()) remove(key) else putString(key, value) }.apply()
        }

        fun current(option: GlanceOption): String? = preferences.getString(GlanceOptions.key(source.id, option.name), null)?.takeIf { it.isNotEmpty() }

        fun label(option: GlanceOption): CharSequence {
            val value = current(option)
            return when (option.type) {
                GlanceOption.Type.TEXT -> value ?: ui.string(R.string.glance_option_text_empty)
                GlanceOption.Type.CHOICE -> choiceText(ui, option, value) ?: ui.string(option.defaultRes)
                GlanceOption.Type.WIDGET -> GlanceWidgetOption.label(context, value) ?: ui.string(option.defaultRes)
                GlanceOption.Type.CALENDAR -> {
                    val ids = NotificationPick.apps(value)
                    when {
                        ids.isEmpty() -> ui.string(option.defaultRes)
                        ids.size == 1 -> calendarChoices(ui, option, calendars).firstOrNull { it.first == ids.first() }?.second
                            ?: ui.string(R.string.glance_option_calendars_one)
                        else -> ui.string(R.string.glance_option_calendars_many, ids.size)
                    }
                }
                GlanceOption.Type.APPS -> if (value == null) ui.string(option.defaultRes) else ui.string(R.string.glance_notification_apps_count, NotificationPick.apps(value).size)
            }
        }

        fun heading(text: Int) = TextView(context).apply {
            this.text = ui.string(text)
            ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.PRIMARY)
            setPadding(dp(28f), dp(20f), dp(28f), dp(10f))
        }

        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // The main switch: a big pill that fills with the primary container colour when on.
        val key = GlanceSources.key(source)
        column.addView(
            mainSwitch(ui, preferences.getBoolean(key, false)) { on -> preferences.edit().putBoolean(key, on).apply() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(16f)
                marginEnd = dp(16f)
            },
        )

        // When to show: one chip per choice, one chosen.
        column.addView(heading(R.string.glance_sheet_when))
        val whenKey = GlanceWhen.key(source)
        val chips = LinkedHashMap<String?, GlanceChip>()
        val flow = ChipFlow(context, dp(8f)).apply { setPadding(dp(24f), 0, dp(24f), 0) }
        val chosen = preferences.getString(whenKey, null)
        for (value in listOf<String?>(null) + GlanceWhen.values) {
            val chip = GlanceChip(context, ui.string(shortLabel(value)))
            chip.select(value == chosen, animate = false)
            chip.setOnClickListener {
                preferences.edit().apply { if (value == null) remove(whenKey) else putString(whenKey, value) }.apply()
                chips.forEach { (other, view) -> view.select(other == value) }
            }
            chips[value] = chip
            flow.addView(chip)
        }
        // Custom time: its chip names the range it holds once one is set.
        val customChosen = GlanceWhen.parseCustom(chosen) >= 0
        val custom = GlanceChip(context, if (customChosen) whenText(ui, chosen) else ui.string(R.string.glance_when_custom))
        custom.select(customChosen, animate = false)
        custom.setOnClickListener {
            editCustomTime(ui, preferences.getString(whenKey, null)) { value ->
                preferences.edit().putString(whenKey, value).apply()
                chips.forEach { (_, view) -> view.select(false) }
                custom.label(whenText(ui, value))
                custom.select(true)
            }
        }
        flow.addView(custom)
        column.addView(flow)

        // Options: a card row each, its value under its name.
        column.addView(heading(R.string.glance_sheet_options))
        val rows = options.mapIndexed { index, option ->
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16f), dp(12f), dp(20f), dp(12f))
                addView(TextView(context).apply {
                    text = ui.string(option.titleRes)
                    ExpressiveType.TITLE_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE)
                })
                addView(values[index].apply {
                    text = label(option)
                    ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
                })
            }
        }
        column.addView(ExpressiveCardList.build(context, rows) { index ->
            val option = options[index]
            val changed = { value: String?, text: CharSequence -> store(option, value); values[index].text = text }
            when (option.type) {
                GlanceOption.Type.TEXT -> editText(ui, context, option, current(option)) { text -> store(option, text); values[index].text = label(option) }
                GlanceOption.Type.CHOICE -> {
                    val listed = listOf<Pair<String?, CharSequence>>(null to ui.string(option.defaultRes)) + option.choices.map { it.value to ui.string(it.labelRes) }
                    val now = current(option)
                    val choices = if (option.customRes == 0) listed else listed + (CUSTOM to (choiceText(ui, option, now)?.takeIf { now !in option.choices.map { c -> c.value } } ?: ui.string(R.string.glance_option_custom)))
                    val selected = if (option.customRes != 0 && now != null && option.choices.none { it.value == now }) CUSTOM else now
                    FocusPagesDialog.choose(context, ui.resources, ui.string(option.titleRes), choices, selected) { value, text ->
                        if (value == CUSTOM) {
                            editNumber(ui, option, now?.toIntOrNull()) { number -> store(option, number.toString()); values[index].text = label(option) }
                        } else {
                            changed(value, text)
                        }
                    }
                }
                GlanceOption.Type.WIDGET -> GlanceWidgetOption.choose(ui, option, current(option)) { value -> store(option, value); values[index].text = label(option) }
                // Several calendars at once; none ticked reads every calendar, as before.
                GlanceOption.Type.CALENDAR -> pickMany(
                    ui, context, option,
                    calendarChoices(ui, option, calendars).mapNotNull { (id, label) -> id?.let { it to label } },
                    NotificationPick.apps(current(option)),
                ) { value -> store(option, value); values[index].text = label(option) }
                GlanceOption.Type.APPS -> FocusPagesDialog.readThen(context, { launchableApps(context) }) { apps ->
                    pickMany(ui, context, option, apps, NotificationPick.apps(current(option))) { value -> store(option, value); values[index].text = label(option) }
                }
            }
        })

        ExpressiveDialog(context)
            .icon(icon)
            .title(ui.string(source.titleRes))
            .message(ui.string(source.descriptionRes))
            .content(column)
            .confirm(ui.string(R.string.glance_order_done))
            .show()
            .setOnDismissListener { onClosed() }
    }

    /** A large pill with a switch, as Settings puts at the top of a page: primary container when on. */
    private fun mainSwitch(ui: GlanceUi, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val context = ui.context
        val surface = Surface(context, 0, context.dpOf(28f).toFloat())
        val label = TextView(context)
        val toggle = Switch(context)
        fun paint(on: Boolean, animate: Boolean) {
            val fill = if (on) ExpressiveRole.PRIMARY_CONTAINER.of(context) else ExpressiveRole.SURFACE_CONTAINER_HIGHEST.of(context)
            if (animate) GlanceMotion.color(surface.color, fill) { surface.color = it } else surface.color = fill
            label.text = ui.string(if (on) R.string.glance_sheet_on else R.string.glance_sheet_off)
            label.setTextColor(if (on) ExpressiveRole.ON_PRIMARY_CONTAINER.of(context) else ExpressiveRole.ON_SURFACE.of(context))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = surface.drawable
            minimumHeight = context.dpOf(72f)
            setPadding(context.dpOf(24f), 0, context.dpOf(20f), 0)
            isClickable = true
            addView(label.apply { ExpressiveType.TITLE_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle.apply {
                isChecked = checked
                setOnCheckedChangeListener { _, on -> paint(on, animate = true); onChange(on) }
            })
            setOnClickListener { toggle.toggle() }
            GlanceMotion.pressable(this, 0.97f)
            paint(checked, animate = false)
        }
    }

    /** "All calendars", then each visible calendar by name and account; just the first when none could be read. */
    private fun calendarChoices(ui: GlanceUi, option: GlanceOption, calendars: CalendarList?): List<Pair<String?, CharSequence>> =
        listOf<Pair<String?, CharSequence>>(null to ui.string(option.defaultRes)) +
            calendars?.ids?.indices?.map { i ->
                calendars.ids[i].toString() to ui.string(R.string.glance_option_calendars_choice, calendars.names[i], calendars.accounts[i])
            }.orEmpty()

    /** Every app with a launcher icon, by name; read off the main thread. */
    private fun launchableApps(context: Context): List<Pair<String, CharSequence>> {
        val manager = context.packageManager
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching { manager.queryIntentActivities(probe, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
            .map { it.activityInfo.packageName to it.activityInfo.applicationInfo.loadLabel(manager) }
            .distinctBy { it.first }
            .sortedBy { it.second.toString().lowercase() }
    }

    /** A checkbox row per item (apps, calendars); Done stores the ticked ones, none ticked meaning all of them. */
    private fun pickMany(
        ui: GlanceUi,
        context: Context,
        option: GlanceOption,
        apps: List<Pair<String, CharSequence>>,
        chosen: Set<String>,
        onDone: (String?) -> Unit,
    ) {
        val ticked = HashSet(chosen)
        val boxes = apps.map { (name, _) -> CheckBox(context).apply { isChecked = name in ticked; isClickable = false; isFocusable = false } }
        val rows = apps.mapIndexed { index, (_, label) ->
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val dp = { v: Float -> (v * context.resources.displayMetrics.density).roundToInt() }
                setPadding(dp(16f), dp(8f), dp(20f), dp(8f))
                addView(TextView(context).apply {
                    text = label
                    ExpressiveType.TITLE_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(boxes[index])
            }
        }
        val list = ExpressiveCardList.build(context, rows) { index ->
            val name = apps[index].first
            if (!ticked.remove(name)) ticked.add(name)
            boxes[index].isChecked = name in ticked
        }
        ExpressiveDialog(context)
            .title(ui.string(option.titleRes))
            .content(ScrollView(context).apply { addView(list) })
            .confirm(ui.string(R.string.glance_order_done)) { onDone(NotificationPick.encode(ticked).ifEmpty { null }) }
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    /**
     * A custom time: two big time buttons (each opens the clock picker) and the days of the week as
     * round toggles, with Weekdays, Weekends and Every day as shortcuts. Done hands back the value.
     */
    private fun editCustomTime(ui: GlanceUi, current: String?, onDone: (String) -> Unit) {
        val context = ui.context
        val packed = GlanceWhen.parseCustom(current)
        var start = if (packed >= 0) GlanceWhen.customStart(packed) else DEFAULT_START
        var end = if (packed >= 0) GlanceWhen.customEnd(packed) % (24 * 60) else DEFAULT_END
        var days = if (packed >= 0) GlanceWhen.customDays(packed) else GlanceWhen.EVERY_DAY
        val dp = { v: Float -> context.dpOf(v) }

        fun timeButton(label: Int, minute: () -> Int, onPick: (Int) -> Unit): LinearLayout {
            val surface = Surface(context, ExpressiveRole.PRIMARY_CONTAINER.of(context), dp(24f).toFloat())
            val value = TextView(context)
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = surface.drawable
                setPadding(dp(12f), dp(16f), dp(12f), dp(16f))
                isClickable = true
                addView(TextView(context).apply {
                    text = ui.string(label)
                    ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
                    setTextColor(ExpressiveRole.ON_PRIMARY_CONTAINER.of(context))
                })
                addView(value.apply {
                    text = clock(context, minute())
                    ExpressiveType.HEADLINE_SMALL.applyTo(this, ExpressiveRole.ON_SURFACE)
                    setTextColor(ExpressiveRole.ON_PRIMARY_CONTAINER.of(context))
                })
                setOnClickListener {
                    android.app.TimePickerDialog(context, { _, hour, min ->
                        onPick(hour * 60 + min)
                        value.text = clock(context, minute())
                        value.scaleX = 0.8f; value.scaleY = 0.8f
                        value.animate().scaleX(1f).scaleY(1f).setDuration(GlanceMotion.MEDIUM).setInterpolator(GlanceMotion.SPRING).start()
                    }, minute() / 60, minute() % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
                }
                GlanceMotion.pressable(this, 0.95f)
            }
        }

        val times = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16f), 0, dp(16f), 0)
            addView(timeButton(R.string.glance_custom_from, { start }) { start = it }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(timeButton(R.string.glance_custom_to, { end }) { end = it }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8f) })
        }

        // One round toggle per day, in the locale's first-day order.
        val first = java.util.Calendar.getInstance().firstDayOfWeek - java.util.Calendar.SUNDAY
        val dayViews = HashMap<Int, TextView>()
        fun paintDays() {
            for ((day, view) in dayViews) {
                val on = days and (1 shl day) != 0
                (view.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                    if (on) ExpressiveRole.PRIMARY.of(context) else ExpressiveRole.SURFACE_CONTAINER_HIGHEST.of(context),
                )
                view.setTextColor(if (on) ExpressiveRole.ON_PRIMARY.of(context) else ExpressiveRole.ON_SURFACE.of(context))
            }
        }
        val week = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12f), dp(16f), dp(12f), 0)
            for (i in 0 until 7) {
                val day = (first + i) % 7
                val view = TextView(context).apply {
                    text = dayName(day, short = true).take(1)
                    contentDescription = dayName(day, short = false)
                    gravity = Gravity.CENTER
                    ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
                    background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
                    isClickable = true
                    setOnClickListener {
                        val next = days xor (1 shl day)
                        if (next != 0) days = next
                        paintDays()
                        animate().scaleX(1.15f).scaleY(1.15f).setDuration(GlanceMotion.SHORT).withEndAction {
                            animate().scaleX(1f).scaleY(1f).setDuration(GlanceMotion.MEDIUM).setInterpolator(GlanceMotion.SPRING).start()
                        }.start()
                    }
                }
                dayViews[day] = view
                addView(view, LinearLayout.LayoutParams(dp(40f), dp(40f)).apply { marginStart = dp(3f); marginEnd = dp(3f) })
            }
        }
        val shortcuts = ChipFlow(context, dp(8f)).apply {
            setPadding(dp(24f), dp(16f), dp(24f), 0)
            for ((label, mask) in listOf(R.string.glance_days_every to GlanceWhen.EVERY_DAY, R.string.glance_days_weekdays to WEEKDAYS, R.string.glance_days_weekends to WEEKENDS)) {
                addView(GlanceChip(context, ui.string(label)).apply {
                    setOnClickListener {
                        days = mask
                        paintDays()
                    }
                })
            }
        }
        paintDays()

        ExpressiveDialog(context)
            .title(ui.string(R.string.glance_when_custom_title))
            .content(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(times)
                addView(week)
                addView(shortcuts)
            })
            .confirm(ui.string(R.string.glance_order_done)) { onDone(GlanceWhen.custom(start, if (end == 0) 24 * 60 else end, days)) }
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    /** A whole number for an option, read through its plural as it is typed. */
    private fun editNumber(ui: GlanceUi, option: GlanceOption, current: Int?, onDone: (Int) -> Unit) {
        val context = ui.context
        val preview = TextView(context)
        val input = EditText(context).apply {
            setText(current?.toString().orEmpty())
            setSelection(text.length)
            hint = ui.string(R.string.glance_option_custom_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            ExpressiveType.HEADLINE_SMALL.applyTo(this, ExpressiveRole.ON_SURFACE)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    val number = s?.toString()?.toIntOrNull()?.takeIf { it in 1..MAX_NUMBER }
                    preview.text = if (number == null) "" else ui.resources.getQuantityString(option.customRes, number, number)
                }
            })
        }
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val side = context.dpOf(24f)
            setPadding(side, 0, side, 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(preview.apply { ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT) })
        }
        ExpressiveDialog(context)
            .title(ui.string(option.titleRes))
            .content(holder as View)
            .confirm(ui.string(R.string.glance_order_done)) { input.text.toString().toIntOrNull()?.takeIf { it in 1..MAX_NUMBER }?.let(onDone) }
            .dismiss(context.getString(android.R.string.cancel))
            .show()
        input.requestFocus()
    }

    private const val CUSTOM = "\u0000custom"
    private const val DEFAULT_START = 9 * 60
    private const val DEFAULT_END = 17 * 60
    private const val WEEKDAYS = 0b0111110
    private const val WEEKENDS = 0b1000001
    private const val MAX_NUMBER = 9999

    private fun editText(ui: GlanceUi, context: Context, option: GlanceOption, current: String?, onDone: (String) -> Unit) {
        val input = EditText(context).apply {
            setText(current.orEmpty())
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
            ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
        }
        val holder = LinearLayout(context).apply {
            val side = (24f * context.resources.displayMetrics.density).roundToInt()
            setPadding(side, 0, side, 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        ExpressiveDialog(context)
            .title(ui.string(option.titleRes))
            .content(holder as View)
            .confirm(ui.string(R.string.glance_order_done)) { onDone(input.text.toString().trim()) }
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }
}
