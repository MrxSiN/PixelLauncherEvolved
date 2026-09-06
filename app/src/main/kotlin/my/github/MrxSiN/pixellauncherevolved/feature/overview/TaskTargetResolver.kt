package my.github.MrxSiN.pixellauncherevolved.feature.overview

import android.content.Intent
import android.os.Process
import android.os.UserHandle
import android.view.View

import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble.BubbleTarget

/** Turns an Overview task card into the application it represents. */
interface TaskTargetResolver {

    /** Returns null when the card has no bubbleable application. */
    fun resolve(taskView: View): BubbleTarget?
}

/**
 * Reads the recorded task of a `com.android.quickstep.views.TaskView`.
 *
 * A task keeps the intent that started it, which is exactly what the shell
 * needs to re-open the application inside a bubble. Resolution runs on every
 * layout pass, so the reflective members are looked up once and reused.
 */
class TaskViewTargetResolver(private val logger: Logger) : TaskTargetResolver {

    private val firstTaskMethods = HashMap<Class<*>, Method>()
    private val fields = HashMap<String, Field>()

    override fun resolve(taskView: View): BubbleTarget? = try {
        val task = firstTask(taskView)
        val key = task?.let { field(it, "key").get(it) }
        val baseIntent = key?.let { field(it, "baseIntent").get(it) as Intent? }

        if (key == null || baseIntent?.component == null) {
            null
        } else {
            BubbleTarget(
                intent = bubbleIntent(baseIntent),
                user = user(field(key, "userId").getInt(key)),
            )
        }
    } catch (error: Throwable) {
        logger.warn("Unable to read the task behind an Overview card", error)
        null
    }

    private fun firstTask(taskView: View): Any? {
        val method = synchronized(firstTaskMethods) {
            firstTaskMethods.getOrPut(taskView.javaClass) {
                // Declared on TaskView but reached through its subclasses too.
                taskView.javaClass.getMethod("getFirstTask")
            }
        }
        return method.invoke(taskView)
    }

    private fun field(owner: Any, name: String): Field = synchronized(fields) {
        fields.getOrPut("${owner.javaClass.name}#$name") {
            owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
        }
    }

    /**
     * The shell resolves the bubbled activity by package, and a recorded base
     * intent carries only a component, so the package is restated here.
     */
    private fun bubbleIntent(baseIntent: Intent): Intent = Intent(baseIntent).apply {
        if (`package` == null) `package` = component!!.packageName
    }

    private fun user(userId: Int): UserHandle = try {
        UserHandle::class.java.getDeclaredMethod("of", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(null, userId) as UserHandle
    } catch (error: Throwable) {
        logger.warn("Falling back to the current user for user id $userId", error)
        Process.myUserHandle()
    }
}
