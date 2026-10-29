/** Actual boot/router/controller integration in Node; NOT browser or audio acceptance. */
import test from "node:test";
import assert from "node:assert/strict";
import { bootFixture, settle } from "./helpers/boot-fixture.mjs";
import { HOME_RAILS } from "../src/js/api.js";

test("Node boot fixture starts a blank Search without real provider or media I/O", async (t) => {
  const app = bootFixture(t);
  await settle();
  assert.equal(app.state.route.name, "search");
  assert.equal(app.state.search.status, "idle");
  assert.equal(app.calls.length, 0);
  assert.equal(app.audio.src, "");
});

test("hash-driven Search loads the new query instead of comparing after assignment", async (t) => {
  const app = bootFixture(t);
  app.navigate("#/search?q=rain");
  await settle();
  assert.equal(app.calls.length, 1);
  assert.equal(new URL(app.calls[0].url).searchParams.get("query"), "rain");
  app.calls[0].succeed(); await settle();
  assert.equal(app.state.search.status, "ready");
  assert.equal(app.state.search.results[0].id, "rain");
  assert.match(app.doc.title, /rain/);
});

test("a replacement search aborts the older request without starting fallback traffic", async (t) => {
  const app = bootFixture(t);
  app.submit("old"); await settle();
  const old = app.calls[0];
  app.submit("fresh"); await settle();
  assert.equal(old.signal.aborted, true);
  assert.equal(app.calls.length, 2);
  app.calls[1].succeed(); await settle();
  assert.equal(app.state.search.results[0].id, "fresh");
  assert.equal(app.state.source.mode, "live");
});

test("a late obsolete search failure cannot relabel newer results as samples", async (t) => {
  const app = bootFixture(t, { respectAbort: false, fail: (url) => url.includes("query=old") && !url.startsWith("https://saavn.dev/") });
  app.submit("old"); await settle(); const old = app.calls[0];
  app.submit("fresh"); await settle(); app.calls[1].succeed(); await settle();
  assert.equal(app.state.source.mode, "live");
  old.networkError(); await settle(); await settle();
  assert.equal(app.state.search.results[0].id, "fresh");
  assert.equal(app.state.source.mode, "live", "stale-error guards must cover global source state, not only result rows");
  assert.equal(app.state.source.error, null);
});

test("a late obsolete success cannot clear the current network-failure disclosure", async (t) => {
  const app = bootFixture(t, { respectAbort: false, fail: (url) => url.includes("query=fresh") });
  app.submit("old"); await settle(); const old = app.calls[0];
  app.submit("fresh"); await settle(); await settle();
  assert.equal(app.state.source.mode, "sample");
  const failure = app.state.source.error;
  old.succeed(); await settle();
  assert.equal(app.state.source.mode, "sample");
  assert.strictEqual(app.state.source.error, failure);
});

test("Clear Search invalidates pending results and keeps the cleared route empty", async (t) => {
  const app = bootFixture(t, { respectAbort: false });
  app.submit("old"); await settle(); const old = app.calls[0];
  app.click("clear-search");
  assert.equal(old.signal.aborted, true);
  old.succeed(); await settle();
  assert.equal(app.state.search.query, "");
  assert.equal(app.state.route.query, "");
  assert.equal(app.state.search.status, "idle");
  assert.deepEqual(app.state.search.results, []);
  assert.equal(app.state.source.mode, "unknown");
  app.navigate("#/search?player=open"); await settle();
  assert.equal(app.calls.length, 1);
});

test("leaving Search cancels its job and returning retries the interrupted query", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=rain", respectAbort: false });
  await settle(); const old = app.calls[0];
  app.navigate("#/library/playlists"); await settle();
  assert.equal(old.signal.aborted, true);
  assert.equal(app.state.search.status, "idle");
  old.succeed(); await settle();
  assert.equal(app.state.source.mode, "unknown");
  app.navigate("#/search?q=rain"); await settle();
  assert.equal(app.calls.length, 2);
  app.calls[1].succeed(); await settle();
  assert.equal(app.state.search.status, "ready");
});

test("replacing an entity cancels the older entity and preserves the latest result", async (t) => {
  const app = bootFixture(t, { hash: "#/album/old", respectAbort: false });
  await settle(); const old = app.calls[0];
  app.navigate("#/playlist/fresh"); await settle();
  assert.equal(old.signal.aborted, true);
  assert.equal(app.calls.length, 2);
  app.calls[1].succeed(); await settle();
  old.succeed(); await settle();
  assert.equal(app.state.entity.kind, "playlist");
  assert.equal(app.state.entity.id, "fresh");
  assert.equal(app.state.entity.data.playlist.id, "fresh");
  assert.equal(app.state.source.mode, "live");
});

