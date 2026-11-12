package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.content.Context
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
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature
import my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusPagesDialog
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOption
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOptions
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSource
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceSources
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.CalendarBridge
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more.NotificationPick
import my.github.MrxSiN.pixellauncherevolved.glance.CalendarList
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * "Options" for every At a Glance source: one row each, its summary the values that are not the
 * default, a tap opening a dialog with a row per option ([GlanceOption]; Hide after is on every
 * source). Stored as `glance_option_<id>_<name>`; absent is the default. The calendar choice is a
 * single calendar or all (Smartspacer allows several); the calendar names are read from this module's
 * app off the main thread before the dialog opens.
 */
internal object GlanceOptionsRows : SettingsRow {

    override fun addTo(group: Any, scope: RowScope) {
        val preferences = LauncherSettings.preferences(scope.context)
        for (source in GlanceSources.all) {
            val options = GlanceOptions.of(source)
            lateinit var row: Any
            row = scope.link(
                key = SettingsKeys.row("glance_options_${source.id}"),
                title = source.titleRes,
                summary = summary(scope, preferences, source, options),
            ) {
                if (options.any { it.type == GlanceOption.Type.CALENDAR }) {
                    FocusPagesDialog.readThen(scope.context, {
                        CalendarBridge.calendars(scope.context.contentResolver, scope.environment.modulePackage, scope.environment.logger)
                    }) { calendars -> show(scope, preferences, source, options, calendars) { scope.api.setSummary(row, summary(scope, preferences, source, options)) } }
                } else {
                    show(scope, preferences, source, options, null) { scope.api.setSummary(row, summary(scope, preferences, source, options)) }
                }
            }
            scope.api.add(group, row)
            scope.requires(CompatibilityFeature.AT_A_GLANCE, row)
        }
    }

    private fun summary(scope: RowScope, preferences: SharedPreferences, source: GlanceSource, options: List<GlanceOption>): String {
        val set = options.mapNotNull { option ->
            val value = preferences.getString(GlanceOptions.key(source.id, option.name), null)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val shown = when (option.type) {
                GlanceOption.Type.TEXT -> value
                GlanceOption.Type.CALENDAR -> scope.string(R.string.glance_option_calendars_one)
                GlanceOption.Type.APPS -> scope.string(R.string.glance_notification_apps_count, NotificationPick.apps(value).size)
                GlanceOption.Type.WIDGET -> GlanceWidgetOption.label(scope.context, value)?.toString() ?: return@mapNotNull null
                GlanceOption.Type.CHOICE -> option.choices.firstOrNull { it.value == value }?.let { scope.string(it.labelRes) } ?: return@mapNotNull null
            }
            scope.string(R.string.glance_option_summary_entry, scope.string(option.titleRes), shown)
        }
        return if (set.isEmpty()) scope.string(R.string.glance_option_summary_default) else set.joinToString(", ")
    }

