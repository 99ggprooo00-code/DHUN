/**
 * Installable, offline-capable, and honest about updates.
 *
 * The three things a web app owes a visitor that a website does not:
 *
 *   1. **It opens offline.** A service worker caches the shell, so a reload on a
 *      train shows the app and its library instead of a dinosaur. Audio cannot
 *      be cached (streams are signed and cross-origin, and caching them would
 *      be a download feature the app deliberately does not have) — the app says
 *      exactly that instead of failing silently.
 *   2. **It installs.** `beforeinstallprompt` is captured and surfaced as a
 *      button, not fired automatically: an install prompt that appears unbidden
 *      is the reason people distrust install prompts.
 *   3. **It updates without losing your music.** A new service worker waits
 *      and takes over when every DHUN tab is closed. The page shows a toast
 *      asking the visitor to close and reopen; nothing reloads the page, because
 *      a reload would stop playback.
 *
 * When any of this is unavailable the module reports which part failed in
 * `state`; `main.js` turns that into one sentence on screen rather than a
 * silent no-op.
 */

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
  let registration = null;
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
    state = { ...state, ...patch };
    onState(state);
  }

  async function register() {
    if (!navigator?.serviceWorker?.register) {
      emit({ sw: SW_STATE.UNSUPPORTED });
      return null;
    }
    try {
      registration = await navigator.serviceWorker.register(swUrl, { scope });
      if (registration.waiting) {
        emit({ sw: SW_STATE.UPDATE_READY, updateReady: true });
      } else {
        emit({ sw: SW_STATE.READY, offlineReady: true });
      }
      registration.addEventListener?.("updatefound", () => {
        const installing = registration.installing;
        if (!installing) return;
        installing.addEventListener("statechange", () => {
          // `installed` with a controller already present means an update, not a
          // first install: show the notice instead of swapping under the user.
          if (installing.state === "installed" && navigator.serviceWorker.controller) {
            emit({ sw: SW_STATE.UPDATE_READY, updateReady: true });
          }
        });
      });
      return registration;
    } catch (error) {
      emit({ sw: SW_STATE.FAILED, error: error?.message ?? "the service worker could not be registered" });
      return null;
    }
  }

  function wireInstallPrompt() {
    win.addEventListener?.("beforeinstallprompt", (event) => {
      // Chrome would show its own mini-infobar; the app shows a button instead.
      event.preventDefault?.();
      deferredPrompt = event;
      emit({ installable: true });
    });
    win.addEventListener?.("appinstalled", () => {
      deferredPrompt = null;
      emit({ installable: false, installed: true });
    });
  }

  function wireConnectivity() {
    win.addEventListener?.("online", () => emit({ online: true }));
    win.addEventListener?.("offline", () => emit({ online: false }));
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
      wireInstallPrompt();
      wireConnectivity();
      register();
    },
    /** The visitor chose to install. Returns whether a prompt was shown. */
    async promptInstall() {
      if (!deferredPrompt) return false;
      try {
        deferredPrompt.prompt();
        const choice = await deferredPrompt.userChoice;
        deferredPrompt = null;
        emit({ installable: false });
        return choice?.outcome === "accepted";
      } catch {
        return false;
      }
    },
    /** Hand the app's own version to the worker so its cache name can move. */
    announceVersion(version) {
      try {
        navigator?.serviceWorker?.controller?.postMessage?.({ type: "version", version });
      } catch {
        /* not fatal: the worker then uses its build-time cache name */
      }
    },
  };
}
