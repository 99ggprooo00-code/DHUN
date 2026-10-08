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
 * Since the stylesheet is inlined per route (eleventy.config.js), the CSS pass
 * also runs over each page's `<style>` block — that is where the site's only
 * CSS lives now. The HTML passes would otherwise merely collapse whitespace in
 * it, leaving comments and `: ` padding in the shipped bytes.
 *
 * Correctness is asserted, not assumed: `tools/verify-minify.mjs` rebuilds the
 * site unminified and compares the result ignoring whitespace and comments, and
 * `scripts/website_quality.py` runs the same checks over the committed build.
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

// Placeholder for a `<style>` body while the HTML passes run, so they cannot
// touch CSS that the CSS pass is about to handle properly.
const STYLE_SLOT = /@@DHUN_STYLE_(\d+)@@/g;

function minifyHtml(file) {
  const before = readFileSync(file, "utf8");
  const styles = [];
  let after = before.replace(
    /(<style\b[^>]*>)([\s\S]*?)(<\/style>)/gi,
    (_match, open, css, close) => {
      styles.push(minifyCssText(css));
      return `${open}@@DHUN_STYLE_${styles.length - 1}@@${close}`;
    },
  );

  after = after
    .replace(/>\s+</g, "><")
    .replace(/[ \t]+/g, " ")
    .replace(/ ?\r?\n ?/g, "\n")
    .replace(/^[^\S\n]+/gm, "")
    .replace(STYLE_SLOT, (_match, index) => styles[Number(index)]);

  write(file, before, after);
}

function minifyCss(file) {
  const before = readFileSync(file, "utf8");
  write(file, before, minifyCssText(before));
}

function minifyCssText(css) {
  return css
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/\s+/g, " ")
    .replace(/\s*([{};,])\s*/g, "$1")
    .replace(/:\s+/g, ":")
    .replace(/;}/g, "}")
    .replace(/\s*!\s*important/g, "!important")
    .trim();
}

function write(file, before, after) {
  writeFileSync(file, after);
  saved += before.length - after.length;
}

walk(DIST);
console.log(`minified: saved ${saved} bytes`);
