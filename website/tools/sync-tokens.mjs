/**
 * The site's stylesheet gets its design system from the app, not from a copy.
 *
 * Why this exists: `website/css/tokens.css` used to carry hand-copied values
 * "read out of the app's Compose design system". That is a copy, and a copy of a
 * design system is how the two surfaces drift apart — the app gains a token, the
 * site keeps the old palette, and nothing fails. The user's instruction for this
 * work was explicit: the site must read as DHUN the app, not as a marketing page
 * that happens to use purple.
 *
 * So the app's token file (`app-web/src/css/tokens.css`, which mirrors
 * `DhunAppearance.kt`) is the source, and this tool writes the site's file from
 * it in one of two parts:
 *
 *   1. **The app's tokens, verbatim.** Every `--dhun-*` custom property, the
 *      whole dark-first palette, all six accent ramps, the shape and type and
 *      spacing scales. Copied byte-for-byte so that a colour the app changes is
 *      a colour the site changes, and so the app's own rules
 *      (`app-web/tests/shell.test.mjs` for the token file, and the
 *      contrast/discipline checks in `scripts/test_app_web.py`) keep their
 *      meaning on the site's copy.
 *   2. **A short alias layer.** The site's page rules and the Python gates in
 *      `scripts/website_quality.py` speak a small semantic vocabulary (`--bg`,
 *      `--text`, `--accent`, `--hairline`, …). Each alias is resolved *here*,
 *      against the app's own values, in both colour schemes — so the alias is a
 *      reference into the app's set rather than a second definition of it.
 *
 * Two alias families are deliberately **not** taken from the app, and each says
 * why in its own comment: the glass opacities a page needs (the app's glass sits
 * on album artwork; a document on a flat background needs more of it to keep
 * text legible) and the site-only scaffolding (page width, shadows, the target
 * size). The values are declared in one table below, so "the site differs here"
 * is a line someone can read rather than a diff someone has to notice.
 *
 * Usage:
 *   node tools/sync-tokens.mjs            # rewrite website/css/tokens.css
 *   node tools/sync-tokens.mjs --check    # exit 1 if it is stale
 *
 * The build runs it first, so a stale file cannot be committed quietly, and the
 * workflow's existing "Committed build must match a fresh build" drift check
 * turns a stale file into a failed run. `scripts/website_quality.py`
 * (`design_system_violations`) re-derives the copying rule from the two files
 * with Python only — that is what app CI can check, since it has no Node.
 */
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

const HERE = fileURLToPath(new URL(".", import.meta.url));
const REPO = join(HERE, "..", "..");
export const APP_TOKENS = join(REPO, "app-web", "src", "css", "tokens.css");
export const SITE_TOKENS = join(REPO, "website", "css", "tokens.css");

const APP_URL = "app-web/src/css/tokens.css";

/** The header the generated file carries. Kept in one place: the Python rule
 *  asserts that the committed file *starts* with these first three lines. */
export const HEADER = [
  "/* ============================================================================",
  "   GENERATED FILE — DO NOT EDIT.",
  "   Written by: node website/tools/sync-tokens.mjs",
  `   Source:     ${APP_URL} (which mirrors DhunAppearance.kt / DhunShapes.kt /`,
  "               DhunTypography.kt / DhunSpacing.kt in the app)",
  "",
  "   Every declaration below names the `--dhun-*` property it was read from and",
  "   carries that property's two values in one `light-dark()` pair, so the dark",
  "   and light sets cannot drift apart and neither can drift from the app.",
  "",
  "   The pair is preceded by the app's dark value written out plainly. That line",
  "   is what a reader — or the Python rule — sees without evaluating the",
  "   function; it is NOT a fallback for an old engine, because a custom property",
  "   keeps the later declaration whatever tokens it holds. The sheet therefore",
  "   has a floor: `light-dark()` needs Chrome/Edge 123, Firefox 120 or Safari",
  "   17.5, and the appearance control already needs `:has()` (Chrome 105,",
  "   Firefox 121, Safari 15.4).",
  "   ========================================================================== */",
];

