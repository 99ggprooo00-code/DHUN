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

/** TrackRow subtitle: "Artist • Album". */
export function trackSubtitle(track) {
  return [track.artist, track.album].filter(Boolean).join(" • ");
}

/** Pluralise the app's way: "1 song" / "4 songs", "1 track" / "3 tracks". */
export function pluralise(count, singular) {
  return `${count} ${singular}${count === 1 ? "" : "s"}`;
}

