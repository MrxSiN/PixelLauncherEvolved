package my.github.MrxSiN.pixellauncherevolved.feature.backup

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.FeatureCatalog
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.LayoutMode
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.feature.apps.HiddenAppsStore
import my.github.MrxSiN.pixellauncherevolved.feature.search.WebSearchAppStore
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsStore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    private val settings = FakeSettings()
    private val hidden = FakeHiddenApps()
    private val web = FakeWebSearchApp()
    private val tweaks = TweakStores(settings, hidden, web)

    @Test
    fun `every switch in the catalogue has a name in the file`() {
        for (entry in FeatureCatalog.entries) {
            assertTrue("${entry.setting.key} is not written", entry.setting in SettingsBackupFormat.SWITCHES.values)
        }
    }

    @Test
    fun `a snapshot survives being written and read back`() {
        settings.put(Settings.HOME_BLUR_WALLPAPER, true)
        settings.put(Settings.HOME_BLUR_STRENGTH, 73)
        settings.put(Settings.APP_DRAWER_SEARCH_HIDE_WEB, true)
        settings.put(Settings.TASKBAR_ONLY, true)
        hidden.hide(setOf("com.example.b", "com.example.a"))
        web.choose("org.mozilla.firefox")
        val before = tweaks.snapshot()

        val after = SettingsBackupFormat.read(SettingsBackupFormat.write(before))

        assertEquals(before, after)
        assertEquals(LayoutMode.TASKBAR_ONLY, after.layoutMode)
    }

    @Test
    fun `the file reads the way the settings do`() {
        settings.put(Settings.HOME_BLUR_WALLPAPER, true)

        val text = SettingsBackupFormat.write(tweaks.snapshot())

        assertTrue(text.contains("\"version\": 1"))
        assertTrue(text.contains("\"blur\": true"))
        assertTrue(text.contains("\"blurStrength\": 0.5"))
        assertTrue(text.contains("\"layoutMode\": \"default\""))
    }

    @Test
    fun `a name the file leaves out takes its default`() {
        val snapshot = SettingsBackupFormat.read("""{"version": 1, "hideWebSearch": true, "blurStrength": 0.2}""")

        assertEquals(true, snapshot.switches[Settings.APP_DRAWER_SEARCH_HIDE_WEB])
        assertEquals(Settings.OVERVIEW_BUBBLE_BUTTON.default, snapshot.switches[Settings.OVERVIEW_BUBBLE_BUTTON])
        assertEquals(20, snapshot.blurStrength)
        assertEquals(LayoutMode.DEFAULT, snapshot.layoutMode)
        assertEquals(null, snapshot.webSearchApp)
    }

    @Test(expected = BackupException::class)
    fun `something that is not JSON is refused`() {
        SettingsBackupFormat.read("not a backup")
    }

    @Test
    fun `a file from a newer format is refused as newer`() {
        val reason = runCatching { SettingsBackupFormat.read("""{"version": 2}""") }
            .exceptionOrNull()
            .let { (it as BackupException).reason }

        assertEquals(BackupException.Reason.NEWER_VERSION, reason)
    }

    @Test
    fun `restoring a layout mode switches the others off`() {
        settings.put(Settings.TABLET_MODE, true)

        tweaks.restore(SettingsSnapshot.DEFAULTS.copy(layoutMode = LayoutMode.OVERVIEW_ONLY))

        assertTrue(settings[Settings.OVERVIEW_ONLY])
        assertFalse(settings[Settings.TABLET_MODE])
    }

    @Test
    fun `reset puts every tweak back`() {
        settings.put(Settings.OVERVIEW_HIDE_SELECT, true)
        settings.put(Settings.HOME_BLUR_STRENGTH, 10)
        hidden.hide(setOf("com.example.a"))
        web.choose("org.mozilla.firefox")

        tweaks.reset()

        assertEquals(SettingsSnapshot.DEFAULTS, tweaks.snapshot())
    }
}

private class FakeSettings : SettingsStore {
    private val values = mutableMapOf<String, Any>()

    override fun get(setting: BoolSetting): Boolean = values[setting.key] as? Boolean ?: setting.default

    override fun get(setting: IntSetting): Int = values[setting.key] as? Int ?: setting.default

    override fun put(setting: BoolSetting, value: Boolean) {
        values[setting.key] = value
    }

    override fun put(setting: IntSetting, value: Int) {
        values[setting.key] = value
    }
}

private class FakeHiddenApps : HiddenAppsStore {
    private var apps = emptySet<String>()

    override fun hidden(): Set<String> = apps

    override fun hide(packageNames: Set<String>) {
        apps = packageNames
    }
}

private class FakeWebSearchApp : WebSearchAppStore {
    private var chosen: String? = null

    override fun chosen(): String? = chosen

    override fun choose(packageName: String?) {
        chosen = packageName
    }
}
