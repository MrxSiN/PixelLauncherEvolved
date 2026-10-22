package my.github.MrxSiN.pixellauncherevolved.feature.labels

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.os.UserHandle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.bridge.IconsBridge
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.icons.IconPackController
import my.github.MrxSiN.pixellauncherevolved.feature.icons.IconReloader
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveShapes
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveType
import my.github.MrxSiN.pixellauncherevolved.feature.settings.RowPlacement
import my.github.MrxSiN.pixellauncherevolved.icons.IconOverride
import my.github.MrxSiN.pixellauncherevolved.icons.IconPackState
import my.github.MrxSiN.pixellauncherevolved.icons.IconPacks
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabel
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabelFormat
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabelStore

/**
 * One app's label: its name and the two places it shows.
 *
 * The same dialog opens from the App labels page and from an icon's long-press
 * menu. It leads with the app as the Home screen will draw it, its icon over
 * the name being typed, and under that the app's own name and component, so a
 * renamed app can never be mistaken for another.
 */
internal object AppLabelEditor {

    /**
     * @param context a themed activity context (Home settings, or the launcher itself).
     * @param strings this module's own resources; a view's `resources` is the launcher's.
     * @param ownLabel the app's own label, which an empty name falls back to.
     * @param onSave runs with the new entry once it is stored and the launcher asked to rebind.
     */
    fun show(
        context: Context,
        strings: Resources,
        store: AppLabelStore,
        logger: Logger,
        ownLabel: String,
        current: AppLabel,
        onSave: (AppLabel) -> Unit = {},
    ) {
        fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

        // The preview: the icon over its label, on a tinted card like a Home screen cell.
        // The icon is a button: it opens the icon pack's drawables for this app.
        val controller = IconPackController.current()
        val currentChoice = controller?.let { iconChoiceOf(it, current) } ?: IconsBridge.CHOICE_PACK
        var iconChoice = currentChoice
        val icon = ImageView(context)
        loadIcon(context, controller, current, null, icon)
        val iconButton = FrameLayout(context).apply {
            addView(icon, FrameLayout.LayoutParams(dp(PREVIEW_ICON_DP), dp(PREVIEW_ICON_DP)))
            addView(ImageView(context).apply {
                setImageDrawable(strings.getDrawable(R.drawable.ic_edit_icon, null).mutate().apply { setTint(ExpressiveRole.ON_PRIMARY.of(context)) })
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
                background = ExpressiveShapes.rounded(ExpressiveRole.PRIMARY.of(context), dp(14f).toFloat())
            }, FrameLayout.LayoutParams(dp(28f), dp(28f), Gravity.BOTTOM or Gravity.END))
            contentDescription = strings.getString(R.string.app_labels_change_icon)
            background = ExpressiveShapes.pill(context, android.graphics.Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val pack = controller?.settings?.pack()?.takeIf { controller.settings.usesPack() }
                when {
                    controller == null -> notice(context, strings, R.string.feature_icon_pack_unavailable)
                    pack == null -> notice(context, strings, R.string.feature_icon_per_app_system_only)
                    else -> AppIconPicker.show(context, strings, pack, iconChoice) { choice, picture ->
                        iconChoice = choice
                        if (picture != null) icon.setImageDrawable(picture) else loadIcon(context, controller, current, choice, icon)
                    }
                }
            }
        }
        val shown = TextView(context).apply {
            ExpressiveType.TITLE_MEDIUM.applyTo(this, ExpressiveRole.ON_PRIMARY_CONTAINER)
            gravity = Gravity.CENTER_HORIZONTAL
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            // The name is read from the field below; the preview says nothing new.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val preview = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24f), dp(24f), dp(24f), dp(20f))
            background = ExpressiveShapes.rounded(ExpressiveRole.PRIMARY_CONTAINER.of(context), dp(ExpressiveShapes.CARD_RADIUS_DP).toFloat())
            addView(iconButton, LinearLayout.LayoutParams(dp(PREVIEW_ICON_DP + 8f), dp(PREVIEW_ICON_DP + 8f)))
            addView(shown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        }

        val name = EditText(context).apply {
            setText(current.name.orEmpty())
            hint = strings.getString(R.string.app_labels_name_hint, ownLabel)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(AppLabelFormat.MAX_CODE_POINTS * 2))
            ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
            setHintTextColor(ExpressiveRole.ON_SURFACE_VARIANT.of(context))
            background = null
            setPadding(0, dp(4f), 0, 0)
        }
        // A filled field: its label inside the container, over the text.
        val field = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            background = ExpressiveShapes.rounded(ExpressiveRole.SURFACE_BRIGHT.of(context), dp(FIELD_RADIUS_DP).toFloat())
            addView(TextView(context).apply {
                text = strings.getString(R.string.app_labels_name_title)
                ExpressiveType.LABEL_MEDIUM.applyTo(this, ExpressiveRole.PRIMARY)
                labelFor = View.generateViewId().also { name.id = it }
            })
            addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnClickListener { name.requestFocus() }
        }

