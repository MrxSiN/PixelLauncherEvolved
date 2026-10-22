package my.github.MrxSiN.pixellauncherevolved.focus

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

import my.github.MrxSiN.pixellauncherevolved.feature.apps.EffectiveHiddenApps
import my.github.MrxSiN.pixellauncherevolved.icons.FakePreferences
import my.github.MrxSiN.pixellauncherevolved.labels.AppLabels

class ModeOverridesTest {

    private val preferences = FakePreferences()
    private val effective = EffectiveHiddenApps(preferences)

    @After
    fun reset() = ModeOverrides.update(FakePreferences(), emptyMap())

    @Test
    fun `no Mode on hides only the everywhere list`() {
        preferences.values["app_drawer_hidden_apps"] = "com.a"
        preferences.values["focus_hidden_apps_work"] = "com.b"
        ModeOverrides.update(preferences, emptyMap())

        assertNull(ModeOverrides.modeId)
        assertEquals(setOf("com.a"), effective.hidden())
    }

    @Test
    fun `the latest Mode that hides apps adds its list`() {
        preferences.values["app_drawer_hidden_apps"] = "com.a"
        preferences.values["focus_hidden_apps_work"] = "com.b"
        preferences.values["focus_hidden_apps_bed"] = "com.c"
        // Driving came on last but hides nothing, so it does not win.
        ModeOverrides.update(preferences, mapOf("work" to 10L, "bed" to 20L, "driving" to 30L))

        assertEquals("bed", ModeOverrides.modeId)
        assertEquals(setOf("com.a", "com.c"), effective.hidden())

        ModeOverrides.update(preferences, mapOf("work" to 10L))
        assertEquals(setOf("com.a", "com.b"), effective.hidden())
    }

    @Test
    fun `an unchanged answer is the same set`() {
        preferences.values["focus_hidden_apps_work"] = "com.b"
        ModeOverrides.update(preferences, mapOf("work" to 1L))
        assertSame(effective.hidden(), effective.hidden())
    }

    @Test
    fun `search groups follow the latest Mode that hides any`() {
        preferences.values["focus_search_hide_work"] = "app_drawer_search_hide_web"
        preferences.values["focus_search_hide_bed"] = "app_drawer_search_hide_web,app_drawer_search_hide_play_store"
        ModeOverrides.update(preferences, mapOf("work" to 10L, "bed" to 5L))
        assertEquals(setOf("app_drawer_search_hide_web"), ModeOverrides.searchHidden)

        ModeOverrides.update(preferences, emptyMap())
        assertEquals(emptySet<String>(), ModeOverrides.searchHidden)
    }

    @Test
    fun `a Mode's icon pack applies while it is on and is told once`() {
        var told = 0
        ModeOverrides.onIconsChanged = { told++ }
        preferences.values["focus_icon_pack_work"] = "com.pack"
        ModeOverrides.update(preferences, mapOf("work" to 1L))
        ModeOverrides.update(preferences, mapOf("work" to 1L))
        assertEquals("com.pack", ModeOverrides.iconPack)

        ModeOverrides.update(preferences, emptyMap())
        ModeOverrides.onIconsChanged = null
        assertNull(ModeOverrides.iconPack)
        assertEquals(2, told)
    }

    @Test
    fun `a Mode's shade applies while it is on, is told once and ignores bad text`() {
        var told = 0
        ModeOverrides.onShadeChanged = { told++ }
        preferences.values["focus_shade_work"] = "66000000"
        ModeOverrides.update(preferences, mapOf("work" to 1L))
        ModeOverrides.update(preferences, mapOf("work" to 1L))
        assertEquals(0x66000000, ModeOverrides.shade)

        preferences.values["focus_shade_work"] = "nope"
        ModeOverrides.recompute(preferences)
        assertEquals(0, ModeOverrides.shade)

        ModeOverrides.update(preferences, emptyMap())
        ModeOverrides.onShadeChanged = null
        assertEquals(0, ModeOverrides.shade)
        assertEquals(2, told)
    }

    @Test
    fun `a Mode's label surfaces replace the everywhere switches while it is on`() {
        var told = 0
        ModeOverrides.onLabelsChanged = { told++ }
        AppLabels.setHiddenSurfaces(AppLabels.HIDE_DRAWER)
        preferences.values["focus_labels_work"] = "0"
        ModeOverrides.update(preferences, mapOf("work" to 1L))
        assertEquals(0, AppLabels.hiddenSurfaces())

        preferences.values["focus_labels_work"] = "13"
        ModeOverrides.recompute(preferences)
        assertEquals(AppLabels.HIDE_HOME or AppLabels.HIDE_FOLDER, AppLabels.hiddenSurfaces())

        ModeOverrides.update(preferences, emptyMap())
        ModeOverrides.onLabelsChanged = null
        assertEquals(AppLabels.HIDE_DRAWER, AppLabels.hiddenSurfaces())
        AppLabels.setHiddenSurfaces(0)
        assertEquals(3, told)
    }

    @Test
    fun `the drawer section follows the winning Mode and its name`() {
        var told = 0
        ModeOverrides.onChanged = { told++ }
        preferences.values["focus_drawer_apps_work"] = "com.a,com.b"
        preferences.values["focus_name_work"] = "Work"
        ModeOverrides.update(preferences, mapOf("work" to 1L))
        assertEquals(setOf("com.a", "com.b"), ModeOverrides.drawerApps)
        assertEquals("Work", ModeOverrides.drawerTitle)

        ModeOverrides.update(preferences, emptyMap())
        ModeOverrides.onChanged = null
        assertEquals(emptySet<String>(), ModeOverrides.drawerApps)
        assertNull(ModeOverrides.drawerTitle)
        assertEquals(2, told)
    }

    @Test
    fun `a change of winner is reported once`() {
        var told = 0
        ModeOverrides.onChanged = { told++ }
        preferences.values["focus_hidden_apps_work"] = "com.b"
        ModeOverrides.update(preferences, mapOf("work" to 1L))
        ModeOverrides.update(preferences, mapOf("work" to 1L))
        ModeOverrides.onChanged = null
        assertEquals(1, told)
    }
}
