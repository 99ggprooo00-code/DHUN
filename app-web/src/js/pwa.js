/**
 * Installable, offline-capable, and honest about updates.
 *
 * The three things a web app owes a visitor that a website does not:
 *
 *   1. **It can open offline.** A service worker caches the shell on a best-effort
 *      basis; missing cache entries can still prevent offline use. Audio cannot
 *      be cached (streams are signed and cross-origin, and caching them would
 *      be a download feature the app deliberately does not have).
 *   2. **It installs.** `beforeinstallprompt` is captured and surfaced as a
 *      button, not fired automatically: an install prompt that appears unbidden
 *      is the reason people distrust install prompts.
 *   3. **It updates without losing your music.** A new service worker waits
 *      and takes over when every DHUN tab is closed. The page shows a toast
 *      asking the visitor to close and reopen; nothing reloads the page, because
 *      a reload would stop playback.
 *
 * This module exposes lifecycle and install state; `main.js` currently uses
 * the update-ready flag to show a notice. It does not display offline readiness.
 */

import { listen } from "./dom.js";

export const SW_STATE = Object.freeze({
  UNSUPPORTED: "unsupported",
  REGISTERING: "registering",
  READY: "ready",
  UPDATE_READY: "update-ready",
  FAILED: "failed",
});

export function createPwa({
  navigator = globalThis.navigator,
  win = globalThis,
  onState = () => {},
  swUrl = "./sw.js",
  scope = "./",
} = {}) {
  let destroyed = false;
  let started = false;
  const subscriptions = new Set();
  let registration = null;
  const observedWorkers = new Map();
  let deferredPrompt = null;
  let state = {
    sw: navigator?.serviceWorker ? SW_STATE.REGISTERING : SW_STATE.UNSUPPORTED,
    installable: false,
    installed: Boolean(win.matchMedia?.("(display-mode: standalone)")?.matches),
    online: navigator?.onLine ?? true,
    updateReady: false,
    offlineReady: false,
    error: null,
  };

  function emit(patch) {
    if (destroyed) return;
    state = { ...state, ...patch };
    onState(state);
  }

  function watch(target, type, handler) {
    if (destroyed) return () => {};
    const release = listen(target, type, (event) => { if (!destroyed) handler(event); });
    const stop = () => { release(); subscriptions.delete(stop); };
    subscriptions.add(stop);
    return stop;
  }

  // READY means an activated worker, not a verified offline shell. The worker
  // intentionally tolerates failed cache writes, and there is no cache-completeness
  // protocol. Therefore offlineReady remains false (not verified), even on READY.
  // Observe all concurrent workers: an active worker can finish activation while
  // an update is installing or waiting.
  function observeWorkers() {
    const current = new Set([registration.installing, registration.waiting, registration.active].filter(Boolean));
    for (const [worker, stop] of observedWorkers) {
      if (!current.has(worker)) { stop(); observedWorkers.delete(worker); }
    }
    for (const worker of current) {
      if (observedWorkers.has(worker)) continue;
      const stop = watch(worker, "statechange", () => {
        if (!observedWorkers.has(worker)) return;
        // The browser may clear installing before dispatching its redundant event.
        if (worker.state === "redundant" && !registration.installing && !registration.waiting && !registration.active) {
          emit({ sw: SW_STATE.FAILED, updateReady: false, offlineReady: false, error: "the service worker installation failed" });
          return;
        }
        refreshWorkerState();
      });
      observedWorkers.set(worker, stop);
    }
  }

  function refreshWorkerState() {
    if (!registration) return;
    const worker = registration.installing || registration.waiting || registration.active;
    observeWorkers();
    const active = registration.active?.state === "activated";
    const waiting = registration.waiting && registration.waiting.state !== "redundant";
    if (waiting && active) {
      emit({ sw: SW_STATE.UPDATE_READY, updateReady: true, offlineReady: false, error: null });
    } else if (active || worker?.state === "activated") {
      emit({ sw: SW_STATE.READY, updateReady: false, offlineReady: false, error: null });
    } else if (worker?.state === "redundant") {
      emit({ sw: SW_STATE.FAILED, updateReady: false, offlineReady: false, error: "the service worker installation failed" });
    } else {
      emit({ sw: SW_STATE.REGISTERING, updateReady: false, offlineReady: false, error: null });
    }
  }

  async function register() {
    if (destroyed) return null;
    if (!navigator?.serviceWorker?.register) {
      emit({ sw: SW_STATE.UNSUPPORTED });
      return null;
    }
    try {
      const result = await navigator.serviceWorker.register(swUrl, { scope });
      if (destroyed) return null;
      registration = result;
      watch(registration, "updatefound", refreshWorkerState);
      refreshWorkerState();
      return registration;
    } catch (error) {
      emit({ sw: SW_STATE.FAILED, updateReady: false, offlineReady: false, error: error?.message ?? "the service worker could not be registered" });
      return null;
    }
  }

  function wireInstallPrompt() {
    watch(win, "beforeinstallprompt", (event) => {
      // Chrome would show its own mini-infobar; the app shows a button instead.
      event.preventDefault?.();
      deferredPrompt = event;
      emit({ installable: true });
    });
    watch(win, "appinstalled", () => {
      deferredPrompt = null;
      emit({ installable: false, installed: true });
    });
  }

  function wireConnectivity() {
    watch(win, "online", () => emit({ online: true }));
    watch(win, "offline", () => emit({ online: false }));
  }

  return {
    get state() {
      return state;
    },
    get registration() {
      return registration;
    },
    /** Wire the install prompt and connectivity, then register the worker. */
    start() {
      if (started || destroyed) return;
      started = true;
      wireInstallPrompt();
      wireConnectivity();
      register();
    },
    /** The visitor chose to install. Returns whether a prompt was shown. */
    async promptInstall() {
      if (destroyed || !deferredPrompt) return false;
      const prompt = deferredPrompt;
      try {
        // Keep prompt() synchronous for user activation, but observe both
        // native promises before yielding: either may reject on cancellation.
        const [, choice] = await Promise.all([
          new Promise((resolve) => resolve(prompt.prompt())),
          prompt.userChoice,
        ]);
        if (destroyed) return false;
        deferredPrompt = null;
        emit({ installable: false });
        return choice?.outcome === "accepted";
      } catch {
        return false;
      }
    },
    /** Dispose this page's observers, not the persistent worker or its caches. */
    destroy() {
      if (destroyed) return;
      destroyed = true;
      for (const stop of [...subscriptions]) stop();
      deferredPrompt = null;
      registration = null;
      observedWorkers.clear();
    },
    /** Hand the app's own version to the worker so its cache name can move. */
    announceVersion(version) {
      if (destroyed) return;
      try {
        navigator?.serviceWorker?.controller?.postMessage?.({ type: "version", version });
      } catch {
        /* not fatal: the worker then uses its build-time cache name */
      }
    },
  };
}
