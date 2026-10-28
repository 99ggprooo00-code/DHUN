/**
 * The source seam: one JioSaavn catalogue via unofficial community wrappers.
 *
 * HISTORICAL CONTEXT. This browser build replaced an InnerTube path whose
 * player requests were refused in earlier browser evidence. The current code
 * attempts wrapper-provided HTTPS media/cover URLs. This is not proof of live
 * catalogue/audio success at the deployed origin, or of access/content rights.
 * Native YouTube Music and planned Spotify are separate integrations/statuses.
 *
 * WHAT THIS MODULE OWNS. Three things, and nothing else:
 *
 *   1. THE CONTRACT — endpoints and query strings, in one table (`ENDPOINTS`).
 *   2. NORMALISATION — the API's records turned into the small shapes the
 *      screens draw (`normaliseSong`, `normaliseAlbum`, …). Every screen reads
 *      those shapes; nothing downstream sees a raw response, so a missing field
 *      is handled once, here.
 *   3. FAILURE — a typed error (`SourceError`) that carries what actually
 *      happened, so the interface can say "the API answered 429" rather than
 *      "something went wrong".
 *
 * REQUEST/PRIVACY BOUNDARY. No DHUN-operated backend or DHUN account/tracker
 * identifier is added here. Queries and catalogue IDs do leave this tab; IP,
 * browser-managed headers and third-party logs remain outside DHUN control.
 * Fetch omits credentials and uses no-store. Library metadata snapshots are
 * persisted elsewhere; media/art requests use browser-managed behaviour.
 *
 * THE API IS UNOFFICIAL. There is no provider/operator agreement or support
 * contract established in this repository. JioSaavn's interface/access terms,
 * wrapper permissions and operator retention remain unresolved review gates.
 * Settings/About/Privacy disclose the actual services; errors are not success.

 */

/**
 * The instances this build will talk to, in the order it tries them.
 *
 * WHY A LIST. Failover among three hosts for ONE catalogue family, not a
 * multi-source music implementation. ENDPOINTS and unwrap() define the assumed
 * common contract, tested with fixtures. Do not claim every host runs the same
 * MIT project/version, has the same operator, is CORS-open or is free/available
 * without dated evidence. See research 05 and browser PROVIDER-INTEGRATION.
 *
 * All configured hosts are named in CSP, Settings and Privacy. Failover may
 * disclose the query/ID to more than one operator. Adding/replacing a host
 * requires permission/privacy/security and contract evidence plus coordinated
 * source, CSP, disclosure and test changes; no automatic expansion is approved.

 */
export const API_SOURCES = Object.freeze([
  { id: "saavn", origin: "https://saavn.dev", label: "saavn.dev" },
  { id: "sumit", origin: "https://saavn.sumit.co", label: "saavn.sumit.co" },
  { id: "saavnx", origin: "https://saavnx.vercel.app", label: "saavnx.vercel.app" },
]);

/** The first instance, kept as the default for callers that name one origin. */
export const API_ORIGIN = API_SOURCES[0].origin;
export const API_BASE = `${API_ORIGIN}/api`;

/** How long a request may take before the interface gives up and says so. */
export const REQUEST_TIMEOUT_MS = 15000;

/**
 * How long one attempt may take while the app is still choosing an instance.
 *
 * The full timeout applies once an instance is known to answer. While it is
 * racing, a longer wait per host would mean a visitor staring at a skeleton for
 * three times as long as the situation deserves.
 */
export const PROBE_TIMEOUT_MS = 8000;

/** One page of rows. The searches are paginated; 20 keeps a phone screen full. */
export const PAGE_SIZE = 20;

/**
 * The endpoints this build uses, and only these.
 *
 * Written as functions rather than strings so a caller cannot forget to encode
 * a query — a search for "Arijit & Pritam" is a realistic query, and `&` in a
 * query string is a truncated search, not an error message.
 */
