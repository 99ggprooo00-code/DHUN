/**
 * The design system, read out of the app's token file at build time.
 *
 * The `/ui/` page prints the app's colour, shape, spacing and type values next to
 * swatches. Typing those values into a data file is how a design-system page
 * drifts from the design system: the app changes a ramp, the page keeps showing
 * the old one, and nothing fails. So the values here are parsed from
 * `app-web/src/css/tokens.css` — the file the web app itself is painted from,
 * generated from `DhunAppearance.kt`, `DhunShapes.kt`, `DhunSpacing.kt` and
 * `DhunTypography.kt`.
 *
 * Two consequences worth stating:
 *
 *  1. `scripts/website_quality.py` (`design_system_violations`) re-derives the
 *     same properties from the same two files with Python only, so app CI fails
 *     if the page and the app disagree — and this module is what makes the page
 *     the app's values rather than a copy of them.
 *  2. The swatches are painted with the *site's* generated aliases
 *     (`website/css/tokens.css`), which are themselves generated from the app
 *     file. A visitor therefore sees the token values they are reading about.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

const HERE = fileURLToPath(new URL(".", import.meta.url));
const APP_TOKENS = join(HERE, "..", "..", "..", "app-web", "src", "css", "tokens.css");

/** Strips comments, so a selector group does not arrive with prose attached. */
function bare(css) {
  return css.replace(/\/\*[\s\S]*?\*\//g, "");
}

const css = bare(readFileSync(APP_TOKENS, "utf8"));

/** name → value, from the `:root` blocks of one scheme. */
function scheme(light) {
  const values = {};
  for (const [, selector, body] of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const trimmed = selector.replace(/\s+/g, " ").trim();
    // The optional-accent blocks are skipped: they are per-ramp overrides, and
    // the page prints the ramps themselves.
    if (trimmed.includes("[data-dhun-accent")) continue;
    const isLight = trimmed.includes('[data-dhun-theme="light"]');
    const isDark =
      /(?:^|[,\s]):root\b/.test(trimmed) || trimmed.includes('[data-dhun-theme="dark"]');
    if (light !== isLight || (!isDark && !isLight)) continue;
    for (const [, name, value] of body.matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+)/g)) {
      values[name] = value.trim();
    }
  }
  return values;
}

// `scheme(false)` is the app's dark set plus the scales it declares once, in an
// unconditional block. `scheme(true)` is only what the light block redeclares.
const darkTokens = scheme(false);
const lightTokens = scheme(true);
// The scales live in that unconditional block, so a light lookup falls back to
// the dark set when the app declares a value once — the same cascade a browser
// applies. Reading a *colour* from the fallback would be a bug, so the Proxy is
// only ever asked for a token the light block may legitimately not mention.
const light = new Proxy(lightTokens, {
  get: (target, name) => target[name] ?? darkTokens[name],
});

/** The site alias each printed token is painted from, where one exists. */
const ALIASES = {
  "--dhun-background": "--bg",
  "--dhun-surface": "--surface",
  "--dhun-surface-variant": "--surface-variant",
  "--dhun-surface-elevated": "--surface-elevated",
  "--dhun-surface-highest": "--surface-highest",
  "--dhun-surface-card": "--surface-card",
  "--dhun-text": "--text",
  "--dhun-accent": "--accent",
  "--dhun-accent-container": "--accent-container",
  "--dhun-warning": "--warning",
  "--dhun-success": "--success",
  "--dhun-error": "--error",
};

const COLOURS = [
  ["--dhun-background", "background", "app canvas"],
  ["--dhun-surface", "surface", "cards, sheets"],
  ["--dhun-surface-variant", "surfaceVariant", "raised rows"],
  ["--dhun-surface-elevated", "surfaceElevated", "dialogs"],
  ["--dhun-surface-highest", "surfaceHighest", "controls on top"],
  ["--dhun-surface-card", "surfaceCard", "list rows"],
  ["--dhun-accent", "accent", "brand ramp, dark"],
  ["--dhun-accent-container", "accentContainer", "selected states"],
  ["--dhun-warning", "warning", "gates, warnings"],
  ["--dhun-success", "success", "completed states"],
  ["--dhun-error", "error", "failures"],
];

/** The six accent ramps, each in both schemes. */
const RAMPS = ["brand", "azure", "jade", "amber", "rose", "cyan"];

/** `4px` → `4 px`, for the printed scale. */
const px = (value) => `${String(value).replace(/px$/, "").trim()} px`;

export default {
  source: "app-web/src/css/tokens.css (from shared/.../design/DhunAppearance.kt, DhunShapes.kt, DhunSpacing.kt, DhunTypography.kt)",
  /** The app file the page was built from, as a path a reader can open. */
  file: "app-web/src/css/tokens.css",

  colours: COLOURS.map(([token, name, use]) => ({
    name,
    token,
    alias: ALIASES[token],
    dark: darkTokens[token],
    light: light[token],
    use,
  })).filter((entry) => entry.dark),

  ramps: RAMPS.map((ramp) => ({
    name: ramp,
    dark: darkTokens[`--dhun-accent-${ramp}-dark`],
    light: darkTokens[`--dhun-accent-${ramp}-light`],
    alias: `--ramp-${ramp}`,
  })),

  shapes: ["extra-small", "small", "medium", "large", "extra-large", "extra-extra-large", "full"]
    .map((step) => ({ name: step, value: px(darkTokens[`--dhun-shape-${step}`]) }))
    .filter((entry) => entry.value && !entry.value.startsWith("undefined")),

  spacing: ["xs", "sm", "md", "lg", "xl", "xxl", "xxxl", "huge"]
    .map((step) => ({ name: step, value: px(darkTokens[`--dhun-space-${step}`]) }))
    .filter((entry) => entry.value && !entry.value.startsWith("undefined")),

  type: ["display", "headline", "title", "body", "label"]
    .map((family) => ({
      name: family,
      value: ["large", "medium", "small"]
        .map((size) => {
          const value = darkTokens[`--dhun-text-${family}-${size}`];
          return value ? `${value.split(" ")[1].split("/")[0]}` : null;
        })
        .filter(Boolean)
        .join(" / "),
    }))
    .filter((entry) => entry.value),

  target: `Touch targets: ${px(darkTokens["--dhun-space-touch-target"])} minimum — DhunSpacing.touchTarget`,
};
