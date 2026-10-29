import { checkBackupText } from "./backup-policy.js";

/**
 * Everything the browser keeps, and nothing it does not.
 *
 * THE MODEL. There is no account and no DHUN server, so "your library" is this
 * browser's storage: likes, playlists you made, what you played, and the handful
 * of settings the app remembers. `localStorage` is used because the whole
 * library is small — a track snapshot is a few hundred bytes and the cap below
 * is deliberately generous rather than clever. A service-worker cache is not a
 * database and IndexedDB would be a second failure mode for a first feature.
 *
 * THE SNAPSHOT RULE. Likes and history store the *row the visitor saw*, not only
 * an id. A saved id that can only be drawn by asking the network is a library
 * that is empty exactly when the network is. IDs are still the identity; the
 * snapshot is the fallback. Playlists store ids and look rows up in that same
 * snapshot table, which is why a playlist survives a reload with no connection.
 *
 * PRIVATE MODE. Some browsers throw on `localStorage.setItem` (quota) and some
 * expose a storage that forgets immediately (private windows). Both are handled
 * by keeping the state in memory and reporting `persistent: false`, which the
 * Settings screen shows instead of promising a save that did not happen.
 */

export const STORAGE_KEY = "dhun.web.v3";
/** The export file's format. An import reads only this version (see transfer.js). */
export const FORMAT_VERSION = 3;
export const LIBRARY_LIMITS = Object.freeze({
  likes: 2000,
  history: 200,
  playlists: 200,
  playlistTracks: 1000,
  recentSearches: 10,
});

/*
 * There is no limit on track snapshots because none is needed: a snapshot exists
 * only while a like, a history entry or a playlist refers to its id, and
 * `pruneSnapshots` removes the rest on every save and every read. The total is
 * therefore bounded by the limits above.
 */

/**
 * The settings this build can honour.
 *
 * There is deliberately no equaliser. The Android app's bands are a platform
 * audio binding; in a browser the only route to them is a Web Audio graph over
 * cross-origin audio, which can fail silently and mute the product. A control
 * that might silence the app is worse than no control, so the setting is not
 * stored, not offered, and not migrated in.
 */
export const DEFAULT_SETTINGS = Object.freeze({
  theme: "dark", // DhunThemeMode.default = DARK
  accent: "brand",
  volume: 0.8,
  repeat: "off",
  shuffle: false,
});

export const THEMES = Object.freeze(["dark", "light", "system"]);
export const ACCENTS = Object.freeze(["brand", "azure", "jade", "amber", "rose", "cyan"]);

/** A fresh library. Pure, so a test can ask for one without a browser. */
export function emptyLibrary() {
  return {
    likes: [],
    playlists: [],
    history: [],
    snapshots: {},
    recentSearches: [],
    settings: { ...DEFAULT_SETTINGS },
  };
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function clampNumber(value, min, max, fallback) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.min(max, Math.max(min, number));
}

/** A stored song snapshot, in the shape the rows already draw. */
export function snapshot(song) {
  if (!song?.id) return null;
  return {
    id: String(song.id),
    title: String(song.title ?? "Untitled"),
    artist: String(song.artist ?? ""),
    album: { id: String(song.album?.id ?? ""), name: String(song.album?.name ?? "") },
    artists: safeArray(song.artists).map((artist) => ({ id: String(artist.id ?? ""), name: String(artist.name ?? "") })),
    year: String(song.year ?? ""),
    language: String(song.language ?? ""),
    duration: clampNumber(song.duration, 0, 24 * 3600, 0),
    image: String(song.image ?? ""),
    imageSmall: String(song.imageSmall ?? song.image ?? ""),
    // The stream is intentionally not stored: audio URLs expire, and a library
    // row that plays a stale URL is worse than one that says it needs a refresh.
    stream: "",
    streamQuality: "",
    hasLyrics: Boolean(song.hasLyrics),
    explicit: Boolean(song.explicit),
    kind: "song",
  };
}