test("settings, local collections and incomplete routes retire remote entity work", async (t) => {
  const app = bootFixture(t, { hash: "#/album/old", respectAbort: false });
  await settle(); const old = app.calls[0];
  app.navigate("#/settings");
  assert.equal(old.signal.aborted, true);
  app.navigate("#/collection/local");
  assert.equal(app.state.entity.kind, "collection");
  assert.equal(app.state.entity.status, "ready");
  app.navigate("#/album");
  assert.equal(app.state.entity.status, "error");
  old.succeed(); await settle();
  assert.equal(app.state.entity.id, null);
  assert.equal(app.state.entity.status, "error");
  assert.equal(app.calls.length, 1);
  assert.equal(app.state.source.mode, "unknown");
});

test("player-only route changes do not restart an entity's pending or ready request", async (t) => {
  const app = bootFixture(t, { hash: "#/album/one" });
  await settle(); const call = app.calls[0];
  app.navigate("#/album/one?player=open");
  app.navigate("#/album/one?player=open&ptab=lyrics");
  await settle();
  assert.equal(app.calls.length, 1);
  assert.equal(call.signal.aborted, false);
  call.succeed(); await settle(); const data = app.state.entity.data;
  app.navigate("#/album/one"); await settle();
  assert.equal(app.calls.length, 1);
  assert.strictEqual(app.state.entity.data, data);
});

test("Home boot and player-only routing issue one seven-shelf batch, not duplicates", async (t) => {
  const app = bootFixture(t, { hash: "#/home" });
  await settle();
  assert.equal(app.calls.length, HOME_RAILS.length);
  app.navigate("#/home?player=open"); await settle();
  assert.equal(app.calls.length, HOME_RAILS.length);
  for (const call of app.calls) call.succeed(); await settle();
  assert.equal(app.state.home.status, "ready");
  assert.equal(app.state.home.rails.length, HOME_RAILS.length);
});

test("leaving Home aborts its batch quietly and a return starts fresh shelves", async (t) => {
  const app = bootFixture(t, { hash: "#/home", respectAbort: false });
  await settle(); const old = app.calls.slice();
  app.navigate("#/library/playlists"); await settle();
  assert.ok(old.every((call) => call.signal.aborted));
  for (const call of old) call.succeed(); await settle();
  assert.equal(app.state.home.status, "idle");
  assert.equal(app.state.source.mode, "unknown");
  app.navigate("#/home"); await settle();
  const fresh = app.calls.filter((call) => !old.includes(call));
  assert.equal(fresh.length, HOME_RAILS.length);
  for (const call of fresh) call.succeed(); await settle();
  assert.equal(app.state.home.status, "ready");
});

test("submitted and recent queries keep route state aligned with the URL", async (t) => {
  const app = bootFixture(t);
  app.submit("rain"); await settle();
  assert.equal(app.state.route.query, "rain");
  assert.match(app.win.location.hash, /q=rain/);
  app.click("use-recent", "fresh"); await settle();
  assert.equal(app.state.route.query, "fresh");
  assert.match(app.win.location.hash, /q=fresh/);
  app.navigate("#/search?q=fresh&player=open"); await settle();
  assert.equal(app.calls.length, 2);
});

test("changing the search filter cancels the previous same-query intent", async (t) => {
  const app = bootFixture(t);
  app.submit("rain"); await settle(); const old = app.calls[0];
  app.click("set-search-filter", "albums"); await settle();
  assert.equal(old.signal.aborted, true);
  assert.equal(new URL(app.calls[1].url).pathname, "/api/search/albums");
  app.calls[1].succeed(); await settle();
  assert.equal(app.state.search.filter, "albums");
  assert.equal(app.state.search.results[0].kind, "album");
});

test("screen retirement never cancels a separate player stream-resolution request", async (t) => {
  const app = bootFixture(t);
  app.submit("rain"); await settle(); const search = app.calls[0];
  app.player.play([{ id: "playback-fixture", title: "Fixture", stream: "", image: "", duration: 20 }]);
  await settle();
  const stream = app.calls.find((call) => new URL(call.url).pathname === "/api/songs/playback-fixture");
  assert.ok(stream);
  app.navigate("#/library/playlists"); await settle();
  assert.equal(search.signal.aborted, true);
  assert.equal(stream.signal.aborted, false);
  assert.equal(app.player.requested, true);
  assert.equal(app.audio.src, "", "pending fake resolution is not audible playback");
});

