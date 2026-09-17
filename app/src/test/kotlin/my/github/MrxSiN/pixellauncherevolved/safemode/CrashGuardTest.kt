package my.github.MrxSiN.pixellauncherevolved.safemode

import my.github.MrxSiN.pixellauncherevolved.catalog.BoolSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.IntSetting
import my.github.MrxSiN.pixellauncherevolved.catalog.LayoutMode
import my.github.MrxSiN.pixellauncherevolved.catalog.Settings
import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.settings.SettingsStore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashGuardTest {

    private val store = FakeSafeModeStore()
    private val settings = FakeSettings()
    private val guard = CrashGuard(store, settings, SilentLogger) { NOW }

    @Test
    fun `three crashes within a minute are a loop`() {
        assertTrue(CrashLoop.isLooping(listOf(50_000L, 90_000L, 99_000L), now = NOW))
    }

    @Test
    fun `crashes spread over more than a minute are not`() {
        assertFalse(CrashLoop.isLooping(listOf(10_000L, 90_000L, 99_000L), now = NOW))
    }

    @Test
    fun `a crash loop switches the experimental layout off and remembers it`() {
        settings.put(Settings.TASKBAR_ONLY, true)
        store.crashes += listOf(60_000L, 80_000L, 95_000L)

        assertEquals(listOf(LayoutMode.TASKBAR_ONLY), guard.recover())

        assertFalse(settings[Settings.TASKBAR_ONLY])
        assertEquals(listOf(Settings.TASKBAR_ONLY.key), store.disabled())
        assertTrue(store.crashes.isEmpty())
    }

    @Test
    fun `a crash loop with nothing experimental on changes no setting`() {
        settings.put(Settings.HOME_BLUR_WALLPAPER, true)
        store.crashes += listOf(60_000L, 80_000L, 95_000L)

        assertTrue(guard.recover().isEmpty())

        assertTrue(settings[Settings.HOME_BLUR_WALLPAPER])
        assertTrue(store.disabled().isEmpty())
    }

    @Test
    fun `fewer crashes leave everything on`() {
        settings.put(Settings.TABLET_MODE, true)
        store.crashes += listOf(80_000L, 95_000L)

        assertTrue(guard.recover().isEmpty())
        assertTrue(settings[Settings.TABLET_MODE])
    }

    private companion object {
        const val NOW = 100_000L
    }
}

private class FakeSafeModeStore : SafeModeStore {
    val crashes = mutableListOf<Long>()
    private var disabled = emptyList<String>()

    override fun crashes(): List<Long> = crashes.toList()

    override fun recordCrash(time: Long) {
        crashes += time
    }

    override fun clearCrashes() = crashes.clear()

    override fun disabled(): List<String> = disabled

    override fun setDisabled(keys: List<String>) {
        disabled = keys
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

private object SilentLogger : Logger {
    override fun info(message: String) = Unit

    override fun warn(message: String, error: Throwable?) = Unit
}
