/**
 * URLs that mean something.
 *
 * The single largest difference between "the app in a window" and "a web app":
 * on the web, every screen has to be a *link*. A visitor reloads on purpose, a
 * phone suspends a background tab and evicts it, a chat message pastes the
 * current URL to a friend, and the Back button exists. Any of those breaks an
 * app whose only state lives in a JavaScript object, so every screen here has a
 * route, and the route is the source of truth — the view renders *from* it
 * rather than pushing to it as an afterthought.
 *
 * Hash routing (`#/album/al-02`) is a deliberate choice over the History API:
 * this build is served as static files from a GitHub Pages project sub-path
 * where arbitrary deep paths cannot be rewritten to `index.html`. A hash keeps
 * every deep link shareable, reloadable and Back-able without a server rule.
 * The cost — a `#` in the address bar — is stated in the About screen rather
 * than hidden.
 *
 * Routes are data. `parseRoute`/`formatRoute` are pure functions with no window
 * access, so the whole URL surface is pinned by tests that run in Node.
 */

export const ROUTE_NAMES = Object.freeze([
  "home",
  "search",
  "library",
  "playlist",
  "artist",
  "album",
  "settings",
  "about-legal",
]);

/** Library sub-tabs. `downloads` is deliberately absent: a browser cannot
 *  promise an offline file, so the app does not offer one — see `docs/`. */
export const LIBRARY_TABS = Object.freeze(["playlists", "liked", "history"]);

/** Overlay tabs inside the expanded player. */
export const PLAYER_TABS = Object.freeze(["queue", "lyrics"]);

export const DEFAULT_ROUTE = Object.freeze({ name: "home", player: null, playerTab: "queue" });

/** The routes the previous build advertised, kept working so an old link still
 *  lands somewhere real. `downloads` was a screen this app no longer has. */
const LEGACY_HASHES = Object.freeze({
  "#/": { name: "home" },
  "#/home": { name: "home" },
  "#/search": { name: "search" },
  "#/player": { name: "home", player: "open" },
  "#/player/queue": { name: "home", player: "open", playerTab: "queue" },
  "#/player/lyrics": { name: "home", player: "open", playerTab: "lyrics" },
  "#/settings": { name: "settings" },
  "#/about": { name: "about-legal" },
  "#/about-legal": { name: "about-legal" },
  "#/library": { name: "library", libTab: "playlists" },
  "#/library/playlists": { name: "library", libTab: "playlists" },
  "#/library/downloads": { name: "library", libTab: "playlists", redirectedFrom: "downloads" },
  "#/library/history": { name: "library", libTab: "history" },
});

function decode(segment) {
  try {
    return decodeURIComponent(segment);
  } catch {
    return segment;
  }
}

/**
 * Hash → route object. Never throws: an unparseable hash is the home route,
 * because a broken URL should still show a working app.
 *
 * @returns {{name: string, id?: string, query?: string, libTab?: string,
 *            doc?: string, player: string|null, playerTab: string,
 *            redirectedFrom?: string}}
 */
