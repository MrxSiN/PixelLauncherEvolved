package com.android.launcher3.deviceprofile.parser

/** CP3A's grid spec stand-in: each profile gets its own copy from `mapTypeIndex`. See EarlierGeneration.kt. */
class DisplayOptionSpec(@JvmField val numShownHotseatIcons: Int) {
    fun mapTypeIndex(isTwoPanels: Boolean, isLandscape: Boolean): DisplayOptionSpec = DisplayOptionSpec(numShownHotseatIcons)
}