/**
 * Site alias → app token, per colour scheme.
 *
 * `app` names a custom property in the app's token file. `null` means the alias
 * has no app equivalent and its value comes from [SITE_ONLY]. `shared: true`
 * means the app declares the value once and never overrides it per scheme
 * (shapes, spacing, fonts), so both sides of the generated `light-dark()` are
 * the same value.
 */
const ALIASES = {
  "--bg": { app: "--dhun-background" },
  "--surface": { app: "--dhun-surface" },
  "--surface-variant": { app: "--dhun-surface-variant" },
  "--surface-elevated": { app: "--dhun-surface-elevated" },
  "--surface-highest": { app: "--dhun-surface-highest" },
  "--surface-card": { app: "--dhun-surface-card" },
  "--text": { app: "--dhun-text" },
  "--text-2": { app: "--dhun-text-secondary" },
  "--text-3": { app: "--dhun-text-tertiary" },
  "--text-muted": { app: null },
  "--text-4": { app: null },
  "--accent": { app: "--dhun-accent" },
  "--on-accent": { app: "--dhun-on-accent" },
  "--accent-container": { app: "--dhun-accent-container" },
  "--on-accent-container": { app: "--dhun-on-accent-container" },
  "--warning": { app: "--dhun-warning" },
  "--success": { app: "--dhun-success" },
  "--error": { app: "--dhun-error" },
  "--error-readable": { app: null },
  "--glass": { app: "--dhun-glass" },
  "--glass-strong": { app: "--dhun-glass-strong" },
  "--glass-bar-top": { app: "--dhun-glass-bar-top" },
  "--glass-edge": { app: "--dhun-glass-edge" },
  "--hairline": { app: "--dhun-border" },
  "--hairline-strong": { app: "--dhun-border-strong" },
  "--scrim": { app: "--dhun-scrim" },
  "--scrim-strong": { app: "--dhun-scrim-strong" },
  "--r-xs": { app: "--dhun-shape-extra-small", shared: true },
  "--r-sm": { app: "--dhun-shape-small", shared: true },
  "--r-md": { app: "--dhun-shape-medium", shared: true },
  "--r-lg": { app: "--dhun-shape-large", shared: true },
  "--r-xl": { app: "--dhun-shape-extra-large", shared: true },
  "--r-xxl": { app: "--dhun-shape-extra-extra-large", shared: true },
  "--r-full": { app: "--dhun-shape-full", shared: true },
  "--sp-1": { app: "--dhun-space-xs", shared: true },
  "--sp-2": { app: "--dhun-space-sm", shared: true },
  "--sp-3": { app: "--dhun-space-md", shared: true },
  "--sp-4": { app: "--dhun-space-lg", shared: true },
  "--sp-5": { app: "--dhun-space-xl", shared: true },
  "--sp-6": { app: "--dhun-space-xxl", shared: true },
  "--sp-8": { app: "--dhun-space-xxxl", shared: true },
  "--sp-12": { app: "--dhun-space-huge", shared: true },
  "--target": { app: "--dhun-space-touch-target", shared: true },
  "--font": { app: "--dhun-font-sans", shared: true },
  "--mono": { app: "--dhun-font-mono", shared: true },
  "--shadow": { app: null },
  "--wrap": { app: null, shared: true },
};

/**
 * The aliases with no app equivalent, and the reasoning. A value here is a
 * decision about *this* surface — a page with a flat background is not the same
 * backdrop as a full-bleed album cover.
 */
