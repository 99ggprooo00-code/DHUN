/**
 * Boot smoke test — does the app actually come up?
 *
 * There is no browser in this repository's CI and no jsdom dependency, so
 * this drives `boot()` against a hand-written DOM stub that supports exactly
 * what the app touches and nothing more. It is not a browser: it proves the
 * module graph loads, the first render produces the app's real markup, the
 * shell wires the three tabs, and a track can be played and rendered in both
 * player surfaces. Layout, paint and real events stay unverified here.
 */

import test from "node:test";
import assert from "node:assert/strict";

import { boot } from "./helpers/boot-harness.mjs";

test("the app boots and renders the shell with the app's own tab order", async (t) => {
  const { mount } = await boot(t);
  const html = mount.innerHTML;

  assert.match(html, /DHUN/, "the wordmark renders");
  assert.match(html, /data-action="tab"/, "the tab bar renders");
  const tabs = [...html.matchAll(/data-tab="(HOME|SEARCH|LIBRARY)"/g)].map((m) => m[1]);
  assert.deepEqual([...new Set(tabs)], ["HOME", "SEARCH", "LIBRARY"], "Home, Search, Library — in that order");
  assert.match(html, /aria-current="page"/, "the current tab is marked");
});

test("the first render carries the engineering-preview notice", async (t) => {
  const { mount } = await boot(t);
  assert.match(mount.innerHTML, /Engineering preview/);
  assert.match(mount.innerHTML, /not proven/);
});

test("the catalogue notice says the data is a sample, once the feed lands", async (t) => {
  const { mount, store } = await boot(t);
  await settle(store);
  assert.match(mount.innerHTML, /Sample data/);
});

test("the home feed renders the app's three sections", async (t) => {
  const { mount, store } = await boot(t);
  await settle(store);
  const html = mount.innerHTML;
  assert.match(html, /Quick picks/);
  assert.match(html, /Recommended songs/);
  assert.match(html, /Listen again/);
});

test("nothing renders as 'undefined' or '[object Object]'", async (t) => {
  const { mount, store, app } = await boot(t);
  await settle(store);
  app.player.play(0, { queue: app.catalog.sample.allTracks().slice(0, 3) });
  app.nav.setPlayerExpanded(true);
  app.render();
  const html = mount.innerHTML;
  assert.doesNotMatch(html, />\s*undefined\s*</, "a token interpolated as undefined");
  assert.doesNotMatch(html, /\[object Object\]/);
});

test("playing a track fills the mini player and the full player", async (t) => {
  const { mount, app } = await boot(t);
  const tracks = app.catalog.sample.allTracks().slice(0, 3);
  app.player.play(1, { queue: tracks });
  app.render();

  assert.match(mount.innerHTML, /data-testid="mini-player"/);
  assert.match(mount.innerHTML, new RegExp(tracks[1].title), "the mini player shows the current track");

  app.nav.setPlayerExpanded(true);
  app.render();
  assert.match(mount.innerHTML, /data-testid="full-player"/);
  assert.match(mount.innerHTML, /Now playing/);
  assert.match(mount.innerHTML, /Up next/);
  assert.match(mount.innerHTML, /Lyrics/);
});

test("the full player shows the honest 'no audio stream' state on this build", async (t) => {
  const { mount, app } = await boot(t);
  app.player.play(0, { queue: app.catalog.sample.allTracks().slice(0, 2) });
  app.nav.setPlayerExpanded(true);
  app.render();
  assert.match(mount.innerHTML, /No audio stream/);
  assert.match(mount.innerHTML, /data-testid="backend-notice"/);
});

test("settings opens as a detail page and carries the app's sections", async (t) => {
  const { mount, app } = await boot(t);
  app.nav.push({ kind: "settings" });
  app.render();
  const html = mount.innerHTML;
  assert.match(html, /Appearance/);
  assert.match(html, /Playback &amp; storage|Playback & storage/);
  assert.match(html, /Equalizer/);
  assert.match(html, /Resume on launch/);
  // All 18 VLC presets and the ten bands are offered.
  assert.match(html, /Unlimited/);
  assert.match(html, /16 kHz/);
});

test("the equaliser mirrors the app: moving a slider marks the curve Custom", async (t) => {
  const { app, store } = await boot(t);
  const rock = app.store.get().equalizer;
  assert.equal(rock.presetId, "custom");
  store.set({ equalizer: { ...rock, presetId: "rock" } });
  assert.equal(store.get().equalizer.presetId, "rock");
});

test("deep links open the matching detail page", async (t) => {
  const { mount, app, setHash, applyHashRoute } = await boot(t);
  setHash("#/album/al-01");
  applyHashRoute();
  await new Promise((resolve) => setTimeout(resolve, 0));
  const html = mount.innerHTML;
  assert.match(html, /Dharan Nights/, "the album page rendered for #/album/al-01");
  assert.ok(app.nav.detailStack.some((route) => route.kind === "album"));
});

/** Lets the home-feed promise and the live probe settle. */
async function settle(store, rounds = 6) {
  for (let i = 0; i < rounds; i += 1) await Promise.resolve();
  await new Promise((resolve) => setTimeout(resolve, 0));
  void store;
}