export const ENDPOINTS = Object.freeze({
  searchSongs: (query, page = 0, limit = PAGE_SIZE) =>
    `/search/songs?query=${encodeURIComponent(query)}&page=${page}&limit=${limit}`,
  searchAlbums: (query, page = 0, limit = PAGE_SIZE) =>
    `/search/albums?query=${encodeURIComponent(query)}&page=${page}&limit=${limit}`,
  searchArtists: (query, limit = PAGE_SIZE) => `/search/artists?query=${encodeURIComponent(query)}&limit=${limit}`,
  searchPlaylists: (query, page = 0, limit = PAGE_SIZE) =>
    `/search/playlists?query=${encodeURIComponent(query)}&page=${page}&limit=${limit}`,
  song: (id) => `/songs/${encodeURIComponent(id)}`,
  songsById: (ids) => `/songs?ids=${ids.map(encodeURIComponent).join(",")}`,
  suggestions: (id) => `/songs/${encodeURIComponent(id)}/suggestions`,
  album: (id) => `/albums?id=${encodeURIComponent(id)}`,
  artist: (id) => `/artists/${encodeURIComponent(id)}`,
  artistSongs: (id, page = 0) => `/artists/${encodeURIComponent(id)}/songs?page=${page}`,
  artistAlbums: (id) => `/artists/${encodeURIComponent(id)}/albums`,
  playlist: (id, limit = 100) => `/playlists?id=${encodeURIComponent(id)}&limit=${limit}`,
});

/* --------------------------------------------------------------- errors -- */

/**
 * A failed request, described in the words the interface will show.
 *
 * `kind` is the part the UI branches on; `status` and `url` are the part a
 * person can act on (or paste into a bug report). Nothing here is invented: a
 * timeout says timeout, a 429 says 429, and a response that is not the shape the
 * contract promises says so instead of being treated as an empty catalogue.
 */
export class SourceError extends Error {
  constructor(kind, message, { status = null, url = "", cause = null, attempts = null } = {}) {
    super(message);
    this.name = "SourceError";
    this.kind = kind;
    this.status = status;
    this.url = url;
    this.cause = cause;
    // What each configured instance answered, when the failure is the whole
    // chain rather than one request. `null` for a failure that never raced.
    this.attempts = attempts;
  }
}

export const FAILURE_KIND = Object.freeze({
  offline: "offline",
  timeout: "timeout",
  http: "http",
  shape: "shape",
  network: "network",
});

/**
 * A failed request to a sentence, in the source's own terms.
 *
 * The rule the whole app follows: the interface may not soften what happened.
 * "Could not reach the catalogue" is true; "Something went wrong" is not a
 * description. Each branch names the thing that failed and the next action.
 */
/** One line per instance that was tried: "saavn.dev — HTTP 429". */
export function describeAttempts(attempts) {
  if (!Array.isArray(attempts) || !attempts.length) return "";
  return attempts
    .map((entry) => {
      const what =
        entry.kind === "ok"
          ? "answered"
          : entry.kind === FAILURE_KIND.http
            ? `HTTP ${entry.status}`
            : entry.kind === FAILURE_KIND.timeout
              ? "no answer in time"
              : entry.kind === FAILURE_KIND.shape
                ? "answered in an unexpected shape"
                : "could not be reached";
      return `${entry.label} — ${what}`;
    })
    .join(" · ");
}

export function describeFailure(error) {
  if (!(error instanceof SourceError)) {
    return {
      title: "The catalogue could not be read",
      body: "The request failed before an answer arrived.",
      technical: String(error?.message ?? error ?? ""),
      kind: FAILURE_KIND.network,
    };
  }
  switch (error.kind) {
    case FAILURE_KIND.offline:
      return {
        title: "You appear to be offline",
        body: "The catalogue is a network service; the app kept what you saved on this device and can show that without a connection.",
        technical: `navigator.onLine = false`,
        kind: error.kind,
      };
    case FAILURE_KIND.timeout:
      return {
        title: "The catalogue did not answer in time",
        body: `No response within ${Math.round(REQUEST_TIMEOUT_MS / 1000)} seconds. The service may be slow or unreachable from this network.`,
        technical: `${error.url}`,
        kind: error.kind,
      };
    case FAILURE_KIND.http:
      return {
        title: `The catalogue answered ${error.status}`,
        body:
          error.status === 429
            ? "The API is rate-limiting this network. Wait a moment and try again."
            : error.status === 404
              ? "The API has no record at that address."
              : "The service refused the request. This is the API's answer, not the app's guess.",
        technical: `HTTP ${error.status} — ${error.url}`,
        kind: error.kind,
      };
    case FAILURE_KIND.shape:
      return {
        title: "The catalogue answered in an unexpected shape",
        body: "The response did not match the contract this build was written against, so nothing was drawn from it.",
        technical: `${error.message} — ${error.url}`,
        kind: error.kind,
      };
    default: {
      const record = describeAttempts(error.attempts);
      return {
        title: "The catalogue could not be reached",
        body: record
          ? `Every instance this build knows was tried and none answered: ${record}. An extension, a proxy or the network may be blocking them.`
          : "The request never completed. An extension, a proxy or the network may be blocking it.",
        technical: `${error.message}${error.url ? ` — ${error.url}` : ""}${record ? ` — tried: ${record}` : ""}`,
        kind: error.kind,
      };
    }
  }
}

