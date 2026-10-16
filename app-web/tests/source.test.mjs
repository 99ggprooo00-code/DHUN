/**
 * The data layer, against captured shape fixtures.
 *
 * Mapping somebody else's JSON into your own shapes is where a client silently
 * breaks: the request succeeds, the response changes, and the screen fills with
 * empty rows. So every mapper is a pure function tested here against a
 * representative response, and the failure paths are tested as hard as the happy
 * ones — a `shape` failure that names what was missing is the difference between
 * a five-minute fix and a week of guessing.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  parseDuration,
  browseKind,
  mapSearch,
  mapHome,
  mapBrowse,
  mapPlayer,
  mapResponsiveItem,
  pickAudioFormat,
  failure,
  failureDetail,
  failureHelp,
  failureSummary,
  isRefusal,
  stateError,
  createDemoSource,
  createInnertubeSource,
  createEndpointSource,
  createSourceManager,
  artworkInfo,
  walkJson,
  findRenderer,
  SOURCE_KIND,
} from "../src/js/source.js";

/** A responsive list item, the renderer every list page uses. */
function item({ title, subtitle = [], videoId = null, browseId = null, kind = "track" }) {
  const runs = [{ text: title }];
  if (browseId) {
    runs[0].navigationEndpoint =
      kind === "track"
        ? { watchEndpoint: { videoId: browseId } }
        : { browseEndpoint: { browseId } };
  }
  // Real responses interleave the separator as its own run, which is why the
  // mapper sees one string and has to split it. A helper that glued the parts
  // together would test a response shape that never arrives.
  const subtitleRuns = [];
  subtitle.forEach((text, index) => {
    if (index > 0) subtitleRuns.push({ text: " • " });
    subtitleRuns.push({ text });
  });
  return {
    musicResponsiveListItemRenderer: {
      playlistItemData: videoId ? { videoId } : undefined,
      flexColumns: [
        { musicResponsiveListItemFlexColumnRenderer: { text: { runs } } },
        {
          musicResponsiveListItemFlexColumnRenderer: {
            text: { runs: subtitleRuns },
          },
        },
      ],
      thumbnail: {
        musicThumbnailRenderer: {
          thumbnail: { thumbnails: [{ url: "https://lh3.googleusercontent.com/abc=w60-h60", width: 60 }] },
        },
      },
    },
  };
}

const response = (items) => ({
  contents: { musicShelfRenderer: { contents: items } },
});

test("durations parse in both shapes a music service uses", () => {
  assert.equal(parseDuration("4:02"), 242_000);
  assert.equal(parseDuration("1:02:33"), 3_753_000);
  assert.equal(parseDuration("0:07"), 7_000);
  assert.equal(parseDuration(""), null);
  assert.equal(parseDuration("not a time"), null);
  assert.equal(parseDuration(null), null);
});

test("an id says what kind of thing it points at", () => {
  assert.equal(browseKind("MPREb_abc123"), "album");
  assert.equal(browseKind("VLPLabc"), "playlist");
  assert.equal(browseKind("UCabc123"), "artist");
  assert.equal(browseKind("FEmusic_library_albums"), "artist");
  assert.equal(browseKind("somethingElse"), "unknown");
  assert.equal(browseKind(null), "unknown");
});

