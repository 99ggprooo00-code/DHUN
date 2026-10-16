import test from "node:test";
import assert from "node:assert/strict";

import { formatDuration, trackSubtitle, pluralise } from "../src/js/format.js";

test("durations read the way a music player writes them", () => {
  assert.equal(formatDuration(0), "0:00");
  assert.equal(formatDuration(5_000), "0:05");
  assert.equal(formatDuration(65_000), "1:05");
  assert.equal(formatDuration(600_000), "10:00");
  assert.equal(formatDuration(3_779_000), "1:02:59");
  assert.equal(formatDuration(-100), "0:00", "a negative position is clamped, not rendered");
  assert.equal(formatDuration(undefined), "0:00");
});

// Escaping lives in `dom.js` (the `html` tag) and is asserted in dom.test.mjs —
// a second copy here would be a second thing to keep correct.
//
// The cache-budget helpers (`cacheOptionLabel`, `CACHE_OPTIONS_MB`) used to be
// asserted here. They are gone with the Downloads screen: a browser tab does not
// have a cache the user should be asked to budget, so the app stopped asking.

test("track subtitles and counts read like the app's", () => {
  assert.equal(trackSubtitle({ artist: "Naya Koirala", album: "Dharan Nights" }), "Naya Koirala • Dharan Nights");
  assert.equal(trackSubtitle({ artist: "Naya Koirala" }), "Naya Koirala");
  assert.equal(pluralise(1, "song"), "1 song");
  assert.equal(pluralise(4, "song"), "4 songs");
  assert.equal(pluralise(0, "track"), "0 tracks");
});
