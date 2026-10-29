/**
 * Exercise the actual boot/router/handlers in Node, not a browser DOM.
 * Controlled document, storage, audio and fetch doubles never access providers,
 * user data or sound hardware. They do not prove layout, focus or media output.
 */
import { boot } from "../../src/js/main.js";
import { createStorage } from "../../src/js/library.js";
import { API_SOURCES } from "../../src/js/api.js";

export const settle = () => new Promise((resolve) => setImmediate(resolve));

class Events {
  listeners = new Map();
  addEventListener(name, callback) {
    if (!this.listeners.has(name)) this.listeners.set(name, new Set());
    this.listeners.get(name).add(callback);
  }
  removeEventListener(name, callback) { this.listeners.get(name)?.delete(callback); }
  emit(name, event = {}) { for (const fn of this.listeners.get(name) ?? []) fn(event); }
}

class NodeElement extends Events {
  constructor(tagName, doc, id = "") {
    super();
    this.tagName = tagName.toUpperCase(); this.ownerDocument = doc; this.id = id;
    this.dataset = {}; this.innerHTML = ""; this.value = ""; this.scrollTop = 0;
    this.attributes = new Map(); this.firstElementChild = null;
    this.classList = { contains: () => false, toggle() {}, add() {}, remove() {} };
    this.style = { setProperty() {}, removeProperty() {} };
  }
  setAttribute(name, value) { this.attributes.set(name, String(value)); }
  getAttribute(name) { return this.attributes.get(name) ?? null; }
  removeAttribute(name) { this.attributes.delete(name); }
  contains(node) { return Boolean(node && node.ownerDocument === this.ownerDocument); }
  matches() { return ["BUTTON", "INPUT", "SELECT", "TEXTAREA", "A"].includes(this.tagName) || this.attributes.has("tabindex"); }
  closest(selector) {
    if (selector.startsWith("form") && this.tagName !== "FORM") return null;
    return this.dataset.action ? this : null;
  }
  querySelector() { return null; }
  querySelectorAll() { return []; }
  focus() { this.ownerDocument.activeElement = this; }
  setSelectionRange(start, end) { this.selectionStart = start; this.selectionEnd = end; }
  getClientRects() { return []; }
  getBoundingClientRect() { return { top: 0, left: 0, width: 0, height: 0 }; }
}

