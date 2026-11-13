package my.github.MrxSiN.pixellauncherevolved.feature.glance

import my.github.MrxSiN.pixellauncherevolved.feature.backup.SettingsBackupFormat
import my.github.MrxSiN.pixellauncherevolved.feature.backup.SettingsSnapshot
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceChoices
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceHideAfter
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceOptions
import my.github.MrxSiN.pixellauncherevolved.glance.CalendarWire
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlanceOptionsTest {

    private val ids = listOf("calendar", "calendar_next", "greeting")

    @Test
    fun keysSplitBackIntoIdAndName() {
        assertEquals("glance_option_calendar_next_hide_after", GlanceOptions.key("calendar_next", "hide_after"))
        assertEquals("calendar_next" to "hide_after", GlanceOptions.split("glance_option_calendar_next_hide_after", ids))
        assertEquals("calendar" to "calendars", GlanceOptions.split("glance_option_calendar_calendars", ids))
        assertEquals("greeting" to "name", GlanceOptions.split("glance_option_greeting_name", ids))
        assertNull(GlanceOptions.split("glance_option_unknown_name", ids))
        assertNull(GlanceOptions.split("glance_when_greeting", ids))
    }

    @Test
    fun backupsCarryTheOptionsAndAnOlderOneHasNone() {
        val choices = GlanceChoices(
            on = setOf("greeting"),
            options = mapOf("greeting" to mapOf("name" to "Sam", "hide_after" to "15"), "calendar" to mapOf("calendars" to "3")),
        )
        val read = SettingsBackupFormat.read(SettingsBackupFormat.write(SettingsSnapshot.DEFAULTS.copy(glance = choices)))
        assertEquals(choices, read.glance)
        assertEquals(emptyMap<String, Map<String, String>>(), SettingsBackupFormat.read("""{"version":1,"glance":{"on":["clock"]}}""").glance.options)
    }

    @Test
    fun hideMinutesReadsOnlyPositiveNumbers() {
        assertEquals(0, GlanceOptions.hideMinutes(null))
        assertEquals(0, GlanceOptions.hideMinutes("never"))
        assertEquals(0, GlanceOptions.hideMinutes("0"))
        assertEquals(30, GlanceOptions.hideMinutes("30"))
    }

    @Test
    fun contentExpiresAfterItsLastChangeAndAChangeRestartsIt() {
        val clock = GlanceHideAfter()
        val minute = GlanceHideAfter.MINUTE
        assertEquals(5 * minute, clock.check("a", 1, 0, 5))
        // Seen again later: the clock does not move.
        assertEquals(5 * minute, clock.check("a", 1, 4 * minute, 5))
        // New content starts over.
        assertEquals(9 * minute, clock.check("a", 2, 4 * minute, 5))
        // Nothing set: no expiry and nothing kept.
        assertEquals(GlanceHideAfter.NEVER, clock.check("a", 2, 5 * minute, 0))
        assertEquals(10 * minute, clock.check("a", 2, 5 * minute, 5))
        clock.retain(emptySet())
        assertEquals(20 * minute, clock.check("a", 2, 15 * minute, 5))
    }

    @Test
    fun aStoredStartSurvivesARestartAndIsWrittenOnlyOnChange() {
        val minute = GlanceHideAfter.MINUTE
        val disk = HashMap<String, String>()
        var writes = 0
        val persist: (String, String?) -> Unit = { id, value -> writes++; if (value == null) disk.remove(id) else disk[id] = value }
        val first = GlanceHideAfter(persist)
        first.check("a", 7, minute, 5)
        first.check("a", 7, 2 * minute, 5)
        assertEquals(1, writes)
        // A new launcher process reads the stored start back: the same content keeps its old clock.
        val second = GlanceHideAfter(persist)
        second.restore("a", disk["a"])
        assertEquals(6 * minute, second.check("a", 7, 3 * minute, 5))
        assertEquals(1, writes)
        // Nothing shown clears what is stored; clearing again writes nothing.
        second.forget("a")
        second.forget("a")
        assertEquals(2, writes)
        assertEquals(null, disk["a"])
        second.restore("b", "junk")
        assertEquals(GlanceHideAfter.NEVER, second.check("b", 1, 0, 0))
    }

    @Test
    fun calendarIdsParseAndAnythingElseMeansAll() {
        assertArrayEquals(longArrayOf(4, 12), CalendarWire.parseIds("4, 12"))
        assertArrayEquals(longArrayOf(), CalendarWire.parseIds(null))
        assertArrayEquals(longArrayOf(), CalendarWire.parseIds("x,"))
    }
}
