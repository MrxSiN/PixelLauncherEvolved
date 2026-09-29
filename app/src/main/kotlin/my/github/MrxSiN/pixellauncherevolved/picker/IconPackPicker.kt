package my.github.MrxSiN.pixellauncherevolved.picker

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout

import io.github.libxposed.api.XposedInterface

import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.bridge.IconsBridge
import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Wallpaper & style → Home screen → Icon pack.
 *
 * ```
 * CustomizationPickerFragment.initCustomizationOptionEntries   fills each option list
 * CustomizationPickerFragment.updateHeaderHeightConstraints   sizes the previews against the list
 * ```
 *
 * One entry, right after the picker's own Icons entry and built from the same
 * layout, says which icon pack the home screen draws from and opens its page
 * ([IconPackPage]). Nothing else of the picker is changed.
 *
 * Pixel Lock Screen Evolved adds entries to the Lock screen list through the
 * same method. This entry goes only in the Home screen list, is found again by
 * its own tag, and takes only its own height out of the header sizing, so the
 * two modules can both be on.
 */
internal class IconPackPicker(
    private val xposed: XposedInterface,
    private val classLoader: ClassLoader,
    private val text: Resources,
    private val logger: Logger,
) {

    private val followed = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    fun install() {
        val fragment = runCatching { Class.forName(FRAGMENT, false, classLoader) }.getOrNull()
            ?: return logger.warn("Picker: Wallpaper & style's option lists are gone; no icon pack entry")
        val fill = fragment.declaredMethods.firstOrNull {
            it.name == "initCustomizationOptionEntries" || it.name == "access\$initCustomizationOptionEntries"
        } ?: return logger.warn("Picker: Wallpaper & style no longer fills its option lists here; no icon pack entry")

        xposed.hook(fill).intercept { chain ->
            val result = chain.proceed()
            runCatching {
                val args = chain.args
                val owner = chain.thisObject ?: args.firstOrNull() ?: return@runCatching
                val root = args.filterIsInstance<View>().firstOrNull() ?: return@runCatching
                if (args.filterIsInstance<Enum<*>>().firstOrNull()?.name != HOME_SCREEN) return@runCatching
                val list = root.findViewById<LinearLayout>(PickerPage.resource(root.context, "id", LIST)) ?: return@runCatching
                if (list.findViewWithTag<View>(TAG) != null) return@runCatching
                Entry(list, PickerColors(root.context, classLoader, owner, logger)).add()
            }.onFailure { logger.warn("Picker: the icon pack entry could not be added", it) }
            result
        }

        fragment.declaredMethods.firstOrNull { it.name == UPDATE_HEADER }?.let { update ->
            xposed.hook(update).intercept { chain ->
                val args = chain.args.toTypedArray()
                val page = args.getOrNull(PAGE) as? View
                val height = args.getOrNull(LIST_HEIGHT) as? Int
                if (page == null || height == null) return@intercept chain.proceed()
                args[LIST_HEIGHT] = height - addedHeight(page)
                chain.proceed(args)
            }
        } ?: logger.warn("Picker: the header sizing is gone; the previews may collapse further with the icon pack entry")

        logger.info("Picker: icon packs are chosen in Wallpaper & style")
    }

    private inner class Entry(private val list: LinearLayout, private val colors: PickerColors) {

        private val context: Context = list.context
        private val views = PickerViews(colors)
        private val client = IconsClient(context, logger)
        private lateinit var entry: PickerViews.Entry

        /** The pack the home screen draws from, as the launcher last said; null until it has. */
        private var known: Bundle? = null

        fun add() {
            entry = views.entry(list, ICON_ENTRY, text.getString(R.string.feature_icon_pack_title), null) ?: return
            entry.view.tag = TAG
            val icons = (0 until list.childCount).firstOrNull { index ->
                list.getChildAt(index).findViewById<View>(PickerPage.resource(context, "id", "downloading_icon")) != null
            }
            if (icons == null || icons == list.childCount - 1) {
                list.getChildAt(list.childCount - 1)?.let { last ->
                    views.background(last, if (list.childCount == 1) TOP else MIDDLE)
                }
                views.background(entry.view, BOTTOM)
                list.addView(entry.view)
            } else {
                views.background(entry.view, MIDDLE)
                list.addView(entry.view, icons + 1)
            }
            entry.view.setOnClickListener {
                runCatching { IconPackPage(context, text, colors, client, logger, ::refresh, known).show() }
                    .onFailure { logger.warn("Picker: the icon pack page could not be shown", it) }
            }
            keepWithList(list, entry.view)
            // A new picker session: what the launcher draws may have changed since the last.
            LauncherPreview.forget()
            refresh()
        }

        /** Says which pack the home screen draws from, and shows its icon. */
        fun refresh() = client.call(IconsBridge.STATE) { state ->
            if (state == null || !state.getBoolean(IconsBridge.AVAILABLE)) {
                views.describe(entry, text.getString(R.string.feature_icon_pack_unavailable))
                views.setEnabled(entry, false)
                return@call
            }
            views.setEnabled(entry, true)
            val pack = state.getString(IconsBridge.PACK)
            val drawn = if (state.getBoolean(IconsBridge.USES_PACK) && pack != null) pack else ""
            known = state
            // Drawn ahead, so the page opens with the home screen's icons already in place.
            LauncherPreview.prefetch(context, drawn, logger)
            if (state.getBoolean(IconsBridge.USES_PACK) && pack != null) {
                views.describe(entry, state.getString(IconsBridge.PACK_LABEL) ?: pack)
                entry.icon?.setImageDrawable(IconsClient.drawable(context, state.getByteArray(IconsBridge.TILE_PREFIX + pack)))
            } else {
                views.describe(entry, text.getString(R.string.feature_icon_pack_system))
                entry.icon?.setImageDrawable(runCatching {
                    context.getDrawable(PickerPage.resource(context, "drawable", SYSTEM_ICON))
                }.getOrNull())
            }
        }
    }

    /**
     * Takes the entry out of the list's height while the picker hides the list, as it does the
     * Home screen list on the Lock screen tab: hidden as invisible, the list still counts toward how
     * far the shared scroll view scrolls.
     */
    private fun keepWithList(list: ViewGroup, row: View) {
        if (!followed.add(list)) return
        val update = ViewTreeObserver.OnPreDrawListener {
            val wanted = if (list.visibility == View.VISIBLE) View.VISIBLE else View.GONE
            if (row.visibility != wanted) row.visibility = wanted
            true
        }
        list.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = view.viewTreeObserver.addOnPreDrawListener(update)
            override fun onViewDetachedFromWindow(view: View) = view.viewTreeObserver.removeOnPreDrawListener(update)
        })
        if (list.isAttachedToWindow) list.viewTreeObserver.addOnPreDrawListener(update)
    }

    /** How much taller the entry makes the Home screen list in [page]; 0 while that list is hidden. */
    private fun addedHeight(page: View): Int {
        val list = page.findViewById<ViewGroup>(PickerPage.resource(page.context, "id", LIST))
            ?.takeIf { it.visibility == View.VISIBLE } ?: return 0
        val row = list.findViewWithTag<View>(TAG)?.takeIf { it.visibility != View.GONE } ?: return 0
        val margins = row.layoutParams as? ViewGroup.MarginLayoutParams
        return row.height + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)
    }

    private companion object {
        const val FRAGMENT = "com.android.wallpaper.picker.customization.ui.CustomizationPickerFragment"
        const val UPDATE_HEADER = "updateHeaderHeightConstraints"
        const val HOME_SCREEN = "HOME_SCREEN"
        const val LIST = "home_customization_option_container"
        const val ICON_ENTRY = "customization_option_entry_app_icons"
        const val TAG = "pixel_launcher_evolved_icon_pack"
        const val SYSTEM_ICON = "ic_pack_theme_24px"
        const val TOP = "customization_option_entry_top_background"
        const val MIDDLE = "customization_option_entry_background"
        const val BOTTOM = "customization_option_entry_bottom_background"

        /** `updateHeaderHeightConstraints(page, WallpaperPickerEntry, label height, list height, chip, inset)`. */
        const val PAGE = 0
        const val LIST_HEIGHT = 3
    }
}
