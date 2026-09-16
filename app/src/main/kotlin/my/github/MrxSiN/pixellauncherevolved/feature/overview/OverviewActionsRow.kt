package my.github.MrxSiN.pixellauncherevolved.feature.overview

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
     */
    fun onRecomputed(
        context: FeatureContext,
        row: Class<*>,
        apply: (thisObject: Any?, args: List<Any?>) -> Unit,
    ) {
        context.hookAfter(row, "onFinishInflate", after = apply)
        context.hookAfter(
            row,
            "updateForGroupedTask",
            Boolean::class.javaPrimitiveType!!,
            after = apply,
        )
    }
}
