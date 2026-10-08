/**
 * Application wiring.
 *
 * The only file that touches the document: it owns state, renders the shell,
 * and translates DOM events into actions. Everything else in src/js is pure.
 *
 * Order of business on boot:
 *   1. restore persisted appearance/settings, apply them to <html>
 *   2. build the nav, catalog and player
 *   3. render, then load the home feed and probe the live source in the
 *      background (never blocking the first paint on the network)
 */

import { html, raw, must, delegate, focusWithoutScroll } from "./dom.js";
import { createStore, readPersisted, writePersisted, persistedSlice } from "./store.js";
import {
  createNavState,
  AppTab,
  USER_TABS,
  backAction,
  ShellBackAction,
  layoutOf,
} from "./nav.js";
import { createCatalog, SOURCE } from "./catalog.js";
import { createPlayer, BACKEND } from "./player.js";
import {
  createEqualizerState,
  withBandGain,
  withPreamp,
  withPreset,
  createWebAudioEqualizer,
} from "./equalizer.js";
import { parseLrc, activeLineIndex } from "./lyrics.js";
import SAMPLE_LYRICS from "./data/sample-lyrics.js";
import { icon } from "./views.js";
import * as views from "./views.js";
import { miniPlayer, fullPlayer } from "./player-ui.js";
import { escapeHtml } from "./format.js";

const TAB_TITLE = {
  [AppTab.HOME]: "Home",
  [AppTab.SEARCH]: "Search",
  [AppTab.LIBRARY]: "Library",
};

const TAB_ICON = {
  [AppTab.HOME]: "Home",
  [AppTab.SEARCH]: "Search",
  [AppTab.LIBRARY]: "LibraryMusic",
};

