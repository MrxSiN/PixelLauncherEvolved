package my.github.MrxSiN.pixellauncherevolved.feature.focus

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.HardwareRenderer
import android.graphics.RenderNode

import my.github.MrxSiN.pixellauncherevolved.core.Reflect

/**
 * Draws into a hardware bitmap rather than a software one.
 *
 * Launcher views and widget layouts hold hardware bitmaps of their own, which a
 * software canvas refuses to draw. Recording into a render node and asking the
 * renderer for the result draws them as the screen would.
 */
internal object HardwarePicture {

    fun record(width: Int, height: Int, draw: (Canvas) -> Unit): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val node = RenderNode("Pixel Launcher Evolved picture").apply { setPosition(0, 0, width, height) }
        val canvas = node.beginRecording(width, height)
        try {
            draw(canvas)
        } finally {
            node.endRecording()
        }
        return runCatching {
            Reflect.method(
                HardwareRenderer::class.java,
                CREATE_HARDWARE_BITMAP,
                RenderNode::class.java,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )?.invoke(null, node, width, height) as? Bitmap
        }.getOrNull()
    }

    private const val CREATE_HARDWARE_BITMAP = "createHardwareBitmap"
}
