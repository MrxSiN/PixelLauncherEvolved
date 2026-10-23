package my.github.MrxSiN.pixellauncherevolved.feature.stacks

import android.content.SharedPreferences
import android.util.SparseArray
import android.util.SparseIntArray

import java.lang.reflect.Field

/**
 * The stacks as the home screen uses them, rebuilt at each workspace bind.
 *
 * Hot paths read only [anchors] and [hiddenScreens], sorted arrays replaced
 * whole, so a touch or a bind filter costs a binary search and no allocation.
 * Both are empty while the feature is off, which shows every page again.
 */
internal object WidgetStacks {

    const val KEY = "widget_stacks"

    /** Item ids of the widget on show in each stack. */
    @Volatile
    @JvmField
    var anchors: IntArray = IntArray(0)

    /** Pages holding a widget waiting in a stack, which the home screen does not show. */
    @Volatile
    @JvmField
    var hiddenScreens: IntArray = IntArray(0)

    @Volatile
    var graph: StackGraph = StackGraph.EMPTY
        private set

    /** Null until the feature is installed; set once. */
    @Volatile
    var preferences: SharedPreferences? = null

    /** Told at each workspace bind, on the UI thread, that every widget view is new. */
    @Volatile
    var onBound: () -> Unit = {}

    /** The switch as last read, for paths too frequent to read a preference ([StackDrop]). */
    @Volatile
    @JvmField
    var on: Boolean = false

    /** Whether the switch is on; read at each bind. */
    @Volatile
    var enabled: () -> Boolean = { false }

    /** The page's choices as last read, kept current by the feature's preference listener. */
    @Volatile
    @JvmField
    var sideways: Boolean = false

    @Volatile
    @JvmField
    var indicatorMode: Int = 0

    @Volatile
    @JvmField
    var remember: Boolean = true

    @Volatile
    @JvmField
    var smartRotate: Boolean = false

    /**
     * Puts every stacked widget back on the home screen on its own; the number put back,
     * or null with no home screen to do it on. Set while a launcher runs, for settings.
     */
    @Volatile
    var restoreAll: (() -> Int?)? = null

    /** When the person last chose a widget in each stack, by member id; UI thread only. */
    private val chosenAt = android.util.SparseLongArray()

    /** The person has just chosen what [stack] shows. */
    fun chosen(stack: StackGraph.Stack) {
        val now = android.os.SystemClock.elapsedRealtime()
        for (id in stack.members) chosenAt.put(id, now)
    }

    /** How long ago the person last chose what [stack] shows; a long time when never in this process. */
    fun sinceChosen(stack: StackGraph.Stack): Long {
        var latest = 0L
        for (id in stack.members) latest = maxOf(latest, chosenAt.get(id))
        return if (latest == 0L) Long.MAX_VALUE else android.os.SystemClock.elapsedRealtime() - latest
    }

    /** "Widget stack. Calendar. 2 of 4.", for TalkBack; set by the feature with its own strings. */
    @Volatile
    var describe: (CharSequence, Int, Int) -> String = { label, at, of -> "$label, $at of $of" }

    fun isAnchor(id: Int): Boolean = anchors.isNotEmpty() && anchors.binarySearch(id) >= 0

    fun isHidden(screen: Int): Boolean = hiddenScreens.isNotEmpty() && hiddenScreens.binarySearch(screen) >= 0

    /**
     * Works out the stacks from the items the launcher is about to bind.
     *
     * Called by the bind filter ([my.github.MrxSiN.pixellauncherevolved.feature.focus.FocusHomeFeature])
     * before it decides which pages show. A stored stack the items no longer
     * support is written back without what it lost, so it is not tried again.
     */
    fun onBind(items: SparseArray<*>, fields: ItemFields) {
        onBound()
        val prefs = preferences
        if (prefs == null || !enabled()) {
            clear()
            return
        }
        val text = prefs.getString(KEY, null)
        val stored = if (text == storedText) storedGraph else StackGraph.parse(text).also {
            storedText = text
            storedGraph = it
        }
        if (stored.stacks.isEmpty()) {
            clear()
            graph = stored
            return
        }
        val perScreen = SparseIntArray()
        for (i in 0 until items.size()) {
            val item = items.valueAt(i) ?: continue
            if (fields.container.getInt(item) != CONTAINER_DESKTOP) continue
            val screen = fields.screenId.getInt(item)
            perScreen.put(screen, perScreen.get(screen) + 1)
        }
        val resolved = stored.validate { id ->
            val item = items.get(id) ?: return@validate null
            val type = fields.itemType.getInt(item)
            if (fields.container.getInt(item) != CONTAINER_DESKTOP || (type != TYPE_WIDGET && type != TYPE_CUSTOM_WIDGET)) {
                null
            } else {
                val screen = fields.screenId.getInt(item)
                StackGraph.Placement(screen, aloneOnScreen = perScreen.get(screen) == 1)
            }
        }
        publish(resolved)
        if (!resolved.graph.sameAs(stored)) prefs.edit().putString(KEY, resolved.graph.format()).apply()
    }

    /** The stored text as last parsed at a bind, and what it parsed to: parsed again only once it changes. */
    private var storedText: String? = null
    private var storedGraph: StackGraph = StackGraph.EMPTY

    /** Stores [next] and applies it to the arrays at once, for a change made on the home screen. */
    fun store(next: StackGraph, hidden: IntArray, anchorIds: IntArray) {
        publish(StackGraph.Resolved(next, hidden.copyOf().apply { sort() }, anchorIds.copyOf().apply { sort() }))
        preferences?.edit()?.putString(KEY, next.format())?.apply()
    }

    private fun publish(resolved: StackGraph.Resolved) {
        graph = resolved.graph
        hiddenScreens = resolved.hiddenScreens
        anchors = resolved.anchors
    }

    private fun clear() {
        graph = StackGraph.EMPTY
        hiddenScreens = IntArray(0)
        anchors = IntArray(0)
    }

    class ItemFields(val screenId: Field, val container: Field, val itemType: Field)

    /** `LauncherSettings.Favorites` constants, inlined by the launcher's compiler. */
    const val CONTAINER_DESKTOP = -100
    const val TYPE_WIDGET = 4
    const val TYPE_CUSTOM_WIDGET = 5
}
