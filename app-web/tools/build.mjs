#!/usr/bin/env node
/**
 * Build. There is no bundler: the app is plain ES modules, two stylesheets, a
 * manifest and a service worker, and the build output is a byte-for-byte copy of
 * `src/`.
 *
 * A bundler would earn its place the day the app needs one. Until then the
 * build's jobs are the ones that catch real mistakes, and each of them is a
 * failure rather than a warning:
 *
 *   1. copy `src/` → `dist/`;
 *   2. prove `js/icons.js` is still what `DhunIcons.kt` generates (drift here
 *      means the web app quietly stopped mirroring the app);
 *   3. prove the install icons are still what `gen-icons-png.mjs` draws;
 *   4. prove every module the service worker caches actually exists — the
 *      offline shell is a hand-written list, and a renamed module that the list
 *      misses produces an app that works online and breaks on a train;
 *   5. prove every module in `src/js` is reachable from `main.js` (a module
 *      nobody imports is either dead code or a forgotten wire-up);
 *   6. print the shipped weight so growth is deliberate rather than discovered.
 *
 * Usage: node tools/build.mjs
 */

import { cp, rm, stat, readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";

const here = path.dirname(fileURLToPath(import.meta.url));
const moduleRoot = path.resolve(here, "..");
const srcRoot = path.join(moduleRoot, "src");
const outRoot = path.join(moduleRoot, "dist");

await rm(outRoot, { recursive: true, force: true });
await cp(srcRoot, outRoot, { recursive: true });

function fail(message) {
  console.error(`build: ${message}`);
  process.exit(1);
}

/* ------------------------------------------------ generated files in sync -- */

for (const script of ["gen-icons.mjs", "gen-icons-png.mjs"]) {
  try {
    execFileSync(process.execPath, [path.join(here, script), "--check"], { stdio: "inherit", cwd: moduleRoot });
  } catch {
    fail(`${script} --check failed`);
  }
}

/* ------------------------------------------------------- reachable modules -- */

const jsRoot = path.join(srcRoot, "js");
const modules = [];
async function collect(dir, prefix = "") {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const relative = path.join(prefix, entry.name);
    if (entry.isDirectory()) await collect(path.join(dir, entry.name), relative);
    else if (entry.name.endsWith(".js")) modules.push(relative.split(path.sep).join("/"));
  }
}
await collect(jsRoot);

/**
 * Follow the import graph from the entry point.
 *
 * A module nobody imports is either dead code or a forgotten wire-up, and both
 * are how a web app ends up shipping a screen that never renders. This walks
 * `import ... from "./x.js"` transitively rather than guessing from a filename,
 * because "main.js mentions the word" is not the same as "main.js loads it".
 */
async function reachableModules() {
  const seen = new Set();
  const queue = ["main.js"];
  while (queue.length > 0) {
    const module = queue.shift();
    if (seen.has(module)) continue;
    seen.add(module);
    let source;
    try {
      source = await readFile(path.join(jsRoot, module), "utf8");
    } catch {
      continue;
    }
    for (const match of source.matchAll(/from\s+"(\.\/[^"]+)"|import\s+"(\.\/[^"]+)"/g)) {
      const specifier = match[1] ?? match[2];
      const resolved = path.normalize(path.join(path.dirname(module), specifier)).split(path.sep).join("/");
      queue.push(resolved);
    }
  }
  return seen;
}

const reachable = await reachableModules();
const unreachable = modules.filter((module) => !reachable.has(module));
if (unreachable.length > 0) {
  fail(`these modules are not reachable from main.js (dead code, or a missing wire-up): ${unreachable.join(", ")}`);
}

/* ------------------------------------------------------------ offline list -- */

const swSource = await readFile(path.join(srcRoot, "sw.js"), "utf8");
const shellBlock = swSource.match(/const SHELL = \[([\s\S]*?)\];/);
if (!shellBlock) fail("sw.js has no SHELL list; the offline shell is not declared anywhere");
const shellPaths = [...shellBlock[1].matchAll(/"\.\/([^"]*)"/g)].map((match) => match[1]);
const missingFromShell = [];
for (const entry of shellPaths) {
  const target = entry === "" ? "index.html" : entry;
  try {
    await stat(path.join(srcRoot, target));
  } catch {
    missingFromShell.push(entry);
  }
}
if (missingFromShell.length > 0) {
  fail(`sw.js caches files that do not exist: ${missingFromShell.join(", ")}`);
}
const cached = new Set(shellPaths.map((entry) => entry.replace(/^\.\//, "")));
const uncached = modules.map((module) => `js/${module}`).filter((module) => !cached.has(module));
if (uncached.length > 0) {
  fail(`these modules exist but the service worker does not cache them, so the offline app would break: ${uncached.join(", ")}`);
}

/* ---------------------------------------------------------------- weight -- */

let files = 0;
let bytes = 0;
const byType = new Map();
async function walk(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) await walk(full);
    else {
      const size = (await stat(full)).size;
      files += 1;
      bytes += size;
      const extension = path.extname(entry.name) || "(none)";
      byType.set(extension, (byType.get(extension) ?? 0) + size);
    }
  }
}
await walk(outRoot);

const breakdown = [...byType.entries()]
  .sort((a, b) => b[1] - a[1])
  .map(([extension, size]) => `${extension} ${(size / 1024).toFixed(1)} kB`)
  .join(" · ");

console.log(`build: ${files} file(s), ${bytes} bytes (${(bytes / 1024).toFixed(1)} kB) → ${path.relative(process.cwd(), outRoot)}`);
console.log(`build: ${breakdown}`);
console.log(`build: ${reachable.size} reachable modules, ${shellPaths.length} shell entries, all reachable and all cached.`);