/**
 * Keep only the track snapshots that a like, a history entry or a playlist still
 * refers to. Without this, unliking a track or letting it age out of history left
 * its row in storage for good.
 */
export function pruneSnapshots(library) {
  const referenced = new Set([
    ...library.likes,
    ...library.history.map((entry) => entry.id),
    ...library.playlists.flatMap((playlist) => playlist.trackIds),
  ]);
  const snapshots = {};
  for (const [id, song] of Object.entries(library.snapshots ?? {})) {
    if (referenced.has(id)) snapshots[id] = song;
  }
  return { ...library, snapshots };
}

/**
 * Read storage back into the current shape, keeping every field that is still
 * meaningful and defaulting the rest.
 *
 * This function is the migration path: the stored object is whatever a previous
 * session wrote, so each field is validated rather than trusted, and an
 * unreadable blob is an empty library instead of a boot failure.
 */
export function normaliseLibrary(raw) {
  const base = emptyLibrary();
  if (!raw || typeof raw !== "object") return base;

  const snapshots = {};
  for (const [id, value] of Object.entries(raw.snapshots && typeof raw.snapshots === "object" ? raw.snapshots : {})) {
    const song = snapshot(value);
    if (song && String(song.id) === String(id)) snapshots[id] = song;
  }

  // Only a string or a number can be an id. `String(null)` is `"null"`, which
  // would otherwise become a like nobody can play and nobody can remove.
  const likes = [
    ...new Set(
      safeArray(raw.likes)
        .filter((entry) => typeof entry === "string" || typeof entry === "number")
        .map(String)
        .filter(Boolean),
    ),
  ].slice(0, LIBRARY_LIMITS.likes);
  const history = safeArray(raw.history)
    .map((entry) => (typeof entry === "string" ? { id: entry, at: 0 } : { id: String(entry?.id ?? ""), at: Number(entry?.at ?? 0) || 0 }))
    .filter((entry) => entry.id)
    .slice(0, LIBRARY_LIMITS.history);

  const playlists = safeArray(raw.playlists)
    .map((playlist) => {
      const id = String(playlist?.id ?? "");
      const name = String(playlist?.name ?? "").trim();
      if (!id || !name) return null;
      return {
        id,
        name: name.slice(0, 120),
        description: String(playlist?.description ?? "").slice(0, 400),
        trackIds: [...new Set(safeArray(playlist?.trackIds).map(String))].filter(Boolean).slice(0, LIBRARY_LIMITS.playlistTracks),
        createdAt: Number(playlist?.createdAt ?? 0) || 0,
        updatedAt: Number(playlist?.updatedAt ?? 0) || 0,
      };
    })
    .filter(Boolean)
    .slice(0, LIBRARY_LIMITS.playlists);

  const settings = {
    ...base.settings,
    ...(raw.settings && typeof raw.settings === "object" ? raw.settings : {}),
  };
  // A library saved by an older build can still carry equaliser keys. They are
  // removed rather than ignored, so an export cannot promise a setting the
  // interface cannot show.
  for (const stale of ["equalizerEnabled", "equalizerGains", "equalizerPreampDb", "equalizerPreset"]) {
    delete settings[stale];
  }
  settings.theme = THEMES.includes(settings.theme) ? settings.theme : DEFAULT_SETTINGS.theme;
  settings.accent = ACCENTS.includes(settings.accent) ? settings.accent : DEFAULT_SETTINGS.accent;
  settings.repeat = ["off", "all", "one"].includes(settings.repeat) ? settings.repeat : "off";
  settings.shuffle = Boolean(settings.shuffle);
  settings.volume = clampNumber(settings.volume, 0, 1, DEFAULT_SETTINGS.volume);
  // Which catalogue instance answered last visit. Free text in storage is not
  // trusted: an id that is not in the current list is dropped, and the app falls
  // back to the first instance rather than asking a host it never shipped.
  settings.sourceId = typeof settings.sourceId === "string" ? settings.sourceId.slice(0, 40) : "";

  const recentSearches = [...new Set(safeArray(raw.recentSearches).map((entry) => String(entry ?? "").trim()))]
    .filter(Boolean)
    .slice(0, LIBRARY_LIMITS.recentSearches);

  return pruneSnapshots({ likes, playlists, history, snapshots, recentSearches, settings });
}

