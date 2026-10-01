package my.github.MrxSiN.pixellauncherevolved.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconOverrideFormatTest {

    private companion object {
        const val PACK = "com.example.pack"
    }

    private val main = IconOverride("com.example", "com.example.Main", 0, "example_icon", PACK)
    private val work = IconOverride("com.example", "com.example.Main", 10, null)
    private val other = IconOverride("org.other", "org.other.Start", 0, "other", PACK)

    @Test
    fun `overrides survive being written and read back`() {
        val text = IconOverrideFormat.write(listOf(other, work, main))

        assertEquals(setOf(main, work, other), IconOverrideFormat.read(text).toSet())
        // Sorted, so the same set is always the same text.
        assertEquals(text, IconOverrideFormat.write(listOf(main, other, work)))
    }

    @Test
    fun `an entry this version cannot read is skipped and the rest still load`() {
        val text = "garbage;x|a/b=c;0|a/b=nopack;" + IconOverrideFormat.write(listOf(main)) + ";0|nopackage=icon"

        assertEquals(listOf(main), IconOverrideFormat.read(text))
    }

    @Test
    fun `a choice for one profile does not reach another`() {
        val compiled = IconOverrideFormat.compile(listOf(main, work), PACK) { if (it == "example_icon") 7 else 0 }

        assertEquals(7, compiled.drawableFor("com.example", "com.example.Main", 0))
        assertEquals(IconOverrides.SYSTEM, compiled.drawableFor("com.example", "com.example.Main", 10))
        assertEquals(0, compiled.drawableFor("com.example", "com.example.Main", 11))
        assertEquals(0, compiled.drawableFor("com.example", "com.example.Other", 0))
    }

    @Test
    fun `an override naming a drawable the pack no longer has falls back to the pack`() {
        val compiled = IconOverrideFormat.compile(listOf(main), PACK) { 0 }

        assertEquals(0, compiled.drawableFor("com.example", "com.example.Main", 0))
        assertTrue(compiled.isEmpty())
    }

    @Test
    fun `the digest changes with a package's overrides and with nothing else`() {
        val resolve = { name: String -> name.length }
        val before = IconOverrideFormat.compile(listOf(main, other), PACK, resolve)
        val changed = IconOverrideFormat.compile(listOf(main.copy(drawable = "another"), other), PACK, resolve)

        assertEquals(0, before.digestOf("com.absent"))
        assertNotEquals(before.digestOf("com.example"), changed.digestOf("com.example"))
        assertEquals(before.digestOf("org.other"), changed.digestOf("org.other"))
    }

    @Test
    fun `a drawable chosen from one pack does not apply under another`() {
        val compiled = IconOverrideFormat.compile(listOf(main, work), "com.another.pack") { 7 }

        assertEquals(0, compiled.drawableFor("com.example", "com.example.Main", 0))
        // The stock icon is not pack artwork, so it stays chosen.
        assertEquals(IconOverrides.SYSTEM, compiled.drawableFor("com.example", "com.example.Main", 10))
    }

    @Test
    fun `the store keeps what it is given and forgets on clear`() {
        val store = IconOverrideStore(FakePreferences())
        store.replace(listOf(main, work))

        assertEquals(setOf(main, work), store.overrides().toSet())

        store.clear()
        assertEquals(emptyList<IconOverride>(), store.overrides())
    }
}
