/* ===========================================================================
   DHUN Web — application logic.

   Zero-build vanilla ES modules: GitHub Pages serves static files, so there
   is no bundler, no transpiler and no server-side code in this client.

   Architecture note (why the bridge exists)
   ----------------------------------------
   This client cannot talk to YouTube directly. DHUN's extraction runs
   server-side for two independent reasons, both documented in this repo:

   1. shared/src/commonMain/kotlin/dev/dhun/core/Entities.kt, StreamInfo:
      "googlevideo binds a signed stream URL to the identity that requested
      it: fetching the bytes with a different User-Agent is rejected (403 at
      open, or mid-stream)". A browser's <audio> element always sends the
      browser's own User-Agent, so it can never fetch the bytes itself —
      they must be proxied by something that replays StreamInfo.userAgent.

   2. Extraction itself (InnerTube POSTs to music.youtube.com/youtubei/v1,
      PO tokens, visitor ids, signature timestamps — see
      shared/src/commonMain/kotlin/dev/dhun/innertube/InnerTubeClient.kt)
      is a cross-origin request from a Pages origin, and GitHub Pages is
      static-only with no runtime to perform it.

   So: metadata + bytes both flow through a DHUN Bridge. Point one at this
   client in the sidebar. With no bridge connected the UI still runs in
   demo mode against locally generated audio, so the player path is
   exercisable without any network.
   =========================================================================== */

import { ICONS } from "./icons.js";

const STORE_KEY = "dhun.web.v1";
const DEMO_TRACK_COUNT = 3;

/** @typedef {{id:string,title:string,artistName:string,albumName?:string|null,
 *   durationSeconds?:number|null,thumbnailUrl?:string|null,explicit?:boolean}} Track */

/* ------------------------------------------------------------------ state - */

const state = {
  bridgeUrl: "",
  bridgeStatus: "disconnected", // disconnected | checking | ok | error
  bridgeError: "",
  view: "search", // search | queue
  query: "",
  results: [],
  suggestions: [],
  searching: false,
  searchError: "",
  queue: [],
  currentIndex: -1,
  isPlaying: false,
  currentTime: 0,
  duration: 0,
  volume: 0.85,
  shuffle: false,
  repeat: "off", // off | all | one
  fullscreen: false,
  demoMode: true,
  resolving: false,
};

const audio = new Audio();
audio.preload = "none";
audio.crossOrigin = "anonymous";

const el = {};
const els = [
  "bridgeDot", "bridgeLabel", "bridgeInput", "bridgeConnect", "bridgeDisconnect",
  "demoToggle", "searchInput", "results", "mainHeading", "mainSub", "queueList",
  "queueCount", "mpArtwork", "mpTitle", "mpArtist", "playBtn", "prevBtn", "nextBtn",
  "shuffleBtn", "repeatBtn", "seek", "timeNow", "timeTotal", "volume", "volumeBtn",
  "toasts", "fullscreen", "fsArtwork", "fsTitle", "fsArtist", "fsClose", "fsArtBg",
  "navSearch", "navQueue", "fsPrev", "fsPlay", "fsNext",
];

/** Icons live in a JS module so the HTML shell stays markup-only. */
const iconTargets = {
  navSearchIcon: "search",
  navQueueIcon: "queue",
  bridgeIcon: "plug",
  demoIcon: "musicNote",
  searchIcon: "search",
  prevBtn: "skipPrevious",
  nextBtn: "skipNext",
  shuffleBtn: "shuffle",
  repeatBtn: "repeat",
  volumeBtn: "volume",
  fsPrev: "skipPrevious",
  fsNext: "skipNext",
  fsClose: "close",
};

function injectIcons() {
  for (const [id, name] of Object.entries(iconTargets)) {
    const node = document.getElementById(id);
    if (node) node.innerHTML = ICONS[name];
  }
}

/* -------------------------------------------------------------- utilities - */