/* --------------------------------------------------------------- requests -- */

function absolute(path, origin = API_ORIGIN) {
  return path.startsWith("http") ? path : `${origin}/api${path}`;
}

/**
 * One GET, with a timeout that actually cancels.
 *
 * `AbortSignal.timeout` is not in every browser this app supports, so the
 * controller is explicit. A request that outlives its timeout is aborted rather
 * than left to land on a screen that has already moved on.
 */
export async function requestJson(path, { fetchImpl = globalThis.fetch, timeoutMs = REQUEST_TIMEOUT_MS, signal, origin = API_ORIGIN } = {}) {
  const url = absolute(path, origin);
  if (typeof fetchImpl !== "function") {
    throw new SourceError(FAILURE_KIND.network, "This browser has no fetch()", { url });
  }
  if (globalThis.navigator?.onLine === false) {
    throw new SourceError(FAILURE_KIND.offline, "The browser reports no connection", { url });
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const onAbort = () => controller.abort();
  signal?.addEventListener?.("abort", onAbort, { once: true });
  try {
    const response = await fetchImpl(url, {
      method: "GET",
      mode: "cors",
      credentials: "omit",
      cache: "no-store",
      headers: { Accept: "application/json" },
      signal: controller.signal,
    });
    if (!response.ok) {
      throw new SourceError(FAILURE_KIND.http, `HTTP ${response.status}`, { status: response.status, url });
    }
    let payload;
    try {
      payload = await response.json();
    } catch (error) {
      throw new SourceError(FAILURE_KIND.shape, "the body was not JSON", { url, cause: error });
    }
    return unwrap(payload, url);
  } catch (error) {
    if (error instanceof SourceError) throw error;
    if (error?.name === "AbortError") {
      throw new SourceError(FAILURE_KIND.timeout, "the request was aborted", { url, cause: error });
    }
    if (globalThis.navigator?.onLine === false) {
      throw new SourceError(FAILURE_KIND.offline, error?.message ?? "offline", { url, cause: error });
    }
    throw new SourceError(FAILURE_KIND.network, error?.message ?? "the request failed", { url, cause: error });
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener?.("abort", onAbort);
  }
}

/**
 * The API wraps its answer as `{success, data}`. `success: false` is a failure
 * the service itself reported, so it is raised rather than treated as empty.
 */
function unwrap(payload, url) {
  if (payload === null || typeof payload !== "object") {
    throw new SourceError(FAILURE_KIND.shape, "the body was not an object", { url });
  }
  if (payload.success === false) {
    throw new SourceError(FAILURE_KIND.http, String(payload.message ?? "the API reported failure"), { url });
  }
  if (!("data" in payload)) {
    throw new SourceError(FAILURE_KIND.shape, "the body has no data field", { url });
  }
  return payload.data;
}

/** The `data` of a search is `{total, start, results:[…]}`; tolerate a bare list. */
function resultRows(data, url) {
  if (Array.isArray(data)) return data;
  if (data && Array.isArray(data.results)) return data.results;
  throw new SourceError(FAILURE_KIND.shape, "a search result carried no results array", { url });
}

/* ---------------------------------------------------------- normalisation -- */

/**
 * Text as this app shows it.
 *
 * The API escapes its copy (`&quot;`, `&amp;`) and some rows arrive with
 * markup-ish fragments. Those entities are decoded here, once, because every
 * screen draws the result through the escaping template — decoding there would
 * double-decode and turn text into markup.
 */
const ENTITIES = { "&amp;": "&", "&quot;": '"', "&#39;": "'", "&apos;": "'", "&lt;": "<", "&gt;": ">", "&nbsp;": " " };
export function plainText(value) {
  if (value === null || value === undefined) return "";
  return String(value).replace(/&(?:amp|quot|#39|apos|lt|gt|nbsp);/g, (match) => ENTITIES[match] ?? match).trim();
}

/** The URL shape changed once (`link` → `url`); read both rather than guess. */
export function urlOf(entry) {
  if (!entry) return "";
  if (typeof entry === "string") return entry;
  return plainText(entry.url ?? entry.link ?? "");
}

/**
 * The cover closest to `preferred` pixels, preferring anything larger.
 *
 * Covers arrive as a ladder (50, 150, 500). A phone card wants 500; a 64-pixel
 * row wants 150 and not the 50, which is a visible blur at that size.
 */
export function pickImage(image, preferred = 500) {
  const entries = (Array.isArray(image) ? image : [image]).map((entry) => ({
    url: urlOf(entry),
    // The API names the size in `quality` ("500x500"), not in a number field.
    // Reading only `size`/`width` made every cover resolve to the first entry —
    // measured in the browser: a 500px card was drawing the 50px file.
    size: Number(entry?.size ?? entry?.width ?? String(entry?.quality ?? "").split("x")[0]) || 0,
  })).filter((entry) => entry.url);
  if (!entries.length) return "";
  const larger = entries.filter((entry) => entry.size >= preferred).sort((a, b) => a.size - b.size);
  if (larger.length) return larger[0].url;
  return entries.sort((a, b) => b.size - a.size)[0].url;
}

/**
 * A cover the page is allowed to fetch.
 *
 * `img-src` is `'self' data: https://*.saavncdn.com`, and the catalogue also
 * hands back JioSaavn's own generic artist art from
 * `www.jiosaavn.com/_i/3.0/artist-default-*.png`. Those are not covers:
 * they are the platform's placeholder, and putting them in an `<img>` cost one
 * blocked request per artist row and filled the console with CSP violations
 * (browser job of website run 39376581609, 2026-10-20). A dropped URL renders
 * the drawn initial instead — on brand, no third-party request, and the policy
 * stays as narrow as it was written.
 *
 * Keeping the test here rather than widening the policy also means a future
 * image host has to be admitted deliberately, in both places, with the
 * `scripts/test_app_web.py` gate noticing if only one of them changes.
 */
const IMAGE_URL = /^https:\/\/(?:[a-z0-9-]+\.)*saavncdn\.com\//i;
export function cdnImage(url) {
  return IMAGE_URL.test(url) ? url : "";
}

/**
 * The stream, preferring 320 kbps and falling back down the ladder.
 *
 * `downloadUrl` is the API's name for "the audio files". A row with no entry is
 * a track that cannot be played, which the player reports instead of loading an
 * empty `src` and failing silently.
 */
const QUALITY_ORDER = ["320kbps", "160kbps", "96kbps", "48kbps", "12kbps"];
export function pickStream(downloadUrl) {
  const entries = (Array.isArray(downloadUrl) ? downloadUrl : [downloadUrl])
    .map((entry) => ({ url: urlOf(entry), quality: plainText(entry?.quality ?? "") }))
    .filter((entry) => entry.url);
  for (const quality of QUALITY_ORDER) {
    const match = entries.find((entry) => entry.quality === quality);
    if (match) return match;
  }
  return entries[0] ?? { url: "", quality: "" };
}

/** Artists arrive as `{primary, featured, all}`; joined for a subtitle. */
export function artistNames(song) {
  // Featured artists are frequently listed twice (in `primary` and again in
  // `all`), so the join de-duplicates: "A, A" is not a credit.
  const unique = (names) => [...new Set(names.filter(Boolean))];
  const names = (list) => (list ?? []).map((artist) => plainText(artist?.name));
  // `primary` plus `featured` is the credit line; `all` is the superset the API
  // also sends, and using it would repeat the featured artists.
  const credited = unique([...names(song?.artists?.primary), ...names(song?.artists?.featured)]);
  if (credited.length) return credited.join(", ");
  const all = unique(names(song?.artists?.all));
  if (all.length) return all.slice(0, 3).join(", ");
  // A row that has already been normalised carries a single `artist` string.
  return plainText(song?.artist ?? "") || plainText(song?.subtitle ?? "");
}

/**
 * A song, in the shape the rows draw.
 *
 * `duration` is seconds in this API; the player and the rows both want seconds,
 * so no conversion happens here and none is needed twice.
 */
export function normaliseSong(raw) {
  if (!raw || typeof raw !== "object" || !raw.id) return null;
  // A row that has already been normalised keeps its own stream: re-normalising
  // (a saved snapshot, a queue round-tripped through storage) must not silently
  // strip the audio URL.
  const stream = raw.downloadUrl
    ? pickStream(raw.downloadUrl)
    : raw.stream
      ? { url: raw.stream, quality: raw.streamQuality ?? "" }
      : { url: "", quality: "" };
  // The API sends `{primary, featured, all}`; an already-normalised row sends a
  // flat array. Both are accepted so a snapshot can be re-normalised without
  // losing its credits.
  const creditSource = Array.isArray(raw.artists)
    ? raw.artists
    : (raw.artists?.all ?? raw.artists?.primary ?? []);
  const artistList = creditSource
    .map((artist) => ({ id: plainText(artist?.id), name: plainText(artist?.name), image: cdnImage(pickImage(artist?.image, 150)) }))
    .filter((artist) => artist.name);
  return {
    kind: "song",
    id: String(raw.id),
    title: plainText(raw.name ?? raw.title ?? "Untitled"),
    subtitle: plainText(raw.subtitle ?? ""),
    artist: artistNames(raw),
    artists: artistList,
    album: {
      id: plainText(raw.album?.id ?? ""),
      name: plainText(raw.album?.name ?? ""),
    },
    year: plainText(raw.year ?? ""),
    language: plainText(raw.language ?? ""),
    duration: Number(raw.duration ?? 0) || 0,
    image: cdnImage(pickImage(raw.image, 500)),
    imageSmall: cdnImage(pickImage(raw.image, 150)),
    stream: stream.url,
    streamQuality: stream.quality,
    hasLyrics: Boolean(raw.hasLyrics),
    explicit: Boolean(raw.explicitContent),
    playCount: Number(raw.playCount ?? 0) || 0,
    url: plainText(raw.url ?? ""),
  };
}

export function normaliseAlbum(raw) {
  if (!raw || typeof raw !== "object" || !raw.id) return null;
  const songs = (raw.songs ?? []).map(normaliseSong).filter(Boolean);
  return {
    kind: "album",
    id: String(raw.id),
    title: plainText(raw.name ?? raw.title ?? "Untitled album"),
    artist: (raw.artists?.primary ?? raw.artists?.all ?? []).map((artist) => plainText(artist?.name)).filter(Boolean).join(", "),
    artists: (raw.artists?.all ?? raw.artists?.primary ?? []).map((artist) => ({
      id: plainText(artist?.id),
      name: plainText(artist?.name),
      image: cdnImage(pickImage(artist?.image, 150)),
    })).filter((artist) => artist.name),
    year: plainText(raw.year ?? ""),
    language: plainText(raw.language ?? ""),
    songCount: Number(raw.songCount ?? songs.length) || songs.length,
    duration: songs.reduce((total, song) => total + (song.duration || 0), 0),
    image: cdnImage(pickImage(raw.image, 500)),
    songs,
    url: plainText(raw.url ?? ""),
  };
}

export function normaliseArtist(raw) {
  if (!raw || typeof raw !== "object" || !raw.id) return null;
  return {
    kind: "artist",
    id: String(raw.id),
    title: plainText(raw.name ?? "Unknown artist"),
    image: cdnImage(pickImage(raw.image, 500)),
    imageSmall: cdnImage(pickImage(raw.image, 150)),
    description: plainText(raw.description ?? ""),
    followerCount: Number(raw.followerCount ?? raw.fanCount ?? 0) || 0,
    verified: Boolean(raw.isVerified),
    dominantLanguage: plainText(raw.dominantLanguage ?? ""),
    dominantType: plainText(raw.dominantType ?? ""),
    url: plainText(raw.url ?? ""),
  };
}

export function normalisePlaylist(raw) {
  if (!raw || typeof raw !== "object" || !raw.id) return null;
  const songs = (raw.songs ?? []).map(normaliseSong).filter(Boolean);
  return {
    kind: "playlist",
    id: String(raw.id),
    title: plainText(raw.name ?? raw.title ?? "Untitled playlist"),
    description: plainText(raw.description ?? ""),
    owner: plainText(raw.username ?? raw.owner ?? "JioSaavn"),
    year: plainText(raw.year ?? ""),
    language: plainText(raw.language ?? ""),
    songCount: Number(raw.songCount ?? songs.length) || songs.length,
    duration: songs.reduce((total, song) => total + (song.duration || 0), 0),
    image: cdnImage(pickImage(raw.image, 500)),
    songs,
    url: plainText(raw.url ?? ""),
  };
}

/* ---------------------------------------------------------------- reading -- */

/** One list of songs, normalised, with rows that did not parse dropped. */
function songsFrom(data, url) {
  return resultRows(data, url).map(normaliseSong).filter(Boolean);
}

/**
 * One request, against whichever instance will answer it.
 *
 * THE ORDER OF EVENTS. The instance that last answered is tried first, alone and
 * with the full timeout — the common case costs one request to one host. If it
 * fails, the remaining instances are raced against each other: the first success
 * wins, and the winner becomes the remembered instance. Only when every instance
 * has refused does the request fail, and then it fails with *all* of their
 * answers attached, so the interface can print which host said what instead of
 * "the catalogue could not be reached".
 *
 * CANCELLATION. A race leaves losers in flight. Each attempt gets its own
 * AbortController chained to the caller's signal, and the losers are aborted as
 * soon as a winner is known — a request nobody is waiting for should not keep a
 * community server busy.
 */
function createRacer({ fetchImpl, timeoutMs, sources, preferredId, onOriginChange }) {
  const list = sources.slice();
  let activeId = list.some((source) => source.id === preferredId) ? preferredId : (list[0]?.id ?? "");
  let attempts = [];
  // While a failover is running, every other request waits for it rather than
  // starting a race of its own: Home asks for seven shelves at once, and seven
  // simultaneous races would mean twenty-one requests to community servers that
  // are already struggling.
  let switching = null;

  const byId = (id) => list.find((source) => source.id === id) ?? null;

  function attempt(source, path, options, controller, budget) {
    return requestJson(path, {
      ...options,
      fetchImpl,
      timeoutMs: budget,
      origin: source.origin,
      signal: controller.signal,
    }).then(
      (data) => ({ ok: true, source, data }),
      (error) => ({
        ok: false,
        source,
        error,
        entry: {
          id: source.id,
          label: source.label,
          origin: source.origin,
          kind: error?.kind ?? FAILURE_KIND.network,
          status: error?.status ?? null,
          message: String(error?.message ?? error ?? ""),
        },
      }),
    );
  }

  function adopt(source) {
    if (source.id === activeId) return;
    activeId = source.id;
    onOriginChange?.(source);
  }

  const okEntry = (source) => ({ id: source.id, label: source.label, origin: source.origin, kind: "ok", status: 200, message: "" });

  /**
   * Race the other instances, once, for everyone who is waiting.
   *
   * Resolves with `{source, path, data}` — the winner's answer to the path this
   * caller asked for. A caller whose own path was not the one raced re-requests
   * it from the winner and reuses the result if it was.
   */
  function switchTo(path, options, failedId) {
    if (switching) return switching;
    switching = (async () => {
      const rest = list.filter((source) => source.id !== failedId);
      const controllers = rest.map(() => new AbortController());
      const abortAll = () => controllers.forEach((controller) => controller.abort());
      options.signal?.addEventListener?.("abort", abortAll, { once: true });
      try {
        const winner = await new Promise((resolve, reject) => {
          const failures = [];
          if (!rest.length) {
            reject(failures);
            return;
          }
          let left = rest.length;
          rest.forEach((source, index) => {
            attempt(source, path, options, controllers[index], PROBE_TIMEOUT_MS).then((result) => {
              if (result.ok) {
                resolve(result);
                return;
              }
              failures.push(result.entry);
              left -= 1;
              if (left === 0) reject(failures);
            });
          });
        });
        adopt(winner.source);
        return { source: winner.source, path, data: winner.data };
      } catch (failures) {
        if (!Array.isArray(failures)) throw failures;
        const worst = failures[0] ?? null;
        throw new SourceError(worst?.kind ?? FAILURE_KIND.network, worst?.message ?? "every instance failed", {
          status: worst?.status ?? null,
          url: `${failedId}/api${path}`,
          attempts: failures,
        });
      } finally {
        options.signal?.removeEventListener?.("abort", abortAll);
        abortAll();
      }
    })().finally(() => {
      switching = null;
    });
    return switching;
  }

  return {
    get origin() {
      return byId(activeId) ?? list[0] ?? null;
    },
    get origins() {
      return list.slice();
    },
    get attempts() {
      return attempts.slice();
    },

    async get(path, options = {}) {
      if (!list.length) {
        throw new SourceError(FAILURE_KIND.network, "no catalogue instance is configured", { url: path });
      }
      const tried = [];
      let source = byId(activeId) ?? list[0];

      for (let round = 0; round < 3; round += 1) {
        const controller = new AbortController();
        const linkOuter = () => controller.abort();
        options.signal?.addEventListener?.("abort", linkOuter, { once: true });
        let result;
        try {
          result = await attempt(source, path, options, controller, timeoutMs);
        } finally {
          options.signal?.removeEventListener?.("abort", linkOuter);
        }

        if (result.ok) {
          attempts = [...tried, okEntry(source)];
          return result.data;
        }
        // Offline is not a per-host problem: racing the others would repeat the
        // same refusal three times, more slowly.
        if (result.error?.kind === FAILURE_KIND.offline) {
          attempts = [result.entry];
          throw result.error;
        }
        tried.push(result.entry);

        // Another request may already have moved the chain to a live instance —
        // seven Home shelves fail together, and only one of them should pay for
        // the switch.
        if (switching) {
          try {
            await switching;
          } catch {
            /* this caller retries below and reports its own result */
          }
        }
        const moved = byId(activeId);
        if (moved && moved.id !== source.id) {
          source = moved;
          continue;
        }

        let switched = null;
        try {
          switched = await switchTo(path, options, source.id);
        } catch (error) {
          attempts = [...tried, ...(error?.attempts ?? [])];
          throw new SourceError(error?.kind ?? result.entry.kind, error?.message ?? result.entry.message, {
            status: error?.status ?? result.entry.status,
            url: error?.url ?? `${source.origin}/api${path}`,
            attempts: attempts.slice(),
          });
        }
        if (switched.path === path) {
          attempts = [...tried, okEntry(switched.source)];
          return switched.data;
        }
        source = switched.source;
      }

      // Every instance was tried and none answered this path.
      const last = tried[tried.length - 1] ?? { kind: FAILURE_KIND.network, status: null, message: "" };
      throw new SourceError(last.kind, last.message, {
        status: last.status,
        url: `${source.origin}/api${path}`,
        attempts: tried.slice(),
      });
    },
  };
}

export function createSource({
  fetchImpl = globalThis.fetch,
  timeoutMs = REQUEST_TIMEOUT_MS,
  sources = API_SOURCES,
  preferred = "",
  onOriginChange = null,
} = {}) {
  const racer = createRacer({ fetchImpl, timeoutMs, sources, preferredId: preferred, onOriginChange });
  const get = (path, options = {}) => racer.get(path, options);

  /**
   * Which instance is answering, and what the last request saw on each host.
   * `main.js` draws this on Settings and inside a failure, so a visitor can tell
   * a dead instance from a dead network.
   */
  const status = () => ({
    origin: racer.origin,
    origins: racer.origins,
    attempts: racer.attempts,
  });

  return {
    /** Search rows of one kind. `kind` is a view concern, not an API one. */
    async search(kind, query, page = 0, options = {}) {
      const trimmed = query.trim();
      if (!trimmed) return [];
      const path =
        kind === "albums"
          ? ENDPOINTS.searchAlbums(trimmed, page)
          : kind === "artists"
            ? ENDPOINTS.searchArtists(trimmed)
            : kind === "playlists"
              ? ENDPOINTS.searchPlaylists(trimmed, page)
              : ENDPOINTS.searchSongs(trimmed, page);
      const data = await get(path, options);
      const rows = resultRows(data, path);
      if (kind === "albums") return rows.map(normaliseAlbum).filter(Boolean);
      if (kind === "artists") return rows.map(normaliseArtist).filter(Boolean);
      if (kind === "playlists") return rows.map(normalisePlaylist).filter(Boolean);
      return rows.map(normaliseSong).filter(Boolean);
    },

    async album(id, options = {}) {
      const data = await get(ENDPOINTS.album(id), options);
      const album = normaliseAlbum(Array.isArray(data) ? data[0] : data);
      if (!album) throw new SourceError(FAILURE_KIND.shape, "the album carried no record", { url: ENDPOINTS.album(id) });
      return album;
    },

    async playlist(id, options = {}) {
      const data = await get(ENDPOINTS.playlist(id), options);
      const playlist = normalisePlaylist(Array.isArray(data) ? data[0] : data);
      if (!playlist) throw new SourceError(FAILURE_KIND.shape, "the playlist carried no record", { url: ENDPOINTS.playlist(id) });
      return playlist;
    },

    async artist(id, options = {}) {
      const [profile, songs, albums] = await Promise.all([
        get(ENDPOINTS.artist(id), options),
        get(ENDPOINTS.artistSongs(id), options).catch(() => ({ results: [] })),
        get(ENDPOINTS.artistAlbums(id), options).catch(() => []),
      ]);
      const artist = normaliseArtist(Array.isArray(profile) ? profile[0] : profile);
      if (!artist) throw new SourceError(FAILURE_KIND.shape, "the artist carried no record", { url: ENDPOINTS.artist(id) });
      return {
        artist,
        songs: songsFrom(songs, ENDPOINTS.artistSongs(id)).slice(0, 30),
        albums: (Array.isArray(albums) ? albums : []).map(normaliseAlbum).filter(Boolean),
      };
    },

    async song(id, options = {}) {
      const data = await get(ENDPOINTS.song(id), options);
      const song = normaliseSong(Array.isArray(data) ? data[0] : data);
      if (!song) throw new SourceError(FAILURE_KIND.shape, "the song carried no record", { url: ENDPOINTS.song(id) });
      return song;
    },

    /** "You might also like" — the API's own suggestion list for a track. */
    async suggestions(id, options = {}) {
      const data = await get(ENDPOINTS.suggestions(id), options);
      return songsFrom(data, ENDPOINTS.suggestions(id)).slice(0, 12);
    },

    /** Hydrate saved ids back into rows (likes and history store ids only). */
    async songsByIds(ids, options = {}) {
      const wanted = [...new Set(ids.filter(Boolean))].slice(0, 50);
      if (!wanted.length) return [];
      const data = await get(ENDPOINTS.songsById(wanted), options);
      const byId = new Map(songsFrom(data, ENDPOINTS.songsById(wanted)).map((song) => [song.id, song]));
      return wanted.map((id) => byId.get(String(id))).filter(Boolean);
    },

    /** Which instance is answering, and what the last request saw on each host. */
    status,
  };
}

/**
 * The home rails.
 *
 * JioSaavn has no "home feed" endpoint in the public wrapper; what it has is a
 * search that is very good at returning a shelf when the query is a mood or a
 * name. So the feed is a curated set of queries, run in parallel, each becoming
 * a rail. That is a real answer from the catalogue, not a hard-coded list of
 * rows pretending to be recommendations.
 */
export const HOME_RAILS = Object.freeze([
  { id: "trending", title: "Trending now", query: "trending hits", kind: "songs" },
  { id: "hindi", title: "Hindi favourites", query: "hindi hits", kind: "songs" },
  { id: "chill", title: "Chill & lo-fi", query: "lofi chill", kind: "songs" },
  { id: "punjabi", title: "Punjabi", query: "punjabi hits", kind: "songs" },
  { id: "albums", title: "New albums", query: "new releases", kind: "albums" },
  { id: "artists", title: "Artists to know", query: "arijit singh", kind: "artists" },
  { id: "playlists", title: "Playlists", query: "romantic", kind: "playlists" },
]);

/** A greeting from the device's own clock, the way the app's Home does. */
export function greetingFor(hour) {
  if (hour < 5) return "Still up?";
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  if (hour < 21) return "Good evening";
  return "Good night";
}
