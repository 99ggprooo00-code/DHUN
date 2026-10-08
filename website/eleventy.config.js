/**
 * Eleventy build for the DHUN marketing site.
 *
 * Deliberate constraints (see .ai/WEBSITE_PLAN.md Part A §3):
 *  - one command (`eleventy`) → `dist/`, plain files out;
 *  - no plugins, no bundler, no client-side JavaScript;
 *  - the output is byte-stable, because the built site is committed at
 *    `website/dist/` and a drift check in `.github/workflows/website.yml`
 *    rebuilds and compares. No build timestamps, no random ids, no hashing.
 *
 * Two performance decisions live in this file (Part A §10.1):
 *  1. **CSS is inlined, per route.** Each page declares the CSS modules it
 *     needs in its front matter (`cssModules`), and `inlineCss` composes them
 *     into one `<style>` block in `<head>`. That removes the render-blocking
 *     stylesheet request and the network dependency tree entirely, at the same
 *     byte cost as the linked sheet — and because the modules are declared
 *     explicitly, a route never ships a module it cannot use (the 404 carries
 *     none of the mockup geometry, and `/ui/` alone carries the token
 *     swatches). A module name the config does not know is a build failure,
 *     not a silent omission. What it gives up is cross-page CSS caching,
 *     which costs nothing here: the sheet lives in the HTML.
 *  2. **Icons are one `<symbol>` each, referenced with `<use>`.** A page draws
 *     the same glyph many times (the mockups are full of them), so the geometry
 *     is written once per page by `iconSprite` and each use costs a tag. No
 *     icon font, no external sprite request, no JavaScript.
 *
 * Neither is asserted by argument: `scripts/website_quality.py` fails the build
 * if a page uses a class its modules do not define (`css_coverage_violations`)
 * or if a `<use>` points at a `<symbol>` the page does not have
 * (`sprite_violations`), and the browser job in `website.yml` asserts every
 * icon actually renders geometry.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

const HERE = fileURLToPath(new URL(".", import.meta.url));

// Hand-drawn 24×24 geometry — no icon library, no third-party asset.
const ICONS = {
  home: '<path d="M4 11.2 12 4l8 7.2V20h-5.5v-5h-5V20H4z"/>',
  search: '<circle cx="10.5" cy="10.5" r="6"/><path d="m15.4 15.4 4.6 4.6"/>',
  library: '<path d="M4 5h3v14H4zM9 5h3v14H9zM14.4 6l2.8-.7 3 12.3-2.8.7z"/>',
  gear: '<circle cx="12" cy="12" r="3.2"/><path d="M12 3.5v2.2M12 18.3v2.2M20.5 12h-2.2M5.7 12H3.5M18 6l-1.6 1.6M7.6 16.4 6 18M18 18l-1.6-1.6M7.6 7.6 6 6"/>',
  play: '<path d="M8 5.5v13l11-6.5z"/>',
  pause: '<path d="M8 5h3v14H8zM13 5h3v14h-3z"/>',
  skipNext: '<path d="M6 5.5 15 12l-9 6.5zM16.5 5.5h2v13h-2z"/>',
  skipPrev: '<path d="M18 5.5 9 12l9 6.5zM5.5 5.5h2v13h-2z"/>',
  shuffle: '<path d="M4 6h4l3 4M20 6h-4l-8 12H4M16 18h4v-0M17 8.5 20 6l-3-2.5M17 15.5 20 18l-3 2.5"/>',
  repeat: '<path d="M7 7h10l-2.5-2.5M17 17H7l2.5 2.5"/>',
  download: '<path d="M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 19h14"/>',
  check: '<path d="m5 13 4.5 4.5L19 7"/>',
  list: '<path d="M4 7h11M4 12h11M4 17h7M18 6.5l1.5 1.5L22 5.5"/>',
  lyrics: '<path d="M5 6h9M5 10h14M5 14h11M5 18h6"/>',
  queue: '<path d="M4 7h10M4 12h10M4 17h6M17 15.5v5l4-2.5z"/>',
  related: '<path d="M8 7h12M8 12h12M8 17h12M4 7h.01M4 12h.01M4 17h.01"/>',
  external: '<path d="M14 5h5v5M19 5l-8 8M18 14v5H5V6h5"/>',
  clock: '<circle cx="12" cy="12" r="8"/><path d="M12 7.5V12l3 2"/>',
  speaker: '<path d="M5 10h3l4.5-4v12L8 14H5z"/><path d="M16 9.5a3.5 3.5 0 0 1 0 5M18.5 7a7 7 0 0 1 0 10"/>',
  equalizer: '<path d="M6 18V9M12 18V5M18 18v-6"/>',
  widget: '<rect x="4" y="4" width="7" height="7" rx="1.6"/><rect x="13" y="4" width="7" height="7" rx="1.6"/><rect x="4" y="13" width="7" height="7" rx="1.6"/><rect x="13" y="13" width="7" height="7" rx="1.6"/>',
  tray: '<path d="M4 5h16v10H4zM9 19h6l1-2H8z"/>',
  window: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18"/>',
  offline: '<circle cx="12" cy="12" r="8"/><path d="M12 8v4l3 2"/><path d="m5 19 14-14"/>',
};

// The layout's placeholder for the page's icon definitions, and the reference
// form the shortcode emits. Both are named here so the transform, the shortcode
// and the Python checks in scripts/website_quality.py agree on one spelling.
const SPRITE_SLOT = "<!--icon-sprite-->";
const SPRITE_USE = /<use href="#i-([a-zA-Z0-9]+)"\/>/g;

// Module order is meaningful: tokens first, then the shared layers, then the
// page-specific ones. `scripts/website_quality.py` reads the built pages, so
// the order only has to be stable — and it is, because it is written here.
const CSS_MODULES = ["tokens", "base", "components", "mockups", "ui"];

function readModule(name) {
  if (!CSS_MODULES.includes(name)) {
    throw new Error(`unknown CSS module: ${name} (known: ${CSS_MODULES.join(", ")})`);
  }
  return readFileSync(join(HERE, "css", `${name}.css`), "utf8").trim();
}

export default function (eleventyConfig) {
  // No `addPassthroughCopy`: the site ships no separate asset files. The one
  // asset it has (`src/assets/dhun-favicon.svg`) is inlined into every page as
  // a `data:` URI by `src/_data/favicon.js`, which is why each route is a
  // single request. A file added to `src/assets/` without a consumer therefore
  // does *not* ship — deliberately: the quality checker fails on any built file
  // no page references (`unreferenced_file_violations`), so shipping one by
  // passthrough would be a red test rather than a silent extra request.

  // Per-page stylesheet: composed from the modules the page declares. Emitted
  // unminified — `tools/minify.mjs` minifies `<style>` blocks with the same
  // lossless CSS pass it uses everywhere else, and `tools/verify-minify.mjs`
  // proves that pass only removed whitespace and comments.
  eleventyConfig.addFilter("inlineCss", function (modules) {
    // Iterate what the *page* asked for, not the allowlist. A name the config
    // does not know is a typo in front matter, and `readModule` throws on it —
    // the earlier filter-first version silently dropped an unknown module, so a
    // page shipped with no stylesheet and nothing said so until the class
    // coverage rule noticed the unstyled markup.
    const names = (modules || []).filter((name) => CSS_MODULES.includes(name));
    if (names.length !== (modules || []).length) {
      const unknown = (modules || []).filter((name) => !CSS_MODULES.includes(name));
      throw new Error(`unknown CSS module(s): ${unknown.join(", ")} (known: ${CSS_MODULES.join(", ")})`);
    }
    return names.map(readModule).join("\n");
  });

  // Shortcode: a reference to this page's icon sprite. Callers add their own
  // label when the icon carries meaning on its own.
  eleventyConfig.addShortcode("icon", function (name, cls) {
    if (!ICONS[name]) throw new Error(`unknown icon: ${name}`);
    const klass = cls ? ` ${cls}` : "";
    return `<svg class="i${klass}" viewBox="0 0 24 24" aria-hidden="true"><use href="#i-${name}"/></svg>`;
  });

  // The sprite is assembled by a transform over the finished document, not by
  // shortcode state: Eleventy renders a layout in its own pass, so a module-
  // level "icons used so far" set leaks across pages. The transform reads the
  // document's own <use> references, so it is stateless by construction.
  eleventyConfig.addTransform("iconSprite", function (content, outputPath) {
    if (!outputPath || !outputPath.endsWith(".html")) return content;
    const names = [
      ...new Set([...content.matchAll(SPRITE_USE)].map((match) => match[1])),
    ].sort();
    if (!names.length) return content.split(SPRITE_SLOT).join("");
    if (!content.includes(SPRITE_SLOT)) {
      throw new Error("this page draws icons but its layout has no icon-sprite slot");
    }
    const symbols = names
      .map((name) => `<symbol id="i-${name}" viewBox="0 0 24 24">${ICONS[name]}</symbol>`)
      .join("");
    const sprite = `<svg class="sprite" aria-hidden="true">${symbols}</svg>`;
    return content.split(SPRITE_SLOT).join(sprite);
  });

  return {
    dir: {
      input: "src",
      output: "dist",
      includes: "_includes",
      data: "_data",
    },
    templateFormats: ["njk", "html", "txt", "xml"],
    htmlTemplateEngine: "njk",
    markdownTemplateEngine: "njk",
  };
}
