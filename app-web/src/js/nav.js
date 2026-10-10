/**
 * Navigation state.
 *
 * Mirror of shared/src/commonMain/kotlin/dev/dhun/ui/shell/AppNavState.kt and
 * the pure decision layer in DhunShellLayout.kt (DhunShellPolicy). Same rules,
 * same order, same names — including the tab-history cap and the back stack
 * order, because BACK that behaves differently to the app is a different app.
 *
 * Why it is kept apart from the rendering: the app pins these rules with JVM
 * unit tests rather than emulators. `tests/nav.test.mjs` does the same here.
 */

/** DhunAppShell.AppTab — CATALOG exists in the enum but is deliberately not a
 *  user tab, so it is not in the web nav either. */
export const AppTab = Object.freeze({
  HOME: "HOME",
  SEARCH: "SEARCH",
  LIBRARY: "LIBRARY",
});

/** AppTab.userTabs, in the app's order. */
export const USER_TABS = [AppTab.HOME, AppTab.SEARCH, AppTab.LIBRARY];

/** AppTab.root — where BACK has run out of app to unwind. */
export const ROOT_TAB = AppTab.HOME;

/** AppNavState.MAX_TAB_HISTORY. */
export const MAX_TAB_HISTORY = 8;

/** DetailRoute kinds that are id-less singletons (AppNavState.push's rule). */
export const SINGLETON_KINDS = Object.freeze(["settings", "about-legal"]);

/** DhunShellLayout — the one breakpoint, DhunSpacing.navigationRailBreakpoint. */
export const RAIL_BREAKPOINT_PX = 840;

export function createNavState(initial = {}) {
  let tabHistory = [...(initial.tabHistory ?? [])];
  let currentTab = initial.tab ?? AppTab.HOME;
  let playerExpanded = initial.playerExpanded ?? false;
  let detailStack = [...(initial.detailStack ?? [])];

  return {
    get selectedTab() {
      return currentTab;
    },
    get playerExpanded() {
      return playerExpanded;
    },
    get detailStack() {
      return [...detailStack];
    },
    get tabHistory() {
      return [...tabHistory];
    },
    get hasOverlay() {
      return playerExpanded || detailStack.length > 0;
    },
    get hasDetail() {
      return detailStack.length > 0;
    },
    get hasTabHistory() {
      return currentTab !== ROOT_TAB;
    },
    get layout() {
      return layoutOf(typeof globalThis.innerWidth === "number" ? globalThis.innerWidth : 0);
    },

    /**
     * AppNavState.selectedTab's setter records the tab it left. Every path
     * that switches tabs (nav bar, rail, shortcuts, deep links) goes through
     * here so BACK always has somewhere to go.
     */
    selectTab(tab, { keepDetailOnTabChange = false } = {}) {
      if (!USER_TABS.includes(tab)) return false;
      if (tab === currentTab) {
        // Re-tap on the selected tab pops one detail page, as the app does.
        if (detailStack.length > 0) {
          detailStack = detailStack.slice(0, -1);
          return true;
        }
        return false;
      }
      tabHistory.push(currentTab);
      if (tabHistory.length > MAX_TAB_HISTORY) {
        tabHistory = tabHistory.slice(tabHistory.length - MAX_TAB_HISTORY);
      }
      currentTab = tab;
      if (!keepDetailOnTabChange) detailStack = [];
      return true;
    },

    /** DetailRoute — the four detail destinations, same shapes as the sealed
     *  interface in AppNavState.kt. */
    push(route) {
      if (!route || !route.kind) return false;
      // SettingsPage and AboutLegalPage are id-less singletons: at most one of
      // each lives on the stack, so re-tapping the entry replaces it instead of
      // stacking identical copies. Mirrors AppNavState.push.
      if (SINGLETON_KINDS.includes(route.kind)) {
        detailStack = detailStack.filter((r) => r.kind !== route.kind);
      }
      detailStack = [...detailStack, route];
      return true;
    },

    popDetail() {
      if (detailStack.length === 0) return false;
      detailStack = detailStack.slice(0, -1);
      return true;
    },

    setPlayerExpanded(expanded) {
      playerExpanded = Boolean(expanded);
    },

    /** PopTab — returns to the tab this one was reached from. */
    popTab() {
      if (tabHistory.length === 0) return false;
      const previous = tabHistory[tabHistory.length - 1];
      tabHistory = tabHistory.slice(0, -1);
      currentTab = previous;
      return true;
    },

    /** closeTop — one press closes exactly one layer. */
    closeTop() {
      if (playerExpanded) {
        playerExpanded = false;
        return true;
      }
      if (detailStack.length > 0) {
        detailStack = detailStack.slice(0, -1);
        return true;
      }
      return false;
    },
  };
}

/** DhunShellLayout.of — non-finite or non-positive widths fall back to
 *  SinglePane, the more conservative layout. */
export function layoutOf(availableWidth) {
  const finite = Number.isFinite(availableWidth) && availableWidth > 0;
  return finite && availableWidth >= RAIL_BREAKPOINT_PX ? "TwoPane" : "SinglePane";
}

/** ShellBackAction, in the same words as the Kotlin enum. */
export const ShellBackAction = Object.freeze({
  CollapsePlayer: "CollapsePlayer",
  PopDetail: "PopDetail",
  ReturnToPreviousTab: "ReturnToPreviousTab",
  PlatformDefault: "PlatformDefault",
});

/**
 * DhunShellPolicy.backAction — the order is the contract:
 * player sheet → detail page → the tab it came from → platform.
 */
export function backAction({ playerExpanded, detailDepth, tab, hasTabHistory }) {
  const depth = detailDepth < 0 ? 0 : detailDepth;
  if (playerExpanded) return ShellBackAction.CollapsePlayer;
  if (depth > 0) return ShellBackAction.PopDetail;
  if (tab !== ROOT_TAB && hasTabHistory) return ShellBackAction.ReturnToPreviousTab;
  return ShellBackAction.PlatformDefault;
}