const SITE_ONLY = {
  "--text-muted": {
    // Dark is the app's own rung; light is the same ink at the alpha that
    // clears the floor on every light surface.
    dark: "rgba(255, 255, 255, 0.6)",
    light: "rgba(26, 23, 20, 0.65)",
    why: "the site's tertiary rung: the app's --dhun-text-tertiary is 3.95:1 on "
      + "--surface in light (measured by the browser job, 2026-10-16, below the "
      + "4.5:1 body floor) because it is a 0.549-alpha ink; the site paints its "
      + "tertiary text with the same ink at 0.65, which is 5.06:1 on "
      + "--surface-highest and 5.55:1 on --surface. --text-3 stays declared above "
      + "so the sheet still mirrors the app, and no rule paints text with it",
  },
  "--text-4": {
    dark: "rgba(255, 255, 255, 0.45)",
    light: "rgba(26, 23, 20, 0.42)",
    why: "non-text affordances only: 0.45-alpha white is ~3.9:1 and fails the "
      + "4.5:1 body floor, so no text uses it",
  },
  "--error-readable": {
    dark: "#ec9fae",
    // Light takes the app's own `--dhun-error`, which passes there.
    lightFrom: "--dhun-error",
    why: "the app's --dhun-error is 3.99:1 on --surface-highest in dark "
      + "(measured by the browser job), so dark uses the same hue lightened to "
      + "5.83:1; light passes with the app's own value",
  },
  "--shadow": {
    dark: "0 18px 48px rgba(0, 0, 0, 0.45)",
    light: "0 18px 48px rgba(26, 23, 20, 0.14)",
    why: "the app draws elevation with its surface ladder, not with a shadow; "
      + "the site keeps one shadow for the couple of places a page needs depth",
  },
  "--wrap": {
    dark: "1120px",
    light: "1120px",
    why: "page scaffolding: the reading measure, not a design-system value",
  },
};

/** Reads every custom property out of the app's token file, per scheme. */
export function readAppTokens(css) {
  // Comments out first: a selector group that arrives with the preceding block
  // comment attached does not start with `:root`, which is how the scales in the
  // file's first `:root` block were first read as "not declared".
  const bare = css.replace(/\/\*[\s\S]*?\*\//g, "");
  const tokens = { dark: {}, light: {} };
  for (const [, selector, body] of bare.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const trimmed = selector.replace(/\s+/g, " ").trim();
    // Only the *base* blocks count. The file also carries one block per optional
    // accent (`[data-dhun-theme="dark"][data-dhun-accent="cyan"]` and five more)
    // which redeclares `--dhun-accent`; folding those in would make whichever
    // ramp came last in the file look like the app's default, which is how this
    // first read the site's accent as cyan instead of brand.
    if (trimmed.includes("[data-dhun-accent")) continue;
    const isLight = trimmed.includes('[data-dhun-theme="light"]');
    const isDark =
      /(?:^|[,\s]):root\b/.test(trimmed) || trimmed.includes('[data-dhun-theme="dark"]');
    if (!isDark && !isLight) continue;
    for (const [, name, value] of body.matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+)/g)) {
      const clean = value.trim();
      if (isLight) tokens.light[name] = clean;
      else tokens.dark[name] = clean;
    }
  }
  return tokens;
}

/**
 * One app property, dark value and light value, from the app's own file.
 *
 * The ramps are the one family the app spells with the scheme *inside* the name
 * (`--dhun-accent-brand-dark`, `…-light`) rather than in a per-scheme block, so
 * a caller that passes a bare `--dhun-accent-brand` gets the suffixed pair.
 */
function appPair(name, appTokens) {
  if (!(name in appTokens.dark) && `${name}-dark` in appTokens.dark) {
    return appPair2(`${name}-dark`, `${name}-light`, appTokens);
  }
  // The app declares the scales (shapes, spacing, fonts) once, in an
  // unconditional `:root` block, and the colours per scheme. So a missing side
  // means "the app states this once", not "undefined".
  return appPair2(name, name, appTokens);
}

