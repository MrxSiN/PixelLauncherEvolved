package my.github.MrxSiN.pixellauncherevolved.feature.glance

import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources.more.WidgetChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetChoiceTest {

    @Test
    fun choiceRoundTrips() {
        val value = WidgetChoice.encode("com.example/.Provider", 42)
        assertEquals("com.example/.Provider|42", value)
        assertEquals("com.example/.Provider" to 42, WidgetChoice.decode(value))
    }

    @Test
    fun unreadableChoicesDecodeToNull() {
        assertNull(WidgetChoice.decode(null))
        assertNull(WidgetChoice.decode(""))
        assertNull(WidgetChoice.decode("com.example/.Provider"))
        assertNull(WidgetChoice.decode("|5"))
        assertNull(WidgetChoice.decode("com.example/.Provider|x"))
        assertNull(WidgetChoice.decode("com.example/.Provider|-1"))
    }

    @Test
    fun hostIdIsNeverTheLaunchersAndIsStable() {
        val id = WidgetChoice.hostId("my.github.MrxSiN.pixellauncherevolved")
        assertNotEquals(1024, id)
        assertEquals(id, WidgetChoice.hostId("my.github.MrxSiN.pixellauncherevolved"))
        assertEquals(0x4000_0000, id and 0x7F00_0000)
    }

    @Test
    fun cardSizeIsScreenWidthLessMarginsAndOneColumnTall() {
        assertEquals(360 - 32 to (360 - 32) / 4, WidgetChoice.cardSize(1080, 3f))
    }
}
