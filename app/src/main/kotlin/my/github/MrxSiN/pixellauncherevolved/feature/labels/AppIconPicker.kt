package my.github.MrxSiN.pixellauncherevolved.feature.labels

import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.bridge.IconsBridge
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveShapes
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveType
import my.github.MrxSiN.pixellauncherevolved.icons.IconPackDrawables
import my.github.MrxSiN.pixellauncherevolved.icons.IconPackState

/**
 * Chooses one app's icon from the icon pack in use, for the Rename dialog.
 *
 * The same three answers Wallpaper & style's per-app icons page gives: the
 * pack's own icon for the app, the system icon, or any drawable the pack ships.
 * Nothing is stored here; the choice goes back to the Rename dialog, which
 * stores it with the name on Save.
 *
 * The pack's drawable list is read, searched and drawn on the App labels
 * worker, a page at a time, never on the launcher's main thread.
 */
internal object AppIconPicker {

    /**
     * @param chosen the choice shown as selected: [IconsBridge.CHOICE_PACK],
     * [IconsBridge.CHOICE_SYSTEM] or a drawable name.
     * @param onPick runs on the main thread with the choice and its picture
     * (null for the pack's own icon and the system icon, which the Rename
     * dialog draws itself).
     */
    fun show(
        context: Context,
        strings: Resources,
        pack: String,
        chosen: String,
        onPick: (choice: String, picture: Drawable?) -> Unit,
    ) {
        fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()
        val density = context.resources.displayMetrics.densityDpi
        lateinit var dialog: Dialog

        fun pick(choice: String, picture: Drawable?) {
            onPick(choice, picture)
            dialog.dismiss()
        }

        val chips = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(chip(context, strings.getString(R.string.feature_icon_choice_pack), chosen == IconsBridge.CHOICE_PACK) {
                pick(IconsBridge.CHOICE_PACK, null)
            })
            addView(chip(context, strings.getString(R.string.feature_icon_choice_system), chosen == IconsBridge.CHOICE_SYSTEM) {
                pick(IconsBridge.CHOICE_SYSTEM, null)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8f) })
        }

        val grid = GridLayout(context).apply { columnCount = COLUMNS }
        val status = TextView(context).apply {
            text = strings.getString(R.string.feature_icon_per_app_loading)
            ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(16f), 0, dp(16f))
        }
        val more = TextView(context).apply {
            text = strings.getString(R.string.feature_icon_choice_more)
            ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.ON_PRIMARY_CONTAINER)
            gravity = Gravity.CENTER
            minHeight = dp(40f)
            setPadding(dp(20f), 0, dp(20f), 0)
            background = ExpressiveShapes.pill(context, ExpressiveRole.PRIMARY_CONTAINER.of(context))
            visibility = View.GONE
        }

        // A search or a page is one generation: an answer for an older one is dropped.
        var generation = 0
        var query = ""
        var shown = 0
        var drawables: IconPackDrawables? = null

        fun load(reset: Boolean) {
            val asked = ++generation
            val wanted = query
            val offset = if (reset) 0 else shown
            if (reset) {
                grid.removeAllViews()
                shown = 0
                status.text = strings.getString(R.string.feature_icon_per_app_loading)
                status.visibility = View.VISIBLE
            }
            more.visibility = View.GONE
            AppLabelsScreen.worker.execute {
                val list = drawables ?: IconPackDrawables.of(context, pack)?.also { drawables = it }
                val names = list?.search(wanted, offset + PAGE + 1)?.drop(offset).orEmpty()
                val pictures = names.take(PAGE).mapNotNull { name ->
                    list?.drawableFor(name, density)?.let { name to IconPackState.adaptive(it) }
                }
                grid.post {
                    if (asked != generation) return@post
                    for ((name, picture) in pictures) {
                        grid.addView(tile(context, name, picture, name == chosen) { pick(name, picture) }, GridLayout.LayoutParams(
                            GridLayout.spec(GridLayout.UNDEFINED),
                            GridLayout.spec(GridLayout.UNDEFINED, 1f),
                        ).apply {
                            width = 0
                            height = dp(TILE_DP)
                            setMargins(dp(4f), dp(4f), dp(4f), dp(4f))
                        })
                    }
                    shown += pictures.size
                    status.text = strings.getString(R.string.feature_icon_per_app_no_match)
                    status.visibility = if (shown == 0) View.VISIBLE else View.GONE
                    more.visibility = if (names.size > PAGE) View.VISIBLE else View.GONE
                }
            }
        }
        more.setOnClickListener { load(reset = false) }

        val search = EditText(context).apply {
            hint = strings.getString(R.string.feature_icon_choice_search)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
            setHintTextColor(ExpressiveRole.ON_SURFACE_VARIANT.of(context))
            background = ExpressiveShapes.pill(context, ExpressiveRole.SURFACE_BRIGHT.of(context))
            minHeight = dp(48f)
            setPadding(dp(20f), 0, dp(20f), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty()
                    load(reset = true)
                }
            })
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), 0, dp(20f), 0)
            addView(chips)
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f) })
            addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
            addView(status)
            addView(more, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8f)
            })
        }

        dialog = ExpressiveDialog(context)
            .title(strings.getString(R.string.app_labels_change_icon))
            .content(content)
            .dismiss(context.getString(android.R.string.cancel))
            .show()
        load(reset = true)
    }

    /** A selectable pill, filled while it is the choice. */
    private fun chip(context: Context, label: String, selected: Boolean, onClick: () -> Unit) = TextView(context).apply {
        fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()
        text = label
        ExpressiveType.LABEL_LARGE.applyTo(this, if (selected) ExpressiveRole.ON_PRIMARY else ExpressiveRole.ON_SURFACE)
        gravity = Gravity.CENTER
        minHeight = dp(40f)
        setPadding(dp(16f), 0, dp(16f), 0)
        background = ExpressiveShapes.pill(context, (if (selected) ExpressiveRole.PRIMARY else ExpressiveRole.SURFACE_BRIGHT).of(context))
        isSelected = selected
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    /** One drawable of the pack, on a rounded tile ringed while it is the choice. */
    private fun tile(context: Context, name: String, picture: Drawable, selected: Boolean, onClick: () -> Unit) = ImageView(context).apply {
        val density = context.resources.displayMetrics.density
        setImageDrawable(picture)
        val inset = (10f * density).roundToInt()
        setPadding(inset, inset, inset, inset)
        val fill = ExpressiveShapes.pill(context, ExpressiveRole.SURFACE_BRIGHT.of(context))
        background = if (!selected) fill else LayerDrawable(arrayOf(fill, GradientDrawable().apply {
            cornerRadius = 100f * density
            setStroke((3f * density).roundToInt(), ExpressiveRole.PRIMARY.of(context))
        }))
        // Pack names are words joined with underscores.
        contentDescription = name.replace('_', ' ')
        isSelected = selected
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private const val COLUMNS = 4
    private const val PAGE = 24
    private const val TILE_DP = 72f
}
