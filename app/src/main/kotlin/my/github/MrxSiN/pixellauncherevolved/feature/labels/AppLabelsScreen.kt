package my.github.MrxSiN.pixellauncherevolved.feature.labels

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import java.util.concurrent.Executor
import java.util.concurrent.Executors

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveGrouping
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressivePage
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveShapes
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveType
import my.github.MrxSiN.pixellauncherevolved.icons.LaunchableApp
import my.github.MrxSiN.pixellauncherevolved.icons.LaunchableApps
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabel
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabelStore
import my.github.MrxSiN.pixellauncherevolved.labels.ProfileKind
import my.github.MrxSiN.pixellauncherevolved.labels.Profiles

/**
 * Every app, to rename or to hide the label of.
 *
 * A Settings page of its own, because it is a searchable list of every app, and
 * each app opens a small dialog with its name and the two places its label
 * shows. The apps are the launcher's own list (`LauncherApps`), so the page
 * offers what the drawer shows and nothing of a locked private space.
 *
 * A change is stored when it is saved and applied at once: the launcher reloads
 * its model, which rebinds every icon and re-sorts the drawer under the new name.
 */
internal class AppLabelsScreen(
    private val context: Context,
    private val strings: Resources,
    private val store: AppLabelStore,
    private val logger: Logger,
    private val onChanged: () -> Unit,
) {

    private val main = Handler(Looper.getMainLooper())
    private val profiles = Profiles(context)
    private val rows = ArrayList<Row>()
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private var query = ""

    fun show() {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(searchField(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(SEARCH_HEIGHT_DP)).apply {
                marginStart = dp(EDGE_DP)
                marginEnd = dp(EDGE_DP)
                bottomMargin = dp(16f)
            })
            empty = TextView(context).apply {
                text = strings.getString(R.string.app_labels_loading)
                ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(24f), 0, dp(24f))
            }
            addView(empty)
            list = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(CARD_INSET_DP), 0, dp(CARD_INSET_DP), 0)
            }
            addView(list)
        }

        ExpressivePage(context)
            .title(strings.getString(R.string.settings_page_app_labels))
            .description(strings.getString(R.string.app_labels_page_description))
            .backLabel(strings.getString(R.string.app_labels_back))
            .content(content)
            .show()

        worker.execute {
            val apps = runCatching { LaunchableApps.of(context) }
                .onFailure { logger.warn("App labels: the apps could not be listed", it) }
                .getOrDefault(emptyList())
            val labels = forgetRemovedProfiles(store.labels())
            val kinds = apps.mapTo(HashSet()) { it.userId }.associateWith { profiles.kindOf(it) }
            main.post { fill(apps, labels, kinds) }
        }
    }

    /**
     * Labels for a profile that no longer exists are dropped, so a profile made
     * later under the same number does not inherit them. A paused or locked
     * profile still exists and keeps its labels.
     */
    private fun forgetRemovedProfiles(labels: List<AppLabel>): List<AppLabel> {
        val ids = profiles.ids()
        if (ids.isEmpty()) return labels
        val kept = labels.filter { it.userId in ids }
        if (kept.size != labels.size) {
            logger.info("App labels: forgot ${labels.size - kept.size} labels of removed profiles")
            main.post { store.replace(kept) }
        }
        return kept
    }

    private fun searchField() = EditText(context).apply {
        hint = strings.getString(R.string.app_labels_search_hint)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        ExpressiveType.BODY_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE)
        setHintTextColor(ExpressiveRole.ON_SURFACE_VARIANT.of(context))
        background = ExpressiveShapes.pill(context, ExpressiveRole.SURFACE_CONTAINER_HIGH.of(context))
        setPadding(dp(20f), 0, dp(20f), 0)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                filter()
            }
        })
    }

    private fun fill(apps: List<LaunchableApp>, labels: List<AppLabel>, kinds: Map<Int, ProfileKind?>) {
        val byApp = labels.associateBy { Triple(it.packageName, it.className, it.userId) }
        for (app in apps) {
            val label = byApp[Triple(app.packageName, app.className, app.userId)]
                ?: AppLabel(app.packageName, app.className, app.userId, name = null)
            val row = Row(app, label, kinds[app.userId])
            row.view.setOnClickListener { edit(row) }
            rows += row
            list.addView(row.view)
        }
        filter()
        val filled = ArrayList(rows)
        worker.execute { loadIcons(filled) }
    }

    /** Hides what does not match and regroups what does into one card. */
    private fun filter() {
        val shown = rows.filter { it.matches(query) }
        for (row in rows) row.view.visibility = View.GONE
        shown.forEachIndexed { index, row ->
            row.view.visibility = View.VISIBLE
            row.view.background = ExpressiveShapes.card(context, ExpressiveGrouping.placementOf(index, shown.size))
            (row.view.layoutParams as LinearLayout.LayoutParams).bottomMargin =
                if (index < shown.lastIndex) dp(ExpressiveShapes.ROW_GAP_DP) else 0
        }
        empty.visibility = if (shown.isEmpty() && rows.isNotEmpty()) View.VISIBLE else View.GONE
        if (rows.isNotEmpty()) empty.text = strings.getString(R.string.app_labels_no_match)
    }

    /** On the worker: an app's icon is read from its package, one at a time, and shown as it arrives. */
    private fun loadIcons(filled: List<Row>) {
        for (row in filled) {
            val icon: Drawable = runCatching { row.app.info.getBadgedIcon(0) }.getOrNull() ?: continue
            main.post { row.icon.setImageDrawable(icon) }
        }
    }

    private fun edit(row: Row) {
        AppLabelEditor.show(context, strings, store, logger, row.app.label, row.label) { label ->
            row.label = label
            row.bind()
            onChanged()
        }
    }

    /** One app's row: its icon, the name it shows, and what was changed. */
    private inner class Row(val app: LaunchableApp, var label: AppLabel, private val kind: ProfileKind?) {

        val icon = ImageView(context)
        private val title = TextView(context).apply { ExpressiveType.TITLE_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE) }
        private val summary = TextView(context).apply { ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT) }

        val view: View = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ROW_HEIGHT_DP)
            setPadding(dp(16f), dp(12f), dp(20f), dp(12f))
            isClickable = true
            isFocusable = true
            addView(icon, LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply { marginEnd = dp(16f) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(summary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        init {
            bind()
        }

        fun shownName(): String = label.name ?: app.label

        fun matches(query: String): Boolean =
            query.isEmpty() || app.label.contains(query, ignoreCase = true) || label.name?.contains(query, ignoreCase = true) == true

        fun bind() {
            title.text = shownName()
            val parts = listOfNotNull(
                label.name?.let { strings.getString(R.string.app_labels_renamed, app.label) },
                when {
                    label.hideHome && label.hideDrawer -> strings.getString(R.string.app_labels_hidden_both)
                    label.hideHome -> strings.getString(R.string.app_labels_hidden_home)
                    label.hideDrawer -> strings.getString(R.string.app_labels_hidden_drawer)
                    else -> null
                },
                when (kind) {
                    ProfileKind.WORK -> strings.getString(R.string.app_labels_work_app)
                    ProfileKind.PRIVATE -> strings.getString(R.string.app_labels_private_app)
                    ProfileKind.CLONE -> strings.getString(R.string.app_labels_clone_app)
                    else -> null
                },
            )
            summary.text = parts.reduceOrNull { joined, part -> strings.getString(R.string.app_labels_summary_join, joined, part) }
            summary.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    internal companion object {
        const val EDGE_DP = 24f
        const val CARD_INSET_DP = 16f
        const val SEARCH_HEIGHT_DP = 56f
        const val ROW_HEIGHT_DP = 72f
        const val ICON_DP = 40f

        /** Lists apps and loads their icons; one thread for the life of the launcher, started on first use. */
        val worker: Executor by lazy {
            Executors.newSingleThreadExecutor { Thread(it, "ple-app-labels").apply { isDaemon = true } }
        }
    }
}
