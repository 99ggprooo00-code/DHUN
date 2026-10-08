import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "..", "..");
const tokensCss = readFileSync(path.join(here, "..", "src", "css", "tokens.css"), "utf8");
const appearanceKt = readFileSync(
  path.join(repoRoot, "shared/src/commonMain/kotlin/dev/dhun/design/DhunAppearance.kt"),
  "utf8",
).toUpperCase();

/** Every colour literal in tokens.css, as the 6- or 8-digit hex Compose uses. */
function colourLiterals(css) {
  const found = [];
  for (const match of css.matchAll(/#([0-9a-fA-F]{6})\b/g)) {
    found.push(match[1].toUpperCase());
  }
  for (const match of css.matchAll(/rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)/g)) {
    const [, r, g, b, a] = match;
    const hex = (n) => n.toString(16).padStart(2, "0").toUpperCase();
    const alpha = Math.round(Number(a) * 255)
      .toString(16)
      .padStart(2, "0")
      .toUpperCase();
    found.push(`${alpha}${hex(Number(r))}${hex(Number(g))}${hex(Number(b))}`);
  }
  return found;
}

test("tokens.css is a mirror: every colour in it exists in DhunAppearance.kt", () => {
  const literals = colourLiterals(tokensCss);
  assert.ok(literals.length > 40, `expected the full palette, found ${literals.length} literals`);
  const untraced = literals.filter((hex) => !appearanceKt.includes(hex));
  assert.deepEqual(
    untraced,
    [],
    "these colours are not in the app's token source — they were invented, not mirrored",
  );
});

test("the dark and light token sets are both declared", () => {
  assert.match(tokensCss, /\[data-dhun-theme="dark"\]/);
  assert.match(tokensCss, /\[data-dhun-theme="light"\]/);
  // Accent ramps: six accents, light and dark.
  for (const accent of ["brand", "azure", "jade", "amber", "rose", "cyan"]) {
    for (const mode of ["light", "dark"]) {
      assert.match(
        tokensCss,
        new RegExp(`--dhun-accent-${accent}-${mode}:`),
        `missing ${accent} ${mode} ramp`,
      );
    }
  }
});

test("the app's rule holds on the web too: no raw hex outside tokens.css", () => {
  const appCss = readFileSync(path.join(here, "..", "src", "css", "app.css"), "utf8");
  const hexes = [...appCss.matchAll(/#([0-9a-fA-F]{3,8})\b/g)].map((m) => m[0]);
  assert.deepEqual(hexes, [], `raw colours in app.css: ${hexes.join(", ")}`);
});

/** @media conditions cannot use custom properties, so the two breakpoint
 *  literals in app.css are the one documented exception to the token rule. */
function stripMediaConditions(css) {
  return css
    .replace(/\/\*[\s\S]*?\*\//g, "") // comments are prose, not declarations
    .replace(/@media[^{]*\{/g, "@media {");
}

test("no raw pixel values outside tokens.css", () => {
  const appCss = stripMediaConditions(readFileSync(path.join(here, "..", "src", "css", "app.css"), "utf8"));
  const px = [...appCss.matchAll(/:\s*[^;{}]*?(\d+(?:\.\d+)?)px/g)].map((m) => m[0].trim());
  assert.deepEqual(px, [], `raw px in app.css: ${px.join(" | ")}`);
});

test("the breakpoint literals in app.css match the token file", () => {
  const appCss = readFileSync(path.join(here, "..", "src", "css", "app.css"), "utf8");
  for (const [token, literal] of [
    ["--dhun-breakpoint-two-pane", "840px"],
    ["--dhun-breakpoint-wide-player", "480px"],
  ]) {
    const declared = new RegExp(`${token}:\\s*(\\d+)px`).exec(tokensCss);
    assert.ok(declared, `${token} is missing from tokens.css`);
    assert.ok(
      appCss.includes(`min-width: ${literal}`),
      `app.css must use ${literal} where tokens.css declares ${token}=${declared[1]}px`,
    );
  }
});

test("the motion and shape tokens carry the app's own values", () => {
  // DhunAnimations: fast 150 / medium 300 / slow 500.
  assert.match(tokensCss, /--dhun-motion-fast:\s*150ms/);
  assert.match(tokensCss, /--dhun-motion-medium:\s*300ms/);
  assert.match(tokensCss, /--dhun-motion-slow:\s*500ms/);
  // DhunShapes: 4 / 8 / 12 / 16 / 28 / 32.
  assert.match(tokensCss, /--dhun-shape-extra-small:\s*4px/);
  assert.match(tokensCss, /--dhun-shape-small:\s*8px/);
  assert.match(tokensCss, /--dhun-shape-medium:\s*12px/);
  assert.match(tokensCss, /--dhun-shape-large:\s*16px/);
  assert.match(tokensCss, /--dhun-shape-extra-large:\s*28px/);
  assert.match(tokensCss, /--dhun-shape-extra-extra-large:\s*32px/);
  // DhunSpacing: the screen padding the app uses is 20dp.
  assert.match(tokensCss, /--dhun-space-screen-padding:\s*20px/);
});
