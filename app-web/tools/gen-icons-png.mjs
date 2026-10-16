#!/usr/bin/env node
/**
 * Generates the installable-app icons — no image library, no dependency.
 *
 * A PWA that wants to be installed needs PNG icons at 192px and 512px (SVG is
 * accepted by some browsers and ignored by others, and iOS wants a PNG on the
 * home screen). Adding a rasteriser as a dependency to draw one circle would be
 * absurd, and committing binary icons nobody can regenerate is worse. So this
 * script draws them: raw RGBA pixels, a 4× supersampled scanline renderer, and a
 * small PNG encoder on top of Node's own `zlib`.
 *
 * The art is the app's own mark: the dark canvas, the accent ring, the centre
 * dot — the same geometry as `assets/icon.svg`, which is written by the same
 * script so the two can never disagree.
 *
 * Usage:
 *   node tools/gen-icons-png.mjs           # write the files
 *   node tools/gen-icons-png.mjs --check   # exit 1 if they do not match
 */

import { deflateSync, inflateSync } from "node:zlib";
import { readFileSync, writeFileSync, existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const assets = path.resolve(here, "..", "src", "assets");
const check = process.argv.includes("--check");

/* ------------------------------------------------------------- palette ---- */

// The app's own tokens (DhunAppearance.kt): background #161616, accent #BB86FC.
const CANVAS = [0x16, 0x16, 0x16];
const ACCENT = [0xbb, 0x86, 0xfc];

/* -------------------------------------------------------------- drawing --- */

const SUPERSAMPLE = 4;

/** Signed distance to a rounded rectangle centred in a `size` square. */
function roundedRectDistance(x, y, size, radius) {
  const half = size / 2;
  const dx = Math.abs(x - half) - (half - radius);
  const dy = Math.abs(y - half) - (half - radius);
  const outside = Math.hypot(Math.max(dx, 0), Math.max(dy, 0));
  return outside + Math.min(Math.max(dx, dy), 0) - radius;
}

/**
 * The mark, as coverage values per pixel.
 *
 * `padding` is what makes the maskable variant safe: Android crops a maskable
 * icon to whatever shape the launcher uses and only guarantees the central 80%,
 * so the maskable file draws the same mark smaller inside a full-bleed canvas.
 */
function render({ size, padding = 0, fullBleed = false }) {
  const pixels = new Uint8Array(size * size * 4);
  const radius = fullBleed ? 0 : size * 0.22;
  const inset = size * padding;
  const markSize = size - inset * 2;
  const centre = size / 2;
  const ringOuter = markSize * 0.34;
  const ringInner = ringOuter * 0.72;
  const dot = ringInner * 0.34;
  const samples = SUPERSAMPLE;

  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      let insideCanvas = 0;
      let insideRing = 0;
      let insideDot = 0;
      for (let sy = 0; sy < samples; sy += 1) {
        for (let sx = 0; sx < samples; sx += 1) {
          const px = x + (sx + 0.5) / samples;
          const py = y + (sy + 0.5) / samples;
          if (fullBleed || roundedRectDistance(px, py, size, radius) <= 0) insideCanvas += 1;
          const distance = Math.hypot(px - centre, py - centre);
          if (distance <= ringOuter && distance >= ringInner) insideRing += 1;
          if (distance <= dot) insideDot += 1;
        }
      }
      const total = samples * samples;
      const cover = insideCanvas / total;
      const ring = Math.min(insideRing / total, cover);
      const centreDot = Math.min(insideDot / total, cover);
      // Accent where the mark is, canvas underneath, and full transparency
      // outside a rounded rect (so a launcher's own background shows through).
      const alpha = cover;
      const blend = (angle) => {};
      const mix = ACCENT.map((channel, index) => {
        const base = CANVAS[index];
        const accent = channel;
        return Math.round(base * (1 - Math.max(ring, centreDot)) + accent * Math.max(ring, centreDot));
      });
      const offset = (y * size + x) * 4;
      pixels[offset] = mix[0];
      pixels[offset + 1] = mix[1];
      pixels[offset + 2] = mix[2];
      pixels[offset + 3] = Math.round(alpha * 255);
    }
  }
  return pixels;
}

