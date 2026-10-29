/**
 * Routes, as data.
 *
 * The app is a hash-router single page: GitHub Pages serves one `index.html`
 * and no server rewrites, so a path-based route would 404 on reload. Hash
 * routes are also what makes a deep link into the player work with no effort:
 * `#/album/abc?player=open`.
 *
 * Every route is parsed to a plain object and every object formats back to the
 * same hash. That round trip is asserted in `tests/router.test.mjs`, because a
 * link that formats differently from how it parses is a link that quietly goes
 * somewhere else.
 */

export const HOME = Object.freeze({ name: "home", id: null, query: "", libTab: null, doc: null, player: null, playerTab: "queue" });

/** The three destinations in the app bar and the bottom navigation. */
export const NAV_TABS = Object.freeze([
  { id: "HOME", label: "Home", icon: "Home", route: "home" },
  { id: "SEARCH", label: "Search", icon: "Search", route: "search" },
  { id: "LIBRARY", label: "Library", icon: "LibraryMusic", route: "library" },
]);

export const LIBRARY_TABS = Object.freeze(["playlists", "likes", "history"]);
export const PLAYER_TABS = Object.freeze(["queue", "lyrics"]);

/** Detail screens, i.e. the ones a back gesture returns *out* of. */
export const DETAIL_ROUTES = Object.freeze(["album", "artist", "playlist", "collection", "settings", "about"]);

const NAME_ALIASES = Object.freeze({
  "": "home",
  home: "home",
  search: "search",
  library: "library",
  album: "album",
  artist: "artist",
  playlist: "playlist",
  collection: "collection",
  settings: "settings",
  about: "about",
});

/** Split `#/x/y?z=1` into its path segments and its query parameters. */
export function parseHash(hash) {
  const raw = String(hash ?? "").replace(/^#/, "");
  const [pathPart, queryPart = ""] = raw.split("?");
  const segments = pathPart.split("/").filter(Boolean);
  const params = new URLSearchParams(queryPart);
  return { segments, params };
}

// A bad percent escape must not prevent the entire app from booting.
function decodeSegment(segment) {
  if (!segment) return null;
  try { return decodeURIComponent(segment); } catch { return null; }
}

export function parseRoute(hash) {
  const { segments, params } = parseHash(hash);
  const name = NAME_ALIASES[(segments[0] ?? "").toLowerCase()] ?? "home";
  const route = {
    ...HOME,
    name,
    player: params.get("player") === "open" ? "open" : null,
    playerTab: PLAYER_TABS.includes(params.get("ptab")) ? params.get("ptab") : "queue",
  };
  if (name === "search") route.query = params.get("q") ?? "";
  if (name === "album" || name === "artist" || name === "playlist" || name === "collection") {
    route.id = decodeSegment(segments[1]);
  }
  if (name === "library") {
    const tab = (segments[1] ?? "").toLowerCase();
    route.libTab = LIBRARY_TABS.includes(tab) ? tab : "playlists";
  }
  if (name === "about") route.doc = decodeSegment(segments[1]);
  return route;
}

export function formatRoute(route) {
  const parts = [];
  switch (route.name) {
    case "search":
      parts.push("search");
      break;
    case "library":
      parts.push("library", route.libTab ?? "playlists");
      break;
    case "album":
    case "artist":
    case "playlist":
    case "collection":
      parts.push(route.name, encodeURIComponent(route.id ?? ""));
      break;
    case "settings":
      parts.push("settings");
      break;
    case "about":
      parts.push("about", encodeURIComponent(route.doc ?? "privacy"));
      break;
    default:
      parts.push("home");
      break;
  }
  const params = new URLSearchParams();
  if (route.name === "search" && route.query) params.set("q", route.query);
  if (route.player === "open") {
    params.set("player", "open");
    if (route.playerTab && route.playerTab !== "queue") params.set("ptab", route.playerTab);
  }
  const query = params.toString();
  return `#/${parts.join("/")}${query ? `?${query}` : ""}`;
}

/** Which nav tab is lit for a route. Detail screens keep the tab they opened from. */
export function tabFor(route, previousTab = "HOME") {
  if (route.name === "home") return "HOME";
  if (route.name === "search") return "SEARCH";
  if (route.name === "library" || route.name === "collection") return "LIBRARY";
  if (route.name === "album" || route.name === "artist" || route.name === "playlist") return previousTab;
  return null;
}

/** The document title for a route, filled in by the screen once it has data. */
export function titleFor(route, entityTitle = "") {
  switch (route.name) {
    case "search":
      return route.query ? `Search: ${route.query}` : "Search";
    case "library":
      return route.libTab === "likes" ? "Liked songs" : route.libTab === "history" ? "Recently played" : "Your library";
    case "album":
      return entityTitle || "Album";
    case "artist":
      return entityTitle || "Artist";
    case "playlist":
      return entityTitle || "Playlist";
    case "collection":
      return entityTitle || "Playlist";
    case "settings":
      return "Settings";
    case "about":
      return route.doc === "terms" ? "Terms" : route.doc === "licences" ? "Licences" : "Privacy";
    default:
      return "";
  }
}
