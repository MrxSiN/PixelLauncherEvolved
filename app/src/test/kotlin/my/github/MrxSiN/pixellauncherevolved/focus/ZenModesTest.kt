package my.github.MrxSiN.pixellauncherevolved.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenModesTest {

    @Test
    fun manualOverrideDeterminesActiveState() {
        val dump = """
            ZenRule[id=active,state=STATE_FALSE,enabled=TRUE,conditionOverride=OVERRIDE_ACTIVATE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android],
            ZenRule[id=inactive,state=STATE_TRUE,enabled=TRUE,conditionOverride=OVERRIDE_DEACTIVATE,name=Sleep,zenMode=ZEN_MODE_IMPORTANT_INTERRUPTIONS,pkg=android],
            mUser=0
        """.trimIndent()

        val modes = ZenModes(CommandRunner { dump }).modes().associateBy { it.id }

        assertTrue(modes.getValue("active").isActive)
        assertFalse(modes.getValue("inactive").isActive)
    }

    @Test
    fun aModeSwitchedOffIsReportedButNeverActive() {
        val dump = """
            ZenRule[id=off,state=STATE_TRUE,enabled=FALSE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android],
            mUser=0
        """.trimIndent()

        val mode = ZenModes(CommandRunner { dump }).modes().single()

        assertFalse(mode.isEnabled)
        assertFalse(mode.isActive)
    }

    @Test
    fun iconIsTheChosenOneOrTheTypeDefault() {
        val dump = """
            ZenRule[id=picked,state=STATE_FALSE,enabled=TRUE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android,iconResName=android:drawable/ic_zen_mode_icon_work,type=0],
            ZenRule[id=typed,state=STATE_FALSE,enabled=TRUE,name=Driving,zenMode=ZEN_MODE_OFF,pkg=android,iconResName=null,type=4],
            ZenRule[id=MANUAL_RULE,state=STATE_FALSE,enabled=TRUE,name=null,zenMode=ZEN_MODE_OFF,pkg=android,iconResName=null,type=0],
            mUser=0
        """.trimIndent()

        val modes = ZenModes(CommandRunner { dump }).modes().associateBy { it.id }

        assertEquals("android:drawable/ic_zen_mode_icon_work", modes.getValue("picked").icon)
        assertEquals("android:drawable/ic_zen_mode_type_driving", modes.getValue("typed").icon)
        assertEquals("android:drawable/ic_zen_mode_type_special_dnd", modes.getValue("MANUAL_RULE").icon)
    }

    @Test
    fun theModeThatCameOnLastWins() {
        val dump = """
            ZenRule[id=work,state=STATE_TRUE,enabled=TRUE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android,lastActivation=2026-10-01T08:00:00Z,lastManualActivation=null],
            ZenRule[id=driving,state=STATE_FALSE,enabled=TRUE,conditionOverride=OVERRIDE_ACTIVATE,name=Driving,zenMode=ZEN_MODE_OFF,pkg=android,lastActivation=2026-09-30T08:00:00Z,lastManualActivation=2026-10-01T09:30:00.123Z],
            mUser=0
        """.trimIndent()

        val active = ZenModes(CommandRunner { dump }).modes().filter { it.isActive }.associate { it.id to it.activatedAt }
        val pages = mapOf("work" to setOf(2), "driving" to setOf(1))

        assertEquals(1790847000123L, active.getValue("driving"))
        // Driving came on last, so it wins whatever the order says.
        assertEquals("driving", FocusPlan.winner(pages, priority = listOf("work", "driving"), active = active))
        // Unknown times fall back on the order.
        assertEquals("work", FocusPlan.winner(pages, priority = listOf("work", "driving"), active = mapOf("work" to 0L, "driving" to 0L)))
    }
}
