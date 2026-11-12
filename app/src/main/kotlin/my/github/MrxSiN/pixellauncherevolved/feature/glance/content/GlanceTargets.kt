package my.github.MrxSiN.pixellauncherevolved.feature.glance.content

import android.app.smartspace.SmartspaceAction
import android.app.smartspace.SmartspaceTarget
import android.app.smartspace.uitemplatedata.BaseTemplateData
import android.app.smartspace.uitemplatedata.TapAction
import android.app.smartspace.uitemplatedata.Text
import android.content.ComponentName
import android.os.Process

import android.app.smartspace.uitemplatedata.Icon as TemplateIcon

/**
 * Turns [GlanceItem]s into the framework's `SmartspaceTarget`s and merges them into the list the
 * launcher's At a Glance view is given.
 *
 * Rewritten from Smartspacer's `TargetTemplate.Basic`, `ComplicationTemplate.Basic` and the
 * default `TargetMerger`: a card is its own page with a header action and a default template; an
 * extra fills the first free item beside the date on the first page that has a template.
 */
internal class GlanceTargets(private val component: ComponentName) {

    /** `SmartspaceTarget.FEATURE_*` and `UI_TEMPLATE_DEFAULT`, read once from the framework, not inlined. */
    private val constants = HashMap<String, Int>()

    private fun constant(name: String): Int = constants.getOrPut(name) {
        runCatching { SmartspaceTarget::class.java.getField(name).getInt(null) }.getOrDefault(0)
    }

    fun card(id: String, item: GlanceItem): SmartspaceTarget {
        val header = SmartspaceAction.Builder("${id}_header", item.title.toString())
            .setSubtitle(item.subtitle)
            .setIcon(item.icon)
            .setPendingIntent(item.tap)
            .setIntent(item.tapIntent)
            .build()
        val template = BaseTemplateData.Builder(constant(UI_TEMPLATE_DEFAULT))
            .setPrimaryItem(subItem("${id}_primary", item.title, null, item))
            .setSubtitleItem(subItem("${id}_subtitle", item.subtitle ?: "", item.icon, item))
            .build()
        // The framework refuses a sub-item with no text, icon or tap, so a card with only a title
        // (a greeting, say) leaves its subtitle out rather than passing an empty one.
        return SmartspaceTarget.Builder(id, component, Process.myUserHandle())
            .setFeatureType(constant(item.feature))
            .setHeaderAction(header)
            .setBaseAction(SmartspaceAction.Builder("${id}_base", "").build())
            .setTemplateData(template)
            .build()
    }

    /**
     * [targets] with [cards] after the first page and [extras] beside the date, or [targets] itself
     * when there is nothing to add. Never changes [targets] or what it holds.
     */
    fun merge(targets: List<*>, cards: List<SmartspaceTarget>, extras: List<Pair<String, GlanceItem>>): List<*> {
        if (cards.isEmpty() && extras.isEmpty()) return targets
        // A card the system already shows a card of the same kind for (Pixel's own torch card, say)
        // is left out rather than shown twice.
        val undefined = constant(FEATURE_UNDEFINED)
        val cards = cards.filter { card ->
            card.featureType == undefined || targets.none { it is SmartspaceTarget && it.featureType == card.featureType }
        }
        val out = ArrayList<Any?>(targets.size + cards.size)
        var pending = extras
        for (index in targets.indices) {
            var target = targets[index]
            if (pending.isNotEmpty() && target is SmartspaceTarget && target.templateData != null) {
                val (placed, left) = withExtras(target, pending)
                target = placed
                pending = left
            }
            out += target
            if (index == 0) out.addAll(cards)
        }
        if (targets.isEmpty()) out.addAll(cards)
        return out
    }

    /** [target] with as many [extras] as it has free items for, and the extras left over. */
    private fun withExtras(target: SmartspaceTarget, extras: List<Pair<String, GlanceItem>>): Pair<SmartspaceTarget, List<Pair<String, GlanceItem>>> {
        val data = target.templateData ?: return target to extras
        val queue = ArrayDeque(extras)
        fun next() = queue.removeFirstOrNull()?.let { (id, item) -> subItem(id, item.title, item.icon, item) }
        val subtitle = data.subtitleItem ?: next()
        val supplemental = data.subtitleSupplementalItem ?: next()
        if (queue.size == extras.size) return target to extras

        val rebuilt = BaseTemplateData.Builder(data.templateType)
            .setPrimaryItem(data.primaryItem)
            .setSubtitleItem(subtitle)
            .setSubtitleSupplementalItem(supplemental)
            .setSupplementalLineItem(data.supplementalLineItem)
            .setSupplementalAlarmItem(data.supplementalAlarmItem)
            .setLayoutWeight(data.layoutWeight)
            .build()
        return copy(target, rebuilt) to queue.toList()
    }

    private fun copy(target: SmartspaceTarget, data: BaseTemplateData): SmartspaceTarget =
        SmartspaceTarget.Builder(target.smartspaceTargetId, target.componentName, target.userHandle)
            .setHeaderAction(target.headerAction)
            .setBaseAction(target.baseAction)
            .setCreationTimeMillis(target.creationTimeMillis)
            .setExpiryTimeMillis(target.expiryTimeMillis)
            .setScore(target.score)
            .setActionChips(target.actionChips)
            .setIconGrid(target.iconGrid)
            .setFeatureType(target.featureType)
            .setSensitive(target.isSensitive)
            .setShouldShowExpanded(target.shouldShowExpanded())
            .setSourceNotificationKey(target.sourceNotificationKey)
            .setAssociatedSmartspaceTargetId(target.associatedSmartspaceTargetId)
            .setSliceUri(target.sliceUri)
            .setWidget(target.widget)
            .setTemplateData(data)
            .setRemoteViews(target.remoteViews)
            .build()

    private fun subItem(id: String, text: CharSequence, icon: android.graphics.drawable.Icon?, item: GlanceItem): BaseTemplateData.SubItemInfo? {
        if (text.isEmpty() && icon == null) return null
        val builder = BaseTemplateData.SubItemInfo.Builder().setText(Text.Builder(text).build())
        if (icon != null) builder.setIcon(TemplateIcon.Builder(icon).setShouldTint(item.tintIcon).build())
        if (item.tap != null || item.tapIntent != null) {
            builder.setTapAction(TapAction.Builder(id).setPendingIntent(item.tap).setIntent(item.tapIntent).build())
        }
        return builder.build()
    }

    private companion object {
        const val UI_TEMPLATE_DEFAULT = "UI_TEMPLATE_DEFAULT"
        const val FEATURE_UNDEFINED = "FEATURE_UNDEFINED"
    }
}
