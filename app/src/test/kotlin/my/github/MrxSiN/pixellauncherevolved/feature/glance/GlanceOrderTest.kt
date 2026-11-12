package my.github.MrxSiN.pixellauncherevolved.feature.glance

import my.github.MrxSiN.pixellauncherevolved.feature.backup.SettingsBackupFormat
import my.github.MrxSiN.pixellauncherevolved.feature.backup.SettingsSnapshot
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceChoices
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlancePrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class GlanceOrderTest {

    @Test
    fun orderedPutsChosenIdsFirstAndKeepsTheRestInPlace() {
        val ids = listOf("torch", "alarm", "battery", "clock")
        assertEquals(ids, GlancePrefs.ordered(ids, emptyList()) { it })
        assertEquals(listOf("clock", "battery", "torch", "alarm"), GlancePrefs.ordered(ids, listOf("clock", "battery")) { it })
        assertEquals(listOf("alarm", "torch", "battery", "clock"), GlancePrefs.ordered(ids, listOf("gone", "alarm")) { it })
    }

    @Test
    fun backupsCarryTheChoices() {
        val choices = GlanceChoices(on = setOf("battery", "clock"), whens = mapOf("alarm" to "morning"), order = listOf("clock", "battery"))
        val read = SettingsBackupFormat.read(SettingsBackupFormat.write(SettingsSnapshot.DEFAULTS.copy(glance = choices)))
        assertEquals(choices, read.glance)
    }

    @Test
    fun anOlderBackupReadsAsNoContent() {
        val read = SettingsBackupFormat.read("""{"version":1}""")
        assertEquals(GlanceChoices(), read.glance)
    }
}
