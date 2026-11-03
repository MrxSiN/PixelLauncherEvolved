package com.android.launcher3

import com.android.launcher3.deviceprofile.DeviceProperties
import com.android.launcher3.deviceprofile.HotseatProfile
import com.android.launcher3.deviceprofile.WorkspaceProfile

/*
 * Test stand-ins for launcher classes, shaped the way AOSP android-17.0.0_r1
 * declares them (the earlier launcher generation), with CP3A's own classes
 * beside them in the deviceprofile packages. A test plays one generation by
 * hiding the other's classes from its class loader. None of this claims what
 * a real Pixel 11 Pro launcher holds; it only exercises the module's choices.
 */

class InvariantDeviceProfile {

    /** android-17.0.0_r1: `DisplayOptionSpec(InvariantDeviceProfile, boolean isTwoPanels, boolean isLandscape)`. */
    class DisplayOptionSpec(inv: InvariantDeviceProfile, isTwoPanels: Boolean, isLandscape: Boolean) {
        @JvmField val numShownHotseatIcons: Int = if (isTwoPanels) 6 else 4
        @JvmField val numAllAppsColumns: Int = if (isLandscape) 6 else 4

        /** A second constructor, as for a secondary display, which is never the one hooked. */
        constructor(columns: Int) : this(InvariantDeviceProfile(), false, columns > 4)
    }
}

class DeviceProfile {
    private val mDeviceProperties = DeviceProperties()
    private val mHotseatProfile = HotseatProfile()
    private val mWorkspaceProfile = WorkspaceProfile()

    /** CP3A's name for the part, of another type here: it must not be taken for the part. */
    @JvmField val workspaceProfile: String = "not a WorkspaceProfile"

    /** android-17.0.0_r1 asks the profile itself. */
    fun isVerticalBarLayout(): Boolean = true

    /** android-17.0.0_r1, CP2A: the builder nested in the profile. */
    class Builder {
        fun build(): DeviceProfile = DeviceProfile()
    }
}