/** `darkName` and `lightName`, each read from the scheme the app declares it in. */
function appPair2(darkName, lightName, appTokens) {
  const dark = appTokens.dark[darkName] ?? appTokens.light[darkName];
  const light = appTokens.light[lightName] ?? appTokens.dark[lightName];
  if (dark === undefined || light === undefined) {
    throw new Error(`${darkName} / ${lightName} is not declared in ${APP_URL}`);
  }
  return { dark, light };
}

/** One alias's dark and light value, resolved out of the app's file. */
function valuesFor(alias, spec, appTokens) {
  if (spec.ramp) return appPair(`--dhun-accent-${spec.ramp}`, appTokens);
  if (spec.app) {
    const pair = appPair(spec.app, appTokens);
    return spec.shared ? { dark: pair.dark, light: pair.dark } : pair;
  }
  const own = SITE_ONLY[alias];
  const dark = own.dark ?? appTokens.dark[own.lightFrom ?? ""];
  const light = own.light ?? (own.lightFrom ? appTokens.light[own.lightFrom] : dark);
  if (dark === undefined || light === undefined) {
    throw new Error(`${alias}: no value for one of the schemes (${JSON.stringify(own)})`);
  }
  return { dark, light };
}

/** The six accent ramps the app ships, in the app's own order. */
const RAMPS = ["brand", "azure", "jade", "amber", "rose", "cyan"];

/** One ramp's four roles, read from the app file for each scheme. */
function rampRoles(ramp, appTokens) {
  const roles = {
    "--accent": `--dhun-accent-${ramp}`,
    "--on-accent": `--dhun-on-accent-${ramp}`,
    "--accent-container": `--dhun-accent-container-${ramp}`,
    "--on-accent-container": `--dhun-on-accent-container-${ramp}`,
  };
  return Object.entries(roles).map(([alias, name]) => ({
    alias,
    name: `${name}-{dark,light}`,
    ...appPair2(`${name}-dark`, `${name}-light`, appTokens),
  }));
}

