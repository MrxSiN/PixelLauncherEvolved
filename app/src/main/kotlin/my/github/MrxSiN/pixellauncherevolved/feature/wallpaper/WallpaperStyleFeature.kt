package my.github.MrxSiN.pixellauncherevolved.feature.wallpaper

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Switch
import android.widget.TextView

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.wallpaper.WallpaperBlur

/**
 * Adds Blur Wallpaper directly below Layout on Wallpaper & Style's Home screen.
 *
 * Layout is the last option the Home screen tab builds, so the row is appended
 * to the same container; the rounded card the list is drawn as is closed off by
 * whichever row ends up last, so the background of the row that used to be last
 * is handed over as well.
 *
 * This is also the process that stores the choice: it is the one that holds
 * `WRITE_SECURE_SETTINGS`, which is why the setting lives where it does.
 */
class WallpaperStyleFeature(
    private val xposed: XposedInterface,
    private val classLoader: ClassLoader,
    private val moduleApplicationInfo: ApplicationInfo,
    private val logger: Logger,
) {

    fun install() {
        val binder = runCatching { Class.forName(BINDER_CLASS, false, classLoader) }.getOrNull()
        val bind = binder?.declaredMethods?.singleOrNull { it.name == "bind" }
        if (bind == null) {
            logger.warn("Wallpaper & Style customization binder is unavailable")
            return
        }

        xposed.hook(bind).intercept { chain ->
            chain.proceed().also {
                runCatching {
                    chain.args.filterIsInstance<View>().firstOrNull()?.let { root -> attach(root, labels(root)) }
                }.onFailure { error -> logger.warn("Unable to add Blur Wallpaper", error) }
            }
        }
        logger.info("Wallpaper & Style: Blur Wallpaper added below Layout")
    }

    /**
     * This module's own strings, read once and kept.
     *
     * They live in this module's APK, which Wallpaper & Style opens through the
     * `ApplicationInfo` the framework handed over at load. That path stops
     * existing the moment the module is updated, and Wallpaper & Style outlives
     * a module update easily — open it after updating and the row would simply
     * not appear. Reading the strings the first time the screen is drawn, while
     * the path is still the one this process started with, costs one lookup and
     * survives the update; the resources themselves are not held, because an
     * open `Resources` keeps the replaced APK on disk.
     */
    private var labels: Labels? = null

    private fun labels(root: View): Labels = labels ?: run {
        val resources = root.context.packageManager.getResourcesForApplication(moduleApplicationInfo)
        Labels(
            title = resources.getString(R.string.feature_home_blur_wallpaper_title),
            on = resources.getString(R.string.feature_home_blur_wallpaper_on),
            off = resources.getString(R.string.feature_home_blur_wallpaper_off),
        ).also { labels = it }
    }

    private data class Labels(val title: String, val on: String, val off: String)

    private fun attach(root: View, labels: Labels) {
        val context = root.context
        val container = root.findViewById<ViewGroup>(context.id(HOME_CONTAINER)) ?: return
        if (container.findViewWithTag<View>(ROW_TAG) != null) return

        val layout = context.layout(GRID_ENTRY)
        if (layout == 0) return

        val (title, on, off) = labels
        val row = LayoutInflater.from(context).inflate(layout, container, false)
        val description = row.findViewById<TextView>(context.id(DESCRIPTION))
        val toggle = Switch(context).apply {
            isClickable = false
            isFocusable = false
            showText = false
        }

        row.tag = ROW_TAG
        row.findViewById<TextView>(context.id(TITLE)).text = title
        row.findViewById<ViewGroup>(context.id(ICON_CONTAINER)).apply {
            // The slot is sized and rounded for an icon; a switch is neither.
            background = null
            layoutParams = layoutParams.apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            removeAllViews()
            addView(
                toggle,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }

        fun render(enabled: Boolean) {
            toggle.isChecked = enabled
            description.text = if (enabled) on else off
            row.contentDescription = "$title, ${description.text}"
        }

        render(WallpaperBlur.isEnabled(context))
        row.setOnClickListener {
            val enabled = !toggle.isChecked
            if (WallpaperBlur.setEnabled(context, enabled)) {
                render(enabled)
            } else {
                logger.warn("Blur Wallpaper could not be stored; secure settings refused the write")
            }
        }

        append(container, row)
    }

    /**
     * Adds the row as the last of the list, and closes the card off with it.
     *
     * Wallpaper & Style rounds the first and last rows of a container rather
     * than the container itself, so appending without moving that background
     * would leave the list rounded above the new row and square below it.
     */
    private fun append(container: ViewGroup, row: View) {
        val context = container.context
        val previous = container.getChildAt(container.childCount - 1)
        val last = context.drawable(if (previous == null) ONLY_BACKGROUND else LAST_BACKGROUND)
        val middle = context.drawable(MIDDLE_BACKGROUND)

        if (last != 0 && (previous == null || middle != 0)) {
            previous?.setBackgroundResource(middle)
            row.setBackgroundResource(last)
        }

        container.addView(row)
    }

    private fun Context.id(name: String): Int = resources.getIdentifier(name, "id", packageName)
    private fun Context.layout(name: String): Int = resources.getIdentifier(name, "layout", packageName)
    private fun Context.drawable(name: String): Int =
        resources.getIdentifier(name, "drawable", packageName)

    private companion object {
        const val BINDER_CLASS =
            "com.android.wallpaper.customization.ui.binder.ThemePickerCustomizationOptionsBinder"
        const val HOME_CONTAINER = "home_customization_option_container"
        const val GRID_ENTRY = "customization_option_entry_grid"
        const val TITLE = "option_entry_title"
        const val DESCRIPTION = "option_entry_description"
        const val ICON_CONTAINER = "option_entry_icon_container"
        const val MIDDLE_BACKGROUND = "customization_option_entry_background"
        const val LAST_BACKGROUND = "customization_option_entry_bottom_background"
        const val ONLY_BACKGROUND = "customization_option_entry_singleton_background"
        const val ROW_TAG = "pixellauncherevolved:blur_wallpaper"
    }
}
