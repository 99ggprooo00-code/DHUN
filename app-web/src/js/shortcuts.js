/**
 * Keyboard control.
 *
 * A web app is used with a keyboard far more often than a phone app is: a
 * laptop, a browser tab that already has focus, someone who never touches the
 * mouse. Space to play, `/` to search, arrows to seek — the conventions people
 * already have in every other player.
 *
 * Two rules make this safe rather than surprising, and both are enforced here
 * rather than at the call site (which is where shortcuts usually get broken):
 *
 *  1. **Never steal a keystroke from a text field.** Space in a search box is a
 *     space. `/` inside a playlist-name input is a slash. The matcher checks
 *     the event target's editability before it looks at the key at all.
 *  2. **Never claim a combination the browser owns.** `Cmd+W`, `Ctrl+T`,
 *     `Cmd+L` are not ours; only the keys listed below are intercepted, and the
 *     handler calls `preventDefault()` exclusively for those.
 *
 * The table is data, so `shortcuts.test.mjs` can pin every binding and the help
 * sheet in the UI is generated from the same source rather than hand-copied.
 */

/** Is this event target a place where the user is typing? */
export function isTextEntryTarget(target) {
  if (!target || typeof target !== "object") return false;
  const tag = String(target.tagName ?? "").toLowerCase();
  if (tag === "textarea" || tag === "select") return true;
  if (tag === "input") {
    const type = String(target.type ?? "text").toLowerCase();
    // Buttons and ranges are not text entry: Space must still toggle playback
    // when the volume slider has focus, which is the difference between a
    // working shortcut and an infuriating one.
    return !["range", "checkbox", "radio", "button", "submit", "reset", "file", "color"].includes(type);
  }
  return Boolean(target.isContentEditable);
}

/** Modifier label for the platform the visitor is on, for the help sheet. */
export function modifierLabel(platform = globalThis.navigator?.platform ?? "") {
  return /mac|iphone|ipad|ipod/i.test(String(platform)) ? "⌘" : "Ctrl";
}

/**
 * The bindings. `keys` are matched against `KeyboardEvent.key` (case-insensitive
 * for letters), `mod` is "primary" for Cmd/Ctrl, `shift` is explicit so `?` and
 * `/` can coexist.
 *
 * `when` narrows a binding to a player state; the dispatcher passes the app's
 * current capabilities in, so a shortcut that cannot act is never swallowed.
 */
export const SHORTCUTS = Object.freeze([
  { id: "play-pause", keys: [" "], label: "Play or pause", group: "Playback", when: "hasTrack" },
  // Two shapes, because two habits exist: Alt+arrow is what the desktop build
  // uses, and Cmd/Ctrl+arrow is what every other player on the machine taught
  // the person sitting in front of it.
  { id: "next", keys: ["ArrowRight"], mod: "alt", primary: true, label: "Next track", group: "Playback", when: "hasTrack" },
  { id: "previous", keys: ["ArrowLeft"], mod: "alt", primary: true, label: "Previous track", group: "Playback", when: "hasTrack" },
  { id: "seek-forward", keys: ["ArrowRight"], label: "Seek forward 5 seconds", group: "Playback", when: "hasTrack" },
  { id: "seek-back", keys: ["ArrowLeft"], label: "Seek back 5 seconds", group: "Playback", when: "hasTrack" },
  { id: "volume-up", keys: ["ArrowUp"], label: "Volume up", group: "Playback" },
  { id: "volume-down", keys: ["ArrowDown"], label: "Volume down", group: "Playback" },
  { id: "mute", keys: ["m"], label: "Mute or unmute", group: "Playback", when: "hasTrack" },
  { id: "shuffle", keys: ["s"], label: "Toggle shuffle", group: "Playback" },
  { id: "repeat", keys: ["r"], label: "Cycle repeat mode", group: "Playback" },
  { id: "like", keys: ["l"], label: "Like the current track", group: "Playback", when: "hasTrack" },
  { id: "search", keys: ["/"], label: "Focus search", group: "Go" },
  { id: "home", keys: ["g", "h"], chord: true, label: "Go home", group: "Go" },
  { id: "library", keys: ["g", "l"], chord: true, label: "Go to library", group: "Go" },
  { id: "close", keys: ["Escape"], label: "Close the player or dialog", group: "Go" },
  { id: "help", keys: ["?"], shift: true, label: "Show this list", group: "Go" },
]);

/** Group the table for the help sheet, preserving declaration order. */
export function shortcutGroups() {
  const groups = new Map();
  for (const shortcut of SHORTCUTS) {
    if (!groups.has(shortcut.group)) groups.set(shortcut.group, []);
    groups.get(shortcut.group).push(shortcut);
  }
  return [...groups.entries()].map(([name, items]) => ({ name, items }));
}

