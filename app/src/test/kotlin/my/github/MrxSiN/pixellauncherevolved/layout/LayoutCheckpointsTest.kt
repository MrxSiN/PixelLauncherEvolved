package my.github.MrxSiN.pixellauncherevolved.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutCheckpointsTest {

    @Test
    fun aManifestKeepsEveryValueAndItsType() {
        val manifest = Manifest(
            db = "launcher_ple_5_by_7.db",
            items = 41,
            launcher = mapOf("idp_grid_name" to "ple_custom", "migration_src_hotseat_count" to 4, "migration_src_workspace_size" to "5,7"),
            module = mapOf("home_grid_columns" to 5, "home_dock_hidden" to true, "home_dock_kept" to 0L, "odd" to 1.5f),
        )

        val read = requireNotNull(Manifest.decode(manifest.encode()))

        assertEquals("launcher_ple_5_by_7.db", read.db)
        assertEquals(41, read.items)
        assertEquals(manifest.launcher, read.launcher)
        assertEquals(manifest.module, read.module)
    }

    @Test
    fun aManifestNamingAPathOrMissingItsCountIsRefused() {
        val text = Manifest("launcher_4_by_6.db", 3, emptyMap<String, Any>(), emptyMap<String, Any>()).encode()

        assertNull(Manifest.decode(text.replace("launcher_4_by_6.db", "../../shared_prefs/x.db")))
        assertNull(Manifest.decode(text.replace("items=3", "items=three")))
    }

    @Test
    fun theLayoutDependsOnTheModuleOnlyForItsGridOrDock() {
        assertFalse(LayoutCheckpoints.dependsOnModule("launcher_4_by_6.db", dockHidden = false, dockIcons = 0))
        assertTrue(LayoutCheckpoints.dependsOnModule("launcher_ple_5_by_7.db", dockHidden = false, dockIcons = 0))
        assertTrue(LayoutCheckpoints.dependsOnModule("launcher_4_by_6.db", dockHidden = true, dockIcons = 0))
        assertTrue(LayoutCheckpoints.dependsOnModule("launcher_4_by_6.db", dockHidden = false, dockIcons = 6))
    }
}
