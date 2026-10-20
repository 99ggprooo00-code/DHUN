/**
 * Presentation helpers. Pure functions, so every edge is cheap to assert.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { describeLength, formatCount, formatDuration, greetingFor, metaLine, percent, pluralise, trackSubtitle } from "../src/js/format.js";

test("durations are the catalogue's seconds, not milliseconds", () => {
  assert.equal(formatDuration(0), "0:00");
  assert.equal(formatDuration(7), "0:07");
  assert.equal(formatDuration(187), "3:07");
  assert.equal(formatDuration(3600), "1:00:00");
  assert.equal(formatDuration(3725), "1:02:05");
});

test("a duration that is absent or nonsense reads as zero, never NaN", () => {
  assert.equal(formatDuration(undefined), "0:00");
  assert.equal(formatDuration(null), "0:00");
  assert.equal(formatDuration(-40), "0:00");
  assert.equal(formatDuration("nope"), "0:00");
});

test("collection lengths say songs and minutes, and drop the minutes at zero", () => {
  assert.equal(describeLength(1, 65), "1 song · 1 min");
  assert.equal(describeLength(12, 1800), "12 songs · 30 min");
  assert.equal(describeLength(3, 0), "3 songs");
});

test("counts round the way a music app shows them", () => {
  assert.equal(formatCount(0), "0");
  assert.equal(formatCount(999), "999");
  assert.equal(formatCount(1200), "1.2K");
  assert.equal(formatCount(12_400), "12K");
  assert.equal(formatCount(3_400_000), "3.4M");
});

test("the track subtitle skips what the catalogue did not send", () => {
  assert.equal(trackSubtitle({ artist: "Naya", album: { name: "Monsoon" } }), "Naya · Monsoon");
  assert.equal(trackSubtitle({ artist: "Naya" }), "Naya");
  assert.equal(trackSubtitle({}), "");
});

test("the greeting is a function of the hour, so it can be tested at any hour", () => {
  assert.equal(greetingFor(2), "Still up?");
  assert.equal(greetingFor(8), "Good morning");
  assert.equal(greetingFor(14), "Good afternoon");
  assert.equal(greetingFor(19), "Good evening");
  assert.equal(greetingFor(23), "Good night");
});

test("percent is clamped and survives a zero duration", () => {
  assert.equal(percent(30, 120), 25);
  assert.equal(percent(0, 0), 0);
  assert.equal(percent(-10, 100), 0);
  assert.equal(percent(500, 100), 100);
});

test("small helpers do not print 'undefined'", () => {
  assert.equal(metaLine(["Hindi", null, 2024, ""]), "Hindi · 2024");
  assert.equal(pluralise(1, "track"), "1 track");
  assert.equal(pluralise(2, "track"), "2 tracks");
});
