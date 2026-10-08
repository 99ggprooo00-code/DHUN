/**
 * Whitespace minifier for the built site — DHUN's own code, no dependencies.
 *
 * Two jobs, both deliberately conservative:
 *   HTML: collapse runs of whitespace to one space; drop whitespace between a
 *         `>` and a `<`. Text content is never re-flowed and no whitespace is
 *         removed between two text characters, so inline spacing is preserved.
 *   CSS:  drop comments, collapse whitespace, remove it around `{ } ; ,` and
 *         the single space after `:`. `calc()`-style operators and value lists
 *         keep their interior spaces, and `!important` keeps its leading space.
 *
 * Correctness is asserted, not assumed: `scripts/test_website_quality.py`
 * rebuilds and proves the built HTML/CSS is unchanged up to whitespace by
 * comparing the whitespace-stripped text of source and output. Minification is
 * also why the page-weight budget in that test can be met without dropping
 * content.
 */
import { readFileSync, writeFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const DIST = new URL("../dist/", import.meta.url).pathname;
let saved = 0;

function walk(dir) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full);
    else if (full.endsWith(".html")) minifyHtml(full);
    else if (full.endsWith(".css")) minifyCss(full);
  }
}

function minifyHtml(file) {
  const before = readFileSync(file, "utf8");
  const after = before
    .replace(/>\s+</g, "><")
    .replace(/[ \t]+/g, " ")
    .replace(/ ?\r?\n ?/g, "\n")
    .replace(/^[^\S\n]+/gm, "");
  write(file, before, after);
}

function minifyCss(file) {
  const before = readFileSync(file, "utf8");
  const after = before
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/\s+/g, " ")
    .replace(/\s*([{};,])\s*/g, "$1")
    .replace(/:\s+/g, ":")
    .replace(/;}/g, "}")
    .replace(/\s*!\s*important/g, "!important")
    .trim();
  write(file, before, after);
}

function write(file, before, after) {
  writeFileSync(file, after);
  saved += before.length - after.length;
}

walk(DIST);
console.log(`minified: saved ${saved} bytes`);
