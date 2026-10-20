package my.github.MrxSiN.pixellauncherevolved.feature.labels

import android.content.Context
import android.content.res.Resources
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.icons.IconReloader
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveShapes
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveType
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabel
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabelFormat
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabelStore

/**
 * One app's label: its name and the two places it shows.
 *
 * The same dialog opens from the App labels page and from an icon's long-press
 * menu, so the app's own name and component are always shown above the field:
 * a renamed app can never be mistaken for another.
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

        val name = EditText(context).apply {
            setText(current.name.orEmpty())
            hint = strings.getString(R.string.app_labels_name_hint, ownLabel)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(AppLabelFormat.MAX_CODE_POINTS * 2))
            ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
            setHintTextColor(ExpressiveRole.ON_SURFACE_VARIANT.of(context))
        }
        val home = toggle(context, strings.getString(R.string.app_labels_show_home), !current.hideHome)
        val drawer = toggle(context, strings.getString(R.string.app_labels_show_drawer), !current.hideDrawer)
        val reset = TextView(context).apply {
            text = strings.getString(R.string.app_labels_reset_app)
            ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.PRIMARY)
            gravity = Gravity.CENTER
            minHeight = dp(48f)
            setPadding(dp(16f), 0, dp(16f), 0)
            background = ExpressiveShapes.pill(context, android.graphics.Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                name.setText("")
                home.isChecked = true
                drawer.isChecked = true
                it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), 0, dp(24f), 0)
            addView(TextView(context).apply {
                text = strings.getString(R.string.app_labels_name_title)
                ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.PRIMARY)
                labelFor = View.generateViewId().also { name.id = it }
            })
            addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(home, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f) })
            addView(drawer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(reset, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
                topMargin = dp(8f)
            })
        }

        val dialog = ExpressiveDialog(context)
            .title(current.name ?: ownLabel)
            .message(strings.getString(R.string.app_labels_identity, ownLabel, "${current.packageName}/${current.className}"))
            .content(content)
            .dismiss(context.getString(android.R.string.cancel))
            .confirm(strings.getString(R.string.app_labels_save)) {
                val chosen = AppLabelFormat.normalize(name.text)?.takeIf { it != ownLabel }
                val label = current.copy(name = chosen, hideHome = !home.isChecked, hideDrawer = !drawer.isChecked)
                content.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                if (label != current) {
                    store.put(label)
                    logger.info("App labels: ${label.packageName}/${label.className} in profile ${label.userId} changed")
                    IconReloader(context, logger).reload("app labels", SystemClock.elapsedRealtime(), null)
                    onSave(label)
                }
            }
            .show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun toggle(context: Context, title: String, checked: Boolean) = Switch(context).apply {
        text = title
        isChecked = checked
        ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
        minHeight = (48f * context.resources.displayMetrics.density).roundToInt()
        setOnCheckedChangeListener { view, on ->
            view.performHapticFeedback(if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
        }
    }
}
