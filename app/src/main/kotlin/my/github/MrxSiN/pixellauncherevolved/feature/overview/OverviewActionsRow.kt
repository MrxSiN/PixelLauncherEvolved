package my.github.MrxSiN.pixellauncherevolved.feature.overview

import my.github.MrxSiN.pixellauncherevolved.core.Reflect
import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * The Overview action row, and the moments it is worth reading again.
 *
 * ```
 * com.android.quickstep.views.OverviewActionsView extends android.widget.FrameLayout
 *   public void onFinishInflate()
 *   public void updateForGroupedTask(boolean isGroupedTask)
 * ```
 *
 * Every feature that puts something in this row, or takes something out of it,
 * has to do so again whenever the launcher recomputes it; naming those moments
 * once means a launcher update moves one file rather than each of them.
 *
 * Up to Android 17 `CP2A.260805.005` the recompute was
 * `private void updateActionButtonsVisibility()`. `CP3A.260905.009` inlined it
 * into its only caller, `updateForGroupedTask(boolean)`, which still logs the
 * old method's name and is the same moment: the row has just been told which
 * task is selected and has finished deciding what it shows for it.
 */
internal object OverviewActionsRow {

    const val CLASS_NAME = "com.android.quickstep.views.OverviewActionsView"

    /** Null when this launcher has no such row, which leaves a feature uninstalled. */
    fun find(context: FeatureContext): Class<*>? = context.findClass(CLASS_NAME)

    /**
     * Runs [apply] on the row once it is inflated and after every recompute.
     *
     * A missing signature is reported by [FeatureContext.hookAfter] and skipped,
     * so a launcher that has moved one of the two still gets the other.
     *
     * The recompute is the `CP3A` `updateForGroupedTask(boolean)` when there is
     * one, and otherwise the earlier launcher's own
     * `updateActionButtonsVisibility()`, which `CP2A.260805.005` and AOSP
     * `android-17.0.0_r1` both have (the latter calls it from a two-argument
     * `updateForGroupedTask(boolean, boolean)`). Never both: on a launcher
     * that has both, the row would be applied twice per change for nothing.
     */
    fun onRecomputed(
        context: FeatureContext,
        row: Class<*>,
        apply: (thisObject: Any?, args: List<Any?>) -> Unit,
    ) {
        context.hookAfter(row, "onFinishInflate", after = apply)
        when (recompute(row)) {
            GROUPED_TASK -> context.hookAfter(row, GROUPED_TASK, Boolean::class.javaPrimitiveType!!, after = apply)
            BUTTONS_VISIBILITY -> context.hookAfter(row, BUTTONS_VISIBILITY, after = apply)
            else -> context.logger.warn("The Overview action row has no recompute this module knows; only its inflation is followed")
        }
    }

    /** Which recompute [row] has, as [onRecomputed] picks it; null for neither. */
    fun recompute(row: Class<*>): String? = when {
        Reflect.method(row, GROUPED_TASK, Boolean::class.javaPrimitiveType!!) != null -> GROUPED_TASK
        runCatching { row.getDeclaredMethod(BUTTONS_VISIBILITY) }.isSuccess -> BUTTONS_VISIBILITY
        else -> null
    }

    private const val GROUPED_TASK = "updateForGroupedTask"
    private const val BUTTONS_VISIBILITY = "updateActionButtonsVisibility"
}
