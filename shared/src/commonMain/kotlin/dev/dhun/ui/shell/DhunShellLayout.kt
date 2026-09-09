package dev.dhun.ui.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunSpacing

/**
 * How the shell divides the window: one content pane, or a master (the tab
 * list) beside a detail pane (the top of the [AppNavState.detailStack]).
 *
 * Deliberately **pure and stateless** — every rule the shell needs (layout at
 * a width, pane split, back disposition, tab selection) is a function of
 * inputs, so the large-screen behavior is pinned by JVM unit tests instead of
 * requiring an emulator. `DhunAppShell` renders exactly what these say.
 *
 * The decision layer is separated from the render layer because a two-pane
 * layout that cannot be tested is a layout that will silently regress: the
 * phone path and the tablet path used to share one `when (detailStack …)`
 * block, which is precisely why large screens never got a second pane.
 */
enum class DhunShellLayout {
    /** Handsets and narrow windows: the detail stack replaces the tab content. */
    SinglePane,

    /**
     * The rail breakpoint and wider (Phase 13 "tablet/large-screen layout"
     * acceptance row): master list + detail pane side by side, so browsing an
     * artist no longer hides Home/Search/Library behind it.
     */
    TwoPane,
    ;

    /** True when the detail route is a pane rather than a full-width overlay. */
    val showsDetailPane: Boolean get() = this == TwoPane

    companion object {
        /**
         * The one breakpoint, reusing [DhunSpacing.navigationRailBreakpoint]
         * instead of a second number that could drift away from the rail: the
         * rail and the two-pane split always arrive together, on a tablet or on
         * a dragged desktop window.
         *
         * Non-finite or non-positive widths (an unmeasured / collapsed window)
         * fall back to [SinglePane], the more conservative layout.
         */
        fun of(availableWidth: Dp): DhunShellLayout {
            val width = availableWidth.value
            return if (width.isFinite() && width > 0f &&
                availableWidth >= DhunSpacing.navigationRailBreakpoint
            ) {
                TwoPane
            } else {
                SinglePane
            }
        }
    }
}

/**
 * Pane widths for a large-screen shell. Only produced for
 * [DhunShellLayout.TwoPane] — [SinglePane] has nothing to split.
 *
 * [masterWidth] is fixed so the list keeps a reading width (its rows and
 * quick-picks were designed at roughly phone width); everything left over is
 * the detail pane, which is what actually gains from the extra space.
 */
data class ShellPanes(val masterWidth: Dp, val detailWeight: Float) {
    init {
        require(masterWidth > 0.dp) { "master pane must be positive, was $masterWidth" }
        require(detailWeight > 0f && detailWeight.isFinite()) {
            "detail pane weight must be a positive finite number, was $detailWeight"
        }
    }
}

/** Back-button outcomes for the shell. */
enum class ShellBackAction {
    /** Collapse [AppNavState.playerExpanded] first — FullPlayer is a sheet, not a page. */
    CollapsePlayer,

    /** Pop exactly one [DetailRoute]; on two-pane layouts the pane shows what is underneath. */
    PopDetail,

    /** Nothing the shell owns is open; the platform default runs (Android → moveTaskToBack). */
    PlatformDefault,
}

/** Result of [DhunShellPolicy.backAction] — an explicit action, never a nullable fall-through. */
data class ShellBack(val action: ShellBackAction, val detailPaneOpen: Boolean)

/**
 * The shell's layout rules. See [DhunShellLayout] for why they live apart from
 * the composable.
 */
object DhunShellPolicy {

    /**
     * Fraction of the content area (everything right of the rail) that the
     * master keeps, before the ceilings below apply. Detail — the album/artist
     * page — gets the larger share: it is the pane that grows a grid.
     */
    const val MASTER_PANE_FRACTION: Float = 0.4f

    /**
     * Ceiling for the master pane, taken from the design system's reading
     * measure ([DhunSpacing.playerContentMaxWidth]) rather than a fresh number,
     * so a raw dp literal stays out of the ui layer entirely and both surfaces
     * follow the same decision. Past this width every extra dp goes to detail:
     * a list of rows does not improve when it gets wider.
     */
    val masterPaneMaxWidth: Dp get() = DhunSpacing.playerContentMaxWidth

