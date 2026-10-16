/**
 * Where the web app keeps things.
 *
 * A native app has a filesystem. A web app has three storage APIs with three
 * different failure modes, and picking the wrong one for a given piece of data
 * is how a web player loses somebody's playlists:
 *
 *   `localStorage`  — synchronous, ~5 MB, string-only. Right for a handful of
 *                     small scalars (theme, accent, volume, source choice).
 *                     Wrong for the library: a few hundred tracks overflow it,
 *                     and an overflow throws mid-write.
 *   `IndexedDB`     — asynchronous, hundreds of MB, structured. Right for the
 *                     library, which grows with use and must survive a reload.
 *   memory          — the honest fallback when both are refused (Safari private
 *                     mode, storage partitioned away, `setItem` throwing).
 *
 * Every write here is failure-tolerant by contract: a browser that cannot
 * remember must still play music. When persistence is unavailable the app says
 * so on Settings instead of pretending the save worked — see
 * `platform.js#unavailableCapabilities`.
 *
 * The library is also **exportable**. A web app that holds a user's playlists
 * inside one origin's IndexedDB owes them a way out, so `exportLibrary()` emits
 * a versioned JSON document and `importLibrary()` accepts one back. That is the
 * web equivalent of a native app's backup file, and it is the only portable
 * artefact the app produces — deliberately not a download-folder of audio.
 */

export const SETTINGS_KEY = "dhun.settings.v2";
export const LIBRARY_DB = "dhun.library";
export const LIBRARY_DB_VERSION = 1;
export const LIBRARY_STORE = "documents";
export const LIBRARY_DOC_ID = "library";
export const EXPORT_FORMAT = "dhun.library.export";
export const EXPORT_VERSION = 2;

/* ------------------------------------------------------------------ memory -- */

/** The always-available store: an object in the tab. Losing it on reload is
 *  the documented cost of a browser that refuses both durable APIs. */
function createMemoryStore() {
  let value = null;
  return {
    kind: "memory",
    async read() {
      return value ? structuredCloneSafe(value) : null;
    },
    async write(next) {
      value = structuredCloneSafe(next);
      return true;
    },
    async clear() {
      value = null;
      return true;
    },
  };
}

/** `structuredClone` is not everywhere; JSON round-trip is enough for the
 *  plain-data documents this module stores and cannot throw on proxies. */
function structuredCloneSafe(value) {
  try {
    return structuredClone(value);
  } catch {
    return JSON.parse(JSON.stringify(value));
  }
}

/* --------------------------------------------------------------- settings -- */

/**
 * Settings: a small, flat, synchronous record.
 *
 * Read once at boot. A malformed or hostile value in `localStorage` (another
 * script on the same origin, a user editing it by hand, a half-written record
 * from a browser that died mid-write) must never take the app down, so every
 * field is validated against `SETTINGS_SCHEMA` and anything unrecognised is
 * dropped back to its default rather than trusted.
 */
export const SETTINGS_SCHEMA = Object.freeze({
  theme: { values: ["dark", "light", "system"], fallback: "dark" },
  accent: { values: ["brand", "azure", "jade", "amber", "rose", "cyan"], fallback: "brand" },
  volume: { number: { min: 0, max: 1 }, fallback: 1 },
  repeat: { values: ["off", "all", "one"], fallback: "off" },
  shuffle: { boolean: true, fallback: false },
  resumeOnLaunch: { boolean: true, fallback: true },
  lyricsEnabled: { boolean: true, fallback: true },
  romanizeLyrics: { boolean: true, fallback: true },
  normalizeVolume: { boolean: true, fallback: false },
  equalizerEnabled: { boolean: true, fallback: false },
  equalizerPreset: { string: true, fallback: "flat" },
  equalizerPreampDb: { number: { min: -20, max: 20 }, fallback: 0 },
  equalizerGains: { gains: true, fallback: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0] },
  /** Where metadata comes from. See `source.js` for what each id means. */
  sourceId: { values: ["auto", "endpoint", "innertube", "demo"], fallback: "auto" },
  /** A user-provided read-only endpoint that maps `source.js`'s request shapes. */
  endpointUrl: { url: true, fallback: "" },
  /** Search history is user data: it is capped, listed and individually erasable. */
  recentSearches: { stringList: 12, fallback: [] },
  /** Set once the visitor has dismissed the install banner. */
  installDismissed: { boolean: true, fallback: false },
  /** Set once the visitor has seen the keyboard-shortcut sheet. */
  shortcutsSeen: { boolean: true, fallback: false },
});

function isUrl(value) {
  if (typeof value !== "string" || value.trim() === "") return false;
  try {
    const url = new URL(value);
    return url.protocol === "https:" || url.protocol === "http:";
  } catch {
    return false;
  }
}

