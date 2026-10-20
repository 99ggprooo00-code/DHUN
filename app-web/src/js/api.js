/**
 * The source seam: JioSaavn's public catalogue, read from the browser.
 *
 * WHY THIS SOURCE. The web build used to talk to YouTube Music through
 * InnerTube. That endpoint refuses a browser's player request often enough that
 * the app spent most of its life showing an error it could not act on, and no
 * amount of client code changes what the origin allows. JioSaavn publishes
 * ordinary HTTPS audio and cover URLs that a browser can fetch directly, which
 * is the difference between "a player with an error state" and "a player".
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
 * WHAT IT DOES NOT DO. It sends no identifier, stores nothing, and calls no
 * DHUN server — there is no DHUN server. The request goes from this tab to the
 * catalogue the visitor's browser can already reach.
 *
 * THE API IS UNOFFICIAL. `saavn.dev` is a community wrapper around JioSaavn, not
 * an official endpoint with a support contract. That is the honest limit of this
 * source and the app says so in Settings and About; the failure taxonomy below
 * is what makes the limit visible instead of mysterious when it bites.
 */

export const API_ORIGIN = "https://saavn.dev";
export const API_BASE = `${API_ORIGIN}/api`;

/** How long a request may take before the interface gives up and says so. */
export const REQUEST_TIMEOUT_MS = 15000;

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
  constructor(kind, message, { status = null, url = "", cause = null } = {}) {
    super(message);
    this.name = "SourceError";
    this.kind = kind;
    this.status = status;
    this.url = url;
    this.cause = cause;
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
    default:
      return {
        title: "The catalogue could not be reached",
        body: "The request never completed. An extension, a proxy or the network may be blocking it.",
        technical: `${error.message}${error.url ? ` — ${error.url}` : ""}`,
        kind: error.kind,
      };
  }
}

/* --------------------------------------------------------------- requests -- */

function absolute(path) {
  return path.startsWith("http") ? path : `${API_BASE}${path}`;
}

/**
 * One GET, with a timeout that actually cancels.
 *
 * `AbortSignal.timeout` is not in every browser this app supports, so the
 * controller is explicit. A request that outlives its timeout is aborted rather
 * than left to land on a screen that has already moved on.
 */
export async function requestJson(path, { fetchImpl = globalThis.fetch, timeoutMs = REQUEST_TIMEOUT_MS, signal } = {}) {
  const url = absolute(path);
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
    .map((artist) => ({ id: plainText(artist?.id), name: plainText(artist?.name), image: pickImage(artist?.image, 150) }))
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
    image: pickImage(raw.image, 500),
    imageSmall: pickImage(raw.image, 150),
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
      image: pickImage(artist?.image, 150),
    })).filter((artist) => artist.name),
    year: plainText(raw.year ?? ""),
    language: plainText(raw.language ?? ""),
    songCount: Number(raw.songCount ?? songs.length) || songs.length,
    duration: songs.reduce((total, song) => total + (song.duration || 0), 0),
    image: pickImage(raw.image, 500),
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
    image: pickImage(raw.image, 500),
    imageSmall: pickImage(raw.image, 150),
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
    image: pickImage(raw.image, 500),
    songs,
    url: plainText(raw.url ?? ""),
  };
}

/* ---------------------------------------------------------------- reading -- */

/** One list of songs, normalised, with rows that did not parse dropped. */
function songsFrom(data, url) {
  return resultRows(data, url).map(normaliseSong).filter(Boolean);
}

export function createSource({ fetchImpl = globalThis.fetch, timeoutMs = REQUEST_TIMEOUT_MS } = {}) {
  const get = (path, options = {}) => requestJson(path, { fetchImpl, timeoutMs, ...options });

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
