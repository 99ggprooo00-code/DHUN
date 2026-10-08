/**
 * Equalizer — geometry, presets and the browser engine binding.
 *
 * Mirror of shared/src/commonMain/kotlin/dev/dhun/player/equalizer/:
 *   EqualizerBands.kt      → BANDS_HZ, MIN_GAIN_DB, MAX_GAIN_DB, clampGain,
 *                            frequencyLabel
 *   EqualizerPreset.kt     → the 18 VLC 10-band presets, CUSTOM_ID, matching
 *   EqualizerSession.kt    → setEnabled / selectPreset / setPreamp / setBandGain
 *
 * The values are libVLC's (`f_vlc_frequency_table_10b`, `eqz_preset_10b`),
 * copied so the web app shows the same curves under the same names.
 */

/** EqualizerBands.COUNT / FREQUENCIES_HZ. */
export const BANDS_HZ = Object.freeze([60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000]);
export const BAND_COUNT = BANDS_HZ.length;

/** EqualizerBands.MIN_GAIN_DB / MAX_GAIN_DB — libVLC's range. */
export const MIN_GAIN_DB = -20;
export const MAX_GAIN_DB = 20;

/** EqualizerPresets.CUSTOM_ID. */
export const CUSTOM_ID = "custom";

/** EqualizerPresets.MATCH_EPSILON_DB. */
export const MATCH_EPSILON_DB = 0.05;

function preset(id, displayName, preampDb, gainsDb) {
  if (gainsDb.length !== BAND_COUNT) {
    throw new Error(`preset '${id}' must have ${BAND_COUNT} bands, was ${gainsDb.length}`);
  }
  return Object.freeze({ id, displayName, preampDb, gainsDb: Object.freeze(gainsDb) });
}

/** The 18 VLC presets, in the app's order (EqualizerPresets.ALL). */
export const PRESETS = Object.freeze([
  preset("flat", "Flat", 12, [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]),
  preset("classical", "Classical", 12, [0, 0, 0, 0, 0, 0, -7.2, -7.2, -7.2, -9.6]),
  preset("club", "Club", 6, [0, 0, 8, 5.6, 5.6, 5.6, 3.2, 0, 0, 0]),
  preset("dance", "Dance", 5, [9.6, 7.2, 2.4, 0, 0, -5.6, -7.2, -7.2, 0, 0]),
  preset("fullbass", "Full bass", 5, [-8, 9.6, 9.6, 5.6, 1.6, -4, -8, -10.4, -11.2, -11.2]),
  preset("fullbasstreble", "Full bass and treble", 4, [7.2, 5.6, 0, -7.2, -4.8, 1.6, 8, 11.2, 12, 12]),
  preset("fulltreble", "Full treble", 3, [-9.6, -9.6, -9.6, -4, 2.4, 11.2, 16, 16, 16, 16.8]),
  preset("headphones", "Headphones", 4, [4.8, 11.2, 5.6, -3.2, -2.4, 1.6, 4.8, 9.6, 12.8, 14.4]),
  preset("largehall", "Large Hall", 5, [10.4, 10.4, 5.6, 5.6, 0, -4.8, -4.8, -4.8, 0, 0]),
  preset("live", "Live", 7, [-4.8, 0, 4, 5.6, 5.6, 5.6, 4, 2.4, 2.4, 2.4]),
  preset("party", "Party", 6, [7.2, 7.2, 0, 0, 0, 0, 0, 0, 7.2, 7.2]),
  preset("pop", "Pop", 6, [-1.6, 4.8, 7.2, 8, 5.6, 0, -2.4, -2.4, -1.6, -1.6]),
  preset("reggae", "Reggae", 8, [0, 0, 0, -5.6, 0, 6.4, 6.4, 0, 0, 0]),
  preset("rock", "Rock", 5, [8, 4.8, -5.6, -8, -3.2, 4, 8.8, 11.2, 11.2, 11.2]),
  preset("ska", "Ska", 6, [-2.4, -4.8, -4, 0, 4, 5.6, 8.8, 9.6, 11.2, 9.6]),
  preset("soft", "Soft", 5, [4.8, 1.6, 0, -2.4, 0, 4, 8, 9.6, 11.2, 12]),
  preset("softrock", "Soft rock", 7, [4, 4, 2.4, 0, -4, -5.6, -3.2, 0, 2.4, 8.8]),
  preset("techno", "Techno", 5, [8, 5.6, 0, -5.6, -4.8, 0, 8, 9.6, 9.6, 8.8]),
]);

/** EqualizerBands.clampGain — NaN/Infinity collapse to 0 dB, then clamp. */
export function clampGain(gainDb) {
  if (!Number.isFinite(gainDb)) return 0;
  return Math.min(MAX_GAIN_DB, Math.max(MIN_GAIN_DB, gainDb));
}

/** EqualizerBands.clampGains — always exactly BAND_COUNT long. */
export function clampGains(gains) {
  const clamped = (gains ?? []).slice(0, BAND_COUNT).map(clampGain);
  while (clamped.length < BAND_COUNT) clamped.push(0);
  return clamped;
}