    /**
     * Narrowest detail pane worth keeping. Below it the "two-pane" would be a
     * sliver that clips album artwork and track metadata, so the master is
     * trimmed back by this much instead — the split never produces a column
     * narrower than one usable list row of artwork + title + actions.
     */
    val detailPaneMinWidth: Dp get() = DhunSpacing.artworkThumb + DhunSpacing.skeletonTextWidth + DhunSpacing.xxl

    /**
     * Width the rail occupies when it is shown instead of the bottom bar.
     * [DhunSpacing.bottomNavHeight] (80dp) is the app's own dock measure and the
     * M3 navigation-rail width — the token is shared because it is the same
     * 80dp strip rotated, and it comfortably covers the 64dp item height
     * [DhunSpacing.navigationBarContent] gives the bottom bar. The pane seam's
     * [DhunSpacing.border] comes off the same budget.
     */
    fun railAllowance(hasRail: Boolean): Dp =
        if (hasRail) maxOf(DhunSpacing.bottomNavHeight, DhunSpacing.divider) else 0.dp

    /** Layout the shell should render for a measured width. */
    fun layoutAt(availableWidth: Dp): DhunShellLayout = DhunShellLayout.of(availableWidth)

    /**
     * Pane split. The layout is decided on the shell's measured width
     * ([DhunShellLayout.of] — the rail breakpoint), but the *space* is the
     * area the Scaffold handed back after its insets ([contentWidth]), minus
     * what the rail and the separator will take ([hasRail]). Mixing those two
     * measurements up is the trap this function exists to avoid: content width
     * sits just under the breakpoint at the breakpoint itself, so a split that
     * reused it would silently refuse to appear on exactly the devices the row
     * is about.
     *
     * `master = min(ceiling, fraction · available)`, then trimmed so the detail
     * pane keeps at least [detailPaneMinWidth]; the detail column is flexible and
     * fills whatever is left. Degenerate widths (non-positive, non-finite) and
     * [DhunShellLayout.SinglePane] yield `null` rather than an impossible layout.
     */
    fun panes(shellWidth: Dp, contentWidth: Dp, hasRail: Boolean = true): ShellPanes? {
        if (layoutAt(shellWidth) != DhunShellLayout.TwoPane) return null
        if (!contentWidth.value.isFinite() || contentWidth <= 0.dp) return null
        val allowance = railAllowance(hasRail)
        if (!allowance.value.isFinite() || contentWidth <= allowance) return null
        val available = contentWidth - allowance
        if (!available.value.isFinite() || available <= 0.dp) return null
        val master = minOf(
            masterPaneMaxWidth,
            available * MASTER_PANE_FRACTION,
            available - detailPaneMinWidth,
        ).coerceAtLeast(0.dp)
        if (master <= 0.dp) return null
        return ShellPanes(masterWidth = master, detailWeight = 1f)
    }

    /**
     * What the platform BackHandler should do. Player first, then exactly one
     * detail route: this ordering is the program-level contract stated in
     * [AppNavState] (BACK never exits the app while an overlay is open). On a
     * two-pane collapse it also reports that the detail pane is still showing a
     * page, which is what keeps Back from also switching the master away.
     *
     * [detailDepth] below zero is treated as zero, so a buggy caller cannot
     * invent routes that do not exist.
     */
    fun backAction(
        layout: DhunShellLayout,
        playerExpanded: Boolean,
        detailDepth: Int,
    ): ShellBack {
        val depth = if (detailDepth < 0) 0 else detailDepth
        val action = when {
            playerExpanded -> ShellBackAction.CollapsePlayer
            depth > 0 -> ShellBackAction.PopDetail
            else -> ShellBackAction.PlatformDefault
        }
        return ShellBack(action, layout.showsDetailPane && depth > 0)
    }

    /**
     * Whether a nav item reads as selected.
     *
     * Single-pane: a pushed detail route covers the tab, so no tab is
     * highlighted — unchanged from what shipped to devices.
     * Two-pane: the tab is still visible beside the detail pane, so it stays
     * highlighted; clearing it would leave the rail with no indicator at all.
     */
    fun isTabSelected(
        layout: DhunShellLayout,
        selectedTab: AppTab,
        tab: AppTab,
        detailDepth: Int,
    ): Boolean = when (layout) {
        DhunShellLayout.SinglePane -> selectedTab == tab && detailDepth <= 0
        DhunShellLayout.TwoPane -> selectedTab == tab
    }
}
