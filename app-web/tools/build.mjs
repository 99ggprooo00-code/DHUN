#!/usr/bin/env node
/**
 * Build. There is no bundler: the app is plain ES modules and two stylesheets,
 * and the output is a byte-for-byte copy of the source directory.
 *
 * A bundler would earn its place the day the app needs one. Until then the
 * build's only jobs are to copy, to prove the icon file is still generated
 * from the Kotlin source, and to print the shipped weight so growth is
 * deliberate rather than discovered.
 *
 * Usage: node tools/build.mjs
 */

import { cp, rm, stat, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";

const here = path.dirname(fileURLToPath(import.meta.url));
const moduleRoot = path.resolve(here, "..");
const srcRoot = path.join(moduleRoot, "src");
const outRoot = path.join(moduleRoot, "dist");

await rm(outRoot, { recursive: true, force: true });
await cp(srcRoot, outRoot, { recursive: true });

// Generated-from-source check: icons.js must match DhunIcons.kt.
execFileSync(process.execPath, [path.join(here, "gen-icons.mjs"), "--check"], {
  stdio: "inherit",
  cwd: moduleRoot,
});

let files = 0;
let bytes = 0;
async function walk(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) await walk(full);
    else {
      files += 1;
      bytes += (await stat(full)).size;
    }
  }
}
await walk(outRoot);

console.log(`build: ${files} file(s), ${bytes} bytes → ${path.relative(process.cwd(), outRoot)}`);