/** Human-readable key cap for the help sheet: "⌘/Ctrl" is spelled out because a
 *  help sheet that only says "Ctrl" is wrong for half the traffic. */
export function shortcutKeys(shortcut, platform) {
  const primaryLabel = `${modifierLabel(platform)}+`;
  const keys = shortcut.keys.map((key) => {
    if (key === " ") return "Space";
    if (key === "ArrowRight") return "→";
    if (key === "ArrowLeft") return "←";
    if (key === "ArrowUp") return "↑";
    if (key === "ArrowDown") return "↓";
    if (key === "Escape") return "Esc";
    return key.length === 1 ? key.toUpperCase() : key;
  });
  if (shortcut.chord) return keys.join(" then ");
  const caps = keys.join("+");
  const shapes = [];
  if (shortcut.mod === "alt" || shortcut.alt) shapes.push(`Alt+${caps}`);
  if (shortcut.mod === "primary" || shortcut.primary) shapes.push(`${primaryLabel}${caps}`);
  return shapes.length ? shapes.join(" / ") : caps;
}

/**
 * Match one key event against the table.
 *
 * `capabilities` is `{ hasTrack }`. A binding whose `when` does not hold is
 * skipped *without* being swallowed, so pressing Space with an empty queue does
 * nothing instead of scrolling the page by accident — and pressing Space with a
 * track playing pauses it.
 *
 * Chords (`g` then `h`) are supported through a tiny two-step buffer held by the
 * caller: this function reports `{ pending: "g" }` and the caller passes that
 * back in as `buffer` on the next keystroke.
 */
export function matchShortcut(event, { capabilities = {}, buffer = null, platform } = {}) {
  if (!event || typeof event.key !== "string") return { action: null, pending: null, swallowed: false };
  // Read the modifiers once, as booleans. A real KeyboardEvent always has them,
  // but a synthesised or partially built event may not, and `undefined` must
  // mean "not held" rather than "unmatched".
  const primary = Boolean(event.metaKey || event.ctrlKey);
  const altKey = Boolean(event.altKey);
  const shiftKey = Boolean(event.shiftKey);
  if (event.ctrlKey && event.metaKey) return { action: null, pending: null, swallowed: false };
  // A text field owns its keys. Releasing the buffer here keeps a half-typed
  // chord from firing after the user clicks into the search box.
  if (isTextEntryTarget(event.target)) return { action: null, pending: null, swallowed: false };

  const key = event.key.length === 1 ? event.key.toLowerCase() : event.key;
  const chordFirst = SHORTCUTS.filter((s) => s.chord).map((s) => s.keys[0]);

  // Second half of a chord.
  if (buffer) {
    const chord = SHORTCUTS.find((s) => s.chord && s.keys[0] === buffer && s.keys[1] === key);
    if (chord && !primary && !altKey) {
      return { action: allowed(chord, capabilities), pending: null, swallowed: Boolean(allowed(chord, capabilities)) };
    }
    return { action: null, pending: null, swallowed: false };
  }

  for (const shortcut of SHORTCUTS) {
    if (shortcut.chord) continue;
    const wantsAlt = Boolean(shortcut.mod === "alt" || shortcut.alt);
    const wantsPrimary = shortcut.mod === "primary";
    const wantsShift = Boolean(shortcut.shift);
    const shapes = [[wantsAlt, wantsPrimary]];
    if (shortcut.primary) shapes.push([false, true]);
    // Shift is only decisive for letters: `S` and `s` arrive as the same key
    // once lower-cased, so the modifier is the only thing separating shuffle
    // from a capital S. For every other key the character already says what it
    // is — and `?` needs shift on a US layout but none on an AZERTY one, so
    // enforcing it here would be a help sheet half the visitors cannot open.
    const shiftDecides = shortcut.keys.some((candidate) => /^[a-zA-Z]$/.test(candidate));
    if (!shapes.some(([wantsAltShape, wantsPrimaryShape]) => altKey === wantsAltShape && primary === wantsPrimaryShape)) continue;
    if (shiftDecides && shiftKey !== wantsShift) continue;
    if (!shortcut.keys.includes(key)) continue;
    const action = allowed(shortcut, capabilities);
    if (!action) return { action: null, pending: null, swallowed: false };
    return { action, pending: null, swallowed: true };
  }

  if (chordFirst.includes(key) && !primary && !altKey && !shiftKey) {
    return { action: null, pending: key, swallowed: true };
  }
  return { action: null, pending: null, swallowed: false };
}

/** A disallowed binding resolves to null so the caller cannot act on it. */
function allowed(shortcut, capabilities) {
  if (shortcut.when === "hasTrack" && !capabilities.hasTrack) return null;
  return shortcut;
}

/** How long a chord stays open. Long enough to feel deliberate, short enough
 *  that a stray `g` does not change what the next keystroke means. */
export const CHORD_TIMEOUT_MS = 1200;
