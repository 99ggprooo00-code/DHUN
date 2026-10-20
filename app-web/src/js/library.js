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
export const LIBRARY_LIMITS = Object.freeze({
  likes: 2000,
  history: 200,
  playlists: 200,
  playlistTracks: 1000,
  recentSearches: 10,
  snapshots: 2500,
});

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

  const recentSearches = [...new Set(safeArray(raw.recentSearches).map((entry) => String(entry ?? "").trim()))]
    .filter(Boolean)
    .slice(0, LIBRARY_LIMITS.recentSearches);

  return { likes, playlists, history, snapshots, recentSearches, settings };
}

/* ------------------------------------------------------------- operations -- */

/** Add or remove a like. Returns the new state and which way it went. */
export function toggleLike(library, song) {
  const id = String(song?.id ?? "");
  if (!id) return { library, liked: false, changed: false };
  const liked = !library.likes.includes(id);
  const likes = liked ? [id, ...library.likes].slice(0, LIBRARY_LIMITS.likes) : library.likes.filter((entry) => entry !== id);
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
  if (!trimmed) return { library, playlist: null };
  const playlist = {
    id: newPlaylistId(now),
    name: trimmed.slice(0, 120),
    description: "",
    trackIds: [],
    createdAt: now,
    updatedAt: now,
  };
  return { library: { ...library, playlists: [playlist, ...library.playlists].slice(0, LIBRARY_LIMITS.playlists) }, playlist };
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
  if (!id) return { library, added: false };
  let added = false;
  const playlists = library.playlists.map((playlist) => {
    if (playlist.id !== playlistId || playlist.trackIds.includes(id)) return playlist;
    added = true;
    return {
      ...playlist,
      trackIds: [...playlist.trackIds, id].slice(0, LIBRARY_LIMITS.playlistTracks),
      updatedAt: now,
    };
  });
  const snapshots = added ? { ...library.snapshots, [id]: snapshot(song) ?? library.snapshots[id] } : library.snapshots;
  return { library: { ...library, playlists, snapshots }, added };
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
export function createStorage(backing = globalThis.localStorage, key = STORAGE_KEY) {
  let memory = null;
  let persistent = false;

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
    load() {
      if (memory) return memory;
      if (!persistent) return emptyLibrary();
      try {
        memory = normaliseLibrary(JSON.parse(backing.getItem(key) ?? "null"));
      } catch {
        memory = emptyLibrary();
      }
      return memory;
    },
    save(library) {
      memory = library;
      if (!persistent) return false;
      try {
        backing.setItem(key, JSON.stringify(library));
        return true;
      } catch {
        // Quota: keep working in memory and stop claiming persistence.
        persistent = false;
        return false;
      }
    },
    /** A file the visitor can keep; the app never uploads it. */
    exportAll(library) {
      return JSON.stringify({ app: "DHUN web", version: 3, exportedAt: new Date().toISOString(), ...library }, null, 2);
    },
    importAll(text) {
      const parsed = JSON.parse(text);
      const library = normaliseLibrary(parsed);
      memory = library;
      this.save(library);
      return library;
    },
  };
}
