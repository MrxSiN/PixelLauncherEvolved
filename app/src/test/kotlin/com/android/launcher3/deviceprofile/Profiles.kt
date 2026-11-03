package com.android.launcher3.deviceprofile

import com.android.launcher3.DeviceProfile

/* Test stand-ins; see EarlierGeneration.kt. */

class DeviceProperties {
    @JvmField val taskbarConfiguration = TaskbarConfiguration(false)
}

/** android-17.0.0_r1, CP2A: a one-field class built through its own constructor. */
class TaskbarConfiguration(@JvmField val isTaskbarPresent: Boolean)

class HotseatProfile

class WorkspaceProfile

/** CP3A's builder, hidden from a test that plays the earlier launcher. */
class DeviceProfileBuilder {
    fun build(): DeviceProfile = DeviceProfile()
}
