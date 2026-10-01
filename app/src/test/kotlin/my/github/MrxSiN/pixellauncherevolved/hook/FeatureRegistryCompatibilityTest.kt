package my.github.MrxSiN.pixellauncherevolved.hook

import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every tweak on the compatibility page is gated by at least one feature, so a
 * tweak shown as unavailable is also one the registry does not install.
 */
class FeatureRegistryCompatibilityTest {

    @Test
    fun `every compatibility feature gates an installed feature`() {
        val gated = FeatureRegistry.features.mapNotNull { it.compatibility }.toSet()
        // DOCK_ICONS is checked by DockFeature itself, so its show/hide half installs without it;
        // GridFeature checks each of its four parts itself, so one missing part leaves the others.
        val settingsOnly = setOf(
            CompatibilityFeature.ORGANIZE_PAGES,
            CompatibilityFeature.DOCK_ICONS,
            CompatibilityFeature.GRID,
            CompatibilityFeature.GRID_ICON_SIZE,
            CompatibilityFeature.GRID_SPACING,
            CompatibilityFeature.APP_DRAWER_COLUMNS,
        )

        assertEquals(CompatibilityFeature.entries.toSet() - settingsOnly, gated)
    }
}
