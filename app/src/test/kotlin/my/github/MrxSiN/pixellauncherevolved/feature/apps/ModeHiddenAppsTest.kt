package my.github.MrxSiN.pixellauncherevolved.feature.apps

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

import my.github.MrxSiN.pixellauncherevolved.icons.FakePreferences

class ModeHiddenAppsTest {

    private val preferences = FakePreferences()
    private val effective = EffectiveHiddenApps(preferences)

    @After
    fun reset() = ModeHiddenApps.update(FakePreferences(), emptyMap())

    @Test
    fun `no Mode on hides only the everywhere list`() {
        preferences.values["app_drawer_hidden_apps"] = "com.a"
        preferences.values["focus_hidden_apps_work"] = "com.b"
        ModeHiddenApps.update(preferences, emptyMap())

        assertNull(ModeHiddenApps.modeId)
        assertEquals(setOf("com.a"), effective.hidden())
    }

    @Test
    fun `the latest Mode that hides apps adds its list`() {
        preferences.values["app_drawer_hidden_apps"] = "com.a"
        preferences.values["focus_hidden_apps_work"] = "com.b"
        preferences.values["focus_hidden_apps_bed"] = "com.c"
        // Driving came on last but hides nothing, so it does not win.
        ModeHiddenApps.update(preferences, mapOf("work" to 10L, "bed" to 20L, "driving" to 30L))

        assertEquals("bed", ModeHiddenApps.modeId)
        assertEquals(setOf("com.a", "com.c"), effective.hidden())

        ModeHiddenApps.update(preferences, mapOf("work" to 10L))
        assertEquals(setOf("com.a", "com.b"), effective.hidden())
    }

    @Test
    fun `an unchanged answer is the same set`() {
        preferences.values["focus_hidden_apps_work"] = "com.b"
        ModeHiddenApps.update(preferences, mapOf("work" to 1L))
        assertSame(effective.hidden(), effective.hidden())
    }

    @Test
    fun `a change of winner is reported once`() {
        var told = 0
        ModeHiddenApps.onChanged = { told++ }
        preferences.values["focus_hidden_apps_work"] = "com.b"
        ModeHiddenApps.update(preferences, mapOf("work" to 1L))
        ModeHiddenApps.update(preferences, mapOf("work" to 1L))
        ModeHiddenApps.onChanged = null
        assertEquals(1, told)
    }
}
