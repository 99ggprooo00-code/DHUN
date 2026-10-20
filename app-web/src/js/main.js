/**
 * The application: boot, render, and every action the interface can trigger.
 *
 * This file is the only place that knows about all the others. The shape of it:
 *
 *   1. `boot()` builds the pieces — settings, library, sources, the audio
 *      engine, the player, the router, the service worker — and renders a shell
 *      immediately. Nothing is awaited before the first paint, because a shell
 *      that appears instantly and fills in is a better first second than a blank
 *      screen that waits for IndexedDB.
 *   2. Every region of the chrome has its own mount point (`#dhun-view`,
 *      `#dhun-dock`, `#dhun-sheet`, …) and its own render function. Only the
 *      region whose data changed is rewritten, so typing in the search box does
 *      not lose focus and scrolling does not jump.
 *   3. All interaction is one delegated listener over `[data-action]`. New UI
 *      becomes reachable by adding a `case`, and an action that nobody handles is
 *      *loud* in development rather than a button that does nothing.
 *
 * The web-specific problems solved here, in one place:
 *
 *   - the autoplay policy (a rejected `play()` becomes a prompt, not an error);
 *   - the CORS split between "plays but cannot be filtered" and "plays and can";
 *   - the same app open in five tabs (single-player election via `tabsync.js`);
 *   - the browser's Back button (the route is the source of truth);
 *   - a storage-less private window (settings and library degrade, the app runs);
 *   - a stream URL that expired while the tab was idle (re-resolve exactly once).
 */

import { html, raw, delegate, focusWithoutScroll } from "./dom.js";
import { detectPlatform, unavailableCapabilities, isFramed } from "./platform.js";
import {
  createSettingsStore,
  createLibraryStore,
  emptyLibrary,
  normaliseLibrary,
  exportLibrary,
  importLibrary,
  readSession,
  writeSession,
  clearSession,
} from "./storage.js";
import {
  createSourceManager,
  failure,
  failureDetail,
  failureSummary,
  isRefusal,
  stateError,
  SOURCE_KIND,
  ENDPOINT_CONTRACT,
  HOME_MOODS,
  moodById,
  greetingForNow,
} from "./source.js";
import { pluralise } from "./format.js";
import { createAudioEngine, AUDIO_STATUS } from "./audio.js";
import { createPlayer, REPEAT } from "./player.js";
import { createMediaSession } from "./mediasession.js";
import { createTabSync, openTabChannel } from "./tabsync.js";
import { createPwa } from "./pwa.js";
import { createRouter, parseRoute, formatRoute, needsData, entityLabel, LIBRARY_TABS } from "./router.js";
import { BANDS_HZ, PRESETS, CUSTOM_ID, clampGains, matchingPreset, presetChoices } from "./equalizer.js";
import { createNavState, backAction, ShellBackAction, RAIL_BREAKPOINT_PX } from "./nav.js";
import { parseLrc, activeLineIndex } from "./lyrics.js";
import * as views from "./views.js";
import { LEGAL_DOCUMENTS, legalDocumentById } from "./data/legal-content.js";
import { miniPlayer, fullPlayer } from "./player-ui.js";
import { WEB_VERSION, BUILD_LABEL } from "./version.js";
import { matchShortcut, CHORD_TIMEOUT_MS } from "./shortcuts.js";

/** The three destinations, and the icon each one wears. */
const TAB_ICON = Object.freeze({ HOME: "Home", SEARCH: "Search", LIBRARY: "LibraryMusic" });
const TAB_LABEL = Object.freeze({ HOME: "Home", SEARCH: "Search", LIBRARY: "Your library" });
const NAV_TABS = Object.freeze(["HOME", "SEARCH", "LIBRARY"]);

/** How long a toast stays on screen. Long enough to read twice. */
const TOAST_MS = 4000;
/** History is a convenience, not a log: it stays short and is easy to clear. */
const MAX_HISTORY = 200;
/** Requesting lyrics for every skipped track is rude to the service and slow. */
const LYRICS_ENDPOINT = "https://lrclib.net/api";

