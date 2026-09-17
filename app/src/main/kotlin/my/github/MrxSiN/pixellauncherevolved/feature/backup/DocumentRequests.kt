package my.github.MrxSiN.pixellauncherevolved.feature.backup

import android.app.Activity
import android.content.Intent
import android.net.Uri

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * Asks the system file picker for a document, from inside Home settings.
 *
 * The picker answers through `onActivityResult` of the activity that asked, and
 * that activity is the launcher's own settings screen, which this module cannot
 * subclass. `Activity.onActivityResult` is where every answer arrives that the
 * activity's own result registry did not claim, and a request code of this
 * module's is never one of those, so the answer is taken there.
 *
 * A pending request lives in this process only. Should the launcher be ended
 * while the picker is open, the answer finds no one waiting and is dropped,
 * which costs the person one more tap.
 */
internal object DocumentRequests {

    private val pending = ConcurrentHashMap<Int, (Uri?) -> Unit>()
    private val nextCode = AtomicInteger(FIRST_CODE)

    fun install(context: FeatureContext) {
        val onResult = runCatching {
            Activity::class.java.getDeclaredMethod(
                "onActivityResult",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Intent::class.java,
            )
        }.getOrNull()
        if (onResult == null) {
            context.logger.warn("Activity results are unreachable; settings cannot be exported or imported")
            return
        }

        context.xposed.hook(onResult).intercept { chain ->
            val result = chain.proceed()
            val code = chain.args.getOrNull(0) as? Int
            val callback = code?.let(pending::remove)
            if (callback != null) {
                val ok = chain.args.getOrNull(1) == Activity.RESULT_OK
                val uri = (chain.args.getOrNull(2) as? Intent)?.data
                runCatching { callback(uri.takeIf { ok }) }
                    .onFailure { context.logger.warn("A document request could not be finished", it) }
            }
            result
        }
    }

    /** Starts [intent] from [activity], and calls [onResult] with the chosen document or null. */
    fun launch(activity: Activity, intent: Intent, onResult: (Uri?) -> Unit) {
        val code = nextCode.getAndIncrement()
        pending[code] = onResult
        runCatching { activity.startActivityForResult(intent, code) }
            .onFailure {
                pending.remove(code)
                onResult(null)
            }
    }

    /** Low enough for an androidx activity, which refuses codes above 16 bits. */
    private const val FIRST_CODE = 0x5000
}
