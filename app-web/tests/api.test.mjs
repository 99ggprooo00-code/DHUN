/**
 * The catalogue seam.
 *
 * Two things are asserted here and nothing else: the normalisers turn the API's
 * shape into the shape the screens draw, and every way a request can fail
 * becomes an honest, specific error rather than "Something went wrong". The
 * tests run against a fake `fetch`, so no network is needed — but the JSON in
 * them is copied from the real API's field names, because a normaliser tested
 * against an invented contract proves nothing.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  API_BASE,
  API_ORIGIN,
  ENDPOINTS,
  FAILURE_KIND,
  SourceError,
  artistNames,
  createSource,
  describeFailure,
  normaliseAlbum,
  normaliseArtist,
  normalisePlaylist,
  normaliseSong,
  pickImage,
  pickStream,
  plainText,
  urlOf,
} from "../src/js/api.js";

const songJson = {
  id: "abc123",
  name: "Monsoon Over Dharan &amp; Rain",
  type: "song",
  year: "2024",
  duration: 227,
  label: "",
  explicitContent: false,
  playCount: 12345,
  language: "nepali",
  hasLyrics: true,
  lyricsId: null,
  url: "https://www.jiosaavn.com/song/x",
  copyright: "",
  album: { id: "al1", name: "Dharan Nights", url: "" },
  artists: {
    primary: [{ id: "ar1", name: "Naya Koirala", role: "", image: [], type: "artist", url: "" }],
    featured: [],
    all: [{ id: "ar1", name: "Naya Koirala" }],
  },
  image: [
    { quality: "50x50", url: "https://a.saavncdn.com/x-50.jpg" },
    { quality: "500x500", url: "https://a.saavncdn.com/x-500.jpg" },
  ],
  downloadUrl: [
    { quality: "12kbps", url: "https://a.saavncdn.com/x-12.m4a" },
    { quality: "320kbps", url: "https://a.saavncdn.com/x-320.m4a" },
  ],
};

test("the API origin is a public service, never a DHUN backend", () => {
  assert.equal(API_ORIGIN, "https://saavn.dev");
  assert.equal(API_BASE, "https://saavn.dev/api");
  assert.equal(ENDPOINTS.searchSongs("a b", 0), "/search/songs?query=a%20b&page=0&limit=20");
  assert.equal(ENDPOINTS.song("x/../y"), "/songs/x%2F..%2Fy", "an id cannot climb out of its own path");
});

test("a song is normalised to the row shape the screens draw", () => {
  const song = normaliseSong(songJson);
  assert.equal(song.id, "abc123");
  assert.equal(song.title, "Monsoon Over Dharan & Rain", "the API's HTML entities are decoded");
  assert.equal(song.artist, "Naya Koirala");
  assert.equal(song.duration, 227, "durations stay in seconds");
  assert.equal(song.image, "https://a.saavncdn.com/x-500.jpg", "the largest cover is chosen");
  assert.equal(song.imageSmall, "https://a.saavncdn.com/x-500.jpg", "the smallest cover that is big enough for a row");
  assert.equal(song.stream, "https://a.saavncdn.com/x-320.m4a", "the best bitrate is the stream");
  assert.equal(song.streamQuality, "320kbps");
  assert.equal(song.hasLyrics, true);
  assert.equal(song.explicit, false);
  assert.equal(song.kind, "song");
});

test("a song that is already a normalised row is not mangled", () => {
  const once = normaliseSong(songJson);
  const twice = normaliseSong(once);
  assert.deepEqual(twice, once);
});

test("a row without an id is not a row", () => {
  assert.equal(normaliseSong({ name: "nameless" }), null);
  assert.equal(normaliseSong(null), null);
  assert.equal(normaliseSong({ id: "x" }).title, "Untitled", "a nameless row still gets a title, not 'undefined'");
});

test("a song with no audio says so instead of inventing a URL", () => {
  const song = normaliseSong({ ...songJson, downloadUrl: [] });
  assert.equal(song.stream, "");
  assert.equal(song.streamQuality, "");
});

test("the second bitrate is not the last one", () => {
  assert.equal(
    pickStream([
      { quality: "12kbps", url: "a" },
      { quality: "96kbps", url: "b" },
      { quality: "320kbps", url: "c" },
    ]).url,
    "c",
  );
  assert.equal(pickStream([]).url, "", "a track with no audio yields no URL, not a guess");
  assert.equal(pickStream(undefined).url, "");
});

test("images pick the closest quality to what the layout needs", () => {
  const images = [
    { quality: "50x50", url: "small" },
    { quality: "150x150", url: "medium" },
    { quality: "500x500", url: "large" },
  ];
  assert.equal(pickImage(images, 500), "large");
  assert.equal(pickImage(images, 50), "small");
  assert.equal(pickImage(images, 4000), "large", "when nothing is big enough, the biggest is the answer");
  assert.equal(pickImage([{ quality: "150x150", url: "medium" }, { quality: "500x500", url: "large" }], 500), "large",
    "the API names the size in `quality`, and the picker has to read it");
  assert.equal(urlOf("https://x/y.jpg"), "https://x/y.jpg");
  assert.equal(urlOf({ link: "https://x/z.jpg" }), "https://x/z.jpg", "the older field name is still read");
  assert.equal(urlOf(undefined), "");
});

test("albums, artists and playlists come back with their rows", () => {
  const album = normaliseAlbum({
    id: "al1",
    name: "Dharan Nights",
    year: "2024",
    songCount: 2,
    songs: [songJson, { ...songJson, id: "def456" }],
    artists: { primary: [{ id: "ar1", name: "Naya Koirala" }], all: [] },
    image: [{ quality: "500x500", url: "https://a.saavncdn.com/al.jpg" }],
    url: "",
  });
  assert.equal(album.title, "Dharan Nights");
  assert.equal(album.kind, "album");
  assert.equal(album.artist, "Naya Koirala");
  assert.equal(album.songs.length, 2);

  const artist = normaliseArtist({
    id: "ar1",
    name: "Naya Koirala",
    fanCount: "12000",
    image: [{ quality: "500x500", url: "https://a.saavncdn.com/ar.jpg" }],
  });
  assert.equal(artist.title, "Naya Koirala");
  assert.equal(artist.kind, "artist");

  const playlist = normalisePlaylist({
    id: "pl1",
    name: "Late night",
    songCount: 1,
    songs: [songJson],
    image: [{ quality: "500x500", url: "https://a.saavncdn.com/pl.jpg" }],
  });
  assert.equal(playlist.title, "Late night");
  assert.equal(playlist.kind, "playlist");
  assert.equal(playlist.songs.length, 1);
});

test("plainText decodes the entities the catalogue actually sends", () => {
  assert.equal(plainText("Tom &amp; Jerry"), "Tom & Jerry");
  assert.equal(plainText("&quot;quoted&quot;"), '"quoted"');
  assert.equal(plainText("&#39;one&#39;"), "'one'");
  assert.equal(plainText(undefined), "");
});

test("artistNames joins the credited artists without duplicates", () => {
  assert.equal(
    artistNames({ artists: { primary: [{ name: "A" }, { name: "A" }], featured: [{ name: "B" }], all: [] } }),
    "A, B",
  );
  assert.equal(artistNames({ artists: { primary: [], featured: [], all: [{ name: "C" }] } }), "C");
  assert.equal(artistNames({}), "");
});

/* ------------------------------------------------------------- failures -- */

