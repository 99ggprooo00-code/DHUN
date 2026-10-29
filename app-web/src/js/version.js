/**
 * What build this is.
 *
 * A web app has no installer, no app store and no "About" dialog the OS draws,
 * so the version has to live in the app itself — visible on the About screen,
 * sent to the service worker so its cache name moves with it, and printed in the
 * diagnostics panel so a bug report can name a build.
 */
export const WEB_VERSION = "3.0.10";

/** A human label for the same thing, including what the build targets. */
export const BUILD_LABEL = "web (static, no backend)";

/** The service worker cache namespace. Bumping the version retires the old
 *  cache instead of leaving stale files behind on a device nobody can reach. */
export function cacheName(version = WEB_VERSION) {
  return `dhun-web-v${version}`;
}
