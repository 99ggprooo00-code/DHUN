#!/usr/bin/env node
/**
 * Development server.
 *
 * `node tools/serve.mjs` and nothing else: no dependency, no build step, no
 * watcher. It exists to make the app behave the same locally as it does when
 * deployed under a project sub-path, which is the failure mode that has bitten
 * this repository before:
 *
 *   - it serves the **built** tree (`dist/`) when one exists, so what is tested
 *     is what shipped, and `src/` otherwise so an edit shows up on reload;
 *   - it sends the same Content-Security-Policy as the deployed page, so a
 *     header-only relaxation cannot hide a violation during development;
 *   - it serves the service worker from the module root with the right MIME
 *     type and `Service-Worker-Allowed`, so the offline shell can be exercised
 *     locally;
 *   - unknown paths that are not files fall back to `index.html` (the app routes
 *     on the hash, so every route is the same document), while anything under
 *     `assets/` returns a real 404 instead of an HTML page masquerading as an
 *     image.
 *
 * Binds 0.0.0.0 by default because the sandbox preview reaches it through a
 * proxy on a public host.
 *
 * Usage: node tools/serve.mjs [port] [--src]
 */

import { createServer } from "node:http";
import { createReadStream, promises as fs } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const moduleRoot = path.resolve(here, "..");
const args = process.argv.slice(2);
const port = Number(args.find((arg) => /^\d+$/.test(arg)) ?? process.env.PORT ?? 4173);
const forceSrc = args.includes("--src");

async function pickRoot() {
  const dist = path.join(moduleRoot, "dist");
  if (!forceSrc) {
    try {
      const stat = await fs.stat(path.join(dist, "index.html"));
      if (stat.isFile()) return dist;
    } catch {
      /* no build yet: fall through to src/ */
    }
  }
  return path.join(moduleRoot, "src");
}

const root = await pickRoot();

const CSP = [
  "default-src 'none'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: https:",
  "media-src 'self' https: blob:",
  "connect-src 'self' https:",
  "worker-src 'self'",
  "manifest-src 'self'",
  "base-uri 'none'",
  "form-action 'none'",
].join("; ");

const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
  ".txt": "text/plain; charset=utf-8",
  ".md": "text/markdown; charset=utf-8",
};

function headers(extra = {}) {
  return {
    "Content-Security-Policy": CSP,
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    ...extra,
  };
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? "/", `http://${request.headers.host ?? "localhost"}`);
  const requested = decodeURIComponent(url.pathname);
  const relative = requested.replace(/^\/+/, "");
  const resolved = path.resolve(root, relative === "" ? "index.html" : relative);

  // Path traversal guard: a resolved path must stay inside the served root.
  if (resolved !== root && !resolved.startsWith(root + path.sep)) {
    response.writeHead(403, headers()).end("forbidden");
    return;
  }

  try {
    const stat = await fs.stat(resolved);
    const file = stat.isDirectory() ? path.join(resolved, "index.html") : resolved;
    const type = TYPES[path.extname(file)] ?? "application/octet-stream";
    const isWorker = path.basename(file) === "sw.js";
    response.writeHead(
      200,
      headers({
        "Content-Type": type,
        "Content-Length": (await fs.stat(file)).size,
        ...(isWorker ? { "Service-Worker-Allowed": "/" } : {}),
      }),
    );
    createReadStream(file).pipe(response);
  } catch {
    // An asset that is not there is a 404 — never the app shell. Serving HTML
    // for a missing image is how a broken <img> becomes an invisible bug.
    if (relative.startsWith("assets/") || path.extname(relative) !== "") {
      response.writeHead(404, headers({ "Content-Type": "text/plain; charset=utf-8" })).end("not found");
      return;
    }
    // A route: the app routes on the hash, so every route is the same document.
    try {
      const shell = await fs.readFile(path.join(root, "index.html"));
      response.writeHead(200, headers({ "Content-Type": TYPES[".html"], "Content-Security-Policy": CSP })).end(shell);
    } catch {
      response.writeHead(404, headers()).end("not found");
    }
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`dhun web: serving ${root} on http://0.0.0.0:${port}/`);
});
