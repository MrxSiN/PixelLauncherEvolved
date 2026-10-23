package my.github.MrxSiN.pixellauncherevolved.feature.stacks

/**
 * Which home screen widgets are stacked, as stored.
 *
 * A stack is an ordered list of the launcher's own item ids (`favorites._id`)
 * and the one of them on show. Every member stays an ordinary widget row in the
 * launcher's database: the one on show sits where the stack is, and each other
 * one sits alone at the top of a page of its own that the home screen does not
 * show ([hiddenScreens]). Nothing here is written to the database, and nothing
 * the launcher reads depends on it, so without this module those pages simply
 * appear, one widget each, and no widget is lost.
 *
 * Each member may name a part of the day ([Slot]) when Smart rotate brings it
 * forward. Item ids belong to this launcher database, so stacks never travel in
 * a backup.
 *
 * Stored as `2;shown:id@slot,id@slot|...`; version 1 (`1;shown:id,id`, no slots)
 * still reads, with every slot empty. A value this cannot read is no stacks.
 */
internal class StackGraph(val stacks: List<Stack>) {

    class Stack(val members: IntArray, val shown: Int, val slots: IntArray = IntArray(members.size)) {
        val shownId: Int get() = members[shown]

        val isFull: Boolean get() = members.size >= MAX_MEMBERS

        fun indexOf(id: Int): Int = members.indexOf(id)

        fun showing(index: Int) = Stack(members, index, slots)

        /** With [id] at the end, its slot empty. */
        fun plus(id: Int) = Stack(members + id, shown, slots + 0)

        /** Member [from] moved to [to], the one on show still on show. */
        fun moved(from: Int, to: Int): Stack {
            val ids = members.toMutableList()
            val parts = slots.toMutableList()
            val shownId = shownId
            ids.add(to, ids.removeAt(from))
            parts.add(to, parts.removeAt(from))
            val order = ids.toIntArray()
            return Stack(order, order.indexOf(shownId), parts.toIntArray())
        }

        fun withSlot(index: Int, slot: Int) = Stack(members, shown, slots.copyOf().also { it[index] = slot })

        fun without(id: Int): Stack? {
            val at = indexOf(id)
            if (at < 0) return this
            val left = members.filterIndexed { i, _ -> i != at }.toIntArray()
            if (left.size < 2) return null
            val parts = slots.filterIndexed { i, _ -> i != at }.toIntArray()
            val current = shownId
            return Stack(left, left.indexOf(if (current == id) left[0] else current).coerceAtLeast(0), parts)
        }
    }

    /** Parts of the day Smart rotate knows; [NONE] is a member it never brings forward. */
    object Slot {
        const val NONE = 0
        const val MORNING = 1
        const val AFTERNOON = 2
        const val EVENING = 3
        const val NIGHT = 4

        /** The part of the day [hour] (0..23) falls in. */
        fun at(hour: Int): Int = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> NIGHT
        }
    }

    /** Where one item is, as validation needs it; null for an item that is not a home screen widget. */
    class Placement(val screen: Int, val aloneOnScreen: Boolean)

    fun format(): String = VERSION + ";" + stacks.joinToString("|") { stack ->
        "${stack.shown}:" + stack.members.indices.joinToString(",") { "${stack.members[it]}@${stack.slots[it]}" }
    }

    /** Whether [other] stores exactly as this does (same text from [format]), without formatting either. */
    fun sameAs(other: StackGraph): Boolean {
        val mine = stacks
        val theirs = other.stacks
        if (mine.size != theirs.size) return false
        for (i in mine.indices) {
            val a = mine[i]
            val b = theirs[i]
            if (a.shown != b.shown || !a.members.contentEquals(b.members) || !a.slots.contentEquals(b.slots)) return false
        }
        return true
    }

    /** The stack [id] is in; an index walk, as drag moves and touches ask it. */
    fun stackOf(id: Int): Stack? {
        val all = stacks
        for (i in all.indices) if (all[i].indexOf(id) >= 0) return all[i]
        return null
    }

    fun replace(old: Stack?, new: Stack?): StackGraph =
        StackGraph(stacks.mapNotNull { if (it === old) new else it } + listOfNotNull(new.takeIf { old == null }))

    /**
     * The stacks the launcher's items still support, and the pages they hide.
     *
     * Only ever hides less than is stored. A member that is gone, is not a home
     * screen widget, or shares its page with anything is left out of its stack,
     * and so shown again; a stack whose widget on show is gone is taken apart.
     * Nothing is moved or deleted to make the stored stacks fit.
     */
    fun validate(placement: (Int) -> Placement?): Resolved {
        val kept = ArrayList<Stack>(stacks.size)
        val hidden = ArrayList<Int>()
        val anchors = ArrayList<Int>()
        val seen = HashSet<Int>()
        for (stack in stacks) {
            val shownId = stack.shownId
            val shownAt = placement(shownId)
            if (shownAt == null || !seen.add(shownId)) continue
            val members = ArrayList<Int>(stack.members.size)
            val parts = ArrayList<Int>(stack.members.size)
            val screens = ArrayList<Int>(stack.members.size)
            var shown = -1
            for ((index, id) in stack.members.withIndex()) {
                if (members.size >= MAX_MEMBERS) break
                if (id == shownId) {
                    shown = members.size
                    members += id
                    parts += stack.slots[index]
                    continue
                }
                val at = placement(id) ?: continue
                if (!at.aloneOnScreen || at.screen == shownAt.screen || at.screen in hidden || at.screen in screens) continue
                if (!seen.add(id)) continue
                members += id
                parts += stack.slots[index]
                screens += at.screen
            }
            if (members.size < 2 || shown < 0) {
                seen.removeAll(members.toSet())
                continue
            }
            kept += Stack(members.toIntArray(), shown, parts.toIntArray())
            hidden += screens
            anchors += shownId
        }
        return Resolved(StackGraph(kept), hidden.toIntArray().apply { sort() }, anchors.toIntArray().apply { sort() })
    }

    class Resolved(val graph: StackGraph, val hiddenScreens: IntArray, val anchors: IntArray)

    companion object {
        const val VERSION = "2"
        private const val VERSION_1 = "1"

        /** As on iOS: more than this and a stack stops being quicker than a page. */
        const val MAX_MEMBERS = 10

        val EMPTY = StackGraph(emptyList())

        fun parse(stored: String?): StackGraph {
            if (stored.isNullOrEmpty()) return EMPTY
            val version = stored.substringBefore(';')
            val body = stored.substringAfter(';', "")
            if ((version != VERSION && version != VERSION_1) || body.isEmpty()) return EMPTY
            return StackGraph(body.split('|').mapNotNull { entry ->
                val shown = entry.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
                val ids = ArrayList<Int>()
                val parts = ArrayList<Int>()
                for (token in entry.substringAfter(':', "").split(',')) {
                    val id = token.substringBefore('@').toIntOrNull() ?: continue
                    if (id in ids) continue
                    ids += id
                    parts += (token.substringAfter('@', "0").toIntOrNull() ?: 0).takeIf { it in Slot.NONE..Slot.NIGHT } ?: 0
                }
                if (ids.size < 2 || shown !in ids.indices) null else Stack(ids.toIntArray(), shown, parts.toIntArray())
            })
        }
    }
}
