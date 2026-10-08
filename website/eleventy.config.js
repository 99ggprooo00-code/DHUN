/**
 * Eleventy build for the DHUN marketing site.
 *
 * Deliberate constraints (see .ai/WEBSITE_PLAN.md Part A §3):
 *  - one command (`eleventy`) → `dist/`, plain files out;
 *  - no plugins, no bundler, no client-side JavaScript;
 *  - the output is byte-stable, because the built site is committed at
 *    `website/dist/` and a drift check in `.github/workflows/website.yml`
 *    rebuilds and compares. No build timestamps, no random ids, no hashing.
 */
export default function (eleventyConfig) {
  eleventyConfig.addPassthroughCopy({ "src/assets": "assets" });

  // Shortcode: an inline SVG icon, hand-drawn geometry (no icon library, no
  // third-party asset). Decorative by default; callers add their own label
  // when the icon carries meaning on its own.
  eleventyConfig.addShortcode("icon", function (name, cls) {
    const paths = {
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
    const body = paths[name];
    if (!body) throw new Error(`unknown icon: ${name}`);
    const klass = cls ? ` ${cls}` : "";
    return `<svg class="i${klass}" viewBox="0 0 24 24" aria-hidden="true">${body}</svg>`;
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
