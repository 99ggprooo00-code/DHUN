/**
 * Where the music comes from.
 *
 * A web app cannot reuse the Android/desktop extraction chain (it is Kotlin, and
 * it needs a socket the browser will not open), so this module is the web
 * client's whole data layer. It has three implementations of one interface, and
 * the app tells the visitor which one answered — never merely that *something*
 * did:
 *
 *   `innertube`  YouTube Music's own anonymous JSON endpoints (`youtubei/v1/*`)
 *                called straight from the page with the browser's `fetch`. No
 *                account, no cookie, no proxy, no token. This is the primary
 *                source and the one that can actually play music.
 *   `endpoint`   A user-supplied read-only HTTP endpoint that speaks the small
 *                request/response contract documented in
 *                `docs/web-data-endpoints.md`. It exists because the browser
 *                path is fragile: an origin that refuses cross-origin requests
 *                (CORS), a network that inspects it, or a future upstream change
 *                all end the same way for a purely client-side app. The endpoint
 *                is the one escape hatch that needs no DHUN-operated server —
 *                the visitor points the app at one they control.
 *   `demo`       A bundled, clearly-labelled fictional catalogue. It keeps the
 *                interface usable and testable when neither of the above can
 *                answer. It has no audio, and says so; it never pretends to be
 *                the catalogue.
 *
 * Everything that transforms a response into the app's own shapes is a pure
 * function (`mapSearch`, `mapHome`, `mapBrowse`, `mapPlayer`, `pickAudioFormat`)
 * so the parsing is pinned by tests against captured response fixtures instead of
 * being verified by hand in a browser.
 *
 * The honesty rule this module enforces: **a source that fails says why, in the
 * interface, in the words the network gave it.** `describe()` returns the state
 * the UI prints, and there is no path where a failed live request is silently
 * rendered as a working catalogue of the wrong rows.
 */

import SAMPLE_CATALOG from "./data/sample-catalog.js";

export const SOURCE_KIND = Object.freeze({
  INNERTUBE: "innertube",
  ENDPOINT: "endpoint",
  DEMO: "demo",
});

/* ------------------------------------------------------------ failure type -- */

/**
 * A structured failure. Never a thrown stack: every rejection the UI can reach
 * is one of these, with a `stage` the code can branch on and a `message` the
 * user can read.
 */
export function failure(stage, error, extra = {}) {
  const message =
    typeof error === "string"
      ? error
      : error instanceof Error
        ? error.message
        : String(error);
  return {
    ok: false,
    stage,
    message,
    /** CORS is the one failure with its own remedy, so it is its own stage. */
    kind: extra.kind ?? classify(stage, message),
    ...extra,
  };
}

function classify(stage, message) {
  const text = message.toLowerCase();
  if (text.includes("failed to fetch") || text.includes("networkerror") || text.includes("load failed")) {
    return "network-or-cors";
  }
  if (text.includes("aborted") || text.includes("timeout")) return "timeout";
  return stage;
}

/* ------------------------------------------------------------- failure copy -- */

/*
 * A `fetch` rejection carries the browser's own words: "Failed to fetch" in
 * Chrome, "NetworkError when attempting to fetch resource." in Firefox, "Load
 * failed" in Safari. Printing one of those as the headline of an error state is
 * the single worst thing this app can do with it: it is the same sentence for a
 * flat network, a DNS failure, a corporate proxy, an ad-blocker and a genuine
 * cross-origin refusal, it names no remedy, and it tells a person staring at a
 * dead player nothing they can act on. It is also embarrassing in a product
 * whose whole claim is that it says what is happening.
 *
 * So the copy has three registers, and this table is the one source for all of
 * them. `summary` is what a row, a toast or a strip says — one line, no jargon,
 * the fact and nothing else. `help` is the explanation under it, with the remedy
 * where a remedy exists. `technical` says whether the raw message is worth
 * keeping: when it is (`"HTTP 502 from /youtubei/v1/player"`, `"Failed to
 * fetch"`), `failureDetail` surfaces it as a labelled diagnostic line, because
 * a user reporting a bug should not have to open developer tools to quote it —
 * and when the message is already written for a person (the demo catalogue's,
 * the endpoint's), it is the summary itself and there is nothing to demote.
 *
 * Rules this table keeps, asserted by `tests/source.test.mjs`:
 *   - no `summary` is ever a browser string, and none is empty;
 *   - every kind `failure()` can produce has an entry, including the stages;
 *   - `summary` is one sentence, so it cannot become the wall of text again.
 */
const FALLBACK_COPY = Object.freeze({
  summary: "The request could not be completed.",
  help: "Retry, or switch the data source below.",
});