function formatTime(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return "--:--";
  const total = Math.floor(seconds);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => (
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]
  ));
}

/** Deterministic 32-bit hash — used only to pick placeholder artwork colours. */
function hashString(value) {
  let h = 2166136261;
  for (let i = 0; i < value.length; i += 1) {
    h ^= value.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return Math.abs(h);
}

/**
 * Artwork source for a track. Falls back to a generated gradient tile so the
 * UI never shows a broken image when a bridge omits thumbnailUrl.
 */
function artworkFor(track, size = 120) {
  if (track?.thumbnailUrl) return track.thumbnailUrl;
  const seed = hashString(track?.id || track?.title || "dhun");
  const hue = seed % 360;
  const hue2 = (hue + 48) % 360;
  const initials = (track?.title || "D")
    .split(/\s+/).slice(0, 2).map((w) => w[0] ?? "").join("").toUpperCase();
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
<stop offset="0" stop-color="hsl(${hue},52%,26%)"/>
<stop offset="1" stop-color="hsl(${hue2},46%,13%)"/></linearGradient></defs>
<rect width="${size}" height="${size}" fill="url(#g)"/>
<text x="50%" y="53%" dominant-baseline="middle" text-anchor="middle"
font-family="system-ui,sans-serif" font-size="${Math.round(size * 0.34)}"
font-weight="600" fill="rgba(255,255,255,0.55)">${escapeHtml(initials)}</text></svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

function toast(message, tone = "info", ms = 4200) {
  const node = document.createElement("div");
  node.className = "toast";
  node.dataset.tone = tone;
  node.textContent = message;
  el.toasts.appendChild(node);
  setTimeout(() => node.remove(), ms);
}

function persist() {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify({
      bridgeUrl: state.bridgeUrl,
      volume: state.volume,
      shuffle: state.shuffle,
      repeat: state.repeat,
      demoMode: state.demoMode,
    }));
  } catch { /* private mode / storage disabled — non-fatal */ }
}

function restore() {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (!raw) return;
    const saved = JSON.parse(raw);
    if (typeof saved.bridgeUrl === "string") state.bridgeUrl = saved.bridgeUrl;
    if (typeof saved.volume === "number") state.volume = saved.volume;
    if (typeof saved.shuffle === "boolean") state.shuffle = saved.shuffle;
    if (saved.repeat === "off" || saved.repeat === "all" || saved.repeat === "one") {
      state.repeat = saved.repeat;
    }
    if (typeof saved.demoMode === "boolean") state.demoMode = saved.demoMode;
  } catch { /* corrupt entry — fall back to defaults */ }
}

/* --------------------------------------------------------- demo catalogue - */

/**
 * Demo mode plays audio generated at build time by web/tools/make-demo-audio.py
 * (run by the Pages workflow; also runnable locally). It exists so the player,
 * queue and Media Session paths are verifiable without a bridge or network.
 */
function demoCatalogue() {
  const base = new URL("../assets/demo/", document.baseURI).href;
  const specs = [
    { n: 1, title: "Signal Drift", artist: "DHUN Demo Ensemble", len: 24 },
    { n: 2, title: "Low Orbit", artist: "DHUN Demo Ensemble", len: 24 },
    { n: 3, title: "Nightbus", artist: "DHUN Demo Ensemble", len: 24 },
  ];
  return specs.slice(0, DEMO_TRACK_COUNT).map((spec) => ({
    id: `demo-${spec.n}`,
    title: spec.title,
    artistName: spec.artist,
    albumName: "Synthesised Demo Audio",
    durationSeconds: spec.len,
    thumbnailUrl: null,
    explicit: false,
    demo: true,
    demoSrc: `${base}demo-${spec.n}.wav`,
  }));
}

/* ---------------------------------------------------------- bridge client - */

function bridgeBase() {
  return (state.bridgeUrl || "").trim().replace(/\/+$/, "");
}

