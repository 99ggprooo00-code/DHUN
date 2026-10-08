#!/usr/bin/env node
/**
 * Generates `src/js/icons.js` from the application's own icon source,
 * `shared/src/commonMain/kotlin/dev/dhun/design/DhunIcons.kt`.
 *
 * The web app mirrors DHUN; it does not redesign it. Hand-copying 31 vectors
 * is how a mirror drifts, so this reads the vectors straight out of the
 * Compose enum and emits them unchanged. `tests/icons.test.mjs` fails when the
 * generated file no longer matches the Kotlin source, so drift is a red test
 * rather than a code review argument.
 *
 * Usage: node tools/gen-icons.mjs [--check]
 *   --check  exit 1 if src/js/icons.js is not what would be generated.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..", "..");
const sourcePath = path.join(
  root,
  "shared/src/commonMain/kotlin/dev/dhun/design/DhunIcons.kt",
);
const outPath = path.join(here, "..", "src", "js", "icons.js");

const ENTRY = /^\s{4}([A-Z][A-Za-z0-9_]*)\("([^"]+)"\),\s*$/gm;

const source = readFileSync(sourcePath, "utf8");
const icons = [...source.matchAll(ENTRY)].map(([, name, d]) => ({ name, d }));

if (icons.length === 0) {
  console.error(`gen-icons: no icon entries found in ${sourcePath}`);
  process.exit(1);
}

const body = icons
  .map(({ name, d }) => `  ${name}: "${d.replace(/"/g, '\\"')}",`)
  .join("\n");

const out = `/**
 * GENERATED FILE — do not edit by hand.
 *
 * Source of truth: shared/src/commonMain/kotlin/dev/dhun/design/DhunIcons.kt
 * Regenerate with: npm run gen:icons   (app-web/tools/gen-icons.mjs)
 *
 * 24dp Material vectors, byte-identical to the Compose enum. Kept as path
 * data (not an SVG file per icon) so the web app ships no icon request and no
 * icon library — the same "no third-party runtime asset" rule the app obeys.
 */
export const VIEWBOX = 24;

export const ICON_PATHS = Object.freeze({
${body}
});

/** An SVG string for [name], sized by CSS. Unknown names throw — a missing
 *  icon is a build bug, never a blank space. */
export function iconSvg(name, { size = 24, className = "" } = {}) {
  const d = ICON_PATHS[name];
  if (!d) throw new Error(\`unknown DhunIcon: \${name}\`);
  const cls = className ? \` class="\${className}"\` : "";
  return (
    \`<svg\${cls} viewBox="0 0 \${VIEWBOX} \${VIEWBOX}" width="\${size}" height="\${size}" \` +
    \`fill="currentColor" aria-hidden="true" focusable="false"><path d="\${d}"/></svg>\`
  );
}
`;

const check = process.argv.includes("--check");
if (check) {
  const current = readFileSync(outPath, "utf8");
  if (current !== out) {
    console.error("gen-icons: src/js/icons.js is out of date — run `npm run gen:icons`");
    process.exit(1);
  }
  console.log(`gen-icons: up to date (${icons.length} icons)`);
} else {
  writeFileSync(outPath, out);
  console.log(`gen-icons: wrote ${icons.length} icons to ${path.relative(root, outPath)}`);
}
