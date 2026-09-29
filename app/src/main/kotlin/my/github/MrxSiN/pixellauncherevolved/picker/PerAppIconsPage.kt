package my.github.MrxSiN.pixellauncherevolved.picker

import android.app.AlertDialog
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.bridge.IconsBridge
import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Wallpaper & style → Home screen → Icon pack → Per-app icons.
 *
 * Every launchable app, in every profile the launcher shows, with the icon the
 * home screen draws for it now (drawn by the launcher, badged for work and
 * private apps as the home screen badges them) and what was chosen for it.
 * Search narrows the list by name; a tap asks what that one app should wear:
 * the pack's own answer, its stock icon, or any drawable the pack offers.
 *
 * Only the rows in view need pictures, and a picture is a Binder answer, so
 * the list shows [LIMIT] apps at a time and fetches their pictures a page at a
 * time; the search reaches the rest.
 */
internal class PerAppIconsPage(
    private val context: Context,
    private val text: Resources,
    private val colors: PickerColors,
    private val client: IconsClient,
    private val logger: Logger,
    private val onClosed: () -> Unit,
) {

    private class App(val key: String, val label: String, var choice: String)

    private val views = PickerViews(colors)
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val pictures = HashMap<String, android.graphics.drawable.Drawable>()
    private var apps: List<App> = emptyList()
    private var query = ""

    fun show() {
        val padding = runCatching {
            context.resources.getDimensionPixelSize(PickerPage.resource(context, "dimen", "customization_option_container_horizontal_padding"))
        }.getOrDefault(PickerPage.dp(context, 16f))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, PickerPage.dp(context, 8f), padding, PickerPage.dp(context, 24f))
            addView(search(text.getString(R.string.feature_icon_per_app_search)) { query = it; redraw() })
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = PickerPage.dp(context, 16f)
            })
        }
        note(text.getString(R.string.feature_icon_per_app_loading))
        PickerPage(context, colors, text.getString(R.string.feature_icon_per_app_title), ScrollView(context).apply { addView(column) }, onClosed)
            .show()

        client.call(IconsBridge.APPS) { answer ->
            val keys = answer?.getStringArray(IconsBridge.KEYS).orEmpty()
            val labels = answer?.getStringArray(IconsBridge.LABELS).orEmpty()
            val choices = answer?.getStringArray(IconsBridge.CHOICES).orEmpty()
            apps = keys.indices.map { App(keys[it], labels.getOrElse(it) { "" }, choices.getOrElse(it) { IconsBridge.CHOICE_PACK }) }
            redraw()
        }
    }

    /** The apps on screen: the ones with a choice first, then the rest, or whatever the search matches. */
    private fun shownApps(): List<App> {
        val wanted = query.trim().lowercase()
        val matching = if (wanted.isEmpty()) apps else apps.filter { it.label.lowercase().contains(wanted) }
        return (matching.filter { it.choice != IconsBridge.CHOICE_PACK } + matching.filter { it.choice == IconsBridge.CHOICE_PACK })
            .take(LIMIT)
    }

    private fun redraw() {
        list.removeAllViews()
        val shown = shownApps()
        if (shown.isEmpty()) {
            note(text.getString(if (apps.isEmpty()) R.string.feature_icon_per_app_loading else R.string.feature_icon_per_app_no_match))
            return
        }
        val group = views.group(context)
        val rows = HashMap<String, PickerViews.Entry>()
        for (app in shown) {
            val entry = views.entry(group, ICON_ENTRY, app.label, describe(app.choice)) ?: continue
            pictures[app.key]?.let { entry.icon?.setImageDrawable(it) }
            entry.view.setOnClickListener { choose(app, entry) }
            group.addView(entry.view)
            rows[app.key] = entry
        }
        views.shape(group)
        list.addView(group)
        fetch(shown.map { it.key }.filterNot(pictures::containsKey), rows)
    }

    /** Pictures for [keys], a page at a time, each drawn into its row as it arrives. */
    private fun fetch(keys: List<String>, rows: Map<String, PickerViews.Entry>) {
        if (keys.isEmpty()) return
        val page = keys.take(IconsBridge.PAGE)
        client.call(IconsBridge.APP_ICONS, extras = Bundle().apply { putStringArray(IconsBridge.KEYS, page.toTypedArray()) }) { answer ->
            for (key in page) {
                val picture = IconsClient.drawable(context, answer?.getByteArray(IconsBridge.ICON_PREFIX + key)) ?: continue
                pictures[key] = picture
                rows[key]?.icon?.setImageDrawable(picture)
            }
            fetch(keys.drop(IconsBridge.PAGE), rows)
        }
    }

    private fun describe(choice: String): String? = when (choice) {
        IconsBridge.CHOICE_PACK -> null
        IconsBridge.CHOICE_SYSTEM -> text.getString(R.string.feature_icon_choice_system)
        else -> text.getString(R.string.feature_icon_per_app_chosen, choice.replace('_', ' '))
    }

    /** Asks what [app] should wear, and applies the answer. */
    private fun choose(app: App, entry: PickerViews.Entry) {
        lateinit var dialog: AlertDialog
        val grid = GridLayout(context).apply { columnCount = COLUMNS }
        val more = TextView(context).apply {
            text = this@PerAppIconsPage.text.getString(R.string.feature_icon_choice_more)
            gravity = Gravity.CENTER
            setPadding(0, PickerPage.dp(context, 12f), 0, PickerPage.dp(context, 12f))
            colors.paint(this, "colorPrimary", ::setTextColor)
            visibility = View.GONE
        }
        var drawableQuery = ""
        var offset = 0

        fun set(choice: String) {
            dialog.dismiss()
            entry.view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            client.call(IconsBridge.OVERRIDE, extras = Bundle().apply {
                putString(IconsBridge.KEY, app.key)
                putString(IconsBridge.CHOICE, choice)
            }) {
                app.choice = choice
                pictures.remove(app.key)
                views.describe(entry, describe(choice))
                fetch(listOf(app.key), mapOf(app.key to entry))
            }
        }

        fun loadDrawables(reset: Boolean) {
            if (reset) {
                grid.removeAllViews()
                offset = 0
            }
            client.call(IconsBridge.DRAWABLES, drawableQuery, Bundle().apply { putInt(IconsBridge.OFFSET, offset) }) { answer ->
                val names = answer?.getStringArray(IconsBridge.NAMES).orEmpty()
                offset += names.size
                more.visibility = if (names.size == IconsBridge.PAGE) View.VISIBLE else View.GONE
                val size = PickerPage.dp(context, TILE_DP)
                for (name in names) {
                    grid.addView(
                        ImageView(context).apply {
                            setImageDrawable(IconsClient.drawable(context, answer?.getByteArray(IconsBridge.ICON_PREFIX + name)))
                            contentDescription = name.replace('_', ' ')
                            val pad = PickerPage.dp(context, 6f)
                            setPadding(pad, pad, pad, pad)
                            isFocusable = true
                            setOnClickListener { set(name) }
                        },
                        GridLayout.LayoutParams().apply {
                            width = size
                            height = size
                        },
                    )
                }
            }
        }
        more.setOnClickListener { loadDrawables(reset = false) }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = PickerPage.dp(context, 20f)
            setPadding(pad, PickerPage.dp(context, 8f), pad, 0)
            val choices = views.group(context)
            views.entry(choices, PLAIN_ENTRY, text.getString(R.string.feature_icon_choice_pack), null)?.let {
                it.view.setOnClickListener { set(IconsBridge.CHOICE_PACK) }
                choices.addView(it.view)
            }
            views.entry(choices, PLAIN_ENTRY, text.getString(R.string.feature_icon_choice_system), null)?.let {
                it.view.setOnClickListener { set(IconsBridge.CHOICE_SYSTEM) }
                choices.addView(it.view)
            }
            views.shape(choices)
            addView(choices)
            addView(search(text.getString(R.string.feature_icon_choice_search)) { drawableQuery = it; loadDrawables(reset = true) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = PickerPage.dp(context, 16f)
                })
            addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = PickerPage.dp(context, 8f)
            })
            addView(more)
        }
        dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.label)
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        loadDrawables(reset = true)
    }

    /** A search field shaped and coloured as the picker's search fields. */
    private fun search(hint: String, onQuery: (String) -> Unit): View = EditText(context).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        isSingleLine = true
        val pad = PickerPage.dp(context, 20f)
        setPadding(pad, PickerPage.dp(context, 14f), pad, PickerPage.dp(context, 14f))
        val shape = GradientDrawable().apply { cornerRadius = PickerPage.dp(context, 28f).toFloat() }
        background = shape
        colors.paint(this, "colorSurfaceContainerHigh") { shape.setColor(it) }
        colors.paint(this, "colorOnSurface", ::setTextColor)
        colors.paint(shape, "colorOnSurfaceVariant", ::setHintTextColor)
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(edited: Editable?) = onQuery(edited?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
    }

    private fun note(line: String) {
        list.removeAllViews()
        list.addView(TextView(context).apply {
            text = line
            gravity = Gravity.CENTER
            setPadding(0, PickerPage.dp(context, 24f), 0, 0)
            colors.paint(this, "colorOnSurfaceVariant", ::setTextColor)
        })
    }

    private companion object {
        const val ICON_ENTRY = "customization_option_entry_app_icons"
        const val PLAIN_ENTRY = "customization_option_entry_lock_screen_notifications"
        const val LIMIT = 60
        const val COLUMNS = 4
        const val TILE_DP = 60f
    }
}
