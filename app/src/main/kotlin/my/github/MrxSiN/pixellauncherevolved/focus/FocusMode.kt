package my.github.MrxSiN.pixellauncherevolved.focus

/**
 * One of the device's Modes, as this module needs to know it.
 *
 * Android calls these automatic zen rules. A person calls them Bedtime,
 * Driving, Sleeping — whatever they named them — and every one of them can be
 * on or off at any moment. This carries only what deciding a home screen needs:
 * which rule it is, what to call it on screen, and whether it is on now.
 *
 * @property id the rule's own identifier, which survives a rename. Assignments
 * are stored against this rather than the name, so renaming a mode in Settings
 * does not lose the pages given to it.
 * @property name what the person called it, used only for showing.
 * @property isActive whether the mode is on at the moment it was read.
 * @property icon the drawable Settings shows beside the Mode, as a
 * `package:drawable/name` resource name. Null when there is nothing to show.
 * @property isEnabled false for a Mode switched off in Settings. It still exists
 * and keeps its pages, but cannot come on, so it is not offered pages.
 */
data class FocusMode(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val icon: String? = null,
    val isEnabled: Boolean = true,
)
