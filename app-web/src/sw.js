/**
 * The service worker: the app opens without a network.
 *
 * What it caches and — more importantly — what it does not:
 *
 *   **Cached:** the shell. `index.html`, both stylesheets, every module, the
 *   manifest and the icons. These are the app's own bytes, they are small, and
 *   without them a reload on a train shows a browser error page instead of the
 *   visitor's own library.
 *
 *   **Not cached:** audio, and every API response. Stream URLs are signed and
 *   expire within hours, so a cached stream is a file that plays until it
 *   silently does not; and DHUN deliberately has no download feature, so
 *   pretending a cache is one would be a lie with extra steps. Metadata requests
 *   are excluded for a different reason: a stale home feed presented as current
 *   is worse than an honest "you are offline".
 *
 * Strategy:
 *   - shell requests: cache-first, with a background revalidate on navigation;
 *   - everything else: the network, untouched.
 *
 * Updates never swap under the visitor. A new worker installs and waits; the
 * page asks the visitor to close and reopen DHUN, and the worker takes over when
 * no DHUN tab is open. The `skip-waiting` message can still force it; the app
 * does not send it, because a reload would stop playback.
 * The cache name carries the app version, and `version` messages move it, so an
 * old cache is deleted rather than accumulating.
 */

const DEFAULT_CACHE = "dhun-web-v3.0.11";

/** Everything the interface needs to open offline. Kept as an explicit list so
 *  a forgotten module is a missing-file error in development rather than a
 *  half-working offline app nobody can reproduce. `tools/build.mjs` checks this
 *  list against the real build output. */
const SHELL = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./css/tokens.css",
  "./css/app.css",
  "./js/main.js",
  "./js/dom.js",
  "./js/format.js",
  "./js/icons.js",
  "./js/api.js",
  "./js/library.js",
  "./js/indexeddb-storage.js",
  "./js/app-storage.js",
  "./js/backup-policy.js",
  "./js/player.js",
  "./js/router.js",
  "./js/components.js",
  "./js/views.js",
  "./js/shell.js",
  "./js/player-ui.js",
  "./js/legal.js",
  "./js/markdown.js",
  "./js/shortcuts.js",
  "./js/transfer.js",
  "./js/demo.js",
  "./js/version.js",
  "./js/pwa.js",
  "./js/data/web-legal.js",
  "./assets/icon.svg",
  "./assets/icon-192.png",
  "./assets/icon-512.png",
  "./assets/icon-maskable-512.png",
];

let cacheName = DEFAULT_CACHE;

self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(cacheName);
      // Individually, so one 404 does not abort the whole install: a missing
      // icon must not cost the visitor offline support.
      await Promise.all(
        SHELL.map((path) =>
          cache.add(new Request(path, { cache: "reload" })).catch(() => undefined),
        ),
      );
    })(),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      const names = await caches.keys();
      await Promise.all(
        names.filter((name) => name.startsWith("dhun-web-") && name !== cacheName).map((name) => caches.delete(name)),
      );
      // Serve the new shell on the reload that follows an update without a
      // second prompt.
      await self.clients.claim();
    })(),
  );
});

self.addEventListener("message", (event) => {
  const message = event.data;
  if (!message) return;
  if (message.type === "skip-waiting") {
    self.skipWaiting();
  }
  if (message.type === "version" && typeof message.version === "string") {
    cacheName = `dhun-web-v${message.version}`;
  }
});

/** Only the app's own files are shell: same-origin, GET, and inside the scope. */
function isShellRequest(request, url) {
  if (request.method !== "GET") return false;
  if (url.origin !== self.location.origin) return false;
  if (!url.pathname.startsWith(new URL(self.registration.scope).pathname)) return false;
  if (url.pathname.includes("/api/")) return false;
  return true;
}

self.addEventListener("fetch", (event) => {
  const request = event.request;
  let url;
  try {
    url = new URL(request.url);
  } catch {
    return;
  }
  // Everything not shell — audio, artwork, metadata, a visitor's endpoint — goes
  // straight to the network. No cache, no interception, no surprise.
  if (!isShellRequest(request, url)) return;

  if (request.mode === "navigate") {
    // Navigations: try the network for a fresh shell, fall back to the cached
    // one when there is no network at all.
    event.respondWith(
      (async () => {
        try {
          const response = await fetch(request);
          if (response.ok) {
            // A transient 404/503 must not poison the known-good offline shell.
            // Cache failure must not discard an otherwise usable online page.
            try {
              const cache = await caches.open(cacheName);
              await cache.put("./index.html", response.clone());
            } catch { /* offline availability is best-effort */ }
          }
          return response;
        } catch {
          const cache = await caches.open(cacheName);
          return (await cache.match("./index.html")) ?? (await cache.match("./")) ?? Response.error();
        }
      })(),
    );
    return;
  }

  event.respondWith(
    (async () => {
      const cache = await caches.open(cacheName).catch(() => null);
      const cached = await cache?.match(request).catch(() => undefined);
      if (cached) return cached;
      try {
        const response = await fetch(request);
        if (response.ok && cache) await cache.put(request, response.clone()).catch(() => undefined);
        return response;
      } catch (error) {
        throw error;
      }
    })(),
  );
});
