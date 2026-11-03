package com.android.launcher3.deviceprofile.parser

/**
 * android-17.0.0_r1's display option stand-in: options are listed here, but
 * the weighted pick is private `InvariantDeviceProfile` code, so there is no
 * `parseWeightedPredefinedDisplayOption`. See EarlierGeneration.kt.
 */
class DisplayOption {
    companion object {
        @JvmStatic
        fun getPredefinedDisplayOptions(): List<DisplayOption> = emptyList()
    }
}