        lateinit var refresh: () -> Unit
        val home = toggle(context, strings.getString(R.string.app_labels_show_home), !current.hideHome, RowPlacement.TOP) { refresh() }
        val drawer = toggle(context, strings.getString(R.string.app_labels_show_drawer), !current.hideDrawer, RowPlacement.BOTTOM) { refresh() }
        val reset = TextView(context).apply {
            text = strings.getString(R.string.app_labels_reset_app)
            ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.ON_PRIMARY_CONTAINER)
            gravity = Gravity.CENTER
            minHeight = dp(40f)
            setPadding(dp(20f), 0, dp(20f), 0)
            background = ExpressiveShapes.pill(context, ExpressiveRole.PRIMARY_CONTAINER.of(context))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                name.setText("")
                home.isChecked = true
                drawer.isChecked = true
                it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
        }
        refresh = {
            shown.text = AppLabelFormat.normalize(name.text) ?: ownLabel
            // Hidden on the Home screen: the cell keeps its place, as the launcher's does.
            shown.visibility = if (home.isChecked) View.VISIBLE else View.INVISIBLE
            val changed = name.text.isNotEmpty() || !home.isChecked || !drawer.isChecked
            reset.isEnabled = changed
            reset.alpha = if (changed) 1f else DISABLED_ALPHA
        }
        name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refresh()
        })
        refresh()

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), 0, dp(24f), 0)
            addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = strings.getString(R.string.app_labels_identity, ownLabel, "${current.packageName}/${current.className}")
                ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
                gravity = Gravity.CENTER_HORIZONTAL
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
            addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20f) })
            addView(home, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16f)
                bottomMargin = dp(ExpressiveShapes.ROW_GAP_DP)
            })
            addView(drawer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(reset, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.START
                topMargin = dp(16f)
            })
        }

        val dialog = ExpressiveDialog(context)
            .title(strings.getString(R.string.app_labels_rename))
            .content(content)
            .dismiss(context.getString(android.R.string.cancel))
            .confirm(strings.getString(R.string.app_labels_save)) {
                val chosen = AppLabelFormat.normalize(name.text)?.takeIf { it != ownLabel }
                val label = current.copy(name = chosen, hideHome = !home.isChecked, hideDrawer = !drawer.isChecked)
                content.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                // The icon pack's reload rebinds the labels too, so one reload carries both.
                val iconChanged = controller != null && iconChoice != currentChoice
                if (iconChanged) storeIcon(requireNotNull(controller), current, iconChoice)
                if (label != current) {
                    store.put(label)
                    logger.info("App labels: ${label.packageName}/${label.className} in profile ${label.userId} changed")
                    if (!iconChanged) IconReloader(context, logger).reload("app labels", SystemClock.elapsedRealtime(), null)
                    onSave(label)
                }
            }
            .show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    /**
     * On the App labels worker: the app's icon for [choice], then shown.
     *
     * Null is the icon as the home screen draws it now; the pack's own icon is
     * the pack's entry for the app, ignoring any override; the system icon is
     * the app's own.
     */
    private fun loadIcon(context: Context, controller: IconPackController?, app: AppLabel, choice: String?, into: ImageView) {
        AppLabelsScreen.worker.execute {
            val icon = runCatching {
                val info = context.getSystemService(LauncherApps::class.java)
                    ?.resolveActivity(
                        Intent().setComponent(ComponentName(app.packageName, app.className)),
                        UserHandle.getUserHandleForUid(app.userId * PER_USER_RANGE),
                    ) ?: return@runCatching null
                val own = info.getBadgedIcon(0)
                val density = context.resources.displayMetrics.densityDpi
                when (choice) {
                    null -> IconPackState.iconFor(info.activityInfo, info.applicationInfo.uid, density, own)
                    IconsBridge.CHOICE_PACK -> packDefault(context, controller, app, density) ?: own
                    else -> own
                }
            }.getOrNull() ?: return@execute
            into.post { into.setImageDrawable(icon) }
        }
    }

    /** The pack's own drawable for [app], as its component map names it, or null where it has none. */
    private fun packDefault(context: Context, controller: IconPackController?, app: AppLabel, density: Int): Drawable? {
        val pack = controller?.settings?.pack() ?: return null
        val id = controller.cachedIndexOf(pack)?.drawableFor(app.packageName, app.className)?.takeIf { it > 0 } ?: return null
        val artwork = IconPacks.resourcesOf(context, pack)?.getDrawableForDensity(id, density, null) ?: return null
        return IconPackState.adaptive(artwork)
    }

    /** What the icon pack page would say this app wears: the pack's own, the system's, or a drawable name. */
    private fun iconChoiceOf(controller: IconPackController, app: AppLabel): String {
        val override = controller.overrides.overrides()
            .firstOrNull { it.packageName == app.packageName && it.className == app.className && it.userId == app.userId }
        return when {
            override == null -> IconsBridge.CHOICE_PACK
            override.drawable == null -> IconsBridge.CHOICE_SYSTEM
            override.pack != controller.settings.pack() -> IconsBridge.CHOICE_PACK
            else -> override.drawable
        }
    }

    /** Stores [choice] for [app] as Wallpaper & style's per-app icons page does, then applies it. */
    private fun storeIcon(controller: IconPackController, app: AppLabel, choice: String) {
        val keep = controller.overrides.overrides().filterNot {
            it.packageName == app.packageName && it.className == app.className && it.userId == app.userId
        }
        val chosen = when (choice) {
            IconsBridge.CHOICE_PACK -> null
            IconsBridge.CHOICE_SYSTEM -> IconOverride(app.packageName, app.className, app.userId, null)
            else -> IconOverride(app.packageName, app.className, app.userId, choice, controller.settings.pack())
        }
        controller.overrides.replace(keep + listOfNotNull(chosen))
        controller.apply("per-app icon")
    }

    private fun notice(context: Context, strings: Resources, message: Int) {
        ExpressiveDialog(context)
            .title(strings.getString(R.string.app_labels_change_icon))
            .message(strings.getString(message))
            .dismiss(context.getString(android.R.string.ok))
            .show()
    }

    /** A switch row, drawn as one end of a grouped card. */
    private fun toggle(context: Context, title: String, checked: Boolean, placement: RowPlacement, onChange: () -> Unit) =
        Switch(context).apply {
            fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()
            text = title
            isChecked = checked
            ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
            minHeight = dp(56f)
            setPadding(dp(20f), 0, dp(16f), 0)
            background = ExpressiveShapes.card(context, placement)
            setOnCheckedChangeListener { view, on ->
                view.performHapticFeedback(if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
                onChange()
            }
        }

    private const val PREVIEW_ICON_DP = 72f
    private const val FIELD_RADIUS_DP = 16f
    private const val DISABLED_ALPHA = 0.38f

    /** `UserHandle.PER_USER_RANGE`: a user id times it is that user's first uid. */
    private const val PER_USER_RANGE = 100_000
}
