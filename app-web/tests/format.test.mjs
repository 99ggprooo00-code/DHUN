import test from "node:test";
import assert from "node:assert/strict";

import {
  formatDuration,
  cacheOptionLabel,
  CACHE_OPTIONS_MB,
  trackSubtitle,
  pluralise,
  escapeHtml,
} from "../src/js/format.js";

test("durations read the way a music player writes them", () => {
  assert.equal(formatDuration(0), "0:00");
  assert.equal(formatDuration(5_000), "0:05");
  assert.equal(formatDuration(65_000), "1:05");
  assert.equal(formatDuration(600_000), "10:00");
  assert.equal(formatDuration(3_779_000), "1:02:59");
  assert.equal(formatDuration(-100), "0:00", "a negative position is clamped, not rendered");
  assert.equal(formatDuration(undefined), "0:00");
});

test("cache labels match SettingsViewModel.cacheOptionLabel", () => {
  assert.equal(cacheOptionLabel(0), "Unlimited");
  assert.equal(cacheOptionLabel(256), "256 MB");
  assert.equal(cacheOptionLabel(1023), "1023 MB");
  assert.equal(cacheOptionLabel(1024), "1 GB");
  assert.equal(cacheOptionLabel(4096), "4 GB");
  assert.deepEqual(CACHE_OPTIONS_MB, [256, 512, 1024, 2048, 4096, 0]);
});

test("track subtitles and counts read like the app's", () => {
  assert.equal(trackSubtitle({ artist: "Naya Koirala", album: "Dharan Nights" }), "Naya Koirala • Dharan Nights");
  assert.equal(trackSubtitle({ artist: "Naya Koirala" }), "Naya Koirala");
  assert.equal(pluralise(1, "song"), "1 song");
  assert.equal(pluralise(4, "song"), "4 songs");
  assert.equal(pluralise(0, "track"), "0 tracks");
});

test("interpolated values are escaped — a title is data, never markup", () => {
  assert.equal(escapeHtml('<img src=x onerror="alert(1)">'), "&lt;img src=x onerror=&quot;alert(1)&quot;&gt;");
  assert.equal(escapeHtml("a & b"), "a &amp; b");
  assert.equal(escapeHtml("'quote'"), "&#39;quote&#39;");
  assert.equal(escapeHtml(null), "");
  assert.equal(escapeHtml(undefined), "");
});
