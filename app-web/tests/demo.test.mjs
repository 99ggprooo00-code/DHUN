/**
 * The sample catalogue.
 *
 * It exists so a blocked network shows the shape of the app. It must never be
 * able to pretend to play: every row is marked `demo` and carries an empty
 * stream, which is what turns "nothing happened" into "this is an example".
 */
import test from "node:test";
import assert from "node:assert/strict";

import { DEMO_ALBUMS, DEMO_ARTISTS, DEMO_NOTICE, DEMO_SONGS, demoEntity, demoHome, searchDemo } from "../src/js/demo.js";

test("there is enough of a catalogue to lay out every screen", () => {
  assert.ok(DEMO_SONGS.length >= 12, "the sample catalogue is too small to fill a rail");
  assert.ok(DEMO_ALBUMS.length >= 3);
  assert.ok(DEMO_ARTISTS.length >= 3);
});

test("every sample row is marked and has no stream", () => {
  for (const song of DEMO_SONGS) {
    assert.equal(song.demo, true, `${song.title} is not marked as an example`);
    assert.equal(song.stream, "", `${song.title} carries a stream URL`);
    assert.ok(song.id.startsWith("demo-"), "a sample row must not collide with a real id");
    assert.ok(song.title && song.artist);
  }
});

test("the notice says what the rows are and that they are silent", () => {
  assert.ok(DEMO_NOTICE.includes("Sample catalogue"));
  assert.ok(DEMO_NOTICE.includes("carry no audio"));
});

test("searching the sample catalogue returns rows of the asked kind", () => {
  const song = searchDemo("songs", "monsoon")[0];
  assert.ok(song && song.demo === true && song.stream === "");
  const album = searchDemo("albums", "Dharan")[0];
  assert.ok(album && album.songs.length > 0, "an album result must carry its rows");
  assert.equal(album.kind, "album");
  const artist = searchDemo("artists", "naya")[0];
  assert.ok(artist && artist.id.startsWith("demo-ar-"));
  assert.deepEqual(searchDemo("songs", "zzzzzz"), [], "no match is an empty list, not a fabricated row");
});

test("an entity route finds its sample record, and a missing id finds nothing", () => {
  const album = DEMO_ALBUMS[0];
  assert.equal(demoEntity("album", album.id).id, album.id);
  assert.equal(demoEntity("album", "no-such-id"), null);
  assert.equal(demoEntity("artist", DEMO_ARTISTS[0].artist.id).artist.id, DEMO_ARTISTS[0].artist.id);
  assert.equal(demoEntity("track", "whatever"), null, "an unknown kind is not a route");
});

test("the sample home has rails with rows in them", () => {
  const rails = demoHome();
  assert.ok(rails.length >= 3);
  for (const rail of rails) assert.ok(rail.items.length > 0, `${rail.title} is empty`);
});
