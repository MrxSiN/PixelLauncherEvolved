package my.github.MrxSiN.pixellauncherevolved.lock

import java.util.concurrent.TimeUnit

/** Turns the screen off without exposing how the privileged action is performed. */
fun interface ScreenLocker {
    fun lock(): Boolean
}

/** Sends Android's power-key event through the device's root shell. */
class RootScreenLocker : ScreenLocker {

    override fun lock(): Boolean {
        val process = runCatching {
            ProcessBuilder("su", "-c", POWER_KEY_COMMAND)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return false

        return try {
            process.outputStream.close()
            val completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) process.destroy()
            completed && process.exitValue() == 0
        } catch (_: Throwable) {
            process.destroy()
            false
        }
    }

    private companion object {
        const val POWER_KEY_COMMAND = "input keyevent KEYCODE_POWER"
        const val TIMEOUT_SECONDS = 15L
    }
}