const FAILURE_COPY = Object.freeze({
  "network-or-cors": {
    summary: "DHUN could not reach the source.",
    help: "The browser refused the request before reading a response. That is either no network, or the origin not permitting cross-origin calls from this site. The About & Legal screen explains what DHUN can do about it — and what it cannot.",
    technical: true,
  },
  timeout: {
    summary: "The source took too long to answer, so the request was stopped.",
    help: "The request was stopped after waiting. A slow or unreachable source will do this. Retry, or switch the data source below.",
    technical: true,
  },
  http: {
    summary: "The source answered with an error.",
    help: "The request arrived, and the server answered with an error rather than a result. That is the source's answer, not a problem on this device. Retry, or switch the data source below.",
    technical: true,
  },
  shape: {
    summary: "The source answered in a shape this client does not understand.",
    help: "The response arrived but did not have the structure this client reads. That usually means the upstream changed what it sends, so this needs an update rather than a retry — the source badge above the list says who answered.",
    technical: true,
  },
  fetch: {
    summary: "This browser did not provide a way to make the request.",
    help: "DHUN asks through the browser's own `fetch`. Without it there is nothing to ask with — a content blocker or a browser setting is the usual cause.",
    technical: false,
  },
  endpoint: {
    summary: "No endpoint address is configured.",
    help: "You chose your own endpoint as the only source, but there is no address for DHUN to ask. Add one below, or switch to Automatic.",
    technical: false,
  },
  cipher: {
    summary: "The source offered this track only behind a signature it does not share.",
    help: "DHUN will not guess one. Try another track, or switch the data source below — the native Android and Windows builds carry the full resolver chain.",
    technical: false,
  },
  nostream: {
    summary: "The source answered, but offered no audio for this track.",
    help: "The row may be a video with no audio stream, or the source may have changed what it returns. Retry, or try another track.",
    technical: false,
  },
  notfound: {
    summary: "The source does not have that item.",
    help: "The link may be stale, or the row was removed upstream. Go back and pick it again.",
    technical: true,
  },
  playability: {
    summary: "The source refused to play this track.",
    help: "That sentence is the source's own reason, usually regional or age-related. Nothing on this page can work around it, and DHUN will not pretend otherwise.",
    technical: false,
  },
  lyrics: {
    summary: "DHUN could not reach the lyrics service.",
    help: "Lyrics come from LRCLIB, a third-party service the app sends the track's name and artist to. The request did not get an answer. Nothing else in the app is affected, and the setting that stops these lookups is in Settings → Playback.",
    technical: true,
  },
  demo: {
    summary: "This row is part of the demo catalogue, which has no audio.",
    help: "The demo rows are invented so the interface stays usable while no real source answers. Settings → Data source lists what DHUN can ask instead.",
    technical: false,
  },
});

/** Kinds whose plain message is the source's own words and already reads like a
 *  sentence a person would say — the demo catalogue's, and the upstream's own
 *  "Video unavailable". Shown as-is rather than replaced by a generic line. */
const SPEAKS_FOR_ITSELF = new Set(["demo", "playability"]);

function copyFor(result) {
  const kind = result?.kind;
  const entry = FAILURE_COPY[kind];
  if (entry) {
    // A message the source wrote for a person wins over the table's generic
    // version of it: "Video unavailable" is more useful than "the source refused".
    if (SPEAKS_FOR_ITSELF.has(kind) && typeof result?.message === "string" && result.message.trim()) {
      return { ...entry, summary: result.message.trim() };
    }
    if (kind === "http") {
      const status = Number(/\bHTTP\s+(\d{3})\b/.exec(String(result?.message ?? ""))?.[1]) || null;
      if (status) return { ...entry, summary: `The source answered with an error (HTTP ${status}).` };
    }
    return entry;
  }
  // No entry: the kind may be a stage name, or missing altogether. The message
  // is still evidence — so classify it before giving up, and a bare
  // `{ message: "Failed to fetch" }` gets the network sentence rather than a
  // shrug.
  const guessed = FAILURE_COPY[classify("", String(result?.message ?? ""))];
  if (guessed) return guessed;
  return { ...FALLBACK_COPY, technical: true };
}

/**
 * The one-line sentence for a row, a toast or a strip.
 *
 * Never the browser's words: `failureSummary({ kind: "network-or-cors", message:
 * "Failed to fetch" })` is "DHUN could not reach the source.", and the raw string
 * lives in `failureDetail` for whoever wants to report it.
 */
export function failureSummary(result) {
  const summary = String(copyFor(result).summary ?? "").trim();
  return summary || FALLBACK_COPY.summary;
}

/** The longer explanation, with the remedy where one exists. */
export function failureHelp(result) {
  return copyFor(result).help;
}

/**
 * The technical string to keep as a diagnostic line, or `null` when the failure
 * is already written for a person. Callers label it ("Technical detail") so it
 * reads as evidence rather than as the message.
 */
export function failureDetail(result) {
  if (!copyFor(result).technical) return null;
  const message = typeof result?.message === "string" ? result.message.trim() : "";
  if (!message) return null;
  return message === failureSummary(result) ? null : message;
}

/** True when a failure should be shown as a cross-origin/offline refusal rather
 *  than a generic error — the one failure with its own explanation and remedy. */
export function isRefusal(result) {
  return result?.kind === "network-or-cors";
}

/**
 * A failure as a screen consumes it.
 *
 * `help` is what the state prints, `summary` is the same fact in one line for a
 * toast or a strip, and `technical` is the raw string (or `null`). It lives here,
 * beside the copy table, rather than inside the boot sequence: a screen's shape
 * is data, and a shape assembled in the middle of a 1,500-line module is a shape
 * no test can reach.
 */
export function stateError(result) {
  if (!result) return null;
  const summary = failureSummary(result);
  const help = failureHelp(result);
  return {
    kind: result.kind ?? null,
    stage: result.stage ?? null,
    summary,
    /**
     * The state's body.
     *
     * A screen renders `help` *without* `summary`, which is why the copy table's
     * help sentences each open with a sentence that stands on its own — and why
     * a failure that speaks for itself (the source's own "Video unavailable",
     * the demo catalogue's "this row is fictional") is carried into the body
     * rather than dropped. Dropping it would show an explanation of a reason
     * nobody can see.
     */
    help: SPEAKS_FOR_ITSELF.has(result.kind) && summary ? `${summary} ${help}` : help,
    technical: failureDetail(result),
  };
}

/* ------------------------------------------------------------- utilities -- */

/** "4:02" or "1:02:33" → milliseconds. Null when there is no duration. An empty
 *  or blank string is no duration — not zero seconds, which would print "0:00"
 *  next to a track whose length the response never told us. */
export function parseDuration(text) {
  if (typeof text !== "string") return null;
  const trimmed = text.trim();
  if (!trimmed) return null;
  const parts = trimmed.split(":").map((part) => Number(part));
  if (parts.length === 0 || parts.some((part) => !Number.isFinite(part))) return null;
  return parts.reduce((total, part) => total * 60 + part, 0) * 1000;
}