test("a current genuine network failure still exposes the labelled sample path", async (t) => {
  const app = bootFixture(t, { fail: () => true });
  app.submit("fixture"); await settle(); await settle();
  assert.equal(app.state.search.status, "ready");
  assert.equal(app.state.source.mode, "sample");
  assert.ok(app.state.source.error);
  assert.ok(app.state.search.results.every((row) => row.demo));
});

test("a current HTTP failure is an error, not a fictional successful result", async (t) => {
  const app = bootFixture(t);
  app.submit("fixture"); await settle(); app.calls[0].http(404); await settle();
  for (const call of app.calls) if (!call.settled) call.http(404);
  await settle(); await settle();
  assert.equal(app.state.search.status, "error");
  assert.match(app.state.search.error.title, /404/);
  assert.equal(app.state.source.mode, "unknown");
  assert.deepEqual(app.state.search.results, []);
});

test("leaving an artist cancels the profile, songs and albums under one view intent", async (t) => {
  const app = bootFixture(t, { hash: "#/artist/one" });
  await settle(); assert.equal(app.calls.length, 3);
  app.navigate("#/about/privacy"); await settle();
  assert.ok(app.calls.every((call) => call.signal.aborted));
  assert.equal(app.state.route.name, "about");
  assert.equal(app.state.source.mode, "unknown");
});

test("Retry replaces an interrupted same-query request without stale cleanup winning", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=rain", respectAbort: false });
  await settle(); const old = app.calls[0];
  app.click("retry"); await settle();
  assert.equal(old.signal.aborted, true);
  assert.equal(app.calls.length, 2);
  old.succeed(); await settle();
  app.calls[1].succeed(); await settle();
  assert.equal(app.state.search.status, "ready");
  assert.equal(app.state.search.results[0].id, "rain");
});

test("initial query deep links issue exactly one request", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=rain" });
  await settle(); assert.equal(app.calls.length, 1);
  app.calls[0].succeed(); await settle();
  assert.equal(app.state.search.status, "ready");
  assert.equal(app.state.search.results[0].id, "rain");
});

test("Refresh on Search replaces its job while preserving query and filter", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=rain" });
  await settle();
  app.click("set-search-filter", "albums"); await settle();
  const old = app.calls.at(-1);
  app.click("refresh"); await settle();
  assert.equal(app.calls.length, 3, "Search refresh must not dispatch a no-op entity load");
  assert.equal(old.signal.aborted, true);
  const fresh = app.calls.at(-1);
  assert.equal(new URL(fresh.url).pathname, "/api/search/albums");
  assert.equal(new URL(fresh.url).searchParams.get("query"), "rain");
  fresh.succeed(); await settle();
  assert.equal(app.state.search.status, "ready");
  assert.equal(app.state.search.filter, "albums");
});

function listenerCounts(app) {
  const count = (target) => [...target.listeners.values()].reduce((sum, listeners) => sum + listeners.size, 0);
  return { root: count(app.root), document: count(app.doc), window: count(app.win), audio: count(app.audio),
    media: [...app.mediaQueries.values()].reduce((sum, target) => sum + count(target), 0) };
}

test("destroy cancels screen work and makes retained route/go callbacks inert", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=old", respectAbort: false });
  await settle(); const call = app.calls[0];
  const queuedRoute = [...app.win.listeners.get("hashchange")][0];
  assert.equal(typeof app.destroy, "function");
  app.destroy();
  assert.equal(call.signal.aborted, true);
  const source = { ...app.state.source }; const title = app.doc.title;
  app.doc.getElementById("dhun-view").innerHTML = "replacement view";
  const hash = app.win.location.hash;
  app.go({ ...app.state.route, query: "must-not-start" }, { replace: true });
  assert.equal(app.win.location.hash, hash);
  app.win.location.hash = "#/search?q=queued"; queuedRoute();
  call.succeed(); await settle();
  assert.equal(app.calls.length, 1);
  assert.deepEqual(app.state.source, source);
  assert.equal(app.doc.title, title);
  assert.equal(app.doc.getElementById("dhun-view").innerHTML, "replacement view");
  app.destroy(); // idempotent; no stale owner can be revived
});

