package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.app.Dialog
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature

/** What a double tap on an empty spot of home does. Greyed out while the gesture is off. */
internal object DoubleTapActionRow : SettingsRow {

    private val CHOICES = listOf(
        Settings.DOUBLE_TAP_SLEEP to R.string.feature_double_tap_action_sleep,
        Settings.DOUBLE_TAP_MODES to R.string.feature_double_tap_action_modes,
    )

    override fun addTo(group: Any, scope: RowScope) {
        lateinit var row: Any
        row = scope.link(SettingsKeys.row("double_tap_action"), R.string.feature_double_tap_action_title, summary(scope)) {
            val context = scope.context
            lateinit var dialog: Dialog
            val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val density = context.resources.displayMetrics.density
            for ((value, label) in CHOICES) {
                list.addView(TextView(context).apply {
                    text = scope.string(label)
                    ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
                    minHeight = (48f * density).roundToInt()
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    val inset = (24f * density).roundToInt()
                    setPadding(inset, 0, inset, 0)
                    setOnClickListener {
                        scope.settings.put(Settings.HOME_DOUBLE_TAP_ACTION, value)
                        scope.api.setSummary(row, summary(scope))
                        dialog.dismiss()
                    }
                })
            }
            dialog = ExpressiveDialog(context)
                .title(scope.string(R.string.feature_double_tap_action_title))
                .content(list)
                .dismiss(context.getString(android.R.string.cancel))
                .show()
        }
        scope.api.add(group, row)
        scope.dependOn(FeatureCatalog.DOUBLE_TAP_TO_SLEEP, row)
        scope.requires(CompatibilityFeature.DOUBLE_TAP_TO_SLEEP, row)
    }

    private fun summary(scope: RowScope): String {
        val chosen = scope.settings[Settings.HOME_DOUBLE_TAP_ACTION]
        return scope.string(CHOICES.firstOrNull { it.first == chosen }?.second ?: CHOICES[0].second)
    }
}
