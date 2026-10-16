/**
 * What this browser can actually do.
 *
 * A web app is not one runtime. It is Chrome on Android, Safari on an iPhone,
 * Firefox on a laptop that just lost Wi-Fi, and a private window where storage
 * is refused. Every one of those differences used to be discovered at the worst
 * possible moment — the first click after the user has committed to a track.
 * This module is the one place that asks, once, and answers in plain booleans so
 * the rest of the app can branch on facts instead of on `try/catch`.
 *
 * Two rules it obeys:
 *
 *  1. **Feature-detect, never user-agent sniff.** A UA string is a claim; the
 *     presence of `navigator.mediaSession` is a fact. Nothing here reads
 *     `navigator.userAgent` to decide behaviour.
 *  2. **A missing capability is reported, not thrown.** Every probe is wrapped,
 *     every field has a `false` default, and `describe()` turns the result into
 *     the sentences the Settings screen shows a visitor.
 */

/** Quietly run a probe: any throw means "not available on this browser". */
function safely(probe, fallback = false) {
  try {
    return probe() ?? fallback;
  } catch {
    return fallback;
  }
}

/**
 * True when `localStorage` can actually be *written*, not merely present.
 *
 * Safari's private mode exposes the object and throws on `setItem`, so testing
 * for existence is exactly the test that fails in the field. The probe writes a
 * namespaced key and removes it again; it is the only safe way to answer.
 */
export function storageWorks(storage = globalThis.localStorage) {
  if (!storage) return false;
  const key = "__dhun_probe__";
  try {
    storage.setItem(key, "1");
    storage.removeItem(key);
    return true;
  } catch {
    return false;
  }
}

/** True when IndexedDB is present *and* a database can be opened. */
export function indexedDbWorks(factory = globalThis.indexedDB) {
  return typeof factory?.open === "function";
}

/**
 * Whether `<audio>` can report decoded duration/metadata across origins.
 *
 * Web Audio cannot analyse or filter media whose bytes are cross-origin and
 * lack `Access-Control-Allow-Origin`. The equaliser therefore either works or
 * is unavailable — it is never silently a no-op, because a slider that moves
 * and changes nothing is a lie told in small print.
 */
export function canProcessAudio() {
  return safely(() => typeof globalThis.AudioContext === "function" ||
    typeof globalThis.webkitAudioContext === "function");
}

/**
 * Network reachability, reported by the browser rather than inferred.
 *
 * `navigator.onLine` is famously optimistic (a captive portal reports online),
 * so it is only ever used for the *offline* answer: false means definitely no
 * network, true means "ask the server". Nothing downstream treats `true` as a
 * guarantee.
 */
export function networkState(nav = globalThis.navigator) {
  const online = typeof nav?.onLine === "boolean" ? nav.onLine : true;
  return {
    online,
    /** Effective connection quality where the browser exposes it, else null. */
    effectiveType: nav?.connection?.effectiveType ?? null,
    saveData: Boolean(nav?.connection?.saveData),
  };
}

/**
 * Everything the app can learn about its host before it renders a pixel.
 *
 * `standalone` distinguishes an installed PWA from a tab, because the two have
 * different problems: an installed app needs an update prompt, a tab needs a
 * URL that keeps working when the user pastes it into a chat.
 */
export function detectPlatform(env = {}) {
  const win = env.window ?? globalThis;
  const nav = env.navigator ?? globalThis.navigator ?? {};
  const matchMedia = win.matchMedia?.bind(win);

  return {
    /** Durable settings and library are possible at all. */
    storage: storageWorks(env.localStorage ?? globalThis.localStorage),
    /** Structured library storage (playlists, history) is possible. */
    indexedDb: indexedDbWorks(env.indexedDB ?? globalThis.indexedDB),
    /** The Web Audio equaliser chain can be built. */
    audioContext: canProcessAudio(),
    /** Media keys / lock-screen controls can be published. */
    mediaSession: typeof nav.mediaSession === "object" && nav.mediaSession !== null,
    /** Pre-caching and offline shell updates are possible. */
    serviceWorker: safely(() => "serviceWorker" in (nav ?? {})),
    /** The tab can ask the browser to keep playing while the screen is off. */
    wakeLock: safely(() => "wakeLock" in nav),
    /** Cross-tab takeover messages are possible. */
    broadcast: safely(() => typeof globalThis.BroadcastChannel === "function"),
    /** The browser offered an install prompt (Chromium-family only). */
    installable: false,
    /** Running as an installed app rather than in a tab. */
    standalone: Boolean(
      matchMedia?.("(display-mode: standalone)")?.matches || nav.standalone === true,
    ),
    /** Hardware/OS asks for less motion; the player honours it for scroll/resize. */
    reduceMotion: Boolean(matchMedia?.("(prefers-reduced-motion: reduce)")?.matches),
    network: networkState(nav),
    /** Reported so the About screen can show the engine the app is running on. */
    language: nav.language ?? null,
  };
}

/**
 * The sentences the Settings screen prints. Written as data, not markup, so the
 * same strings are asserted by tests and shown in the UI without drifting.
 *
 * Only `false` capabilities are listed: a visitor is reading this because
 * something did not work, and a list of what *did* work buries that answer.
 */
export function unavailableCapabilities(platform) {
  const missing = [];
  if (!platform.storage) {
    missing.push({
      id: "storage",
      title: "Settings cannot be saved here",
      body: "This browser is blocking site storage (a private window, or cookies disabled for this site). DHUN still plays, but theme, equaliser and library changes last only for this visit.",
    });
  }
  if (!platform.indexedDb) {
    missing.push({
      id: "indexeddb",
      title: "Library is session-only",
      body: "Playlists, liked songs and history need IndexedDB. It is unavailable here, so they are kept in memory and are forgotten when the tab closes.",
    });
  }
  if (!platform.mediaSession) {
    missing.push({
      id: "mediasession",
      title: "No lock-screen controls",
      body: "This browser has no Media Session API, so playback cannot be controlled from the operating system's media panel or headset buttons.",
    });
  }
  if (!platform.audioContext) {
    missing.push({
      id: "audio",
      title: "Equaliser unavailable",
      body: "The equaliser needs the Web Audio API to filter the stream in real time. This browser does not provide it, so the equaliser is hidden rather than shown as a control that does nothing.",
    });
  }
  if (!platform.serviceWorker) {
    missing.push({
      id: "serviceworker",
      title: "No offline shell",
      body: "Without a service worker the interface itself is not cached, so a reload without a network connection shows the browser's offline error instead of the app.",
    });
  }
  return missing;
}
