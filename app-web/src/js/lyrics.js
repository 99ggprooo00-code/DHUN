/**
 * Synced lyrics.
 *
 * `parseLrc` is a mirror of shared/src/commonMain/kotlin/dev/dhun/lyrics/
 * LrcParser.kt, including what it tolerates:
 *   - `[mm:ss]` / `[mm:ss.xx]` / `[mm:ss.xxx]` (`[mm:ss,xx]` too — the Kotlin
 *     regex accepts `.` or `:` as the fraction separator)
 *   - several timestamps on one line: `[00:12.34][00:15.10]Repeat`
 *   - metadata tags (`[ti:…]`, `[ar:…]`, `[by:…]`) skipped
 *   - enhanced word timings `<mm:ss.xx>` stripped
 *   - unsynced fallback: a line with no timestamp becomes `{timeMs: null}`
 *     when `allowUnsyncedFallback` is true
 * Output is sorted by time, same as the app's.
 */

const TIMESTAMP = /\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]/g;
const WORD_TIMING = /<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>/g;
const METADATA_TAG = /^\[[a-z]{2,}:.*]$/i;

export function parseLrc(lrcText, { allowUnsyncedFallback = true } = {}) {
  const lines = [];
  for (const raw of String(lrcText ?? "").split(/\r?\n/)) {
    const line = raw.trim();
    if (line === "") continue;

    const stamps = [...line.matchAll(TIMESTAMP)];
    if (stamps.length === 0) {
      if (allowUnsyncedFallback && !METADATA_TAG.test(line)) {
        const text = line.replace(WORD_TIMING, "").trim();
        if (text !== "") lines.push({ timeMs: null, text });
      }
      continue;
    }

    const last = stamps[stamps.length - 1];
    const textStart = last.index + last[0].length;
    const text = line.slice(textStart).replace(WORD_TIMING, "").trim();

    for (const stamp of stamps) {
      const minutes = Number.parseInt(stamp[1], 10);
      const seconds = Number.parseInt(stamp[2], 10);
      const fraction = stamp[3] ?? "0";
      if (!Number.isFinite(minutes) || !Number.isFinite(seconds)) continue;
      const fractionMs = fractionToMs(fraction);
      lines.push({ timeMs: minutes * 60_000 + seconds * 1000 + fractionMs, text });
    }
  }

  return lines.sort((a, b) => {
    if (a.timeMs === null) return b.timeMs === null ? 0 : 1;
    if (b.timeMs === null) return -1;
    return a.timeMs - b.timeMs;
  });
}

/** `[mm:ss.xx]` → 120 ms, `[mm:ss.xxx]` → 123 ms, `[mm:ss,x]` → 100 ms. */
function fractionToMs(fraction) {
  if (!fraction) return 0;
  const digits = fraction.length;
  const value = Number.parseInt(fraction, 10);
  if (!Number.isFinite(value)) return 0;
  if (digits === 1) return value * 100;
  if (digits === 2) return value * 10;
  return value;
}

/** The index of the line that should be highlighted at `positionMs`.
 *  A track with no synced lines returns -1 and the UI says so. */
export function activeLineIndex(lines, positionMs) {
  let index = -1;
  for (let i = 0; i < lines.length; i += 1) {
    const timeMs = lines[i].timeMs;
    if (timeMs === null) continue;
    if (timeMs <= positionMs) index = i;
    else break;
  }
  return index;
}

export function isSynced(lines) {
  return lines.some((line) => line.timeMs !== null);
}
