package my.github.MrxSiN.pixellauncherevolved.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractAnalyzerTest {

    private val analyzer = ContractAnalyzer { name -> runCatching { Class.forName(name) }.getOrNull() }

    @Test
    fun `a method with matching parameters resolves`() {
        assertEquals(ContractOutcome.RESOLVED, check(Member.Method("add", listOf("java.lang.Object"))).outcome)
    }

    @Test
    fun `a member declared on a superclass resolves, private or not`() {
        assertEquals(ContractOutcome.RESOLVED, check(Member.Field("modCount")).outcome)
    }

    @Test
    fun `a method with other parameters is missing`() {
        assertEquals(ContractOutcome.MISSING_MEMBER, check(Member.Method("add", listOf("long"))).outcome)
    }

    @Test
    fun `a missing class is told apart from a missing member`() {
        val contract = LauncherContract(Signature("com.example.Gone", Member.Method("run")), setOf(CompatibilityFeature.SPLIT_SCREEN))

        assertEquals(ContractOutcome.MISSING_CLASS, analyzer.analyze(listOf(contract)).results.single().outcome)
    }

    @Test
    fun `an alternative that exists is a fallback`() {
        val contract = LauncherContract(
            Signature(LIST, Member.Method("updateActionButtonsVisibility")),
            setOf(CompatibilityFeature.OVERVIEW_ACTIONS),
            alternatives = listOf(Signature(LIST, Member.Method("trimToSize", emptyList()))),
        )

        val report = analyzer.analyze(listOf(contract))

        assertEquals(ContractOutcome.FALLBACK, report.results.single().outcome)
        assertEquals("ArrayList.trimToSize()", report.results.single().alternative?.describe())
        assertEquals(1, report.fallbacks)
        assertEquals(0, report.failed)
        assertTrue(report.isCompatible(CompatibilityFeature.OVERVIEW_ACTIONS))
    }

    @Test
    fun `only a missing required member makes a feature incompatible`() {
        val optional = LauncherContract(
            Signature(LIST, Member.Method("gone")),
            setOf(CompatibilityFeature.OVERVIEW_ONLY),
            isRequired = false,
        )
        val required = LauncherContract(Signature(LIST, Member.Method("gone")), setOf(CompatibilityFeature.TASKBAR_ONLY))

        val report = analyzer.analyze(listOf(optional, required))

        assertTrue(report.isCompatible(CompatibilityFeature.OVERVIEW_ONLY))
        assertFalse(report.isCompatible(CompatibilityFeature.TASKBAR_ONLY))
        assertEquals(2, report.failed)
    }

    @Test
    fun `the report reads the way a person checks it`() {
        val contract = LauncherContract(
            Signature(LIST, Member.Method("updateActionButtonsVisibility")),
            setOf(CompatibilityFeature.OVERVIEW_ACTIONS),
            alternatives = listOf(Signature(LIST, Member.Method("ensureCapacity", listOf("int")))),
        )

        val text = analyzer.analyze(listOf(contract)).format("907")

        assertTrue(text.startsWith("Pixel Launcher versionCode: 907"))
        assertTrue(
            text.contains("⚠ ArrayList.updateActionButtonsVisibility\n  Missing\n  Alternative found:\n  ArrayList.ensureCapacity(int)"),
        )
    }

    private fun check(member: Member): ContractResult = analyzer
        .analyze(listOf(LauncherContract(Signature(LIST, member), setOf(CompatibilityFeature.HIDDEN_APPS))))
        .results
        .single()

    private companion object {
        const val LIST = "java.util.ArrayList"
    }
}
