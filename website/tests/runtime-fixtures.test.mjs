import assert from "node:assert/strict";
import { test } from "node:test";
import { audioResponse, installSyntheticPlayback, toneWav, TONE_ORIGIN, TONE_PATH } from "./runtime-fixtures.mjs";

test("runtime tone is generated PCM with a real bounded duration, not provider music", () => {
  const bytes = toneWav(1);
  assert.equal(bytes.toString("ascii", 0, 4), "RIFF");
  assert.equal(bytes.toString("ascii", 8, 12), "WAVE");
  assert.equal(bytes.readUInt32LE(24), 8000);
  assert.equal(bytes.readUInt32LE(40), 16000);
  assert.equal(bytes.length, 16044);
  assert.notEqual(bytes.subarray(44).some(byte => byte !== 0), false);
});

test("synthetic media serves valid range responses for real browser seek", () => {
  const bytes = toneWav(1);
  assert.equal(audioResponse(bytes).status, 200);
  const part = audioResponse(bytes, "bytes=100-199");
  assert.equal(part.status, 206); assert.equal(part.body.length, 100);
  assert.equal(part.headers["content-range"], `bytes 100-199/${bytes.length}`);
  assert.equal(audioResponse(bytes, "bytes=100-").body.length, bytes.length - 100);
  assert.equal(audioResponse(bytes, "bytes=999999-").status, 416);
  assert.equal(audioResponse(bytes, "bytes=100-2").status, 416);
});

test("positive fixture intercepts only shipped API hosts and its exact synthetic media path", async () => {
  const routes = [];
  await installSyntheticPlayback({ async route(match, handle) { routes.push({ match, handle }); } });
  assert.equal(routes.length, 2);
  assert.equal(routes[0].match(new URL("https://saavn.dev/api/search/songs")), true);
  assert.equal(routes[0].match(new URL("http://127.0.0.1:8080/app/js/main.js")), false);
  assert.equal(routes[1].match(new URL(`${TONE_ORIGIN}${TONE_PATH}?track=a`)), true);
  assert.equal(routes[1].match(new URL(`${TONE_ORIGIN}/unrelated.mp4`)), false);
});

test("positive fixture never invents a successful song for a demo ID", async () => {
  const routes = [];
  await installSyntheticPlayback({ async route(match, handle) { routes.push({ match, handle }); } });
  let response;
  await routes[0].handle({ request: () => ({ url: () => "https://saavn.dev/api/songs/demo-tr-1" }), fulfill: async value => { response = value; } });
  assert.deepEqual(JSON.parse(response.body).data, []);
});
