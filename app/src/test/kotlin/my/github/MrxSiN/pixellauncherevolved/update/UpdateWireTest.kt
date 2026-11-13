package my.github.MrxSiN.pixellauncherevolved.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateWireTest {

    @Test
    fun theTagIsReadOffTheReleasePage() {
        assertEquals("v0.1.8", UpdateWire.tagOf("https://github.com/MrxSiN/PixelLauncherEvolved/releases/tag/v0.1.8"))
        assertEquals("v1.0.0", UpdateWire.tagOf("https://github.com/x/y/releases/tag/v1.0.0/?a=b"))
        // No release yet: GitHub sends the releases list, which names no tag.
        assertNull(UpdateWire.tagOf("https://github.com/MrxSiN/PixelLauncherEvolved/releases"))
        assertNull(UpdateWire.tagOf(null))
    }

    @Test
    fun onlyAHigherVersionIsNewer() {
        assertTrue(UpdateWire.isNewer("v0.1.8", "0.1.7"))
        assertTrue(UpdateWire.isNewer("v0.2", "0.1.7"))
        assertTrue(UpdateWire.isNewer("1.0.0", "0.9.9"))
        assertFalse(UpdateWire.isNewer("v0.1.7", "0.1.7"))
        assertFalse(UpdateWire.isNewer("v0.1.6", "0.1.7"))
        assertFalse(UpdateWire.isNewer("v0.1.7-beta", "0.1.7"))
        assertFalse(UpdateWire.isNewer("nightly", "0.1.7"))
    }
}