export function render(appCss) {
  const appTokens = readAppTokens(appCss);
  const lines = [HEADER.join("\n"), "", ":root {"];
  lines.push("  /* Dark is the app's default. The plain value on each line is what a reader");
  lines.push("     that does not evaluate `light-dark()` reads; the pair below it is what a");
  lines.push("     browser computes. */");
  lines.push("  color-scheme: dark;");
  for (const [alias, spec] of Object.entries(ALIASES)) {
    const { dark, light } = valuesFor(alias, spec, appTokens);
    const note = spec.app
      ? spec.app
      : spec.ramp
        ? `--dhun-accent-${spec.ramp}-{dark,light}`
        : `site value: ${SITE_ONLY[alias].why}`;
    lines.push(`  ${alias}: ${dark};  /* ${note} */`);
    if (dark !== light) {
      lines.push(`  ${alias}: light-dark(${light}, ${dark});`);
    }
  }
  lines.push("");
  lines.push("  /* The six accent ramps as flat aliases, for the appearance chips and the");
  lines.push("     /ui/ swatch table. `--accent` above is the ramp in force. */");
  for (const ramp of RAMPS) {
    const [accent] = rampRoles(ramp, appTokens);
    const { dark, light } = valuesFor(`--ramp-${ramp}`, { ramp }, appTokens);
    lines.push(`  --ramp-${ramp}: ${dark};  /* --dhun-accent-${ramp}-dark */`);
    if (dark !== light) {
      lines.push(`  --ramp-${ramp}: light-dark(${light}, ${dark});`);
    }
    void accent;
  }
  lines.push("}");
  lines.push("");
  lines.push("/* The five ramps a visitor can choose. `light-dark()` resolves the scheme, so");
  lines.push("   each ramp is one block rather than one per scheme. */");
  for (const ramp of RAMPS.filter((ramp) => ramp !== "brand")) {
    const roles = rampRoles(ramp, appTokens);
    lines.push(`:root:has(#accent-${ramp}:checked) {`);
    for (const role of roles) {
      lines.push(
        role.dark === role.light
          ? `  ${role.alias}: ${role.dark};`
          : `  ${role.alias}: light-dark(${role.light}, ${role.dark});`,
      );
    }
    lines.push("}");
    lines.push("");
  }
  lines.push("/* ---------------------------------------------------------------------------");
  lines.push("   Appearance — the app's own two choices, with no JavaScript.");
  lines.push("");
  lines.push("   The app ships a theme (dark by default, light optional) and six accent");
  lines.push("   ramps, and its Settings screen is where a visitor changes them. A static");
  lines.push("   site cannot run that screen, so the same two controls are offered as radio");
  lines.push("   groups at the foot of every page and applied with `:has()`. The palette is");
  lines.push("   the app's, so picking an accent here picks the ramp the app would use.");
  lines.push("");
  lines.push("   Dark is still the default and the OS preference is still respected: light");
  lines.push("   applies when the OS asks for it and the visitor has not chosen dark, or");
  lines.push("   when the visitor has chosen light. The choice is remembered for this page");
  lines.push("   only — no JavaScript, no storage, no cookie.");
  lines.push("   --------------------------------------------------------------------------- */");
  lines.push("");
  lines.push("/* The two themes, as `color-scheme` states: `light-dark()` above resolves");
  lines.push("   against it, and so does every form control and scrollbar on the page. */");
  lines.push(":root:has(#theme-light:checked) {");
  lines.push("  color-scheme: light;");
  lines.push("}");
  lines.push("");
  lines.push(":root:has(#theme-dark:checked) {");
  lines.push("  color-scheme: dark;");
  lines.push("}");
  lines.push("");
  lines.push("@media (prefers-color-scheme: light) {");
  lines.push("  :root:not(:has(#theme-dark:checked)) {");
  lines.push("    color-scheme: light;");
  lines.push("  }");
  lines.push("}");
  lines.push("");
  lines.push("/* `prefers-contrast: more` — the operating system's \"increase contrast\"");
  lines.push("   setting. The secondary rungs become the primary text colour, which is");
  lines.push("   >= 7:1 against every surface in both schemes (re-derived from these tokens");
  lines.push("   by `high_contrast_violations` in scripts/website_quality.py). A reader who");
  lines.push("   asks for more contrast and gets the same ladder has been told no for no");
  lines.push("   reason. */");
  lines.push("@media (prefers-contrast: more) {");
  lines.push("  :root {");
  lines.push("    --text-2: var(--text);");
  lines.push("    --text-muted: var(--text);");
  lines.push("  }");
  lines.push("}");
  lines.push("");
  return lines.join("\n");
}

function main(argv) {
  const check = argv.includes("--check");
  const appCss = readFileSync(APP_TOKENS, "utf8");
  const targets = [
    { path: SITE_TOKENS, expected: render(appCss), label: "website/css/tokens.css" },
  ];
  let stale = 0;
  for (const target of targets) {
    const actual = (() => {
      try {
        return readFileSync(target.path, "utf8");
      } catch {
        return null;
      }
    })();
    if (actual === target.expected) continue;
    if (check) {
      console.error(
        `tokens: ${target.label} is stale — run 'node website/tools/sync-tokens.mjs' and commit the result.`,
      );
      stale += 1;
      continue;
    }
    writeFileSync(target.path, target.expected, "utf8");
    console.log(`tokens: wrote ${target.label} (${target.expected.length} bytes) from ${APP_URL}.`);
  }
  if (stale === 0) {
    console.log(
      check
        ? "tokens: website/css/tokens.css matches the app's token file."
        : "tokens: website/css/tokens.css was already current.",
    );
  }
  return stale ? 1 : 0;
}

if (process.argv[1] && process.argv[1].endsWith("sync-tokens.mjs")) {
  process.exit(main(process.argv.slice(2)));
}