/** Validate one value against its schema entry; returns the fallback on doubt. */
export function coerceSetting(key, value) {
  const rule = SETTINGS_SCHEMA[key];
  if (!rule) return undefined;
  const { fallback } = rule;
  if (rule.values) return rule.values.includes(value) ? value : fallback;
  if (rule.boolean) return typeof value === "boolean" ? value : fallback;
  if (rule.string) return typeof value === "string" ? value : fallback;
  if (rule.url) return isUrl(value) ? value : fallback;
  if (rule.number) {
    if (typeof value !== "number" || !Number.isFinite(value)) return fallback;
    const { min = -Infinity, max = Infinity } = rule.number;
    return Math.min(max, Math.max(min, value));
  }
  if (rule.gains) {
    if (!Array.isArray(value) || value.length === 0) return [...fallback];
    const gains = value.map((gain) =>
      typeof gain === "number" && Number.isFinite(gain)
        ? Math.min(20, Math.max(-20, gain))
        : 0,
    );
    return gains.length === 10 ? gains : [...fallback];
  }
  if (rule.stringList) {
    if (!Array.isArray(value)) return [...fallback];
    return value
      .filter((entry) => typeof entry === "string" && entry.trim() !== "")
      .slice(0, rule.stringList);
  }
  return fallback;
}

export function defaultSettings() {
  const settings = {};
  for (const [key, rule] of Object.entries(SETTINGS_SCHEMA)) {
    settings[key] = Array.isArray(rule.fallback)
      ? structuredCloneSafe(rule.fallback)
      : rule.fallback;
  }
  return settings;
}

/** Every key the settings object is allowed to carry, in one list the tests and
 *  the Settings screen both read. */
export const SETTINGS_KEYS = Object.freeze(Object.keys(SETTINGS_SCHEMA));

export function normaliseSettings(raw) {
  const settings = defaultSettings();
  if (!raw || typeof raw !== "object") return settings;
  for (const key of SETTINGS_KEYS) {
    if (key in raw) settings[key] = coerceSetting(key, raw[key]);
  }
  return settings;
}

export function createSettingsStore(storage = globalThis.localStorage) {
  const available = (() => {
    if (!storage) return false;
    try {
      storage.setItem("__dhun_probe__", "1");
      storage.removeItem("__dhun_probe__");
      return true;
    } catch {
      return false;
    }
  })();

  let cache = available ? read() : defaultSettings();

  function read() {
    try {
      const raw = storage.getItem(SETTINGS_KEY);
      return raw ? normaliseSettings(JSON.parse(raw)) : defaultSettings();
    } catch {
      // Corrupt JSON, or a storage object that throws on read. Either way the
      // app runs on defaults and keeps running.
      return defaultSettings();
    }
  }

  function write(settings) {
    if (!available) return false;
    try {
      storage.setItem(SETTINGS_KEY, JSON.stringify(settings));
      return true;
    } catch {
      // Quota exceeded, or storage revoked between reads. Report, never throw.
      return false;
    }
  }

  return {
    available,
    get all() {
      return { ...cache, equalizerGains: [...cache.equalizerGains], recentSearches: [...cache.recentSearches] };
    },
    get(key) {
      return cache[key];
    },
    /** Patch semantics: unknown keys are ignored, invalid values fall back. */
    set(patch) {
      const next = { ...cache };
      for (const [key, value] of Object.entries(patch)) {
        if (!(key in SETTINGS_SCHEMA)) continue;
        next[key] = coerceSetting(key, value);
      }
      cache = next;
      return write(cache) ? cache : null;
    },
    reset() {
      cache = defaultSettings();
      write(cache);
      return cache;
    },
  };
}

/* ---------------------------------------------------------------- library -- */

/** An empty library. Shape is fixed here so the UI and the export format agree. */
export function emptyLibrary() {
  return {
    /** Supersedes the v1 `favourites` array: a like is a track id plus when. */
    likes: [],
    playlists: [],
    history: [],
    /** Play counts, keyed by track id — what "Listen again" and sorting use. */
    plays: {},
    updatedAt: null,
  };
}

/**
 * Repair a library document from anywhere (IndexedDB, an imported file, a
 * half-migrated older version). A web app receives its own data back through
 * several doors and each one can carry junk; this is the bouncer.
 */