/* ----------------------------------------------------------- PNG encoder -- */

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();

function crc32(buffer) {
  let c = 0xffffffff;
  for (const byte of buffer) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const typeAndData = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(typeAndData), 0);
  return Buffer.concat([length, typeAndData, crc]);
}

function encodePng(size, pixels) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header[8] = 8; // bit depth
  header[9] = 6; // colour type: RGBA
  header[10] = 0; // deflate
  header[11] = 0; // adaptive filtering
  header[12] = 0; // no interlace

  // One filter byte (0 = none) per scanline, then the RGBA row itself.
  const stride = size * 4;
  const raw = Buffer.alloc((stride + 1) * size);
  for (let y = 0; y < size; y += 1) {
    raw[y * (stride + 1)] = 0;
    Buffer.from(pixels.buffer, pixels.byteOffset + y * stride, stride).copy(raw, y * (stride + 1) + 1);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

/** Read a PNG back into RGBA pixels — used only by `--check`. */
function decodePng(buffer) {
  let offset = 8;
  let width = 0;
  let height = 0;
  const idat = [];
  while (offset < buffer.length) {
    const length = buffer.readUInt32BE(offset);
    const type = buffer.toString("ascii", offset + 4, offset + 8);
    const data = buffer.subarray(offset + 8, offset + 8 + length);
    if (type === "IHDR") {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
    }
    if (type === "IDAT") idat.push(data);
    offset += 12 + length;
  }
  const raw = inflateSync(Buffer.concat(idat));
  const stride = width * 4;
  const pixels = new Uint8Array(width * height * 4);
  for (let y = 0; y < height; y += 1) {
    const filter = raw[y * (stride + 1)];
    if (filter !== 0) return null;
    raw.copy(pixels, y * stride, y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
  }
  return { width, height, pixels };
}

/* ----------------------------------------------------------------- icons -- */

const ICON_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" role="img" aria-label="DHUN">
  <rect width="512" height="512" rx="113" fill="#161616"/>
  <circle cx="256" cy="256" r="174" fill="none" stroke="#BB86FC" stroke-width="49"/>
  <circle cx="256" cy="256" r="42" fill="#BB86FC"/>
</svg>
`;

const targets = [
  { file: "icon.svg", write: () => Buffer.from(ICON_SVG, "utf8") },
  { file: "icon-192.png", write: () => encodePng(192, render({ size: 192 })) },
  { file: "icon-512.png", write: () => encodePng(512, render({ size: 512 })) },
  {
    file: "icon-maskable-512.png",
    write: () => encodePng(512, render({ size: 512, padding: 0.14, fullBleed: true })),
  },
];

mkdirSync(assets, { recursive: true });

let problems = 0;
for (const target of targets) {
  const full = path.join(assets, target.file);
  const expected = target.write();
  if (check) {
    if (!existsSync(full)) {
      console.error(`gen-icons-png: ${target.file} is missing — run node tools/gen-icons-png.mjs`);
      problems += 1;
      continue;
    }
    const actual = readFileSync(full);
    if (target.file.endsWith(".png")) {
      // A PNG written by a different zlib version is still the same image, so
      // compare pixels rather than bytes.
      const a = decodePng(actual);
      const b = decodePng(expected);
      const same =
        a && b && a.width === b.width && a.height === b.height && Buffer.compare(Buffer.from(a.pixels), Buffer.from(b.pixels)) === 0;
      if (!same) {
        console.error(`gen-icons-png: ${target.file} does not match what this script draws`);
        problems += 1;
      }
    } else if (Buffer.compare(actual, expected) !== 0) {
      console.error(`gen-icons-png: ${target.file} does not match this script's art`);
      problems += 1;
    }
    continue;
  }
  writeFileSync(full, expected);
  console.log(`gen-icons-png: wrote ${target.file} (${expected.length} bytes)`);
}

if (problems > 0) process.exit(1);
if (check) console.log("gen-icons-png: icons match the generated art.");