/** Depth-first walk over an InnerTube response, visiting every object. */
export function walkJson(node, visit, depth = 0) {
  if (!node || typeof node !== "object" || depth > 40) return;
  if (Array.isArray(node)) {
    for (const entry of node) walkJson(entry, visit, depth + 1);
    return;
  }
  visit(node);
  for (const value of Object.values(node)) walkJson(value, visit, depth + 1);
}

/** Pull the first renderer of a given type out of a response. */
export function findRenderer(root, type) {
  let found = null;
  walkJson(root, (node) => {
    if (found) return;
    if (node[type]) found = node[type];
  });
  return found;
}

function textOfRuns(runs) {
  return (runs ?? [])
    .map((run) => run?.text ?? "")
    .join("")
    .trim();
}

function thumbnails(value) {
  const list =
    value?.thumbnail?.thumbnails ??
    value?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails ??
    value?.thumbnails ??
    [];
  if (!Array.isArray(list) || list.length === 0) return null;
  // Largest last in the InnerTube ordering; pick the widest available, and
  // square the URL up to a sane size instead of shipping a 544px image in a
  // 56px row. `=w544-h544-l90-rj` is the URL's own size suffix syntax.
  const best = [...list].sort((a, b) => (b.width ?? 0) - (a.width ?? 0))[0];
  const url = best?.url;
  if (typeof url !== "string" || !url.startsWith("https://")) return null;
  return url.replace(/=w\d+-h\d+.*$/, "=w256-h256-l90-rj");
}

/** The last navigation endpoint in a list of runs — where a title points. */
function endpointOf(runs) {
  for (const run of runs ?? []) {
    const endpoint = run?.navigationEndpoint;
    if (!endpoint) continue;
    if (endpoint.watchEndpoint?.videoId) {
      return { kind: "track", videoId: endpoint.watchEndpoint.videoId };
    }
    const browseId = endpoint.browseEndpoint?.browseId;
    if (browseId) return { kind: browseKind(browseId), browseId };
    if (endpoint.watchPlaylistEndpoint?.playlistId) {
      return { kind: "playlist", browseId: `VL${endpoint.watchPlaylistEndpoint.playlistId}` };
    }
  }
  return null;
}

/** InnerTube ids are self-describing: `MPREb_` album, `UC…`/`FEmusic_` artist, `VL…` playlist. */
export function browseKind(browseId) {
  if (typeof browseId !== "string") return "unknown";
  if (browseId.startsWith("MPRE")) return "album";
  if (browseId.startsWith("VL") || browseId.startsWith("RDCLAK")) return "playlist";
  if (browseId.startsWith("UC") || browseId.startsWith("FEmusic_library")) return "artist";
  return "unknown";
}

/* ------------------------------------------------------------ Innertube -- */

const INNERTUBE_BASE = "https://music.youtube.com/youtubei/v1/";
/**
 * The key the web player ships in its own page.
 *
 * Not a secret and not ours: it is public, it is in YouTube's own HTML, and it
 * identifies "the web client" rather than a person. It is sent because the
 * request claims to be that client; InnerTube also answers some paths without
 * it, and no DHUN request carries an account, a cookie or a PO token.
 */
export const INNERTUBE_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30";
/**
 * The client identity sent with every request. Public values, identical to the
 * ones YouTube's own web player sends — no account, no cookie, no PO token.
 * `clientVersion` is refreshed from the response's own footer when the server
 * offers one, so a build that has been open for a while does not silently rot.
 */
export const INNERTUBE_CLIENT = Object.freeze({
  clientName: "WEB_REMIX",
  clientVersion: "1.20250310.01.00",
  clientHeaderId: "67",
  hl: "en",
  gl: "US",
});

export const REQUEST_TIMEOUT_MS = 12000;

