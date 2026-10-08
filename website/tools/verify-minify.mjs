/**
 * Prove that `tools/minify.mjs` only removed whitespace and comments.
 *
 * The page-weight budget (`.ai/WEBSITE_PLAN.md` Part A §10) is met partly by
 * minifying the built HTML/CSS. A minifier that quietly dropped a paragraph
 * would make the budget pass and the site wrong, so this script rebuilds the
 * site unminified into a temporary directory and compares every built file
 * with the committed one *ignoring whitespace*: any other difference is a
 * failure. It also asserts that minification actually reduced the size, so the
 * comparison cannot pass by doing nothing.
 *
 * Runs in `.github/workflows/website.yml` (needs Node; never runs in app CI,
 * which stays Python-only and browser/Node-free by design).
 */
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, readdirSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative } from "node:path";

const WEBSITE = new URL("../", import.meta.url).pathname;
const DIST = join(WEBSITE, "dist");
const TMP = mkdtempSync(join(tmpdir(), "dhun-minify-"));

function walk(dir, base = dir, out = new Map()) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, base, out);
    else out.set(relative(base, full), readFileSync(full, "utf8"));
  }
  return out;
}

// `normalize` folds every difference the minifier is allowed to make.
function normalize(text, kind) {
  let out = text.replace(/<!--[\s\S]*?-->/g, "");
  if (kind === "css") out = out.replace(/\/\*[\s\S]*?\*\//g, "");
  return out.replace(/\s+/g, "").replace(/;}/g, "}");
}

try {
  execFileSync("node", [join(WEBSITE, "node_modules/@11ty/eleventy/cmd.cjs"), `--output=${TMP}`], {
    cwd: WEBSITE,
    stdio: "inherit",
  });

  const built = walk(DIST);
  const fresh = walk(TMP);
  const problems = [];

  for (const [name, text] of fresh) {
    if (!built.has(name)) {
      // assets are copied by Eleventy; everything fresh must reach dist
      problems.push(`${name}: present in a fresh build but missing from dist/`);
      continue;
    }
    if (!/\.(html|css)$/.test(name)) continue;
    const kind = name.endsWith(".css") ? "css" : "html";
    if (normalize(text, kind) !== normalize(built.get(name), kind)) {
      problems.push(`${name}: minified output differs from the fresh build beyond whitespace`);
    }
  }

  let savedBytes = 0;
  for (const [name, text] of built) {
    if (!fresh.has(name)) continue;
    const before = Buffer.byteLength(fresh.get(name));
    const after = Buffer.byteLength(text);
    savedBytes += before - after;
    if (after > before) problems.push(`${name}: minified file is larger than the source build`);
  }

  if (savedBytes <= 0) {
    problems.push("minification saved no bytes — the budget would be met by luck, not by work");
  }

  if (problems.length) {
    console.error(`FAIL: ${problems.length} minification problem(s):`);
    for (const problem of problems) console.error(`  - ${problem}`);
    process.exitCode = 1;
  } else {
    console.log(
      `OK: every built file matches a fresh unminified build ignoring whitespace ` +
        `(${savedBytes} bytes saved by minification).`,
    );
  }
} finally {
  rmSync(TMP, { recursive: true, force: true });
}
