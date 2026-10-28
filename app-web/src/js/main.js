/**
 * Boot and wiring.
 *
 * This file is the only place that knows about all the others. It owns:
 *
 *   - the state object (route, source status, the three screens' data, library);
 *   - the render pass (bar, screen, dock, overlay, sheet, toast);
 *   - the action table (one delegated listener, `data-action` values);
 *   - the router subscription;
 *   - the keyboard map.
 *
 * Everything below it is pure or close to it, which is what makes the screens
 * testable without a browser and the player testable without a network.
 *
 * THE RENDER RULE. A screen render replaces the screen's nodes, so focus and the
 * caret are captured before the swap and restored after it. The previous build
 * learned this the hard way: pressing Enter in Search rebuilt the view and left
 * focus on `<body>`, so the second half of a query could not be typed.
 */

import { html, delegate, listen, focusWithoutScroll } from "./dom.js";
import { icon, notice } from "./components.js";
import * as views from "./views.js";
import * as components from "./components.js";
import { appBar, bottomNav, miniError, miniPlayer, navRail } from "./shell.js";
import { fullPlayer, fullPlayerRenderKey, updateFullPlayerProgress } from "./player-ui.js";
import { API_ORIGIN, HOME_RAILS, createSource, describeAttempts, describeFailure, SourceError, FAILURE_KIND } from "./api.js";
import { demoEntity, demoHome, searchDemo } from "./demo.js";
import {
  addToPlaylist,
  createPlaylist,
  createStorage,
  deletePlaylist,
  historySongs,
  likedSongs,
  playlistSongs,
  rememberPlay,
  rememberSearch,
  removeFromPlaylist,
  renamePlaylist,
  toggleLike,
} from "./library.js";
import { attachMediaSession, createPlayer, currentTrack, STATUS } from "./player.js";
import { percent } from "./format.js";
import { DETAIL_ROUTES, formatRoute, parseRoute, tabFor, titleFor } from "./router.js";
import { WEB_LEGAL_DOCUMENTS, webLegalById } from "./data/web-legal.js";
import { legalBody } from "./legal.js";
import { WEB_VERSION } from "./version.js";
import { createPwa } from "./pwa.js";
import { matchShortcut } from "./shortcuts.js";
import { handleFileImport } from "./transfer.js";

const NETWORK_KINDS = new Set([FAILURE_KIND.network, FAILURE_KIND.timeout, FAILURE_KIND.offline]);
const SEARCH_FILTERS = ["songs", "albums", "artists", "playlists"];

/**
 * Is this page inside a frame?
 *
 * A cross-origin parent makes `win.top` throw when read; that is treated as
 * "framed", because a page that cannot prove it is top-level should not start.
 */
export function isFramed(win = globalThis) {
  try {
    return win.self !== win.top;
  } catch {
    return true;
  }
}

