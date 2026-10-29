/**
 * Keyboard control.
 *
 * A web app is used with a keyboard far more often than a phone app is, and the
 * conventions people already have in a music player are worth keeping: Space to
 * play, `/` to search, arrows to seek, `m` to mute.
 *
 * Two rules make that safe rather than surprising, and both live here rather
 * than at the call site, which is where shortcuts usually get broken:
 *
 *   1. **Never steal a keystroke from a text field.** Space in a search box is a
 *      space; `/` inside a playlist name is a slash. The matcher checks the
 *      target's editability before it looks at the key.
 *   2. **Never claim a combination the browser owns.** `Ctrl+W`, `Cmd+T`,
 *      `Cmd+L` are not ours. A keystroke with a modifier returns nothing.
 *
 * The table below is data, so the help sheet and the tests read the same source
 * rather than a hand-copy that drifts.
 */

/** Is this the place a person is typing? */
export function isTextEntryTarget(target) {
  if (!target || typeof target !== "object") return false;
  const tag = String(target.tagName ?? "").toLowerCase();
  if (tag === "textarea" || tag === "select") return true;
  if (tag === "input") {
    const type = String(target.type ?? "text").toLowerCase();
    // A range slider is not text entry: Space must still toggle playback when
    // the volume slider has focus. Same for a checkbox and a button.
    return !["range", "checkbox", "radio", "button", "submit", "reset", "file", "color"].includes(type);
  }
  return Boolean(target.isContentEditable);
}

export const SHORTCUTS = Object.freeze([
  { id: "play-pause", key: " ", label: "Play or pause", group: "Playback" },
  { id: "seek-forward", key: "ArrowRight", label: "Seek forward 5 seconds", group: "Playback" },
  { id: "seek-back", key: "ArrowLeft", label: "Seek back 5 seconds", group: "Playback" },
  { id: "next", key: "n", label: "Next track", group: "Playback" },
  { id: "previous", key: "p", label: "Previous track", group: "Playback" },
  { id: "volume-up", key: "ArrowUp", label: "Volume up", group: "Playback" },
  { id: "volume-down", key: "ArrowDown", label: "Volume down", group: "Playback" },
  { id: "mute", key: "m", label: "Mute or unmute", group: "Playback" },
  { id: "shuffle", key: "s", label: "Shuffle", group: "Playback" },
  { id: "repeat", key: "r", label: "Repeat", group: "Playback" },
  { id: "search", key: "/", label: "Focus search", group: "Navigation" },
  { id: "escape", key: "Escape", label: "Close a sheet or the player", group: "Navigation" },
  { id: "help", key: "?", label: "Show this list", group: "Navigation" },
]);

const BY_KEY = new Map(SHORTCUTS.map((shortcut) => [shortcut.key, shortcut]));

/**
 * Match one keydown.
 *
 * Returns `{ action, preventDefault }` or `null`. `preventDefault` is true only
 * for the keys this app actually acts on — a shortcut that cannot act does not
 * swallow the key.
 */
export function matchShortcut(event, { inDialog = false } = {}) {
  if (!event || typeof event.key !== "string") return null;
  // Candidate editing/cancellation belongs to the IME, not global app shortcuts.
  if (event.isComposing || event.keyCode === 229) return null;
  // Outside composition, Escape is the way out of a dialog, including from a field.
  if (event.key === "Escape") {
    if (event.ctrlKey || event.metaKey || event.altKey) return null;
    return { action: "escape", preventDefault: false };
  }
  // Inside a dialog only Escape is ours: Tab is the shell's trap, and every
  // other key belongs to the dialog's own controls.
  if (inDialog) return null;
  if (event.ctrlKey || event.metaKey || event.altKey) return null;
  if (isTextEntryTarget(event.target)) return null;
  const key = event.key.length === 1 ? event.key.toLowerCase() : event.key;
  const shortcut = BY_KEY.get(key);
  if (!shortcut) return null;
  return { action: shortcut.id, preventDefault: true };
}

/** The help sheet's rows, grouped, from the same table. */
export function shortcutGroups() {
  const groups = new Map();
  for (const shortcut of SHORTCUTS) {
    if (!groups.has(shortcut.group)) groups.set(shortcut.group, []);
    groups.get(shortcut.group).push(shortcut);
  }
  return [...groups.entries()].map(([group, shortcuts]) => ({ group, shortcuts }));
}
