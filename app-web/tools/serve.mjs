#!/usr/bin/env node
/**
 * Zero-dependency static file server for development.
 *
 * Node's http module is enough: the app is static files, and a dev server
 * should not be the reason a build has 200 transitive packages. Binds
 * 0.0.0.0 so the sandbox preview (a proxied public host) can reach it, and
 * sets the same Content-Security-Policy as the deployed page so a header-only
 * relaxation cannot hide a violation during development.
 *
 * Usage: node tools/serve.mjs [port]
 */

import { createServer } from "node:http";
import { createReadStream, promises as fs } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..", "src");
const port = Number(process.argv[2] ?? process.env.PORT ?? 4173);

const CSP = [
  "default-src 'none'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data:",
  "media-src 'self' https:",
  "connect-src 'self' https://music.youtube.com https://lrclib.net",
  "base-uri 'none'",
  "form-action 'none'",
].join("; ");

const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".webmanifest": "application/manifest+json",
};

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, `http://${request.headers.host ?? "localhost"}`);
    const requested = decodeURIComponent(url.pathname);
    const relative = requested === "/" ? "index.html" : requested.replace(/^\/+/, "");
    const resolved = path.resolve(root, relative);

    // Path traversal guard: a resolved path must stay inside src/.
    if (resolved !== root && !resolved.startsWith(root + path.sep)) {
      response.writeHead(403).end("forbidden");
      return;
    }

    const stat = await fs.stat(resolved);
    const file = stat.isDirectory() ? path.join(resolved, "index.html") : resolved;
    const type = TYPES[path.extname(file)] ?? "application/octet-stream";

    response.writeHead(200, {
      "Content-Type": type,
      "Content-Length": (await fs.stat(file)).size,
      "Content-Security-Policy": CSP,
      "Cache-Control": "no-store",
    });
    createReadStream(file).pipe(response);
  } catch {
    // Single-page app: an unknown path renders the shell, and main.js reads
    // window.location.hash for deep links.
    try {
      const shell = await fs.readFile(path.join(root, "index.html"));
      response.writeHead(200, {
        "Content-Type": TYPES[".html"],
        "Content-Security-Policy": CSP,
        "Cache-Control": "no-store",
      });
      response.end(shell);
    } catch {
      response.writeHead(404).end("not found");
    }
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`dhun web: serving ${root} on http://0.0.0.0:${port}/`);
});
