package my.github.MrxSiN.pixellauncherevolved.safemode

import my.github.MrxSiN.pixellauncherevolved.catalog.LayoutMode
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsStore

/**
 * Notices a launcher that keeps crashing, and switches the experimental tweaks
 * off before they are installed again.
 *
 * A layout mode rewrites how the launcher measures itself at startup. When a
 * monthly update moves what it rewrites, the launcher can die before it draws
 * anything, restart, and die again, with no home screen left to open Home
 * settings from. So every crash is written down as it happens, and the next
 * start — before any tweak is installed — looks at how many there were.
 *
 * Only a crash that reaches the launcher's default handler is counted. That is
 * every exception a hook throws on the launcher's threads; a native crash or an
 * ANR is not seen, and is not what a hook breaking a layout causes.
 */
class CrashGuard(
    private val store: SafeModeStore,
    private val settings: SettingsStore,
    private val logger: Logger,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Records every uncaught crash, then lets the launcher's own handler end the process. */
    fun watch() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { store.recordCrash(clock()) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Switches the experimental tweaks off when the launcher is crash looping.
     *
     * Call before any feature installs. Answers what was switched off, which is
     * nothing unless the crashes make a loop and one of those tweaks was on.
     */
    fun recover(): List<LayoutMode> {
        if (!CrashLoop.isLooping(store.crashes(), clock())) return emptyList()
        store.clearCrashes()

        val enabled = LayoutMode.entries.filter { mode -> mode.setting?.let(settings::get) == true }
        // Grid & size changes how every profile is measured too; it goes back to
        // the launcher's own, remembered as key=value so it can be put back.
        val grid = Settings.GRID.filter { settings[it] != it.default }
        if (enabled.isEmpty() && grid.isEmpty()) {
            logger.warn("Pixel Launcher is crash looping with no experimental tweak on; nothing to switch off")
            return emptyList()
        }

        enabled.forEach { mode -> mode.setting?.let { settings.put(it, false) } }
        val gridEntries = grid.map { "${it.key}$GRID_VALUE${settings[it]}" }
        grid.forEach { settings.put(it, it.default) }
        store.setDisabled(enabled.mapNotNull { it.setting?.key } + gridEntries)
        logger.warn("Safe Mode: Pixel Launcher crashed ${CrashLoop.CRASHES} times within a minute; switched off $enabled $gridEntries")
        return enabled
    }

    companion object {
        /** Separates a Grid & size key from the value Safe Mode put back to default, in [SafeModeStore.disabled]. */
        const val GRID_VALUE = "="
    }
}
