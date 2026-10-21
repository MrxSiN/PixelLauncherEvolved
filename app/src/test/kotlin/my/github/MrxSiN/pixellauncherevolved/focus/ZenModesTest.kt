package my.github.MrxSiN.pixellauncherevolved.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenModesTest {

    @Test
    fun manualOverrideDeterminesActiveState() {
        val dump = dump(
            "ZenRule[id=active,state=STATE_FALSE,enabled=TRUE,conditionOverride=OVERRIDE_ACTIVATE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android]",
            "ZenRule[id=inactive,state=STATE_TRUE,enabled=TRUE,conditionOverride=OVERRIDE_DEACTIVATE,name=Sleep,zenMode=ZEN_MODE_IMPORTANT_INTERRUPTIONS,pkg=android]",
        )

        val modes = zen(dump).modes().associateBy { it.id }

        assertTrue(modes.getValue("active").isActive)
        assertFalse(modes.getValue("inactive").isActive)
    }

    /** Whether a rule is on, row for row as `ZenRule.isActive` decides it on `CP3A.261005.005`. */
    @Test
    fun activeStateFollowsTheFrameworksTruthTable() {
        val rows = listOf(
            Triple("OVERRIDE_ACTIVATE", "STATE_FALSE", true),
            Triple("OVERRIDE_DEACTIVATE", "STATE_TRUE", false),
            Triple("OVERRIDE_NONE", "STATE_TRUE", true),
            Triple("OVERRIDE_NONE", "STATE_UNKNOWN", true),
            Triple("OVERRIDE_NONE", "STATE_FALSE", false),
            Triple("OVERRIDE_NONE", "STATE_ERROR", false),
        )
        val dump = dump(*rows.mapIndexed { index, (override, state, _) ->
            "ZenRule[id=r$index,state=$state,enabled=TRUE,conditionOverride=$override,name=R$index,zenMode=ZEN_MODE_OFF,pkg=android]"
        }.toTypedArray(), "ZenRule[id=off,state=STATE_TRUE,enabled=FALSE,conditionOverride=OVERRIDE_ACTIVATE,name=Off,zenMode=ZEN_MODE_OFF,pkg=android]")

        val active = zen(dump).modes().associate { it.id to it.isActive }

        rows.forEachIndexed { index, (override, state, on) -> assertEquals("$override/$state", on, active.getValue("r$index")) }
        // A Mode switched off in Settings is off whatever its override or condition.
        assertFalse(active.getValue("off"))
    }

    @Test
    fun aModeSwitchedOffIsReportedButNeverActive() {
        val dump = dump("ZenRule[id=off,state=STATE_TRUE,enabled=FALSE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android]")

        val mode = zen(dump).modes().single { it.id == "off" }

        assertFalse(mode.isEnabled)
        assertFalse(mode.isActive)
    }

    @Test
    fun iconIsTheChosenOneOrTheTypeDefault() {
        val dump = dump(
            "ZenRule[id=picked,state=STATE_FALSE,enabled=TRUE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android,iconResName=android:drawable/ic_zen_mode_icon_work,type=0]",
            "ZenRule[id=typed,state=STATE_FALSE,enabled=TRUE,name=Driving,zenMode=ZEN_MODE_OFF,pkg=android,iconResName=null,type=4]",
        )

        val modes = zen(dump).modes().associateBy { it.id }

        assertEquals("android:drawable/ic_zen_mode_icon_work", modes.getValue("picked").icon)
        assertEquals("android:drawable/ic_zen_mode_type_driving", modes.getValue("typed").icon)
        assertEquals("android:drawable/ic_zen_mode_type_special_dnd", modes.getValue("MANUAL_RULE").icon)
    }

    @Test
    fun theModeThatCameOnLastWins() {
        val dump = dump(
            "ZenRule[id=work,state=STATE_TRUE,enabled=TRUE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android,lastActivation=2026-10-01T08:00:00Z,lastManualActivation=null]",
            "ZenRule[id=driving,state=STATE_FALSE,enabled=TRUE,conditionOverride=OVERRIDE_ACTIVATE,name=Driving,zenMode=ZEN_MODE_OFF,pkg=android,lastActivation=2026-09-30T08:00:00Z,lastManualActivation=2026-10-01T09:30:00.123Z]",
        )

        val active = zen(dump).modes().filter { it.isActive }.associate { it.id to it.activatedAt }
        val pages = mapOf("work" to setOf(2), "driving" to setOf(1))

        assertEquals(1790847000123L, active.getValue("driving"))
        // Driving came on last, so it wins whatever the order says.
        assertEquals("driving", FocusPlan.winner(pages, priority = listOf("work", "driving"), active = active))
        // Unknown times fall back on the order.
        assertEquals("work", FocusPlan.winner(pages, priority = listOf("work", "driving"), active = mapOf("work" to 0L, "driving" to 0L)))
    }

    @Test
    fun onlyThisUsersModesAreRead() {
        val dump = config(0, "ZenRule[id=mine,state=STATE_TRUE,enabled=TRUE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android]") +
            config(10, "ZenRule[id=theirs,state=STATE_TRUE,enabled=TRUE,name=Clinic,zenMode=ZEN_MODE_OFF,pkg=android]") +
            USER_LINE

        assertEquals(listOf("mine"), zen(dump, user = 0).modes().map { it.id } - MANUAL)
        assertEquals(listOf("theirs"), zen(dump, user = 10).modes().map { it.id } - MANUAL)
        // A user with no block of its own is unknown, never another user's Modes or none.
        assertEquals(FocusFailure.MALFORMED, zen(dump, user = 1).snapshot().failure)
    }

    /** A Mode deleted in Settings is listed under deletedRules, and is not one of the Modes. */
    @Test
    fun deletedModesAreNotModes() {
        val dump = config(
            0,
            "ZenRule[id=kept,state=STATE_FALSE,enabled=TRUE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android]",
            deleted = "ZenRule[id=gone,state=STATE_TRUE,enabled=TRUE,name=Old,zenMode=ZEN_MODE_OFF,pkg=android]",
        ) + USER_LINE

        val snapshot = zen(dump).snapshot()

        assertEquals(FocusStatus.VALID, snapshot.status)
        assertEquals(listOf("kept", MANUAL), snapshot.modes.map { it.id }.sortedBy { it == MANUAL })
    }

    /** A dump cut short, or laid out differently, is partial: never a complete list of fewer Modes. */
    @Test
    fun aTruncatedOrDriftedDumpIsPartial() {
        val whole = dump("ZenRule[id=a,state=STATE_TRUE,enabled=TRUE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android]")

        // Stopped before the user line.
        assertEquals(FocusStatus.PARTIAL, zen(whole.substringBefore("mUser=")).snapshot().status)
        // Stopped inside the configuration, before the manual and deleted rules.
        assertEquals(FocusStatus.PARTIAL, zen(whole.substringBefore("manualRule=") + USER_LINE).snapshot().status)
        // A build that names the section differently.
        assertEquals(FocusStatus.PARTIAL, zen(whole.replace("automaticRules=", "rules=")).snapshot().status)
        assertEquals(FocusStatus.PARTIAL, zen("").snapshot().status)
    }

    /** The Modes changing during a read: read again once, then said as stale. */
    @Test
    fun aReadDuringAChangeIsReadAgainThenStale() {
        val dump = dump("ZenRule[id=a,state=STATE_TRUE,enabled=TRUE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android]")

        var reads = 0
        // Before and after each read: changed across the first, steady across the second.
        val tags = ArrayDeque(listOf("a", "b", "b", "b"))
        val settles = ZenModes(0, CommandRunner { reads++; RootResult.Output(dump) }) { tags.removeFirst() }
        assertEquals(FocusStatus.VALID, settles.snapshot().status)
        assertEquals(2, reads)

        var counter = 0
        val churning = ZenModes(0, CommandRunner { RootResult.Output(dump) }) { (counter++).toString() }
        val stale = churning.snapshot()
        assertEquals(FocusStatus.STALE, stale.status)
        assertFalse(stale.isReadable)
        assertTrue(stale.modes.isEmpty())
    }

    @Test
    fun aFailedReadSaysWhyAndNeverReadsAsNoModes() {
        for ((reason, status) in listOf(
            FocusFailure.TIMEOUT to FocusStatus.UNAVAILABLE,
            FocusFailure.REFUSED to FocusStatus.UNAVAILABLE,
            FocusFailure.TOO_LARGE to FocusStatus.PARTIAL,
        )) {
            val snapshot = ZenModes(0, CommandRunner { RootResult.Failed(reason) }).snapshot()
            assertFalse(snapshot.isReadable)
            assertEquals(reason, snapshot.failure)
            assertEquals(status, snapshot.status)
        }
    }

    @Test
    fun concurrentReadsShareOneShell() {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val runs = java.util.concurrent.atomic.AtomicInteger()
        val dump = dump("ZenRule[id=a,state=STATE_TRUE,enabled=TRUE,name=Work,zenMode=ZEN_MODE_OFF,pkg=android]")
        val zen = ZenModes(0, CommandRunner {
            if (runs.incrementAndGet() == 1) {
                started.countDown()
                release.await()
            }
            RootResult.Output(dump)
        })

        val first = Thread { zen.snapshot() }.apply { start() }
        started.await()
        // Asked while the first read runs: it waits for that read and then runs one of its own,
        // because the one in flight began before it asked.
        val second = Thread { zen.snapshot() }.apply { start() }
        val third = Thread { zen.snapshot() }.apply { start() }
        Thread.sleep(100)
        release.countDown()
        listOf(first, second, third).forEach { it.join() }

        // Second and third began asking together, so one shell answers both.
        assertEquals(2, runs.get())
    }

    private fun zen(dump: String, user: Int = 0) = ZenModes(user, CommandRunner { RootResult.Output(dump) })

    private companion object {
        const val MANUAL = "MANUAL_RULE"
        const val USER_LINE = "    mUser=0\n"

        /** The Zen section of a one-user dump, laid out as `CP3A.261005.005` prints it. */
        fun dump(vararg rules: String) = config(0, *rules) + USER_LINE

        fun config(user: Int, vararg rules: String, deleted: String = "") =
            "    mConfigs[u=$user]=ZenModeConfig[user=$user\nautomaticRules={\n" + rules.joinToString(",\n") + "}\n" +
                "manualRule=ZenRule[id=MANUAL_RULE,state=STATE_FALSE,enabled=TRUE,conditionOverride=OVERRIDE_NONE,name=null,zenMode=ZEN_MODE_OFF,pkg=android,iconResName=null,type=0],\n" +
                "deletedRules={" + (if (deleted.isEmpty()) "" else "\n$deleted") + "}]\n"
    }
}