async function bridgeFetch(path, options = {}) {
  if (!bridgeBase()) {
    // Without this guard the request would be resolved against the page's own
    // origin and 404 on GitHub Pages, which reads as a mystery failure.
    throw new Error("no bridge URL configured");
  }
  const url = `${bridgeBase()}${path}`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 12000);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status} ${response.statusText}`);
    }
    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}

function setBridgeStatus(status, message = "") {
  state.bridgeStatus = status;
  state.bridgeError = message;
  el.bridgeDot.dataset.state = status === "ok" ? "ok" : status === "error" ? "error"
    : status === "checking" ? "checking" : "";
  el.bridgeLabel.textContent = status === "ok" ? "Bridge connected"
    : status === "checking" ? "Checking…"
    : status === "error" ? `Bridge error: ${message || "unreachable"}`
    : "No bridge";
  el.bridgeConnect.disabled = status === "checking";
  el.bridgeDisconnect.disabled = status === "disconnected" || status === "checking";
}

async function checkBridge(persistOnSuccess = false) {
  if (!bridgeBase()) {
    setBridgeStatus("disconnected");
    return false;
  }
  setBridgeStatus("checking");
  try {
    const health = await bridgeFetch("/health");
    if (!health || health.ok !== true) throw new Error("health check failed");
    setBridgeStatus("ok");
    state.demoMode = false;
    if (persistOnSuccess) {
      persist();
      toast(`Bridge connected (${health.engine || "unknown engine"} v${health.version || "?"})`, "success");
    }
    return true;
  } catch (error) {
    setBridgeStatus("error", error.name === "AbortError" ? "timed out" : error.message);
    return false;
  }
}

async function bridgeSearch(query) {
  const data = await bridgeFetch(`/search?q=${encodeURIComponent(query)}&filter=songs`);
  return Array.isArray(data?.songs) ? data.songs : [];
}

/* -------------------------------------------------------------- audio path - */

/**
 * The URL this client hands to the <audio> element.
 *
 * Bridge mode points at the bridge's byte proxy (`/audio/{id}`), NOT at the
 * googlevideo URL from /stream — see the StreamInfo.userAgent contract in the
 * header comment: the browser's own User-Agent gets a 403.
 */
function audioSrcFor(track) {
  if (track.demo) return track.demoSrc;
  return `${bridgeBase()}/audio/${encodeURIComponent(track.id)}`;
}

async function playIndex(index, { autoplay = true } = {}) {
  const track = state.queue[index];
  if (!track) return;
  state.currentIndex = index;
  state.resolving = true;
  render();

  if (!track.demo && !(bridgeBase() && state.bridgeStatus === "ok")) {
    const ok = await checkBridge();
    if (!ok) {
      state.resolving = false;
      toast("Connect a bridge to stream music — see the sidebar.", "error", 6000);
      render();
      return;
    }
  }

  audio.src = audioSrcFor(track);
  audio.volume = state.volume;
  if (autoplay) {
    try {
      await audio.play();
      state.isPlaying = true;
    } catch (error) {
      state.isPlaying = false;
      state.resolving = false;
      toast(`Playback failed: ${error.message}`, "error", 6000);
      render();
      return;
    }
  }
  state.resolving = false;
  updateMediaSession(track);
  render();
}

function togglePlay() {
  if (state.currentIndex < 0) {
    const startIndex = state.queue.length > 0 ? 0 : -1;
    if (startIndex < 0) {
      toast("Nothing queued yet — search for something first.");
      return;
    }
    playIndex(startIndex);
    return;
  }
  if (state.isPlaying) {
    audio.pause();
  } else {
    audio.play().catch((error) => toast(`Playback failed: ${error.message}`, "error"));
  }
}

function nextIndex(from, direction) {
  const len = state.queue.length;
  if (len === 0) return -1;
  if (state.shuffle && len > 1) {
    let pick = from;
    let guard = 0;
    while (pick === from && guard < 12) {
      pick = Math.floor(Math.random() * len);
      guard += 1;
    }
    return pick;
  }
  return (from + direction + len) % len;
}

function step(direction) {
  if (state.currentIndex < 0) return;
  const target = nextIndex(state.currentIndex, direction);
  if (target >= 0) playIndex(target);
}

function addToQueue(track) {
  const exists = state.queue.some((t) => t.id === track.id);
  state.queue.push(track);
  render();
  toast(exists ? `Already in queue — added again: ${track.title}` : `Added to queue: ${track.title}`);
  if (state.currentIndex < 0) playIndex(state.queue.length - 1, { autoplay: false });
}

function removeFromQueue(index) {
  const wasCurrent = index === state.currentIndex;
  state.queue.splice(index, 1);
  if (wasCurrent) {
    audio.pause();
    audio.removeAttribute("src");
    state.currentIndex = -1;
    state.isPlaying = false;
    if (state.queue.length > 0) playIndex(Math.min(index, state.queue.length - 1));
  } else if (index < state.currentIndex) {
    state.currentIndex -= 1;
  }
  render();
}

function clearQueue() {
  audio.pause();
  audio.removeAttribute("src");
  state.queue = [];
  state.currentIndex = -1;
  state.isPlaying = false;
  render();
}

/* -------------------------------------------------------------- searching - */

async function runSearch(query) {
  state.query = query;
  state.searchError = "";

  // A cached "ok" status is not enough on its own: the URL can have been
  // cleared since the last health check. Require both.
  const usableNow = Boolean(bridgeBase()) && state.bridgeStatus === "ok";
  if (!usableNow) {
    const connected = await checkBridge();
    if (!connected) {
      state.searching = false;
      state.searchError = state.bridgeStatus === "error" ? state.bridgeError : "no-bridge";
      render();
      return;
    }
    state.demoMode = false;
  }

  state.searching = true;
  render();
  try {
    const tracks = await bridgeSearch(query);
    state.results = tracks;
    if (tracks.length === 0) state.searchError = "empty";
  } catch (error) {
    state.results = [];
    state.searchError = error.message || "search failed";
  } finally {
    state.searching = false;
    render();
  }
}

function loadDemoCatalogue() {
  state.demoMode = true;
  state.results = demoCatalogue();
  state.searchError = "";
  state.searching = false;
  state.query = "";
  el.searchInput.value = "";
  persist();
  render();
  toast("Demo mode: 3 locally generated tracks. Connect a bridge for real music.", "info", 6000);
}

/* -------------------------------------------------------- media session --- */

function updateMediaSession(track) {
  if (!("mediaSession" in navigator) || !track) return;
  try {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: track.artistName || "Unknown artist",
      album: track.albumName || "DHUN",
      artwork: [{ src: artworkFor(track, 512), sizes: "512x512", type: "image/svg+xml" }],
    });
    navigator.mediaSession.setActionHandler("play", () => togglePlay());
    navigator.mediaSession.setActionHandler("pause", () => togglePlay());
    navigator.mediaSession.setActionHandler("previoustrack", () => step(-1));
    navigator.mediaSession.setActionHandler("nexttrack", () => step(1));
  } catch { /* unsupported action — ignore */ }
}

/* ----------------------------------------------------------------- render - */

function trackRow(track, index, { activeId = null, showRemove = false, listName = "results" } = {}) {
  const active = activeId !== null && track.id === activeId;
  const row = document.createElement(showRemove ? "div" : "button");
  row.className = "track-row";
  row.type = showRemove ? undefined : "button";
  if (active) row.dataset.active = "true";
  row.innerHTML = `
    <span class="track-index">${active && state.isPlaying ? ICONS.equalizer : index + 1}</span>
    <img class="artwork" alt="" loading="lazy" src="${escapeHtml(artworkFor(track))}">
    <span class="track-meta">
      <span class="track-title">${escapeHtml(track.title)}${track.explicit ? '<span class="explicit">E</span>' : ""}</span>
      <span class="track-artist">${escapeHtml(track.artistName || "Unknown artist")}${
        track.albumName ? ` · ${escapeHtml(track.albumName)}` : ""}</span>
    </span>
    <span class="track-duration">${formatTime(track.durationSeconds ?? NaN)}</span>`;

  if (showRemove) {
    const remove = document.createElement("button");
    remove.className = "tbtn";
    remove.title = "Remove from queue";
    remove.setAttribute("aria-label", `Remove ${track.title} from queue`);
    remove.innerHTML = ICONS.close;
    remove.addEventListener("click", () => removeFromQueue(index));
    row.appendChild(remove);
  } else {
    row.addEventListener("click", () => {
      const existing = state.queue.findIndex((t) => t.id === track.id);
      if (existing >= 0) {
        playIndex(existing);
      } else {
        state.queue = state.queue.concat(state.results);
        const newIndex = state.queue.findIndex((t) => t.id === track.id);
        playIndex(newIndex >= 0 ? newIndex : 0);
      }
    });
    row.addEventListener("contextmenu", (event) => {
      event.preventDefault();
      addToQueue(track);
    });
  }
  row.dataset.list = listName;
  return row;
}

function stateCard({ title, body, tone = "info", actions = [] }) {
  const card = document.createElement("div");
  card.className = "state-card";
  card.dataset.tone = tone;
  card.innerHTML = `<h2>${escapeHtml(title)}</h2><p>${escapeHtml(body)}</p>`;
  const row = document.createElement("div");
  row.className = "state-actions";
  for (const action of actions) {
    const btn = document.createElement("button");
    btn.className = `btn ${action.primary ? "btn-accent" : "btn-ghost"}`;
    btn.textContent = action.label;
    btn.addEventListener("click", action.onClick);
    row.appendChild(btn);
  }
  card.appendChild(row);
  return card;
}

function renderResults() {
  el.results.replaceChildren();
  const activeId = state.currentIndex >= 0 ? state.queue[state.currentIndex]?.id ?? null : null;

  if (state.searching) {
    el.results.appendChild(stateCard({
      title: "Searching…",
      body: `Asking the bridge for "${state.query}".`,
    }));
    return;
  }

  if (state.searchError === "no-bridge") {
    el.results.appendChild(stateCard({
      title: "No bridge connected",
      body: "DHUN's extraction runs server-side: a browser cannot request YouTube "
        + "streams itself (see Entities.kt — googlevideo binds stream URLs to the "
        + "requesting User-Agent, so the bytes must be proxied). Run the bridge, "
        + "paste its URL in the sidebar, or explore the UI with generated demo audio.",
      actions: [
        { label: "Load demo audio", primary: true, onClick: loadDemoCatalogue },
        { label: "Retry bridge", onClick: () => checkBridge() },
      ],
    }));
    return;
  }

  if (state.searchError === "error" || (state.searchError && state.searchError !== "empty")) {
    el.results.appendChild(stateCard({
      title: "Search failed",
      body: `${state.searchError}`,
      tone: "error",
      actions: [{ label: "Retry", primary: true, onClick: () => runSearch(state.query) }],
    }));
    return;
  }

  if (state.searchError === "empty") {
    el.results.appendChild(stateCard({
      title: "No results",
      body: `Nothing came back for "${state.query}".`,
    }));
    return;
  }

  if (state.results.length === 0) {
    el.results.appendChild(stateCard({
      title: state.demoMode ? "Demo mode" : "Search YouTube Music",
      body: state.demoMode
        ? "Generated demo tracks are loaded below so you can exercise the player "
          + "without a bridge. Connect one in the sidebar for real music."
        : "Type a song, artist or album above to search through the bridge.",
      actions: state.demoMode ? [] : [
        { label: "Load demo audio", onClick: loadDemoCatalogue },
      ],
    }));
    if (state.demoMode) {
      const list = document.createElement("div");
      list.className = "track-list";
      for (const [index, track] of state.results.entries()) {
        list.appendChild(trackRow(track, index, { activeId }));
      }
      el.results.appendChild(list);
    }
    return;
  }

  const list = document.createElement("div");
  list.className = "track-list";
  for (const [index, track] of state.results.entries()) {
    list.appendChild(trackRow(track, index, { activeId }));
  }
  el.results.appendChild(list);
}

function renderQueue() {
  el.queueList.replaceChildren();
  el.queueCount.textContent = `${state.queue.length} track${state.queue.length === 1 ? "" : "s"}`;
  if (state.queue.length === 0) {
    const empty = document.createElement("p");
    empty.className = "queue-empty";
    empty.textContent = "Queue is empty. Play something to start it.";
    el.queueList.appendChild(empty);
    return;
  }
  const list = document.createElement("div");
  list.className = "track-list";
  for (const [index, track] of state.queue.entries()) {
    list.appendChild(trackRow(track, index, {
      activeId: state.currentIndex >= 0 ? state.queue[state.currentIndex]?.id ?? null : null,
      showRemove: true,
      listName: "queue",
    }));
  }
  el.queueList.appendChild(list);
}

function renderMiniplayer() {
  const track = state.currentIndex >= 0 ? state.queue[state.currentIndex] : null;
  el.mpTitle.textContent = track?.title ?? "Nothing playing";
  el.mpArtist.textContent = track
    ? `${track.artistName || "Unknown artist"}${state.resolving ? " · resolving…" : ""}`
    : "Pick a track to begin";
  el.mpArtwork.src = artworkFor(track ?? { title: "D", id: "none" });
  el.playBtn.innerHTML = state.resolving ? ICONS.spinner : state.isPlaying ? ICONS.pause : ICONS.play;
  el.playBtn.setAttribute("aria-label", state.isPlaying ? "Pause" : "Play");
  el.fsPlay.innerHTML = state.resolving ? ICONS.spinner : state.isPlaying ? ICONS.pause : ICONS.play;
  el.fsPlay.setAttribute("aria-label", state.isPlaying ? "Pause" : "Play");
  el.fsPrev.disabled = state.queue.length === 0;
  el.fsNext.disabled = state.queue.length === 0;
  el.prevBtn.disabled = state.queue.length === 0;
  el.nextBtn.disabled = state.queue.length === 0;
  el.shuffleBtn.setAttribute("aria-pressed", String(state.shuffle));
  el.repeatBtn.innerHTML = state.repeat === "one" ? ICONS.repeatOne : ICONS.repeat;
  el.repeatBtn.setAttribute("aria-pressed", String(state.repeat !== "off"));
  el.repeatBtn.title = `Repeat: ${state.repeat}`;

  const duration = Number.isFinite(audio.duration) && audio.duration > 0
    ? audio.duration
    : (track?.durationSeconds ?? 0);
  el.seek.max = String(duration || 0);
  el.seek.value = String(Math.min(state.currentTime, duration || 0));
  el.seek.disabled = !track;
  el.timeNow.textContent = formatTime(state.currentTime);
  el.timeTotal.textContent = formatTime(duration);

  el.volume.value = String(state.volume * 100);
  el.volumeBtn.innerHTML = state.volume === 0 ? ICONS.volumeOff : ICONS.volume;

  el.fsArtwork.src = artworkFor(track ?? { title: "D", id: "none" }, 512);
  el.fsArtBg.style.backgroundImage = `url("${artworkFor(track ?? { title: "D", id: "none" }, 512)}")`;
  el.fsTitle.textContent = track?.title ?? "Nothing playing";
  el.fsArtist.textContent = track ? (track.artistName || "Unknown artist") : "";
}

function renderView() {
  const isSearch = state.view === "search";
  el.navSearch.setAttribute("aria-current", isSearch ? "page" : "false");
  el.navQueue.setAttribute("aria-current", isSearch ? "false" : "page");
  el.mainHeading.textContent = isSearch ? "Search" : "Queue";
  el.mainSub.textContent = isSearch
    ? (state.demoMode
      ? "Demo mode — generated audio, no bridge required."
      : "Results come from your DHUN Bridge.")
    : "Everything lined up next.";
  const searchVisible = isSearch ? "" : "none";
  el.searchInput.parentElement.style.display = isSearch ? "flex" : "none";
  el.results.style.display = searchVisible;
  el.searchInput.disabled = !isSearch;
}

function render() {
  renderView();
  renderResults();
  renderQueue();
  renderMiniplayer();
  el.fullscreen.dataset.open = String(state.fullscreen);
}

/* ------------------------------------------------------------------ wiring - */

function bindAudioEvents() {
  audio.addEventListener("play", () => { state.isPlaying = true; renderMiniplayer(); });
  audio.addEventListener("pause", () => { state.isPlaying = false; renderMiniplayer(); });
  audio.addEventListener("timeupdate", () => {
    state.currentTime = audio.currentTime;
    el.timeNow.textContent = formatTime(state.currentTime);
    if (!el.seek.matches(":active")) {
      el.seek.value = String(audio.currentTime);
    }
  });
  audio.addEventListener("durationchange", () => {
    state.duration = audio.duration;
    renderMiniplayer();
  });
  audio.addEventListener("ended", () => {
    if (state.repeat === "one") {
      audio.currentTime = 0;
      audio.play().catch(() => {});
      return;
    }
    const target = nextIndex(state.currentIndex, 1);
    const wrapped = target <= state.currentIndex;
    if (wrapped && state.repeat === "off") {
      state.isPlaying = false;
      renderMiniplayer();
      return;
    }
    playIndex(target);
  });
  audio.addEventListener("error", () => {
    if (!audio.src) return;
    state.isPlaying = false;
    state.resolving = false;
    const track = state.queue[state.currentIndex];
    toast(`Could not play "${track?.title ?? "track"}" — check the bridge.`, "error", 6000);
    render();
  });
}

function bindUiEvents() {
  el.bridgeConnect.addEventListener("click", async () => {
    state.bridgeUrl = el.bridgeInput.value.trim();
    const ok = await checkBridge(true);
    if (ok) {
      state.demoMode = false;
      state.searchError = "";
      if (state.query) runSearch(state.query);
      else render();
    } else {
      toast(`Bridge unreachable: ${state.bridgeError}`, "error", 6000);
    }
  });

  el.bridgeDisconnect.addEventListener("click", () => {
    state.bridgeUrl = "";
    setBridgeStatus("disconnected");
    el.bridgeInput.value = "";
    persist();
    render();
  });

  el.demoToggle.addEventListener("click", loadDemoCatalogue);

  let debounce = 0;
  el.searchInput.addEventListener("input", () => {
    const value = el.searchInput.value.trim();
    if (!value) {
      state.results = state.demoMode ? demoCatalogue() : [];
      state.searchError = "";
      state.query = "";
      render();
      return;
    }
    clearTimeout(debounce);
    debounce = setTimeout(() => runSearch(value), 420);
  });
  el.searchInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      clearTimeout(debounce);
      const value = el.searchInput.value.trim();
      if (value) runSearch(value);
    }
  });

  el.navSearch.addEventListener("click", () => { state.view = "search"; render(); });
  el.navQueue.addEventListener("click", () => { state.view = "queue"; render(); });

  el.playBtn.addEventListener("click", togglePlay);
  el.prevBtn.addEventListener("click", () => step(-1));
  el.nextBtn.addEventListener("click", () => step(1));

  el.shuffleBtn.addEventListener("click", () => {
    state.shuffle = !state.shuffle;
    persist();
    render();
  });

  el.repeatBtn.addEventListener("click", () => {
    state.repeat = state.repeat === "off" ? "all" : state.repeat === "all" ? "one" : "off";
    persist();
    render();
  });

  el.seek.addEventListener("input", () => {
    state.currentTime = Number(el.seek.value);
    el.timeNow.textContent = formatTime(state.currentTime);
  });
  el.seek.addEventListener("change", () => {
    if (Number.isFinite(audio.duration)) {
      audio.currentTime = Number(el.seek.value);
      state.currentTime = audio.currentTime;
    }
  });

  el.volume.addEventListener("input", () => {
    state.volume = Number(el.volume.value) / 100;
    audio.volume = state.volume;
    renderMiniplayer();
  });
  el.volume.addEventListener("change", persist);
  el.volumeBtn.addEventListener("click", () => {
    state.volume = state.volume > 0 ? 0 : 0.85;
    audio.volume = state.volume;
    el.volume.value = String(state.volume * 100);
    persist();
    renderMiniplayer();
  });

  el.fsPrev.addEventListener("click", () => step(-1));
  el.fsNext.addEventListener("click", () => step(1));
  el.fsPlay.addEventListener("click", togglePlay);

  const openFullscreen = (open) => {
    state.fullscreen = open;
    render();
    if (open) el.fsClose.focus();
    else el.playBtn.focus();
  };
  const openFromKey = (event) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      openFullscreen(true);
    }
  };
  el.mpArtwork.addEventListener("click", () => openFullscreen(true));
  el.mpArtwork.addEventListener("keydown", openFromKey);
  el.mpTitle.addEventListener("click", () => openFullscreen(true));
  el.mpTitle.addEventListener("keydown", openFromKey);
  el.fsClose.addEventListener("click", () => openFullscreen(false));

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && state.fullscreen) {
      openFullscreen(false);
      return;
    }
    const tag = event.target?.tagName;
    // Interactive elements keep their native activation; the shortcuts below
    // would otherwise double-fire (e.g. Space on a focused button).
    if (tag === "INPUT" || tag === "TEXTAREA" || tag === "BUTTON" || event.target?.isContentEditable) {
      if (event.key === "Escape" && (tag === "INPUT" || tag === "TEXTAREA")) event.target.blur();
      return;
    }
    if (event.key === " ") { event.preventDefault(); togglePlay(); }
    if (event.key === "ArrowRight" && event.shiftKey) step(1);
    if (event.key === "ArrowLeft" && event.shiftKey) step(-1);
    if (event.key.toLowerCase() === "f") openFullscreen(!state.fullscreen);
  });
}

/* -------------------------------------------------------------------- boot - */

function boot() {
  for (const id of els) el[id] = document.getElementById(id);
  const missing = els.filter((id) => !el[id]);
  if (missing.length > 0) {
    // Fail loudly during development rather than throwing on first render.
    console.error("[dhun] missing element ids:", missing.join(", "));
  }
  injectIcons();
  restore();

  audio.volume = state.volume;
  el.bridgeInput.value = state.bridgeUrl;
  el.volume.value = String(state.volume * 100);
  el.searchInput.placeholder = "Search songs, artists, albums";

  bindAudioEvents();
  bindUiEvents();

  if (state.bridgeUrl) {
    setBridgeStatus("checking");
    checkBridge(false).then((ok) => {
      if (!ok) toast(`Saved bridge unreachable (${state.bridgeError})`, "error", 6000);
      render();
    });
  } else {
    setBridgeStatus("disconnected");
  }

  if (state.demoMode) state.results = demoCatalogue();
  render();
}

boot();

/* Exposed for web/tests/client.test.mjs (the jsdom harness). Nothing in the
   app imports this module twice, so exporting costs nothing at runtime. */
export {
  state,
  audio,
  formatTime,
  artworkFor,
  demoCatalogue,
  runSearch,
  playIndex,
  addToQueue,
  removeFromQueue,
  clearQueue,
  checkBridge,
  render,
  toast,
};
