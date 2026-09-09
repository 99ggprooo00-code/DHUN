package dev.dhun.ui.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunSpacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-policy tests for the large-screen shell (Phase 13 tablet row, landed
 * under Phase 14). These pin the *decisions* the shell renders — where the
 * breakpoint is, how the panes split, what Back does, which nav item is
 * selected — so the tablet path has a regression gate that does not need an
 * emulator. Rendering on real hardware remains a separate, human gate.
 */
class DhunShellLayoutTest {

    // ---------------------------------------------------------------- layout

    @Test
    fun theTwoPaneSplitArrivesOnTheSameBreakpointAsTheNavigationRail() {
        // The rail token is the single source of truth: if someone moves the
        // rail breakpoint, two-pane must move with it and this test holds.
        val rail = DhunSpacing.navigationRailBreakpoint
        assertEquals(DhunShellLayout.SinglePane, DhunShellLayout.of(rail - 1.dp))
        assertEquals(DhunShellLayout.SinglePane, DhunShellLayout.of(0.dp))
        assertEquals(DhunShellLayout.TwoPane, DhunShellLayout.of(rail))
        assertEquals(DhunShellLayout.TwoPane, DhunShellLayout.of(rail + 200.dp))
    }

    @Test
    fun onlyTwoPaneTreatsADetailRouteAsAPane() {
        assertTrue(DhunShellLayout.TwoPane.showsDetailPane)
        assertFalse(DhunShellLayout.SinglePane.showsDetailPane)
        // A collapsed / unmeasured window must never pick the wider layout.
        listOf(-1.dp, 0.dp, Float.NaN.dp, Float.POSITIVE_INFINITY.dp).forEach { width ->
            assertEquals(DhunShellLayout.SinglePane, DhunShellLayout.of(width), "width $width")
        }
    }

    @Test
    fun policyAndEnumAgreeOnTheLayout() {
        listOf(320.dp, 839.5.dp, 840.dp, 1920.dp).forEach { width ->
            assertEquals(
                DhunShellLayout.of(width),
                DhunShellPolicy.layoutAt(width),
                "policy diverged from the enum for width $width",
            )
        }
    }

    // ------------------------------------------------------------------ panes

    /**
     * The measurements the shell really passes: a window of [shell] dp, of
     * which the Scaffold handed back [content] dp (status bar / window insets
     * take the rest). The rail is inside [content], which is the whole point of
     * the split taking the *remainder*.
     */
    private fun split(shell: Dp, content: Dp) = DhunShellPolicy.panes(shell, content)

    @Test
    fun singlePaneHasNothingToSplit() {
        assertNull(split(400.dp, 380.dp))
        assertNull(split(DhunSpacing.navigationRailBreakpoint - 0.5.dp, 700.dp))
    }

    @Test
    fun theRailGetsItsWidthOutOfTheSplit() {
        // The reservation is a real token, not a number copied into the policy:
        // 80dp is the app's dock measure, which is also the M3 rail width.
        assertEquals(DhunSpacing.bottomNavHeight, DhunShellPolicy.railAllowance(hasRail = true))
        assertEquals(0.dp, DhunShellPolicy.railAllowance(hasRail = false))
        val rail = DhunShellPolicy.railAllowance(hasRail = true)
        // A content area no wider than the rail is not a two-pane at all.
        assertNull(split(900.dp, rail))
        assertNull(split(900.dp, rail - 40.dp))
    }

    @Test
    fun masterGrowsProportionallyThenStopsAtTheCeiling() {
        // Content is already rail-reduced here (the shell measures it that way),
        // so `available` is what the split divides — asserted in the policy's own
        // Dp terms, never as a hand-computed magic number.
        val content = 840.dp
        val available = content - DhunShellPolicy.railAllowance(hasRail = true)
        assertEquals(
            available * DhunShellPolicy.MASTER_PANE_FRACTION,
            split(840.dp, content)?.masterWidth,
        )
        // A wide desktop window: everything past the ceiling goes to the detail.
        assertEquals(DhunSpacing.playerContentMaxWidth, split(1900.dp, 1900.dp)?.masterWidth)
        assertEquals(
            DhunSpacing.playerContentMaxWidth,
            split(2600.dp, 2600.dp)?.masterWidth,
        )
    }

    @Test
    fun twoPaneAppearsExactlyWhereTheRailDoes() {
        // Regression this whole file exists for: the layout is decided on the
        // shell width but the *space* is the content area, and content at the
        // 840dp breakpoint is only ~760dp. A split that required the breakpoint
        // of the remainder would show a rail with no detail pane on tablets.
        val breakpoint = DhunSpacing.navigationRailBreakpoint
        val atBreakpoint = requireNotNull(split(breakpoint, breakpoint - 10.dp)) {
            "no detail pane at the rail breakpoint — the two branches drifted apart"
        }
        assertTrue(
            atBreakpoint.masterWidth < breakpoint - DhunShellPolicy.railAllowance(hasRail = true),
            "master must leave room for a real detail column",
        )
        // And no rail at all → the split gets the whole content area.
        assertEquals(
            DhunShellPolicy.panes(breakpoint, breakpoint, hasRail = false)?.masterWidth,
            (breakpoint * DhunShellPolicy.MASTER_PANE_FRACTION),
        )
    }