export function parseRoute(hash, { defaultTab = "playlists" } = {}) {
  const raw = typeof hash === "string" ? hash : "";
  const withoutHash = raw.startsWith("#") ? raw.slice(1) : raw;
  const [pathPart, queryPart = ""] = withoutHash.split("?");
  const params = new URLSearchParams(queryPart);

  const playerTab = PLAYER_TABS.includes(params.get("ptab")) ? params.get("ptab") : "queue";
  const base = { player: null, playerTab };

  const path = pathPart.replace(/^\/+/, "").replace(/\/+$/, "");
  const segments = path === "" ? [] : path.split("/").map(decode);

  // Legacy exact matches first: they carry meaning the generic parser would
  // lose (an old `#/player/lyrics` link must open the player on the lyrics tab).
  // The table is consulted for the whole path, not just one segment — a two
  // segment legacy path such as `#/player/queue` is exactly the case that
  // matters. It cannot shadow a real screen because no route family is named
  // `player`; `router.test.mjs` pins that.
  const legacy = LEGACY_HASHES[`#/${path}`];
  if (legacy && !params.has("q")) {
    const route = { ...base, ...legacy };
    if (params.get("player") === "lyrics" || params.get("player") === "queue") {
      route.player = "open";
      route.playerTab = params.get("player");
    } else if (legacy.player) {
      route.player = legacy.player;
    }
    return finalise(route, params, defaultTab);
  }

  let route;
  switch (segments[0]) {
    case undefined:
    case "":
      route = { ...base, name: "home" };
      break;
    case "search":
      route = { ...base, name: "search", query: params.get("q") ?? "" };
      break;
    case "library":
      route = {
        ...base,
        name: "library",
        libTab: LIBRARY_TABS.includes(segments[1]) ? segments[1] : defaultTab,
        // Only present on the URL that used to mean Downloads. A route that
        // carries `redirectedFrom: undefined` is a route that deep-equals
        // nothing, which is a bug in every consumer that compares routes.
        ...(segments[1] === "downloads" ? { redirectedFrom: "downloads" } : {}),
      };
      break;
    case "playlist":
      route = segments[1] ? { ...base, name: "playlist", id: segments[1] } : { ...base, name: "home" };
      break;
    case "artist":
      route = segments[1] ? { ...base, name: "artist", id: segments[1] } : { ...base, name: "home" };
      break;
    case "album":
      route = segments[1] ? { ...base, name: "album", id: segments[1] } : { ...base, name: "home" };
      break;
    case "settings":
      route = { ...base, name: "settings" };
      break;
    case "legal":
    case "about-legal":
      route = { ...base, name: "about-legal", doc: segments[1] ? decode(segments[1]) : undefined };
      break;
    default:
      route = { ...base, name: "home" };
      break;
  }

  return finalise(route, params, defaultTab);
}

function finalise(route, params, defaultTab) {
  if (route.name === "library" && !route.libTab) route.libTab = defaultTab;
  const player = params.get("player");
  if (player) route.player = player;
  if (route.player === "open" || route.player === "1" || route.player === "true") {
    route.player = "open";
  } else if (PLAYER_TABS.includes(route.player)) {
    route.playerTab = route.player;
    route.player = "open";
  } else {
    route.player = null;
  }
  return route;
}

/**
 * What the player says it is playing, for a detail route.
 *
 * This lives here because it is a fact about the route, not about the player:
 * `main.js` used to build it inline with `"Album · "` hardcoded, so a playlist
 * and an artist both told the listener they were an album, and an entity with no
 * title left a dangling separator — the full player showed the string
 * "Album ·" on its own. A name this function does not recognise is not an
 * entity, and an entity with no name has nothing to say, so both return the
 * empty string and the player falls back to its own "Playing".
 */
export function entityLabel(routeName, name) {
  const kind = { album: "Album", artist: "Artist", playlist: "Playlist" }[routeName];
  if (!kind || !name) return "";
  return `${kind} · ${name}`;
}

/** Route object → hash. Round-trips: `parseRoute(formatRoute(r))` deep-equals
 *  `r` for every route the app produces, which is asserted by tests. */
export function formatRoute(route) {
  const params = new URLSearchParams();
  let path;
  switch (route.name) {
    case "search":
      path = "/search";
      if (route.query) params.set("q", route.query);
      break;
    case "library":
      path = `/library/${LIBRARY_TABS.includes(route.libTab) ? route.libTab : "playlists"}`;
      break;
    case "playlist":
      path = `/playlist/${encodeURIComponent(route.id ?? "")}`;
      break;
    case "artist":
      path = `/artist/${encodeURIComponent(route.id ?? "")}`;
      break;
    case "album":
      path = `/album/${encodeURIComponent(route.id ?? "")}`;
      break;
    case "settings":
      path = "/settings";
      break;
    case "about-legal":
      path = route.doc ? `/about-legal/${encodeURIComponent(route.doc)}` : "/about-legal";
      break;
    case "home":
    default:
      path = "/home";
      break;
  }
  if (route.player === "open") {
    params.set("player", "open");
    if (route.playerTab && route.playerTab !== "queue") params.set("ptab", route.playerTab);
  }
  const query = params.toString();
  return `#${path}${query ? `?${query}` : ""}`;
}

/**
 * Whether entering `route` should fetch its data.
 *
 * This exists because the decision was made inline, from `sameRoute(previous,
 * route)`, and the initial route is *by construction* the same as the route the
 * app starts on — `main.js` seeds its state with `parseRoute(location.hash)`.
 * So on a fresh visit the comparison was true, the fetch was skipped, and `/app/`
 * opened on an idle Home: no request to any source was ever made, a deep link to
 * `#/search?q=…` never searched, and `#/album/<id>` never loaded. Rendered to a
 * screenshot, a first-time visitor got "Nothing to show yet" over an empty feed
 * with no error, because there was no error — nothing had been tried.
 *
 * `initial` is the router's own signal (`onRoute` passes `{ initial: !started }`)
 * and it is the only reliable one: "the URL did not change" and "the app has
 * never loaded anything" are different facts, and only the second one decides
 * whether there is data to fetch.
 */
