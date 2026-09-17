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
class ZenModes(private val shell: CommandRunner = RootShell()) {

    /** Whether Modes can be read at all, which here means whether root answers. */
    fun isGranted(): Boolean = snapshot().isReadable

    /**
     * Every Mode that exists, each saying whether it is on now and whether it
     * is switched on in Settings at all.
     *
     * A Mode switched off is still reported. It cannot come on, so it is not
     * offered pages, but it has not gone either: leaving it out made it look
     * deleted, and a deleted Mode's pages are given back.
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
        val isEnabled = ENABLED.find(block)?.groupValues?.get(1) == "TRUE"

        // A manual choice overrides the rule owner's condition. Android keeps
        // the underlying state unchanged, so the override must win explicitly.
        val isActive = when (OVERRIDE.find(block)?.groupValues?.get(1)) {
            "OVERRIDE_ACTIVATE" -> true
            "OVERRIDE_DEACTIVATE" -> false
            else -> STATE.find(block)?.groupValues?.get(1) in ACTIVE_STATES
        } && isEnabled

        // The manual rule is Do Not Disturb turned on by hand. It carries no
        // name or icon of its own, so it is given the ones Settings shows for it.
        return if (id == MANUAL_ID) {
            FocusMode(id = id, name = MANUAL_NAME, isActive = isActive, icon = MANUAL_ICON, isEnabled = isEnabled)
        } else {
            FocusMode(id = id, name = name, isActive = isActive, icon = icon(block), isEnabled = isEnabled)
        }
    }

    /**
     * The icon Settings draws beside a Mode.
     *
     * A Mode someone picked an icon for names it. One that did not is drawn
     * from its type, with the framework's own drawable for that type.
     */
    private fun icon(block: String): String? {
        ICON.find(block)?.groupValues?.get(1)?.takeIf { it != "null" }?.let { return it }
        val type = TYPE.find(block)?.groupValues?.get(1)?.toIntOrNull()
        return FRAMEWORK_DRAWABLE + (TYPE_ICONS[type] ?: TYPE_OTHER_ICON)
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

        const val FRAMEWORK_DRAWABLE = "android:drawable/"
        const val MANUAL_ICON = FRAMEWORK_DRAWABLE + "ic_zen_mode_type_special_dnd"
        const val TYPE_OTHER_ICON = "ic_zen_mode_type_other"

        /** `AutomaticZenRule.TYPE_*`, to the drawable Settings uses for each. */
        val TYPE_ICONS = mapOf(
            1 to "ic_zen_mode_type_schedule_time",
            2 to "ic_zen_mode_type_schedule_calendar",
            3 to "ic_zen_mode_type_bedtime",
            4 to "ic_zen_mode_type_driving",
            5 to "ic_zen_mode_type_immersive",
            6 to "ic_zen_mode_type_theater",
            7 to "ic_zen_mode_type_managed",
        )

        val ID = Regex("""^id=([^,]+),""")
        val STATE = Regex("""state=(STATE_[A-Z_]+)""")
        val ENABLED = Regex("""enabled=([A-Z]+)""")
        val OVERRIDE = Regex("""conditionOverride=(OVERRIDE_[A-Z_]+)""")
        val ICON = Regex("""iconResName=([^,\]]+)""")
        val TYPE = Regex("""[,\[]type=(-?\d+)""")
        val ACTIVE_STATES = setOf("STATE_TRUE", "STATE_UNKNOWN")

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
fun interface CommandRunner {
    fun run(command: String): String?
}

class RootShell : CommandRunner {

    /** Null when root is unavailable, refused, or the command did not finish. */
    override fun run(command: String): String? {
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
