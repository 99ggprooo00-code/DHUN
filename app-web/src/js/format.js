/** Presentation helpers — the small formatting rules the app's screens share. */

/** "3:07" / "1:02:59". The app shows a plain m:ss for tracks. */
export function formatDuration(ms) {
  const total = Math.max(0, Math.round((ms ?? 0) / 1000));
  const seconds = total % 60;
  const minutes = Math.floor(total / 60) % 60;
  const hours = Math.floor(total / 3600);
  const mm = hours > 0 ? String(minutes).padStart(2, "0") : String(minutes);
  return hours > 0
    ? `${hours}:${mm}:${String(seconds).padStart(2, "0")}`
    : `${mm}:${String(seconds).padStart(2, "0")}`;
}

/** SettingsViewModel.cacheOptionLabel — 0 → "Unlimited", <1024 → "512 MB",
 *  else → "2 GB". */
export function cacheOptionLabel(mb) {
  if (mb === 0) return "Unlimited";
  if (mb >= 1 && mb <= 1023) return `${mb} MB`;
  return `${Math.floor(mb / 1024)} GB`;
}

/** SettingsViewModel.CACHE_OPTIONS_MB. */
export const CACHE_OPTIONS_MB = Object.freeze([256, 512, 1024, 2048, 4096, 0]);

/** TrackRow subtitle: "Artist • Album". */
export function trackSubtitle(track) {
  return [track.artist, track.album].filter(Boolean).join(" • ");
}

/** Pluralise the app's way: "1 song" / "4 songs", "1 track" / "3 tracks". */
export function pluralise(count, singular) {
  return `${count} ${singular}${count === 1 ? "" : "s"}`;
}

/** Escape for interpolation into HTML built with template strings. Every
 *  value that comes from a catalogue (including a live one) goes through
 *  here — a track title is data, never markup. */
export function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
