package my.github.MrxSiN.pixellauncherevolved.focus

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
}
