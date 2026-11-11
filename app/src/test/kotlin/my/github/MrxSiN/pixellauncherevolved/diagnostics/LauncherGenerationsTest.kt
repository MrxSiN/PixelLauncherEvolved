package my.github.MrxSiN.pixellauncherevolved.diagnostics

import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.APP_DRAWER_COLUMNS
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.DOCK
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.DOCK_ICONS
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.FULL_TABLET_LAYOUT
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.GRID
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.GRID_ICON_SIZE
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.GRID_SPACING
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.HOME_SEARCH_BAR
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.OVERVIEW_ACTIONS
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.OVERVIEW_ONLY
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.TASKBAR_ONLY
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.WIDGET_STACKS

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract list read against what four launchers hold, all of them
 * Pixel Launcher versionCode 907: the same version is not the same launcher.
 * Primary signatures are the latest launcher's (`CP4A`); every other build is
 * read through its alternatives.
 *
 * A fixture says which signatures a launcher has, as the analyzer would find
 * them. The Pixel 11 Pro fixture is GitHub issue 1's report and nothing more:
 * what it lists as missing is missing, and an earlier-generation alternative is
 * there only in the second fixture, which is AOSP `android-17.0.0_r1`'s source
 * shape, not anything read off a Pixel 11 Pro. Passing proves which features
 * the module would offer and gate on each; it proves nothing about how they
 * behave on that phone.
 */
class LauncherGenerationsTest {

    /** Pixel 8 Pro, Android 17 `CP4A.261105.040`, the latest: every primary signature resolves (308 of 308 on device). */
    private val pixel8 = Fixture { signature -> signature in primaries }

    /** Android 17 QPR1 `CP3A`: the primaries but the ones `CP4A` introduced, and `CP3A`'s own names. */
    private val cp3a = Fixture(::isCp3a)

    /** Pixel 11 Pro `CD1A.261005.003`, as issue 1 reports it: `CP3A`'s shape with the 27 failures missing, nothing else known. */
    private val pixel11Reported = Fixture { signature -> isCp3a(signature) && signature.describe() !in REPORTED_MISSING }

    /** The same, with the earlier generation's members AOSP `android-17.0.0_r1` declares in its source. */
    private val pixel11IfAospShaped = Fixture { signature ->
        (isCp3a(signature) && signature.describe() !in REPORTED_MISSING) || signature.describe() in AOSP_R1_EARLIER
    }

    @Test
    fun `the Pixel 8 Pro on the latest launcher offers every feature, on primary signatures alone`() {
        val report = pixel8.report()

        assertEquals(report.total, report.resolved)
        assertEquals(0, report.fallbacks)
        for (feature in CompatibilityFeature.entries) assertTrue("$feature", report.isCompatible(feature))
    }

    @Test
    fun `CP3A offers every feature, falling back only to its own names`() {
        val report = cp3a.report()

        assertEquals(report.total, report.resolved)
        for (feature in CompatibilityFeature.entries) assertTrue("$feature", report.isCompatible(feature))
        val fallbacks = report.results.filter { it.outcome == ContractOutcome.FALLBACK }
        assertTrue(fallbacks.isNotEmpty())
        assertTrue(fallbacks.all { it.alternative?.describe() in CP3A_EARLIER })
    }

    @Test
    fun `every failure issue 1 reports is a contract this list still checks`() {
        val described = LauncherContracts.all.flatMap { listOf(it.signature) + it.alternatives }.map { it.describe() }.toSet()
        for (missing in REPORTED_MISSING) assertTrue("No contract describes $missing", missing in described)
    }

    @Test
    fun `the Pixel 11 Pro as reported offers no feature that cannot install`() {
        val report = pixel11Reported.report()

        // The ten issue 1 lists, and the two it listed as compatible that then
        // failed to install: their missing members are now on their contracts.
        val gated = setOf(
            WIDGET_STACKS, HOME_SEARCH_BAR, DOCK, DOCK_ICONS, GRID, GRID_ICON_SIZE, GRID_SPACING,
            APP_DRAWER_COLUMNS, OVERVIEW_ACTIONS, TASKBAR_ONLY, OVERVIEW_ONLY, FULL_TABLET_LAYOUT,
        )
        assertEquals(gated, CompatibilityFeature.entries.filterNot(report::isCompatible).toSet())
        assertTrue(report.results.filter { it.outcome == ContractOutcome.FALLBACK }.all { it.alternative?.describe() in CP3A_EARLIER })
    }

    @Test
    fun `an AOSP-shaped Pixel 11 Pro falls back for the dock, and nothing without evidence`() {
        val report = pixel11IfAospShaped.report()
        val offered = CompatibilityFeature.entries.filter(report::isCompatible).toSet()

        for (feature in listOf(DOCK, DOCK_ICONS, APP_DRAWER_COLUMNS, OVERVIEW_ACTIONS, OVERVIEW_ONLY, TASKBAR_ONLY, GRID_SPACING)) {
            assertTrue("$feature", feature in offered)
        }
        // No earlier seam for these: the grid pick, the Compose popup, and the
        // search bar's identity (OseWidgetView is Google's, not in AOSP).
        for (feature in listOf(GRID, GRID_ICON_SIZE, FULL_TABLET_LAYOUT, WIDGET_STACKS, HOME_SEARCH_BAR)) {
            assertFalse("$feature", feature in offered)
        }
        assertTrue(
            report.results.filter { it.outcome == ContractOutcome.FALLBACK }
                .all { it.alternative?.describe().let { name -> name in AOSP_R1_EARLIER || name in CP3A_EARLIER } },
        )
    }