    /** The dialog: a card row per option, its value under its name; a tap changes it and stores it at once. */
    private fun show(
        scope: RowScope,
        preferences: SharedPreferences,
        source: GlanceSource,
        options: List<GlanceOption>,
        calendars: CalendarList?,
        onClosed: () -> Unit,
    ) {
        val context = scope.context
        val values = options.map { TextView(context) }

        fun store(option: GlanceOption, value: String?) {
            val key = GlanceOptions.key(source.id, option.name)
            preferences.edit().apply { if (value.isNullOrEmpty()) remove(key) else putString(key, value) }.apply()
        }

        fun current(option: GlanceOption): String? = preferences.getString(GlanceOptions.key(source.id, option.name), null)?.takeIf { it.isNotEmpty() }

        fun label(option: GlanceOption): CharSequence {
            val value = current(option)
            return when (option.type) {
                GlanceOption.Type.TEXT -> value ?: scope.string(R.string.glance_option_text_empty)
                GlanceOption.Type.CHOICE -> option.choices.firstOrNull { it.value == value }?.let { scope.string(it.labelRes) } ?: scope.string(option.defaultRes)
                GlanceOption.Type.WIDGET -> GlanceWidgetOption.label(context, value) ?: scope.string(option.defaultRes)
                GlanceOption.Type.CALENDAR -> calendarChoices(scope, option, calendars).firstOrNull { it.first == value }?.second
                    ?: if (value == null) scope.string(option.defaultRes) else scope.string(R.string.glance_option_calendars_one)
                GlanceOption.Type.APPS -> if (value == null) scope.string(option.defaultRes) else scope.string(R.string.glance_notification_apps_count, NotificationPick.apps(value).size)
            }
        }

        val rows = options.mapIndexed { index, option ->
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val dp = { v: Float -> (v * context.resources.displayMetrics.density).roundToInt() }
                setPadding(dp(16f), dp(12f), dp(20f), dp(12f))
                addView(TextView(context).apply {
                    text = scope.string(option.titleRes)
                    ExpressiveType.TITLE_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE)
                })
                addView(values[index].apply {
                    text = label(option)
                    ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
                })
            }
        }

        val list = ExpressiveCardList.build(context, rows) { index ->
            val option = options[index]
            val changed = { value: String?, text: CharSequence -> store(option, value); values[index].text = text }
            when (option.type) {
                GlanceOption.Type.TEXT -> editText(scope, context, option, current(option)) { text -> store(option, text); values[index].text = label(option) }
                GlanceOption.Type.CHOICE -> FocusPagesDialog.choose(
                    context, scope.resources, scope.string(option.titleRes),
                    listOf<Pair<String?, CharSequence>>(null to scope.string(option.defaultRes)) + option.choices.map { it.value to scope.string(it.labelRes) },
                    current(option), changed,
                )
                GlanceOption.Type.WIDGET -> GlanceWidgetOption.choose(scope, option, current(option)) { value -> store(option, value); values[index].text = label(option) }
                GlanceOption.Type.CALENDAR -> FocusPagesDialog.choose(
                    context, scope.resources, scope.string(option.titleRes), calendarChoices(scope, option, calendars), current(option), changed,
                )
                GlanceOption.Type.APPS -> FocusPagesDialog.readThen(context, { launchableApps(context) }) { apps ->
                    pickApps(scope, context, option, apps, NotificationPick.apps(current(option))) { value -> store(option, value); values[index].text = label(option) }
                }
            }
        }

        ExpressiveDialog(context)
            .title(scope.string(source.titleRes))
            .content(list)
            .confirm(scope.string(R.string.glance_order_done))
            .show()
            .setOnDismissListener { onClosed() }
    }

    /** "All calendars", then each visible calendar by name and account; just the first when none could be read. */
    private fun calendarChoices(scope: RowScope, option: GlanceOption, calendars: CalendarList?): List<Pair<String?, CharSequence>> =
        listOf<Pair<String?, CharSequence>>(null to scope.string(option.defaultRes)) +
            calendars?.ids?.indices?.map { i ->
                calendars.ids[i].toString() to scope.string(R.string.glance_option_calendars_choice, calendars.names[i], calendars.accounts[i])
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

    /** A checkbox row per app; Done stores the ticked ones, none ticked meaning every app. */
    private fun pickApps(
        scope: RowScope,
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
            .title(scope.string(option.titleRes))
            .content(ScrollView(context).apply { addView(list) })
            .confirm(scope.string(R.string.glance_order_done)) { onDone(NotificationPick.encode(ticked).ifEmpty { null }) }
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    private fun editText(scope: RowScope, context: Context, option: GlanceOption, current: String?, onDone: (String) -> Unit) {
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
            .title(scope.string(option.titleRes))
            .content(holder as View)
            .confirm(scope.string(R.string.glance_order_done)) { onDone(input.text.toString().trim()) }
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }
}
