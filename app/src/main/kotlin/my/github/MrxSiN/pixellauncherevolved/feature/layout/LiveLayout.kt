package my.github.MrxSiN.pixellauncherevolved.feature.layout

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper

import my.github.MrxSiN.pixellauncherevolved.catalog.LayoutMode
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext
import my.github.MrxSiN.pixellauncherevolved.settings.LauncherSettings

import java.lang.ref.WeakReference

/**
 * The layout mode the running launcher is laid out for, and the rebuild that
 * applies a new one without a restart.
 *
 * Every layout feature installs its hooks whatever the mode, and each hook
 * reads one of these fields rather than the preference file: several of them
 * run on the gesture thread or once per Recents layout.
 *
 * A change is applied the way a grid picked in Wallpaper & style is:
 * `InvariantDeviceProfile.onConfigChanged` rebuilds the grid and every device
 * profile, which runs the builder hooks again, and tells the launcher and the
 * taskbar to re-lay themselves out from the new profiles. Choosing a mode writes
 * several switches, so the rebuild is posted once for all of them, and the
 * fields change on the main thread right before it.
 *
 * Overview only also fits each task card's app chip as the card is inflated,
 * and Recents keeps a pool of cards inflated under the previous mode. So a
 * change into or out of it recreates the launcher activity as well, which
 * inflates Recents again; the process and the taskbar stay.
 */
internal object LiveLayout : SharedPreferences.OnSharedPreferenceChangeListener {

    @Volatile @JvmField var overviewOnly = false
    @Volatile @JvmField var taskbarOnly = false
    @Volatile @JvmField var tablet = false

    /** Whether the mode gives the launcher a taskbar. */
    val taskbar: Boolean get() = tablet || taskbarOnly

    private var context: FeatureContext? = null
    private val main = Handler(Looper.getMainLooper())
    private var pending = false
    private var mode = LayoutMode.DEFAULT
    private var launcher: WeakReference<Activity>? = null

    private val apply = Runnable {
        pending = false
        val context = context ?: return@Runnable
        val before = mode
        read(context)
        val after = mode
        if (before == after) return@Runnable

        runCatching {
            rebuild(context)
            if (before == LayoutMode.OVERVIEW_ONLY || after == LayoutMode.OVERVIEW_ONLY) launcher?.get()?.recreate()
        }
            .onSuccess { context.logger.info("Layout mode: $before to $after, applied live") }
            .onFailure { context.logger.warn("Unable to apply layout mode $after live; it applies on the next launcher start", it) }
    }

    /** Called by each layout feature as it installs; only the first call does anything. */
    @Synchronized
    fun install(context: FeatureContext) {
        if (this.context != null) return
        this.context = context
        read(context)
        context.findClass(LAUNCHER)?.let { type ->
            context.hookAfter(type, "onCreate", Bundle::class.java) { activity, _ -> launcher = WeakReference(activity as Activity) }
        }
        LauncherSettings.preferences(context.appContext).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (key == null || LayoutMode.ofKey(key) == null || pending) return
        pending = true
        main.post(apply)
    }

    private fun read(context: FeatureContext) {
        overviewOnly = context.settings[Settings.OVERVIEW_ONLY]
        taskbarOnly = context.settings[Settings.TASKBAR_ONLY]
        tablet = context.settings[Settings.TABLET_MODE]
        mode = LayoutMode.current(context.settings::get)
    }

    /**
     * ```
     * com.android.launcher3.InvariantDeviceProfile
     *   public static final DaggerSingletonObject INSTANCE
     *   public void onConfigChanged()
     * ```
     */
    fun rebuild(context: FeatureContext) {
        val idp = requireNotNull(singleton(context.classLoader, INVARIANT_DEVICE_PROFILE, context.appContext))
        requireNotNull(Reflect.method(idp.javaClass, "onConfigChanged")).invoke(idp)
    }

    /** One of the launcher's `DaggerSingletonObject INSTANCE`s, such as `InvariantDeviceProfile`'s; null when unreachable. */
    fun singleton(classLoader: ClassLoader, type: String, context: Context): Any? {
        val owner = my.github.MrxSiN.pixellauncherevolved.core.Host.cls(classLoader, type) ?: return null
        val singleton = Reflect.field(owner, "INSTANCE")?.get(null) ?: return null
        return Reflect.method(singleton.javaClass, "get", Context::class.java)?.invoke(singleton, context)
    }

    private const val LAUNCHER = "com.android.launcher3.Launcher"
    private const val INVARIANT_DEVICE_PROFILE = "com.android.launcher3.InvariantDeviceProfile"
}
