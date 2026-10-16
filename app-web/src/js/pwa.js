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
 *      until the visitor says so; "Reload to update" appears as a bar, and the
 *      click that accepts it is a click the visitor chose.
 *
 * When any of this is unavailable the module says which part and why — see
 * `platform.js#unavailableCapabilities` for the sentences the UI prints.
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
  let waitingWorker = null;
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
        waitingWorker = registration.waiting;
        emit({ sw: SW_STATE.UPDATE_READY, updateReady: true });
      } else {
        emit({ sw: SW_STATE.READY, offlineReady: true });
      }
      registration.addEventListener?.("updatefound", () => {
        const installing = registration.installing;
        if (!installing) return;
        installing.addEventListener("statechange", () => {
          // `installed` with a controller already present means an update, not a
          // first install: offer the reload instead of swapping under the user.
          if (installing.state === "installed" && navigator.serviceWorker.controller) {
            waitingWorker = installing;
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

  /** Apply a waiting service worker: the page reloads once, on purpose. */
  function applyUpdate() {
    if (!waitingWorker) return false;
    try {
      waitingWorker.postMessage({ type: "skip-waiting" });
      waitingWorker = null;
      emit({ updateReady: false, sw: SW_STATE.READY });
      win.location?.reload?.();
      return true;
    } catch (error) {
      emit({ error: error?.message ?? "the update could not be applied" });
      return false;
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
    applyUpdate,
    /** Force the worker to check for a new version — the Settings row. */
    checkForUpdate() {
      try {
        registration?.update?.();
        return true;
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
