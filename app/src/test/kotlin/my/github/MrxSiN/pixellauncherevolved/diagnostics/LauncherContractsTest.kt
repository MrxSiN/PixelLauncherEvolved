package my.github.MrxSiN.pixellauncherevolved.diagnostics

import java.io.File

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holds the contract list to the code it describes.
 *
 * Every contract names a class and a member some feature looks up. A hook moved
 * in a feature and not in the list would leave the compatibility page checking
 * something nothing uses, so each name has to appear in the sources outside
 * the diagnostics packages.
 */
class LauncherContractsTest {

    private val sources: String = File("src/main/kotlin")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" && "diagnostics" !in it.invariantSeparatorsPath.split('/') }
        .joinToString("\n") { it.readText() }

    @Test
    fun `every contract names what a feature looks up`() {
        val signatures = LauncherContracts.all.flatMap { listOf(it.signature) + it.alternatives }

        for (signature in signatures) {
            val type = signature.owner.substringAfterLast('.').substringAfterLast('$')
            assertTrue("No feature names $type", type in sources)
            signature.member?.let { member ->
                assertTrue("No feature looks up ${signature.describe()}", "\"${member.name}\"" in sources)
            }
        }
    }

    @Test
    fun `every feature on the compatibility page has a contract`() {
        for (feature in CompatibilityFeature.entries) {
            assertTrue("$feature has no contract", LauncherContracts.all.any { feature in it.features })
        }
    }
}
