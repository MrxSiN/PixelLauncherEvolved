package my.github.MrxSiN.pixellauncherevolved.icons

import java.io.File

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconPackIndexTest {

    private fun index(): IconPackIndex {
        val components = KeyedIds().apply {
            put(ComponentHash.ofDescriptor("ComponentInfo{com.a/com.a.Main}"), 11)
            put(ComponentHash.ofDescriptor("ComponentInfo{com.b/.Start}"), 22)
            // Listed twice with two answers: neither is safe to keep.
            put(ComponentHash.ofDescriptor("com.c/com.c.X"), 33)
            put(ComponentHash.ofDescriptor("com.c/com.c.X"), 34)
        }
        components.collisions()
        val packages = KeyedIds().apply { put(ComponentHash.ofPackage("com.a"), 11) }
        val calendarDays = IntArray(IconPackIndex.CALENDAR_DAYS) { 100 + it }
        return IconPackIndex(
            "pack", 3,
            components.keys(), components.ids(),
            packages.keys(), packages.ids(),
            longArrayOf(ComponentHash.of("com.cal", "com.cal.Main")), calendarDays,
        )
    }

    @Test
    fun `components resolve exactly, abbreviated classes included`() {
        val index = index()

        assertEquals(11, index.drawableFor("com.a", "com.a.Main"))
        assertEquals(22, index.drawableFor("com.b", "com.b.Start"))
        assertEquals(0, index.drawableFor("com.c", "com.c.X"))
        assertEquals(0, index.drawableFor("com.z", "com.z.Main"))
    }

    @Test
    fun `a renamed activity falls back only to an unambiguous package entry`() {
        val index = index()

        assertEquals(11, index.drawableFor("com.a", "com.a.Renamed"))
        assertEquals(0, index.drawableFor("com.b", "com.b.Renamed"))
    }

    @Test
    fun `a calendar answers by day`() {
        val index = index()

        assertEquals(100, index.calendarFor("com.cal", "com.cal.Main", 0))
        assertEquals(130, index.calendarFor("com.cal", "com.cal.Main", 30))
        assertEquals(0, index.calendarFor("com.a", "com.a.Main", 3))
    }

    @Test
    fun `an index read back from disk is the same, and a stale one is refused`() {
        val file = File.createTempFile("pack", ".idx").apply { deleteOnExit() }
        index().write(file)

        val read = IconPackIndex.read(file, "pack", 3)!!
        assertEquals(11, read.drawableFor("com.a", "com.a.Main"))
        assertEquals(115, read.calendarFor("com.cal", "com.cal.Main", 15))
        assertNull(IconPackIndex.read(file, "pack", 4))
        assertNull(IconPackIndex.read(file, "another", 3))
    }
}