test("every failure kind is described in the network's own terms", () => {
  const titles = new Set();
  for (const kind of Object.values(FAILURE_KIND)) {
    const described = describeFailure(
      new SourceError(kind, "raw message", { status: 503, url: "https://saavn.dev/api/x" }),
    );
    assert.ok(described.title.length > 4, `${kind} has no title`);
    assert.ok(described.body.length > 10, `${kind} has no explanation`);
    assert.equal(described.kind, kind);
    titles.add(described.title);
  }
  assert.equal(titles.size, Object.keys(FAILURE_KIND).length, "two failure kinds describe themselves identically");
  // The evidence a person can copy: the URL, the status, the element's message.
  assert.ok(describeFailure(new SourceError(FAILURE_KIND.http, "", { status: 503, url: "https://saavn.dev/api/x" })).technical.includes("503"));
  assert.ok(describeFailure(new SourceError(FAILURE_KIND.shape, "no record", { url: "u" })).technical.includes("no record"));
  assert.ok(describeFailure(new SourceError(FAILURE_KIND.network, "Failed to fetch")).technical.includes("Failed to fetch"));
});

test("the four failures a visitor meets read differently", () => {
  const offline = describeFailure(new SourceError(FAILURE_KIND.offline, "offline"));
  const timeout = describeFailure(new SourceError(FAILURE_KIND.timeout, "timeout"));
  const http = describeFailure(new SourceError(FAILURE_KIND.http, "500", { status: 500 }));
  const shape = describeFailure(new SourceError(FAILURE_KIND.shape, "no results"));
  const titles = new Set([offline.title, timeout.title, http.title, shape.title]);
  assert.equal(titles.size, 4, "two failures share a message, so one of them is wrong");
  assert.ok(http.title.includes("500"), "an HTTP failure names the status");
});

