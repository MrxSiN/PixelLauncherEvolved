package my.github.MrxSiN.pixellauncherevolved.feature.search

import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveCardList
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRadio
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveType

/**
 * Asks which app opens a tapped Web Search result.
 *
 * The first row is the launcher's own answer, so the choice can always be
 * given back; the rest are what this device offers. A single-choice list
 * rather than a set of switches, because exactly one app opens the result.
 *
 * Drawn as a Material 3 Expressive dialog: the apps are one grouped card, each
 * row led by the app's icon and ended by a radio button. A tap is the choice,
 * so the dialog closes on its own once the radio has had a moment to answer.
 */
internal object WebSearchAppDialog {

    fun show(
        context: Context,
        resources: Resources,
        store: WebSearchAppStore,
        apps: WebSearchApps,
        onChosen: () -> Unit,
    ) {
        val title = resources.getString(R.string.feature_app_drawer_search_web_app_title)
        val installed = apps.installed()
        if (installed.isEmpty()) {
            ExpressiveDialog(context)
                .title(title)
                .message(resources.getString(R.string.feature_app_drawer_search_web_app_none))
                .dismiss(context.getString(android.R.string.ok))
                .show()
            return
        }

        // The launcher's own answer is the first row and the null choice, so
        // the list reads as one question with one answer already selected.
        // That answer is the Google app, so it wears the Google app's icon.
        val choices = listOf(null) + installed
        val chosen = store.chosen()
        val selected = choices.indexOfFirst { it?.packageName == chosen }
            .takeIf { chosen != null && it >= 0 } ?: 0

        val radios = choices.indices.map { index ->
            ExpressiveRadio(context).apply { setChecked(index == selected, animate = false) }
        }
        val rows = choices.mapIndexed { index, app ->
            row(
                context,
                label = app?.label ?: resources.getString(R.string.feature_app_drawer_search_web_app_default_label),
                packageName = app?.packageName ?: WebSearchApps.GOOGLE_APP,
                radio = radios[index],
            )
        }

        lateinit var dialog: Dialog
        val list = ExpressiveCardList.build(context, rows) { index ->
            radios.forEachIndexed { other, radio -> radio.setChecked(other == index, animate = true) }
            store.choose(choices[index]?.packageName)
            onChosen()
            rows[index].postDelayed({ dialog.dismiss() }, CLOSE_DELAY_MILLIS)
        }
        dialog = ExpressiveDialog(context)
            .title(title)
            .content(list)
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    private fun row(context: Context, label: CharSequence, packageName: String, radio: ExpressiveRadio): View {
        fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(20f), dp(12f))

            runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()?.let { icon ->
                addView(ImageView(context).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(
                    dp(ICON_DP),
                    dp(ICON_DP),
                ).apply { marginEnd = dp(16f) })
            }
            addView(TextView(context).apply {
                text = label
                ExpressiveType.TITLE_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(radio, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(16f) })

            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = RadioButton::class.java.name
                    info.isCheckable = true
                    info.isChecked = radio.checked
                }
            }
        }
    }

    private const val ICON_DP = 40f

    /** Long enough to see the radio answer, short enough to still feel like one tap. */
    private const val CLOSE_DELAY_MILLIS = 250L
}
