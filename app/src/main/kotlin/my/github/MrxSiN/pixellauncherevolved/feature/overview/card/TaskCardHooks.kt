package my.github.MrxSiN.pixellauncherevolved.feature.overview.card

import android.view.ViewGroup

import my.github.MrxSiN.pixellauncherevolved.hook.FeatureContext

/**
 * The three moments of a task card's life a decorated button cares about.
 *
 * A card is decorated when it is inflated, re-anchored whenever it is laid out,
 * and faded out as it grows back into a full screen app. Every button this
 * module puts on a card needs the same three, so they are hooked in one place
 * rather than once per button.
 */
fun FeatureContext.decorateTaskCards(taskView: Class<*>, decorator: TaskCardButtonDecorator) {
    hookAfter(taskView, "onFinishInflate") { card, _ ->
        decorator.onTaskViewInflated(card as ViewGroup)
    }

    hookAfter(
        taskView,
        "onLayout",
        Boolean::class.javaPrimitiveType!!,
        Int::class.javaPrimitiveType!!,
        Int::class.javaPrimitiveType!!,
        Int::class.javaPrimitiveType!!,
        Int::class.javaPrimitiveType!!,
    ) { card, _ -> decorator.onTaskViewLaidOut(card as ViewGroup) }

    hookAfter(
        taskView,
        "setFullscreenProgress",
        Float::class.javaPrimitiveType!!,
    ) { card, args -> decorator.onFullscreenProgress(card as ViewGroup, args[0] as Float) }
}
