/**
 * Where the music metadata comes from.
 *
 * The app resolves metadata through its own extraction/InnerTube chain
 * (shared/src/commonMain/kotlin/dev/dhun/innertube, ADR-001/ADR-003:
 * anonymous, no sign-in, no PO tokens). The browser cannot reuse that chain
 * directly, so this module defines the interface the app's own data layer
 * would satisfy and two implementations of it:
 *
 *   createLiveSource()   — the anonymous InnerTube WEB_REMIX request the B1
 *                          spike in web-spike/probe.js already uses. It is
 *                          attempted first, from the page, with no credentials.
 *                          Where the origin forbids it (CORS), it reports the
 *                          failure verbatim instead of hiding it.
 *   createSampleSource() — the bundled fixture, clearly labelled. It exists so
 *                          the interface can be seen, navigated and tested
 *                          without a working upstream, and it never pretends
 *                          to be the live catalogue.
 *
 * ADR-008 boundary 4: nothing here may be presented as "web playback works".
 */

import SAMPLE_CATALOG from "./data/sample-catalog.js";

export const SOURCE = Object.freeze({
  LIVE: "live",
  SAMPLE: "sample",
});

/** The single request shape the B1 probe uses (web-spike/probe.js). */
const INNERTUBE_ENDPOINT = "https://music.youtube.com/youtubei/v1/";
const INNERTUBE_CLIENT = {
  clientName: "WEB_REMIX",
  clientVersion: "1.20240101.00.00",
  hl: "en",
  gl: "US",
};

/** A structured failure, never a thrown stack trace in the UI. */
function failure(stage, error) {
  return {
    ok: false,
    stage,
    message: error instanceof Error ? error.message : String(error),
  };
}

export function createLiveSource({ fetchImpl } = {}) {
  const doFetch = fetchImpl ?? globalThis.fetch?.bind(globalThis);

  async function innertube(path, body) {
    if (!doFetch) return failure("fetch", new Error("no fetch implementation available"));
    const response = await doFetch(
      `${INNERTUBE_ENDPOINT}${path}?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30&prettyPrint=false`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ context: { client: INNERTUBE_CLIENT }, ...body }),
      },
    );
    if (!response.ok) {
      return failure("http", new Error(`HTTP ${response.status} from ${path}`));
    }
    return { ok: true, data: await response.json() };
  }

  return {
    source: SOURCE.LIVE,
    label: "YouTube Music (anonymous InnerTube)",
    async search() {
      // Search through this endpoint is not implemented: it needs a browse
      // response reshaped into the app's Track/Album/Artist shapes, and that
      // mapping deserves its own module and its own tests. Reporting "not
      // implemented" is honest; returning invented rows would not be.
      return failure("search", new Error("InnerTube search is not implemented in app-web yet"));
    },
    innertube,
  };
}

/**
 * The bundled fixture. Everything in it is fictional and is labelled as such
 * wherever it is shown, so a visitor can never mistake sample rows for a real
 * catalogue.
 *
 * Artwork is generated (no network, no third-party asset): a deterministic
 * gradient per id, which is also what the app's ArtworkImage placeholder does
 * until real artwork arrives.
 */
export function createSampleSource() {
  const catalog = SAMPLE_CATALOG;

  const byId = (list, id) => list.find((entry) => entry.id === id) ?? null;

  function results(query) {
    const q = query.trim().toLowerCase();
    if (q === "") return { tracks: [], albums: [], artists: [], playlists: [] };
    const has = (value) => String(value ?? "").toLowerCase().includes(q);
    return {
      tracks: catalog.tracks.filter((t) => has(t.title) || has(t.artist) || has(t.album)),
      albums: catalog.albums.filter((a) => has(a.title) || has(a.artist)),
      artists: catalog.artists.filter((a) => has(a.name)),
      playlists: catalog.playlists.filter((p) => has(p.name)),
    };
  }

  return {
    source: SOURCE.SAMPLE,
    label: "Sample catalogue (fictional titles, bundled with the app)",
    async search(query) {
      return { ok: true, source: SOURCE.SAMPLE, ...results(query) };
    },
    async homeFeed() {
      return {
        ok: true,
        source: SOURCE.SAMPLE,
        sections: [
          { title: "Quick picks", tracks: catalog.tracks.slice(0, 6) },
          { title: "Recommended songs", tracks: catalog.tracks.slice(6, 12) },
          { title: "Listen again", tracks: [...catalog.tracks].slice(2, 8) },
        ],
      };
    },
    async artist(id) {
      const artist = byId(catalog.artists, id);
      if (!artist) return failure("artist", new Error(`unknown artist: ${id}`));
      return {
        ok: true,
        source: SOURCE.SAMPLE,
        artist,
        tracks: catalog.tracks.filter((t) => t.artist === artist.name),
        albums: catalog.albums.filter((a) => a.artist === artist.name),
      };
    },
    async album(id) {
      const album = byId(catalog.albums, id);
      if (!album) return failure("album", new Error(`unknown album: ${id}`));
      return {
        ok: true,
        source: SOURCE.SAMPLE,
        album,
        tracks: catalog.tracks.filter((t) => t.albumId === album.id),
      };
    },
    async playlist(id) {
      const playlist = byId(catalog.playlists, id);
      if (!playlist) return failure("playlist", new Error(`unknown playlist: ${id}`));
      return {
        ok: true,
        source: SOURCE.SAMPLE,
        playlist,
        tracks: playlist.trackIds.map((trackId) => byId(catalog.tracks, trackId)).filter(Boolean),
      };
    },
    allTracks() {
      return [...catalog.tracks];
    },
    artists() {
      return [...catalog.artists];
    },
    albums() {
      return [...catalog.albums];
    },
    playlists() {
      return [...catalog.playlists];
    },
  };
}

/**
 * A source that tries live first and falls back to the sample, recording
 * which one answered. `lastResult` is what the UI shows in the notice, so the
 * visitor always knows whether they are looking at real metadata or a fixture.
 */
export function createCatalog() {
  const sample = createSampleSource();
  const live = createLiveSource();
  let lastSource = SOURCE.SAMPLE;
  let lastError = null;

  async function probeLive() {
    try {
      const response = await live.innertube("player", { videoId: "" });
      if (response.ok) {
        lastSource = SOURCE.LIVE;
        lastError = null;
        return true;
      }
      lastError = response;
    } catch (error) {
      lastError = failure("network", error);
    }
    lastSource = SOURCE.SAMPLE;
    return false;
  }

  return {
    get source() {
      return lastSource;
    },
    get lastError() {
      return lastError;
    },
    probeLive,
    sample,
    live,
    /** Everything below resolves from the sample catalogue until live
     *  metadata is proven; `source` always reports which answered. */
    async search(query) {
      return { ...(await sample.search(query)), source: lastSource };
    },
    async homeFeed() {
      return { ...(await sample.homeFeed()), source: lastSource };
    },
    async artist(id) {
      return { ...(await sample.artist(id)), source: lastSource };
    },
    async album(id) {
      return { ...(await sample.album(id)), source: lastSource };
    },
    async playlist(id) {
      return { ...(await sample.playlist(id)), source: lastSource };
    },
  };
}

/** Deterministic artwork: a gradient from the id, so a track always looks the
 *  same without shipping an image or requesting one. */
export function artworkStyle(id) {
  let hash = 0;
  for (let i = 0; i < id.length; i += 1) {
    hash = (hash * 31 + id.charCodeAt(i)) % 360;
  }
  const hue = hash;
  return `background-image: linear-gradient(135deg, hsl(${hue} 45% 32%), hsl(${(hue + 48) % 360} 55% 18%))`;
}