/** Direct validation can also receive objects not produced by JSON.parse.
 * Walk iteratively and reject numbers that serialization would change. */
function rejectUnsafeNumbers(root) {
  function* children(value) {
    if (Array.isArray(value)) { yield* value; return; }
    for (const key in value) if (Object.hasOwn(value, key)) yield value[key];
  }
  const pending = [[root][Symbol.iterator]()];
  const visited = new WeakSet();
  while (pending.length) {
    const next = pending[pending.length - 1].next();
    if (next.done) { pending.pop(); continue; }
    const value = next.value;
    if (typeof value === "number" && (!Number.isFinite(value) || Object.is(value, -0))) {
      throw new Error("the file contains a non-finite or negative-zero number; the current library was not replaced");
    }
    if (value !== null && typeof value === "object" && !visited.has(value)) {
      visited.add(value);
      pending.push(children(value));
    }
  }
}

/** Canonicalise a decimal without converting it through binary floating point. */
function decimalValue(token) {
  // Legacy string fields may use an explicit plus, leading zeroes, `.5` or
  // `1.` even though those spellings are not JSON number tokens.
  const match = /^([+-]?)(?:(\d+)(?:\.(\d*))?|\.(\d+))(?:[eE]([+-]?\d+))?$/.exec(token);
  if (!match) return null;
  const negative = match[1] === "-";
  const fraction = match[3] ?? match[4] ?? "";
  let digits = ((match[2] ?? "0") + fraction).replace(/^0+/, "");
  if (!digits) return { negative, digits: "0", exponent: 0 };
  let exponent = Number(match[5] ?? 0) - fraction.length;
  if (!Number.isSafeInteger(exponent)) return null;
  let end = digits.length;
  while (end > 1 && digits[end - 1] === "0") end -= 1;
  if (end !== digits.length) {
    exponent += digits.length - end;
    digits = digits.slice(0, end);
  }
  if (!Number.isSafeInteger(exponent)) return null;
  return { negative, digits, exponent };
}

function sameDecimalValue(left, right) {
  const a = decimalValue(left);
  const b = decimalValue(right);
  return Boolean(a && b && a.negative === b.negative && a.digits === b.digits && a.exponent === b.exponent);
}

/**
 * JSON.parse converts numeric tokens to IEEE-754 before validation. Reject any
 * token whose decimal value differs from the Number's JSON representation, including
 * overflow, precision loss, underflow and negative zero. JSON syntax is checked
 * first, so this scanner only needs to skip strings and inspect number tokens.
 */
function assertLosslessJSONNumbers(text) {
  const numberToken = /-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/y;
  for (let i = 0; i < text.length; i += 1) {
    if (text[i] === '"') {
      i += 1;
      while (i < text.length) {
        if (text[i] === "\\") { i += 2; continue; }
        if (text[i] === '"') break;
        i += 1;
      }
      continue;
    }
    if (text[i] !== "-" && (text[i] < "0" || text[i] > "9")) continue;
    numberToken.lastIndex = i;
    const match = numberToken.exec(text);
    if (!match) continue;
    const token = match[0];
    const number = Number(token);
    if (!Number.isFinite(number) || !sameDecimalValue(token, String(number))) {
      throw new Error("the file contains a number JavaScript cannot preserve exactly; no library was replaced");
    }
    i += token.length - 1;
  }
}