export function normaliseLibrary(raw) {
  const library = emptyLibrary();
  if (!raw || typeof raw !== "object") return library;

  const trackIdOk = (value) => typeof value === "string" && value.length > 0 && value.length <= 64;

  if (Array.isArray(raw.likes)) {
    const seen = new Set();
    for (const entry of raw.likes) {
      const id = typeof entry === "string" ? entry : entry?.id;
      if (!trackIdOk(id) || seen.has(id)) continue;
      seen.add(id);
      library.likes.push({ id, at: typeof entry?.at === "number" ? entry.at : 0 });
    }
  }

  if (Array.isArray(raw.playlists)) {
    for (const entry of raw.playlists) {
      if (!entry || typeof entry !== "object") continue;
      const id = trackIdOk(entry.id) ? entry.id : null;
      const name = typeof entry.name === "string" ? entry.name.trim().slice(0, 120) : "";
      if (!id || !name) continue;
      library.playlists.push({
        id,
        name,
        description: typeof entry.description === "string" ? entry.description.slice(0, 400) : "",
        trackIds: Array.isArray(entry.trackIds)
          ? [...new Set(entry.trackIds.filter(trackIdOk))].slice(0, 2000)
          : [],
        /** A playlist can outlive the metadata of its tracks; the UI says so. */
        createdAt: typeof entry.createdAt === "number" ? entry.createdAt : 0,
        updatedAt: typeof entry.updatedAt === "number" ? entry.updatedAt : 0,
      });
    }
  }

  if (Array.isArray(raw.history)) {
    library.history = raw.history
      .filter((entry) => entry && typeof entry === "object" && trackIdOk(entry.id))
      .slice(0, 200)
      .map((entry) => ({
        id: entry.id,
        at: typeof entry.at === "number" ? entry.at : 0,
        title: typeof entry.title === "string" ? entry.title.slice(0, 200) : "",
        artist: typeof entry.artist === "string" ? entry.artist.slice(0, 200) : "",
        artwork: typeof entry.artwork === "string" ? entry.artwork.slice(0, 600) : "",
        durationMs: typeof entry.durationMs === "number" ? entry.durationMs : 0,
      }));
  }

  if (raw.plays && typeof raw.plays === "object") {
    for (const [id, count] of Object.entries(raw.plays)) {
      if (!trackIdOk(id)) continue;
      if (typeof count !== "number" || !Number.isFinite(count) || count <= 0) continue;
      library.plays[id] = Math.min(Math.floor(count), 100000);
    }
  }

  library.updatedAt = typeof raw.updatedAt === "number" ? raw.updatedAt : null;
  return library;
}

function openDatabase(factory, name, version, store) {
  if (!factory?.open) return Promise.resolve(null);
  return new Promise((resolve) => {
    let request;
    try {
      request = factory.open(name, version);
    } catch {
      resolve(null);
      return;
    }
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(store)) db.createObjectStore(store);
    };
    request.onsuccess = () => resolve(request.result);
    // Blocked means another tab is holding an older version open; error means
    // the origin has no storage. Both degrade to the memory store rather than
    // failing the boot.
    request.onerror = () => resolve(null);
    request.onblocked = () => resolve(null);
  });
}

/**
 * The library store: IndexedDB when it works, memory when it does not.
 *
 * Writes are debounced by the caller (the store's subscriber), not here — this
 * layer stays a plain read/write pair so it can be tested without timers.
 */
export async function createLibraryStore({
  factory = globalThis.indexedDB,
  name = LIBRARY_DB,
  version = LIBRARY_DB_VERSION,
  store = LIBRARY_STORE,
} = {}) {
  const db = await openDatabase(factory, name, version, store);
  if (!db) {
    const memory = createMemoryStore();
    const initial = normaliseLibrary(await memory.read());
    return {
      kind: "memory",
      async read() {
        return normaliseLibrary(await memory.read()) ?? initial;
      },
      async write(document) {
        await memory.write(document);
        return true;
      },
      async clear() {
        await memory.clear();
        return true;
      },
    };
  }

  function transaction(mode, run) {
    return new Promise((resolve) => {
      let tx;
      try {
        tx = db.transaction(store, mode);
      } catch {
        resolve(null);
        return;
      }
      const request = run(tx.objectStore(store));
      request.onsuccess = () => resolve(request.result ?? null);
      request.onerror = () => resolve(null);
      tx.onabort = () => resolve(null);
    });
  }

  return {
    kind: "indexeddb",
    async read() {
      const raw = await transaction("readonly", (objectStore) => objectStore.get(LIBRARY_DOC_ID));
      return raw ? normaliseLibrary(raw) : emptyLibrary();
    },
    async write(document) {
      const result = await transaction("readwrite", (objectStore) =>
        objectStore.put(document, LIBRARY_DOC_ID),
      );
      return result !== null;
    },
    async clear() {
      const result = await transaction("readwrite", (objectStore) =>
        objectStore.delete(LIBRARY_DOC_ID),
      );
      return result !== null;
    },
  };
}

/* ------------------------------------------------------- export / import -- */

/**
 * The portable form of everything the app owns. Versioned, self-describing and
 * free of anything the user did not put there — no device ids, no IP, no
 * timestamps beyond the ones already in the library.
 */
