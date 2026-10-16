/**
 * The URL surface, pinned.
 *
 * Every screen in a web app is a link, so a router bug is a screen that cannot
 * be reloaded, shared or reached with the Back button. These run in Node with no
 * window: `parseRoute` and `formatRoute` are pure functions, which is the whole
 * reason they live in their own module.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { parseRoute, formatRoute, sameRoute, DEFAULT_ROUTE, LIBRARY_TABS, needsData, createRouter } from "../src/js/router.js";

test("the empty hash is the home route, not an error", () => {
  assert.equal(parseRoute("").name, "home");
  assert.equal(parseRoute("#").name, "home");
  assert.equal(parseRoute("#/").name, "home");
  assert.equal(parseRoute("#/home").name, "home");
  assert.equal(parseRoute("#/nonsense/deep/path").name, "home");
});

test("every route survives a round trip through the URL", () => {
  const routes = [
    { name: "home", player: null, playerTab: "queue" },
    { name: "search", query: "koshi sessions", player: null, playerTab: "queue" },
    { name: "library", libTab: "liked", player: null, playerTab: "queue" },
    { name: "library", libTab: "history", player: null, playerTab: "queue" },
    { name: "playlist", id: "pl-42", player: null, playerTab: "queue" },
    { name: "album", id: "MPREb_abc", player: "open", playerTab: "lyrics" },
    { name: "artist", id: "UCabc", player: "open", playerTab: "queue" },
    { name: "settings", player: null, playerTab: "queue" },
    { name: "about-legal", doc: "privacy", player: null, playerTab: "queue" },
  ];
  for (const route of routes) {
    const parsed = parseRoute(formatRoute(route));
    assert.deepEqual(parsed, route, `${formatRoute(route)} did not round trip`);
    assert.ok(sameRoute(parsed, route));
  }
});

test("ids with characters a URL cares about survive the round trip", () => {
  const route = { name: "playlist", id: "VL/weird id+&?", player: null, playerTab: "queue" };
  assert.equal(parseRoute(formatRoute(route)).id, route.id);
});

test("the previous build's links still land somewhere real", () => {
  // These are the URLs the mirror build published. A returning visitor's
  // bookmark must not 404, and the one screen that no longer exists says so.
  assert.deepEqual(parseRoute("#/player"), { name: "home", player: "open", playerTab: "queue" });
  assert.equal(parseRoute("#/player/lyrics").playerTab, "lyrics");
  assert.equal(parseRoute("#/player/queue").playerTab, "queue");
  assert.equal(parseRoute("#/settings").name, "settings");
  assert.equal(parseRoute("#/about-legal").name, "about-legal");
  assert.equal(parseRoute("#/about").name, "about-legal");
});

test("a link to the removed Downloads screen lands on Playlists and says why", () => {
  const route = parseRoute("#/library/downloads");
  assert.equal(route.name, "library");
  assert.equal(route.libTab, "playlists");
  assert.equal(route.redirectedFrom, "downloads");
  // And it is a first-class library tab, never a tab the app renders.
  assert.deepEqual([...LIBRARY_TABS], ["playlists", "liked", "history"]);
});

test("the player's open state and tab ride along with any route", () => {
  const route = parseRoute("#/album/MPREb_x?player=open&ptab=lyrics");
  assert.equal(route.name, "album");
  assert.equal(route.id, "MPREb_x");
  assert.equal(route.player, "open");
  assert.equal(route.playerTab, "lyrics");
});

test("a hostile or truncated hash cannot throw", () => {
  for (const hash of ["#/album/%", "#/search?q=%E0%A4", "#/%ZZ", "#//", "#/library/"]) {
    assert.doesNotThrow(() => parseRoute(hash));
    assert.ok(parseRoute(hash).name);
  }
});

/*
 * The bug these pin: `/app/` opened on an idle Home and never fetched anything.
 *
 * `main.js` seeds its state with `parseRoute(location.hash)`, so by the time the
 * router's first `onRoute` fired, the "previous" route was already the current
 * one — `sameRoute` returned true, and every load was skipped. The screenshot
 * was "Nothing to show yet" with no error, because nothing had been tried.
 */
test("needsData fetches on the first route, whatever the URL is", () => {
  for (const hash of ["", "#/home", "#/search?q=koshi", "#/album/MPREb_1", "#/library/history", "#/settings"]) {
    const route = parseRoute(hash);
    // What main.js does: seed the state from the URL before the router starts.
    const seeded = parseRoute(hash);
    assert.equal(
      needsData(seeded, route, { initial: true }),
      true,
      `${hash || "(no hash)"} must fetch on the initial route`,
    );
  }
});

test("needsData re-fetches when the route actually changes", () => {
  const home = parseRoute("#/home");
  assert.equal(needsData(home, parseRoute("#/search?q=a"), { initial: false }), true);
  assert.equal(needsData(home, parseRoute("#/album/x"), { initial: false }), true);
  assert.equal(needsData(null, home, { initial: false }), true, "a router with no previous route still fetches");
});

test("needsData does not re-fetch the route already on screen", () => {
  // `router.navigate()` to the current hash re-runs the handler so the view is
  // correct (the layer stack is UI state, not URL state). That must not become a
  // second network request for the same screen.
  const home = parseRoute("#/home");
  assert.equal(needsData(home, parseRoute("#/home"), { initial: false }), false);
  const search = parseRoute("#/search?q=koshi");
  assert.equal(needsData(search, parseRoute("#/search?q=koshi"), { initial: false }), false);
  // Opening the player is a different URL, and it is a *presentation* change:
  // it re-renders, and it must not re-query. (This failed when `needsData` used
  // `sameRoute`, which compares the player's open state.)
  assert.equal(needsData(search, parseRoute("#/search?q=koshi&player=open"), { initial: false }), false,
    "opening the player is not a reason to re-query the source");
  assert.equal(needsData(parseRoute("#/home?player=open"), parseRoute("#/home?ptab=lyrics"), { initial: false }), false);
});

test("the router tells the app which call is the first one", () => {
  // The signal `needsData` depends on. A router that stopped passing it would
  // silently reintroduce the idle-first-render bug.
  const seen = [];
  const fakeWin = {
    location: { hash: "" },
    history: { replaceState: () => {} },
    addEventListener: () => {},
    removeEventListener: () => {},
  };
  const router = createRouter({ win: fakeWin, onRoute: (route, meta) => seen.push(meta) });
  router.start();
  // `replace` so the handler runs without a real hashchange event: the point
  // here is the flag, not the event plumbing.
  router.navigate({ ...parseRoute("#/search"), query: "koshi" }, { replace: true });
  assert.equal(seen[0]?.initial, true, "the first call must be marked initial");
  assert.equal(seen.at(-1)?.initial, false, "later calls must not be");
});

test("sameRoute compares what the view renders, and nothing else", () => {
  const base = { ...DEFAULT_ROUTE };
  assert.ok(sameRoute(base, { ...base }));
  assert.ok(!sameRoute(base, { ...base, name: "search", query: "" }));
  assert.ok(!sameRoute(base, { ...base, player: "open" }));
  assert.ok(!sameRoute(base, { ...base, player: "open", playerTab: "lyrics" }));
});