/** EqualizerBands.frequencyLabel — "60 Hz", "1 kHz", "16 kHz". */
export function frequencyLabel(hz) {
  return hz >= 1000 && hz % 1000 === 0 ? `${hz / 1000} kHz` : `${hz} Hz`;
}

/** SettingsScreen.formatDb — "+4.8 dB", "0 dB", "-3.2 dB". */
export function formatDb(db) {
  const rounded = Math.round(db * 10) / 10;
  return rounded > 0 ? `+${rounded} dB` : `${rounded} dB`;
}

/** EqualizerBands.zeros. */
export function zeroGains() {
  return Array(BAND_COUNT).fill(0);
}

/** EqualizerPresets.byId. */
export function presetById(id) {
  return PRESETS.find((p) => p.id === id) ?? null;
}

/**
 * EqualizerPresets.matching — first stock preset whose preamp and all ten
 * gains are within MATCH_EPSILON_DB, else null.
 */
export function matchingPreset(gainsDb, preampDb, epsilon = MATCH_EPSILON_DB) {
  return (
    PRESETS.find((p) => {
      if (Math.abs(p.preampDb - preampDb) > epsilon) return false;
      return p.gainsDb.every(
        (gain, i) => Math.abs(gain - (gainsDb[i] ?? 0)) <= epsilon,
      );
    }) ?? null
  );
}

export function createEqualizerState(initial = {}) {
  const gainsDb = clampGains(initial.gainsDb ?? zeroGains());
  const preampDb = clampGain(initial.preampDb ?? 0);
  return {
    enabled: Boolean(initial.enabled),
    preampDb,
    gainsDb,
    presetId: initial.presetId ?? (matchingPreset(gainsDb, preampDb)?.id ?? CUSTOM_ID),
  };
}

/** EqualizerSession.setBandGain / setPreamp — the preset becomes "custom"
 *  unless the resulting curve still matches a stock one. */
export function withBandGain(state, index, gainDb) {
  if (index < 0 || index >= BAND_COUNT) return state;
  const gainsDb = state.gainsDb.map((g, i) => (i === index ? clampGain(gainDb) : g));
  return {
    ...state,
    gainsDb,
    presetId: matchingPreset(gainsDb, state.preampDb)?.id ?? CUSTOM_ID,
  };
}

export function withPreamp(state, preampDb) {
  const next = clampGain(preampDb);
  return {
    ...state,
    preampDb: next,
    presetId: matchingPreset(state.gainsDb, next)?.id ?? CUSTOM_ID,
  };
}

/** EqualizerSession.selectPreset — a no-op for unknown ids, including
 *  CUSTOM_ID, exactly as the app's session contract says. */
export function withPreset(state, id) {
  if (id === CUSTOM_ID) return state;
  const preset = presetById(id);
  if (!preset) return state;
  return {
    enabled: state.enabled,
    preampDb: preset.preampDb,
    gainsDb: [...preset.gainsDb],
    presetId: preset.id,
  };
}

/**
 * The browser engine binding.
 *
 * Web Audio has no 10-band filter node, so the curve is built from one
 * BiquadFilterNode per band: lowshelf for the lowest band, highshelf for the
 * highest, peaking for the eight between — the nearest honest mapping of
 * libVLC's fixed 10-band geometry. Preamp is a GainNode ahead of the chain.
 *
 * Returns null when the browser has no Web Audio (or refuses to create the
 * graph), and the UI then shows the sliders disabled rather than pretending.
 */
export function createWebAudioEqualizer(audioContext, mediaElement) {
  if (!audioContext || typeof audioContext.createMediaElementSource !== "function") return null;
  try {
    const source = audioContext.createMediaElementSource(mediaElement);
    const preamp = audioContext.createGain();
    const filters = BANDS_HZ.map((hz, i) => {
      const node = audioContext.createBiquadFilter();
      node.type = i === 0 ? "lowshelf" : i === BANDS_HZ.length - 1 ? "highshelf" : "peaking";
      node.frequency.value = hz;
      node.Q.value = 1;
      node.gain.value = 0;
      return node;
    });
    let node = source;
    node.connect(preamp);
    node = preamp;
    for (const filter of filters) {
      node.connect(filter);
      node = filter;
    }
    node.connect(audioContext.destination);

    return {
      /** Applies an EqualizerState. Disabled means a flat, unity chain. */
      apply(state) {
        const on = Boolean(state?.enabled);
        preamp.gain.value = on ? dbToGain(state.preampDb) : 1;
        filters.forEach((filter, i) => {
          filter.gain.value = on ? clampGain(state.gainsDb[i] ?? 0) : 0;
        });
      },
      disconnect() {
        try {
          source.disconnect();
        } catch {
          /* already detached — nothing to undo */
        }
      },
    };
  } catch {
    return null;
  }
}

function dbToGain(db) {
  return 10 ** (db / 20);
}
