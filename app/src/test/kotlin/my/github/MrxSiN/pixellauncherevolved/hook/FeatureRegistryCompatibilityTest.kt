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
        val settingsOnly = setOf(CompatibilityFeature.ORGANIZE_PAGES)

        assertEquals(CompatibilityFeature.entries.toSet() - settingsOnly, gated)
    }
}
