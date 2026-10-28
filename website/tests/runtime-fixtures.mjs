/** Test-only synthetic metadata and PCM. Never shipped or provider acceptance. */
import { API_SOURCES } from "../../app-web/src/js/api.js";

export const TONE_ORIGIN = "https://aac.saavncdn.com";
export const TONE_PATH = "/dhun-controlled-runtime-tone.wav";

export function toneWav(seconds = 30) {
  const rate = 8000;
  const frames = Math.floor(seconds * rate);
  const buffer = Buffer.alloc(44 + frames * 2);
  buffer.write("RIFF", 0); buffer.writeUInt32LE(buffer.length - 8, 4); buffer.write("WAVEfmt ", 8);
  buffer.writeUInt32LE(16, 16); buffer.writeUInt16LE(1, 20); buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(rate, 24); buffer.writeUInt32LE(rate * 2, 28);
  buffer.writeUInt16LE(2, 32); buffer.writeUInt16LE(16, 34); buffer.write("data", 36);
  buffer.writeUInt32LE(frames * 2, 40);
  for (let i = 0; i < frames; i++) buffer.writeInt16LE(Math.round(1200 * Math.sin(i * 2 * Math.PI * 220 / rate)), 44 + i * 2);
  return buffer;
}

export function audioResponse(bytes, range = "") {
  const headers = { "content-type": "audio/wav", "access-control-allow-origin": "*", "accept-ranges": "bytes", "cache-control": "no-store" };
  if (!range) return { status: 200, headers, body: bytes };
  const match = /^bytes=(\d+)-(\d*)$/.exec(range);
  const start = match ? Number(match[1]) : -1;
  const end = match?.[2] ? Math.min(Number(match[2]), bytes.length - 1) : bytes.length - 1;
  if (start < 0 || start >= bytes.length || end < start) {
    return { status: 416, headers: { ...headers, "content-range": `bytes */${bytes.length}` }, body: "" };
  }
  return { status: 206, headers: { ...headers, "content-range": `bytes ${start}-${end}/${bytes.length}` }, body: bytes.subarray(start, end + 1) };
}

export async function installSyntheticPlayback(context) {
  const origins = new Set(API_SOURCES.map(source => source.origin));
  const songs = ["a", "b", "c"].map(id => ({
    id: `controlled-${id}`, name: `Controlled tone ${id.toUpperCase()}`, type: "song", duration: 30,
    album: { id: "controlled-album", name: "Synthetic test fixture" },
    artists: { primary: [{ id: "controlled-artist", name: "Synthetic fixture, not provider audio" }] },
    image: [], downloadUrl: [{ quality: "320kbps", url: `${TONE_ORIGIN}${TONE_PATH}?track=${id}` }],
  }));
  await context.route(url => origins.has(url.origin) && url.pathname.startsWith("/api/"), route => {
    const path = new URL(route.request().url()).pathname;
    let data = { results: [], total: 0 };
    if (path === "/api/search/songs") data = { results: songs, total: songs.length };
    else if (path.startsWith("/api/songs/")) data = songs.filter(song => song.id === decodeURIComponent(path.slice("/api/songs/".length)));
    return route.fulfill({ status: 200, contentType: "application/json", headers: { "access-control-allow-origin": "*" }, body: JSON.stringify({ success: true, data }) });
  });
  const bytes = toneWav();
  await context.route(url => url.origin === TONE_ORIGIN && url.pathname === TONE_PATH,
    route => route.fulfill(audioResponse(bytes, route.request().headers().range)));
}