/** One request, with a timeout the browser respects and failures normalised. */
async function requestJson(doFetch, url, { method = "POST", headers = {}, body, signal } = {}) {
  const controller = typeof AbortController === "function" ? new AbortController() : null;
  const timer = controller
    ? setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
    : null;
  try {
    const response = await doFetch(url, {
      method,
      headers,
      body,
      // Anonymous by construction: no cookies are sent, so a signed-in YouTube
      // session in the same browser is never used by DHUN.
      credentials: "omit",
      mode: "cors",
      cache: "no-store",
      signal: signal ?? controller?.signal,
    });
    if (!response.ok) return failure("http", new Error(`HTTP ${response.status} from ${new URL(url).pathname}`));
    return { ok: true, data: await response.json() };
  } catch (error) {
    return failure("network", error);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

export function createInnertubeSource({ fetchImpl = globalThis.fetch?.bind(globalThis), client = INNERTUBE_CLIENT } = {}) {
  let activeClient = { ...client };

  async function call(path, body) {
    if (typeof fetchImpl !== "function") {
      return failure("fetch", new Error("this browser did not provide fetch()"));
    }
    const url = `${INNERTUBE_BASE}${path}?key=${INNERTUBE_KEY}&prettyPrint=false`;
    const result = await requestJson(fetchImpl, url, {
      headers: {
        "Content-Type": "application/json",
        "X-YouTube-Client-Name": activeClient.clientHeaderId,
        "X-YouTube-Client-Version": activeClient.clientVersion,
      },
      body: JSON.stringify({
        context: {
          client: {
            clientName: activeClient.clientName,
            clientVersion: activeClient.clientVersion,
            hl: activeClient.hl,
            gl: activeClient.gl,
          },
        },
        ...body,
      }),
    });
    if (result.ok) adoptClientVersion(result.data);
    return result;
  }

  /** The response carries the version the server expects; a long-lived tab uses
   *  whatever it last learned rather than the string it was built with. */
  function adoptClientVersion(data) {
    const version =
      data?.responseContext?.serviceTrackingParams
        ?.flatMap((param) => param?.params ?? [])
        ?.find((param) => param?.key === "cver")?.value ?? null;
    if (typeof version === "string" && version && version !== activeClient.clientVersion) {
      activeClient = { ...activeClient, clientVersion: version };
    }
  }

  return {
    kind: SOURCE_KIND.INNERTUBE,
    label: "YouTube Music (anonymous, from this browser)",
    describe: () => ({
      kind: SOURCE_KIND.INNERTUBE,
      label: "YouTube Music — anonymous",
      detail: "Requests go from this browser to music.youtube.com. No account, no cookie, no DHUN server.",
    }),

    async search(query) {
      const result = await call("search", { query: String(query) });
      if (!result.ok) return result;
      const mapped = mapSearch(result.data);
      if (!mapped.ok) return mapped;
      return { ok: true, ...mapped };
    },

    async home() {
      const result = await call("browse", { browseId: "FEmusic_home" });
      if (!result.ok) return result;
      return mapHome(result.data);
    },

    async browse(browseId) {
      const kind = browseKind(browseId);
      const result = await call("browse", { browseId });
      if (!result.ok) return result;
      return mapBrowse(result.data, { kind, browseId });
    },

    async resolveStream(videoId) {
      const result = await call("player", {
        videoId,
        contentCheckOk: true,
        racyCheckOk: true,
        playbackContext: {
          contentPlaybackContext: { signatureTimestamp: null },
        },
      });
      if (!result.ok) return result;
      const mapped = mapPlayer(result.data);
      return mapped.ok ? mapped : mapped;
    },
  };
}

/* ------------------------------------------------------------- mapping -- */

/** Every `musicResponsiveListItemRenderer`, in document order. */
export function collectResponsiveItems(root) {
  const items = [];
  walkJson(root, (node) => {
    if (node.musicResponsiveListItemRenderer) items.push(node.musicResponsiveListItemRenderer);
  });
  return items;
}

/**
 * One list item → a track, album, artist or playlist.
 *
 * The list pages (search, home shelves, album tracklists, artist pages) all use
 * the same renderer with different columns, so the shape is decided by the
 * navigation endpoint of the title rather than by which page it came from.
 */
export function mapResponsiveItem(renderer) {
  const columns = (renderer.flexColumns ?? []).map((column) =>
    textOfRuns(column?.musicResponsiveListItemFlexColumnRenderer?.text?.runs),
  );
  const columnRuns = (renderer.flexColumns ?? []).map(
    (column) => column?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?? [],
  );
  const title = columns[0] ?? "";
  if (!title) return null;

  const videoId = renderer.playlistItemData?.videoId ?? renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId ?? null;
  const endpoint = endpointOf(columnRuns[0]) ?? endpointOf(columnRuns[1]);
  const artwork = thumbnails(renderer);
  const subtitle = columns[1] ?? "";
  // The column's runs arrive joined with a separator run. Both the bullet and
  // the middle dot appear in real responses depending on the page.
  const subtitleParts = subtitle.split(/[•·]/).map((part) => part.trim()).filter(Boolean);
  const duration = parseDuration(subtitleParts.at(-1) ?? "");

  if (videoId && (!endpoint || endpoint.kind === "track")) {
    return {
      kind: "track",
      id: videoId,
      videoId,
      title,
      artist: subtitleParts[0] ?? "",
      album: subtitleParts[1] ?? null,
      durationMs: duration,
      artwork,
    };
  }

  const browseId = endpoint?.browseId ?? renderer.navigationEndpoint?.browseEndpoint?.browseId ?? null;
  if (!browseId) {
    // A row that points nowhere is still worth showing when it carries a video
    // id; otherwise it is dropped rather than rendered as a dead link.
    if (videoId) {
      return {
        kind: "track",
        id: videoId,
        videoId,
        title,
        artist: subtitleParts[0] ?? "",
        album: subtitleParts[1] ?? null,
        durationMs: duration,
        artwork,
      };
    }
    return null;
  }

  const kind = endpoint?.kind && endpoint.kind !== "unknown" ? endpoint.kind : browseKind(browseId);
  if (kind === "album") {
    return {
      kind: "album",
      id: browseId,
      title,
      artist: subtitleParts[0] ?? "",
      year: subtitleParts.find((part) => /^\d{4}$/.test(part)) ?? null,
      artwork,
    };
  }
  if (kind === "artist") {
    return { kind: "artist", id: browseId, name: title, artwork };
  }
  if (kind === "playlist") {
    return {
      kind: "playlist",
      id: browseId,
      name: title,
      artist: subtitleParts[0] ?? "",
      trackCount: Number(subtitleParts.find((part) => /^\d+ songs?$/i.test(part))?.split(" ")[0]) || null,
      artwork,
    };
  }
  return null;
}

/** Search response → the four result groups the Search screen renders. */
export function mapSearch(data) {
  const items = collectResponsiveItems(data).map(mapResponsiveItem).filter(Boolean);
  if (items.length === 0) {
    // A search response with no list items is either a genuinely empty result or
    // an unrecognised shape. Distinguish them, because only one is a bug.
    const hasAnyRenderer = findRenderer(data, "musicShelfRenderer") ?? findRenderer(data, "itemSectionRenderer");
    if (!hasAnyRenderer) {
      return failure("shape", new Error("the search response had no list renderers"), {
        kind: "shape",
      });
    }
    return { ok: true, tracks: [], albums: [], artists: [], playlists: [] };
  }
  return {
    ok: true,
    tracks: items.filter((item) => item.kind === "track"),
    albums: items.filter((item) => item.kind === "album"),
    artists: items.filter((item) => item.kind === "artist"),
    playlists: items.filter((item) => item.kind === "playlist"),
  };
}

/** Home response → titled shelves of cards. */
export function mapHome(data) {
  const shelves = [];
  walkJson(data, (node) => {
    const shelf =
      node.musicCarouselShelfRenderer ??
      node.musicShelfRenderer ??
      null;
    if (!shelf) return;
    const title =
      textOfRuns(shelf.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs) ||
      textOfRuns(shelf.header?.musicShelfRenderer?.title?.runs) ||
      textOfRuns(shelf.title?.runs) ||
      "";
    if (!title) return;
    const entries = [];
    walkJson(shelf.contents ?? [], (inner) => {
      if (inner.musicResponsiveListItemRenderer) {
        const item = mapResponsiveItem(inner.musicResponsiveListItemRenderer);
        if (item) entries.push(item);
      }
      if (inner.musicTwoRowItemRenderer) {
        const item = mapTwoRowItem(inner.musicTwoRowItemRenderer);
        if (item) entries.push(item);
      }
    });
    if (entries.length > 0) shelves.push({ title, entries });
  });

  // The walk visits parent shelves before the children they contain; keep the
  // first occurrence of each title and drop nested duplicates.
  const seen = new Set();
  const unique = shelves.filter((shelf) => {
    const key = shelf.title.toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  if (unique.length === 0) {
    const hasRenderer = findRenderer(data, "musicCarouselShelfRenderer") ?? findRenderer(data, "musicShelfRenderer");
    if (!hasRenderer) {
      return failure("shape", new Error("the home response had no shelves"), { kind: "shape" });
    }
  }
  return { ok: true, shelves: unique };
}

/** The card renderer used by home shelves for albums, playlists and artists. */
export function mapTwoRowItem(renderer) {
  const title = textOfRuns(renderer.title?.runs);
  if (!title) return null;
  const subtitle = (renderer.subtitle?.runs ?? []).map((run) => run?.text ?? "").join("").trim();
  const browseId =
    renderer.navigationEndpoint?.browseEndpoint?.browseId ??
    renderer.title?.runs?.[0]?.navigationEndpoint?.browseEndpoint?.browseId ??
    null;
  const playlistId = renderer.navigationEndpoint?.watchPlaylistEndpoint?.playlistId ?? null;
  const artwork = thumbnails(renderer);
  const id = browseId ?? (playlistId ? `VL${playlistId}` : null);
  if (!id) return null;
  const kind = browseKind(id);
  if (kind === "artist") return { kind: "artist", id, name: title, artwork };
  if (kind === "playlist") {
    return { kind: "playlist", id, name: title, artist: subtitle, artwork };
  }
  return {
    kind: "album",
    id,
    title,
    artist: subtitle,
    year: /^\d{4}$/.test(subtitle) ? subtitle : null,
    artwork,
  };
}

/**
 * A browse response (album, artist, playlist) → header + tracklist.
 *
 * Albums, artists and playlists share one response envelope, so one function
 * handles all three and reports which it recognised. A tracklist is read from
 * the responsive list items; an album's "1/12" index column and a playlist's
 * per-row album column are both handled by `mapResponsiveItem`.
 */
export function mapBrowse(data, { kind = "unknown", browseId = null } = {}) {
  const title =
    textOfRuns(findRenderer(data, "musicDetailHeaderRenderer")?.title?.runs) ||
    textOfRuns(findRenderer(data, "musicImmersiveHeaderRenderer")?.title?.runs) ||
    textOfRuns(findRenderer(data, "musicVisualHeaderRenderer")?.title?.runs) ||
    textOfRuns(findRenderer(data, "musicHeaderRenderer")?.title?.runs) ||
    "";
  const headerRenderer =
    findRenderer(data, "musicDetailHeaderRenderer") ??
    findRenderer(data, "musicImmersiveHeaderRenderer") ??
    null;
  const subtitleText =
    textOfRuns(headerRenderer?.subtitle?.runs) ||
    textOfRuns(headerRenderer?.secondSubtitle?.runs) ||
    "";
  const parts = subtitleText.split("•").map((part) => part.trim()).filter(Boolean);
  const artwork =
    thumbnails(headerRenderer) ??
    thumbnails(findRenderer(data, "musicThumbnailRenderer")) ??
    null;

  const items = collectResponsiveItems(data)
    .map(mapResponsiveItem)
    .filter((item) => item && item.kind === "track");

  if (!title && items.length === 0) {
    return failure("shape", new Error("the browse response had neither a header nor a tracklist"), {
      kind: "shape",
    });
  }

  if (kind === "album" || /album/i.test(subtitleText)) {
    return {
      ok: true,
      kind: "album",
      album: {
        id: browseId,
        title,
        artist: parts.find((part) => !/^\d{4}$/.test(part) && !/songs?$/i.test(part)) ?? "",
        year: parts.find((part) => /^\d{4}$/.test(part)) ?? null,
        trackCount: items.length,
        artwork,
      },
      tracks: items,
    };
  }

  if (kind === "artist") {
    const albums = [];
    walkJson(data, (node) => {
      if (node.musicTwoRowItemRenderer) {
        const item = mapTwoRowItem(node.musicTwoRowItemRenderer);
        if (item?.kind === "album" || item?.kind === "playlist") albums.push(item);
      }
    });
    return {
      ok: true,
      kind: "artist",
      artist: {
        id: browseId,
        name: title,
        description: textOfRuns(headerRenderer?.description?.runs) || "",
        artwork,
      },
      tracks: items,
      albums: dedupeById(albums),
    };
  }

  return {
    ok: true,
    kind: "playlist",
    playlist: {
      id: browseId,
      name: title,
      description: textOfRuns(headerRenderer?.description?.runs) || "",
      artist: parts[0] ?? "",
      trackCount: items.length,
      artwork,
    },
    tracks: items,
  };
}

function dedupeById(items) {
  const seen = new Set();
  return items.filter((item) => {
    if (!item?.id || seen.has(item.id)) return false;
    seen.add(item.id);
    return true;
  });
}

/**
 * Choose an audio stream from a player response.
 *
 * Preference order, and why:
 *   1. an audio-only adaptive format with a direct `url` — smallest bytes for
 *      the same audio, and the only kind whose container the EQ can process;
 *   2. a muxed format with a direct `url` — plays everywhere, carries video;
 *   3. nothing — a `signatureCipher`-only response cannot be played in a browser
 *      without implementing YouTube's signature deciphering, which is out of
 *      scope and is reported as such rather than worked around.
 *
 * `itag` is kept because it is what the UI shows when a stream fails: "itag 251
 * refused to play" is a support-ready sentence, "playback failed" is not.
 */
export function pickAudioFormat(streamingData) {
  const adaptive = (streamingData?.adaptiveFormats ?? []).filter(
    (format) => typeof format?.mimeType === "string" && format.mimeType.startsWith("audio/"),
  );
  const muxed = streamingData?.formats ?? [];

  const usable = (format) => typeof format?.url === "string" && format.url.startsWith("https://");
  const cipherOnly = [...adaptive, ...muxed].filter(
    (format) => !usable(format) && format?.signatureCipher,
  );

  const byBitrate = (a, b) => (Number(b.bitrate) || 0) - (Number(a.bitrate) || 0);
  const audio = adaptive.filter(usable).sort(byBitrate)[0];
  if (audio) {
    return {
      ok: true,
      url: audio.url,
      mimeType: audio.mimeType,
      itag: audio.itag ?? null,
      bitrate: Number(audio.bitrate) || null,
      contentLength: Number(audio.contentLength) || null,
      /** Adaptive audio is served from `googlevideo.com`; whether it carries
       *  CORS headers decides if the equaliser can process it. Probed by
       *  `audio.js` at load time, never assumed. */
      crossOrigin: true,
    };
  }
  const mux = muxed.filter(usable).sort(byBitrate)[0];
  if (mux) {
    return {
      ok: true,
      url: mux.url,
      mimeType: mux.mimeType,
      itag: mux.itag ?? null,
      bitrate: Number(mux.bitrate) || null,
      contentLength: Number(mux.contentLength) || null,
      crossOrigin: true,
    };
  }
  if (cipherOnly.length > 0) {
    return failure(
      "cipher",
      new Error(
        `YouTube returned ${cipherOnly.length} stream(s) behind a signature cipher and none in the clear`,
      ),
      { kind: "cipher", itag: cipherOnly[0].itag ?? null },
    );
  }
  return failure("nostream", new Error("the player response contained no audio format"), {
    kind: "nostream",
  });
}

/** Player response → playable track metadata + stream. */
export function mapPlayer(data) {
  const details = data?.videoDetails ?? null;
  const status = data?.playabilityStatus?.status ?? "UNKNOWN";
  if (status !== "OK" && !details) {
    return failure(
      "playability",
      new Error(
        data?.playabilityStatus?.reason ?? `playability status ${status}`,
      ),
      { kind: "playability", status },
    );
  }
  const stream = pickAudioFormat(data?.streamingData);
  const durationMs = details?.lengthSeconds ? Number(details.lengthSeconds) * 1000 : null;
  return {
    ok: stream.ok,
    ...(stream.ok ? { stream } : stream),
    track: {
      id: details?.videoId ?? null,
      videoId: details?.videoId ?? null,
      title: details?.title ?? "",
      artist: details?.author ?? "",
      durationMs,
      artwork: thumbnails(details) ?? null,
      isLive: Boolean(details?.isLiveContent),
    },
  };
}

/* --------------------------------------------------------------- endpoint -- */

/**
 * The documented contract a user-hosted endpoint implements. Kept as data so the
 * About & Legal screen can print it and `docs/web-data-endpoints.md` can be
 * checked against the same source.
 */
export const ENDPOINT_CONTRACT = Object.freeze([
  { method: "GET", path: "/health", returns: '{ "ok": true, "version": "1" }' },
  { method: "POST", path: "/search", body: '{ "query": "…" }', returns: "the search groups: { tracks, albums, artists, playlists }" },
  { method: "GET", path: "/home", returns: "{ shelves: [{ title, entries: [...] }] }" },
  { method: "GET", path: "/browse/:id", returns: "a header (album | artist | playlist) plus { tracks }" },
  { method: "POST", path: "/stream", body: '{ "videoId": "…" }', returns: "{ url, mimeType, itag?, title?, artist?, durationMs? }" },
]);

function normaliseEndpoint(url) {
  const trimmed = String(url ?? "").trim().replace(/\/+$/, "");
  if (!trimmed) return null;
  try {
    const parsed = new URL(trimmed);
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") return null;
    return parsed;
  } catch {
    return null;
  }
}

/**
 * A source backed by a user-provided endpoint.
 *
 * The shapes above are deliberately the *same* ones the Innertube mapper
 * produces, so the UI cannot tell the difference between the two — which is the
 * point: if the anonymous path is blocked on a given network, pointing the app
 * at an endpoint you control restores every feature except none, because the
 * endpoint does the resolving.
 */
export function createEndpointSource({ url, fetchImpl = globalThis.fetch?.bind(globalThis) } = {}) {
  const base = normaliseEndpoint(url);

  async function call(path, { method = "GET", body } = {}) {
    if (!base) return failure("endpoint", new Error("no endpoint URL is configured"), { kind: "endpoint" });
    if (typeof fetchImpl !== "function") return failure("fetch", new Error("this browser did not provide fetch()"));
    const target = new URL(`${base.pathname.replace(/\/$/, "")}${path}`, base.origin);
    const result = await requestJson(fetchImpl, target.toString(), {
      method,
      headers: body ? { "Content-Type": "application/json" } : {},
      body: body ? JSON.stringify(body) : undefined,
    });
    if (!result.ok) return result;
    return { ok: true, data: result.data };
  }

  return {
    kind: SOURCE_KIND.ENDPOINT,
    label: base ? `Your endpoint (${base.host})` : "Your endpoint (not configured)",
    describe: () =>
      base
        ? {
            kind: SOURCE_KIND.ENDPOINT,
            label: `Your endpoint — ${base.host}`,
            detail: "Metadata and streams come from the address you configured. DHUN never talks to it for you.",
          }
        : {
            kind: SOURCE_KIND.ENDPOINT,
            label: "Your endpoint — not configured",
            detail: "Add an address on the Settings screen.",
          },
    get configured() {
      return Boolean(base);
    },
    async health() {
      const result = await call("/health");
      return result.ok ? { ok: true, version: result.data?.version ?? null } : result;
    },
    async search(query) {
      const result = await call("/search", { method: "POST", body: { query: String(query) } });
      if (!result.ok) return result;
      const data = result.data ?? {};
      return {
        ok: true,
        tracks: Array.isArray(data.tracks) ? data.tracks : [],
        albums: Array.isArray(data.albums) ? data.albums : [],
        artists: Array.isArray(data.artists) ? data.artists : [],
        playlists: Array.isArray(data.playlists) ? data.playlists : [],
      };
    },
    async home() {
      const result = await call("/home");
      if (!result.ok) return result;
      const shelves = Array.isArray(result.data?.shelves) ? result.data.shelves : [];
      return { ok: true, shelves };
    },
    async browse(id) {
      const result = await call(`/browse/${encodeURIComponent(id)}`);
      if (!result.ok) return result;
      return { ok: true, kind: result.data?.kind ?? browseKind(id), ...result.data };
    },
    async resolveStream(videoId) {
      const result = await call("/stream", { method: "POST", body: { videoId } });
      if (!result.ok) return result;
      const { url: streamUrl, mimeType, itag = null, title = "", artist = "", durationMs = null, artwork = null } = result.data ?? {};
      if (typeof streamUrl !== "string" || !/^https?:\/\//.test(streamUrl)) {
        return failure("shape", new Error("the endpoint returned no playable url"), { kind: "shape" });
      }
      return {
        ok: true,
        stream: { url: streamUrl, mimeType: mimeType ?? "audio/mpeg", itag, crossOrigin: true },
        track: { videoId, title, artist, durationMs, artwork },
      };
    },
  };
}

/* ------------------------------------------------------------------ demo -- */

/**
 * The bundled demo catalogue.
 *
 * Labelled everywhere it appears, and it has no audio: rows play only if a real
 * source can resolve them. Its job is to keep the interface navigable when the
 * live path is unavailable or refused, so a visitor can see what the app does
 * instead of an error page — not to stand in for a music service.
 */
export function createDemoSource({ catalog = SAMPLE_CATALOG } = {}) {
  const byId = (list, id) => list.find((entry) => entry.id === id) ?? null;
  const trackById = (id) => catalog.tracks.find((track) => track.id === id || track.videoId === id) ?? null;

  function matches(query) {
    const needle = query.trim().toLowerCase();
    if (!needle) return { tracks: [], albums: [], artists: [], playlists: [] };
    const has = (value) => String(value ?? "").toLowerCase().includes(needle);
    return {
      tracks: catalog.tracks.filter((track) => has(track.title) || has(track.artist) || has(track.album)),
      albums: catalog.albums.filter((album) => has(album.title) || has(album.artist)),
      artists: catalog.artists.filter((artist) => has(artist.name)),
      playlists: catalog.playlists.filter((playlist) => has(playlist.name)),
    };
  }

  return {
    kind: SOURCE_KIND.DEMO,
    label: "Demo catalogue (fictional songs, no audio)",
    describe: () => ({
      kind: SOURCE_KIND.DEMO,
      label: "Demo catalogue — fictional, no audio",
      detail:
        "These titles are invented and no stream exists for them. DHUN shows them so the interface can be used while no real source answers.",
    }),
    isDemo: true,
    async search(query) {
      return { ok: true, demo: true, ...matches(query) };
    },
    async home() {
      return {
        ok: true,
        demo: true,
        shelves: [
          { title: "Quick picks", entries: catalog.tracks.slice(0, 6) },
          { title: "Recommended songs", entries: catalog.tracks.slice(6, 12) },
          { title: "Listen again", entries: [...catalog.tracks].slice(2, 8) },
          { title: "Albums for you", entries: catalog.albums },
        ],
      };
    },
    async browse(id) {
      const album = byId(catalog.albums, id);
      if (album) {
        return {
          ok: true,
          demo: true,
          kind: "album",
          album: { ...album, trackCount: catalog.tracks.filter((track) => track.albumId === album.id).length },
          tracks: catalog.tracks.filter((track) => track.albumId === album.id),
        };
      }
      const artist = byId(catalog.artists, id);
      if (artist) {
        return {
          ok: true,
          demo: true,
          kind: "artist",
          artist,
          tracks: catalog.tracks.filter((track) => track.artist === artist.name),
          albums: catalog.albums.filter((entry) => entry.artist === artist.name),
        };
      }
      const playlist = byId(catalog.playlists, id);
      if (playlist) {
        const tracks = playlist.trackIds.map((trackId) => byId(catalog.tracks, trackId)).filter(Boolean);
        return { ok: true, demo: true, kind: "playlist", playlist: { ...playlist, trackCount: tracks.length }, tracks };
      }
      return failure("notfound", new Error(`nothing in the demo catalogue has the id ${id}`), {
        kind: "notfound",
      });
    },
    async resolveStream(videoId) {
      const track = trackById(videoId);
      return failure(
        "demo",
        new Error(
          track
            ? `"${track.title}" is a fictional demo entry, so there is no audio to fetch`
            : "the demo catalogue has no audio at all",
        ),
        { kind: "demo" },
      );
    },
    trackById,
  };
}

/* -------------------------------------------------------------- composite -- */

/**
 * The source the app actually uses.
 *
 * Resolution order, and it is a *policy* rather than a fallback chain:
 *
 *   `auto`      an endpoint when one is configured (it is the visitor's explicit
 *               choice of infrastructure), otherwise the anonymous Innertube
 *               client; the demo only answers when both fail.
 *   `endpoint`  endpoint only.  `innertube` Innertube only.  `demo` demo only.
 *
 * Whichever answers is recorded in `lastUsed.kind` and shown in the UI. When a
 * request is served by the demo because a live source failed, the failure is
 * attached to that state so the notice can say *why* — the difference between
 * "you are looking at sample data" and "you are looking at sample data because
 * the network refused the real request" is the whole message.
 */
export function createSourceManager({ settings, fetchImpl } = {}) {
  const demo = createDemoSource();
  let endpoint = createEndpointSource({ url: settings?.get?.("endpointUrl") ?? "", fetchImpl });
  let innertube = createInnertubeSource({ fetchImpl });

  let lastUsed = { kind: SOURCE_KIND.DEMO, reason: null, at: 0 };

  function configuredId() {
    return settings?.get?.("sourceId") ?? "auto";
  }

  /** Rebuild sources whose configuration changed (the endpoint URL). */
  function refresh() {
    endpoint = createEndpointSource({ url: settings?.get?.("endpointUrl") ?? "", fetchImpl });
    innertube = createInnertubeSource({ fetchImpl });
  }

  function order() {
    switch (configuredId()) {
      case SOURCE_KIND.ENDPOINT:
        return [endpoint];
      case SOURCE_KIND.INNERTUBE:
        return [innertube];
      case SOURCE_KIND.DEMO:
        return [demo];
      case "auto":
      default:
        return endpoint.configured ? [endpoint, innertube, demo] : [innertube, demo];
    }
  }

  /**
   * Run `attempt(source)` down the order above until one answers. A source that
   * fails is recorded and the next is tried; the demo never reports a failure,
   * so the chain always terminates.
   */
  async function run(attempt, { allowDemo = true, stage = "request" } = {}) {
    const sequence = order().filter((source) => allowDemo || source.kind !== SOURCE_KIND.DEMO);
    let lastFailure = null;
    for (const source of sequence) {
      const result = await attempt(source);
      if (result?.ok) {
        lastUsed = {
          kind: source.kind,
          reason: lastFailure,
          at: Date.now(),
          demo: source.kind === SOURCE_KIND.DEMO,
        };
        return { ...result, usedKind: source.kind, reason: lastFailure };
      }
      if (result?.ok === false) lastFailure = result;
    }
    if (lastFailure) {
      lastUsed = { kind: null, reason: lastFailure, at: Date.now(), demo: false };
      return lastFailure;
    }
    return failure(stage, new Error("no data source is available"));
  }

  return {
    get lastUsed() {
      return lastUsed;
    },
    refresh,
    /** What the UI prints for "where did these rows come from". */
    describeActive() {
      const active = order()[0];
      return active?.describe?.() ?? demo.describe();
    },
    search: (query) => run((source) => source.search(query), { allowDemo: true, stage: "search" }),
    home: () => run((source) => source.home(), { allowDemo: true, stage: "home" }),
    browse: (id) => run((source) => source.browse(id), { allowDemo: true, stage: "browse" }),
    /**
     * Stream resolution never falls back to the demo, because the demo has
     * nothing to play: falling back would turn "this could not be played" into
     * "this played" for a fictional row.
     */
    resolveStream: (videoId) => run((source) => source.resolveStream(videoId), { allowDemo: false, stage: "stream" }),
    /** Ordered list of sources with their description, for the Settings picker. */
    options() {
      return [
        { id: "auto", label: "Automatic", detail: "Your endpoint if set, otherwise YouTube Music directly." },
        { id: SOURCE_KIND.ENDPOINT, label: "Your endpoint only", detail: endpoint.configured ? endpoint.describe().label : "No address configured yet." },
        { id: SOURCE_KIND.INNERTUBE, label: "YouTube Music only", detail: "Never silently falls back to sample data." },
        { id: SOURCE_KIND.DEMO, label: "Demo catalogue only", detail: "Fictional rows, no audio, always available." },
      ];
    },
    demo,
    get endpoint() {
      return endpoint;
    },
    get innertube() {
      return innertube;
    },
  };
}

/* -------------------------------------------------------------- artwork -- */

/**
 * Artwork for a track: the real image when the source supplied one, otherwise a
 * deterministic gradient from the id. Deterministic matters — a row that changed
 * colour on every render would look like a bug, and a placeholder that looks
 * like a loading state would make an empty result look broken.
 */
export function artworkInfo(entry) {
  if (entry?.artwork && typeof entry.artwork === "string") {
    return { url: entry.artwork, gradient: null };
  }
  const seed = String(entry?.id ?? entry?.videoId ?? entry?.title ?? "dhun");
  let hash = 0;
  for (let index = 0; index < seed.length; index += 1) {
    hash = (hash * 31 + seed.charCodeAt(index)) % 360;
  }
  return {
    url: null,
    gradient: `linear-gradient(135deg, hsl(${hash} 42% 30%), hsl(${(hash + 42) % 360} 48% 16%))`,
  };
}