export function boot({ root, audio = null, AudioContextCtor = null, storage = true } = {}) {
  const mount = root ?? must("#app");
  const catalog = createCatalog();
  const saved = storage ? readPersisted() : null;

  const store = createStore({
    theme: saved?.theme ?? "dark",
    accent: saved?.accent ?? "brand",
    cacheSizeMb: saved?.cacheSizeMb ?? 512,
    resumeOnLaunch: saved?.resumeOnLaunch ?? true,
    equalizer: createEqualizerState(saved?.equalizer),
    libraryTab: "PLAYLISTS",
    query: "",
    searchStatus: "idle",
    searchResults: null,
    homeFeed: null,
    homeStatus: "loading",
    detail: null,
    detailStatus: "idle",
    favourites: saved?.favourites ?? [],
    playlists: saved?.playlists ?? catalog.sample.allTracks().length ? saved?.playlists ?? defaultPlaylists(catalog) : [],
    history: saved?.history ?? [],
    recentSearches: saved?.recentSearches ?? [],
    playerTab: "queue",
    sheet: null,
    liveSource: SOURCE.SAMPLE,
    liveError: null,
  });

  const nav = createNavState();
  let audioGraph = null;
  let audioContext = null;

  const player = createPlayer({
    audio,
    onEqualizerState: () => {
      audioGraph?.apply(store.get().equalizer);
    },
  });

  /* ------------------------------------------------------------ rendering -- */

  function state() {
    return store.get();
  }

  function render() {
    const s = state();
    document.documentElement.dataset.dhunTheme = s.theme;
    document.documentElement.dataset.dhunAccent = s.accent;

    const detail = nav.detailStack[nav.detailStack.length - 1] ?? null;
    mount.innerHTML = shellMarkup(s, { detail });

    // The full player is a sheet over the shell (AppNavState.playerExpanded),
    // so it is appended rather than laid out inside it.
    if (nav.playerExpanded) {
      const overlay = document.createElement("div");
      overlay.innerHTML = fullPlayer(player, {
        lyrics: lyricsForCurrent(),
        activeLyricIndex: activeIndexForCurrent(),
        tab: s.playerTab,
      });
      mount.appendChild(overlay.firstElementChild);
    }

    if (s.sheet) renderSheet(s);
  }

  function shellMarkup(s, { detail }) {
    const twoPane = layoutOf(window.innerWidth) === "TwoPane";
    return html`
      <a class="dhun-skip-link" href="#main">Skip to content</a>
      <div class="dhun-shell" data-detail="${detail && twoPane ? "open" : "closed"}">
        <nav class="dhun-rail" aria-label="Primary">
          <div class="dhun-brand-block">
            <span class="dhun-wordmark">DHUN</span>
          </div>
          ${raw(
            USER_TABS.map(
              (tab) => html`<button
                class="dhun-nav-item"
                type="button"
                data-action="tab"
                data-tab="${tab}"
                aria-current="${nav.selectedTab === tab ? "page" : "false"}"
                aria-label="${TAB_TITLE[tab]} tab"
              >
                ${icon(TAB_ICON[tab])}
                <span>${TAB_TITLE[tab]}</span>
              </button>`,
            ).join(""),
          )}
          <button class="dhun-nav-item" type="button" data-action="open-settings" aria-label="Settings">
            ${icon("Palette")}
            <span>Settings</span>
          </button>
        </nav>

        <div class="dhun-content">
          <div class="dhun-master">
            <main id="main" class="dhun-scroll ${raw(detail && !twoPane ? "dhun-scroll--plain" : "")}">
              ${raw(masterMarkup(s, { detail, twoPane }))}
            </main>
            ${raw(miniPlayer(player))}
          </div>
          <div class="dhun-pane-seam" aria-hidden="true"></div>
          <aside class="dhun-detail" aria-label="Detail" tabindex="-1">${raw(detail && twoPane ? detailMarkup(s) : "")}</aside>
        </div>
      </div>

      <nav class="dhun-bottom-nav" aria-label="Primary">
        ${raw(
          USER_TABS.map(
            (tab) => html`<button
              class="dhun-nav-item"
              type="button"
              data-action="tab"
              data-tab="${tab}"
              aria-current="${nav.selectedTab === tab ? "page" : "false"}"
              aria-label="${TAB_TITLE[tab]} tab"
            >
              ${icon(TAB_ICON[tab])}
              <span>${TAB_TITLE[tab]}</span>
            </button>`,
          ).join(""),
        )}
      </nav>
    `;
  }

  function masterMarkup(s, { detail, twoPane }) {
    if (detail && !twoPane) return detailMarkup(s);
    switch (nav.selectedTab) {
      case AppTab.SEARCH:
        return searchMarkup(s);
      case AppTab.LIBRARY:
        return libraryMarkup(s);
      case AppTab.HOME:
      default:
        return homeMarkup(s);
    }
  }

  function homeMarkup(s) {
    return html`
      <header class="dhun-topbar">
        <h1 class="dhun-topbar__title">Home</h1>
        <div class="dhun-topbar__actions">
          <button class="dhun-icon-button" type="button" data-action="open-settings" aria-label="Settings">
            ${icon("Palette")}
          </button>
        </div>
      </header>
      ${raw(views.previewNotice())}
      ${s.homeStatus === "error"
        ? raw(views.errorState({ title: "Could not load Home", body: "The catalogue did not respond." }))
        : raw(views.homeScreen({ feed: s.homeFeed, source: s.liveSource, currentTrackId: player.currentTrack?.id }))}
    `;
  }

  function searchMarkup(s) {
    return html`
      <header class="dhun-topbar">
        <h1 class="dhun-topbar__title">Search</h1>
      </header>
      <div class="dhun-search">
        ${icon("Search")}
        <input
          id="search-input"
          type="search"
          value="${s.query}"
          placeholder="Search YouTube Music for songs, albums, and artists"
          aria-label="Search"
          autocomplete="off"
        />
        ${s.query
          ? html`<button class="dhun-icon-button" type="button" data-action="clear-search" aria-label="Clear search">
              ${icon("Close")}
            </button>`
          : ""}
      </div>
      ${raw(
        views.searchScreen({
          query: s.query,
          results: s.searchResults,
          source: s.liveSource,
          recentSearches: s.recentSearches,
          status: s.searchStatus,
        }),
      )}
    `;
  }

  function libraryMarkup(s) {
    return html`
      <header class="dhun-topbar">
        <h1 class="dhun-topbar__title">Your library</h1>
      </header>
      ${raw(
        views.libraryScreen({
          tab: s.libraryTab,
          playlists: s.playlists,
          history: s.history,
          favourites: s.favourites,
          downloadsAvailable: false,
        }),
      )}
    `;
  }

  function detailMarkup(s) {
    const detail = nav.detailStack[nav.detailStack.length - 1];
    if (!detail) return "";
    if (detail.kind === "settings") {
      return html`
        <header class="dhun-topbar">
          <button class="dhun-icon-button" type="button" data-action="back" aria-label="Back">${icon("ArrowBack")}</button>
          <h1 class="dhun-topbar__title">Settings</h1>
        </header>
        ${raw(
          views.settingsScreen({
            theme: s.theme,
            accent: s.accent,
            cacheSizeMb: s.cacheSizeMb,
            resumeOnLaunch: s.resumeOnLaunch,
            equalizer: s.equalizer,
          }),
        )}
      `;
    }
    if (s.detailStatus === "loading") return views.loadingState(4);
    if (s.detailStatus === "error") {
      return views.errorState({ title: "Could not load this page", body: s.detailError ?? "" });
    }
    if (!s.detail) return "";

    const back = html`<header class="dhun-topbar">
      <button class="dhun-icon-button" type="button" data-action="back" aria-label="Back">${icon("ArrowBack")}</button>
      <h1 class="dhun-topbar__title">${s.detail.title ?? "Detail"}</h1>
    </header>`;

    if (detail.kind === "artist") return back + views.artistScreen(s.detail);
    if (detail.kind === "album") return back + views.albumScreen(s.detail);
    return back + views.playlistScreen(s.detail);
  }

  function renderSheet(s) {
    const track = findTrack(s.sheet.trackId);
    if (!track) return;
    const overlay = document.createElement("div");
    overlay.className = "dhun-scrim";
    overlay.dataset.testid = "sheet";
    overlay.innerHTML =
      s.sheet.kind === "overflow"
        ? views.trackOverflowSheet(track, { inFavourites: s.favourites.includes(track.id) })
        : views.addToPlaylistSheet(track, s.playlists);
    document.body.appendChild(overlay);
    overlay.addEventListener("click", (event) => {
      if (event.target === overlay) closeSheet();
    });
  }

  function closeSheet() {
    store.set({ sheet: null });
    document.querySelector(".dhun-scrim")?.remove();
  }

  /* --------------------------------------------------------------- lyrics -- */

  function lyricsForCurrent() {
    const track = player.currentTrack;
    if (!track) return [];
    const lrc = SAMPLE_LYRICS[track.id];
    return lrc ? parseLrc(lrc) : [];
  }

  function activeIndexForCurrent() {
    const lines = lyricsForCurrent();
    return activeLineIndex(lines, player.state.positionMs);
  }

  /* --------------------------------------------------------------- actions -- */

  function findTrack(id) {
    return catalog.sample.allTracks().find((track) => track.id === id) ?? null;
  }

  function tracksByIds(ids) {
    return ids.map(findTrack).filter(Boolean);
  }

  function playTracks(tracks, startIndex = 0) {
    if (!tracks.length) return;
    player.play(startIndex, { queue: tracks });
    recordHistory(tracks[startIndex]);
    render();
  }

  function recordHistory(track) {
    if (!track) return;
    const history = [track, ...state().history.filter((entry) => entry.id !== track.id)].slice(0, 50);
    store.set({ history });
  }

  function openDetail(route) {
    store.set({ detailStatus: "loading", detail: null, detailError: null });
    nav.push(route);
    render();
    loadDetail(route);
  }

  async function loadDetail(route) {
    if (route.kind === "settings") {
      store.set({ detailStatus: "idle" });
      render();
      return;
    }
    try {
      const load = {
        artist: () => catalog.artist(route.id),
        album: () => catalog.album(route.id),
        playlist: () => catalog.playlist(route.id),
      }[route.kind];
      const result = await load();
      if (!result.ok) throw new Error(result.message);
      const shape = {
        artist: () => ({ title: result.artist.name, artist: result.artist, tracks: result.tracks, albums: result.albums }),
        album: () => ({ title: result.album.title, album: result.album, tracks: result.tracks }),
        playlist: () => ({
          title: result.playlist.name,
          playlist: result.playlist,
          tracks: result.playlist.isLikedFolder && result.playlist.trackIds.length === 0
            ? tracksByIds(state().favourites)
            : result.tracks,
        }),
      }[route.kind];
      store.set({ detail: shape(), detailStatus: "ok" });
    } catch (error) {
      store.set({ detailStatus: "error", detailError: error.message });
    }
    render();
  }

  function goBack() {
    const action = backAction({
      playerExpanded: nav.playerExpanded,
      detailDepth: nav.detailStack.length,
      tab: nav.selectedTab,
      hasTabHistory: nav.hasTabHistory,
    });
    switch (action) {
      case ShellBackAction.CollapsePlayer:
        nav.setPlayerExpanded(false);
        break;
      case ShellBackAction.PopDetail:
        nav.popDetail();
        store.set({ detail: null, detailStatus: "idle" });
        break;
      case ShellBackAction.ReturnToPreviousTab:
        nav.popTab();
        break;
      default:
        return; // PlatformDefault: there is nothing for the app to close.
    }
    render();
  }

  /* ---------------------------------------------------------------- events -- */

  delegate(mount, "click", "[data-action]", (event, target) => {
    const action = target.dataset.action;
    const s = state();
    switch (action) {
      case "tab":
        nav.selectTab(target.dataset.tab, {
          keepDetailOnTabChange: nav.layout === "TwoPane",
        });
        render();
        break;

      case "open-settings":
        openDetail({ kind: "settings" });
        break;

      case "back":
        goBack();
        break;

      case "play-track": {
        const track = findTrack(target.dataset.trackId);
        if (!track) break;
        const context = currentContextTracks();
        const index = Math.max(0, context.findIndex((entry) => entry.id === track.id));
        playTracks(context.length ? context : [track], context.length ? index : 0);
        break;
      }

      case "play-queue-index":
        player.play(Number(target.dataset.index));
        recordHistory(player.currentTrack);
        render();
        break;

      case "play-playlist": {
        const result = playlistTracks(target.dataset.id);
        playTracks(result, 0);
        openDetail({ kind: "playlist", id: target.dataset.id });
        break;
      }

      case "play-collection":
        playTracks(s.detail?.tracks ?? [], 0);
        break;

      case "toggle-play":
        player.toggle();
        render();
        break;

      case "next":
        player.next();
        recordHistory(player.currentTrack);
        render();
        break;

      case "previous":
        player.previous();
        render();
        break;

      case "toggle-shuffle":
        player.setShuffle(!player.state.shuffle);
        render();
        break;

      case "cycle-repeat":
        player.cycleRepeat();
        render();
        break;

      case "expand-player":
        nav.setPlayerExpanded(true);
        render();
        focusWithoutScroll(mount.querySelector('[data-action="collapse-player"]'));
        break;

      case "collapse-player":
        nav.setPlayerExpanded(false);
        render();
        focusWithoutScroll(mount.querySelector('[data-action="expand-player"]'));
        break;

      case "player-tab":
        store.set({ playerTab: target.dataset.tab });
        render();
        break;

      case "open-queue":
        store.set({ playerTab: "queue" });
        render();
        break;

      case "open-artist":
        openDetail({ kind: "artist", id: target.dataset.id });
        break;

      case "open-album":
        openDetail({ kind: "album", id: target.dataset.id });
        break;

      case "open-playlist":
        openDetail({ kind: "playlist", id: target.dataset.id });
        break;

      case "track-overflow":
        store.set({ sheet: { kind: "overflow", trackId: target.dataset.trackId } });
        render();
        break;

      case "add-to-playlist":
        store.set({ sheet: { kind: "add", trackId: target.dataset.trackId } });
        render();
        break;

      case "close-sheet":
        closeSheet();
        break;

      case "queue-next": {
        const track = findTrack(s.sheet?.trackId);
        if (track) {
          const queue = [...player.state.queue];
          const at = player.state.index + 1;
          queue.splice(at, 0, track);
          player.setQueue(queue, player.state.index);
        }
        closeSheet();
        render();
        break;
      }

      case "toggle-favourite": {
        const trackId = s.sheet?.trackId;
        if (!trackId) break;
        const favourites = s.favourites.includes(trackId)
          ? s.favourites.filter((id) => id !== trackId)
          : [...s.favourites, trackId];
        store.set({ favourites });
        closeSheet();
        render();
        break;
      }

      case "go-to-artist": {
        const track = findTrack(s.sheet?.trackId);
        closeSheet();
        if (track) {
          const artist = catalog.sample.allTracks().find((entry) => entry.id === track.id);
          const match = findArtistByName(track.artist);
          if (match) openDetail({ kind: "artist", id: match.id });
          else if (artist) render();
        }
        break;
      }

      case "go-to-album": {
        const track = findTrack(s.sheet?.trackId);
        closeSheet();
        if (track?.albumId) openDetail({ kind: "album", id: track.albumId });
        break;
      }

      case "library-tab":
        store.set({ libraryTab: target.dataset.tab });
        render();
        break;

      case "recent-search":
        store.set({ query: target.dataset.term });
        runSearch(target.dataset.term);
        break;

      case "forget-search":
        store.set({ recentSearches: s.recentSearches.filter((term) => term !== target.dataset.term) });
        render();
        break;

      case "clear-search":
        store.set({ query: "", searchResults: null, searchStatus: "idle" });
        render();
        focusWithoutScroll(document.getElementById("search-input"));
        break;

      case "remove-history":
        store.set({ history: s.history.filter((entry) => entry.id !== target.dataset.trackId) });
        render();
        break;

      case "retry":
        if (nav.selectedTab === AppTab.HOME) loadHome();
        else if (s.query) runSearch(s.query);
        else if (nav.detailStack.length) loadDetail(nav.detailStack[nav.detailStack.length - 1]);
        break;

      /* -- settings ------------------------------------------------------- */
      case "set-theme":
        store.set({ theme: target.dataset.theme });
        render();
        break;

      case "set-accent":
        store.set({ accent: target.dataset.accent });
        render();
        break;

      case "set-cache":
        store.set({ cacheSizeMb: Number(target.dataset.mb) });
        render();
        break;

      case "toggle-resume":
        store.set({ resumeOnLaunch: !s.resumeOnLaunch });
        render();
        break;

      case "toggle-equalizer":
        store.set({ equalizer: { ...s.equalizer, enabled: !s.equalizer.enabled } });
        applyEqualizer();
        render();
        break;

      case "set-preset":
        store.set({ equalizer: withPreset(s.equalizer, target.dataset.preset) });
        applyEqualizer();
        render();
        break;

      default:
        break;
    }
  });

  /* Range inputs (seek bar, equaliser) fire `input`, not `click`. */
  delegate(mount, "input", "[data-action='seek']", (event, target) => {
    player.seek(Number(target.value));
    renderPlayerSurfaces();
  });

  delegate(mount, "input", "[data-action='set-band']", (event, target) => {
    const index = Number(target.dataset.index);
    store.set({ equalizer: withBandGain(state().equalizer, index, Number(target.value)) });
    applyEqualizer();
    renderPlayerSurfaces();
  });

  delegate(mount, "input", "[data-action='set-preamp']", (event, target) => {
    store.set({ equalizer: withPreamp(state().equalizer, Number(target.value)) });
    applyEqualizer();
    renderPlayerSurfaces();
  });

  delegate(mount, "submit", "form[data-action='new-playlist']", (event, target) => {
    event.preventDefault();
    const input = target.querySelector("input[name='name']");
    const name = input.value.trim();
    if (name === "") return;
    const playlists = [
      ...state().playlists,
      { id: `pl-${Date.now().toString(36)}`, name, trackIds: [] },
    ];
    store.set({ playlists });
    render();
  });

  delegate(mount, "input", "#search-input", (event, target) => {
    store.set({ query: target.value });
    runSearch(target.value);
  });

  /* ----------------------------------------------------------- equalizer -- */

  function applyEqualizer() {
    store.get().equalizer;
    if (!audio) return; // No <audio> element: the sliders are stateful, the
    // engine is not attached. The UI stays usable and honest.
    if (!audioContext && AudioContextCtor) {
      try {
        audioContext = new AudioContextCtor();
      } catch {
        audioContext = null;
      }
    }
    if (!audioGraph && audioContext) {
      audioGraph = createWebAudioEqualizer(audioContext, audio);
    }
    audioGraph?.apply(store.get().equalizer);
  }

  /* -------------------------------------------------------------- keyboard -- */

  document.addEventListener("keydown", (event) => {
    const typing = event.target instanceof HTMLElement && /^(INPUT|TEXTAREA)$/.test(event.target.tagName);
    if (event.key === "Escape") {
      if (state().sheet) return closeSheet();
      if (nav.playerExpanded || nav.hasDetail) return goBack();
      return;
    }
    if (typing) return;
    if (event.key === " ") {
      event.preventDefault();
      player.toggle();
      render();
    } else if (event.key === "ArrowRight" && event.shiftKey) {
      player.next();
      render();
    } else if (event.key === "ArrowLeft" && event.shiftKey) {
      player.previous();
      render();
    } else if (event.key === "/") {
      event.preventDefault();
      nav.selectTab(AppTab.SEARCH);
      render();
      focusWithoutScroll(document.getElementById("search-input"));
    }
  });

  /* ----------------------------------------------------------------- data -- */

  async function loadHome() {
    store.set({ homeStatus: "loading" });
    render();
    const feed = await catalog.homeFeed();
    store.set({ homeFeed: feed.ok ? feed : null, homeStatus: feed.ok ? "ok" : "error" });
    render();
  }

  let searchTimer = null;
  function runSearch(query) {
    clearTimeout(searchTimer);
    const trimmed = query.trim();
    if (trimmed === "") {
      store.set({ searchResults: null, searchStatus: "idle" });
      render();
      return;
    }
    store.set({ searchStatus: "loading" });
    render();
    searchTimer = setTimeout(async () => {
      const results = await catalog.search(trimmed);
      if (!results.ok) {
        store.set({ searchStatus: "error" });
      } else {
        const recentSearches = [trimmed, ...state().recentSearches.filter((t) => t !== trimmed)].slice(0, 8);
        store.set({ searchResults: results, searchStatus: "ok", recentSearches });
      }
      render();
    }, 220);
  }

  function currentContextTracks() {
    const detail = state().detail;
    if (detail?.tracks?.length) return detail.tracks;
    const feed = state().homeFeed;
    if (feed?.sections?.length) return feed.sections.flatMap((section) => section.tracks);
    return catalog.sample.allTracks();
  }

  function playlistTracks(id) {
    const playlist = state().playlists.find((entry) => entry.id === id);
    if (!playlist) return [];
    if (playlist.isLikedFolder) return tracksByIds(state().favourites);
    return tracksByIds(playlist.trackIds);
  }

  function findArtistByName(name) {
    return catalog.sample.artists().find((artist) => artist.name === name) ?? null;
  }

  /* ----------------------------------------------------------- persistence -- */

  let saveTimer = null;
  store.subscribe((s) => {
    if (!storage) return;
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => writePersisted(persistedSlice(s)), 250);
  });

  player.subscribe(() => {
    renderPlayerSurfaces();
  });

  /** The player moves 4x a second; re-rendering the whole shell that often
   *  would fight the user's scroll position. Only the player surfaces move. */
  function renderPlayerSurfaces() {
    const mini = mount.querySelector(".dhun-mini");
    if (mini) mini.outerHTML = miniPlayer(player);
    if (nav.playerExpanded) {
      const full = mount.querySelector(".dhun-full");
      if (full) {
        full.outerHTML = fullPlayer(player, {
          lyrics: lyricsForCurrent(),
          activeLyricIndex: activeIndexForCurrent(),
          tab: state().playerTab,
        });
      }
    }
    const save = state();
    if (storage) {
      clearTimeout(saveTimer);
      saveTimer = setTimeout(() => writePersisted(persistedSlice(save)), 250);
    }
  }

  window.addEventListener("resize", () => {
    render();
  });

  window.addEventListener("hashchange", () => applyHashRoute());

  /** Deep links: #/artist/<id>, #/album/<id>, #/playlist/<id>, #/settings. */
  function applyHashRoute() {
    const match = /^#\/(artist|album|playlist)\/(.+)$/.exec(window.location.hash);
    if (match) return openDetail({ kind: match[1], id: match[2] });
    if (window.location.hash === "#/settings") return openDetail({ kind: "settings" });
    return undefined;
  }

  /* ------------------------------------------------------------------ boot -- */

  render();
  loadHome();
  catalog.probeLive().then((live) => {
    store.set({
      liveSource: live ? SOURCE.LIVE : SOURCE.SAMPLE,
      liveError: catalog.lastError,
    });
    render();
  });
  applyHashRoute();

  // Resume on launch: restore the last queue (NowPlayingPersistence's job in
  // the app) without auto-playing — no browser may start audio unprompted.
  if (saved?.resumeOnLaunch !== false && saved?.queue?.length) {
    player.setQueue(tracksByIds(saved.queue.map((track) => track.id)), saved.queueIndex ?? 0);
    if (typeof saved.positionMs === "number") player.seek(saved.positionMs);
    render();
  }

  return { store, nav, player, render, catalog, applyHashRoute, playTracks, closeSheet };
}

function defaultPlaylists(catalog) {
  return [
    {
      id: "pl-liked",
      name: "Liked Songs",
      isLikedFolder: true,
      trackIds: [],
    },
    ...catalog.sample.playlists().map((playlist) => ({ ...playlist })),
  ];
}

export { escapeHtml, BACKEND };
