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

import { parseRoute, formatRoute, sameRoute, DEFAULT_ROUTE, LIBRARY_TABS } from "../src/js/router.js";

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

test("sameRoute compares what the view renders, and nothing else", () => {
  const base = { ...DEFAULT_ROUTE };
  assert.ok(sameRoute(base, { ...base }));
  assert.ok(!sameRoute(base, { ...base, name: "search", query: "" }));
  assert.ok(!sameRoute(base, { ...base, player: "open" }));
  assert.ok(!sameRoute(base, { ...base, player: "open", playerTab: "lyrics" }));
});