export function needsData(previous, route, { initial = false } = {}) {
  if (initial) return true;
  if (!previous) return true;
  return !sameData(previous, route);
}

/**
 * Two routes read the same data when they ask the same question.
 *
 * Deliberately *not* `sameRoute`, which also compares the player's open state
 * and tab: those are presentation, and treating them as a route change made
 * opening the player re-run the screen's query — a second network request, and a
 * flash of the loading state, every time somebody played a track from a list.
 */
export function sameData(a, b) {
  if (!a || !b) return false;
  return a.name === b.name && a.id === b.id && a.query === b.query && a.libTab === b.libTab && a.doc === b.doc;
}

/** Two routes are the same screen when every field the view reads matches. */
export function sameRoute(a, b) {
  if (!a || !b) return false;
  return (
    a.name === b.name &&
    a.id === b.id &&
    a.query === b.query &&
    a.libTab === b.libTab &&
    a.doc === b.doc &&
    (a.player === "open") === (b.player === "open") &&
    (a.player === "open" ? a.playerTab === b.playerTab : true)
  );
}

/**
 * A small hash router.
 *
 * `onRoute` fires for every route the app can be in, including the very first
 * one, so the boot path and the navigation path are the same code. `navigate`
 * writes the hash and lets `hashchange` do the rendering: one code path, no
 * chance of UI state and URL state disagreeing.
 */
export function createRouter({
  win = globalThis,
  onRoute = () => {},
  fallbackHash = "#/home",
  defaultTab = "playlists",
} = {}) {
  let current = null;
  let started = false;
  /**
   * Whether any route has been delivered yet.
   *
   * Separate from `started`, and that separation is the fix for a bug that made
   * `initial` permanently false: `start()` set `started = true` and *then* called
   * `handle()`, which passed `{ initial: !started }` — always `false`. Nothing
   * could tell "this is the first route" from "the URL did not change", so
   * `main.js` fell back to comparing routes, and its pre-seeded state made the
   * first comparison equal. `/app/` therefore opened idle and fetched nothing.
   */
  let delivered = false;

  function read() {
    return parseRoute(win.location?.hash ?? "", { defaultTab });
  }

  /**
   * Normalise on entry. A visitor can arrive with no hash at all (a bare
   * `/app/`) or with a hash this build does not know; both get rewritten to a
   * canonical route with `replaceState` so the Back button does not trap them
   * in a redirect loop.
   */
  function handle() {
    const hash = win.location?.hash ?? "";
    const route = read();
    const canonical = formatRoute(route);
    if (hash !== canonical) {
      try {
        win.history?.replaceState?.(null, "", canonical);
      } catch {
        win.location.hash = canonical;
      }
    }
    const initial = !delivered;
    delivered = true;
    current = route;
    onRoute(route, { initial });
  }

  return {
    get current() {
      return current;
    },
    start() {
      if (started) return;
      started = true;
      const hash = win.location?.hash ?? "";
      if (!hash) {
        try {
          win.history?.replaceState?.(null, "", fallbackHash);
        } catch {
          win.location.hash = fallbackHash;
        }
      }
      win.addEventListener?.("hashchange", handle);
      handle();
    },
    stop() {
      win.removeEventListener?.("hashchange", handle);
      started = false;
      delivered = false;
    },
    /** Push a new URL. `replace` is for corrections (a legacy link, a bad id). */
    navigate(route, { replace = false } = {}) {
      const next = formatRoute(route);
      const currentHash = win.location?.hash ?? "";
      if (next === currentHash) {
        // Same URL, but state may have been written directly (the layer stack is
        // UI state, not URL state). Re-run the handler so the view is correct.
        handle();
        return;
      }
      try {
        if (replace) win.history?.replaceState?.(null, "", next);
        else win.location.hash = next;
      } catch {
        win.location.hash = next;
      }
      if (replace) handle();
    },
    /** Forward/back in the app shell. The browser's own Back is also honoured,
     *  because navigate() writes real history entries. */
    back() {
      try {
        win.history?.back?.();
      } catch {
        handle();
      }
    },
  };
}
