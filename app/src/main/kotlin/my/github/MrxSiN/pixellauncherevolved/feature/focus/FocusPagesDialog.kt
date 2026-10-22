package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.feature.apps.HideAppsSelection
import my.github.MrxSiN.pixellauncherevolved.focus.ModeOverrides
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabels
import my.github.MrxSiN.pixellauncherevolved.feature.search.SearchResultKind
import my.github.MrxSiN.pixellauncherevolved.feature.apps.SharedPreferencesHiddenAppsStore
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveCardList
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveDialog
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressivePage
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRadio
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveRole
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveShapes
import my.github.MrxSiN.pixellauncherevolved.feature.settings.ExpressiveType
import my.github.MrxSiN.pixellauncherevolved.focus.FocusFailure
import my.github.MrxSiN.pixellauncherevolved.focus.FocusMode
import my.github.MrxSiN.pixellauncherevolved.focus.FocusPages
import my.github.MrxSiN.pixellauncherevolved.focus.FocusSnapshot
import my.github.MrxSiN.pixellauncherevolved.focus.FocusSource
import my.github.MrxSiN.pixellauncherevolved.focus.FocusStore
import my.github.MrxSiN.pixellauncherevolved.focus.Orphan
import my.github.MrxSiN.pixellauncherevolved.icons.IconPacks
import my.github.MrxSiN.pixellauncherevolved.focus.forgetModesMissingFrom
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

/**
 * Asks which pages belong to which Mode, from the launcher's Home settings.
 *
 * This was going to be a row in the menu a long press opens, which is where the
 * page being assigned would already be on screen. That menu turned out to be a
 * Compose dialog in classes the launcher's shrinker renames every release, and
 * a Compose screen has no list of child views to add a row to. This dialog uses
 * the launcher's unfiltered page order and model snapshot to show the same
 * pages as selectable miniature home screens.
 *
 * Both steps are Material 3 Expressive dialogs: the Modes as the grouped card
 * Settings → Modes draws, then the chosen Mode's pages as a gallery.
 */
internal object FocusPagesDialog {

