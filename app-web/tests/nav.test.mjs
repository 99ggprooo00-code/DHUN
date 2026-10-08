import test from "node:test";
import assert from "node:assert/strict";

import {
  AppTab,
  USER_TABS,
  ROOT_TAB,
  MAX_TAB_HISTORY,
  RAIL_BREAKPOINT_PX,
  ShellBackAction,
  createNavState,
  layoutOf,
  backAction,
} from "../src/js/nav.js";

test("the user tabs are Home, Search, Library — in the app's order", () => {
  assert.deepEqual(USER_TABS, [AppTab.HOME, AppTab.SEARCH, AppTab.LIBRARY]);
  assert.equal(ROOT_TAB, AppTab.HOME);
  assert.equal(MAX_TAB_HISTORY, 8);
});

test("switching tab records where BACK should return to", () => {
  const nav = createNavState();
  nav.selectTab(AppTab.SEARCH);
  nav.selectTab(AppTab.LIBRARY);
  assert.deepEqual(nav.tabHistory, [AppTab.HOME, AppTab.SEARCH]);
  assert.equal(nav.selectedTab, AppTab.LIBRARY);
  nav.popTab();
  assert.equal(nav.selectedTab, AppTab.SEARCH);
  nav.popTab();
  assert.equal(nav.selectedTab, AppTab.HOME);
  assert.equal(nav.hasTabHistory, false);
});

test("the tab history is bounded — a long session cannot grow it forever", () => {
  const nav = createNavState();
  for (let i = 0; i < 30; i += 1) {
    nav.selectTab(i % 2 === 0 ? AppTab.SEARCH : AppTab.LIBRARY);
  }
  assert.equal(nav.tabHistory.length, MAX_TAB_HISTORY);
});

test("re-tapping the selected tab pops one detail page", () => {
  const nav = createNavState();
  nav.push({ kind: "album", id: "al-01" });
  assert.equal(nav.detailStack.length, 1);
  nav.selectTab(AppTab.HOME);
  assert.equal(nav.detailStack.length, 0, "re-tap closes one page and stays on the tab");
  assert.equal(nav.selectedTab, AppTab.HOME);
});

test("Settings is a singleton on the detail stack", () => {
  const nav = createNavState();
  nav.push({ kind: "settings" });
  nav.push({ kind: "settings" });
  assert.equal(nav.detailStack.length, 1);
  nav.push({ kind: "album", id: "al-01" });
  nav.push({ kind: "settings" });
  assert.deepEqual(
    nav.detailStack.map((route) => route.kind),
    ["album", "settings"],
  );
});

test("closeTop closes exactly one layer: player, then page", () => {
  const nav = createNavState();
  nav.setPlayerExpanded(true);
  nav.push({ kind: "artist", id: "ar-01" });
  assert.equal(nav.hasOverlay, true);
  assert.equal(nav.closeTop(), true);
  assert.equal(nav.playerExpanded, false);
  assert.equal(nav.detailStack.length, 1);
  assert.equal(nav.closeTop(), true);
  assert.equal(nav.detailStack.length, 0);
  assert.equal(nav.closeTop(), false);
});

test("the rail and the two-pane split arrive at the same breakpoint", () => {
  assert.equal(layoutOf(RAIL_BREAKPOINT_PX - 1), "SinglePane");
  assert.equal(layoutOf(RAIL_BREAKPOINT_PX), "TwoPane");
  // Unmeasured or collapsed windows fall back to the conservative layout.
  assert.equal(layoutOf(0), "SinglePane");
  assert.equal(layoutOf(Number.NaN), "SinglePane");
  assert.equal(layoutOf(-100), "SinglePane");
});

test("BACK unwinds player → page → tab → platform, in that order", () => {
  const base = { playerExpanded: false, detailDepth: 0, tab: AppTab.HOME, hasTabHistory: false };
  assert.equal(backAction({ ...base, playerExpanded: true, detailDepth: 2 }), ShellBackAction.CollapsePlayer);
  assert.equal(backAction({ ...base, detailDepth: 1 }), ShellBackAction.PopDetail);
  assert.equal(
    backAction({ ...base, tab: AppTab.SEARCH, hasTabHistory: true }),
    ShellBackAction.ReturnToPreviousTab,
  );
  assert.equal(backAction({ ...base, tab: AppTab.SEARCH, hasTabHistory: false }), ShellBackAction.PlatformDefault);
  assert.equal(backAction({ ...base }), ShellBackAction.PlatformDefault);
  // A negative depth cannot happen, but defensive code must not invent a layer.
  assert.equal(backAction({ ...base, detailDepth: -3 }), ShellBackAction.PlatformDefault);
});

test("two-pane keeps the detail pane when the tab changes; single pane does not", () => {
  const nav = createNavState();
  nav.push({ kind: "album", id: "al-01" });
  nav.selectTab(AppTab.SEARCH, { keepDetailOnTabChange: true });
  assert.equal(nav.detailStack.length, 1, "the rail switches the master and keeps the detail pane");

  const single = createNavState();
  single.push({ kind: "album", id: "al-01" });
  single.selectTab(AppTab.SEARCH, { keepDetailOnTabChange: false });
  assert.equal(single.detailStack.length, 0);
});