export function boot({
  root = globalThis.document?.querySelector?.("#app"),
  win = globalThis,
  doc = globalThis.document,
  fetchImpl = globalThis.fetch?.bind(globalThis),
  AudioContextCtor = globalThis.AudioContext ?? globalThis.webkitAudioContext ?? null,
  audioElement = null,
} = {}) {
  if (!root || !doc) throw new Error("boot() needs a root element and a document");

  /* ------------------------------------------------------- framing refusal */

  /**
   * Before anything is built, and why it is here rather than in a header:
   * `frame-ancestors` is ignored in a `<meta>` CSP (the specification says so,
   * and Chrome logs it on every load), and this app is served by GitHub Pages,
   * which sends no custom response headers. A framed DHUN is a Play button that
   * appears to belong to whatever page surrounds it, so the app declines to
   * start in one. Nothing is constructed first — no settings read, no storage
   * opened, no source or audio engine created — because a refusal that has
   * already touched the visitor's data is not a refusal.
   */
  if (isFramed(win)) {
    root.innerHTML = views.framedRefusal();
    return { framed: true };
  }

  /* ------------------------------------------------------------- foundation */

  const platformRaw = detectPlatform({ window: win });
  const platform = { ...platformRaw, unavailable: unavailableCapabilities(platformRaw) };
  const settings = createSettingsStore();
  let library = emptyLibrary();
  let libraryKind = "memory";

  const sources = createSourceManager({ settings, fetchImpl });
  const engine = createAudioEngine({
    audio: audioElement,
    AudioContextCtor,
    bandsHz: BANDS_HZ,
    onEnded: () => player.next(),
    onNeedsGesture: () => showBanner("gesture"),
  });
  const player = createPlayer({
    engine,
    source: sources,
    settings,
    onEvent: (event) => {
      if (event.type === "track-start") onTrackStart(event.track);
      if (event.type === "track-failed") onTrackFailed(event.track, event.failure);
    },
  });
  const media = createMediaSession({ navigator: win.navigator, document: doc });
  let visualiserFrame = null;

  /** Routes that are one level down: the top bar offers a way back out of them,
   *  because a hash URL is not a visible affordance on a desktop window. */
  const DETAIL_ROUTES = Object.freeze(["playlist", "artist", "album", "settings", "about-legal"]);
  /** The three that draw the entity's own name as the screen's `<h1>`. */
  const ENTITY_ROUTES = Object.freeze(["playlist", "artist", "album"]);
  const tabSync = createTabSync({
    channel: openTabChannel(),
    onOtherTabPlaying: () => {
      if (player.state.playing) {
        engine.pause();
        showBanner("other-tab");
      }
    },
    onTakeover: () => {
      engine.pause();
      clearBanner("other-tab");
    },
  });
  const pwa = createPwa({ navigator: win.navigator, win, onState: () => renderChrome() });
  const nav = createNavState();

  /* ------------------------------------------------------------ app state */

  const state = {
    route: parseRoute(win.location?.hash ?? ""),
    /** Home feed, per route. */
    home: { status: "idle", shelves: [], error: null },
    /**
     * The selected mood, and the answer to *its* request. A mood is a search
     * against the source (`HomeMood.query`), so it carries its own state rather
     * than borrowing the feed's: "Chill" failing to load is not "Home" failing,
     * and a mood that failed must not empty the feed the visitor can go back to.
     */
    mood: "for-you",
    moodFeed: { status: "idle", shelves: [], error: null, query: "" },
    search: { query: "", filter: "all", status: "idle", results: null, error: null },
    entity: { status: "idle", kind: null, id: null, data: null, error: null },
    lyrics: { status: "idle", lines: [], activeIndex: -1, message: "" },
    sheet: null,
    /** A transient message. `null` when there is nothing to say. */
    toast: null,
    /** Which banner, if any, the dock is showing. */
    banner: null,
    /** The context the current queue came from ("Album · Lowland Echoes"). */
    context: "",
    /** The library view's "clear history" is a two-step action. */
    confirmClearHistory: false,
    busy: false,
  };

  let toastTimer = null;
  let lyricRequest = 0;

  /* -------------------------------------------------------------- helpers */

  function clearToast() {
    if (toastTimer) win.clearTimeout?.(toastTimer);
    toastTimer = null;
    if (state.toast) {
      state.toast = null;
      renderSheet();
    }
  }

  function showToast(message) {
    if (toastTimer) win.clearTimeout?.(toastTimer);
    state.toast = message;
    renderSheet();
    toastTimer = win.setTimeout?.(() => {
      toastTimer = null;
      state.toast = null;
      renderSheet();
    }, TOAST_MS);
  }

  function showBanner(kind, payload = {}) {
    state.banner = { kind, ...payload };
    renderDock();
  }

  function clearBanner(kind = null) {
    if (!kind || state.banner?.kind === kind) {
      state.banner = null;
      renderDock();
    }
  }

  function libraryCounts() {
    return {
      playlists: library.playlists.length,
      likes: library.likes.length,
      history: library.history.length,
    };
  }

  function isLiked(track) {
    const id = track?.id ?? track?.videoId;
    return Boolean(id) && library.likes.some((entry) => entry.id === id);
  }

  function persistLibrary() {
    library.updatedAt = Date.now();
    libraryStore.write(library);
  }

  /**
   * Remember a row's metadata, keyed by id.
   *
   * The local counterpart of the app's "Track metadata you have browsed" table.
   * A played, liked or newly-listed row is remembered so a playlist made in this
   * browser can draw its rows after a reload — the network is never asked for a
   * playlist it does not have. Deliberately bounded: this is a cache of what was
   * *seen*, not a catalogue.
   */
  function rememberTrack(track) {
    const id = track?.id ?? track?.videoId;
    const title = typeof track?.title === "string" ? track.title.trim() : "";
    if (!id || !title) return;
    library.tracks[id] = {
      id,
      title,
      artist: typeof track.artist === "string" ? track.artist : "",
      artwork: typeof track.artwork === "string" ? track.artwork : "",
      durationMs: typeof track.durationMs === "number" ? track.durationMs : 0,
    };
    const ids = Object.keys(library.tracks);
    if (ids.length > 2000) for (const stale of ids.slice(0, ids.length - 2000)) delete library.tracks[stale];
  }

  /** A row the library can draw, or null if nothing was ever seen under that id. */
  function libraryTrack(trackId) {
    const known = library.tracks[trackId];
    if (known) return known;
    const played = library.history.find((entry) => entry.id === trackId);
    return played ? { ...played } : null;
  }

  /* --------------------------------------------------------------- library */

  let libraryStore = {
    kind: "memory",
    read: async () => emptyLibrary(),
    write: async () => true,
    clear: async () => true,
  };

  createLibraryStore().then(async (store) => {
    libraryStore = store;
    libraryKind = store.kind;
    library = normaliseLibrary(await store.read());
    renderView();
    renderChrome();
    // `router.start()` runs before this promise settles, so the first pass at a
    // route resolved against an empty library and cached "That playlist could
    // not be loaded" for a playlist sitting in storage. Now that the library is
    // in hand, resolve the entity again — only when the entity is still the one
    // that was asked for.
    const route = state.route;
    if (route.name === "playlist" && route.id && state.entity.id === route.id && library.playlists.some((entry) => entry.id === route.id)) {
      loadEntity("playlist", route.id);
    }
  });

  /* ------------------------------------------------------- data + rendering */

  async function loadHome() {
    if (state.home.status === "loading") return;
    state.home = { ...state.home, status: "loading", error: null };
    renderView();
    const result = await sources.home();
    if (result.ok) {
      state.home = { status: "ready", shelves: result.shelves ?? [], error: null };
    } else {
      state.home = { status: "error", shelves: [], error: stateError(result) };
    }
    renderView();
  }

  /**
   * Ask the source for the selected mood and turn the answer into the one shelf
   * `UseCases.moodSections` produces: title `"<label> songs"`, subtitle
   * `"Songs for <label>"`, the songs as rows.
   *
   * The rows are the source's, unmapped and unfiltered — the demo catalogue
   * answers when nothing live can, exactly as it does for the feed, and the
   * badge and the demo strip say so because this request goes through the same
   * `sources.rowsUsed` record.
   */
  async function loadMood(id) {
    const mood = moodById(id);
    if (!mood.query) {
      // For you is the feed itself; going back to it must not re-fetch Home.
      state.mood = mood.id;
      renderView();
      return;
    }
    state.mood = mood.id;
    state.moodFeed = { status: "loading", shelves: [], error: null, query: mood.query };
    renderView();
    const result = await sources.search(mood.query);
    if (state.mood !== mood.id) return; // the visitor moved on while it loaded
    if (result.ok) {
      const tracks = (result.tracks ?? []).filter((entry) => entry && (entry.id || entry.videoId));
      state.moodFeed = {
        status: "ready",
        query: mood.query,
        error: null,
        shelves: tracks.length
          ? [{
              title: `${mood.label} songs`,
              subtitle: `Songs for ${mood.label.toLowerCase()}`,
              entries: tracks,
            }]
          : [],
      };
    } else {
      state.moodFeed = { status: "error", shelves: [], query: mood.query, error: stateError(result) };
    }
    renderView();
  }

  /**
   * A playlist made in this browser, as the view expects it.
   *
   * Rows come from the local track table (see `rememberTrack`). An id that was
   * never seen with a title is counted, not printed: `playlistScreen` says how
   * many could not be matched rather than showing a row with no name.
   */
  function ownPlaylistEntity(playlist) {
    const tracks = playlist.trackIds.map(libraryTrack).filter(Boolean);
    return {
      kind: "playlist",
      playlist: {
        id: playlist.id,
        name: playlist.name,
        description: playlist.description ?? "",
        trackCountText: pluralise(playlist.trackIds.length, "track"),
      },
      tracks,
      missing: playlist.trackIds.length - tracks.length,
    };
  }

  async function loadEntity(kind, id) {
    // A playlist made in this browser exists only here. Asking the source for it
    // was the whole of the old behaviour, and the answer — "That playlist could
    // not be loaded" — was shown for a playlist the app had just created and was
    // listing two screens away. `/app` has no server to have it in.
    if (kind === "playlist") {
      const own = library.playlists.find((entry) => entry.id === id);
      if (own) {
        state.entity = { status: "ready", kind: "playlist", id, data: ownPlaylistEntity(own), error: null };
        renderView();
        renderChrome();
        return;
      }
    }
    state.entity = { status: "loading", kind, id, data: null, error: null };
    renderView();
    const result = await sources.browse(id);
    if (result.ok) {
      state.entity = { status: "ready", kind: result.kind ?? kind, id, data: result, error: null };
    } else {
      state.entity = { status: "error", kind, id, data: null, error: stateError(result) };
    }
    renderView();
    if (result.ok && result.kind === "playlist") renderChrome();
  }

  async function runSearch(query, { focus = false } = {}) {
    state.search = { ...state.search, query, status: "loading", error: null };
    renderView();
    if (!query.trim()) {
      state.search = { ...state.search, status: "idle", results: null };
      renderView();
      return;
    }
    const result = await sources.search(query);
    if (result.ok) {
      state.search = { query, filter: state.search.filter, status: "ready", results: result, error: null };
      rememberSearch(query);
    } else {
      state.search = { query, filter: state.search.filter, status: "error", results: null, error: stateError(result) };
    }
    renderView();
    if (focus) focusWithoutScroll(doc.querySelector("#search-input"));
  }

  function rememberSearch(query) {
    const trimmed = query.trim();
    if (!trimmed) return;
    const recent = [trimmed, ...settings.get("recentSearches").filter((entry) => entry !== trimmed)].slice(0, 12);
    settings.set({ recentSearches: recent });
  }

  /** The active data source, as the UI shows it. */
  function sourceState() {
    const lastUsed = sources.lastUsed;
    const rows = sources.rowsUsed;
    const active = sources.describeActive();
    const chosen = settings.get("sourceId");
    const wantsEndpoint = chosen === SOURCE_KIND.ENDPOINT && !sources.endpoint.configured;

    /*
     * "These rows are the demo catalogue" is a claim about the rows, so it is
     * made about the rows: `rowsUsed`, not `lastUsed`. The two came apart the
     * moment a stream failed — resolving a track is a request like any other and
     * overwrites `lastUsed` with `kind: null`, so a track that would not play
     * could talk this screen out of saying where the rows came from, and the
     * banner would name YouTube Music over the demo catalogue's rows. A reason is
     * still required: choosing the demo on purpose is not a warning.
     */
    if (rows.kind === SOURCE_KIND.DEMO && rows.reason) {
      return {
        tone: "warn",
        title: "Showing the demo catalogue",
        body: `${failureSummary(rows.reason)} These rows are the demo catalogue: invented titles with no audio.`,
        actions: [{ id: "open-settings-source", label: "Data source" }],
        detail: active.detail,
        kind: SOURCE_KIND.DEMO,
      };
    }
    if (wantsEndpoint) {
      return {
        tone: "warn",
        title: "No endpoint address is set",
        body: "You chose your own endpoint as the only source, but no address is configured, so DHUN has nothing to ask. Add one below, or switch to Automatic.",
        actions: [{ id: "open-settings-source", label: "Fix this" }],
        detail: "Endpoint not configured",
        kind: SOURCE_KIND.ENDPOINT,
      };
    }
    return {
      tone: "info",
      title: active.label,
      body: active.detail,
      actions: [],
      detail: active.detail,
      kind: active.kind,
    };
  }

  /* ------------------------------------------------------------ playback UI */

  function playerViewModel() {
    const playerState = player.state;
    const track = player.current;
    const equalizerEnabled = settings.get("equalizerEnabled");
    const processed = engine.state.processed;
    return {
      track,
      playing: playerState.playing,
      positionMs: playerState.positionMs,
      durationMs: playerState.durationMs || track?.durationMs || 0,
      bufferedMs: playerState.bufferedMs,
      queue: playerState.queue,
      index: playerState.index,
      repeat: playerState.repeat,
      shuffle: playerState.shuffle,
      failures: playerState.failures,
      error: playerState.error,
      needsGesture: playerState.needsGesture,
      audioStatus: playerState.audioStatus,
      resolving: playerState.resolving,
      volume: playerState.volume,
      muted: playerState.muted,
      liked: isLiked(track),
      tab: state.route.playerTab ?? "queue",
      lyrics: state.lyrics,
      romanize: settings.get("romanizeLyrics"),
      canProcess: platform.audioContext,
      eqActive: equalizerEnabled && processed,
      eqLabel: !platform.audioContext
        ? "This browser has no Web Audio API, so the equaliser is unavailable"
        : equalizerEnabled && !processed
          ? "Equaliser on, but this stream cannot be processed (the host does not send CORS headers)"
          : equalizerEnabled
            ? "Equaliser active on this stream"
            : "Equaliser off",
      context: state.context,
      sourceLabel: sources.rowsUsed.kind === SOURCE_KIND.DEMO ? "Demo catalogue" : null,
    };
  }

  function onTrackStart(track) {
    tabSync.claim(track);
    media.update(track);
    media.setPlaybackState("playing");
    const id = track.id ?? track.videoId;
    rememberTrack(track);
    if (id) {
      library.history = [
        {
          id,
          at: Date.now(),
          title: track.title,
          artist: track.artist ?? "",
          artwork: track.artwork ?? "",
          durationMs: track.durationMs ?? 0,
        },
        ...library.history.filter((entry) => entry.id !== id),
      ].slice(0, MAX_HISTORY);
      library.plays[id] = (library.plays[id] ?? 0) + 1;
      persistLibrary();
    }
    loadLyrics(track);
    preResolveUpcoming();
    saveSession();
    renderChrome();
    renderDock();
  }

  function onTrackFailed(track, failure) {
    /*
     * One surface per message. The expanded player draws this same failure —
     * same summary, same technical line, same actions — in its own control
     * cluster, from the same `playerState.error` this handler is handed. The
     * toast would repeat it word for word *and*, being fixed above the dock,
     * land on top of the player's own title and artist while it did: measured
     * 2026-10-16 at 1280x900, the toast covered "Monsoon Over Dharan" and left
     * its name unreadable. Everywhere else — Home, Search, the library, the
     * queue on the page — there is no such strip, and the toast is the only
     * thing that can say the tap did not play anything.
     */
    if (state.route.player === "open") return;
    showToast(`${track.title} could not be played: ${failureSummary(failure)}`);
  }

  async function preResolveUpcoming() {
    const next = player.state.index + 1;
    if (next < player.state.queue.length) player.preResolve(next).catch?.(() => {});
  }

  /**
   * Lyrics, from the public LRCLIB service.
   *
   * The demo catalogue answers from its own bundled fixture instead — a fictional
   * song has no lyrics anywhere, and borrowing real ones would be wrong twice
   * over.
   */
  async function loadLyrics(track) {
    if (!track) return;
    const request = (lyricRequest += 1);
    state.lyrics = { status: "idle", lines: [], activeIndex: -1, message: "" };

    if (!settings.get("lyricsEnabled")) {
      state.lyrics = { status: "disabled", lines: [], activeIndex: -1, message: "" };
      renderOverlay();
      return;
    }
    if (track.demo) {
      state.lyrics = { status: "demo", lines: [], activeIndex: -1, message: "Demo row — no lyrics exist for it." };
      renderOverlay();
      return;
    }
    if (typeof fetchImpl !== "function") {
      const missing = failure("fetch", new Error("this browser did not provide fetch()"));
      state.lyrics = {
        status: "error",
        lines: [],
        activeIndex: -1,
        kind: missing.kind,
        message: failureSummary(missing),
        technical: failureDetail(missing),
      };
      renderOverlay();
      return;
    }

    state.lyrics = { status: "loading", lines: [], activeIndex: -1, message: "" };
    renderOverlay();
    try {
      const query = new URLSearchParams({ artist_name: track.artist ?? "", track_name: track.title ?? "" });
      if (track.durationMs) query.set("duration", String(Math.round(track.durationMs / 1000)));
      const response = await fetchImpl(`${LYRICS_ENDPOINT}/get?${query.toString()}`, { credentials: "omit" });
      if (!response.ok) throw new Error(`HTTP ${response.status} from LRCLIB`);
      const data = await response.json();
      const lrc = data?.syncedLyrics ?? data?.plainLyrics ?? "";
      if (request !== lyricRequest) return;
      const lines = parseLrc(lrc, { allowUnsyncedFallback: true }).map((line) => ({
        text: line.text,
        timeMs: line.timeMs,
      }));
      state.lyrics = {
        status: lines.length ? "ready" : "empty",
        lines,
        activeIndex: -1,
        message: lines.length ? "" : "LRCLIB has no lyrics for this track.",
      };
    } catch (error) {
      if (request !== lyricRequest) return;
      /*
       * The same copy rules as every other failure in the app, and for the same
       * reason: a refused request rejects with the browser's own `TypeError`,
       * whose message is "Failed to fetch" — one sentence for a flat network, a
       * blocked host and a content blocker. The lyrics panel used to print it.
       * A refusal is mapped to the lyrics-specific entry so the sentence names
       * the service the visitor is actually waiting on.
       */
      const result = failure("lyrics", error);
      const kind = isRefusal(result) ? "lyrics" : result.kind;
      state.lyrics = {
        status: "error",
        lines: [],
        activeIndex: -1,
        kind,
        message: failureSummary({ ...result, kind }),
        technical: failureDetail({ ...result, kind }),
      };
    }
    renderOverlay();
  }

  /* ------------------------------------------------------------- session -- */

  function saveSession() {
    if (!settings.get("resumeOnLaunch")) return;
    const playerState = player.state;
    if (!playerState.queue.length) return;
    writeSession({
      queue: playerState.queue.map((track) => ({
        id: track.id ?? track.videoId,
        videoId: track.videoId,
        title: track.title,
        artist: track.artist ?? "",
        album: track.album ?? "",
        artwork: track.artwork ?? null,
        durationMs: track.durationMs ?? 0,
        demo: Boolean(track.demo),
      })),
      index: playerState.index,
      positionMs: playerState.positionMs,
    });
  }

  function restoreSession() {
    if (!settings.get("resumeOnLaunch")) return null;
    const session = readSession();
    if (!session) return null;
    player.setQueue(session.queue, session.index);
    engine.seek(session.positionMs);
    state.context = session.queue[session.index]?.album ? `Album · ${session.queue[session.index].album}` : "";
    return session;
  }

  /* -------------------------------------------------------- shell rendering */

  const mounts = {};

  function mount(id) {
    if (!mounts[id]) mounts[id] = doc.getElementById(id);
    return mounts[id];
  }

  function setRegion(id, markup) {
    const element = doc.getElementById(id);
    if (!element) return;
    // Restoring scroll around an innerHTML swap is what keeps a re-render from
    // throwing the visitor back to the top of a long library.
    const scroller = element.dataset.scroll === "true" ? element : null;
    const top = scroller ? scroller.scrollTop : 0;
    element.innerHTML = markup;
    if (scroller) scroller.scrollTop = top;
  }

  /* --------------------------------------------------- focus on navigation --
   *
   * A card press replaces the screen, so the button that had focus no longer
   * exists and the browser drops focus onto `<body>`. Measured on the built app:
   * open an album from Home and `document.activeElement` is `body` — the next
   * Tab starts at the skip link, and a screen-reader user is told nothing about
   * the screen they just asked for. The clock had the screen's name in the
   * document title and no way to announce it until now.
   *
   * The rule is deliberately narrow: only when focus was actually lost, and the
   * callers decide which screens are worth announcing. A tab press re-renders
   * the nav itself, so the button that was pressed is gone and focus lands on
   * `<body>`; there the new screen's `<h1>` ("Your library") is the useful
   * target, which is what a nav press does now. A search submit keeps the caret
   * in the field instead (see `restoreFieldFocus`): the person is still typing.
   */
  let pendingHeadingFocus = null;

  function focusScreenHeadingIfLost() {
    if (!pendingHeadingFocus) return;
    const wanted = pendingHeadingFocus;
    if (
      state.route.name !== wanted.name ||
      (state.route.id ?? null) !== (wanted.id ?? null) ||
      (state.route.libTab ?? null) !== (wanted.libTab ?? null) ||
      (state.route.doc ?? null) !== (wanted.doc ?? null)
    ) {
      pendingHeadingFocus = null;
      return;
    }
    // An entity screen draws its heading only once the entity has arrived; the
    // flag survives the loading render and is spent by the one that has it.
    const heading = doc.querySelector("#dhun-view h1") ?? doc.querySelector("#dhun-topbar h1");
    if (!heading) return;
    pendingHeadingFocus = null;
    heading.setAttribute("tabindex", "-1");
    focusWithoutScroll(heading);
  }

  function viewTitle(route) {
    switch (route.name) {
      case "search":
        return "Search";
      case "library":
        return LIBRARY_TABS.includes(route.libTab) ? { playlists: "Your library", liked: "Liked songs", history: "History" }[route.libTab] : "Your library";
      case "settings":
        return "Settings";
      case "about-legal":
        return "About & legal";
      case "playlist":
        return state.entity.data?.playlist?.name ?? "Playlist";
      case "artist":
        return state.entity.data?.artist?.name ?? "Artist";
      case "album":
        return state.entity.data?.album?.title ?? "Album";
      case "home":
      default:
        return "Home";
    }
  }

  /**
   * The document's own title, which was one static string for every screen.
   *
   * `viewTitle` already knew the name of the album, artist, playlist, tab and
   * settings screen — it is what the app bar's `h1` reads — and none of it
   * reached `document.title`. So a browser tab, a bookmark and the history
   * entry all said "DHUN — the web build" whatever was open, and a screen
   * reader that announces the document title on navigation announced nothing.
   */
  function updateDocumentTitle() {
    const title = viewTitle(state.route);
    doc.title = title && title !== "Home" ? `${title} — DHUN` : "DHUN — the web build";
  }

  function renderView() {
    const route = state.route;
    const currentId = player.current?.id ?? null;
    let markup;
    switch (route.name) {
      case "search":
        markup = views.searchScreen({
          query: state.search.query,
          results: state.search.status === "loading" ? null : state.search.results ?? state.search.error,
          status: state.search.status,
          filter: state.search.filter,
          recentSearches: settings.get("recentSearches"),
          currentId,
          source: sources.rowsUsed,
        });
        break;
      case "library":
        markup = views.libraryScreen({
          tab: route.libTab,
          playlists: library.playlists,
          liked: library.likes
            .map((entry) => library.history.find((row) => row.id === entry.id) ?? { id: entry.id, title: entry.id })
            .filter(Boolean),
          history: library.history,
          currentId,
          libraryKind,
        });
        break;
      case "playlist": {
        // A screen whose entity is still in flight says so. It used to render
        // its *error* state for that frame — "That playlist could not be
        // loaded" — and replace it when the data arrived, which on a slow source
        // is a visible lie for as long as the request takes.
        const own = library.playlists.find((entry) => entry.id === route.id) ?? null;
        if (!own && state.entity.status === "loading") {
          markup = views.loadingState(["Loading the playlist"]);
          break;
        }
        const data = own ? ownPlaylistEntity(own) : state.entity.data;
        markup = views.playlistScreen({
          playlist: data?.playlist ?? null,
          tracks: data?.tracks ?? [],
          currentId,
          isOwn: Boolean(own),
          missing: Number(data?.missing ?? 0),
        });
        break;
      }
      case "artist":
        if (state.entity.status === "loading") {
          markup = views.loadingState(["Loading the artist"]);
          break;
        }
        markup = views.artistScreen({
          artist: state.entity.data?.artist ?? null,
          tracks: state.entity.data?.tracks ?? [],
          albums: state.entity.data?.albums ?? [],
          currentId,
        });
        break;
      case "album":
        if (state.entity.status === "loading") {
          markup = views.loadingState(["Loading the album"]);
          break;
        }
        markup = views.albumScreen({
          album: state.entity.data?.album ?? null,
          tracks: state.entity.data?.tracks ?? [],
          currentId,
        });
        break;
      case "settings":
        markup = views.settingsScreen({
          settings: { ...settings.all, confirmClearLibrary: Boolean(state.confirmClearLibrary) },
          platform,
          capabilities: platform,
          sources: sources.options(),
          sourceState: sourceState(),
          equalizer: equalizerViewModel(),
          library: { kind: libraryKind, counts: libraryCounts() },
          pwa: pwa.state,
          diagnostics: diagnosticsViewModel(),
        });
        break;
      case "about-legal":
        markup = views.aboutLegalScreen({
          documents: legalIndex(route.doc),
          activeId: route.doc,
          document: legalPage(route.doc),
          platform,
          build: { version: WEB_VERSION, label: BUILD_LABEL },
        });
        break;
      case "home":
      default: {
        // A mood is its own request with its own state, so the screen shows the
        // mood's shelf when one is selected and the feed's when "For you" is.
        const moodSelected = state.mood !== "for-you";
        const source = moodSelected ? state.moodFeed : state.home;
        const mood = moodById(state.mood);
        markup = views.homeScreen({
          shelves: source.status === "loading" ? [] : source.shelves,
          loading: source.status === "loading",
          error: source.error,
          currentId,
          demoTracks: [],
          source: sources.rowsUsed,
          greeting: greetingForNow(Date.now(), -new Date().getTimezoneOffset() * 60_000),
          moods: HOME_MOODS.map(({ id, label }) => ({ id, label })),
          mood: state.mood,
          moodLabel: mood.label,
          moodQuery: mood.query,
        });
        break;
      }
    }
    // A render replaces the screen's nodes, so a field that had the caret is
    // gone by the time the new markup is in: pressing Enter in Search rebuilt
    // the view and left focus on `<body>`, which meant the second half of a
    // query could not be typed until the first request came back. Capture the
    // field by identity, not by node, and put the caret back in its successor.
    const focusedField = captureFieldFocus();
    setRegion("dhun-view", markup);
    updateDocumentTitle();
    renderChrome();
    if (!restoreFieldFocus(focusedField)) focusScreenHeadingIfLost();
  }

  /**
   * Which text field had the caret, as something a re-render can find again.
   * Returns null for anything that is not a field: a button whose screen is
   * being replaced should not be re-focused — its screen is gone.
   */
  function captureFieldFocus() {
    const active = doc.activeElement;
    if (!active || !["INPUT", "TEXTAREA", "SELECT"].includes(active.tagName)) return null;
    // The caret and selection are worth keeping when the field is the same
    // field: a search box that scrolls back to character zero is a box that
    // ate what you typed.
    const caret = { start: active.selectionStart ?? null, end: active.selectionEnd ?? null };
    if (active.id) return { id: active.id, ...caret };
    const action = active.dataset?.action;
    if (!action) return null;
    return {
      action,
      index: active.dataset.index ?? null,
      value: active.dataset.value ?? null,
      ...caret,
    };
  }

  function restoreFieldFocus(captured) {
    if (!captured) return false;
    const field = captured.id
      ? doc.getElementById(captured.id)
      : doc.querySelector(
          `[data-action="${captured.action}"]${captured.index ? `[data-index="${captured.index}"]` : ""}${captured.value ? `[data-value="${captured.value}"]` : ""}`,
        );
    if (!field || field === doc.activeElement) return Boolean(field && field === doc.activeElement);
    focusWithoutScroll(field);
    if (typeof field.setSelectionRange === "function" && captured.start !== null) {
      try {
        field.setSelectionRange(captured.start, captured.end ?? captured.start);
      } catch {
        /* a field type that has no selection, e.g. number */
      }
    }
    return true;
  }

  function renderChrome() {
    const route = state.route;
    const title = viewTitle(route);
    const tabs = NAV_TABS.map((tab) => {
      const active =
        (tab === "HOME" && route.name === "home") ||
        (tab === "SEARCH" && route.name === "search") ||
        (tab === "LIBRARY" && route.name === "library");
      return html`<button type="button" class="dhun-nav-item" data-action="nav-tab" data-tab="${tab}"
        ${active ? raw('aria-current="page"') : raw("")}>
        ${views.icon(TAB_ICON[tab], { size: 24 })}
        <span class="dhun-nav-item__label">${TAB_LABEL[tab]}</span>
      </button>`;
    });

    setRegion(
      "dhun-rail",
      html`<span class="dhun-brand-block" aria-hidden="true">
          <span class="dhun-wordmark">DHUN</span>
        </span>
        ${tabs}`,
    );
    setRegion("dhun-tabs", html`${tabs}`);
    // The detail screens draw their own `<h1>` — the album, artist or playlist
    // name, under its cover or over its hero — and `AlbumScreen.kt` /
    // `ArtistScreen.kt` carry no title in the chrome at all. Repeating the name
    // up here put the same heading twice on one screen (313px apart on a
    // desktop) and left the page with two `h1`s. The bar keeps what the app's
    // chrome does not have to offer: the way back, the source badge, actions.
    const pageOwnsTitle = ENTITY_ROUTES.includes(route.name);
    setRegion(
      "dhun-topbar",
      html`<div class="dhun-topbar__lead">
          ${DETAIL_ROUTES.includes(route.name) || route.player === "open"
            ? html`<button type="button" class="dhun-icon-button" data-action="back" aria-label="Back">${views.icon("ArrowBack", { size: 22 })}</button>`
            : html``}
          ${pageOwnsTitle ? html`` : html`<h1 class="dhun-topbar__title">${title}</h1>`}
          ${views.sourceBadge(sources.describeRows())}
        </div>
        <div class="dhun-topbar__actions">
          <button type="button" class="dhun-icon-button" data-action="open-search" aria-label="Search">${views.icon("Search", { size: 22 })}</button>
          <button type="button" class="dhun-icon-button" data-action="open-settings" aria-label="Settings"
            ${route.name === "settings" ? raw('aria-current="page"') : raw("")}>${views.icon("Settings", { size: 22 })}</button>
        </div>`,
    );
    renderDock();
    renderOverlay();
    renderSheet();
  }

  /**
   * Clear the first-run strip the first time the visitor does anything at all.
   *
   * Capture-phase listeners on the window, removed as soon as one fires: the
   * strip has done its job once the app is being used, and a banner that stays
   * for the session stops being orientation and becomes furniture. It is not
   * persisted anywhere — a reload starts a fresh visit and shows it once more.
   */
  function dismissWelcomeOnFirstInteraction() {
    const once = () => {
      clearBanner("info");
      win.removeEventListener("pointerdown", once, true);
      win.removeEventListener("keydown", once, true);
    };
    win.addEventListener("pointerdown", once, true);
    win.addEventListener("keydown", once, true);
  }

  function renderDock() {
    const vm = playerViewModel();
    const banner = renderBanner(vm);
    setRegion(
      "dhun-dock",
      html`${banner}
        ${vm.track ? miniPlayer(vm, { context: state.context }) : html``}`,
    );
  }

  function renderBanner(vm) {
    if (!pwa.state.online) {
      return views.notice({
        tone: "warn",
        title: "You are offline",
        body: "The interface still works and your library is here. Tracks need a connection: stream URLs are not stored on the device.",
      });
    }
    if (state.banner?.kind === "gesture") {
      return views.notice({
        tone: "warn",
        title: "Sound is waiting for a click",
        body: "This browser blocks audio until you interact with the page.",
        actions: [{ id: "toggle-play", label: "Play" }],
      });
    }
    if (state.banner?.kind === "other-tab") {
      return views.notice({
        tone: "info",
        title: "Paused — another DHUN tab is playing",
        body: "Only one tab plays at a time so you do not hear two copies.",
        actions: [{ id: "take-over-playback", label: "Play here instead" }],
      });
    }
    if (pwa.state.updateReady) {
      return views.notice({
        tone: "info",
        title: "A new version is ready",
        body: "Reload to use it. Your library and settings stay exactly as they are.",
        actions: [{ id: "apply-update", label: "Reload" }],
      });
    }
    if (pwa.state.installable && !settings.get("installDismissed") && !pwa.state.installed) {
      return views.notice({
        tone: "info",
        title: "Install DHUN",
        body: "Adds an icon to your home screen or applications menu and opens without browser chrome.",
        actions: [
          { id: "install", label: "Install" },
          { id: "dismiss-install", label: "Not now" },
        ],
      });
    }
    /*
     * A player link with nothing to play it.
     *
     * `#/home?player=open` — what the site's "Launch /app/ Player" link and any
     * bookmark of an expanded player produce — asks for a screen that is only
     * drawn once a track is loaded, and the app used to drop the request in
     * silence: the visitor landed on Home with no player and no reason. The
     * request is kept, so the player does open as soon as a track starts; until
     * then the app says what it is waiting for. Derived from the route rather
     * than stored, so it goes away by itself the moment something plays.
     */
    if (state.route.player === "open" && !vm.track) {
      return views.notice({
        tone: "info",
        title: "Nothing is playing yet",
        body: "The player opens on a track. Choose one and it opens here, holding the screen you asked for.",
      });
    }
    if (state.banner?.kind === "info") {
      return views.notice({ tone: "info", title: state.banner.title ?? "", body: state.banner.body ?? "" });
    }
    return html``;
  }

  /* The overlay's scroller is a child — `.dhun-full__inner` — so `setRegion`'s
     own scroll restore (which reads `data-scroll` off the region itself) never
     reached it, and the region's markup is replaced on every tab press.
     Measured 2026-10-16 on a 390x844 phone: switching to Lyrics reset the
     player's scroll 613 → 0 and moved the panel the visitor had just asked for
     from y=327 to y=940 — off the bottom of the screen, so the tab they pressed
     appeared to do nothing. A re-render of the *same* track keeps the visitor
     where they were; a different track is the one case that should start back
     at the cover. */
  const playerScroll = { trackKey: null, top: 0 };

  function renderOverlay() {
    const vm = playerViewModel();
    const open = state.route.player === "open";
    const scroller = root?.querySelector?.("#dhun-overlay .dhun-full__inner");
    if (scroller) playerScroll.top = scroller.scrollTop;
    setRegion("dhun-overlay", open && vm.track ? fullPlayer(vm) : html``);
    const trackKey = open && vm.track ? String(vm.track.id ?? vm.track.videoId ?? "") : null;
    const next = root?.querySelector?.("#dhun-overlay .dhun-full__inner");
    if (next && trackKey && trackKey === playerScroll.trackKey) next.scrollTop = playerScroll.top;
    playerScroll.trackKey = trackKey;
    settleModalFocus();
    drawVisualiser();
  }

  /* ------------------------------------------------------------ visualiser */

  /**
   * Draw the spectrum on the full player's canvas.
   *
   * The canvas is only in the markup while audio is playing through the Web
   * Audio graph, so this starts with the render and stops as soon as the element
   * is gone or the tab is in the background — an animation that keeps running
   * behind a hidden tab is a battery cost for nobody's benefit. The colour comes
   * from the stylesheet (`color` on `.dhun-visualiser`), so the accent ramp and
   * the theme still decide what it looks like.
   */
  function drawVisualiser() {
    stopVisualiser();
    const canvas = doc.querySelector(".dhun-visualiser");
    if (!canvas || typeof canvas.getContext !== "function") return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const accent = win.getComputedStyle?.(canvas)?.color || null;
    const bars = 40;
    const paint = () => {
      if (!canvas.isConnected) {
        stopVisualiser();
        return;
      }
      const spectrum = engine.spectrum();
      const { width, height } = canvas;
      ctx.clearRect(0, 0, width, height);
      if (spectrum && accent) {
        ctx.fillStyle = accent;
        const barWidth = width / bars;
        for (let index = 0; index < bars; index += 1) {
          const bin = spectrum[Math.floor((index / bars) * spectrum.length)] ?? 0;
          const barHeight = Math.max(2, (bin / 255) * height);
          ctx.fillRect(index * barWidth, height - barHeight, Math.max(1, barWidth - 2), barHeight);
        }
      }
      visualiserFrame = platform.reduceMotion ? null : win.requestAnimationFrame(paint);
    };
    paint();
  }

  function stopVisualiser() {
    if (visualiserFrame !== null) {
      win.cancelAnimationFrame?.(visualiserFrame);
      visualiserFrame = null;
    }
  }

  /** Put the cursor in the sheet's one field: the sheet exists to fill it in. */
  function focusSheetField() {
    const field = doc.querySelector(".dhun-sheet input[name='name']");
    if (!field) return;
    focusWithoutScroll(field);
    field.select?.();
  }

  function renderSheet() {
    const sheet = state.sheet;
    let markup = html``;
    if (sheet?.kind === "track-menu") {
      markup = views.trackMenuSheet({
        track: sheet.track,
        inQueue: Boolean(sheet.inQueue),
        liked: isLiked(sheet.track),
        playlists: library.playlists,
      });
    } else if (sheet?.kind === "playlist-picker") {
      markup = views.playlistPickerSheet({ track: sheet.track, playlists: library.playlists });
    } else if (sheet?.kind === "playlist-name") {
      markup = views.playlistNameSheet({ mode: sheet.mode, playlist: sheet.playlist });
    } else if (sheet?.kind === "confirm") {
      markup = views.confirmSheet(sheet.options);
    } else if (sheet?.kind === "shortcuts") {
      markup = views.shortcutsSheet({ platform: win.navigator?.platform ?? "" });
    }
    // `markup` is a Markup fragment, and every fragment is truthy — including an
    // empty one, because it is an object. Ask for its text.
    const open = String(markup).length > 0;
    setRegion("dhun-sheet", open ? html`<div class="dhun-scrim">${markup}</div>` : html``);
    setRegion("dhun-toast", state.toast ? views.toast(state.toast) : html``);
    settleModalFocus();
  }

  /* ------------------------------------------------------------ dialog focus --
   *
   * A sheet carries `role="dialog" aria-modal="true"`, and for a long time that
   * was a claim rather than a behaviour: the caret started in the sheet (the
   * naming sheet focuses its field) but the second Tab left it, walked into the
   * page behind the scrim, and the page was never marked unavailable. Measured
   * on the built app: open "New playlist", press Tab twice, and focus is on the
   * skip link behind the dialog. A screen reader is told the rest of the page is
   * hidden; the keyboard found otherwise.
   *
   * The full player makes the same claim (`role="dialog" aria-modal="true"`) and
   * goes through the same helper, so the one surface that is always reachable —
   * the sheet — is not the only one that behaves like the dialog it says it is.
   * (The player only renders once audio is actually playing from a source the
   * visitor configured, so that path has no gate yet; the sheet's does.)
   *
   * So: focus moves into a dialog when it opens, Tab and Shift+Tab wrap inside
   * the dialog on top, and closing it puts focus back on whatever opened it.
   * `aria-modal` stays because it is now true. The return target is remembered
   * as a selector as well as a node: the click that opens the sheet or player is
   * followed by a re-render that replaces the button, so the node is often
   * detached by the time the dialog closes.
   */
  let modalKey = null;
  let modalReturn = null;

  function modalRoot() {
    // The sheet renders above the player, and Escape closes it first.
    return doc.querySelector(".dhun-sheet") ?? doc.querySelector('#dhun-overlay [role="dialog"]');
  }

  function focusablesIn(root) {
    return [...root.querySelectorAll(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    )].filter((element) => element.getClientRects().length > 0 && element.getAttribute("aria-hidden") !== "true");
  }

  function focusInsideAnyModal() {
    const sheet = doc.querySelector(".dhun-sheet");
    const playerDialog = doc.querySelector('#dhun-overlay [role="dialog"]');
    const active = doc.activeElement;
    return Boolean((sheet && sheet.contains(active)) || (playerDialog && playerDialog.contains(active)));
  }

  function settleModalFocus() {
    const root = modalRoot();
    const key = root ? (root.classList.contains("dhun-sheet") ? "sheet" : "player") : null;
    if (key && !modalKey) {
      // A dialog opened. Remember what had focus so closing can hand it back.
      const active = doc.activeElement;
      modalReturn = active && active !== doc.body && active.getAttribute
        ? { node: active, action: active.dataset?.action ?? null, id: active.dataset?.id ?? null, value: active.dataset?.value ?? null }
        : null;
      modalKey = key;
      if (!root.contains(doc.activeElement)) {
        // The sheet's own field, when it has one, has already claimed the caret.
        focusWithoutScroll(key === "player" ? root : focusablesIn(root)[0] ?? root);
      }
      return;
    }
    if (key) {
      // A sheet opening over an open player (or the reverse) must not overwrite
      // the remembered trigger, and must not yank a caret that is still valid.
      modalKey = key;
      if (!focusInsideAnyModal()) focusWithoutScroll(key === "player" ? root : focusablesIn(root)[0] ?? root);
      return;
    }
    if (modalKey) {
      modalKey = null;
      const wanted = modalReturn;
      modalReturn = null;
      if (!wanted) return;
      let target = wanted.node && doc.contains(wanted.node) ? wanted.node : null;
      if (!target && wanted.action) {
        // The original node was re-rendered away; find the same control again.
        const selector = `[data-action="${wanted.action}"]${wanted.id ? `[data-id="${wanted.id}"]` : ""}${wanted.value ? `[data-value="${wanted.value}"]` : ""}`;
        target = doc.querySelector(selector);
      }
      if (target?.focus) focusWithoutScroll(target);
    }
  }

  /** Keep Tab inside the dialog on top. Called before the shortcut matcher,
   *  which owns Escape but must not own Tab. */
  function trapModalTab(event) {
    const root = modalRoot();
    if (!root) return false;
    const items = focusablesIn(root);
    if (!items.length) return false;
    const first = items[0];
    const last = items[items.length - 1];
    const active = doc.activeElement;
    if (!root.contains(active)) {
      event.preventDefault();
      focusWithoutScroll(event.shiftKey ? last : first);
      return true;
    }
    if (event.shiftKey && (active === first || active === root)) {
      event.preventDefault();
      focusWithoutScroll(last);
      return true;
    }
    if (!event.shiftKey && active === last) {
      event.preventDefault();
      focusWithoutScroll(first);
      return true;
    }
    return false;
  }

  function equalizerViewModel() {
    const gains = settings.get("equalizerGains");
    const activePresetId = matchingPreset(gains, settings.get("equalizerPreampDb"))?.id ?? CUSTOM_ID;
    const processed = engine.state.processed;
    const enabled = settings.get("equalizerEnabled");
    return {
      presets: presetChoices(),
      activePresetId,
      bands: BANDS_HZ.map((hz, index) => ({ index, label: hz >= 1000 ? `${hz / 1000}k` : String(hz) })),
      status: !enabled
        ? "Off. The stream is untouched."
        : processed
          ? "Active on the stream being played."
          : gains.every((gain) => gain === 0)
            ? "On, with a flat curve."
            : `On, but this stream's host does not permit filtering, so the curve is not applied. ${statusLineForProcessing()}`,
    };
  }

  function statusLineForProcessing() {
    return "Streams that permit it are filtered; the badge on the player says which.";
  }

  function diagnosticsViewModel() {
    const rows = [
      { label: "Build", value: `${WEB_VERSION} · ${BUILD_LABEL}` },
      { label: "Route", value: formatRoute(state.route) },
      { label: "Data source", value: sources.describeActive().label },
      { label: "Rows on screen", value: sources.describeRows().label, state: sources.rowsUsed.kind === SOURCE_KIND.DEMO ? "warn" : "ok" },
      { label: "Last source used", value: sources.lastUsed.kind ?? "none yet", state: sources.lastUsed.kind === SOURCE_KIND.DEMO ? "warn" : "ok" },
      { label: "Library storage", value: libraryKind === "indexeddb" ? "IndexedDB" : "memory only", state: libraryKind === "indexeddb" ? "ok" : "warn" },
      { label: "Settings storage", value: settings.available ? "localStorage" : "not available", state: settings.available ? "ok" : "warn" },
      { label: "Media Session controls", value: media.supported ? "available" : "none in this browser", state: media.supported ? "ok" : "warn" },
      { label: "Web Audio (equaliser)", value: platform.audioContext ? (engine.hasContext ? "graph running" : "available, idle") : "unavailable", state: platform.audioContext ? "ok" : "warn" },
      { label: "Equaliser on this stream", value: engine.state.processed ? "processing" : engine.canProcess ? "not processing (CORS)" : "no graph yet", state: engine.state.processed ? "ok" : "neutral" },
      { label: "Service worker", value: pwa.state.sw, state: pwa.state.sw === "ready" ? "ok" : pwa.state.sw === "unsupported" ? "warn" : "neutral" },
      { label: "Cross-tab control", value: tabSync.supported ? "available" : "not in this browser", state: tabSync.supported ? "ok" : "warn" },
      { label: "Network", value: pwa.state.online ? "online" : "offline", state: pwa.state.online ? "ok" : "warn" },
      { label: "Standalone", value: pwa.state.installed ? "installed app" : "browser tab" },
      { label: "Stream cache", value: `${player.cacheSize} resolved URL(s)` },
    ];
    return { rows, contract: ENDPOINT_CONTRACT, streamCacheSize: player.cacheSize };
  }

  /* -------------------------------------------------------------- legal -- */

  /**
   * The bundled pages, as an index the screen can link to.
   *
   * Real `href`s rather than buttons: every page in this list is a URL, and a
   * URL can be middle-clicked, copied and shared. `formatRoute` builds the same
   * link the router parses, so there is one grammar rather than two.
   */
  function legalIndex(activeId) {
    return LEGAL_DOCUMENTS.map((entry) => ({
      id: entry.id,
      title: entry.title,
      status: entry.status,
      effectiveDate: entry.effectiveDate,
      href: formatRoute({ name: "about-legal", doc: entry.id, player: null, playerTab: "queue" }),
      active: entry.id === activeId,
    }));
  }

  /** One page, or a stand-in that says the link is wrong rather than rendering
   *  an empty document as if it were the page the visitor asked for. */
  function legalPage(id) {
    if (!id) return null;
    return (
      legalDocumentById(id) ?? {
        id,
        title: "This page is not in this build",
        status: "missing",
        effectiveDate: "",
        sha256: "",
        markdown: "",
      }
    );
  }

  /* ------------------------------------------------------------ navigation */

  const router = createRouter({
    win,
    onRoute: (route, meta) => {
      const previous = state.route;
      state.route = route;
      applyTheme();
      if (needsData(previous, route, meta)) {
        // Data fetching is driven by the route, so a deep link and a click take
        // exactly the same path — including the *first* one: `meta.initial` is
        // how the router says "the app has not loaded anything yet", which the
        // old `sameRoute(previous, route)` test could not tell from "the URL did
        // not change" (see `needsData` in router.js).
        if (route.name === "home" && state.home.status === "idle") loadHome();
        if (route.name === "search") {
          state.search = { ...state.search, query: route.query ?? "", filter: "all" };
          if (route.query) runSearch(route.query);
        }
        if (["playlist", "artist", "album"].includes(route.name)) {
          if (route.id && state.entity.id !== route.id) loadEntity(route.name, route.id);
          if (!route.id) router.navigate({ ...route, name: "home" }, { replace: true });
        }
      }
      if (route.redirectedFrom === "downloads") {
        showToast("Downloads are not part of the web app — a browser cannot keep a signed stream offline.");
      }
      // A toast raised before the player opened would ride in with it and, being
      // fixed above the dock, cover the player's own notice and its buttons —
      // measured 2026-10-16 on a 390x844 phone, where the tap that expanded the
      // player had just raised "could not be played" and the toast sat across
      // Retry / Data source / Why?. The player is a full-screen surface with its
      // own status strip; it starts clean.
      if (route.player === "open" && previous.player !== "open") clearToast();
      const screenChanged =
        route.name !== previous.name ||
        (route.id ?? null) !== (previous.id ?? null) ||
        (route.libTab ?? null) !== (previous.libTab ?? null) ||
        (route.doc ?? null) !== (previous.doc ?? null);
      renderView();
      // After the render, not before it: the trigger is destroyed by this very
      // render, so before it `activeElement` is still the card that was pressed.
      const lost = !doc.activeElement || doc.activeElement === doc.body;
      if (!meta.initial && screenChanged && lost) {
        pendingHeadingFocus = { name: route.name, id: route.id ?? null, libTab: route.libTab ?? null, doc: route.doc ?? null };
        focusScreenHeadingIfLost();
      }
    },
  });

  function go(route, options) {
    router.navigate(route, options);
  }

  /* ------------------------------------------------------------- actions -- */

  function trackFromElement(target) {
    const id = target.dataset.id;
    const index = Number(target.dataset.index ?? "");
    const queue = player.state.queue;
    const inQueueList = Boolean(target.closest?.(".dhun-tracklist--queue"));
    if (inQueueList && Number.isInteger(index) && index >= 0 && index < queue.length) {
      return { track: queue[index], index, fromQueue: true };
    }
    const inLibrary = [
      ...library.history,
      ...library.likes.map((entry) => library.history.find((row) => row.id === entry.id) ?? { id: entry.id, title: entry.id }),
    ];
    const all = [
      ...(state.search.results?.tracks ?? []),
      ...(state.home.shelves ?? []).flatMap((shelfEntry) => shelfEntry.entries),
      ...(state.entity.data?.tracks ?? []),
      ...library.history,
      ...inLibrary,
      ...player.state.queue,
    ];
    const found = all.find((entry) => (entry.id ?? entry.videoId) === id);
    return { track: found ?? { id, videoId: id, title: id }, index: -1, fromQueue: false };
  }

  /** Play a track and, when it came from a list, make that list the queue. */
  function playFromList(track, { list = null, index = 0, context = "" } = {}) {
    state.context = context;
    if (list && list.length) {
      const rows = list.filter((entry) => entry && (entry.id || entry.videoId));
      const start = Math.max(0, rows.findIndex((entry) => (entry.id ?? entry.videoId) === (track.id ?? track.videoId)));
      player.playTracks(rows, start >= 0 ? start : index);
    } else {
      player.playTracks([track], 0);
    }
    renderChrome();
  }

  /** The label for whatever entity screen is open, right now. */
  function entityContext() {
    const data = state.entity.data ?? {};
    const name = data.playlist?.name ?? data.artist?.name ?? data.album?.title ?? "";
    return name ? entityLabel(state.route.name, name) : "";
  }

  /** The list a row belongs to, so "play this" queues its neighbours. */
  function listContext() {
    const route = state.route;
    if (route.name === "search") return { list: state.search.results?.tracks ?? [], context: `Search · ${state.search.query}` };
    if (route.name === "home") {
      const quick = state.home.shelves.find((shelfEntry) => shelfEntry.title.toLowerCase().includes("quick"));
      const list = quick?.entries ?? state.home.shelves?.[0]?.entries ?? [];
      return { list, context: quick?.title ?? "Home" };
    }
    if (route.name === "library") {
      if (route.libTab === "liked") {
        const liked = library.likes.map((entry) => library.history.find((row) => row.id === entry.id) ?? { id: entry.id, title: entry.id });
        return { list: liked, context: "Liked songs" };
      }
      return { list: library.history, context: "History" };
    }
    if (["playlist", "artist", "album"].includes(route.name)) {
      const data = state.entity.data ?? {};
      const name = data.playlist?.name ?? data.artist?.name ?? data.album?.title ?? "";
      return { list: data.tracks ?? [], context: name ? entityLabel(route.name, name) : "" };
    }
    return { list: [], context: "" };
  }

  const handlers = {
    /* ------------------------------------------------------------ shell -- */
    "nav-tab": (target) => {
      const tab = target.dataset.tab;
      nav.selectTab(tab);
      const route =
        tab === "HOME" ? { name: "home", player: state.route.player, playerTab: state.route.playerTab }
        : tab === "SEARCH" ? { name: "search", query: state.search.query, player: state.route.player, playerTab: state.route.playerTab }
        : { name: "library", libTab: state.route.libTab ?? "playlists", player: state.route.player, playerTab: state.route.playerTab };
      go(route);
    },
    back: () => {
      const action = backAction({
        playerExpanded: state.route.player === "open",
        detailDepth: DETAIL_ROUTES.includes(state.route.name) ? 1 : 0,
        tab: nav.selectedTab,
        hasTabHistory: nav.hasTabHistory,
      });
      if (action === ShellBackAction.CollapsePlayer) go({ ...state.route, player: null });
      else if (action === ShellBackAction.ReturnToPreviousTab) handlers["nav-tab"]({ dataset: { tab: nav.tabHistory.at(-1) ?? "HOME" } });
      else router.back();
    },
    "open-settings": () => go({ ...state.route, name: "settings" }),
    "open-search": () => go({ ...state.route, name: "search" }),
    "open-about": () => go({ ...state.route, name: "about-legal" }),
    "open-settings-source": () => go({ ...state.route, name: "settings" }),
    "open-settings-data": () => go({ ...state.route, name: "settings" }),
    /* ---------------------------------------------------------- search -- */
    "search-submit": (target, event) => {
      event.preventDefault();
      const value = doc.querySelector("#search-input")?.value ?? "";
      go({ ...state.route, name: "search", query: value });
      runSearch(value, { focus: true });
    },
    "search-filter": (target) => {
      state.search.filter = target.dataset.filter;
      renderView();
    },
    "search-again": (target) => {
      const query = target.dataset.query ?? "";
      go({ ...state.route, name: "search", query });
      runSearch(query);
    },
    "clear-search": () => {
      go({ ...state.route, name: "search", query: "" });
      state.search = { query: "", filter: state.search.filter, status: "idle", results: null, error: null };
      renderView();
      focusWithoutScroll(doc.querySelector("#search-input"));
    },
    "retry-search": () => runSearch(state.search.query, { focus: true }),
    "forget-search": (target) => {
      const query = target.dataset.query;
      settings.set({ recentSearches: settings.get("recentSearches").filter((entry) => entry !== query) });
      renderView();
    },
    "clear-searches": () => {
      settings.set({ recentSearches: [] });
      renderView();
    },

    /* --------------------------------------------------------- library -- */
    "library-tab": (target) => go({ ...state.route, name: "library", libTab: target.dataset.tab }),
    "play-liked": () => {
      const list = library.likes.map((entry) => library.history.find((row) => row.id === entry.id) ?? { id: entry.id, title: entry.id });
      if (list.length) playFromList(list[0], { list, context: "Liked songs" });
    },
    "clear-history": () => {
      if (!state.confirmClearHistory) {
        state.confirmClearHistory = true;
        state.sheet = {
          kind: "confirm",
          options: {
            title: "Clear your listening history?",
            body: "Only the history list is removed. Your playlists and likes stay.",
            confirmLabel: "Clear history",
            action: "clear-history-confirm",
          },
        };
        renderSheet();
        return;
      }
    },
    "clear-history-confirm": () => {
      library.history = [];
      persistLibrary();
      state.sheet = null;
      state.confirmClearHistory = false;
      showToast("History cleared.");
      renderView();
    },
    // Two entry points, one form:
    //   the library's "New playlist" button (no input in its subtree) opens the
    //   sheet; the sheet's own form is what creates anything.
    "new-playlist": (target, event) => {
      event.preventDefault();
      if (target.tagName !== "FORM") {
        state.sheet = { kind: "playlist-name", mode: "create" };
        renderSheet();
        focusSheetField();
        return;
      }
      const name = (target.querySelector("input[name='name']")?.value ?? "").trim();
      if (!name) {
        focusSheetField();
        return;
      }
      const playlist = {
        id: `pl-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
        name: name.slice(0, 120),
        description: "",
        trackIds: [],
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };
      library.playlists = [playlist, ...library.playlists];
      persistLibrary();
      state.sheet = null;
      showToast(`Created “${playlist.name}”.`);
      renderView();
      renderChrome();
    },
    // Rename is the same sheet, prefilled. It used to be `window.prompt`, which
    // no part of this app can style, announce or translate.
    "rename-playlist": (target, event) => {
      event.preventDefault?.();
      const playlist = currentPlaylist();
      if (!playlist) return;
      if (target.tagName !== "FORM") {
        state.sheet = { kind: "playlist-name", mode: "rename", playlist: { ...playlist } };
        renderSheet();
        focusSheetField();
        return;
      }
      const name = (target.querySelector("input[name='name']")?.value ?? "").trim();
      if (!name) {
        focusSheetField();
        return;
      }
      playlist.name = name.slice(0, 120);
      playlist.updatedAt = Date.now();
      persistLibrary();
      state.sheet = null;
      showToast("Playlist renamed.");
      renderView();
      renderChrome();
    },
    "delete-playlist": () => {
      const playlist = currentPlaylist();
      if (!playlist) return;
      state.sheet = {
        kind: "confirm",
        options: {
          title: `Delete “${playlist.name}”?`,
          body: "The playlist is removed from this device. Liked songs are unaffected.",
          confirmLabel: "Delete playlist",
          action: "delete-playlist-confirm",
        },
      };
      renderSheet();
    },
    "delete-playlist-confirm": () => {
      const playlist = currentPlaylist();
      library.playlists = library.playlists.filter((entry) => entry.id !== playlist?.id);
      persistLibrary();
      state.sheet = null;
      showToast("Playlist deleted.");
      go({ name: "library", libTab: "playlists" });
    },
    // The album's own two buttons, after `AlbumViewModel.play` / `playShuffled`:
    // Play starts at the top of the list, Shuffle prepares the same queue at
    // index 0 and then turns shuffle on, so the first track stays in front the
    // way the app's `setShuffle` does. Whether a *previously* chosen shuffle
    // survives into this new queue is the app's `PERSIST_SHUFFLE_REPEAT`
    // setting, and it defaults to keeping it — so Play does not silently undo a
    // mode the listener chose.
    "play-entity": () => {
      const data = state.entity.data;
      if (!data?.tracks?.length) return;
      playFromList(data.tracks[0], { list: data.tracks, context: entityContext() });
    },
    "shuffle-entity": () => {
      const data = state.entity.data;
      if (!data?.tracks?.length) return;
      playFromList(data.tracks[0], { list: data.tracks, context: entityContext() });
      player.setShuffle(true);
      showToast("Shuffle on — every track plays once.");
      renderOverlay();
    },
    "open-playlist": (target) => go({ name: "playlist", id: target.dataset.id, player: state.route.player, playerTab: state.route.playerTab }),
    "open-entity": (target) => {
      const kind = target.dataset.kind;
      const id = target.dataset.id;
      if (!kind || !id) return;
      if (kind === "playlist") go({ name: "playlist", id });
      else if (kind === "artist") go({ name: "artist", id });
      else go({ name: "album", id });
    },
    "retry-home": () => (state.mood === "for-you" ? loadHome() : loadMood(state.mood)),
    "go-home": () => go({ name: "home" }),
    // The rail is the app's `HomeMood` rail: choosing a mood asks the source for
    // it. Re-pressing the selected mood does not re-ask — a tab that reloads
    // itself on every tap is a tab nobody trusts.
    "set-mood": (target) => {
      const id = target.dataset.mood ?? "for-you";
      if (id === state.mood) return;
      loadMood(id);
    },

    /* ---------------------------------------------------------- playback -- */
    "play-track": (target) => {
      const { track, index, fromQueue } = trackFromElement(target);
      if (fromQueue) {
        player.playAt(index);
        renderDock();
        renderOverlay();
        return;
      }
      const { list, context } = listContext();
      playFromList(track, { list, context });
    },
    "play-queue-row": (target) => {
      const index = Number(target.dataset.index);
      player.playAt(index);
      renderOverlay();
      renderDock();
    },
    "track-menu": (target) => {
      const { track } = trackFromElement(target);
      state.sheet = { kind: "track-menu", track, inQueue: target.closest(".dhun-tracklist--queue") !== null };
      renderSheet();
    },
    "menu-play-now": () => {
      const track = state.sheet?.track;
      if (track) player.playTrack(track);
      state.sheet = null;
      renderSheet();
      renderDock();
    },
    "menu-play-next": () => {
      const track = state.sheet?.track;
      if (track) player.enqueue(track, { next: true });
      state.sheet = null;
      showToast("Playing next.");
      renderSheet();
    },
    "menu-queue-last": () => {
      const track = state.sheet?.track;
      if (track) player.enqueue(track);
      state.sheet = null;
      showToast("Added to the queue.");
      renderSheet();
    },
    "menu-like": () => {
      const track = state.sheet?.track;
      if (track) toggleLike(track);
      state.sheet = null;
      renderSheet();
      renderView();
    },
    "menu-add-playlist": () => {
      const track = state.sheet?.track;
      state.sheet = { kind: "playlist-picker", track };
      renderSheet();
    },
    "menu-open-album": (target) => {
      state.sheet = null;
      go({ name: "album", id: target.dataset.id });
    },
    "menu-open-artist": (target) => {
      state.sheet = null;
      go({ name: "artist", id: target.dataset.id });
    },
    "menu-copy-link": async () => {
      const track = state.sheet?.track;
      const route = { name: "search", query: track?.title ?? "", player: null };
      const url = `${win.location?.origin ?? ""}${win.location?.pathname ?? ""}${formatRoute(route)}`;
      try {
        await win.navigator?.clipboard?.writeText?.(url);
        showToast("Link copied.");
      } catch {
        showToast(url);
      }
      state.sheet = null;
      renderSheet();
    },
    "add-to-playlist": (target) => {
      const playlist = library.playlists.find((entry) => entry.id === target.dataset.id);
      const track = state.sheet?.track;
      const id = track?.id ?? track?.videoId;
      if (!playlist || !id) return;
      if (!playlist.trackIds.includes(id)) playlist.trackIds.push(id);
      rememberTrack(track);
      playlist.updatedAt = Date.now();
      persistLibrary();
      state.sheet = null;
      showToast(`Added to “${playlist.name}”.`);
      renderSheet();
      renderView();
    },
    "close-sheet": () => {
      state.sheet = null;
      state.confirmClearHistory = false;
      renderSheet();
    },
    "toggle-play": () => {
      if (!player.current && player.state.queue.length === 0) {
        const { list, context } = listContext();
        if (list.length) playFromList(list[0], { list, context });
        return;
      }
      clearBanner("gesture");
      player.toggle();
      media.setPlaybackState(player.state.playing ? "playing" : "paused");
      renderDock();
      renderOverlay();
    },
    "next-track": () => {
      player.next();
      renderDock();
      renderOverlay();
    },
    "previous-track": () => {
      player.previous();
      renderDock();
      renderOverlay();
    },
    "toggle-shuffle": () => {
      const value = player.toggleShuffle();
      showToast(value ? "Shuffle on — every track plays once." : "Shuffle off.");
      renderOverlay();
    },
    "cycle-repeat": () => {
      const mode = player.cycleRepeat();
      showToast(mode === REPEAT.OFF ? "Repeat off." : mode === REPEAT.ALL ? "Repeat the queue." : "Repeat this track.");
      renderOverlay();
    },
    "toggle-like": () => {
      if (player.current) toggleLike(player.current);
      renderOverlay();
      renderView();
    },
    "toggle-mute": () => {
      player.setMuted(!player.state.muted);
      renderOverlay();
      renderDock();
    },
    "set-volume": (target) => {
      const value = Number(target.value) / 100;
      player.setVolume(value);
      const label = target.parentElement?.querySelector(".dhun-volume__value");
      if (label) label.textContent = `${Math.round(value * 100)}%`;
      if (state.route.name === "settings") renderView();
    },
    seek: (target) => {
      player.seekTo(Number(target.value) * 1000);
      renderOverlay();
    },
    "player-tab": (target) => {
      go({ ...state.route, player: "open", playerTab: target.dataset.tab });
    },
    "expand-player": () => go({ ...state.route, player: "open", playerTab: state.route.playerTab ?? "queue" }),
    "collapse-player": () => go({ ...state.route, player: null }),
    "retry-track": () => player.retry(),
    "open-equalizer": () => go({ ...state.route, name: "settings" }),
    "queue-up": (target) => {
      const index = Number(target.dataset.index);
      player.move(index, index - 1);
      renderOverlay();
    },
    "queue-down": (target) => {
      const index = Number(target.dataset.index);
      player.move(index, index + 1);
      renderOverlay();
    },
    "queue-remove": (target) => {
      const index = Number(target.dataset.index);
      player.removeAt(index);
      renderOverlay();
      renderDock();
    },
    "take-over-playback": () => {
      tabSync.takeOver();
      clearBanner("other-tab");
      player.play();
    },
    "dismiss-install": () => {
      settings.set({ installDismissed: true });
      renderDock();
    },
    install: async () => {
      const accepted = await pwa.promptInstall();
      showToast(accepted ? "Installing…" : "Your browser did not offer an install prompt.");
      renderDock();
    },
    "apply-update": () => pwa.applyUpdate(),
    "check-update": () => {
      const asked = pwa.checkForUpdate();
      showToast(asked ? "Checking for a new version…" : "This browser has no service worker to check.");
    },
    "show-shortcuts": () => {
      state.sheet = { kind: "shortcuts" };
      settings.set({ shortcutsSeen: true });
      renderSheet();
    },

    /* ---------------------------------------------------------- settings -- */
    "set-source": (target, event) => {
      if (event.type === "click") {
        settings.set({ sourceId: target.dataset.value });
        sources.refresh();
        state.home = { status: "idle", shelves: [], error: null };
        renderView();
        if (state.route.name === "home") loadHome();
      }
    },
    "set-theme": (target) => {
      settings.set({ theme: target.dataset.value });
      applyTheme();
      renderView();
    },
    "set-accent": (target) => {
      settings.set({ accent: target.dataset.value });
      applyTheme();
      renderView();
    },
    "toggle-resume": () => {
      settings.set({ resumeOnLaunch: !settings.get("resumeOnLaunch") });
      renderView();
    },
    "toggle-compressor": () => {
      settings.set({ normalizeVolume: !settings.get("normalizeVolume") });
      applyEqualizer();
      renderView();
    },
    "toggle-lyrics": () => {
      settings.set({ lyricsEnabled: !settings.get("lyricsEnabled") });
      renderView();
      if (player.current) loadLyrics(player.current);
    },
    "toggle-romanize": () => {
      settings.set({ romanizeLyrics: !settings.get("romanizeLyrics") });
      renderView();
      renderOverlay();
    },
    "toggle-equalizer": () => {
      settings.set({ equalizerEnabled: !settings.get("equalizerEnabled") });
      applyEqualizer();
      renderView();
      renderOverlay();
    },
    "set-eq-preset": (target, event) => {
      if (event.type !== "click") return;
      const id = target.dataset.value;
      if (id === CUSTOM_ID) {
        settings.set({ equalizerPreset: CUSTOM_ID });
      } else {
        const preset = PRESETS.find((entry) => entry.id === id);
        if (preset) {
          settings.set({
            equalizerPreset: id,
            equalizerGains: clampGains(preset.gainsDb),
            equalizerPreampDb: preset.preampDb ?? 0,
          });
        }
      }
      applyEqualizer();
      renderView();
    },
    "set-preamp": (target) => {
      settings.set({ equalizerPreampDb: Number(target.value), equalizerPreset: CUSTOM_ID });
      applyEqualizer();
      const value = target.parentElement?.querySelector(".dhun-slider-row__value");
      if (value) value.textContent = `${target.value} dB`;
    },
    "set-band": (target) => {
      const index = Number(target.dataset.index);
      const gains = [...settings.get("equalizerGains")];
      gains[index] = Number(target.value);
      settings.set({
        equalizerGains: gains,
        equalizerPreset: matchingPreset(gains, settings.get("equalizerPreampDb"))?.id ?? CUSTOM_ID,
      });
      applyEqualizer();
      const label = target.parentElement?.querySelector(".dhun-eq__gain");
      if (label) label.textContent = `${Number(target.value) > 0 ? "+" : ""}${Number(target.value).toFixed(1)}`;
    },
    "reset-eq": () => {
      settings.set({ equalizerGains: clampGains(PRESETS[0].gainsDb), equalizerPreset: CUSTOM_ID, equalizerPreampDb: 0 });
      applyEqualizer();
      renderView();
    },
    "test-endpoint": async () => {
      sources.refresh();
      if (!sources.endpoint.configured) {
        showToast("Add an endpoint address first.");
        return;
      }
      showToast("Asking the endpoint…");
      const result = await sources.endpoint.health();
      showToast(
        result.ok
          ? `Endpoint answered${result.version ? ` (v${result.version})` : ""}.`
          : `Endpoint did not answer: ${failureSummary(result)} ${failureDetail(result) ?? ""}`.trim(),
      );
      renderView();
    },
    "endpoint-url": () => {},
    "export-library": () => {
      const document_ = exportLibrary(library, { settings: settings.all });
      const text = JSON.stringify(document_, null, 2);
      const blob = new Blob([text], { type: "application/json" });
      const url = win.URL.createObjectURL(blob);
      const anchor = doc.createElement("a");
      anchor.href = url;
      anchor.download = `dhun-library-${new Date().toISOString().slice(0, 10)}.json`;
      doc.body.append(anchor);
      anchor.click();
      anchor.remove();
      win.URL.revokeObjectURL(url);
      showToast("Library exported.");
    },
    "import-library": async (target) => {
      const file = target.files?.[0];
      if (!file) return;
      const text = await file.text();
      const result = importLibrary(text);
      if (!result.ok) {
        showToast(result.message);
        return;
      }
      // Merge rather than replace: an import must never silently delete a
      // playlist the visitor made since their last export.
      const existing = new Set(library.playlists.map((entry) => entry.id));
      library.playlists = [...library.playlists, ...result.library.playlists.filter((entry) => !existing.has(entry.id))];
      const likedIds = new Set(library.likes.map((entry) => entry.id));
      library.likes = [...library.likes, ...result.library.likes.filter((entry) => !likedIds.has(entry.id))];
      const historyIds = new Set(library.history.map((entry) => entry.id));
      library.history = [...library.history, ...result.library.history.filter((entry) => !historyIds.has(entry.id))].slice(0, MAX_HISTORY);
      if (result.settings) settings.set(result.settings);
      persistLibrary();
      applyTheme();
      applyEqualizer();
      showToast(`Imported ${result.counts.playlists} playlist(s), ${result.counts.likes} like(s), ${result.counts.history} history row(s).`);
      renderView();
    },
    "clear-library": () => {
      state.confirmClearLibrary = true;
      renderView();
    },
    "clear-library-cancel": () => {
      state.confirmClearLibrary = false;
      renderView();
    },
    "clear-library-confirm": async () => {
      await libraryStore.clear();
      library = emptyLibrary();
      state.confirmClearLibrary = false;
      clearSession();
      showToast("Library cleared on this device.");
      renderView();
      renderChrome();
    },
    "clear-stream-cache": () => {
      player.clearStreamCache();
      showToast("Stream cache cleared.");
      renderView();
    },
  };

  function toggleLike(track) {
    const id = track?.id ?? track?.videoId;
    if (!id) return;
    if (isLiked(track)) {
      library.likes = library.likes.filter((entry) => entry.id !== id);
      showToast("Removed from liked.");
    } else {
      library.likes = [{ id, at: Date.now() }, ...library.likes];
      rememberTrack(track);
      if (!library.history.some((entry) => entry.id === id)) {
        library.history = [
          {
            id,
            at: Date.now(),
            title: track.title ?? id,
            artist: track.artist ?? "",
            artwork: track.artwork ?? "",
            durationMs: track.durationMs ?? 0,
          },
          ...library.history,
        ].slice(0, MAX_HISTORY);
      }
      showToast("Added to liked.");
    }
    persistLibrary();
  }

  function currentPlaylist() {
    return library.playlists.find((entry) => entry.id === state.route.id) ?? null;
  }

  /* ----------------------------------------------------------- theme/EQ -- */

  /**
   * The appearance settings are written to the **document element**, not to
   * `#app`.
   *
   * `tokens.css` keys its palettes on the root (`:root, [data-dhun-theme=…]`),
   * and `body { background: var(--dhun-background) }` is painted by an element
   * *above* `#app`. Putting the attribute on `#app` therefore themed the chrome
   * and left the page: choosing Light turned the app bar and the nav pale while
   * the backdrop stayed dark, and the light-theme ink on it was unreadable —
   * the meta line and the Shuffle button simply vanished. The root is also where
   * `index.html` carries its pre-JavaScript default, so there is one place the
   * attribute lives.
   */
  function applyTheme() {
    const choice = settings.get("theme");
    const resolved =
      choice === "system"
        ? win.matchMedia?.("(prefers-color-scheme: light)")?.matches
          ? "light"
          : "dark"
        : choice;
    const themeRoot = doc.documentElement ?? root;
    themeRoot.dataset.dhunTheme = resolved;
    themeRoot.dataset.dhunAccent = settings.get("accent");
    themeRoot.dataset.dhunThemeChoice = choice;
  }

  function applyEqualizer() {
    const enabled = settings.get("equalizerEnabled");
    const applied = engine.setEqualizer({
      enabled,
      gains: settings.get("equalizerGains"),
      preamp: settings.get("equalizerPreampDb"),
      compressor: settings.get("normalizeVolume"),
    });
    if (enabled && !applied && platform.audioContext) {
      showToast("The equaliser is on, but this browser will not let DHUN filter the stream.");
    }
  }

  /* --------------------------------------------------------- subscriptions */

  settings.subscribe?.((next) => {
    (doc.documentElement ?? root).dataset.dhunAccent = next.accent;
  });

  player.subscribe(() => {
    // The dock and the overlay are cheap; the view is not, so it renders only
    // when the current track actually changes what a list shows.
    renderDock();
    renderOverlay();
    updateLyricHighlight();
    if (state.route.player !== "open") renderViewThrottled();
  });

  let viewThrottle = null;
  function renderViewThrottled() {
    if (viewThrottle) return;
    viewThrottle = win.setTimeout?.(() => {
      viewThrottle = null;
      renderView();
    }, 400);
  }

  function updateLyricHighlight() {
    if (state.route.player !== "open" || state.route.playerTab !== "lyrics") return;
    if (!state.lyrics.lines.length) return;
    const index = activeLineIndex(state.lyrics.lines, player.state.positionMs);
    if (index === state.lyrics.activeIndex) return;
    state.lyrics.activeIndex = index;
    const lines = doc.querySelectorAll(".dhun-lyrics__line");
    lines.forEach((line, position) => line.dataset.active = position === index ? "true" : "false");
  }

  /* ------------------------------------------------------------- keyboard */

  let chordBuffer = null;
  let chordTimer = null;

  function onKeydown(event) {
    if (event.key === "Tab" && trapModalTab(event)) return;
    const { action, pending, swallowed } = matchShortcut(event, {
      capabilities: { hasTrack: Boolean(player.current) },
      buffer: chordBuffer,
    });
    if (pending) {
      chordBuffer = pending;
      if (chordTimer) win.clearTimeout?.(chordTimer);
      chordTimer = win.setTimeout?.(() => {
        chordBuffer = null;
      }, CHORD_TIMEOUT_MS);
      event.preventDefault();
      return;
    }
    chordBuffer = null;
    if (!action) return;
    if (swallowed) event.preventDefault();
    const map = {
      "play-pause": "toggle-play",
      next: "next-track",
      previous: "previous-track",
      "seek-forward": null,
      "seek-back": null,
      "volume-up": null,
      "volume-down": null,
      mute: "toggle-mute",
      shuffle: "toggle-shuffle",
      repeat: "cycle-repeat",
      like: "toggle-like",
      search: "focus-search",
      home: "go-home-tab",
      library: "go-library-tab",
      close: "collapse-player",
      help: "show-shortcuts",
    };
    switch (map[action.id]) {
      case "focus-search":
        if (state.route.name !== "search") go({ ...state.route, name: "search" });
        focusWithoutScroll(doc.querySelector("#search-input"));
        break;
      case "go-home-tab":
        handlers["nav-tab"]({ dataset: { tab: "HOME" } });
        break;
      case "go-library-tab":
        handlers["nav-tab"]({ dataset: { tab: "LIBRARY" } });
        break;
      case "collapse-player":
        if (state.sheet) handlers["close-sheet"]();
        else if (state.route.player === "open") handlers["collapse-player"]();
        break;
      default:
        if (action.id === "seek-forward") player.seekBy(5000);
        else if (action.id === "seek-back") player.seekBy(-5000);
        else if (action.id === "volume-up") player.setVolume(Math.min(1, player.state.volume + 0.05));
        else if (action.id === "volume-down") player.setVolume(Math.max(0, player.state.volume - 0.05));
        else if (map[action.id]) handlers[map[action.id]]?.();
        break;
    }
    renderOverlay();
    renderDock();
  }

  /* ----------------------------------------------------------------- boot */

  function renderShell() {
    root.innerHTML = html`
      <a class="dhun-skip-link" href="#dhun-view">Skip to content</a>
      <div class="dhun-shell">
        <nav class="dhun-rail" id="dhun-rail" aria-label="Main"></nav>
        <div class="dhun-content">
          <div class="dhun-master">
            <header class="dhun-topbar" id="dhun-topbar"></header>
            <main class="dhun-scroll" id="dhun-view" data-scroll="true" tabindex="-1"></main>
          </div>
        </div>
      </div>
      <div class="dhun-dock-wrap">
        <div class="dhun-dock" id="dhun-dock"></div>
        <nav class="dhun-bottom-nav" id="dhun-tabs" aria-label="Main"></nav>
      </div>
      <div class="dhun-overlay-mount" id="dhun-overlay"></div>
      <div class="dhun-sheet-mount" id="dhun-sheet"></div>
      <div class="dhun-toast-mount" id="dhun-toast" aria-live="polite"></div>
    `;
  }

  function wire() {
    delegate(root, "click", "[data-action]", (event, target) => {
      const action = target.dataset.action;
      const handler = handlers[action];
      if (!handler) {
        // A button that does nothing is a bug; say so in the console instead of
        // leaving it silent.
        win.console?.warn?.(`DHUN: no handler for action "${action}"`);
        return;
      }
      if (target.tagName === "INPUT" && target.type === "range") return;
      handler(target, event);
    });
    delegate(root, "click", "[data-action][data-value]", (event, target) => {
      const handler = handlers[target.dataset.action];
      if (handler && event.type === "click") handler(target, event);
    });
    delegate(root, "input", "input[type='range'][data-action]", (event, target) => {
      handlers[target.dataset.action]?.(target, event);
    });
    delegate(root, "change", "input[type='file'][data-action]", (event, target) => {
      handlers[target.dataset.action]?.(target, event);
    });
    delegate(root, "submit", "form[data-action]", (event, target) => {
      event.preventDefault();
      handlers[target.dataset.action]?.(target, event);
    });
    delegate(root, "input", "#search-input", (event, target) => {
      // Debounced incremental search: typing should feel immediate, and the
      // network should not see every keystroke.
      if (searchTimer) win.clearTimeout?.(searchTimer);
      const value = target.value;
      searchTimer = win.setTimeout?.(() => {
        go({ ...state.route, name: "search", query: value });
        runSearch(value);
      }, 350);
    });
    win.addEventListener("keydown", onKeydown);
    win.addEventListener("online", () => renderDock());
    win.addEventListener("offline", () => renderDock());
    win.addEventListener("beforeunload", () => {
      saveSession();
      writeSession(readSession() ?? null);
    });
    win.matchMedia?.("(prefers-color-scheme: light)")?.addEventListener?.("change", () => {
      if (settings.get("theme") === "system") applyTheme();
    });
  }

  let searchTimer = null;

  renderShell();
  applyTheme();
  restoreSession();
  wire();
  pwa.start();
  pwa.announceVersion(WEB_VERSION);
  router.start();
  renderChrome();
  renderSheet();
  applyEqualizer();
  media.bind({
    play: () => player.toggle(),
    pause: () => engine.pause(),
    stop: () => engine.stop(),
    next: () => player.next(),
    previous: () => player.previous(),
    seekBackward: (details) => player.seekBy(-(details?.seekOffset ?? 10) * 1000),
    seekForward: (details) => player.seekBy((details?.seekOffset ?? 10) * 1000),
    seekTo: (details) => {
      if (typeof details?.seekTime === "number" && player.state.durationMs) {
        player.seekTo(details.seekTime * 1000);
      }
    },
  });
  // Media Session's position state is only valid while something is playing;
  // publishing a position for a paused player makes some platforms show a
  // stuck progress bar.
  player.subscribe((playerState) => {
    if (playerState.playing) {
      media.position({ positionMs: playerState.positionMs, durationMs: playerState.durationMs });
    }
    if (playerState.needsGesture) showBanner("gesture");
  });
  /*
   * The first-run strip.
   *
   * Two things were wrong with it. It said "Search and play from YouTube Music"
   * — the one claim this app cannot make from its own chrome, since playback
   * depends on an origin nobody here controls, and the project's rule is that no
   * surface promises it (see `docs/web-player/PHASE-02-PLAYBACK-POC.md`). And it
   * was permanent: nothing ever called `clearBanner("info")`, so "Welcome to
   * DHUN" sat above the dock on every route for the whole session. Orientation
   * is for the first minute; it is dismissed by the visitor's first interaction.
   */
  showBanner("info", {
    title: "DHUN in the browser",
    body: "Search YouTube Music anonymously, or point DHUN at your own data source. Your library stays in this browser, and when a source will not answer, DHUN says so instead of pretending.",
  });
  dismissWelcomeOnFirstInteraction();

  return { state, player, engine, settings, sources, router, pwa, platform, libraryStore: () => libraryStore, library: () => library };
}

boot();