    @Test
    fun `a mixed launcher gates only what its own missing members stand under`() {
        // CP3A's builder and profile names, the earlier spec made by its constructor
        // without the dock's field on it, and no folder profile under either name.
        val mixed = Fixture { signature ->
            when (signature.describe()) {
                "DisplayOptionSpec.mapTypeIndex(boolean, boolean)",
                "DisplayOptionSpec.numShownHotseatIcons",
                "DisplayOptionSpec.numAllAppsColumns",
                "DeviceProfile.folderProfile",
                -> false
                "InvariantDeviceProfile\$DisplayOptionSpec.<init>(InvariantDeviceProfile, boolean, boolean)" -> true
                else -> signature in primaries
            }
        }.report()

        assertTrue(mixed.isCompatible(DOCK))
        assertFalse(mixed.isCompatible(DOCK_ICONS))
        assertFalse(mixed.isCompatible(APP_DRAWER_COLUMNS))
        // The folder profile is optional: its absence is shown and gates nothing.
        assertTrue(mixed.results.any { it.contract.signature.describe() == "DeviceProfile.folderProfile" && !it.isMet })
    }

    private val primaries = LauncherContracts.all.map { it.signature }.toSet()

    private fun isCp3a(signature: Signature): Boolean {
        val name = signature.describe()
        return (signature in primaries && name !in CP4A_ONLY) || name in CP3A_EARLIER
    }

    /** Reads the contract list the way [ContractAnalyzer] does: the primary, else the first alternative there. */
    private class Fixture(private val has: (Signature) -> Boolean) {
        fun report() = ContractReport(
            LauncherContracts.all.map { contract ->
                when {
                    has(contract.signature) -> ContractResult(contract, ContractOutcome.RESOLVED)
                    else -> contract.alternatives.firstOrNull(has)
                        ?.let { ContractResult(contract, ContractOutcome.FALLBACK, it) }
                        ?: ContractResult(contract, ContractOutcome.MISSING_MEMBER)
                }
            },
        )
    }

    private companion object {

        /** Primaries `CP4A.261105.040` introduced, read off its dex. */
        val CP4A_ONLY = setOf(
            "OseWidgetView",
            "LauncherAppWidgetHostView.activityContext",
            "DbEntry.resizeParams",
            "ResizeParams.minSpanX",
            "ResizeParams.minSpanY",
        )

        /** Their `CP3A` alternatives, verified on a Pixel 8 Pro on `CP3A.260905.009`. */
        val CP3A_EARLIER = setOf(
            "OseWidgetController.applyTo(LauncherAppWidgetHostView, boolean)",
            "LauncherAppWidgetHostView.mActivityContext",
            "ItemInfo.minSpanX",
            "ItemInfo.minSpanY",
        )

        /** Issue 1's failed contracts, as the report printed them (duplicates once). */
        val REPORTED_MISSING = setOf(
            "WidgetSizeHandler.updateSizeRanges",
            "ItemDeleteHandler.removeItem",
            "ItemDeleteHandler.completeDelete",
            "ItemDeleteHandler.activityContext",
            "PopupContainer.showComposePopup(int, List)",
            "StringContainer\$ResourceString.getText",
            "OseWidgetController.applyTo(LauncherAppWidgetHostView, boolean)",
            "PopupContainerWithArrow.showComposePopup(int, List)",
            "PopupItem.label",
            "PopupItem.category",
            "StringContainer\$ResourceString.resId",
            "StringContainer\$ResourceString.getText(Composer)",
            "DeviceProperties.isVerticalBarLayout()",
            "DeviceProfileBuilder.build()",
            "DisplayOptionSpec.mapTypeIndex(boolean, boolean)",
            "DisplayOptionSpec.numShownHotseatIcons",
            "DeviceProfile.workspaceProfile",
            "DeviceProfile.folderProfile",
            "DisplayOption.parseWeightedPredefinedDisplayOption",
            "GridOption.displayOptionSpec",
            "ResponsiveCellSpecsProvider\$Companion.create",
            "ResponsiveSpecsProvider\$Companion.create",
            "DisplayOptionSpec.numAllAppsColumns",
            "OverviewActionsView.updateForGroupedTask(boolean)",
            "DeviceProperties\$Factory.createDeviceProperties",
        )

        /** Earlier-generation alternatives `android-17.0.0_r1` declares (read from its source, Launcher3 at that tag). */
        val AOSP_R1_EARLIER = setOf(
            "DeviceProfile\$Builder.build()",
            "DeviceProfile.isVerticalBarLayout()",
            "InvariantDeviceProfile\$DisplayOptionSpec.<init>(InvariantDeviceProfile, boolean, boolean)",
            "InvariantDeviceProfile\$DisplayOptionSpec.numShownHotseatIcons",
            "InvariantDeviceProfile\$DisplayOptionSpec.numAllAppsColumns",
            "DeviceProfile.mDeviceProperties",
            "DeviceProfile.mHotseatProfile",
            "DeviceProfile.mWorkspaceProfile",
            "DeviceProfile.mFolderProfile",
            "OverviewActionsView.updateActionButtonsVisibility()",
            "ResponsiveCellSpecsProvider.create",
            "ResponsiveSpecsProvider.create",
            "TaskbarConfiguration.<init>(boolean)",
        )
    }
}