/** Parse valid JSON while rejecting duplicate object keys (including escapes).
 * JSON.parse alone silently keeps the last occurrence. The iterative scan runs
 * only after syntax validation and does not recurse on an untrusted document. */
export function parseImportJSON(text) {
  checkBackupText(text);
  let parsed;
  try { parsed = JSON.parse(text); } catch { throw new Error("the file is not JSON"); }
  assertLosslessJSONNumbers(text);
  const stack = [];
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i];
    if (char === '"') {
      const start = i;
      i += 1;
      while (i < text.length) {
        if (text[i] === "\\") { i += 2; continue; }
        if (text[i] === '"') break;
        i += 1;
      }
      let next = i + 1;
      while (/\s/.test(text[next] ?? "") && next < text.length) next += 1;
      if (text[next] === ":") {
        const keys = stack[stack.length - 1];
        const key = JSON.parse(text.slice(start, i + 1));
        if (keys.has(key)) throw new Error("the file contains a duplicate JSON object key; no library was replaced");
        keys.add(key);
      }
    } else if (char === "{") stack.push(new Set());
    else if (char === "[") stack.push(null);
    else if (char === "}" || char === "]") stack.pop();
  }
  return parsed;
}

/**
 * Import is a replacement, not startup recovery. Reject anything that the
 * permissive storage reader would silently drop or change. Missing optional
 * snapshot fields and settings receive the same defaults as older v3 exports.
 */
export function validateImport(raw) {
  rejectUnsafeNumbers(raw);
  const object = (value) => value !== null && typeof value === "object" && !Array.isArray(value);
  const invalid = (path, why) => { throw new Error(`Invalid library: ${path} ${why}. The current library was not replaced.`); };
  if (!object(raw) || raw.app !== "DHUN web") invalid("file", "is not a DHUN web export");
  if (raw.version !== FORMAT_VERSION) invalid("version", `must be ${FORMAT_VERSION}`);
  const fields = ["likes", "playlists", "history", "snapshots", "recentSearches", "settings"];
  for (const key of Object.keys(raw)) {
    if (![...fields, "app", "version", "exportedAt"].includes(key)) invalid(key, "is an unsupported field");
  }
  for (const key of ["likes", "playlists", "history", "recentSearches"]) {
    if (!Array.isArray(raw[key])) invalid(key, "must be an array");
    if (raw[key].length > LIBRARY_LIMITS[key]) invalid(key, `exceeds the limit of ${LIBRARY_LIMITS[key]}`);
  }
  if (!object(raw.snapshots)) invalid("snapshots", "must be an object");
  if (!object(raw.settings)) invalid("settings", "must be an object");
  if (raw.exportedAt !== undefined && typeof raw.exportedAt !== "string") invalid("exportedAt", "must be text");

  const id = (value, path) => {
    if (!(typeof value === "string" || (typeof value === "number" && Number.isFinite(value))) || !String(value)) {
      invalid(path, "must be a nonempty string or number");
    }
    return String(value);
  };
  const unique = (values, path) => {
    const seen = new Set();
    for (const value of values) {
      if (seen.has(value)) invalid(path, "contains a duplicate entry");
      seen.add(value);
    }
  };
  unique(raw.likes.map((v) => id(v, "likes")), "likes");
  const playlistIds = [];
  for (const playlist of raw.playlists) {
    if (!object(playlist)) invalid("playlist", "must be an object");
    playlistIds.push(id(playlist.id, "playlist id"));
    if (typeof playlist.name !== "string" || !playlist.name.trim()) invalid("playlist name", "must be nonempty text");
    if (!Array.isArray(playlist.trackIds)) invalid("playlist trackIds", "must be an array");
    if (playlist.trackIds.length > LIBRARY_LIMITS.playlistTracks) invalid("playlist trackIds", `exceeds the limit of ${LIBRARY_LIMITS.playlistTracks}`);
    unique(playlist.trackIds.map((v) => id(v, "playlist track id")), "playlist trackIds");
  }
  unique(playlistIds, "playlist ids");
  for (const entry of raw.history) {
    if (typeof entry === "string") id(entry, "history id");
    else if (object(entry)) id(entry.id, "history id");
    else invalid("history entry", "must contain an id");
  }
  for (const [key, value] of Object.entries(raw.snapshots)) {
    if (!object(value) || id(value.id, "snapshot id") !== key) invalid("snapshot", "must have an id matching its key");
  }
  for (const value of raw.recentSearches) {
    if (typeof value !== "string" || !value.trim()) invalid("recentSearches", "must contain nonempty text");
  }

  let normalized;
  try { normalized = normaliseLibrary(raw); }
  catch { invalid("file", "has an invalid nested structure"); }

  // Compare every supplied value, including unknown fields. Extra defaults are
  // fine; removed fields, clamping, truncation and duplicate collapsing are not.
  // Legacy numeric IDs/numeric strings and whitespace trimming are lossless
  // representations accepted by the existing reader.
  function compare(input, output, path) {
    if (input === output) return;
    if (typeof input === "number" && typeof output === "string" && String(input) === output) return;
    if (typeof input === "string" && typeof output === "number" && input.trim() && Number.isFinite(Number(input)) && sameDecimalValue(input.trim(), String(output))) return;
    if (typeof input === "string" && typeof output === "string" && input.trim() === output) return;
    if (Array.isArray(input) && Array.isArray(output)) {
      if (input.length !== output.length) invalid(path, "would lose entries during import");
      input.forEach((value, i) => compare(value, output[i], `${path}[${i}]`));
      return;
    }
    if (object(input) && object(output)) {
      for (const key of Object.keys(input)) {
        if (!Object.hasOwn(output, key)) invalid(`${path}.${key}`, "would be discarded or is unsupported");
        compare(input[key], output[key], `${path}.${key}`);
      }
      return;
    }
    invalid(path, "would change during import");
  }
  for (const field of fields) {
    if (field === "history") {
      raw.history.forEach((entry, i) => {
        if (typeof entry === "string") compare(entry, normalized.history[i]?.id, `history[${i}]`);
        else compare(entry, normalized.history[i], `history[${i}]`);
      });
    } else compare(raw[field], normalized[field], field);
  }
  return normalized;
}