test("non-cached pagehide releases owned listeners and cannot restart on pageshow", async (t) => {
  const app = bootFixture(t, { hash: "#/artist/one" });
  await settle(); assert.equal(app.calls.length, 3);
  app.win.emit("pagehide", { persisted: false }); await settle();
  assert.ok(app.calls.every((call) => call.signal.aborted));
  assert.deepEqual(listenerCounts(app), { root: 0, document: 0, window: 0, audio: 0, media: 0 });
  app.win.emit("pageshow", { persisted: true });
  app.submit("new"); await settle();
  assert.equal(app.calls.length, 3);
  assert.equal(app.state.source.mode, "unknown");
});

test("cached pagehide suspends view jobs and pageshow retries the current route once", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=old", respectAbort: false });
  await settle(); const old = app.calls[0];
  app.win.emit("pagehide", { persisted: true });
  assert.equal(old.signal.aborted, true);
  app.navigate("#/search?q=fresh");
  app.submit("must-not-start-while-cached");
  old.succeed(); await settle();
  assert.equal(app.calls.length, 1);
  assert.equal(app.state.source.mode, "unknown");
  app.win.emit("pageshow", { persisted: true }); await settle();
  assert.equal(app.calls.length, 2);
  assert.equal(new URL(app.calls[1].url).searchParams.get("query"), "fresh");
  app.win.emit("pageshow", { persisted: true }); await settle();
  assert.equal(app.calls.length, 2);
  app.calls[1].succeed(); await settle();
  assert.equal(app.state.search.results[0].id, "fresh");
});

test("route cancellation leaves a shared failover race alive for player resolution", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=rain", fail: (url) => url.startsWith("https://saavn.dev/") });
  await settle();
  app.player.play([{ id: "independent-player", title: "Fixture", stream: "", image: "", duration: 20 }]);
  await settle();
  const probes = app.calls.filter((call) => !call.url.startsWith("https://saavn.dev/"));
  assert.equal(probes.length, 2);
  app.navigate("#/library/playlists"); await settle();
  assert.equal(app.state.search.status, "idle");
  assert.ok(probes.every((call) => !call.signal.aborted), "the surviving player still needs instance selection");
  assert.equal(app.player.requested, true);
  assert.equal(app.state.source.mode, "unknown");
});

test("full component teardown aborts its player lookup but not another component", async (t) => {
  const app = bootFixture(t, { hash: "#/home" });
  const other = bootFixture(t, { hash: "#/search?q=other" });
  app.player.play([{ id: "owned-player", title: "Fixture", stream: "", image: "", duration: 20 }]);
  await settle();
  assert.equal(typeof app.destroy, "function");
  app.destroy();
  assert.ok(app.calls.every((call) => call.signal.aborted));
  assert.equal(other.calls[0].signal.aborted, false);
  other.calls[0].succeed(); await settle();
  assert.equal(other.state.search.results[0].id, "other");
  assert.equal(app.audio.paused, true);
});

test("repeated navigation and explicit remount do not accumulate owned listeners", async (t) => {
  const app = bootFixture(t);
  const initial = listenerCounts(app);
  for (let i = 0; i < 4; i += 1) {
    app.navigate("#/library/playlists"); app.navigate("#/settings"); app.navigate("#/search");
  }
  assert.deepEqual(listenerCounts(app), initial);
  assert.equal(typeof app.destroy, "function");
  app.destroy();
  assert.deepEqual(listenerCounts(app), { root: 0, document: 0, window: 0, audio: 0, media: 0 });
  const mounted = app.mount();
  assert.deepEqual(listenerCounts(app), initial);
  app.submit("fresh"); await settle();
  assert.equal(app.calls.length, 1);
  app.calls[0].succeed(); await settle();
  assert.equal(mounted.state.search.results[0].id, "fresh");
  mounted.destroy();
  assert.deepEqual(listenerCounts(app), { root: 0, document: 0, window: 0, audio: 0, media: 0 });
});

test("a retained toast timeout cannot mutate or render after component teardown", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=rain" });
  await settle(); app.calls[0].succeed(); await settle();
  app.click("toggle-like", undefined, { id: "rain" });
  assert.ok(app.timers.size > 0);
  const delayed = [...app.timers.values()][0];
  assert.equal(typeof app.destroy, "function");
  app.destroy();
  assert.equal(app.timers.size, 0);
  app.state.toast = { message: "replacement owner", tone: "info" };
  app.doc.getElementById("dhun-toast").innerHTML = "replacement toast";
  delayed();
  assert.equal(app.state.toast.message, "replacement owner");
  assert.equal(app.doc.getElementById("dhun-toast").innerHTML, "replacement toast");
});