    private val reader: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ple-focus-modes").apply { isDaemon = true }
    }

    /** Main thread only. */
    private var reading = false

    /**
     * Runs [read] off the main thread, then [then] on it, unless the screen
     * that asked has gone. The Modes are read from this module's app, which
     * may ask a root shell, so the launcher's main thread never waits on it.
     * A second tap while one read is out is dropped, as the dialog it opens
     * would have covered it.
     */
    fun <T> readThen(context: Context, read: () -> T, then: (T) -> Unit) {
        if (reading) return
        reading = true
        reader.execute {
            val value = read()
            context.mainExecutor.execute {
                reading = false
                val activity = context as? Activity
                if (activity == null || !(activity.isFinishing || activity.isDestroyed)) then(value)
            }
        }
    }

    fun show(
        context: Context,
        resources: Resources,
        store: FocusStore,
        source: FocusSource,
        previewSource: FocusPagePreviewSource,
    ) {
        readThen(context, source::snapshot) { snapshot -> show(context, resources, store, snapshot, previewSource) }
    }

    private fun show(
        context: Context,
        resources: Resources,
        store: FocusStore,
        snapshot: FocusSnapshot,
        previewSource: FocusPagePreviewSource,
    ) {
        store.forgetModesMissingFrom(snapshot)
        // A Mode switched off cannot come on, so it is not offered pages.
        val modes = snapshot.modes.filter(FocusMode::isEnabled)
        val problem = when {
            !snapshot.isReadable && snapshot.failure == FocusFailure.REFUSED -> R.string.feature_focus_needs_access
            !snapshot.isReadable -> R.string.feature_focus_read_failed
            modes.isEmpty() -> R.string.feature_focus_no_modes
            FocusPages.order.isEmpty() -> R.string.feature_focus_no_pages
            else -> null
        }
        if (problem != null) {
            ExpressiveDialog(context)
                .title(resources.getString(R.string.feature_focus_pages_title))
                .message(resources.getString(problem))
                .dismiss(context.getString(android.R.string.ok))
                .show()
            return
        }

        val previews = previewSource.pages(FocusPages.order, store.assignments())
        val list = FocusModeList(
            context,
            FocusModeList.Text(
                active = resources.getString(R.string.feature_focus_active),
                summary = { screens -> summary(resources, screens) },
            ),
        )
        // The list stays open under a Mode's page and is drawn again when that page closes.
        val rows = FrameLayout(context)
        fun refill() {
            // A deleted Mode's pages follow the Modes as one more row, under the name it had.
            val orphans = store.orphans()
            val orphanRows = orphans.map { (id, orphan) ->
                FocusMode(ORPHAN_ROW + id, resources.getString(R.string.feature_focus_orphan, orphan.name ?: resources.getString(R.string.feature_focus_orphan_unnamed)), false)
            }
            val owned = store.assignments() + orphans.map { (id, orphan) -> ORPHAN_ROW + id to orphan.screens }
            rows.removeAllViews()
            rows.addView(list.build(modes + orphanRows, owned, previews) { mode ->
                val orphan = orphans[mode.id.removePrefix(ORPHAN_ROW)]
                if (mode.id.startsWith(ORPHAN_ROW) && orphan != null) {
                    giveOrphan(context, resources, store, mode.id.removePrefix(ORPHAN_ROW), orphan, modes, list, previews, ::refill)
                } else {
                    choosePages(context, resources, store, mode, previews, ::refill)
                }
            })
        }
        refill()
        ExpressivePage(context)
            .title(resources.getString(R.string.feature_focus_pages_title))
            .description(resources.getString(R.string.feature_focus_choose_mode_summary))
            .backLabel(resources.getString(R.string.action_navigate_up))
            .content(rows)
            .show()
    }

    /**
     * Ticks the pages this Mode should show.
     *
     * A page belongs to one Mode at a time, so pages owned by another Mode are
     * not offered here. Ticking nothing gives the Mode's pages back to the
     * ordinary home screen, which is how an assignment is undone.
     *
     * A full page, saved as it is left, as a Settings page is. Home screens
     * that would leave none for while no Mode is on are not saved; the rest is,
     * and a dialog says why. [onSaved] redraws the Mode list under it.
     */
    private fun choosePages(
        context: Context,
        resources: Resources,
        store: FocusStore,
        mode: FocusMode,
        previews: Map<Int, FocusPagePreview>,
        onSaved: () -> Unit,
    ) {
        val assignments = store.assignments()
        val grid = FocusPageGrid(context) { screen ->
            resources.getString(R.string.feature_focus_page, requireNotNull(FocusPages.numberOf(screen)))
        }
        val gallery = grid.build(
            pages = selectablePages(FocusPages.order, assignments, mode.id),
            previews = previews,
            selected = assignments[mode.id].orEmpty(),
        )

        lateinit var page: Dialog
        val preferences = LauncherSettings.preferences(context)

        // Off keeps the Mode to its own home screens, as before; on keeps the regular ones beside them.
        val withOrdinary = toggle(context, store.withOrdinary(mode.id))
        val ordinary = CardRow(context, resources, R.drawable.ic_mode_home, resources.getString(R.string.feature_focus_with_ordinary), withOrdinary)

        val hiddenStore = SharedPreferencesHiddenAppsStore.forMode(preferences, mode.id)
        val hiddenApps = CardRow(context, resources, R.drawable.ic_mode_hidden_apps, resources.getString(R.string.feature_focus_hidden_apps))
        hiddenApps.value(appCount(resources, hiddenStore, R.plurals.feature_focus_hidden_apps_count))
        val drawerStore = SharedPreferencesHiddenAppsStore(preferences, ModeOverrides.DRAWER_APPS_KEY + mode.id)
        val drawerApps = CardRow(context, resources, R.drawable.ic_mode_drawer_top, resources.getString(R.string.feature_focus_drawer_apps))
        drawerApps.value(appCount(resources, drawerStore, R.plurals.feature_focus_drawer_apps_summary))

        // Null follows the everywhere choice; saved with the home screens.
        var iconPack: String? = preferences.getString(ModeOverrides.ICON_PACK_KEY + mode.id, null)
        val iconRow = CardRow(context, resources, R.drawable.ic_mode_icon_pack, resources.getString(R.string.feature_focus_icon_pack))
        iconRow.value(iconPackLabel(context, resources, iconPack))

        // Null follows Home settings' blur; "0" is none.
        var blurValue: String? = preferences.getString(ModeOverrides.BLUR_KEY + mode.id, null)
        val blurChoices = listOf<Pair<String?, CharSequence>>(
            null to resources.getString(R.string.feature_focus_inherit),
            "0" to resources.getString(R.string.feature_focus_blur_none),
        ) + listOf(25, 50, 75, 100).map { it.toString() to "$it%" }
        val blurRow = CardRow(context, resources, R.drawable.ic_mode_blur, resources.getString(R.string.feature_focus_blur))
        blurRow.value(blurChoices.firstOrNull { it.first == blurValue }?.second ?: "$blurValue%")

        // Null is none: there is no everywhere shade to follow.
        var shadeValue: String? = preferences.getString(ModeOverrides.SHADE_KEY + mode.id, null)
        val shadeChoices = listOf<Pair<String?, CharSequence>>(
            null to resources.getString(R.string.feature_focus_shade_none),
            "33000000" to resources.getString(R.string.feature_focus_shade_dim, 20),
            "66000000" to resources.getString(R.string.feature_focus_shade_dim, 40),
            "99000000" to resources.getString(R.string.feature_focus_shade_dim, 60),
            "40FF8A00" to resources.getString(R.string.feature_focus_shade_warm),
            "40005CFF" to resources.getString(R.string.feature_focus_shade_cool),
        )
        val shadeRow = CardRow(context, resources, R.drawable.ic_mode_tint, resources.getString(R.string.feature_focus_shade))
        shadeRow.value(shadeChoices.firstOrNull { it.first == shadeValue }?.second ?: "#$shadeValue")

        // Null follows the App labels switches; any other value replaces them while this Mode wins.
        var labelsValue: String? = preferences.getString(ModeOverrides.LABELS_KEY + mode.id, null)
        val labelsChoices = listOf<Pair<String?, CharSequence>>(
            null to resources.getString(R.string.feature_focus_inherit),
            "0" to resources.getString(R.string.feature_focus_labels_all),
            (AppLabels.HIDE_HOME or AppLabels.HIDE_FOLDER).toString() to resources.getString(R.string.feature_focus_labels_off_home),
            AppLabels.HIDE_DRAWER.toString() to resources.getString(R.string.feature_focus_labels_off_drawer),
            (AppLabels.HIDE_HOME or AppLabels.HIDE_FOLDER or AppLabels.HIDE_DRAWER).toString() to
                resources.getString(R.string.feature_focus_labels_none),
        )
        val labelsRow = CardRow(context, resources, R.drawable.ic_mode_labels, resources.getString(R.string.feature_focus_labels))
        labelsRow.value(labelsChoices.firstOrNull { it.first == labelsValue }?.second ?: labelsValue.orEmpty())

        // Search groups this Mode turns off; one switched off everywhere stays off here.
        val modeSearch = preferences.getString(ModeOverrides.SEARCH_KEY + mode.id, "").orEmpty().split(',').toSet()
        val searchSwitches = FeatureCatalog.entries.filter { entry -> SearchResultKind.entries.any { it.setting == entry.setting } }
            .map { entry ->
                val everywhere = preferences.getBoolean(entry.setting.key, entry.setting.default)
                val switch = toggle(context, !everywhere && entry.setting.key !in modeSearch).apply { isEnabled = !everywhere }
                entry.setting.key to CardRow(context, resources, null, resources.getString(entry.titleRes), switch).apply {
                    view.isEnabled = !everywhere
                    if (everywhere) {
                        view.alpha = DISABLED_ALPHA
                        value(resources.getString(R.string.feature_focus_search_off_everywhere))
                    }
                }
            }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // The home screens first: choosing them is what this page is for.
            addView(gallery)
            addView(card(context, listOf(ordinary)) { withOrdinary.toggle() }, gap(context, 16f))
            addView(heading(context, resources.getString(R.string.feature_focus_section_apps)))
            addView(card(context, listOf(hiddenApps, drawerApps)) { index ->
                page.dismiss()
                pickApps(context, if (index == 0) hiddenStore else drawerStore)
            })
            addView(heading(context, resources.getString(R.string.settings_group_appearance)))
            addView(card(context, listOf(iconRow, blurRow, shadeRow, labelsRow)) { index ->
                when (index) {
                    0 -> readThen(context, { IconPacks.installed(context) }) { packs ->
                        val choices = listOf<Pair<String?, CharSequence>>(
                            null to resources.getString(R.string.feature_focus_icon_pack_inherit),
                            ModeOverrides.SYSTEM_ICONS to resources.getString(R.string.feature_focus_icon_pack_system),
                        ) + packs.map { it.packageName to it.label }
                        choose(context, resources, resources.getString(R.string.feature_focus_icon_pack), choices, iconPack) { value, label ->
                            iconPack = value
                            iconRow.value(label)
                        }
                    }
                    1 -> choose(context, resources, resources.getString(R.string.feature_focus_blur), blurChoices, blurValue) { value, label ->
                        blurValue = value
                        blurRow.value(label)
                    }
                    2 -> choose(context, resources, resources.getString(R.string.feature_focus_shade), shadeChoices, shadeValue) { value, label ->
                        shadeValue = value
                        shadeRow.value(label)
                    }
                    else -> choose(context, resources, resources.getString(R.string.feature_focus_labels), labelsChoices, labelsValue) { value, label ->
                        labelsValue = value
                        labelsRow.value(label)
                    }
                }
            })
            addView(heading(context, resources.getString(R.string.feature_focus_search_heading)))
            addView(card(context, searchSwitches.map { it.second }) { index ->
                val switch = searchSwitches[index].second.end as Switch
                if (switch.isEnabled) switch.toggle()
            })
        }

        page = ExpressivePage(context)
            .title(mode.name)
            .description(resources.getString(R.string.feature_focus_choose_pages_summary))
            .backLabel(resources.getString(R.string.action_navigate_up))
            .content(content)
            .onClose {
                store.setWithOrdinary(mode.id, withOrdinary.isChecked)
                val off = searchSwitches.filter { (_, row) -> (row.end as Switch).let { it.isEnabled && !it.isChecked } }.map { it.first }
                preferences.edit().apply {
                    if (off.isEmpty()) remove(ModeOverrides.SEARCH_KEY + mode.id)
                    else putString(ModeOverrides.SEARCH_KEY + mode.id, off.joinToString(","))
                    val pack = iconPack
                    if (pack == null) remove(ModeOverrides.ICON_PACK_KEY + mode.id)
                    else putString(ModeOverrides.ICON_PACK_KEY + mode.id, pack)
                    val blur = blurValue
                    if (blur == null) remove(ModeOverrides.BLUR_KEY + mode.id)
                    else putString(ModeOverrides.BLUR_KEY + mode.id, blur)
                    val shade = shadeValue
                    if (shade == null) remove(ModeOverrides.SHADE_KEY + mode.id)
                    else putString(ModeOverrides.SHADE_KEY + mode.id, shade)
                    val labels = labelsValue
                    if (labels == null) remove(ModeOverrides.LABELS_KEY + mode.id)
                    else putString(ModeOverrides.LABELS_KEY + mode.id, labels)
                }.apply()
                ModeOverrides.recompute(preferences)
                val chosen = grid.selectedScreens()
                // Assigning puts the Mode first in priority, so only a real change is assigned.
                if (chosen == store.assignments()[mode.id].orEmpty()) {
                    // Unchanged.
                } else if (leavesOrdinaryPage(FocusPages.order, store.assignments(), mode.id, chosen)) {
                    apply(store, mode, chosen)
                } else if ((context as? Activity)?.isFinishing != true) {
                    // Not after Hidden apps or Top of app drawer has left settings for the drawer.
                    ExpressiveDialog(context)
                        .title(mode.name)
                        .message(resources.getString(R.string.feature_focus_keep_one_page))
                        .dismiss(context.getString(android.R.string.ok))
                        .show()
                }
                onSaved()
            }
            .show()
    }

    /**
     * Gives a deleted Mode's pages to a Mode, on top of the pages it has, or forgets them.
     * Meanwhile they are on the ordinary home screen, as any page no Mode has.
     */
    private fun giveOrphan(
        context: Context,
        resources: Resources,
        store: FocusStore,
        id: String,
        orphan: Orphan,
        modes: List<FocusMode>,
        list: FocusModeList,
        previews: Map<Int, FocusPagePreview>,
        onDone: () -> Unit,
    ) {
        lateinit var dialog: Dialog
        val rows = list.build(modes, store.assignments(), previews) { mode ->
            dialog.dismiss()
            val chosen = store.assignments()[mode.id].orEmpty() + orphan.screens
            if (leavesOrdinaryPage(FocusPages.order, store.assignments(), mode.id, chosen)) {
                apply(store, mode, chosen)
                store.keepOrphan(id, orphan.copy(screens = emptySet()))
                onDone()
            } else {
                ExpressiveDialog(context)
                    .title(mode.name)
                    .message(resources.getString(R.string.feature_focus_keep_one_page))
                    .dismiss(context.getString(android.R.string.ok))
                    .show()
            }
        }
        dialog = ExpressiveDialog(context)
            .title(resources.getString(R.string.feature_focus_orphan, orphan.name ?: resources.getString(R.string.feature_focus_orphan_unnamed)))
            .message(resources.getString(R.string.feature_focus_orphan_summary))
            .content(rows)
            .confirm(resources.getString(R.string.feature_focus_orphan_forget)) {
                store.keepOrphan(id, orphan.copy(screens = emptySet()))
                onDone()
            }
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    /**
     * Opens the drawer to tick apps for [store], the way Hidden apps does:
     * settings steps aside and the launcher finishes the choosing
     * ([HideAppsSelection]). [store] is the Mode's hidden apps, or the apps it
     * puts at the top of the drawer.
     */
    private fun pickApps(context: Context, store: SharedPreferencesHiddenAppsStore) {
        HideAppsSelection.begin(store)
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        (context as? Activity)?.finish()
    }

    private fun appCount(resources: Resources, store: SharedPreferencesHiddenAppsStore, some: Int): String {
        val count = store.hidden().size
        return if (count == 0) resources.getString(R.string.feature_focus_apps_none) else resources.getQuantityString(some, count, count)
    }

    /**
     * One choice out of [choices], as a grouped card of radio rows with
     * [current] already chosen. A tap is the choice; the dialog closes once the
     * radio has had a moment to answer, as the web results picker does.
     */
    private fun choose(
        context: Context,
        resources: Resources,
        title: CharSequence,
        choices: List<Pair<String?, CharSequence>>,
        current: String?,
        onPick: (String?, CharSequence) -> Unit,
    ) {
        lateinit var picker: Dialog
        val radios = choices.map { (value, _) -> ExpressiveRadio(context).apply { setChecked(value == current, animate = false) } }
        val rows = choices.mapIndexed { index, (_, label) -> CardRow(context, resources, null, label, radios[index]) }
        val list = ExpressiveCardList.build(context, rows.map { it.view }) { index ->
            radios.forEachIndexed { other, radio -> radio.setChecked(other == index, animate = true) }
            onPick(choices[index].first, choices[index].second)
            rows[index].view.postDelayed({ picker.dismiss() }, CLOSE_DELAY_MILLIS)
        }
        picker = ExpressiveDialog(context)
            .title(title)
            .content(list)
            .dismiss(context.getString(android.R.string.cancel))
            .show()
    }

    private fun iconPackLabel(context: Context, resources: Resources, pack: String?): CharSequence = when (pack) {
        null -> resources.getString(R.string.feature_focus_icon_pack_inherit)
        ModeOverrides.SYSTEM_ICONS -> resources.getString(R.string.feature_focus_icon_pack_system)
        else -> IconPacks.labelOf(context.packageManager, pack) ?: pack
    }

    /** [rows] as one grouped card; a tap on a row runs [onClick] with its place. */
    private fun card(context: Context, rows: List<CardRow>, onClick: (Int) -> Unit): View =
        ExpressiveCardList.build(context, rows.map { it.view }, onClick)

    /** A section's name over its card, in line with the text inside the card. */
    private fun heading(context: Context, text: CharSequence) = TextView(context).apply {
        this.text = text
        ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.PRIMARY)
        isAccessibilityHeading = true
        val density = context.resources.displayMetrics.density
        setPadding((32f * density).roundToInt(), (24f * density).roundToInt(), 0, (8f * density).roundToInt())
    }

    private fun gap(context: Context, top: Float) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = (top * context.resources.displayMetrics.density).roundToInt() }

    /** A switch the row it ends answers for: the row takes the tap and TalkBack reads them as one. */
    private fun toggle(context: Context, checked: Boolean) = Switch(context).apply {
        isChecked = checked
        isClickable = false
        isFocusable = false
        setOnCheckedChangeListener { view, on ->
            view.performHapticFeedback(if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
        }
    }

    /**
     * One row of a grouped card, as Android 17 settings draws it: a tinted
     * icon in a tonal circle, the title, the current value under it, and
     * [end] (a switch or a radio) at the far side.
     */
    private class CardRow(context: Context, resources: Resources, icon: Int?, title: CharSequence, val end: View? = null) {

        private val summary = TextView(context).apply {
            ExpressiveType.BODY_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
            visibility = View.GONE
        }

        val view = LinearLayout(context).apply {
            fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(20f), dp(12f))
            if (icon != null) {
                addView(ImageView(context).apply {
                    // This module's own drawable, from its own resources: the context's are the launcher's.
                    setImageDrawable(resources.getDrawable(icon, null).mutate().apply { setTint(ExpressiveRole.ON_PRIMARY_CONTAINER.of(context)) })
                    scaleType = ImageView.ScaleType.CENTER
                    background = ExpressiveShapes.rounded(ExpressiveRole.PRIMARY_CONTAINER.of(context), dp(20f).toFloat())
                }, LinearLayout.LayoutParams(dp(40f), dp(40f)).apply { marginEnd = dp(16f) })
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    ExpressiveType.TITLE_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE)
                })
                addView(summary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            end?.let { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(16f) }) }
        }

        fun value(text: CharSequence) {
            summary.text = text
            summary.visibility = View.VISIBLE
        }
    }

    private const val DISABLED_ALPHA = 0.38f
    private const val CLOSE_DELAY_MILLIS = 250L

    /** Gives [screens] to [mode] and takes them off every other Mode. */
    private fun apply(store: FocusStore, mode: FocusMode, screens: Set<Int>) {
        store.rememberName(mode.id, mode.name)
        for ((other, owned) in store.assignments()) {
            if (other == mode.id) continue
            val left = owned - screens
            if (left != owned) store.assign(other, left)
        }
        store.assign(mode.id, screens)
    }

    /** What a Mode's row says underneath its name. */
    private fun summary(resources: Resources, screens: Set<Int>?): String {
        val numbers = screens.orEmpty().mapNotNull(FocusPages::numberOf).sorted()
        return if (numbers.isEmpty()) {
            resources.getString(R.string.feature_focus_no_pages_assigned)
        } else {
            resources.getString(R.string.feature_focus_pages_assigned, numbers.joinToString(", "))
        }
    }
}

/** Pages not already reserved for a different Mode, in launcher order. */
internal fun selectablePages(
    pages: List<Int>,
    assignments: Map<String, Set<Int>>,
    modeId: String,
): List<Int> {
    val reserved = assignments
        .filterKeys { it != modeId }
        .values
        .flatten()
        .toSet()
    return pages.filterNot(reserved::contains)
}

/**
 * Whether giving [chosen] to [modeId] still leaves a page that is no Mode's,
 * for the home screen to show while no Mode is on.
 */
internal fun leavesOrdinaryPage(
    pages: List<Int>,
    assignments: Map<String, Set<Int>>,
    modeId: String,
    chosen: Set<Int>,
): Boolean {
    val reserved = assignments.filterKeys { it != modeId }.values.flatten().toSet() + chosen
    return pages.any { it !in reserved }
}

/** Marks a row standing for a deleted Mode's pages; Mode ids never contain a colon at the start. */
private const val ORPHAN_ROW = ":orphan:"
