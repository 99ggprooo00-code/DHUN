/**
 * Routes are data, and every route survives a round trip.
 *
 * A hash link that formats differently from how it parses is a link that
 * quietly goes somewhere else, so the round trip is asserted for every screen
 * rather than for one example.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { DETAIL_ROUTES, LIBRARY_TABS, NAV_TABS, PLAYER_TABS, formatRoute, parseRoute, tabFor, titleFor } from "../src/js/router.js";

test("an empty hash is home, not an error", () => {
  assert.equal(parseRoute("").name, "home");
  assert.equal(parseRoute("#/").name, "home");
  assert.equal(parseRoute("#/nonsense").name, "home", "an unknown route falls back to the app's front door");
});

test("a library tab is one of the three that exist; anything else is Playlists", () => {
  assert.deepEqual([...LIBRARY_TABS], ["playlists", "likes", "history"]);
  assert.equal(parseRoute("#/library/history").libTab, "history");
  assert.equal(parseRoute("#/library/downloads").libTab, "playlists", "the old Downloads bookmark lands on a real screen");
});

test("the player's open state and panel are query parameters, not routes", () => {
  const route = parseRoute("#/album/abc?player=open&ptab=lyrics");
  assert.equal(route.name, "album");
  assert.equal(route.id, "abc");
  assert.equal(route.player, "open");
  assert.equal(route.playerTab, "lyrics");
  assert.equal(parseRoute("#/album/abc?ptab=nope").playerTab, "queue");
  assert.deepEqual([...PLAYER_TABS], ["queue", "lyrics"]);
});

test("ids are decoded once and encoded once", () => {
  const route = parseRoute("#/playlist/a%20b");
  assert.equal(route.id, "a b");
  assert.equal(formatRoute(route), "#/playlist/a%20b");
});

test("every route formats back to itself", () => {
  const routes = [
    { name: "home" },
    { name: "search", query: "arijit singh" },
    { name: "library", libTab: "likes" },
    { name: "album", id: "al-1" },
    { name: "artist", id: "ar-1" },
    { name: "playlist", id: "pl-1" },
    { name: "collection", id: "col-1" },
    { name: "settings" },
    { name: "about", doc: "privacy" },
    { name: "album", id: "al-1", player: "open", playerTab: "queue" },
  ];
  for (const route of routes) {
    const formatted = formatRoute(route);
    const parsed = parseRoute(formatted);
    assert.equal(parsed.name, route.name, `${formatted} lost its screen`);
    if (route.id) assert.equal(parsed.id, route.id);
    if (route.query) assert.equal(parsed.query, route.query);
    if (route.libTab) assert.equal(parsed.libTab, route.libTab);
    if (route.doc) assert.equal(parsed.doc, route.doc);
    if (route.player) assert.equal(parsed.player, "open");
    assert.equal(formatRoute(parsed), formatted, `${formatted} did not survive a second round trip`);
  }
});

test("the navigation is exactly three destinations", () => {
  assert.equal(NAV_TABS.length, 3);
  assert.deepEqual(NAV_TABS.map((tab) => tab.route), ["home", "search", "library"]);
  for (const tab of NAV_TABS) assert.ok(tab.icon, `${tab.id} has no icon`);
});

test("a detail screen keeps the tab it was opened from", () => {
  assert.equal(tabFor({ name: "home" }), "HOME");
  assert.equal(tabFor({ name: "album", id: "x" }, "SEARCH"), "SEARCH");
  assert.equal(tabFor({ name: "library" }), "LIBRARY");
  assert.ok(DETAIL_ROUTES.includes("settings"));
});

test("screen titles come from one place", () => {
  assert.equal(titleFor({ name: "settings" }), "Settings");
  assert.equal(titleFor({ name: "search", query: "lofi" }), "Search: lofi");
  assert.equal(titleFor({ name: "album", id: "x" }, "Monsoon"), "Monsoon");
  assert.equal(titleFor({ name: "album", id: "x" }), "Album", "a collection without a name still has a title");
});