    @Test
    fun theDetailPaneNeverCollapsesIntoASliver() {
        // The real promise, asserted as an invariant over the whole large-screen
        // range rather than one hand-computed split: whatever the master takes,
        // detail keeps at least its floor.
        (840..2400 step 20).forEach { shell ->
            listOf(shell - 24, shell, shell - 60).forEach { content ->
                val panes = requireNotNull(split(shell.dp, content.dp)) { "no split at ${shell}dp" }
                val available = content.dp - DhunShellPolicy.railAllowance(hasRail = true)
                assertTrue(
                    available - panes.masterWidth >= DhunShellPolicy.detailPaneMinWidth,
                    "detail starved at ${shell}dp (content ${content}dp): master " +
                        "${panes.masterWidth}, floor ${DhunShellPolicy.detailPaneMinWidth}",
                )
            }
        }
        // Recorded honestly rather than faked into a test: with the current tokens
        // (0.4 fraction, 720dp ceiling) the proportion *always* leaves detail 60%
        // of the room, so the floor is a guard that never binds — it exists so a
        // future fraction/ceiling change cannot silently ship a sliver detail pane,
        // not as a branch reachable today. Asserting a number from it would have
        // been the kind of test that tests nothing while looking like one that does.
        val atBreakpoint = requireNotNull(split(840.dp, 840.dp))
        assertTrue(
            atBreakpoint.masterWidth < DhunShellPolicy.masterPaneMaxWidth,
            "the master should still be proportion-bound at the breakpoint",
        )
    }

    @Test
    fun panesStayMonotonicAsTheWindowWidens() {
        // A dragged desktop window must never shrink the master as it grows —
        // the classic weight-based two-pane flicker.
        val masters = (840..2400 step 40).map { width ->
            requireNotNull(split(width.dp, width.dp - 20.dp)).masterWidth
        }
        masters.forEachIndexed { index, master ->
            if (index > 0) {
                assertTrue(
                    master >= masters[index - 1],
                    "master shrank at index $index: ${masters[index - 1]} -> $master",
                )
            }
        }
    }

    @Test
    fun degenerateWidthsRefuseToProduceAnImpossibleLayout() {
        listOf(-1.dp, 0.dp, Float.NaN.dp).forEach { bad ->
            assertNull(split(1200.dp, bad), "content width $bad")
        }
        assertNull(split(Float.POSITIVE_INFINITY.dp, Float.POSITIVE_INFINITY.dp))
    }

    // ------------------------------------------------------------------- back

    @Test
    fun backCollapsesThePlayerBeforePoppingAnyDetailPage() {
        val withBoth = DhunShellPolicy.backAction(DhunShellLayout.SinglePane, playerExpanded = true, detailDepth = 2)
        assertEquals(ShellBackAction.CollapsePlayer, withBoth.action)
        assertFalse(withBoth.detailPaneOpen)

        val detailOnly = DhunShellPolicy.backAction(DhunShellLayout.SinglePane, playerExpanded = false, detailDepth = 2)
        assertEquals(ShellBackAction.PopDetail, detailOnly.action)

        val nothingOpen = DhunShellPolicy.backAction(DhunShellLayout.SinglePane, playerExpanded = false, detailDepth = 0)
        assertEquals(ShellBackAction.PlatformDefault, nothingOpen.action)
    }

    @Test
    fun twoPaneBackReportsThatTheDetailPaneIsStillOpen() {
        val collapsingPlayer = DhunShellPolicy.backAction(DhunShellLayout.TwoPane, playerExpanded = true, detailDepth = 1)
        assertEquals(ShellBackAction.CollapsePlayer, collapsingPlayer.action)
        // Back on a tablet collapses the sheet and keeps the page underneath.
        assertTrue(collapsingPlayer.detailPaneOpen)

        val popping = DhunShellPolicy.backAction(DhunShellLayout.TwoPane, playerExpanded = false, detailDepth = 3)
        assertEquals(ShellBackAction.PopDetail, popping.action)
        assertTrue(popping.detailPaneOpen)

        val empty = DhunShellPolicy.backAction(DhunShellLayout.TwoPane, playerExpanded = false, detailDepth = 0)
        assertEquals(ShellBackAction.PlatformDefault, empty.action)
        assertFalse(empty.detailPaneOpen)
    }

    @Test
    fun aNegativeStackDepthIsTreatedAsEmpty() {
        val back = DhunShellPolicy.backAction(DhunShellLayout.TwoPane, playerExpanded = false, detailDepth = -7)
        assertEquals(ShellBackAction.PlatformDefault, back.action)
        assertFalse(back.detailPaneOpen)
    }

    // ------------------------------------------------------------ tab selected

    @Test
    fun aPushedRouteClearsSelectionOnlyWhenItCoversTheTab() {
        val artist = DetailRoute.ArtistPage("UCx")
        // Single pane: the route covers the tab content → no tab highlighted.
        assertFalse(
            DhunShellPolicy.isTabSelected(
                DhunShellLayout.SinglePane,
                selectedTab = AppTab.HOME,
                tab = AppTab.HOME,
                detailDepth = 1,
            ),
        )
        // Two pane: HOME is still on screen beside the detail → stays highlighted.
        assertTrue(
            DhunShellPolicy.isTabSelected(
                DhunShellLayout.TwoPane,
                selectedTab = AppTab.HOME,
                tab = AppTab.HOME,
                detailDepth = 1,
            ),
        )
        assertTrue(
            DhunShellPolicy.isTabSelected(
                DhunShellLayout.TwoPane,
                selectedTab = AppTab.HOME,
                tab = AppTab.SEARCH,
                detailDepth = 1,
            ).not(),
        )
        // Sanity: a real route type is what we push, not a placeholder.
        assertEquals("UCx", artist.id)
    }
}
