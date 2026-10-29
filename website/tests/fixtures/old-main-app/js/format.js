/** Presentation helpers shared by the screens. Nothing here touches the DOM. */

/** "3:07" / "1:02:59", from seconds — the unit the JioSaavn API uses. */
export function formatDuration(seconds) {
  const total = Math.max(0, Math.round(Number(seconds) || 0));
  const secs = total % 60;
  const minutes = Math.floor(total / 60) % 60;
  const hours = Math.floor(total / 3600);
  if (hours > 0) return `${hours}:${String(minutes).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
  return `${minutes}:${String(secs).padStart(2, "0")}`;
}

/** The length of a collection: "5 songs · 18 min". */
export function describeLength(count, seconds) {
  const songs = `${count} ${count === 1 ? "song" : "songs"}`;
  const minutes = Math.round((Number(seconds) || 0) / 60);
  return minutes > 0 ? `${songs} · ${minutes} min` : songs;
}

/** Play counts, rounded the way a music app shows them: 1.2K, 3.4M. */
export function formatCount(value) {
  const count = Number(value) || 0;
  if (count < 1000) return String(count);
  if (count < 1_000_000) return `${(count / 1000).toFixed(count < 10_000 ? 1 : 0).replace(/\.0$/, "")}K`;
  return `${(count / 1_000_000).toFixed(1).replace(/\.0$/, "")}M`;
}

/** The second line under a track title: artist · album, skipping empties. */
export function trackSubtitle(track) {
  return [track?.artist, track?.album?.name].filter(Boolean).join(" · ");
}

/** Pluralise the way the copy does: "1 track" / "3 tracks". */
export function pluralise(count, singular, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

/** "Hindi · 2024" — the meta line an album or a song carries. */
export function metaLine(parts) {
  return parts.filter(Boolean).join(" · ");
}

/** The greeting's name for the hour. `Date` is passed in so this stays pure. */
export function greetingFor(hour) {
  if (hour < 5) return "Still up?";
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  if (hour < 21) return "Good evening";
  return "Good night";
}

/** A percentage for the seek bar, clamped. */
export function percent(position, duration) {
  const total = Number(duration) || 0;
  if (total <= 0) return 0;
  return Math.max(0, Math.min(100, (Number(position) / total) * 100));
}

/** "1.2" for "1.234" — one decimal is enough for a fan count. */
export function oneDecimal(value) {
  return (Math.round((Number(value) || 0) * 10) / 10).toString();
}