test("a track row maps title, artist, album and duration", () => {
  const mapped = mapResponsiveItem(
    item({ title: "Monsoon Over Dharan", subtitle: ["Naya Koirala", "Dharan Nights", "3:47"], videoId: "vid-1" }).musicResponsiveListItemRenderer,
  );
  assert.equal(mapped.kind, "track");
  assert.equal(mapped.id, "vid-1");
  assert.equal(mapped.title, "Monsoon Over Dharan");
  assert.equal(mapped.artist, "Naya Koirala");
  assert.equal(mapped.album, "Dharan Nights");
  assert.equal(mapped.durationMs, 227_000);
  assert.match(mapped.artwork, /^https:\/\//);
  assert.match(mapped.artwork, /=w256-h256/, "artwork is resized to the row, not the original 544px");
});

test("a row that points at an album, artist or playlist maps to that thing", () => {
  const album = mapResponsiveItem(
    item({ title: "Lowland Echoes", subtitle: ["Bhanu & the Lowlands", "2025"], browseId: "MPREb_x", kind: "entity" })
      .musicResponsiveListItemRenderer,
  );
  assert.deepEqual({ kind: album.kind, title: album.title, year: album.year }, { kind: "album", title: "Lowland Echoes", year: "2025" });

  const artist = mapResponsiveItem(
    item({ title: "Naya Koirala", subtitle: ["Artist"], browseId: "UCabc", kind: "entity" }).musicResponsiveListItemRenderer,
  );
  assert.equal(artist.kind, "artist");

  const playlist = mapResponsiveItem(
    item({ title: "Road trip", subtitle: ["12 songs"], browseId: "VLPL1", kind: "entity" }).musicResponsiveListItemRenderer,
  );
  assert.equal(playlist.kind, "playlist");
  assert.equal(playlist.trackCount, 12);
});

test("search groups its results by kind", () => {
  const mapped = mapSearch(
    response([
      item({ title: "A song", subtitle: ["Artist", "Album", "3:00"], videoId: "v1" }),
      item({ title: "An album", subtitle: ["Artist", "2024"], browseId: "MPREb_1", kind: "entity" }),
      item({ title: "An artist", subtitle: ["Artist"], browseId: "UC1", kind: "entity" }),
    ]),
  );
  assert.equal(mapped.ok, true);
  assert.deepEqual(mapped.tracks.map((entry) => entry.title), ["A song"]);
  assert.deepEqual(mapped.albums.map((entry) => entry.title), ["An album"]);
  assert.deepEqual(mapped.artists.map((entry) => entry.name), ["An artist"]);
});

test("a genuinely empty search is not reported as a broken response", () => {
  const empty = mapSearch({ contents: { musicShelfRenderer: { contents: [] } } });
  assert.equal(empty.ok, true);
  assert.deepEqual(empty.tracks, []);

  const unrecognised = mapSearch({ unexpected: { shape: true } });
  assert.equal(unrecognised.ok, false);
  assert.equal(unrecognised.kind, "shape");
  assert.match(unrecognised.message, /no list renderers/);
});

test("the home feed keeps its shelves in order and drops nested duplicates", () => {
  const mapped = mapHome({
    contents: {
      musicCarouselShelfRenderer: {
        header: { musicCarouselShelfBasicHeaderRenderer: { title: { runs: [{ text: "Quick picks" }] } } },
        contents: [item({ title: "One", subtitle: ["Artist", "Album", "3:00"], videoId: "v1" })],
      },
    },
  });
  assert.equal(mapped.ok, true);
  assert.equal(mapped.shelves.length, 1);
  assert.equal(mapped.shelves[0].title, "Quick picks");
  assert.equal(mapped.shelves[0].entries[0].title, "One");
});

test("an album page maps its header and tracklist", () => {
  const mapped = mapBrowse(
    {
      header: {
        musicDetailHeaderRenderer: {
          title: { runs: [{ text: "Lowland Echoes" }] },
          subtitle: { runs: [{ text: "Bhanu & the Lowlands" }, { text: " • " }, { text: "2025" }] },
          thumbnail: { musicThumbnailRenderer: { thumbnail: { thumbnails: [{ url: "https://x.test/a=w120", width: 120 }] } } },
        },
      },
      contents: response([
        item({ title: "Lowland Echo", subtitle: ["Bhanu & the Lowlands", "Lowland Echoes", "4:34"], videoId: "v1" }),
        item({ title: "Tea Stall Radio", subtitle: ["Bhanu & the Lowlands", "Lowland Echoes", "3:21"], videoId: "v2" }),
      ]),
    },
    { kind: "album", browseId: "MPREb_x" },
  );
  assert.equal(mapped.ok, true);
  assert.equal(mapped.kind, "album");
  assert.equal(mapped.album.title, "Lowland Echoes");
  assert.equal(mapped.album.artist, "Bhanu & the Lowlands");
  assert.equal(mapped.album.year, "2025");
  assert.deepEqual(mapped.tracks.map((track) => track.id), ["v1", "v2"]);
});

test("an artist page keeps its songs and its albums apart", () => {
  const mapped = mapBrowse(
    {
      header: { musicImmersiveHeaderRenderer: { title: { runs: [{ text: "Naya Koirala" }] } } },
      shelves: [
        response([item({ title: "Bijeuli", subtitle: ["Naya Koirala", "Dharan Nights", "3:14"], videoId: "v1" })]),
        {
          musicCarouselShelfRenderer: {
            contents: [
              {
                musicTwoRowItemRenderer: {
                  title: { runs: [{ text: "Dharan Nights", navigationEndpoint: { browseEndpoint: { browseId: "MPREb_1" } } }] },
                  subtitle: { runs: [{ text: "2024" }] },
                },
              },
            ],
          },
        },
      ],
    },
    { kind: "artist", browseId: "UCabc" },
  );
  assert.equal(mapped.kind, "artist");
  assert.equal(mapped.artist.name, "Naya Koirala");
  assert.equal(mapped.tracks.length, 1);
  assert.equal(mapped.albums.length, 1);
});

test("a response with neither a header nor a tracklist is a named failure", () => {
  const mapped = mapBrowse({}, { kind: "album", browseId: "MPREb_x" });
  assert.equal(mapped.ok, false);
  assert.equal(mapped.kind, "shape");
});

test("stream selection prefers audio-only, then muxed, and never invents a URL", () => {
  const audio = pickAudioFormat({
    adaptiveFormats: [
      { itag: 251, mimeType: "audio/webm", bitrate: 130_000, url: "https://cdn.test/a" },
      { itag: 140, mimeType: "audio/mp4", bitrate: 128_000, url: "https://cdn.test/b" },
      { itag: 137, mimeType: "video/mp4", bitrate: 2_000_000, url: "https://cdn.test/v" },
    ],
    formats: [{ itag: 18, mimeType: "video/mp4", bitrate: 500_000, url: "https://cdn.test/mux" }],
  });
  assert.equal(audio.ok, true);
  assert.equal(audio.itag, 251, "the highest-bitrate audio-only format wins");
  assert.equal(audio.crossOrigin, true);

  const muxOnly = pickAudioFormat({ formats: [{ itag: 18, mimeType: "video/mp4", url: "https://cdn.test/mux" }] });
  assert.equal(muxOnly.ok, true);
  assert.equal(muxOnly.itag, 18);
});

test("a ciphered-only response is reported, not worked around", () => {
  const ciphered = pickAudioFormat({
    adaptiveFormats: [{ itag: 251, mimeType: "audio/webm", signatureCipher: "s=abc&url=https%3A%2F%2Fcdn.test%2Fa" }],
  });
  assert.equal(ciphered.ok, false);
  assert.equal(ciphered.kind, "cipher");
  assert.match(ciphered.message, /signature cipher/);
});

test("the player response becomes a track plus a stream, and a refusal is a named failure", () => {
  const good = mapPlayer({
    playabilityStatus: { status: "OK" },
    videoDetails: { videoId: "v1", title: "One", author: "Artist", lengthSeconds: "242" },
    streamingData: { adaptiveFormats: [{ itag: 251, mimeType: "audio/webm", url: "https://cdn.test/a" }] },
  });
  assert.equal(good.ok, true);
  assert.equal(good.track.title, "One");
  assert.equal(good.track.durationMs, 242_000);
  assert.equal(good.stream.itag, 251);

  const refused = mapPlayer({ playabilityStatus: { status: "UNPLAYABLE", reason: "Sign in to confirm you're not a bot" } });
  assert.equal(refused.ok, false);
  assert.equal(refused.kind, "playability");
  assert.match(refused.message, /Sign in to confirm/);
});

test("unplayable-but-known tracks still yield metadata with no stream", () => {
  const mapped = mapPlayer({
    playabilityStatus: { status: "UNPLAYABLE" },
    videoDetails: { videoId: "v1", title: "One", author: "Artist", lengthSeconds: "10" },
  });
  assert.equal(mapped.ok, false);
  assert.equal(mapped.track.title, "One", "the row can still be shown with its real title");
  assert.equal(mapped.kind, "nostream");
});

test("failures classify a refused cross-origin request distinctly", () => {
  const cors = failure("network", new TypeError("Failed to fetch"));
  assert.equal(cors.ok, false);
  assert.equal(cors.kind, "network-or-cors");
  assert.match(failureHelp(cors), /cross-origin/);
  assert.match(failureHelp(failure("http", new Error("HTTP 500"))), /server answered with an error/i);
  assert.match(failureHelp(failure("shape", new Error("x"))), /what it sends|upstream changed/);
});

/*
 * The interface's copy rules, and the reason they are tests rather than taste.
 *
 * A `fetch` rejection carries the browser's own words — "Failed to fetch" in
 * Chrome, "NetworkError when attempting to fetch resource." in Firefox, "Load
 * failed" in Safari — and this app used to print one of those as the headline of
 * a failed screen. It is the same sentence for a flat network, a DNS failure, an
 * ad-blocker and a real cross-origin refusal, and it names nothing a person can
 * do. These tests exist so that cannot come back.
 */

/** The three strings browsers use when a request never gets an answer. */
const BROWSER_WORDS = [
  "Failed to fetch",
  "NetworkError when attempting to fetch resource.",
  "Load failed",
];

test("a browser's own words are never the message a person reads", () => {
  for (const message of BROWSER_WORDS) {
    const result = failure("network", new TypeError(message));
    assert.equal(result.kind, "network-or-cors", message);
    const summary = failureSummary(result);
    assert.notEqual(summary, message);
    assert.ok(!summary.includes(message), `summary must not quote ${message}: ${summary}`);
    assert.match(summary, /could not reach the source/i);
    assert.ok(summary.length <= 120, `a summary is one line, not a paragraph: ${summary}`);
    assert.equal(isRefusal(result), true);
  }
});

test("the browser's string survives as a diagnostic, and the screen keeps it", () => {
  const result = failure("network", new TypeError("Failed to fetch"));
  assert.equal(failureDetail(result), "Failed to fetch");
  const error = stateError(result);
  assert.equal(error.help, failureHelp(result), "the screen's body is the app's sentence");
  assert.equal(error.technical, "Failed to fetch", "the raw string is kept, not thrown away");
  assert.equal(error.summary, failureSummary(result));
  assert.equal(stateError(null), null);
});

test("an HTTP status reaches the sentence and the path stays in the detail", () => {
  const result = failure("http", new Error("HTTP 502 from /youtubei/v1/player"));
  assert.equal(failureSummary(result), "The source answered with an error (HTTP 502).");
  assert.equal(failureDetail(result), "HTTP 502 from /youtubei/v1/player");
});

test("a failure that already speaks for itself is not replaced", () => {
  const demo = failure("demo", new Error("\"A fictional song\" is a fictional demo entry, so there is no audio to fetch"), { kind: "demo" });
  assert.equal(failureSummary(demo), "\"A fictional song\" is a fictional demo entry, so there is no audio to fetch");
  assert.equal(failureDetail(demo), null, "nothing to demote when the message is already a sentence");

  const refused = failure("playability", new Error("Video unavailable"), { kind: "playability" });
  assert.equal(failureSummary(refused), "Video unavailable", "the source's own reason beats a generic line");
});

test("every kind and stage this module can produce has copy, and the copy is not empty", () => {
  const kinds = [
    "network-or-cors",
    "timeout",
    "http",
    "shape",
    "fetch",
    "endpoint",
    "cipher",
    "nostream",
    "notfound",
    "playability",
    "demo",
    "search",
    "home",
    "browse",
    "stream",
    "resolve",
    "queue",
  ];
  for (const kind of kinds) {
    const result = failure(kind, new Error("some internal string"), { kind });
    const summary = failureSummary(result);
    const help = failureHelp(result);
    assert.ok(summary.length > 0, `${kind} has no summary`);
    assert.ok(help.length > 0, `${kind} has no help`);
    assert.ok(summary.length <= 120, `${kind}'s summary is a paragraph: ${summary}`);
    assert.ok(!/undefined|\[object Object\]/.test(summary + help), `${kind} renders a hole: ${summary}`);
    assert.notEqual(failureDetail(result), summary, `${kind} keeps no raw string to demote`);
  }
});

test("every help sentence stands on its own, because a screen renders it without the summary", () => {
  // Found by reading the rendered states page: a state shows `help` as its body
  // and never the summary, so a help that began "That usually means…" or "The
  // reason above…" pointed at a sentence the reader could not see.
  const kinds = [
    "network-or-cors",
    "timeout",
    "http",
    "shape",
    "fetch",
    "endpoint",
    "cipher",
    "nostream",
    "notfound",
    "demo",
    "search",
  ];
  const dangling = /^(That|This|These|Those|It |The reason above|The sentence above)\b/;
  for (const kind of kinds) {
    const help = failureHelp(failure(kind, new Error("an internal string"), { kind }));
    assert.ok(!dangling.test(help), `${kind}'s help opens by pointing at something the reader cannot see: ${help}`);
    assert.match(help, /^[A-Z]/, `${kind}'s help does not open with a sentence: ${help}`);
    assert.ok(/[.!?]$/.test(help), `${kind}'s help does not end a sentence: ${help}`);
  }
});

test("a failure that speaks for itself keeps the source's words in the state's body", () => {
  const refused = failure("playability", new Error("Video unavailable"), { kind: "playability" });
  const error = stateError(refused);
  assert.match(error.help, /^Video unavailable/, "the reason is carried into the body, not dropped");
  assert.match(error.summary, /^Video unavailable$/);

  const demo = failure("demo", new Error("this row is fictional"), { kind: "demo" });
  assert.match(stateError(demo).help, /^this row is fictional/);
});

test("a failure with no kind is classified from its message before being answered generically", () => {
  for (const message of BROWSER_WORDS) {
    assert.match(failureSummary({ message }), /could not reach the source/i);
  }
  assert.equal(failureSummary({ message: "something else entirely" }), "The request could not be completed.");
});

test("a missing or unreadable failure still produces a sentence", () => {
  for (const value of [null, undefined, {}, { kind: null, message: null }, "boom"]) {
    const summary = failureSummary(value);
    assert.ok(summary.length > 0, `no summary for ${JSON.stringify(value)}`);
    assert.ok(!/undefined|\[object Object\]/.test(summary));
  }
});

test("timeouts are their own sentence, not a network claim", () => {
  const slow = failure("network", new TypeError("The operation was aborted"));
  assert.equal(slow.kind, "timeout");
  assert.match(failureSummary(slow), /took too long|stopped/i);
  assert.equal(isRefusal(slow), false, "a timeout is not a cross-origin refusal");
});

test("the demo catalogue answers every browse kind and admits it has no audio", async () => {
  const demo = createDemoSource();
  const home = await demo.home();
  assert.equal(home.ok, true);
  assert.equal(home.demo, true);
  assert.ok(home.shelves.length > 0);

  const album = await demo.browse("al-01");
  assert.equal(album.ok, true);
  assert.equal(album.kind, "album");
  assert.ok(album.tracks.length > 0);

  const missing = await demo.browse("nope");
  assert.equal(missing.ok, false);
  assert.equal(missing.kind, "notfound");

  const stream = await demo.resolveStream("tr-01");
  assert.equal(stream.ok, false);
  assert.equal(stream.kind, "demo");
  assert.match(stream.message, /fictional/);
});

test("the endpoint source speaks the documented contract", async () => {
  const calls = [];
  const fakeFetch = async (url, options = {}) => {
    calls.push({ url, method: options.method ?? "GET", body: options.body });
    if (url.endsWith("/health")) return jsonResponse({ ok: true, version: "1" });
    if (url.endsWith("/search")) {
      return jsonResponse({ tracks: [{ id: "v1", title: "One" }], albums: [], artists: [], playlists: [] });
    }
    if (url.endsWith("/home")) return jsonResponse({ shelves: [{ title: "Shelf", entries: [] }] });
    if (url.endsWith("/stream")) {
      return jsonResponse({ url: "https://cdn.test/a", mimeType: "audio/webm", title: "One", durationMs: 1000 });
    }
    return jsonResponse({ kind: "album", album: { id: "a", title: "A" }, tracks: [] });
  };
  const endpoint = createEndpointSource({ url: "https://example.org/dhun/", fetchImpl: fakeFetch });
  assert.equal(endpoint.configured, true);

  assert.equal((await endpoint.health()).version, "1");
  assert.equal((await endpoint.search("one")).tracks.length, 1);
  assert.equal((await endpoint.home()).shelves.length, 1);
  assert.equal((await endpoint.browse("MPREb_1")).kind, "album");
  const stream = await endpoint.resolveStream("v1");
  assert.equal(stream.ok, true);
  assert.equal(stream.stream.url, "https://cdn.test/a");

  // The base path is preserved: an endpoint mounted under /dhun/ must not lose it.
  assert.ok(calls.every((call) => call.url.startsWith("https://example.org/dhun/")));

  const refused = await createEndpointSource({
    url: "https://example.org/dhun",
    fetchImpl: async () => {
      throw new TypeError("Failed to fetch");
    },
  }).search("x");
  assert.equal(refused.ok, false);
  assert.equal(refused.kind, "network-or-cors");

  const unconfigured = createEndpointSource({ url: "" });
  assert.equal(unconfigured.configured, false);
  assert.equal((await unconfigured.health()).kind, "endpoint");
});

test("a stream response without a usable url is a shape failure, not a silent skip", async () => {
  const endpoint = createEndpointSource({
    url: "https://example.org",
    fetchImpl: async () => jsonResponse({ mimeType: "audio/webm" }),
  });
  const result = await endpoint.resolveStream("v1");
  assert.equal(result.ok, false);
  assert.equal(result.kind, "shape");
});

test("the source manager falls back in the order the policy states, and says who answered", async () => {
  const settings = {
    values: { sourceId: "auto", endpointUrl: "" },
    get(key) {
      return this.values[key];
    },
  };
  // Offline and cross-origin refusal are the same event at this layer: fetch
  // rejects with a TypeError before any response exists.
  const failing = async () => {
    throw new TypeError("Failed to fetch");
  };

  const manager = createSourceManager({ settings, fetchImpl: failing });
  const result = await manager.home();
  assert.equal(result.ok, true, "the demo answers when nothing real can");
  assert.equal(result.usedKind, SOURCE_KIND.DEMO);
  assert.equal(manager.lastUsed.reason.kind, "network-or-cors", "and the reason is kept for the notice");
  assert.match(manager.lastUsed.reason.message, /Failed to fetch/);
  assert.deepEqual(manager.options().map((option) => option.id), ["auto", "endpoint", "innertube", "demo"]);
});

test("stream resolution never falls back to the demo catalogue", async () => {
  const settings = { values: { sourceId: "auto", endpointUrl: "" }, get(key) { return this.values[key]; } };
  const manager = createSourceManager({
    settings,
    fetchImpl: async () => ({ ok: true, status: 200, json: async () => ({}) }),
  });
  const result = await manager.resolveStream("vid");
  assert.equal(result.ok, false, "a fictional row must never be reported as playable");
});

test("the source manager honours an explicit choice instead of falling through", async () => {
  const settings = {
    values: { sourceId: SOURCE_KIND.DEMO, endpointUrl: "" },
    get(key) {
      return this.values[key];
    },
  };
  let liveCalled = false;
  const manager = createSourceManager({
    settings,
    fetchImpl: async () => {
      liveCalled = true;
      return { ok: false, status: 500, json: async () => ({}) };
    },
  });
  const result = await manager.home();
  assert.equal(result.ok, true);
  assert.equal(result.usedKind, SOURCE_KIND.DEMO);
  assert.equal(liveCalled, false, "an explicit 'demo only' must not secretly call a live source");
});

test("artwork is a real image when the source gave one and a stable gradient when it did not", () => {
  const withImage = artworkInfo({ id: "v1", artwork: "https://x.test/a.jpg" });
  assert.equal(withImage.url, "https://x.test/a.jpg");
  assert.equal(withImage.gradient, null);

  const a = artworkInfo({ id: "v1" });
  const b = artworkInfo({ id: "v1" });
  const c = artworkInfo({ id: "v2" });
  assert.equal(a.gradient, b.gradient, "the same row keeps the same placeholder colour");
  assert.notEqual(a.gradient, c.gradient);
});

test("a live source with no fetch says so instead of throwing", async () => {
  const source = createInnertubeSource({ fetchImpl: null });
  const result = await source.home();
  assert.equal(result.ok, false);
  assert.match(result.message, /fetch/);
});

test("walking a response visits every object without recursing for ever", () => {
  const seen = [];
  walkJson({ a: { b: [{ c: 1 }] } }, (node) => seen.push(node));
  assert.ok(seen.length >= 3);
  const cyclic = { name: "root" };
  cyclic.self = cyclic;
  assert.doesNotThrow(() => walkJson(cyclic, () => {}));
  assert.equal(findRenderer({ x: { musicShelfRenderer: { y: 1 } } }, "musicShelfRenderer").y, 1);
});

function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  };
}
