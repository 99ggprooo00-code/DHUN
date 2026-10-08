/**
 * A DOM stub small enough to read in one sitting and strict enough to be
 * useful: it implements only what `src/js/main.js` touches, and it records
 * rather than silently swallowing what it cannot do.
 *
 * It exists because `npm test` must run with no dependencies and no browser,
 * while the most expensive class of bug in a hand-wired client (a handler
 * bound to a node that is no longer there, a null dereference on first paint)
 * is exactly what a boot test catches. Layout, paint and real events it
 * cannot judge, and these tests do not pretend otherwise.
 */

function makeElement(tagName = "div") {
  const element = {
    tagName: tagName.toUpperCase(),
    dataset: {},
    children: [],
    attributes: {},
    className: "",
    style: {},
    _html: "",
    set innerHTML(value) {
      this._html = String(value);
    },
    /** Aggregates appended children: the full player is appended as a sibling
     *  of the shell, so its markup has to be visible in the mount's HTML. */
    get innerHTML() {
      return this._html + this.children.map((child) => (child && child.innerHTML) || "").join("");
    },
    /** The stub keeps no parsed tree, so `firstElementChild` is the whole blob
     *  that was just assigned — which is what `appendChild(overlay.firstElementChild)`
     *  in main.js actually moves. */
    get firstElementChild() {
      const child = makeElement("div");
      child._html = this._html;
      return child;
    },
    set outerHTML(value) {
      this._html = String(value);
    },
    get outerHTML() {
      return this._html;
    },
    appendChild(child) {
      this.children.push(child);
      return child;
    },
    remove() {
      this.removed = true;
    },
    addEventListener() {},
    removeEventListener() {},
    setAttribute(name, value) {
      this.attributes[name] = value;
    },
    getAttribute(name) {
      return name in this.attributes ? this.attributes[name] : null;
    },
    removeAttribute(name) {
      delete this.attributes[name];
    },
    focus() {},
    querySelector: () => null,
    querySelectorAll: () => [],
    closest: () => null,
    contains: () => false,
  };
  return element;
}

/**
 * Boots a fresh copy of the module graph against the stub.
 *
 * @param t  the node:test TestContext, when called from a test — used to
 *           register teardown. May be omitted outside a test.
 */
export async function boot(t = null, options = {}) {
  const mount = makeElement("div");
  const documentElement = makeElement("html");
  const body = makeElement("body");
  const storage = new Map();
  const globalAny = globalThis;

  globalAny.document = {
    documentElement,
    body,
    createElement: makeElement,
    addEventListener() {},
    querySelector: (selector) => (selector === "#app" ? mount : null),
    querySelectorAll: () => [],
    getElementById: () => null,
  };
  globalAny.window = globalAny;
  globalAny.innerWidth = options.innerWidth ?? 1280;
  globalAny.location = { hash: options.hash ?? "" };
  globalAny.addEventListener = () => {};
  globalAny.localStorage = {
    getItem: (key) => (storage.has(key) ? storage.get(key) : null),
    setItem: (key, value) => storage.set(key, value),
    removeItem: (key) => storage.delete(key),
  };
  globalAny.requestAnimationFrame = (callback) => setTimeout(callback, 0);

  // The live catalogue probe would otherwise attempt a real request and keep
  // the process (and the test run) waiting on a network this sandbox has not.
  globalAny.fetch = async () => {
    throw new Error("network is disabled in tests");
  };

  // The player's timing clock is a real setInterval. This records the callback
  // instead of scheduling it: a test that leaves a 250 ms timer running would
  // hang the runner, and the clock's cadence is not what these tests assert
  // (its arithmetic is asserted in tests/player.test.mjs).
  const intervals = new Map();
  let nextIntervalId = 1;
  globalAny.setInterval = (callback) => {
    const id = nextIntervalId++;
    intervals.set(id, callback);
    return id;
  };
  globalAny.clearInterval = (id) => {
    intervals.delete(id);
  };

  // A fresh copy of the module graph per boot() call: state is module-level.
  const { boot: start } = await import(`../../src/js/main.js?boot=${Math.random()}`);
  const app = start({ root: mount, storage: false, audio: null, AudioContextCtor: null });

  if (t && typeof t.after === "function") {
    t.after(() => app.player.dispose());
  }

  return {
    mount,
    body,
    app,
    store: app.store,
    setHash(hash) {
      globalAny.location.hash = hash;
      return app;
    },
    applyHashRoute() {
      return app.applyHashRoute();
    },
    /** Runs every recorded interval callback once — a manual clock tick. */
    tick() {
      for (const callback of intervals.values()) callback();
    },
  };
}