export function exportLibrary(library, { settings = null, now = Date.now() } = {}) {
  const document = {
    format: EXPORT_FORMAT,
    version: EXPORT_VERSION,
    exportedAt: new Date(now).toISOString(),
    library: normaliseLibrary(library),
  };
  // Settings travel only when they are worth carrying: appearance and playback
  // preferences, never the endpoint URL (which may carry a personal token) and
  // never recent searches (which are personal history, not configuration).
  if (settings) {
    document.settings = {
      theme: settings.theme,
      accent: settings.accent,
      volume: settings.volume,
      repeat: settings.repeat,
      shuffle: settings.shuffle,
      lyricsEnabled: settings.lyricsEnabled,
      romanizeLyrics: settings.romanizeLyrics,
      normalizeVolume: settings.normalizeVolume,
      equalizerEnabled: settings.equalizerEnabled,
      equalizerPreset: settings.equalizerPreset,
      equalizerPreampDb: settings.equalizerPreampDb,
      equalizerGains: settings.equalizerGains,
    };
  }
  return document;
}

/**
 * Parse an imported file. Returns a typed result rather than throwing, because
 * the caller is a file input and the user is standing right there: they need a
 * sentence, not a stack trace.
 */
export function importLibrary(text) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { ok: false, reason: "not-json", message: "That file is not JSON." };
  }
  if (!parsed || typeof parsed !== "object" || parsed.format !== EXPORT_FORMAT) {
    return {
      ok: false,
      reason: "not-dhun",
      message: "That file is not a DHUN library export.",
    };
  }
  if (typeof parsed.version !== "number" || parsed.version > EXPORT_VERSION) {
    return {
      ok: false,
      reason: "newer-version",
      message: `That export was written by a newer DHUN (format v${parsed.version}); this build understands up to v${EXPORT_VERSION}.`,
    };
  }
  const library = normaliseLibrary(parsed.library);
  const settings = parsed.settings ? normaliseSettings({ ...defaultSettings(), ...parsed.settings }) : null;
  return {
    ok: true,
    library,
    settings,
    counts: {
      likes: library.likes.length,
      playlists: library.playlists.length,
      history: library.history.length,
    },
  };
}

/* ---------------------------------------------------------------- session -- */

/**
 * The listening session: queue, position and which screen the visitor was on.
 *
 * Kept apart from settings because it changes constantly (every track start) and
 * apart from the library because it is disposable: losing it costs the visitor
 * one click, losing the library costs them their playlists. Both facts drive the
 * design — small, written often, and never allowed to throw.
 */
export const SESSION_KEY = "dhun.session.v1";
export const MAX_SESSION_QUEUE = 200;

export function normaliseSession(raw) {
  if (!raw || typeof raw !== "object") return null;
  const rows = Array.isArray(raw.queue) ? raw.queue : [];
  const queue = rows
    .filter((row) => row && typeof row === "object" && typeof (row.videoId ?? row.id) === "string")
    .slice(0, MAX_SESSION_QUEUE)
    .map((row) => ({
      id: String(row.id ?? row.videoId).slice(0, 64),
      videoId: typeof row.videoId === "string" ? row.videoId.slice(0, 64) : undefined,
      title: typeof row.title === "string" ? row.title.slice(0, 200) : "",
      artist: typeof row.artist === "string" ? row.artist.slice(0, 200) : "",
      album: typeof row.album === "string" ? row.album.slice(0, 200) : "",
      artwork: typeof row.artwork === "string" ? row.artwork.slice(0, 600) : null,
      durationMs: typeof row.durationMs === "number" ? row.durationMs : 0,
      demo: row.demo === true,
    }));
  if (queue.length === 0) return null;
  return {
    queue,
    index: Number.isInteger(raw.index) && raw.index >= 0 && raw.index < queue.length ? raw.index : 0,
    positionMs: typeof raw.positionMs === "number" && raw.positionMs >= 0 ? raw.positionMs : 0,
    updatedAt: typeof raw.updatedAt === "number" ? raw.updatedAt : null,
  };
}

export function readSession(storage = globalThis.localStorage) {
  try {
    const raw = storage?.getItem(SESSION_KEY);
    return raw ? normaliseSession(JSON.parse(raw)) : null;
  } catch {
    return null;
  }
}

export function writeSession(session, storage = globalThis.localStorage) {
  try {
    const normalised = normaliseSession(session);
    if (!normalised) {
      storage?.removeItem(SESSION_KEY);
      return false;
    }
    storage?.setItem(SESSION_KEY, JSON.stringify({ ...normalised, updatedAt: Date.now() }));
    return true;
  } catch {
    // A session that cannot be saved is a session that starts empty next time —
    // annoying, never fatal.
    return false;
  }
}

export function clearSession(storage = globalThis.localStorage) {
  try {
    storage?.removeItem(SESSION_KEY);
    return true;
  } catch {
    return false;
  }
}