/* ------------------------------------------------------------- operations -- */

/** Add or remove a like. Returns the new state and which way it went. */
export function toggleLike(library, song) {
  const id = String(song?.id ?? "");
  if (!id) return { library, liked: false, changed: false };
  const liked = !library.likes.includes(id);
  if (liked && library.likes.length >= LIBRARY_LIMITS.likes) return { library, liked: false, changed: false, reason: "limit" };
  const likes = liked ? [id, ...library.likes] : library.likes.filter((entry) => entry !== id);
  const snapshots = liked && song ? { ...library.snapshots, [id]: snapshot(song) ?? library.snapshots[id] } : library.snapshots;
  return { library: { ...library, likes, snapshots }, liked, changed: true };
}

/** Newest first, de-duplicated: playing a song twice is one history entry. */
export function rememberPlay(library, song, now = Date.now()) {
  const id = String(song?.id ?? "");
  if (!id) return library;
  const history = [{ id, at: now }, ...library.history.filter((entry) => entry.id !== id)].slice(0, LIBRARY_LIMITS.history);
  return { ...library, history, snapshots: { ...library.snapshots, [id]: snapshot(song) ?? library.snapshots[id] } };
}

export function rememberSearch(library, query) {
  const trimmed = String(query ?? "").trim();
  if (!trimmed) return library;
  const recentSearches = [trimmed, ...library.recentSearches.filter((entry) => entry !== trimmed)].slice(0, LIBRARY_LIMITS.recentSearches);
  return { ...library, recentSearches };
}

