import test from "node:test";
import assert from "node:assert/strict";
import { bootFixture } from "./helpers/boot-fixture.mjs";

test("playlist deletion discloses retained recovery and legacy copies", (t) => {
  const app = bootFixture(t, { hash: "#/collection/privacy-test" });
  app.state.library.playlists.push({ id: "privacy-test", name: "Private playlist", trackIds: [], createdAt: 1, updatedAt: 1 });
  app.click("delete-playlist");
  const dialog = String(app.doc.querySelector("#dhun-sheet").innerHTML);
  assert.match(dialog, /removed from the current library/);
  assert.match(dialog, /Older recovery records and legacy storage may still contain it/);
  assert.match(dialog, /songs themselves are not affected/);
});
