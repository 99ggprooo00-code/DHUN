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
import { createSourceManager, failureHelp, SOURCE_KIND, ENDPOINT_CONTRACT } from "./source.js";
import { createAudioEngine, AUDIO_STATUS } from "./audio.js";
import { createPlayer, REPEAT } from "./player.js";
import { createMediaSession } from "./mediasession.js";
import { createTabSync, openTabChannel } from "./tabsync.js";
import { createPwa } from "./pwa.js";
import { createRouter, parseRoute, formatRoute, sameRoute, LIBRARY_TABS } from "./router.js";
import { BANDS_HZ, PRESETS, CUSTOM_ID, clampGains, matchingPreset } from "./equalizer.js";
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

  function showToast(message) {
    state.toast = message;
    renderSheet();
    if (toastTimer) win.clearTimeout?.(toastTimer);
    toastTimer = win.setTimeout?.(() => {
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

  /** The failure shape `views.errorState` renders: message + the helpful half. */
  function asStateError(result) {
    return result ? { message: result.message, help: failureHelp(result) } : null;
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
      state.home = { status: "error", shelves: [], error: asStateError(result) };
    }
    renderView();
  }

  async function loadEntity(kind, id) {
    state.entity = { status: "loading", kind, id, data: null, error: null };
    renderView();
    const result = await sources.browse(id);
    if (result.ok) {
      state.entity = { status: "ready", kind: result.kind ?? kind, id, data: result, error: null };
    } else {
      state.entity = { status: "error", kind, id, data: null, error: asStateError(result) };
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
      state.search = { query, filter: state.search.filter, status: "error", results: null, error: asStateError(result) };
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
    const active = sources.describeActive();
    const chosen = settings.get("sourceId");
    const wantsEndpoint = chosen === SOURCE_KIND.ENDPOINT && !sources.endpoint.configured;

    if (lastUsed.kind === SOURCE_KIND.DEMO && lastUsed.reason) {
      return {
        tone: "warn",
        title: "Showing the demo catalogue",
        body: `${active.detail} The real source did not answer: ${lastUsed.reason.message}`,
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
      sourceLabel: sources.lastUsed.kind === SOURCE_KIND.DEMO ? "Demo catalogue" : null,
    };
  }

  function onTrackStart(track) {
    tabSync.claim(track);
    media.update(track);
    media.setPlaybackState("playing");
    const id = track.id ?? track.videoId;
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
    showToast(`${track.title} could not be played: ${failure.message}`);
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
      state.lyrics = { status: "error", lines: [], activeIndex: -1, message: "fetch() is unavailable" };
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
      state.lyrics = {
        status: "error",
        lines: [],
        activeIndex: -1,
        message: error?.message ?? "the lyrics request failed",
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
          source: sources.lastUsed,
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
      case "playlist":
        markup = views.playlistScreen({
          playlist: state.entity.data?.playlist ?? null,
          tracks: state.entity.data?.tracks ?? [],
          currentId,
          isOwn: library.playlists.some((entry) => entry.id === route.id),
          missing: Number(state.entity.data?.missing ?? 0),
        });
        break;
      case "artist":
        markup = views.artistScreen({
          artist: state.entity.data?.artist ?? null,
          tracks: state.entity.data?.tracks ?? [],
          albums: state.entity.data?.albums ?? [],
          currentId,
        });
        break;
      case "album":
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
      default:
        markup = views.homeScreen({
          shelves: state.home.shelves,
          loading: state.home.status === "loading",
          error: state.home.error,
          currentId,
          demoTracks: [],
          source: sources.lastUsed,
        });
        break;
    }
    setRegion("dhun-view", markup);
    renderChrome();
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
    setRegion(
      "dhun-topbar",
      html`<div class="dhun-topbar__lead">
          ${DETAIL_ROUTES.includes(route.name) || route.player === "open"
            ? html`<button type="button" class="dhun-icon-button" data-action="back" aria-label="Back">${views.icon("ArrowBack", { size: 22 })}</button>`
            : html``}
          <h1 class="dhun-topbar__title">${title}</h1>
          ${views.sourceBadge(sources.describeActive())}
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

  function renderDock() {
    const vm = playerViewModel();
    const banner = renderBanner();
    setRegion(
      "dhun-dock",
      html`${banner}
        ${vm.track ? miniPlayer(vm, { context: state.context }) : html``}`,
    );
  }

  function renderBanner() {
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
    if (state.banner?.kind === "info") {
      return views.notice({ tone: "info", title: state.banner.title ?? "", body: state.banner.body ?? "" });
    }
    return html``;
  }

  function renderOverlay() {
    const vm = playerViewModel();
    const open = state.route.player === "open";
    setRegion("dhun-overlay", open && vm.track ? fullPlayer(vm) : html``);
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
    } else if (sheet?.kind === "confirm") {
      markup = views.confirmSheet(sheet.options);
    } else if (sheet?.kind === "shortcuts") {
      markup = views.shortcutsSheet({ platform: win.navigator?.platform ?? "" });
    }
    // `markup` is a Markup fragment, and every fragment is truthy — including an
    // empty one, because it is an object. Ask for its text.
    setRegion("dhun-sheet", String(markup).length ? html`<div class="dhun-scrim">${markup}</div>` : html``);
    setRegion("dhun-toast", state.toast ? views.toast(state.toast) : html``);
  }

  function equalizerViewModel() {
    const gains = settings.get("equalizerGains");
    const activePresetId = matchingPreset(gains, settings.get("equalizerPreampDb"))?.id ?? CUSTOM_ID;
    const processed = engine.state.processed;
    const enabled = settings.get("equalizerEnabled");
    return {
      presets: [{ id: CUSTOM_ID, name: "Custom" }, ...PRESETS.map((preset) => ({ id: preset.id, name: preset.name }))],
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
    onRoute: (route) => {
      const previous = state.route;
      state.route = route;
      applyTheme();
      if (!sameRoute(previous, route) || previous === null) {
        // Data fetching is driven by the route, so a deep link and a click take
        // exactly the same path.
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
      renderView();
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
      return { list: data.tracks ?? [], context: name ? `Album · ${name}` : "" };
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
    "new-playlist": (target, event) => {
      event.preventDefault();
      const input = target.querySelector("input[name='name']");
      const name = (input?.value ?? "").trim();
      if (!name) return;
      const playlist = {
        id: `pl-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
        name,
        description: "",
        trackIds: [],
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };
      library.playlists = [playlist, ...library.playlists];
      persistLibrary();
      state.sheet = null;
      showToast(`Created “${name}”.`);
      renderView();
      renderChrome();
    },
    "rename-playlist": () => {
      const playlist = currentPlaylist();
      if (!playlist) return;
      const name = win.prompt?.("Rename playlist", playlist.name);
      if (!name) return;
      playlist.name = name.trim().slice(0, 120) || playlist.name;
      playlist.updatedAt = Date.now();
      persistLibrary();
      renderView();
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
    "play-entity": () => {
      const data = state.entity.data;
      if (data?.tracks?.length) playFromList(data.tracks[0], { list: data.tracks, context: "Album · " });
    },
    "queue-entity": () => {
      const data = state.entity.data;
      if (data?.tracks?.length) {
        player.enqueueMany(data.tracks);
        showToast(`Added ${data.tracks.length} track(s) to the queue.`);
      }
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
    "retry-home": () => loadHome(),
    "go-home": () => go({ name: "home" }),

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
      showToast(result.ok ? `Endpoint answered${result.version ? ` (v${result.version})` : ""}.` : `Endpoint did not answer: ${result.message}`);
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

  function applyTheme() {
    const choice = settings.get("theme");
    const resolved =
      choice === "system"
        ? win.matchMedia?.("(prefers-color-scheme: light)")?.matches
          ? "light"
          : "dark"
        : choice;
    root.dataset.dhunTheme = resolved;
    root.dataset.dhunAccent = settings.get("accent");
    root.dataset.dhunThemeChoice = choice;
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
    root.dataset.dhunAccent = next.accent;
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
  showBanner("info", {
    title: "Welcome to DHUN in the browser",
    body: "Search and play from YouTube Music anonymously, or point DHUN at your own data source. Your library stays in this browser.",
  });

  return { state, player, engine, settings, sources, router, pwa, platform, libraryStore: () => libraryStore, library: () => library };
}

boot();
