/**
 * The tab icon, as a `data:` URI built from the one source file.
 *
 * Why a data URI: every route's stylesheet is already inlined
 * (`eleventy.config.js`), so the icon `<link>` was the *only* remaining
 * subresource — Lighthouse measured `requests=2` per route on the merged head
 * (run 37808955008/37808955045 annotations). A `data:` URI makes each route
 * exactly one request: the HTML that carries it. The 500-byte cost is inside
 * the 60 KB per-route budget and is ratcheted in `website/budget-baseline.json`.
 *
 * The trade-off is recorded rather than hidden: an `href` that is not a
 * retrievable file cannot be fetched by a client that does not render SVG
 * favicons, so the tab icon degrades on old engines instead of 404-ing
 * (see `.ai/KNOWN_LIMITATIONS.md`).
 *
 * `scripts/website_quality.py` (`favicon_violations`) decodes this URI out of
 * the *built* pages and compares it with the source file, so the icon cannot
 * drift from `src/assets/dhun-favicon.svg`, and no page may keep a
 * `/assets/dhun-favicon.svg` reference behind.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const SOURCE = fileURLToPath(new URL("../assets/dhun-favicon.svg", import.meta.url));

// Whitespace *between tags* only. Every difference a client can see is a
// whitespace run between two `>`/`<` boundaries; the document has no text
// nodes, so no glyph spacing can change. The Python rule normalises the
// source the same way before comparing, so this is asserted, not assumed.
const compact = (svg) => svg.replace(/>\s+</g, "><").trim();

// Base64, not percent-encoding. Measured on this file (2026-10-08, Node
// v22.22.3): the compact SVG is 459 B, so the URI is 638 B base64, 692 B
// percent-encoded with spaces escaped, and 620 B percent-encoded with literal
// spaces — which is not a valid URI and would rely on every client's error
// recovery. Base64 is therefore the smallest *correct* form, and it removes the
// whole class of escaping bugs the file itself demonstrates: `fill="url(#g)"`
// needs `#` escaped or the URI is truncated at the fragment.
const dataUri = `data:image/svg+xml;base64,${Buffer.from(
  compact(readFileSync(SOURCE, "utf8")),
  "utf8",
).toString("base64")}`;

export default {
  /** Where the icon's bytes come from — quoted by the docs, checked by the rule. */
  source: "src/assets/dhun-favicon.svg",
  dataUri,
};
