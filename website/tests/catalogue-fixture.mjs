/**
 * Controlled upstream outage for browser UI measurements, never shipped code.
 *
 * A layout/keyboard/contrast run must not call community services or treat
 * their CORS/availability as a JavaScript regression. Let the real API failover
 * and labelled sample fallback execute against an explicit network failure.
 * A separate HTTP 503 case proves disclosure and real HTTP-error semantics.
 * This is not a successful live catalogue, media stream or CORS acceptance test.
 * Local shell/assets, other paths/origins and runtime security are untouched.
 */
import { API_SOURCES } from "../../app-web/src/js/api.js";

export async function installCatalogueOutage(context, { kind = "network" } = {}) {
  if (kind !== "network" && kind !== "http") throw new Error(`Unknown catalogue fixture: ${kind}`);
  const origins = new Set(API_SOURCES.map(({ origin }) => origin));
  await context.route(
    (url) => origins.has(url.origin) && url.pathname.startsWith("/api/"),
    (route) => kind === "network" ? route.abort("failed") : route.fulfill({
      status: 503,
      headers: {
        "access-control-allow-origin": "*",
        "cache-control": "no-store",
        "content-type": "application/json",
      },
      body: JSON.stringify({ success: false, message: "Controlled browser-test catalogue outage; not a live response." }),
    }),
  );
}

export async function newAppContext(browser, options, outage) {
  const context = await browser.newContext(options);
  try {
    await installCatalogueOutage(context, outage);
    return context;
  } catch (error) {
    await context.close();
    throw error;
  }
}
