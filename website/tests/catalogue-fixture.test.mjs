import assert from "node:assert/strict";
import { test } from "node:test";
import { API_SOURCES } from "../../app-web/src/js/api.js";
import { installCatalogueOutage, newAppContext } from "./catalogue-fixture.mjs";

async function harness(outage) {
  let match;
  let handle;
  const context = { async route(predicate, handler) { match = predicate; handle = handler; } };
  await installCatalogueOutage(context, outage);
  return { context, match, handle };
}

test("catalogue fixture: all configured wrapper API requests are controlled", async () => {
  const { match } = await harness();
  assert.equal(API_SOURCES.length, 3, "coordinate any provider/origin change, do not expand silently");
  for (const { origin } of API_SOURCES) {
    assert.equal(match(new URL(`${origin}/api/search/songs?query=fixture`)), true);
    assert.equal(match(new URL(`${origin}/api/albums?id=fixture`)), true);
  }
});

test("catalogue fixture: local assets, unrelated origins and non-API paths are not intercepted", async () => {
  const { match } = await harness();
  for (const value of [
    "http://127.0.0.1:8080/app/js/main.js",
    "http://127.0.0.1:8080/app/sw.js",
    "https://saavn.dev/assets/cover.jpg",
    "https://saavn.dev/api-lookalike/search",
    "https://saavn.dev.example/api/search/songs",
    "http://saavn.dev/api/search/songs",
    "https://aac.saavncdn.com/audio.mp4",
  ]) assert.equal(match(new URL(value)), false, value);
});

test("catalogue fixture: real fetch sees an explicit uncached HTTP failure, never invented success/audio", async () => {
  const { handle } = await harness({ kind: "http" });
  let response;
  await handle({ fulfill: async (value) => { response = value; } });
  assert.equal(response.status, 503);
  assert.equal(response.headers["access-control-allow-origin"], "*");
  assert.equal(response.headers["cache-control"], "no-store");
  assert.equal(response.headers["content-type"], "application/json");
  assert.equal(JSON.parse(response.body).success, false);
  assert.match(JSON.parse(response.body).message, /Controlled browser-test.*not a live response/);
  assert.equal("data" in JSON.parse(response.body), false);
});

test("catalogue fixture: context options and real service-worker behaviour remain intact", async () => {
  const context = { async route() {} };
  const options = { viewport: { width: 390, height: 844 }, colorScheme: "dark" };
  let received;
  const result = await newAppContext({ async newContext(value) { received = value; return context; } }, options);
  assert.equal(received, options);
  assert.equal(result, context);
  assert.equal("serviceWorkers" in received, false, "do not bypass the offline-shell test");
});

test("catalogue fixture: setup failure propagates instead of producing a green partial run", async () => {
  const failure = new Error("route setup failed");
  let closed = false;
  const context = { async route() { throw failure; }, async close() { closed = true; } };
  await assert.rejects(() => newAppContext({ async newContext() { return context; } }, {}), (error) => error === failure);
  assert.equal(closed, true);
});

test("catalogue fixture: default network refusal exercises the real network/sample path", async () => {
  const { handle } = await harness();
  let code;
  await handle({ abort: async (value) => { code = value; }, fulfill: () => assert.fail("must not invent a response") });
  assert.equal(code, "failed");
});

test("catalogue fixture: unknown scenarios fail rather than falling through to live requests", async () => {
  await assert.rejects(() => harness({ kind: "unknown" }), /Unknown catalogue fixture/);
});