export function boot({ win = globalThis, doc = globalThis.document } = {}) {
  const root = doc.querySelector("#app");

  // The framing rule runs before storage is touched or a single node is
  // rendered. An embedded copy must not read the visitor's library, and must
  // not put a second player in a page DHUN does not control.
  if (isFramed(win)) {
    root.innerHTML = html`${views.framedRefusal()}`;
    return { state: null, player: null, go: () => {}, destroy: () => {} };
  }
  let disposed = false;
  let suspended = false;
  let pwa = null;
  const componentLifetime = new AbortController();
  const cleanups = new Set();
  const isActive = () => !disposed && !suspended;
  const own = (stop) => { cleanups.add(stop); return stop; };
  const whileActive = (fn) => (...args) => { if (isActive()) return fn(...args); };
  const audio = doc.querySelector("#dhun-audio");
  const storage = createStorage();
  // Which of the catalogue's instances answered last time is remembered, so a
  // returning visitor's first request goes to the host that worked for them
  // instead of re-learning that the first one is down.
  const source = createSource({
    preferred: storage.load().settings.sourceId ?? "",
    onOriginChange: (origin) => {
      if (disposed) return;
      state.library = { ...state.library, settings: { ...state.library.settings, sourceId: origin.id } };
      save();
    },
  });

  const state = {
    route: parseRoute(win.location.hash),
    library: storage.load(),
    source: { mode: "unknown", error: null },
    home: { status: "idle", rails: [], error: null },
    search: { query: "", filter: "songs", status: "idle", results: [], error: null, requestId: 0 },
    entity: { status: "idle", kind: null, id: null, data: null, error: null, requestId: 0 },
    sheet: null,
    toast: null,
    toastTimer: null,
    tab: "HOME",
    pendingHeadingFocus: null,
    pendingFieldFocus: null,
  };

  const settings = () => state.library.settings;

  /* ------------------------------------------------------------- player -- */

  const player = createPlayer({
    audio,
    resolve: async (song) => {
      if (song?.demo) return song; // no stream; the player reports it plainly
      const fresh = await source.song(song.id, { signal: componentLifetime.signal });
      return { ...song, stream: fresh.stream, streamQuality: fresh.streamQuality, image: song.image || fresh.image };
    },
    onError: () => {},
  });

  player.setVolume(settings().volume);
  if (settings().repeat) player.state.repeat = settings().repeat;
  if (settings().shuffle) player.state.shuffle = Boolean(settings().shuffle);

  audio.volume = settings().volume;

  /* ------------------------------------------------------------ helpers -- */

  function save() {
    const saved = storage.save(state.library);
    state.library = storage.load(); // including the existing snapshot-pruning rule
    return saved;
  }

  function showSaveToast(message, saved) {
    showToast(saved ? message : "Changed for this visit only — not saved to browser storage. Export a backup before closing.", saved ? "info" : "warn");
  }

  function commitLibrary(next) {
    if (!storage.save(next, { requirePersistence: true })) {
      const error = "Not saved. This browser could not confirm the change. Your in-tab library was kept. Allow storage or free space and try again; saved data may still exist. Browser site-data controls can clear the origin separately.";
      if (state.sheet) {
        state.sheet = { ...state.sheet, error };
        renderSheet();
      }
      renderView();
      // Rebuilding a failed confirmation must not strand keyboard focus on body.
      const dialog = state.sheet ? dialogRoot() : null;
      if (dialog && !dialog.contains(doc.activeElement)) focusWithoutScroll(focusablesIn(dialog)[0] ?? dialog);
      showToast("Not saved. The library in this tab was kept; allow storage or free space and retry.", "warn");
      return false;
    }
    state.library = storage.load();
    return true;
  }

  function showToast(message, tone = "info") {
    if (!isActive()) return;
    state.toast = { message, tone };
    renderToast();
    if (state.toastTimer) win.clearTimeout(state.toastTimer);
    state.toastTimer = win.setTimeout(() => {
      if (!isActive()) return;
      state.toast = null;
      renderToast();
    }, 3200);
  }

  function go(route, { replace = false } = {}) {
    if (!isActive()) return;
    const href = formatRoute(route);
    if (replace) win.history.replaceState(null, "", href);
    else win.location.hash = href;
    if (replace) onRoute();
  }

  function routeFor(name, extra = {}) {
    return { ...state.route, name, id: null, query: "", libTab: null, doc: null, ...extra, player: state.route.player, playerTab: state.route.playerTab };
  }

  function replaceSearchRoute(query) {
    const href = formatRoute({ ...state.route, name: "search", query });
    if (win.location.hash !== href) win.history.replaceState(null, "", href);
    state.route = parseRoute(href);
    state.tab = tabFor(state.route, state.tab) ?? state.tab;
    retireCatalogueRequest("home");
    retireCatalogueRequest("entity");
  }

  /** The link a card points at. One place, so a card cannot point somewhere else. */
  function hrefFor(item) {
    if (item.kind === "album") return `#/album/${encodeURIComponent(item.id)}`;
    if (item.kind === "artist") return `#/artist/${encodeURIComponent(item.id)}`;
    if (item.kind === "playlist") return `#/playlist/${encodeURIComponent(item.id)}`;
    return `#/album/${encodeURIComponent(item.album?.id ?? "")}`;
  }

  function sourceLabel() {
    if (state.source.mode === "sample") return "Sample catalogue";
    if (state.source.mode === "live") return "JioSaavn";
    return "Catalogue";
  }

  function sourceBadge() {
    return {
      label: sourceLabel(),
      tone: state.source.mode === "sample" ? "sample" : state.source.mode === "live" ? "live" : "idle",
      detail: state.source.mode === "sample" && state.source.error ? describeFailure(state.source.error).title : "JioSaavn public API",
    };
  }

  function noteFailure(error) {
    state.source.error = error;
    state.source.mode = "sample";
    state.source.attempts = Array.isArray(error?.attempts) ? error.attempts : [];
  }

  /**
   * The sentence under "Sample catalogue".
   *
   * It names the instances that were tried and what each said, because the old
   * wording ("the JioSaavn API could not be reached from this network") was
   * true of one hostname and useless to anyone trying to work out whether the
   * fault was theirs. Nothing here is guessed: each clause is an attempt the
   * app made.
   */
  function sampleNote() {
    const record = describeAttempts(state.source.attempts);
    if (!record) {
      return "No catalogue instance answered, so these are example rows — nothing in this list has audio. The live catalogue returns as soon as one answers.";
    }
    return `No catalogue instance answered, so these are example rows — nothing in this list has audio. Tried: ${record}.`;
  }

  /** What each instance answered on the last request — for Settings and errors. */
  function sourceStatus() {
    const status = source.status?.() ?? { origin: null, origins: [], attempts: [] };
    return {
      origin: status.origin,
      origins: status.origins,
      attempts: state.source.attempts?.length ? state.source.attempts : status.attempts,
    };
  }

  /** Tracks for the queue from whatever list the press came from. */
  function listContext(context, fallback = []) {
    if (!context || context === "hero") return fallback;
    const [kind, id] = String(context).split(":");
    if (kind === "album" && state.entity.data?.album?.id === id) return state.entity.data.album.songs;
    if (kind === "artist" && state.entity.data?.artist?.id === id) return state.entity.data.songs;
    if (kind === "playlist" && state.entity.data?.playlist?.id === id) return state.entity.data.playlist.songs;
    if (kind === "collection") return playlistSongs(state.library, state.library.playlists.find((entry) => entry.id === id));
    if (kind === "likes") return likedSongs(state.library);
    if (kind === "history") return historySongs(state.library);
    if (kind === "search") return state.search.results;
    return fallback;
  }

  const playerVm = () => {
    const track = currentTrack(player.state);
    return {
      track,
      queue: player.queueView(),
      status: player.state.status,
      position: player.state.position,
      duration: player.state.duration || track?.duration || 0,
      percent: percent(player.state.position, player.state.duration || track?.duration || 0),
      volume: player.state.volume,
      muted: player.state.muted,
      shuffle: player.state.shuffle,
      repeat: player.state.repeat,
      tab: state.route.playerTab ?? "queue",
      context: contextForDisplay(player.state.context),
      sourceLabel: track ? sourceLabel() : "",
      liked: track ? state.library.likes.includes(track.id) : false,
      playing: player.state.status === STATUS.playing,
      error: player.state.error,
    };
  };

  /* -------------------------------------------------------------- source -- */

  // Only screen-owned catalogue requests live here. Player resolution is
  // independent, so navigation never cancels its stream lookup.
  const catalogueRequests = { home: null, search: null, entity: null };
  let searchComposition = null;

  function isSearchComposing() {
    return Boolean(searchComposition && state.route.name === "search"
      && searchComposition.requestId === state.search.requestId
      && doc.getElementById("search-input") === searchComposition.target);
  }

  function beginCatalogueRequest(kind) {
    if (kind === "search") searchComposition = null;
    catalogueRequests[kind]?.abort();
    const controller = new AbortController();
    catalogueRequests[kind] = controller;
    return {
      signal: controller.signal,
      isCurrent: () => isActive() && catalogueRequests[kind] === controller && !controller.signal.aborted,
      finish: () => {
        if (catalogueRequests[kind] === controller) catalogueRequests[kind] = null;
      },
    };
  }

  function retireCatalogueRequest(kind) {
    if (kind === "search") searchComposition = null;
    const controller = catalogueRequests[kind];
    catalogueRequests[kind] = null;
    controller?.abort();
    if (state[kind].status === "loading") {
      state[kind] = { ...state[kind], status: "idle", error: null,
        ...(kind === "home" ? {} : { requestId: state[kind].requestId + 1 }) };
    }
  }

  /**
   * Run a live request, and fall back to the sample catalogue when the network
   * itself is the problem — not when the API says 404. A missing album is a fact
   * about the catalogue; an unreachable API is a fact about the network, and
   * those deserve different screens.
   */
  async function withFallback(run, fallback, isCurrent = () => true) {
    try {
      const value = await run();
      if (!isCurrent()) return;
      state.source.mode = "live";
      state.source.error = null;
      state.source.attempts = [];
      return value;
    } catch (error) {
      if (!isCurrent()) throw error;
      if (error instanceof SourceError && NETWORK_KINDS.has(error.kind)) {
        noteFailure(error);
        return fallback(error);
      }
      throw error;
    }
  }

  async function loadHome() {
    if (!isActive()) return;
    const request = beginCatalogueRequest("home");
    try {
      state.home = { status: "loading", rails: [], error: null };
      if (state.route.name === "home") renderView();
      const settled = await Promise.allSettled(
        HOME_RAILS.map(async (rail) => ({
          ...rail,
          items: await source.search(rail.kind, rail.query, 0, { signal: request.signal }),
        })),
      );
      if (!request.isCurrent()) return;
      const rails = settled.filter((result) => result.status === "fulfilled" && result.value.items.length > 0).map((result) => result.value);
      const failures = settled.filter((result) => result.status === "rejected").map((result) => result.reason);
      if (rails.length) {
        state.source.mode = "live";
        state.source.error = null;
        state.home = { status: "ready", rails, error: null };
      } else {
        const error = failures[0] ?? new SourceError(FAILURE_KIND.shape, "every shelf came back empty", { url: API_ORIGIN });
        // Every row below is fictional, including after HTTP/shape failures.
        // Always label that fallback; this does not turn an HTTP detail/search
        // error into a successful response (withFallback keeps that distinction).
        noteFailure(error);
        state.home = { status: "sample", rails: demoHome(), error };
      }
      if (state.route.name === "home") renderView();
    } finally {
      request.finish();
    }
  }

  async function runSearch(query, filter = state.search.filter) {
    if (!isActive()) return;
    const request = beginCatalogueRequest("search");
    const trimmed = String(query ?? "").trim();
    state.search = { ...state.search, query: trimmed, filter, requestId: state.search.requestId + 1 };
    const requestId = state.search.requestId;
    if (!trimmed) {
      state.search = { ...state.search, status: "idle", results: [], error: null };
      request.finish();
      renderView();
      return;
    }
    state.search = { ...state.search, status: "loading", results: [], error: null };
    renderView();
    try {
      const results = await withFallback(
        () => source.search(filter, trimmed, 0, { signal: request.signal }),
        () => searchDemo(filter, trimmed),
        request.isCurrent,
      );
      if (requestId !== state.search.requestId) return;
      if (!request.isCurrent()) return;
      state.search = { ...state.search, status: "ready", results, error: state.source.mode === "sample" ? state.source.error : null };
    } catch (error) {
      if (requestId !== state.search.requestId) return;
      if (!request.isCurrent()) return;
      state.search = { ...state.search, status: "error", results: [], error: describeFailure(error) };
    } finally {
      request.finish();
    }
    renderView();
  }

  async function loadEntity() {
    if (!isActive()) return;
    const { name, id } = state.route;
    if (!["album", "artist", "playlist"].includes(name) || !id) retireCatalogueRequest("entity");
    if (!DETAIL_ROUTES.includes(name) || name === "settings" || name === "about") return;
    if (name === "collection") {
      state.entity = { status: "ready", kind: "collection", id, data: null, error: null, requestId: state.entity.requestId + 1 };
      renderView();
      return;
    }
    if (!id) {
      state.entity = {
        status: "error",
        kind: name,
        id,
        data: null,
        error: { title: "That link is incomplete", body: "The address has no id in it, so there is nothing to load.", technical: win.location.hash },
        requestId: state.entity.requestId + 1,
      };
      renderView();
      return;
    }
    const request = beginCatalogueRequest("entity");
    const requestId = state.entity.requestId + 1;
    state.entity = { status: "loading", kind: name, id, data: null, error: null, requestId };
    renderView();
    try {
      const data = await withFallback(
        () =>
          name === "album"
            ? source.album(id, { signal: request.signal }).then((album) => ({ album }))
            : name === "playlist"
              ? source.playlist(id, { signal: request.signal }).then((playlist) => ({ playlist }))
              : source.artist(id, { signal: request.signal }),
        () => {
          const demo = demoEntity(name, id);
          if (!demo) throw new SourceError(FAILURE_KIND.http, "not in the sample catalogue", { status: 404, url: id });
          return name === "album" ? { album: demo } : name === "playlist" ? { playlist: demo } : demo;
        },
        request.isCurrent,
      );
      if (requestId !== state.entity.requestId) return;
      if (!request.isCurrent()) return;
      state.entity = { status: "ready", kind: name, id, data, error: null, requestId };
    } catch (error) {
      if (requestId !== state.entity.requestId) return;
      if (!request.isCurrent()) return;
      state.entity = { status: "error", kind: name, id, data: null, error: describeFailure(error), requestId };
    } finally {
      request.finish();
    }
    renderView();
    // The heading a lost visitor lands on is also the announcement of the screen.
    if (state.pendingHeadingFocus) focusScreenHeading();
  }

  /* --------------------------------------------------------------- title -- */

  function entityTitle() {
    const data = state.entity.data;
    if (!data) return "";
    return data.album?.title ?? data.playlist?.title ?? data.artist?.title ?? "";
  }

  function updateDocumentTitle() {
    const name = titleFor(state.route, entityTitle());
    doc.title = name ? `${name} — DHUN` : "DHUN — the web build";
  }

  /* --------------------------------------------------------------- render -- */

  function setRegion(id, markup) {
    const region = doc.getElementById(id);
    if (region) region.innerHTML = markup;
  }

  function renderChrome() {
    if (!isActive()) return;
    const route = state.route;
    const back = DETAIL_ROUTES.includes(route.name);
    const title =
      route.name === "home"
        ? "DHUN"
        : route.name === "search"
          ? "Search"
          : route.name === "library"
            ? "Your library"
            : route.name === "settings"
              ? "Settings"
              : route.name === "about"
                ? "About & legal"
                : entityTitle() || "DHUN";
    setRegion(
      "dhun-topbar",
      appBar({
        title,
        overline: route.name === "home" ? "the web build" : "",
        back,
        actions:
          route.name === "settings"
            ? html`<button type="button" class="dhun-icon-button" data-action="open-settings" aria-label="Settings">${icon("Settings", { size: 22 })}</button>`
            : html`<button type="button" class="dhun-icon-button" data-action="open-settings" aria-label="Settings">${icon("Settings", { size: 22 })}</button>`,
      }),
    );
    // The highlighted destination is a property of the screen you are on, not of
    // the last tab you pressed: Settings and About belong to no destination, and
    // leaving Library lit under them claimed otherwise.
    const activeTab = tabFor(route) ?? "";
    setRegion("dhun-rail-region", navRail({ active: activeTab }));
    setRegion("dhun-nav-region", bottomNav({ active: activeTab }));
    // The dock carries the failure as well as the track. A playback error that
    // is only visible after opening the full player is a failure most visitors
    // never see: they pressed play, nothing happened, and the row went quiet.
    const track = currentTrack(player.state);
    // `#/?player=open` is what a bookmark of the expanded player produces, and
    // what the project site's "open the player" link points at. With nothing
    // loaded the overlay has nothing to draw, so without this the request
    // vanished: the visitor landed on Home with no player and no explanation.
    const emptyPlayerRequest = state.route.player === "open" && !track;
    setRegion(
      "dhun-dock",
      html`${emptyPlayerRequest
        ? notice({
            tone: "info",
            title: "Nothing is playing yet",
            body: "The player opens on a track. Start one from Home, Search or your library and this dock becomes its controls.",
          })
        : html``}${miniPlayer({
        track,
        status: player.state.status,
        percent: percent(player.state.position, player.state.duration),
      })}${player.state.error && state.route.player !== "open"
        ? miniError({ title: player.state.error.title, technical: player.state.error.technical })
        : html``}`,
    );
    root.dataset.player = state.route.player === "open" ? "open" : "closed";
    root.dataset.hasTrack = currentTrack(player.state) ? "true" : "false";
  }

  // A focused draft belongs to the currently rendered Search intent, not a new submit.
  let renderedSearchRequestId = null;

  function renderView() {
    if (!isActive()) return;
    const route = state.route;
    const likes = state.library.likes;
    const current = currentTrack(player.state);
    const currentId = current?.id ?? "";
    const playing = player.state.status === STATUS.playing;
    const capturedCaret = captureCaret();
    let markup = html``;

    switch (route.name) {
      case "search": {
        const vm = {
          query: state.search.query,
          filter: state.search.filter,
          status: state.search.status,
          results: state.search.results,
          error: state.search.error,
          recent: state.library.recentSearches,
          likes,
          currentId,
          playing,
          hrefFor,
        };
        const results = renderedSearchRequestId === state.search.requestId ? doc.getElementById("search-results") : null;
        if (results) {
          // Keep the actual form/input connected: strings cannot restore IME state.
          results.innerHTML = views.searchResults(vm);
          markup = null;
        } else markup = views.searchScreen(vm);
        break;
      }
      case "library": {
        markup = views.libraryScreen({
          tab: route.libTab ?? "playlists",
          playlists: state.library.playlists,
          playlistCovers: Object.fromEntries(
            state.library.playlists.map((playlist) => [playlist.id, playlistSongs(state.library, playlist)[0]?.image ?? ""]),
          ),
          likes: likedSongs(state.library),
          likeIds: likes,
          history: historySongs(state.library),
          currentId,
          playing,
          storage: storageInfo(),
        });
        break;
      }
      case "album": {
        markup = views.albumScreen({
          status: state.entity.status,
          album: state.entity.data?.album,
          error: state.entity.error,
          currentId,
          playing,
          likes,
          saved: Boolean(state.entity.data?.album && state.library.playlists.some((entry) => entry.name === `Saved · ${state.entity.data.album.title}`)),
        });
        break;
      }
      case "artist": {
        const data = state.entity.data;
        markup = views.artistScreen({
          status: state.entity.status,
          artist: data?.artist,
          songs: data?.songs ?? [],
          albums: data?.albums ?? [],
          error: state.entity.error,
          currentId,
          playing,
          likes,
          hrefFor,
        });
        break;
      }
      case "playlist": {
        markup = views.playlistScreen({
          status: state.entity.status,
          playlist: state.entity.data?.playlist,
          error: state.entity.error,
          currentId,
          playing,
          likes,
        });
        break;
      }
      case "collection": {
        const playlist = state.library.playlists.find((entry) => entry.id === route.id) ?? null;
        markup = views.collectionScreen({
          playlist,
          tracks: playlistSongs(state.library, playlist),
          currentId,
          playing,
          likes,
        });
        break;
      }
      case "settings": {
        markup = views.settingsScreen({
          settings: settings(),
          source: {
            note: "This build reads JioSaavn's public catalogue — search, albums, artists, playlists and audio streams.",
            endpoint: `${sourceStatus().origin?.origin ?? API_ORIGIN}/api`,
            disclosure:
              "Every instance listed here is an unofficial, community-run deployment of the same open-source wrapper around JioSaavn. None has a support contract with this project, and any of them may rate-limit, change or disappear. The app tries them in turn, remembers the one that answered, and says which host said what when none does.",
            chain: sourceStatus(),
          },
          storage: storageInfo(),
          version: `DHUN web ${WEB_VERSION} · no account · no DHUN server`,
        });
        break;
      }
      case "about": {
        const active = webLegalById(route.doc ?? "privacy") ?? WEB_LEGAL_DOCUMENTS[1];
        markup = views.aboutScreen({
          documents: WEB_LEGAL_DOCUMENTS,
          activeId: active.id,
          title: active.title,
          body: legalBody(active),
        });
        break;
      }
      default: {
        markup = views.homeScreen({
          hour: new Date().getHours(),
          headline: "What do you want to hear?",
          status: state.home.status === "loading" ? "loading" : state.home.status === "sample" ? "sample" : state.home.status === "ready" ? "ready" : "loading",
          rails: state.home.rails,
          hero: state.home.rails.find((rail) => rail.kind === "songs")?.items[0] ?? state.home.rails[0]?.items[0] ?? null,
          source: sourceBadge(),
          sampleNote: sampleNote(),
          hrefFor,
        });
      }
    }
    state.pendingFieldFocus = capturedCaret;
    if (markup !== null) setRegion("dhun-view", markup);
    renderedSearchRequestId = route.name === "search" ? state.search.requestId : null;
    updateDocumentTitle();
    renderChrome();
    restoreCaret(capturedCaret);
    if (!state.route.player && state.route.player === null) {
      /* nothing: the overlay is rendered by renderOverlay */
    }
    renderOverlay();
  }

  let overlayKey = null;
  function renderOverlay() {
    if (!isActive()) return;
    const open = state.route.player === "open";
    const vm = playerVm();
    const key = `${open}:${fullPlayerRenderKey(vm)}`;
    const existing = doc.querySelector(".dhun-full");
    if (key === overlayKey && (!open || existing)) {
      if (existing) updateFullPlayerProgress(existing, vm);
      return;
    }
    overlayKey = key;
    // Structural changes still render normally. Progress ticks only patch the
    // scrubber/times: replacing the controls restarted entrance animation and
    // detached focused/clicked buttons four times a second during real media.
    const previous = doc.querySelector(".dhun-full__inner");
    const scroll = previous?.scrollTop ?? 0;
    const previousTab = root.dataset.playerTab ?? "";
    setRegion("dhun-overlay", open && vm.track ? fullPlayer(vm) : html``);
    root.dataset.player = open ? "open" : "closed";
    root.dataset.playerTab = open ? state.route.playerTab ?? "queue" : "";
    const inner = doc.querySelector(".dhun-full__inner");
    if (inner) {
      inner.scrollTop = scroll;
      // A tab press asks for its panel. On a 390×844 window the panel sits just
      // below the fold — 856px down — so without this the press looked like it
      // did nothing. The assignment clamps to the scroll port's own maximum.
      if (open && previousTab && previousTab !== state.route.playerTab) {
        const panel = doc.getElementById("player-panel");
        if (panel) {
          inner.scrollTop = panel.getBoundingClientRect().top - inner.getBoundingClientRect().top + inner.scrollTop - 8;
        }
      }
    }
  }

  function renderSheet() {
    if (!isActive()) return;
    const sheet = state.sheet;
    // `null`, not an empty fragment: `sheetLayer` decides whether a scrim is
    // drawn, and a fragment would be truthy even with nothing in it.
    let markup = null;
    if (sheet?.kind === "track") {
      const song = sheet.song;
      markup = components.sheet({
        title: song.title,
        subtitle: song.artist,
        body: html`
          <button type="button" class="dhun-menu-row" data-action="play-next" data-id="${song.id}">${icon("QueueMusic", { size: 20 })}<span>Play next</span></button>
          <button type="button" class="dhun-menu-row" data-action="queue-track" data-id="${song.id}">${icon("Add", { size: 20 })}<span>Add to queue</span></button>
          <button type="button" class="dhun-menu-row" data-action="open-add-to-playlist" data-id="${song.id}">${icon("LibraryMusic", { size: 20 })}<span>Add to playlist</span></button>
          ${state.route.name === "collection"
            ? html`<button type="button" class="dhun-menu-row dhun-menu-row--danger" data-action="remove-from-playlist" data-id="${song.id}">${icon("Close", { size: 20 })}<span>Remove from this playlist</span></button>`
            : html``}
          <button type="button" class="dhun-menu-row" data-action="toggle-like" data-id="${song.id}">${icon(state.library.likes.includes(song.id) ? "Favorite" : "FavoriteBorder", { size: 20 })}<span>${state.library.likes.includes(song.id) ? "Remove from Liked songs" : "Save to Liked songs"}</span></button>
          ${song.kind ? html`<a class="dhun-menu-row" href="${hrefFor(song)}">${icon("Album", { size: 20 })}<span>Open ${song.album?.name || "the album"}</span></a>` : html``}
        `,
      });
    } else if (sheet?.kind === "add-to-playlist") {
      markup = components.sheet({
        title: "Add to playlist",
        subtitle: sheet.song?.title ?? "",
        body: html`
          ${state.library.playlists.length
            ? html`<div class="dhun-menu">${state.library.playlists.map(
                (playlist) => html`<button type="button" class="dhun-menu-row" data-action="add-to-playlist" data-id="${playlist.id}" data-value="${sheet.song?.id ?? ""}">${icon("Add", { size: 20 })}<span>${playlist.name}</span></button>`,
              )}</div>`
            : components.emptyState({ title: "No playlists yet", body: "Make one here — it stays in this browser.", iconName: "LibraryMusic" })}
          <button type="button" class="dhun-menu-row" data-action="new-playlist" data-value="${sheet.song?.id ?? ""}">${icon("Add", { size: 20 })}<span>New playlist</span></button>
        `,
      });
    } else if (sheet?.kind === "playlist-name") {
      const renaming = sheet.mode === "rename";
      markup = components.sheet({
        title: renaming ? "Rename playlist" : "New playlist",
        subtitle: renaming ? "The new name is saved on this device." : "It is kept in this browser, and nowhere else.",
        body: html`<form class="dhun-form" data-action="${renaming ? "rename-playlist" : "create-playlist"}"${renaming ? html` data-id="${sheet.playlist.id}"` : html``}${sheet.songId ? html` data-value="${sheet.songId}"` : html``}>
          <input class="dhun-input" type="text" name="name" value="${renaming ? sheet.playlist.name : ""}" placeholder="Playlist name" aria-label="Playlist name" maxlength="120" autocomplete="off" enterkeyhint="done" />
          <button type="submit" class="dhun-button dhun-button--filled">${renaming ? "Save" : "Create"}</button>
        </form>`,
      });
    } else if (sheet?.kind === "confirm") {
      markup = components.sheet({
        title: sheet.title,
        subtitle: sheet.body,
        body: html`${sheet.error ? html`<div role="alert">${components.notice({ tone: "warn", title: "Change not saved", body: sheet.error })}</div>` : html``}<div class="dhun-menu">
          <button type="button" class="dhun-menu-row dhun-menu-row--danger" data-action="${sheet.action}"${sheet.id ? html` data-id="${sheet.id}"` : html``}>${icon("Delete", { size: 20 })}<span>${sheet.confirmLabel}</span></button>
          <button type="button" class="dhun-menu-row" data-action="close-sheet">${icon("Close", { size: 20 })}<span>Cancel</span></button>
        </div>`,
      });
    } else if (sheet?.kind === "shortcuts") {
      markup = components.sheet({
        title: "Keyboard shortcuts",
        subtitle: "The shell's shortcuts, not the browser's.",
        body: html`<dl class="dhun-shortcuts">
          ${[
            ["Space", "Play or pause"],
            ["/", "Focus search"],
            ["→ / ←", "Seek 5 seconds"],
            ["↑ / ↓", "Volume"],
            ["M", "Mute"],
            ["S", "Shuffle"],
            ["R", "Repeat"],
            ["N / P", "Next / previous"],
            ["Esc", "Close a sheet or the player"],
          ].map(([key, action]) => html`<div class="dhun-shortcuts__row"><dt><kbd>${key}</kbd></dt><dd>${action}</dd></div>`)}
        </dl>`,
      });
    } else if (sheet?.kind === "clear-library") {
      markup = components.sheet({
        title: "Erase this browser's library?",
        subtitle: "Likes, playlists, history, searches and snapshots are deleted. Settings and the offline shell cache remain. This cannot be undone.",
        body: html`${sheet.error ? html`<div role="alert">${components.notice({ tone: "warn", title: "Library not erased", body: sheet.error })}</div>` : html``}<div class="dhun-menu">
          <button type="button" class="dhun-menu-row dhun-menu-row--danger" data-action="confirm-clear-library">${icon("Delete", { size: 20 })}<span>Erase my library</span></button>
          <button type="button" class="dhun-menu-row" data-action="close-sheet">${icon("Close", { size: 20 })}<span>Cancel</span></button>
        </div>`,
      });
    }
    setRegion("dhun-sheet", components.sheetLayer(markup));
    settleDialogFocus();
  }

  function renderToast() {
    if (!isActive()) return;
    setRegion("dhun-toast", state.toast ? components.toast({ message: state.toast.message, tone: state.toast.tone }) : html``);
  }

  /* ---------------------------------------------------------------- focus -- */

  function captureCaret() {
    const active = doc.activeElement;
    if (!active || !["INPUT", "TEXTAREA"].includes(active.tagName)) return null;
    if (!active.id) return null;
    const sameSearchIntent = active.id === "search-input" && state.route.name === "search"
      && renderedSearchRequestId === state.search.requestId;
    return { id: active.id, start: active.selectionStart, end: active.selectionEnd,
      ...(sameSearchIntent ? { value: active.value } : {}) };
  }

  function restoreCaret(captured) {
    if (!captured) return;
    const field = doc.getElementById(captured.id);
    if (!field) return;
    // A composing field stays connected and under the IME's control.
    if (isSearchComposing() && searchComposition.target === field) return;
    // Assign a value, never HTML; unsubmitted text must not be sent or persisted.
    if (typeof captured.value === "string" && field.value !== captured.value) field.value = captured.value;
    if (field === doc.activeElement) {
      if (field === doc.activeElement && captured.start !== null) setCaret(field, captured);
      return;
    }
    focusWithoutScroll(field);
    setCaret(field, captured);
  }

  function setCaret(field, captured) {
    if (field.selectionStart === captured.start && field.selectionEnd === (captured.end ?? captured.start)) return;
    try {
      field.setSelectionRange(captured.start, captured.end ?? captured.start);
    } catch {
      /* a field type with no selection */
    }
  }

  let dialogKey = null;
  let dialogReturn = null;

  function dialogRoot() {
    return doc.querySelector(".dhun-sheet") ?? doc.querySelector('#dhun-overlay [role="dialog"]');
  }

  function focusablesIn(container) {
    return [...container.querySelectorAll(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    )].filter((element) => element.getClientRects().length > 0 && element.getAttribute("aria-hidden") !== "true");
  }

  /**
   * Dialog focus, kept honest.
   *
   * A sheet and the full player both claim `aria-modal="true"`; that claim is
   * only true if the keyboard cannot leave them, so focus moves in when one
   * opens, wraps inside while it is open, and returns to the control that opened
   * it when it closes. The return target is remembered as a selector too,
   * because the press that opened the sheet re-rendered its trigger away.
   */
  function settleDialogFocus() {
    const dialog = dialogRoot();
    const key = dialog ? (dialog.classList.contains("dhun-sheet") ? "sheet" : "player") : null;
    if (key && !dialogKey) {
      const active = doc.activeElement;
      dialogReturn = active && active !== doc.body && active.getAttribute
        ? { node: active, action: active.dataset?.action ?? null, id: active.dataset?.id ?? null, value: active.dataset?.value ?? null }
        : null;
      dialogKey = key;
      if (!dialog.contains(doc.activeElement)) focusWithoutScroll(key === "player" ? dialog : focusablesIn(dialog)[0] ?? dialog);
      return;
    }
    if (key) {
      dialogKey = key;
      return;
    }
    if (dialogKey) {
      dialogKey = null;
      const wanted = dialogReturn;
      dialogReturn = null;
      if (!wanted) return;
      let target = wanted.node && doc.contains(wanted.node) ? wanted.node : null;
      if (!target && wanted.action) {
        target = doc.querySelector(`[data-action="${wanted.action}"]${wanted.id ? `[data-id="${wanted.id}"]` : ""}${wanted.value ? `[data-value="${wanted.value}"]` : ""}`);
      }
      if (target?.focus) focusWithoutScroll(target);
    }
  }

  function trapDialogTab(event) {
    const dialog = dialogRoot();
    if (!dialog) return false;
    const items = focusablesIn(dialog);
    if (!items.length) return false;
    const first = items[0];
    const last = items[items.length - 1];
    const active = doc.activeElement;
    if (!dialog.contains(active)) {
      event.preventDefault();
      focusWithoutScroll(event.shiftKey ? last : first);
      return true;
    }
    if (event.shiftKey && (active === first || active === dialog)) {
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

  function focusScreenHeading() {
    const heading = doc.querySelector("#dhun-view h1") ?? doc.querySelector("#dhun-topbar h1");
    if (!heading) return;
    state.pendingHeadingFocus = null;
    focusWithoutScroll(heading);
  }

  /* --------------------------------------------------------------- sheets -- */

  function openSheet(sheet) {
    state.sheet = sheet;
    renderSheet();
    const field = doc.querySelector(".dhun-sheet input");
    if (field) {
      focusWithoutScroll(field);
      try {
        field.setSelectionRange(field.value.length, field.value.length);
      } catch {
        /* no selection on this input */
      }
    }
  }

  function closeSheet() {
    if (!state.sheet) return;
    state.sheet = null;
    renderSheet();
  }

  /* --------------------------------------------------------------- routes -- */

  function onRoute() {
    if (!isActive()) return;
    const previous = state.route;
    const route = parseRoute(win.location.hash);
    state.route = route;
    state.tab = tabFor(route, state.tab) ?? state.tab;
    if (route.player !== "open") closeOverlayOnly();

    if (route.name !== "home") retireCatalogueRequest("home");
    if (route.name !== "search") retireCatalogueRequest("search");
    if (!["album", "artist", "playlist"].includes(route.name) || !route.id) retireCatalogueRequest("entity");

    if (route.name === "home" && state.home.status === "idle") loadHome();
    if (route.name === "search") {
      // Compare before assigning so hash-driven query changes actually load.
      if (route.query !== state.search.query || (route.query && state.search.status === "idle")) runSearch(route.query);
    }
    if (DETAIL_ROUTES.includes(route.name)) {
      const sameEntity = previous.name === route.name && previous.id === route.id;
      // Player/tab-only changes retain current work/data; Retry stays explicit.
      if (!sameEntity || state.entity.status === "idle") loadEntity();
    }

    const changed = ["name", "id", "libTab", "doc"].some((key) => (previous[key] ?? null) !== (route[key] ?? null));
    if (changed && route.name !== "search") {
      state.pendingHeadingFocus = true;
    }
    renderView();
    renderSheet();
    renderChrome();
    if (state.pendingHeadingFocus && route.name !== "search") {
      // After the render, and only when focus was actually lost.
      if (!doc.activeElement || doc.activeElement === doc.body) focusScreenHeading();
      else state.pendingHeadingFocus = null;
    }
  }

  function closeOverlayOnly() {
    if (doc.querySelector("#dhun-overlay").firstElementChild && state.route.player !== "open") {
      setRegion("dhun-overlay", html``);
    }
  }

  /* -------------------------------------------------------------- actions -- */

  function pressedSong(target) {
    const id = target.dataset.id;
    const contexts = [state.search.results, state.home.rails.flatMap((rail) => rail.items), currentTrack(player.state) ? [currentTrack(player.state)] : [], ...Object.values(state.library.snapshots ?? {})];
    for (const list of contexts) {
      const found = (Array.isArray(list) ? list : []).find((song) => song?.id === id);
      if (found) return found;
    }
    return state.library.snapshots[id] ?? null;
  }

  function startPlayback(songs, index, context) {
    if (!songs.length) return;
    // A sample row still reaches the player, because the player is where a
    // refusal is explained in full (`missingStreamError`). It is not announced
    // twice: the dock and the player already carry the reason.
    player.play(songs, Math.max(0, Math.min(index, songs.length - 1)), context);
  }

  const actions = {
    /* shell */
    nav: (target) => {
      const tab = target.dataset.tab;
      state.tab = tab;
      const keep = { player: state.route.player, playerTab: state.route.playerTab };
      if (tab === "HOME") go({ ...routeFor("home"), ...keep });
      if (tab === "SEARCH") go({ ...routeFor("search"), ...keep });
      if (tab === "LIBRARY") go({ ...routeFor("library", { libTab: "playlists" }), ...keep });
    },
    back: () => {
      if (state.route.player === "open") {
        go({ ...state.route, player: null });
        return;
      }
      if (DETAIL_ROUTES.includes(state.route.name)) {
        const map = { album: "home", artist: "home", playlist: "search", collection: "library", settings: "home", about: "settings" };
        const name = map[state.route.name] ?? "home";
        go(name === "library" ? routeFor("library", { libTab: "playlists" }) : routeFor(name));
        return;
      }
      go(routeFor("home"));
    },
    "open-settings": () => go({ ...state.route, name: "settings", id: null, doc: null }),
    "go-home": () => go(routeFor("home")),
    "go-library": () => go(routeFor("library", { libTab: "playlists" })),

    /* search */
    "search-submit": (target, event) => {
      event.preventDefault();
      if (isSearchComposing()) return;
      const input = target.querySelector("input[name='q']") ?? doc.querySelector("#search-input");
      const query = (input?.value ?? "").trim();
      if (!query) return;
      state.library = rememberSearch(state.library, query);
      save();
      replaceSearchRoute(query);
      runSearch(query);
    },
    "clear-search": () => {
      replaceSearchRoute("");
      runSearch("");
      focusWithoutScroll(doc.querySelector("#search-input"));
    },
    "set-search-filter": (target) => {
      state.search = { ...state.search, filter: target.dataset.value };
      if (state.search.query) runSearch(state.search.query, target.dataset.value);
      else renderView();
    },
    "use-recent": (target) => {
      const query = target.dataset.value;
      replaceSearchRoute(query);
      runSearch(query);
    },
    "clear-recent": () => {
      if (!commitLibrary({ ...state.library, recentSearches: [] })) return;
      renderView();
    },

    /* playback */
    "play-row": (target) => {
      const index = Number(target.dataset.index ?? 0);
      const context = target.dataset.context ?? "list";
      const songs = listContext(context, state.search.results.length ? state.search.results : state.home.rails.flatMap((rail) => rail.items));
      const track = songs[index] ?? pressedSong(target);
      const realIndex = songs.findIndex((song) => song?.id === track?.id);
      const current = currentTrack(player.state);
      if (current && track && current.id === track.id && !track.demo) {
        player.toggle();
        return;
      }
      startPlayback(songs.length ? songs : [track].filter(Boolean), realIndex >= 0 ? realIndex : index, context === "list" ? "playing" : context);
    },
    "play-hero": () => {
      const hero = state.home.rails.find((rail) => rail.kind === "songs")?.items[0] ?? state.home.rails[0]?.items[0];
      if (!hero) return;
      if (hero.kind === "song") {
        startPlayback(listContext("home", state.home.rails.flatMap((rail) => (rail.kind === "songs" ? rail.items : []))), 0, "Trending now");
      } else go(routeFor(hero.kind === "album" ? "album" : hero.kind === "playlist" ? "playlist" : "artist", { id: hero.id }));
    },
    "play-hero-shuffled": () => {
      const songs = state.home.rails.flatMap((rail) => (rail.kind === "songs" ? rail.items : []));
      if (!songs.length) return;
      player.state.shuffle = true;
      startPlayback(songs, Math.floor(Math.random() * songs.length), "Trending now");
    },
    "play-collection": () => {
      const songs = collectionSongs();
      if (!songs.length) return;
      if (state.entity.status !== "ready" && state.route.name !== "collection") return;
      startPlayback(songs, 0, contextLabel());
    },
    "shuffle-collection": () => {
      const songs = collectionSongs();
      if (!songs.length) return;
      player.state.shuffle = true;
      startPlayback(songs, Math.floor(Math.random() * songs.length), contextLabel());
    },
    "toggle-play": () => player.toggle(),
    "next-track": () => player.next(),
    "previous-track": () => player.previous(),
    "open-player": () => go({ ...state.route, player: "open" }),
    "close-player": () => go({ ...state.route, player: null }),
    "player-tab": (target) => go({ ...state.route, player: "open", playerTab: target.dataset.value }),
    seek: (target) => player.seek(Number(target.value)),
    "set-volume": (target) => {
      player.setVolume(Number(target.value));
      state.library = { ...state.library, settings: { ...settings(), volume: Number(target.value) } };
      save();
    },
    "toggle-shuffle": () => player.toggleShuffle(),
    "cycle-repeat": () => player.cycleRepeat(),
    "set-repeat": (target) => {
      const repeat = target.dataset.value;
      player.state.repeat = repeat;
      state.library = { ...state.library, settings: { ...settings(), repeat } };
      save();
      renderView();
    },
    "play-queue-row": (target) => {
      const index = Number(target.dataset.index);
      player.play(player.state.queue, index, player.state.context);
    },
    "remove-queue-row": (target) => player.removeAt(Number(target.dataset.index)),

    /* library */
    "toggle-like": (target) => {
      const song = pressedSong(target);
      if (!song) return;
      const result = toggleLike(state.library, song);
      state.library = result.library;
      const saved = save();
      showSaveToast(result.liked ? "Saved to Liked songs." : "Removed from Liked songs.", saved);
      renderView();
      renderSheet();
      return result;
    },
    "row-menu": (target) => {
      const song = pressedSong(target);
      if (!song) return;
      openSheet({ kind: "track", song });
    },
    "queue-track": (target) => {
      const song = pressedSong(target);
      if (!song) return;
      showToast(player.enqueue(song) ? "Added to the queue." : "Already in the queue.");
    },
    "play-next": (target) => {
      const song = pressedSong(target);
      if (!song) return;
      showToast(player.playNext(song) ? "Playing next." : "Already in the queue.");
    },
    "open-add-to-playlist": (target) => {
      const song = pressedSong(target);
      if (!song) return;
      openSheet({ kind: "add-to-playlist", song });
    },
    "new-playlist": (target) => openSheet({ kind: "playlist-name", mode: "create", songId: target.dataset.value ?? "" }),
    "create-playlist": (target, event) => {
      event.preventDefault();
      const name = target.querySelector("input[name='name']")?.value ?? "";
      const result = createPlaylist(state.library, name);
      if (!result.playlist) {
        showToast("A playlist needs a name.", "warn");
        return;
      }
      state.library = result.library;
      if (target.dataset.value) {
        const song = pressedSong({ dataset: { id: target.dataset.value } });
        if (song) state.library = addToPlaylist(state.library, result.playlist.id, song).library;
      }
      const saved = save();
      showSaveToast(`Created “${result.playlist.name}”.`, saved);
      closeSheet();
      renderView();
    },
    "rename-playlist": (target) => {
      const id = target.dataset.id ?? state.route.id;
      const playlist = state.library.playlists.find((entry) => entry.id === id);
      if (!playlist) return;
      openSheet({ kind: "playlist-name", mode: "rename", playlist });
    },
    "delete-playlist": () => {
      const playlist = state.library.playlists.find((entry) => entry.id === state.route.id);
      if (!playlist) return;
      openSheet({ kind: "confirm", title: `Delete “${playlist.name}”?`, body: "The playlist is removed from this browser. The songs themselves are not affected.", confirmLabel: "Delete playlist", action: "confirm-delete-playlist", id: playlist.id });
    },
    "confirm-delete-playlist": (target) => {
      if (!commitLibrary(deletePlaylist(state.library, target.dataset.id))) return;
      closeSheet();
      showToast("Playlist deleted.");
      go(routeFor("library", { libTab: "playlists" }));
    },
    "add-to-playlist": (target) => {
      const playlistId = target.dataset.id;
      const song = pressedSong({ dataset: { id: target.dataset.value } });
      if (!song) return;
      const result = addToPlaylist(state.library, playlistId, song);
      state.library = result.library;
      const saved = save();
      showSaveToast(result.added ? "Added to the playlist." : "Already in that playlist.", saved);
      closeSheet();
      renderView();
    },
    "remove-from-playlist": (target) => {
      if (!commitLibrary(removeFromPlaylist(state.library, state.route.id, target.dataset.id))) return;
      renderView();
    },
    "set-library-tab": (target) => go(routeFor("library", { libTab: target.dataset.value })),

    /* appearance */
    "set-theme": (target) => {
      state.library = { ...state.library, settings: { ...settings(), theme: target.dataset.value } };
      save();
      applyAppearance();
      renderView();
    },
    "set-accent": (target) => {
      state.library = { ...state.library, settings: { ...settings(), accent: target.dataset.value } };
      save();
      applyAppearance();
      renderView();
    },

    /* sheets */
    "close-sheet": () => closeSheet(),
    "show-shortcuts": () => openSheet({ kind: "shortcuts" }),
    "scrim-close": (target, event) => {
      if (event.target === target) closeSheet();
    },

    /* data */
    retry: () => {
      if (state.route.name === "home") loadHome();
      else if (state.route.name === "search") runSearch(state.search.query);
      else loadEntity();
    },
    refresh: () => {
      state.source = { mode: "unknown", error: null, attempts: [] };
      if (state.route.name === "home") loadHome();
      else if (state.route.name === "search") runSearch(state.search.query);
      else loadEntity();
    },

    /* storage */
    "export-library": () => {
      const blob = new win.Blob([storage.exportAll(state.library)], { type: "application/json" });
      const url = win.URL.createObjectURL(blob);
      const link = doc.createElement("a");
      link.href = url;
      link.download = "dhun-library.json";
      link.click();
      win.setTimeout(() => win.URL.revokeObjectURL(url), 1000);
      showToast("Library exported.");
    },
    "import-library": async (target) => {
      const file = target.files?.[0];
      if (!file) return;
      try {
        const imported = await handleFileImport(file, storage);
        if (disposed) return;
        state.library = imported;
        applyAppearance();
        renderView();
        showToast("Library imported.");
      } catch (error) {
        if (disposed) return;
        renderView();
        showToast(`Import failed: ${error?.message ?? "the file could not be read"}`, "warn");
      } finally {
        if (!disposed) target.value = ""; // selecting the same file again must allow a retry
      }
    },
    "clear-library": () => openSheet({ kind: "clear-library" }),
    "confirm-clear-library": () => {
      const next = { ...state.library, likes: [], playlists: [], history: [], snapshots: {}, recentSearches: [] };
      if (!commitLibrary(next)) return;
      closeSheet();
      renderView();
      showToast("Library erased.");
    },
  };

  /* ------------------------------------------------------------- dispatch -- */

  own(delegate(root, "click", "[data-action]", whileActive((event, target) => {
    // A `<form data-action>` names what its *submit* does. The click that a
    // submit button dispatches (and the one Enter dispatches before it) also
    // bubbles to this handler, and `closest` finds the enclosing form — so
    // without this guard the form's action ran as a click, re-rendered the
    // sheet and disconnected the form before the browser could submit it.
    // Rename then saved nothing at all, and create ran twice.
    if (target.tagName === "FORM") return;
    const action = target.dataset.action;
    const handler = actions[action];
    if (!handler) return;
    if (target.tagName === "A" && target.getAttribute("href")?.startsWith("#")) {
      // Hash links are the router's; let the default happen.
    }
    handler(target, event);
  })));

  own(listen(root, "compositionstart", whileActive((event) => {
    if (state.route.name === "search" && event.target === doc.getElementById("search-input")) {
      searchComposition = { target: event.target, requestId: state.search.requestId };
    }
  })));
  own(listen(root, "compositionend", whileActive((event) => {
    if (searchComposition?.target === event.target) searchComposition = null;
  })));

  // The file input is change, not click.
  own(listen(root, "change", whileActive((event) => {
    const input = event.target.closest?.("[data-action='import-library']");
    if (input) actions["import-library"](input);
  })));

  own(delegate(root, "input", "[data-action='seek']", whileActive((event, target) => actions.seek(target))));
  own(delegate(root, "input", "[data-action='set-volume']", whileActive((event, target) => actions["set-volume"](target))));

  own(delegate(root, "submit", "form[data-action]", whileActive((event, target) => {
    const action = target.dataset.action;
    if (action === "search-submit") actions["search-submit"](target, event);
    else if (action === "create-playlist" || action === "rename-playlist") {
      event.preventDefault();
      if (action === "create-playlist") actions["create-playlist"](target, event);
      else {
        const name = target.querySelector("input[name='name']")?.value ?? "";
        const id = target.dataset.id;
        state.library = renamePlaylist(state.library, id, name);
        const saved = save();
        closeSheet();
        showSaveToast("Playlist renamed.", saved);
        renderView();
      }
    }
  })));

  own(listen(doc, "keydown", whileActive((event) => {
    if (event.isComposing || event.keyCode === 229 || (isSearchComposing() && doc.activeElement === searchComposition.target)) return;
    if (event.key === "Tab" && trapDialogTab(event)) return;
    const match = matchShortcut(event, { inDialog: Boolean(dialogRoot()) });
    if (!match) return;
    const { action, preventDefault } = match;
    if (preventDefault) event.preventDefault();
    switch (action) {
      case "play-pause":
        player.toggle();
        break;
      case "next":
        player.next();
        break;
      case "previous":
        player.previous();
        break;
      case "seek-forward":
        player.seekBy(5);
        break;
      case "seek-back":
        player.seekBy(-5);
        break;
      case "volume-up":
        player.setVolume(Math.min(1, player.state.volume + 0.05));
        break;
      case "volume-down":
        player.setVolume(Math.max(0, player.state.volume - 0.05));
        break;
      case "mute":
        player.toggleMute();
        break;
      case "shuffle":
        player.toggleShuffle();
        break;
      case "repeat":
        player.cycleRepeat();
        break;
      case "search":
        if (state.route.name !== "search") go(routeFor("search"));
        focusWithoutScroll(doc.querySelector("#search-input"));
        break;
      case "escape":
        if (state.sheet) closeSheet();
        else if (state.route.player === "open") go({ ...state.route, player: null });
        break;
      case "help":
        openSheet({ kind: "shortcuts" });
        break;
      default:
        break;
    }
  })));

  /* ------------------------------------------------------------- player -- */

  let recordedPlayback = 0;
  own(player.subscribe((playerState) => {
    if (disposed) return;
    const track = currentTrack(playerState);
    if (track && playerState.status === STATUS.playing && recordedPlayback !== player.playbackId) {
      recordedPlayback = player.playbackId;
      state.library = rememberPlay(state.library, track);
      save();
    }
    renderChrome();
    if (state.route.player === "open") renderOverlay();
    if (track) {
      attachMediaSession({
        track,
        status: playerState.status,
        position: playerState.position,
        duration: playerState.duration,
        handlers: {
          play: () => { if (!player.requested) player.toggle(); },
          pause: () => { if (player.requested) player.toggle(); },
          previous: () => player.previous(),
          next: () => player.next(),
          seekBackward: () => player.seekBy(-10),
          seekForward: () => player.seekBy(10),
          seekTo: (details) => player.seek(details.seekTime),
        },
      });
    } else {
      attachMediaSession({ track: null, status: STATUS.idle });
    }
  }));

  /* ----------------------------------------------------------- appearance -- */

  function resolvedTheme() {
    const theme = settings().theme;
    if (theme !== "system") return theme;
    return win.matchMedia?.("(prefers-color-scheme: light)")?.matches ? "light" : "dark";
  }

  function applyAppearance() {
    if (!isActive()) return;
    doc.documentElement.dataset.dhunTheme = resolvedTheme();
    doc.documentElement.dataset.dhunAccent = settings().accent;
  }

  function storageInfo() {
    return {
      persistent: storage.persistent,
      note: storage.persistent
        ? "Likes, playlists and history are stored in this browser's localStorage. Exporting writes a JSON file you keep; nothing is uploaded."
        : "Storage could not confirm saving. Changes may last only for this visit; an older saved library may still exist. Export a backup, allow storage or free space, then retry.",
    };
  }

  function collectionSongs() {
    if (state.route.name === "collection") {
      const playlist = state.library.playlists.find((entry) => entry.id === state.route.id);
      return playlistSongs(state.library, playlist);
    }
    const data = state.entity.data;
    return data?.album?.songs ?? data?.playlist?.songs ?? data?.songs ?? [];
  }

  /**
   * What the full player calls what it is playing.
   *
   * The player's queue is started with a context string (`album:demo-al-1`,
   * `collection:pl-…`) because that is what `listContext()` needs to find the
   * rows again. It is not something to show a person: the bar used to print the
   * raw value, so the header read "ALBUM:DEMO-AL-1" over a record whose name it
   * knew all along. A known collection gets its name; everything else gets the
   * kind in words.
   */
  const CONTEXT_WORDS = Object.freeze({
    album: "Album",
    playlist: "Playlist",
    artist: "Artist",
    collection: "Your playlist",
    search: "Search results",
    liked: "Liked songs",
    history: "Recently played",
    home: "Home",
    hero: "Trending now",
    list: "Playing",
  });

  function contextForDisplay(context) {
    if (!context) return "Playing";
    // A context with no colon was written as words when playback started
    // ("Trending now", "Liked songs") and needs no translation.
    if (!String(context).includes(":")) return String(context);
    const [kind, id] = String(context).split(":");
    if ((kind === "album" || kind === "playlist" || kind === "artist") && state.entity.data) {
      const data = state.entity.data[kind];
      if (data && (!id || data.id === id)) return data.title;
    }
    if (kind === "collection") {
      const playlist = state.library.playlists.find((entry) => entry.id === id);
      if (playlist) return playlist.name;
      return CONTEXT_WORDS.collection;
    }
    return CONTEXT_WORDS[kind] ?? "Playing";
  }

  function contextLabel() {
    const data = state.entity.data;
    return data?.album?.title ?? data?.playlist?.title ?? data?.artist?.title ?? state.library.playlists.find((entry) => entry.id === state.route.id)?.name ?? "Playing";
  }

  /* ----------------------------------------------------------------- boot -- */

  applyAppearance();
  state.tab = tabFor(state.route, "HOME") ?? "HOME";

  own(listen(win, "hashchange", whileActive(onRoute)));
  own(listen(win.matchMedia?.("(prefers-color-scheme: light)"), "change", whileActive(() => {
    if (settings().theme === "system") applyAppearance();
  })));
  own(listen(win, "pagehide", (event) => {
    if (event.persisted) suspend(); else destroy();
  }));
  own(listen(win, "pageshow", () => {
    if (disposed || !suspended) return;
    suspended = false;
    applyAppearance();
    onRoute();
    renderToast();
  }));

  // The service worker is what makes the shell open offline. `createPwa`
  // exposes `start()` — it wires the install prompt and connectivity, then
  // registers the worker. An earlier build called `register?.()`, a method that
  // does not exist, so the worker was never registered and the app's offline
  // promise was a file in the build with nothing pointing at it.
  let announcedUpdate = false;
  pwa = createPwa({
    win,
    doc,
    onState: (pwaState) => {
      if (!isActive()) return;
      if (pwaState.updateReady && !announcedUpdate) {
        announcedUpdate = true;
        showToast("A new version is ready. Close this tab and open DHUN again to use it.", "info");
      }
    },
  });
  pwa.start();
  // Initialization has one catalogue owner, just like subsequent navigation.
  onRoute();
  renderChrome();
  renderToast();

  function suspend() {
    if (!isActive()) return;
    suspended = true;
    for (const kind of Object.keys(catalogueRequests)) retireCatalogueRequest(kind);
    if (state.toastTimer) win.clearTimeout(state.toastTimer);
    state.toastTimer = null;
    state.toast = null;
  }

  function destroy() {
    if (disposed) return;
    disposed = true;
    suspended = false;
    for (const kind of Object.keys(catalogueRequests)) retireCatalogueRequest(kind);
    componentLifetime.abort();
    if (state.toastTimer) win.clearTimeout(state.toastTimer);
    state.toastTimer = null;
    for (const stop of cleanups) stop();
    cleanups.clear();
    pwa?.destroy();
    player.destroy();
    attachMediaSession({ track: null, status: STATUS.idle });
  }

  return { state, player, go, destroy };
}

/* Browser startup stays automatic; Node controller tests call boot with
 * document/window doubles and dispose the returned component explicitly. */
if (globalThis.document?.querySelector?.("#app")) {
  if (globalThis.document.readyState === "loading") {
    globalThis.document.addEventListener("DOMContentLoaded", () => boot(), { once: true });
  } else {
    boot();
  }
}