function documentDouble(replaceSearchFields = false) {
  const doc = new Events();
  const nodes = new Map();
  const node = (id, tag = "DIV") => {
    if (!nodes.has(id)) nodes.set(id, new NodeElement(tag, doc, id));
    return nodes.get(id);
  };
  doc.body = node("body", "BODY"); doc.documentElement = node("html", "HTML");
  doc.activeElement = doc.body; doc.title = "";
  doc.getElementById = (id) => replaceSearchFields && ["search-input", "search-results"].includes(id) ? nodes.get(id) ?? null : node(id);
  doc.querySelector = (selector) => {
    if (selector === "#dhun-view h1" || selector === "#dhun-topbar h1") return node("heading", "H1");
    if (replaceSearchFields && selector === "#search-input") return nodes.get("search-input") ?? null;
    if (/^#[\w-]+$/.test(selector)) return node(selector.slice(1), selector === "#search-input" ? "INPUT" : "DIV");
    return null;
  };
  doc.querySelectorAll = () => [];
  doc.createElement = (tag) => new NodeElement(tag, doc);
  doc.contains = (element) => Boolean(element?.ownerDocument === doc);
  if (replaceSearchFields) {
    // Model the known innerHTML replacement boundary for this field only.
    // This is not an HTML parser or evidence about a phone's software keyboard.
    let markup = "";
    Object.defineProperty(node("dhun-view"), "innerHTML", { configurable: true,
      get: () => markup,
      set(value) {
        markup = String(value);
        const old = nodes.get("search-input");
        if (old && doc.activeElement === old) doc.activeElement = doc.body;
        nodes.delete("search-input");
        nodes.delete("search-results");
        if (markup.includes('id="search-results"')) node("search-results").innerHTML = markup;
        const match = /<input\s+id="search-input"[^>]*\bvalue="([^"]*)"/.exec(markup);
        if (match) {
          const field = new NodeElement("INPUT", doc, "search-input");
          const entities = { "&amp;": "&", "&quot;": '"', "&#39;": "'", "&lt;": "<", "&gt;": ">" };
          field.value = match[1].replace(/&(?:amp|quot|#39|lt|gt);/g, (entity) => entities[entity]);
          field.selectionStart = field.selectionEnd = 0;
          nodes.set("search-input", field);
        }
      },
    });
  }
  const audio = node("dhun-audio", "AUDIO");
  Object.assign(audio, { src: "", currentTime: 0, duration: 0, volume: 1, muted: false, paused: true, error: null,
    load() {}, pause() { this.paused = true; }, play() { this.paused = false; return Promise.resolve(); },
    removeAttribute(name) { if (name === "src") this.src = ""; },
  });
  return { doc, nodes, root: node("app"), audio };
}

function payloadFor(url) {
  const u = new URL(url), path = u.pathname;
  const id = u.searchParams.get("query") ?? u.searchParams.get("id") ?? path.split("/").at(-1);
  const song = { id, name: `Fixture ${id}`, duration: 20, image: [], downloadUrl: [], artists: { primary: [] } };
  if (path.includes("/search/")) return { results: [song] };
  if (path.endsWith("/songs")) return { results: [song] };
  if (path.endsWith("/albums") && path.includes("/artists/")) return [];
  if (path.startsWith("/api/artists/")) return { id, name: `Artist ${id}`, image: [] };
  if (path.startsWith("/api/songs/")) return [song];
  if (path === "/api/albums" || path === "/api/playlists") return { id, name: `Entity ${id}`, songs: [song], image: [] };
  return [];
}

function fetchDouble({ respectAbort = true, fail = () => false } = {}) {
  const calls = [];
  let closed = false;
  function fetchImpl(url, options) {
    if (!API_SOURCES.some((source) => url.startsWith(source.origin + "/api/"))) throw new Error("Unexpected fixture request: " + url);
    const signal = options.signal;
    const call = { url, signal, settled: false };
    calls.push(call);
    return new Promise((resolve, reject) => {
      const finish = (fn, value) => {
        if (call.settled) return;
        call.settled = true;
        signal.removeEventListener("abort", onAbort);
        fn(value);
      };
      const onAbort = () => {
        if (respectAbort) finish(reject, Object.assign(new Error("controlled abort"), { name: "AbortError" }));
      };
      call.succeed = (data = payloadFor(url)) => finish(resolve, { ok: true, status: 200, json: async () => ({ success: true, data }) });
      call.http = (status) => finish(resolve, { ok: false, status, json: async () => ({}) });
      call.networkError = () => finish(reject, new TypeError("controlled catalogue refusal"));
      signal.addEventListener("abort", onAbort, { once: true });
      if (closed) call.succeed();
      else if (signal.aborted) onAbort();
      else if (fail(url)) call.networkError();
    });
  }
  return { calls, fetchImpl, close() { closed = true; for (const call of calls) call.succeed(); } };
}

export function bootFixture(t, { hash = "#/search", replaceSearchFields = false, storageSeed = [], ...fetchOptions } = {}) {
  const dom = documentDouble(replaceSearchFields);
  const transport = fetchDouble(fetchOptions);
  const win = new Events(); win.self = win; win.top = win;
  win.location = { hash };
  win.history = { length: 1, state: null, replaceState(state, _title, url) { this.state = state; win.location.hash = String(url); }, back() {} };
  const mediaQueries = new Map();
  win.matchMedia = (query) => {
    if (!mediaQueries.has(query)) {
      const media = new Events(); media.matches = false; mediaQueries.set(query, media);
    }
    return mediaQueries.get(query);
  };
  const timers = new Map(); let timerId = 0;
  win.setTimeout = (fn) => { timers.set(++timerId, fn); return timerId; };
  win.clearTimeout = (id) => timers.delete(id);
  const saved = new Map(storageSeed);
  const storage = { getItem: (key) => saved.get(key) ?? null, setItem: (key, value) => saved.set(key, String(value)), removeItem: (key) => saved.delete(key) };
  const mounts = [];
  function mount() {
    const oldFetch = globalThis.fetch;
    const oldStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
    try {
      globalThis.fetch = transport.fetchImpl;
      Object.defineProperty(globalThis, "localStorage", { configurable: true, value: storage });
      const mounted = boot({ win, doc: dom.doc, storage: createStorage(storage) }); mounts.push(mounted); return mounted;
    } catch (error) {
      transport.close(); throw error;
    } finally {
      globalThis.fetch = oldFetch;
      if (oldStorage) Object.defineProperty(globalThis, "localStorage", oldStorage);
      else delete globalThis.localStorage;
    }
  }
  const app = mount();
  t.after(async () => {
    for (const mounted of mounts) {
      if (mounted.destroy) mounted.destroy(); else mounted.player.destroy();
    }
    transport.close(); timers.clear();
    await settle(); await settle(); transport.close();
  });
  function dispatch(name, target) {
    const old = Object.getOwnPropertyDescriptor(globalThis, "Element");
    try {
      Object.defineProperty(globalThis, "Element", { configurable: true, value: NodeElement });
      dom.root.emit(name, { target, preventDefault() {} });
    } finally {
      if (old) Object.defineProperty(globalThis, "Element", old); else delete globalThis.Element;
    }
  }
  return {
    ...app, win, doc: dom.doc, root: dom.root, calls: transport.calls, audio: dom.audio, backing: storage, saved,
    timers, mediaQueries, mount,
    navigate(next, { historyState = null } = {}) { win.location.hash = next; win.history.state = historyState; win.emit("hashchange"); },
    submit(query) {
      const form = new NodeElement("FORM", dom.doc); form.dataset.action = "search-submit";
      const input = new NodeElement("INPUT", dom.doc); input.value = query;
      form.querySelector = () => input; dispatch("submit", form);
    },
    createPlaylist(name, songId = "") {
      const form = new NodeElement("FORM", dom.doc); form.dataset = { action: "create-playlist", value: songId };
      const input = new NodeElement("INPUT", dom.doc); input.value = name;
      form.querySelector = () => input; dispatch("submit", form);
    },
    click(action, value, extra = {}) {
      const target = new NodeElement("BUTTON", dom.doc); target.dataset = { action, value, ...extra };
      dispatch("click", target);
    },
    importFile(file) {
      const input = new NodeElement("INPUT", dom.doc);
      input.dataset.action = "import-library"; input.files = [file]; input.value = "fixture.json";
      dispatch("change", input); return input;
    },
    composeStart(target = dom.doc.querySelector("#search-input")) {
      dom.root.emit("compositionstart", { target, data: "" });
    },
    composeEnd(target = dom.doc.querySelector("#search-input"), data = "") {
      dom.root.emit("compositionend", { target, data });
    },
    key(key, extra = {}) {
      let prevented = false;
      dom.doc.emit("keydown", { key, target: dom.doc.activeElement, preventDefault() { prevented = true; }, ...extra });
      return prevented;
    },
    close: transport.close,
  };
}
