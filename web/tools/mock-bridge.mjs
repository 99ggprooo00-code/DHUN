#!/usr/bin/env node
/* ===========================================================================
   DHUN Web — mock bridge (development only).

   Speaks exactly the same HTTP contract as the real bridge
   (tools/web-bridge) but serves the locally generated demo audio instead of
   YouTube, so the web client can be developed and tested with no network,
   no extraction and no JDK.

   Run:  node web/tools/mock-bridge.mjs --port 8787
   Then paste http://localhost:8787 into the client's Bridge field.

   NOT for production: it has no extraction, no caching and no auth. The
   production bridge is the Kotlin one under tools/web-bridge/.
   =========================================================================== */

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const demoDir = path.join(here, "..", "assets", "demo");

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, arg, index, all) => {
    if (arg.startsWith("--")) acc.push([arg.slice(2), all[index + 1]]);
    return acc;
  }, []),
);

const PORT = Number(args.port ?? 8787);
const HOST = args.host ?? "0.0.0.0";
const ORIGIN = args.origin ?? "*";

/** Mirrors dev.dhun.core.Track (shared/src/commonMain/.../core/Entities.kt). */
const CATALOGUE = [
  { id: "demo-1", title: "Signal Drift", artistName: "DHUN Demo Ensemble", albumName: "Synthesised Demo Audio", durationSeconds: 24 },
  { id: "demo-2", title: "Low Orbit", artistName: "DHUN Demo Ensemble", albumName: "Synthesised Demo Audio", durationSeconds: 24 },
  { id: "demo-3", title: "Nightbus", artistName: "DHUN Demo Ensemble", albumName: "Synthesised Demo Audio", durationSeconds: 24 },
].map((track) => ({ ...track, artistId: null, albumId: null, thumbnailUrl: null, explicit: false }));

function cors(res) {
  res.setHeader("Access-Control-Allow-Origin", ORIGIN);
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Range");
  res.setHeader("Access-Control-Expose-Headers", "Content-Length, Content-Range, Accept-Ranges");
  res.setHeader("Vary", "Origin");
}

function json(res, status, body) {
  cors(res);
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(body));
}

/**
 * Stream a file with HTTP Range support. The real bridge needs this too:
 * <audio> seeking issues ranged requests, and without 206 handling the
 * browser cannot scrub.
 */
function sendFile(res, req, filePath, contentType) {
  let stat;
  try {
    stat = fs.statSync(filePath);
  } catch {
    json(res, 404, { error: "not found" });
    return;
  }
  const size = stat.size;
  const range = req.headers.range;
  cors(res);
  res.setHeader("Accept-Ranges", "bytes");
  res.setHeader("Content-Type", contentType);
  res.setHeader("Cache-Control", "no-store");

  if (range) {
    const match = /bytes=(\d*)-(\d*)/.exec(range);
    const start = match?.[1] ? Number(match[1]) : 0;
    const end = match?.[2] ? Number(match[2]) : size - 1;
    if (start >= size || end >= size || start > end) {
      res.writeHead(416, { "Content-Range": `bytes */${size}` });
      res.end();
      return;
    }
    res.writeHead(206, {
      "Content-Range": `bytes ${start}-${end}/${size}`,
      "Content-Length": String(end - start + 1),
    });
    if (req.method === "HEAD") { res.end(); return; }
    fs.createReadStream(filePath, { start, end }).pipe(res);
    return;
  }

  res.writeHead(200, { "Content-Length": String(size) });
  if (req.method === "HEAD") { res.end(); return; }
  fs.createReadStream(filePath).pipe(res);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host ?? "localhost"}`);
  const route = url.pathname.replace(/\/+$/, "") || "/";

  if (req.method === "OPTIONS") {
    cors(res);
    res.writeHead(204);
    res.end();
    return;
  }

  if (route === "/health") {
    json(res, 200, { ok: true, engine: "mock", version: "0.1.0", demo: true });
    return;
  }

  if (route === "/search") {
    const query = (url.searchParams.get("q") ?? "").trim().toLowerCase();
    const songs = query
      ? CATALOGUE.filter((t) =>
        `${t.title} ${t.artistName} ${t.albumName}`.toLowerCase().includes(query))
      : CATALOGUE;
    json(res, 200, { query, songs, videos: [], artists: [], albums: [], playlists: [], continuationToken: null });
    return;
  }

  if (route === "/suggestions") {
    const query = (url.searchParams.get("q") ?? "").trim().toLowerCase();
    json(res, 200, CATALOGUE.map((t) => t.title).filter((t) => !query || t.toLowerCase().includes(query)));
    return;
  }

  const streamMatch = /^\/stream\/([^/]+)$/.exec(route);
  if (streamMatch) {
    const id = decodeURIComponent(streamMatch[1]);
    const track = CATALOGUE.find((t) => t.id === id);
    if (!track) { json(res, 404, { error: `unknown videoId ${id}` }); return; }
    // Mirrors dev.dhun.core.StreamInfo. audioUrl points back at this mock's
    // own byte proxy, standing in for googlevideo.
    json(res, 200, {
      videoId: id,
      audioUrl: `http://${req.headers.host}/audio/${id}`,
      mimeType: "audio/wav",
      bitrateKbps: 256,
      codec: "pcm_s16le",
      contentLengthBytes: fs.existsSync(path.join(demoDir, `${id}.wav`))
        ? fs.statSync(path.join(demoDir, `${id}.wav`)).size
        : null,
      userAgent: "DHUNMockBridge/0.1",
    });
    return;
  }

  const audioMatch = /^\/audio\/([^/]+)$/.exec(route);
  if (audioMatch) {
    const id = decodeURIComponent(audioMatch[1]);
    sendFile(res, req, path.join(demoDir, `${id}.wav`), "audio/wav");
    return;
  }

  json(res, 404, { error: `no route ${route}` });
});

server.listen(PORT, HOST, () => {
  console.log(`DHUN mock bridge on http://${HOST}:${PORT}`);
  console.log(`demo audio dir: ${demoDir}`);
  if (!fs.existsSync(path.join(demoDir, "demo-1.wav"))) {
    console.warn("WARNING: demo audio missing — run web/tools/make-demo-audio.py first");
  }
});
