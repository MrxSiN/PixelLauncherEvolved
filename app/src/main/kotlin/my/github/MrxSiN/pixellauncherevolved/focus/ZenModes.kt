package my.github.MrxSiN.pixellauncherevolved.focus

import java.util.concurrent.TimeUnit

/**
 * The device's Modes, read through the root shell.
 *
 * The platform will not tell an ordinary app what Modes exist. `NotificationManager`
 * has `getAutomaticZenRules`, and Do Not Disturb access is enough to call it,
 * but since Android 15 it answers with the rules the calling app itself owns and
 * nothing else. Every Mode a person actually has belongs to `android`, to
 * Wellbeing, to GMS or to Settings Intelligence, so the call comes back empty
 * with no error to explain why — measured on Android 17 as `granted=true
 * rules=0 error=null`.
 *
 * What does know is the notification service's own dump, and root can read it.
 * This module already asks for root for Double Tap to Sleep, so this asks the
 * same way. Without root there are no Modes to offer and the feature says so,
 * rather than silently offering one Mode and pretending that is all of them.
 */
class ZenModes(private val shell: RootShell = RootShell()) {

    /** Whether Modes can be read at all, which here means whether root answers. */
    fun isGranted(): Boolean = snapshot().isReadable

    /**
     * Every Mode, each saying whether it is on now.
     *
     * A Mode switched off in Settings is left out: it cannot come on, so
     * offering to give it a page would be offering a page that never shows.
     */
    fun modes(): List<FocusMode> {
        return snapshot().modes
    }

    /** One notification-service read for a coherent access-and-Modes result. */
    fun snapshot(): FocusSnapshot {
        val dump = dump()
        return if (dump == null) {
            FocusSnapshot(isReadable = false, modes = emptyList())
        } else {
            FocusSnapshot(isReadable = true, modes = parseModes(dump))
        }
    }

    private fun parseModes(dump: String): List<FocusMode> = dump.split(RULE_START)
            .drop(1)
            .mapNotNull(::parse)
            .distinctBy { it.id }
            .sortedBy { it.name }

    /**
     * One `ZenRule[...]` from the dump.
     *
     * The fields are read by name rather than by position, and the name is read
     * up to the field that always follows it, because a Mode someone renamed can
     * hold any character at all including the comma the fields are split on.
     */
    private fun parse(block: String): FocusMode? {
        val id = ID.find(block)?.groupValues?.get(1) ?: return null
        val name = NAME.find(block)?.groupValues?.get(1)?.trim() ?: return null
        if (ENABLED.find(block)?.groupValues?.get(1) != "TRUE") return null

        val isActive = STATE.find(block)?.groupValues?.get(1) == "STATE_TRUE"

        // The manual rule is Do Not Disturb turned on by hand. It carries no
        // name of its own, so it is given the one Settings shows for it.
        return if (id == MANUAL_ID) {
            FocusMode(id = id, name = MANUAL_NAME, isActive = isActive)
        } else {
            FocusMode(id = id, name = name, isActive = isActive)
        }
    }

    /**
     * The part of the dump that describes the Modes as they stand.
     *
     * The dump says everything more than once. It prints the configuration
     * twice, once per user and once for the current user, and then a log of
     * changes in which a rule that came on hours ago still reads as on. Reading
     * the whole thing would take whichever copy came first, which is how a Mode
     * that ended last night can look like a Mode that is running.
     *
     * Only the first configuration block is kept: it ends where the line naming
     * the user begins, and everything after that is either a copy of it or
     * history.
     */
    private fun dump(): String? = shell.run(DUMP_COMMAND)
        ?.substringBefore(CONFIG_END)
        ?.takeIf { RULE_START in it }

    private companion object {
        const val DUMP_COMMAND = "dumpsys notification --zen"

        /** The line that follows the first configuration block. */
        const val CONFIG_END = "mUser="
        const val RULE_START = "ZenRule["
        const val MANUAL_ID = "MANUAL_RULE"
        const val MANUAL_NAME = "Do Not Disturb"

        val ID = Regex("""^id=([^,]+),""")
        val STATE = Regex("""state=(STATE_[A-Z_]+)""")
        val ENABLED = Regex("""enabled=([A-Z]+)""")

        /**
         * The name, up to the field the dump always writes after it.
         *
         * Non-greedy, so a Mode called "Work, focus" is read whole rather than
         * cut at its own comma.
         */
        val NAME = Regex("""name=(.*?),zenMode=""")
    }
}

/** Runs one command as root and hands back what it printed. */
class RootShell {

    /** Null when root is unavailable, refused, or the command did not finish. */
    fun run(command: String): String? {
        val process = runCatching {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        }.getOrNull() ?: return null

        return try {
            process.outputStream.close()
            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (!completed) {
                process.destroy()
                null
            } else {
                output.takeIf { process.exitValue() == 0 }
            }
        } catch (_: Throwable) {
            process.destroy()
            null
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
    }
}