test("a plain Error is still described rather than swallowed", () => {
  const described = describeFailure(new Error("boom"));
  assert.ok(described.title);
  assert.ok(described.technical.includes("boom"));
  assert.equal(describeFailure(null).kind, FAILURE_KIND.network);
});

/* --------------------------------------------------------------- source -- */

function fakeFetch({ json, status = 200, reject = null, onCall } = {}) {
  return async (url, options) => {
    onCall?.(url, options);
    if (reject) throw reject;
    return {
      ok: status >= 200 && status < 300,
      status,
      async json() {
        if (typeof json === "function") return json(url);
        return json;
      },
    };
  };
}

test("a search asks the endpoint for its kind and returns rows", async () => {
  const calls = [];
  const source = createSource({
    fetchImpl: fakeFetch({
      json: { data: { results: [songJson] } },
      onCall: (url) => calls.push(url),
    }),
  });
  const rows = await source.search("songs", "  monsoon  ");
  assert.equal(rows.length, 1);
  assert.ok(calls[0].includes("/search/songs"));
  assert.ok(calls[0].includes("query=monsoon"), "the query is trimmed");
});

test("an empty query is not a request", async () => {
  let called = 0;
  const source = createSource({ fetchImpl: fakeFetch({ json: { data: { results: [] } }, onCall: () => (called += 1) }) });
  assert.deepEqual(await source.search("songs", "   "), []);
  assert.equal(called, 0, "an empty query would send the visitor's blank to the API");
});

test("an HTTP error is a SourceError of kind http, with the status", async () => {
  const source = createSource({ fetchImpl: fakeFetch({ json: {}, status: 500 }) });
  await assert.rejects(
    () => source.search("songs", "x"),
    (error) => {
      assert.ok(error instanceof SourceError);
      assert.equal(error.kind, FAILURE_KIND.http);
      assert.equal(error.status, 500);
      return true;
    },
  );
});

test("a dropped connection is kind network, not kind http", async () => {
  const source = createSource({ fetchImpl: fakeFetch({ reject: new TypeError("Failed to fetch") }) });
  await assert.rejects(
    () => source.search("songs", "x"),
    (error) => error.kind === FAILURE_KIND.offline || error.kind === FAILURE_KIND.network,
  );
});

test("an answer that is not the documented shape is kind shape", async () => {
  const source = createSource({ fetchImpl: fakeFetch({ json: { unexpected: true } }) });
  await assert.rejects(
    () => source.album("al1"),
    (error) => error.kind === FAILURE_KIND.shape,
  );
});

test("a request that never answers times out rather than hanging forever", async () => {
  const source = createSource({
    timeoutMs: 20,
    fetchImpl: (url, { signal }) =>
      new Promise((resolve, reject) => {
        signal.addEventListener("abort", () => reject(Object.assign(new Error("aborted"), { name: "AbortError" })));
      }),
  });
  await assert.rejects(
    () => source.search("songs", "x"),
    (error) => error.kind === FAILURE_KIND.timeout,
  );
});