export function newPlaylistId(now = Date.now(), random = Math.random) {
  return `pl-${Math.floor(now).toString(36)}-${random().toString(36).slice(2, 6)}`;
}

export function createPlaylist(library, name, now = Date.now()) {
  const trimmed = String(name ?? "").trim();
  if (!trimmed) return { library, playlist: null, reason: "name" };
  if (library.playlists.length >= LIBRARY_LIMITS.playlists) return { library, playlist: null, reason: "limit" };
  const playlist = {
    id: newPlaylistId(now),
    name: trimmed.slice(0, 120),
    description: "",
    trackIds: [],
    createdAt: now,
    updatedAt: now,
  };
  return { library: { ...library, playlists: [playlist, ...library.playlists] }, playlist };
}

export function renamePlaylist(library, id, name, now = Date.now()) {
  const trimmed = String(name ?? "").trim();
  if (!trimmed) return library;
  return {
    ...library,
    playlists: library.playlists.map((playlist) =>
      playlist.id === id ? { ...playlist, name: trimmed.slice(0, 120), updatedAt: now } : playlist,
    ),
  };
}

export function deletePlaylist(library, id) {
  return { ...library, playlists: library.playlists.filter((playlist) => playlist.id !== id) };
}

/** Add a track to a playlist. A second add is a no-op, not a duplicate row. */
export function addToPlaylist(library, playlistId, song, now = Date.now()) {
  const id = String(song?.id ?? "");
  if (!id) return { library, added: false, reason: "invalid" };
  const playlist = library.playlists.find((entry) => entry.id === playlistId);
  if (!playlist) return { library, added: false, reason: "missing" };
  if (playlist.trackIds.includes(id)) return { library, added: false, reason: "duplicate" };
  if (playlist.trackIds.length >= LIBRARY_LIMITS.playlistTracks) return { library, added: false, reason: "limit" };
  const playlists = library.playlists.map((entry) => entry.id === playlistId
    ? { ...entry, trackIds: [...entry.trackIds, id], updatedAt: now }
    : entry);
  const snapshots = { ...library.snapshots, [id]: snapshot(song) ?? library.snapshots[id] };
  return { library: { ...library, playlists, snapshots }, added: true };
}

export function removeFromPlaylist(library, playlistId, songId, now = Date.now()) {
  return {
    ...library,
    playlists: library.playlists.map((playlist) =>
      playlist.id === playlistId
        ? { ...playlist, trackIds: playlist.trackIds.filter((id) => id !== String(songId)), updatedAt: now }
        : playlist,
    ),
  };
}

/** The rows a saved playlist draws, in the order it saved them. */
export function playlistSongs(library, playlist) {
  if (!playlist) return [];
  return playlist.trackIds.map((id) => library.snapshots[id]).filter(Boolean);
}

export function likedSongs(library) {
  return library.likes.map((id) => library.snapshots[id]).filter(Boolean);
}

export function historySongs(library) {
  return library.history.map((entry) => library.snapshots[entry.id]).filter(Boolean);
}

/* ---------------------------------------------------------------- storage -- */

/**
 * A storage handle with an in-memory mirror.
 *
 * `persistent` is the honest answer to "did that save?". A private window can
 * accept a `setItem` and forget it, so the flag is set by a write-then-read
 * probe rather than by the existence of the API.
 */
