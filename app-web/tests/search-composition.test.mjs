/** Browser-controller protocol doubles, NOT Android/physical-IME observations. */
import test from "node:test";
import assert from "node:assert/strict";
import { bootFixture, settle } from "./helpers/boot-fixture.mjs";

function watchInput(field) {
  let text = field.value, values = 0, ranges = 0;
  Object.defineProperty(field, "value", { configurable: true, get: () => text, set: (value) => { values += 1; text = value; } });
  const range = field.setSelectionRange.bind(field);
  field.setSelectionRange = (...args) => { ranges += 1; return range(...args); };
  return () => ({ values, ranges });
}

test("an async reply preserves the composing input node without rewriting value or selection", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=committed", replaceSearchFields: true });
  await settle(); const field = app.doc.querySelector("#search-input");
  field.focus(); field.value = "धुन composing draft"; field.setSelectionRange(1, 3);
  app.composeStart(field); const writes = watchInput(field);
  app.calls[0].succeed(); await settle();
  assert.strictEqual(app.doc.querySelector("#search-input"), field, "IME state belongs to the original DOM input, not a reconstructed string");
  assert.strictEqual(app.doc.activeElement, field);
  assert.equal(field.value, "धुन composing draft");
  assert.deepEqual(writes(), { values: 0, ranges: 0 });
  assert.equal(app.state.search.status, "ready");
  assert.match(String(app.doc.getElementById("search-results").innerHTML), /Fixture committed/);
  assert.equal(app.calls.length, 1, "composition text must not be sent before submission");
});

test("same-intent error and empty renders retain the input while publishing their actual state", async (t) => {
  const empty = bootFixture(t, { hash: "#/search?q=empty", replaceSearchFields: true });
  const error = bootFixture(t, { hash: "#/search?q=error", replaceSearchFields: true });
  await settle(); const a = empty.doc.querySelector("#search-input"), b = error.doc.querySelector("#search-input");
  a.focus(); a.value = "नेपाली draft"; empty.composeStart(a);
  b.focus(); b.value = "हिन्दी draft"; error.composeStart(b);
  empty.calls[0].succeed({ results: [] }); error.calls[0].http(404); await settle();
  for (const call of error.calls) if (!call.settled) call.http(404);
  await settle(); await settle();
  assert.strictEqual(empty.doc.querySelector("#search-input"), a);
  assert.strictEqual(error.doc.querySelector("#search-input"), b);
  assert.match(String(empty.doc.getElementById("search-results").innerHTML), /No songs/);
  assert.match(String(error.doc.getElementById("search-results").innerHTML), /404/);
  assert.equal(a.value, "नेपाली draft"); assert.equal(b.value, "हिन्दी draft");
});

test("player-only route rendering does not detach an active composition target", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=committed", replaceSearchFields: true });
  await settle(); const field = app.doc.querySelector("#search-input"); field.focus(); app.composeStart(field);
  app.navigate("#/search?q=committed&player=open");
  assert.strictEqual(app.doc.querySelector("#search-input"), field);
  assert.strictEqual(app.doc.activeElement, field);
  assert.equal(app.calls.length, 1);
});

test("a composing Search cannot submit an incomplete draft; composition end enables explicit submit", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=committed", replaceSearchFields: true });
  await settle(); const field = app.doc.querySelector("#search-input"); field.focus(); field.value = "अधुरो"; app.composeStart(field);
  app.submit("अधुरो"); await settle();
  assert.equal(app.calls.length, 1); assert.equal(app.state.search.query, "committed");
  app.composeEnd(field, "अधुरो"); app.submit("नयाँ गीत"); await settle();
  assert.equal(app.calls.length, 2);
  assert.equal(app.state.search.query, "नयाँ गीत");
  assert.equal(app.calls[0].signal.aborted, true);
});

test("IME Escape cannot close the app player, while normal Escape still works after composition", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=committed&player=open", replaceSearchFields: true });
  await settle(); const field = app.doc.querySelector("#search-input"); field.focus(); app.composeStart(field);
  const original = app.win.location.hash;
  assert.equal(app.key("Escape", { isComposing: true }), false);
  assert.equal(app.win.location.hash, original);
  assert.equal(app.key("Escape", { keyCode: 229 }), false);
  assert.equal(app.win.location.hash, original);
  app.composeEnd(field); app.key("Escape");
  assert.doesNotMatch(app.win.location.hash, /player=open/);
});

test("old composition events cannot resurrect text or block a newer route intent", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=old", replaceSearchFields: true });
  await settle(); const old = app.doc.querySelector("#search-input"); old.focus(); old.value = "old composition"; app.composeStart(old);
  app.navigate("#/search?q=fresh"); await settle(); app.composeEnd(old, "stale");
  assert.equal(app.doc.querySelector("#search-input").value, "fresh");
  assert.equal(app.calls.length, 2);
  app.submit("latest"); await settle();
  assert.equal(app.calls.length, 3); assert.equal(app.state.search.query, "latest");
});

test("composition ownership is retired by clear, navigation and teardown", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=old", replaceSearchFields: true });
  await settle(); const old = app.doc.querySelector("#search-input"); old.focus(); app.composeStart(old);
  app.click("clear-search"); app.composeEnd(old, "stale");
  assert.equal(app.doc.querySelector("#search-input").value, "");
  app.navigate("#/library/playlists"); app.composeEnd(old, "stale");
  assert.equal(app.doc.querySelector("#search-input"), null);
  app.navigate("#/search?q=new"); await settle(); app.submit("fresh"); await settle();
  assert.equal(app.state.search.query, "fresh");
  app.destroy(); app.composeEnd(old, "must stay inert"); assert.equal(app.calls.length, 3);
});

test("outside focus is not stolen by partial Search rendering", async (t) => {
  const app = bootFixture(t, { hash: "#/search?q=committed", replaceSearchFields: true });
  await settle(); app.doc.activeElement = app.doc.body;
  app.calls[0].succeed(); await settle();
  assert.strictEqual(app.doc.activeElement, app.doc.body);
  assert.equal(app.state.search.status, "ready");
});
