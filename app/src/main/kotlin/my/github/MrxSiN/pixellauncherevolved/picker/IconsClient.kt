package my.github.MrxSiN.pixellauncherevolved.picker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper

import java.util.concurrent.Executor
import java.util.concurrent.Executors

import my.github.MrxSiN.pixellauncherevolved.bridge.IconsBridge
import my.github.MrxSiN.pixellauncherevolved.core.Logger

/**
 * Asks the launcher about icon packs ([IconsBridge]), off the UI thread.
 *
 * Every call is a Binder transaction into the launcher, and an apply waits for
 * the launcher to redraw its icons, so they run one at a time on a worker of
 * their own and answer on the main thread. A call the launcher cannot take
 * answers with null: the launcher is restarting, or this module is not active
 * in it.
 */
internal class IconsClient(private val context: Context, private val logger: Logger) {

    private val main = Handler(Looper.getMainLooper())
    private val uri = Uri.parse("content://${IconsBridge.AUTHORITY}")

    fun call(method: String, arg: String? = null, extras: Bundle? = null, answer: (Bundle?) -> Unit) = worker.execute {
        val result = runCatching { context.contentResolver.call(uri, method, arg, extras) }
            .onFailure { logger.warn("Picker: the launcher did not answer $method", it) }
            .getOrNull()
        main.post { answer(result) }
    }

    companion object {
        /** One worker for every page, so an apply and the reads after it keep their order. */
        private val worker: Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ple-picker-icons").apply { isDaemon = true }
        }

        /** A picture the launcher drew, as a drawable. */
        fun drawable(context: Context, bytes: ByteArray?): Drawable? = bytes
            ?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
            ?.let { bitmap: Bitmap -> BitmapDrawable(context.resources, bitmap) }
    }
}