export function createStorage(backing, key = STORAGE_KEY) {
  // Accessing the property itself can throw under browser storage policies.
  // A failed write probe is not a reason to discard still-readable saved data.
  if (backing === undefined) {
    try { backing = globalThis.localStorage; } catch { backing = null; }
  }
  let memory = null;
  let persistent = false;
  let observed = null;
  let observedKnown = false;
  let importGuard = null;

  function probe() {
    if (!backing) return false;
    try {
      backing.setItem(`${key}.probe`, "1");
      const value = backing.getItem(`${key}.probe`);
      backing.removeItem(`${key}.probe`);
      return value === "1";
    } catch {
      return false;
    }
  }

  persistent = probe();

  return {
    get persistent() {
      return persistent;
    },
    get importGuard() {
      return importGuard;
    },
    load() {
      if (memory) return memory;
      try {
        const raw = backing?.getItem(key) ?? null;
        memory = normaliseLibrary(JSON.parse(raw ?? "null"));
        if (backing) { observed = raw; observedKnown = true; }
      } catch {
        memory = emptyLibrary();
      }
      return memory;
    },
    save(library, { requirePersistence = false } = {}) {
      const kept = pruneSnapshots(library);
      // Ordinary edits may remain useful for this visit. Destructive actions
      // and replacement imports publish new memory only after acknowledgement.
      if (!requirePersistence) memory = kept;
      // After an uncertain import, never overwrite a possibly newer saved copy
      // with this tab's stale memory. Ordinary edits remain visit-only.
      if (importGuard) { persistent = false; return false; }
      try {
        const serialized = JSON.stringify(kept);
        backing?.setItem(key, serialized);
        persistent = Boolean(backing) && backing.getItem(key) === serialized;
        if (persistent) { observed = serialized; observedKnown = true; }
      } catch {
        persistent = false;
      }
      // Retry on the next save even after quota/permission failure. An erase
      // may free space, and storage policy can change while this tab is open.
      if (persistent) memory = kept;
      return persistent;
    },
    /** A file the visitor can keep; the app never uploads it. */
    exportAll(library) {
      return JSON.stringify({ app: "DHUN web", version: FORMAT_VERSION, exportedAt: new Date().toISOString(), ...library }, null, 2);
    },
    importAll(text) {
      if (importGuard) {
        throw new Error("The saved library needs review; this tab will not write again. Export the in-tab library and keep the original backup, then open a fresh tab to review saved data.");
      }
      const parsed = parseImportJSON(text);
      const library = validateImport(parsed);
      // Remember the copy this tab last observed; reject an already stale tab
      // before replacement. localStorage has no compare-and-swap, so another
      // tab can still write between this check and setItem.
      this.load();
      let previous;
      try {
        if (!backing || !observedKnown) throw new Error("storage unavailable");
        previous = backing.getItem(key);
      } catch {
        throw new Error("This browser could not read the current saved library, so nothing was written. Allow storage and try again; keep your backup file.");
      }
      if (previous !== observed) {
        importGuard = "conflict";
        persistent = false;
        throw new Error("The saved library changed since this tab loaded; nothing was written. This tab will not write again. Export this tab's library and keep your backup, then open a fresh tab to review saved data.");
      }
      // Complete serialization before the first write.
      const kept = pruneSnapshots(library);
      const serialized = JSON.stringify(kept);
      const unconfirmed = () => {
        importGuard = "unconfirmed";
        persistent = false;
        const error = new Error("The saved library may have changed, but the import could not be confirmed. This tab will not write again. Export the in-tab library, keep the original backup, and open a fresh tab to review saved data.");
        error.code = "IMPORT_UNCERTAIN";
        return error;
      };
      try {
        backing.setItem(key, serialized);
      } catch {
        // A conforming quota failure leaves the old key intact. Read it before
        // saying so: unusual adapters can mutate and then throw.
        let unchanged = false;
        try { unchanged = backing.getItem(key) === previous; } catch { /* outcome unknown */ }
        if (unchanged) {
          persistent = false;
          throw new Error("This browser could not confirm saving the imported library; the saved library was unchanged. Keep the backup and allow storage or free space before retrying.");
        }
        throw unconfirmed();
      }
      let confirmed;
      try { confirmed = backing.getItem(key) === serialized; }
      catch { throw unconfirmed(); }
      if (!confirmed) throw unconfirmed();
      persistent = true;
      observed = serialized; observedKnown = true;
      memory = kept;
      return kept;
    },
  };
}
