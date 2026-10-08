import test from "node:test";
import assert from "node:assert/strict";

import {
  BANDS_HZ,
  BAND_COUNT,
  MIN_GAIN_DB,
  MAX_GAIN_DB,
  CUSTOM_ID,
  PRESETS,
  clampGain,
  clampGains,
  frequencyLabel,
  formatDb,
  presetById,
  matchingPreset,
  createEqualizerState,
  withBandGain,
  withPreamp,
  withPreset,
} from "../src/js/equalizer.js";

test("the band geometry is libVLC's 10 bands, ±20 dB", () => {
  assert.deepEqual(BANDS_HZ, [60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000]);
  assert.equal(BAND_COUNT, 10);
  assert.equal(MIN_GAIN_DB, -20);
  assert.equal(MAX_GAIN_DB, 20);
});

test("every preset carries ten gains and a preamp", () => {
  assert.equal(PRESETS.length, 18);
  for (const preset of PRESETS) {
    assert.equal(preset.gainsDb.length, BAND_COUNT, `${preset.id} must have 10 bands`);
    for (const gain of preset.gainsDb) {
      assert.ok(gain >= MIN_GAIN_DB && gain <= MAX_GAIN_DB, `${preset.id} gain out of range`);
    }
    assert.ok(preset.preampDb >= MIN_GAIN_DB && preset.preampDb <= MAX_GAIN_DB);
  }
});

test("band labels read the way the app writes them", () => {
  assert.equal(frequencyLabel(60), "60 Hz");
  assert.equal(frequencyLabel(1000), "1 kHz");
  assert.equal(frequencyLabel(16000), "16 kHz");
  // 14 kHz is not a whole number of kHz, so it stays in Hz — as in Kotlin.
  assert.equal(frequencyLabel(14000), "14 kHz");
  assert.equal(frequencyLabel(310), "310 Hz");
});

test("gains clamp like libVLC, and NaN collapses to flat", () => {
  assert.equal(clampGain(-99), MIN_GAIN_DB);
  assert.equal(clampGain(99), MAX_GAIN_DB);
  assert.equal(clampGain(Number.NaN), 0);
  assert.equal(clampGain(Number.POSITIVE_INFINITY), 0);
  assert.deepEqual(clampGains([1, 2]), [1, 2, 0, 0, 0, 0, 0, 0, 0, 0]);
  assert.equal(clampGains([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]).length, BAND_COUNT);
});

test("dB formatting matches SettingsScreen.formatDb", () => {
  assert.equal(formatDb(4.82), "+4.8 dB");
  assert.equal(formatDb(0), "0 dB");
  assert.equal(formatDb(-3.25), "-3.2 dB", "ties round towards +infinity, as kotlin.math.round does");
  assert.equal(formatDb(-0), "0 dB");
});

test("a stock curve is recognised, a moved slider is Custom", () => {
  const flat = createEqualizerState();
  assert.equal(flat.presetId, CUSTOM_ID, "zeros with a 0 dB preamp is not the Flat preset (Flat is +12 dB)");

  const state = withPreset(flat, "rock");
  assert.equal(state.presetId, "rock");
  assert.equal(matchingPreset(state.gainsDb, state.preampDb).id, "rock");

  const moved = withBandGain(state, 3, 0);
  assert.equal(moved.presetId, CUSTOM_ID);

  // Inside the epsilon it is still that preset; outside it is not.
  assert.equal(withBandGain(state, 0, 8.01).presetId, "rock");
  assert.equal(withBandGain(state, 0, 8.5).presetId, CUSTOM_ID);
});

test("moving the preamp re-matches the curve too", () => {
  const state = withPreset(createEqualizerState(), "pop");
  assert.equal(withPreamp(state, 6).presetId, "pop");
  assert.equal(withPreamp(state, 12).presetId, CUSTOM_ID);
});

test("selecting Custom or an unknown preset is a no-op", () => {
  const state = withPreset(createEqualizerState(), "jazz" /* unknown */);
  assert.equal(state.presetId, CUSTOM_ID);
  const rock = withPreset(state, "rock");
  assert.equal(withPreset(rock, CUSTOM_ID), rock, "Custom cannot be selected — it is a label");
});

test("out-of-range band indices are ignored rather than throwing", () => {
  const state = createEqualizerState();
  assert.equal(withBandGain(state, 99, 12), state);
  assert.equal(withBandGain(state, -1, 12), state);
  assert.ok(presetById("flat"));
  assert.equal(presetById("nope"), null);
});
