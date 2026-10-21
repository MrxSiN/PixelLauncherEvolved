package my.github.MrxSiN.pixellauncherevolved.focus

import java.io.File
import java.time.Instant
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
/**
 * @param user the Android user whose Modes are read: the one this process, and so
 *   the launcher asking it, runs as. Root sees every user's Modes in one dump.
 */
class ZenModes(private val user: Int, private val shell: CommandRunner = RootShell()) {

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

    /** Serialises reads, so concurrent callers share one root shell rather than each starting their own. */
    private val lock = Any()
    private var last: FocusSnapshot? = null
    private var lastStartedAt = 0L

    /**
     * One notification-service read for a coherent access-and-Modes result.
     *
     * A caller that waited while another read ran takes that read's answer when
     * it began after the caller asked: it is as fresh as one of its own.
     */
    fun snapshot(): FocusSnapshot {
        val askedAt = System.nanoTime()
        synchronized(lock) {
            last?.takeIf { lastStartedAt >= askedAt }?.let { return it }
            lastStartedAt = System.nanoTime()
            return read().also { last = it }
        }
    }

    /** A failure is said as one, never as an empty list of Modes. */
    private fun read(): FocusSnapshot = when (val result = shell.run(DUMP_COMMAND)) {
        is RootResult.Failed -> FocusSnapshot(isReadable = false, modes = emptyList(), failure = result.reason)
        is RootResult.Output -> result.text
            .substringBefore(CONFIG_END)
            .let(::ownConfig)
            ?.takeIf { RULE_START in it }
            ?.let { FocusSnapshot(isReadable = true, modes = parseModes(it)) }
            ?: FocusSnapshot(isReadable = false, modes = emptyList(), failure = FocusFailure.MALFORMED)
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
        val activatedAt = activatedAt(block)
        return if (id == MANUAL_ID) {
            FocusMode(id = id, name = MANUAL_NAME, isActive = isActive, icon = MANUAL_ICON, isEnabled = isEnabled, activatedAt = activatedAt)
        } else {
            FocusMode(id = id, name = name, isActive = isActive, icon = icon(block), isEnabled = isEnabled, activatedAt = activatedAt)
        }
    }

    /**
     * When the rule last came on, by its own schedule (`lastActivation`) or by
     * hand (`lastManualActivation`), whichever is later; 0 when it never has
     * or the dump has neither.
     */
    private fun activatedAt(block: String): Long = ACTIVATION.findAll(block)
        .mapNotNull { runCatching { Instant.parse(it.groupValues[1]).toEpochMilli() }.getOrNull() }
        .maxOrNull() ?: 0L

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
     * This user's block of the configurations, which the dump prints one per
     * user (`mConfigs[u=0]=…`, `mConfigs[u=10]=…`). Reading them all would hand
     * one user's Modes to another user's launcher, a work profile's included.
     * Null when the dump has per-user blocks but none for this user: unknown,
     * not empty. A dump without per-user blocks is taken whole, as before.
     */
    private fun ownConfig(configs: String): String? {
        if (USER_CONFIG !in configs) return configs
        val start = configs.indexOf("$USER_CONFIG$user]=")
        if (start < 0) return null
        val end = configs.indexOf(USER_CONFIG, start + 1)
        return if (end < 0) configs.substring(start) else configs.substring(start, end)
    }

    private companion object {
        /**
         * `--zen` does not narrow the dump on `CP3A.261005.005`: it is the whole
         * notification service, 1.35 MB. `sed` keeps the Zen section up to the
         * first `mUser=` line, 15 KB, measured; the time is the dump's either way.
         *
         * That section prints the configuration once per user, then `mUser=`, then
         * the current user's again, then a log of changes in which a rule that came
         * on hours ago still reads as on. Only what comes before `mUser=` is read.
         */
        const val DUMP_COMMAND = "dumpsys notification --zen | sed -n '/Zen Mode:/,/mUser=/p'"

        /** The line that follows the first configuration block. */
        const val CONFIG_END = "mUser="
        const val RULE_START = "ZenRule["
        const val USER_CONFIG = "mConfigs[u="
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

        /** `lastActivation=` and `lastManualActivation=`, each an `Instant` or `null`. */
        val ACTIVATION = Regex("""last(?:Manual)?Activation=(\d{4}-[^,\]]+)""")

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
    fun run(command: String): RootResult
}

/** What a root command came back with: everything it printed, or why there is nothing. */
sealed interface RootResult {
    class Output(val text: String) : RootResult
    class Failed(val reason: FocusFailure) : RootResult
}

/**
 * @param globalNamespace run in the global mount namespace (`--mount-master`, which
 *   KernelSU and Magisk both take). From an app, root otherwise inherits the app's
 *   own view of `/data`, in which other apps' data is hidden.
 */
class RootShell(private val globalNamespace: Boolean = false) : CommandRunner {

    /**
     * Bounded at every step. The output goes to a file rather than a pipe, so
     * waiting is the one thing that can take time and it gives up after
     * [TIMEOUT_SECONDS]: reading a pipe first, as this used to, waited for as
     * long as anything held it open, timeout or not.
     *
     * ponytail: [Process.destroyForcibly] ends `su`; a command it already started
     * may run on until it finishes by itself. Fine for a dump that ends on its own.
     */
    override fun run(command: String): RootResult {
        val output = runCatching { File.createTempFile("root", null) }
            .getOrElse { return RootResult.Failed(FocusFailure.REFUSED) }
        val process = runCatching {
            val su = if (globalNamespace) listOf("su", "--mount-master", "-c", command) else listOf("su", "-c", command)
            ProcessBuilder(su).redirectErrorStream(true).redirectOutput(output).start()
        }.getOrElse {
            output.delete()
            return RootResult.Failed(FocusFailure.REFUSED)
        }

        return try {
            process.outputStream.close()
            when {
                !process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) -> RootResult.Failed(FocusFailure.TIMEOUT)
                // Refused by the root manager, or the command itself failed: no way to tell apart.
                process.exitValue() != 0 -> RootResult.Failed(FocusFailure.REFUSED)
                output.length() > MAX_BYTES -> RootResult.Failed(FocusFailure.TOO_LARGE)
                else -> RootResult.Output(output.readText())
            }
        } catch (error: Throwable) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            RootResult.Failed(FocusFailure.REFUSED)
        } finally {
            process.destroyForcibly()
            output.delete()
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L

        /** Far above the 15 KB a filtered dump is, far below what a Binder answer can carry. */
        const val MAX_BYTES = 256L * 1024
    }
}
