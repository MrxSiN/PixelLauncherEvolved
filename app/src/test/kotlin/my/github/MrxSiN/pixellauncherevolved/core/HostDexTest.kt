package my.github.MrxSiN.pixellauncherevolved.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HostDexTest {

    @Test
    fun `fingerprint text survives every character the file splits on`() {
        for (s in listOf("", "plain", "tab\there", "line\nbreak\r", "sep\u0001item", "back\\slash\\t", "\\", "\\1\u0001")) {
            val escaped = HostDex.esc(s)
            assertFalse(escaped.contains('\t') || escaped.contains('\n') || escaped.contains('\u0001'))
            assertEquals(s, HostDex.unesc(escaped))
        }
    }
}