test("visibility changes and cached-page suspension do not dispose independent player work", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=rain" });
  await settle(); const search = app.calls[0];
  app.doc.emit("visibilitychange");
  assert.equal(search.signal.aborted, false);
  app.player.play([{ id: "cached-player", title: "Fixture", stream: "", image: "", duration: 20 }]);
  await settle();
  const player = app.calls.find((call) => new URL(call.url).pathname === "/api/songs/cached-player");
  app.win.emit("pagehide", { persisted: true });
  assert.equal(search.signal.aborted, true);
  assert.equal(player.signal.aborted, false);
  assert.equal(app.player.requested, true);
});

test("a pending local import may persist but cannot update a destroyed component", async (t) => {
  const app = bootFixture(t); let finishRead;
  const text = new Promise((resolve) => { finishRead = resolve; });
  const original = app.state.library;
  const imported = { app: "DHUN web", version: 3, ...original, settings: { ...original.settings, theme: "light" } };
  const input = app.importFile({ size: 100, text: () => text });
  app.destroy();
  input.value = "replacement-input";
  app.doc.documentElement.dataset.dhunTheme = "replacement-theme";
  finishRead(JSON.stringify(imported)); await settle();
  assert.strictEqual(app.state.library, original);
  assert.equal(input.value, "replacement-input");
  assert.equal(app.doc.documentElement.dataset.dhunTheme, "replacement-theme");
  assert.equal(app.timers.size, 0);
  const mounted = app.mount();
  assert.equal(mounted.state.library.settings.theme, "light", "an accepted local storage transaction is not a cancelled catalogue job");
});

test("focused unsubmitted Search text and selection survive an asynchronous result render", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=committed", replaceSearchFields: true });
  await settle();
  const field = app.doc.querySelector("#search-input");
  field.focus(); field.value = 'new draft & धुन "quoted"'; field.setSelectionRange(4, 9);
  app.calls[0].succeed(); await settle();
  const current = app.doc.querySelector("#search-input");
  assert.strictEqual(current, field, "same-intent results must preserve the actual input, including IME ownership");
  assert.equal(current.value, 'new draft & धुन "quoted"');
  assert.strictEqual(app.doc.activeElement, current);
  assert.equal(current.selectionStart, 4); assert.equal(current.selectionEnd, 9);
  assert.equal(app.state.search.query, "committed", "an unsubmitted draft is not a new network intent");
  assert.equal(app.calls.length, 1);
});

test("explicit new Search intent replaces the prior draft with its committed query", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=old", replaceSearchFields: true });
  await settle(); const field = app.doc.querySelector("#search-input");
  field.focus(); field.value = "unsubmitted old draft"; field.setSelectionRange(3, 3);
  app.submit("fresh"); await settle();
  assert.equal(app.doc.querySelector("#search-input").value, "fresh");
  assert.equal(app.calls[0].signal.aborted, true);
  app.calls[1].succeed(); await settle();
  assert.equal(app.doc.querySelector("#search-input").value, "fresh");
  assert.equal(app.state.search.results[0].id, "fresh");
});

test("an HTTP error render preserves an actively edited draft without hiding the error", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=committed", replaceSearchFields: true });
  await settle(); const field = app.doc.querySelector("#search-input");
  field.focus(); field.value = "next draft"; field.setSelectionRange(2, 5);
  app.calls[0].http(404); await settle();
  for (const call of app.calls) if (!call.settled) call.http(404);
  await settle(); await settle();
  const current = app.doc.querySelector("#search-input");
  assert.equal(current.value, "next draft");
  assert.strictEqual(app.doc.activeElement, current);
  assert.equal(app.state.search.status, "error");
  assert.match(app.state.search.error.title, /404/);
});

test("a result does not steal input focus after the user leaves the field or Search", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=committed", replaceSearchFields: true });
  await settle(); app.doc.activeElement = app.doc.body;
  app.calls[0].succeed(); await settle();
  assert.strictEqual(app.doc.activeElement, app.doc.body);
  const field = app.doc.querySelector("#search-input"); field.focus(); field.value = "discard on navigation";
  app.navigate("#/library/playlists"); await settle();
  assert.equal(app.doc.querySelector("#search-input"), null);
  assert.notStrictEqual(app.doc.activeElement, field);
});
