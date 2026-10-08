/**
 * The one observable state holder.
 *
 * Mirror of the app's Compose/Snapshot state pattern: screens read state and
 * re-render, they never mutate each other. Named after nothing in particular —
 * the app has one `DhunAppearance` object and per-screen ViewModels; this is
 * the web equivalent of "one holder, subscribers re-render".
 */

/** Minimal key/value persistence. localStorage throws in private modes and
 *  when the origin has no storage access at all — a browser that cannot
 *  remember settings must still play music. */
const STORAGE_KEY = "dhun.web.state.v1";

export function readPersisted() {
  try {
    const raw = globalThis.localStorage?.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function writePersisted(value) {
  try {
    globalThis.localStorage?.setItem(STORAGE_KEY, JSON.stringify(value));
  } catch {
    /* storage unavailable — settings stay session-only, which is a
       limitation, not a failure. */
  }
}

export function createStore(initialState) {
  let state = initialState;
  const listeners = new Set();

  return {
    get() {
      return state;
    },
    /** Shallow-merge `patch`; listeners run once per `set`, not per key. */
    set(patch) {
      const next = typeof patch === "function" ? patch(state) : patch;
      if (next === state) return state;
      state = { ...state, ...next };
      for (const listener of listeners) listener(state);
      return state;
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}

/** Everything the web app persists between visits.
 *
 *  Mirror of the app's own persistence surface: `NowPlayingPersistence`
 *  (queue + position), `SettingsViewModel` (theme, accent, cache budget,
 *  resume-on-launch) and the library (playlists, favourites, history).
 *  Downloads are absent on purpose — see the B3 table in the ADR amendment. */
export function persistedSlice(state) {
  return {
    theme: state.theme,
    accent: state.accent,
    cacheSizeMb: state.cacheSizeMb,
    resumeOnLaunch: state.resumeOnLaunch,
    equalizer: state.equalizer,
    queue: state.queue,
    queueIndex: state.queueIndex,
    positionMs: state.positionMs,
    favourites: state.favourites,
    playlists: state.playlists,
    history: state.history,
    recentSearches: state.recentSearches,
  };
}
