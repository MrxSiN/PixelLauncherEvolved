package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.graphics.Rect
import android.view.View

import java.lang.reflect.Method

/**
 * Reports the thumbnail area of an Overview task card.
 *
 * A task card is larger than the snapshot it shows, so anything anchored to a
 * card corner has to use the thumbnail rectangle the card computes during
 * layout rather than the card's own bounds.
 */
class TaskViewGeometry {

    private val methods = HashMap<Class<*>, Method>()

    /** Fills [outBounds] in task card coordinates. */
    fun thumbnailBounds(taskView: View, outBounds: Rect) {
        outBounds.set(0, 0, taskView.width, taskView.height)

        runCatching {
            val method = synchronized(methods) {
                methods.getOrPut(taskView.javaClass) {
                    taskView.javaClass.getMethod("getThumbnailBounds", Rect::class.java)
                }
            }

            val measured = Rect()
            method.invoke(taskView, measured)
            if (!measured.isEmpty) outBounds.set(measured)
        }
    }
}
