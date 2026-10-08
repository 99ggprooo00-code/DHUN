/**
 * Pure decision logic for the browser checks.
 *
 * `browser.mjs` can only run where a browser exists — `ubuntu-latest` in
 * `.github/workflows/website.yml`. That makes every rule inside it
 * unmutation-provable from the environment this repository is maintained in,
 * which is exactly the state the audit rules were in before they were split
 * out: a check nobody can break on purpose is a check nobody can trust.
 *
 * So the *decisions* live here as pure functions over plain data — no DOM, no
 * Playwright, no globals — and `rules.test.mjs` exercises each one with input
 * that must pass and input that must fail, running under `node --test` in the
 * build job (Node, no browser, no network). `browser.mjs` then does nothing but
 * gather the data and hand it over.
 */

/** WCAG 2.5.5 / 2.5.8 target floors, in CSS pixels. */
export const TARGET_FLOOR = 44;
export const INLINE_TARGET_FLOOR = 24;

/**
 * Which floor applies, and whether this target breaks it.
 *
 * A standalone control needs 44×44 (2.5.5, the site's own `--target` token). A
 * target that sits inline inside a sentence is exempt from that under 2.5.8 and
 * only fails when it is smaller than 24 in *both* dimensions — a one-line-tall
 * footnote marker inside a paragraph is not a defect.
 */
export function targetProblem({ width, height, standalone }) {
  const floor = standalone ? TARGET_FLOOR : INLINE_TARGET_FLOOR;
  const fails = standalone
    ? width < floor || height < floor
    : width < floor && height < floor;
  return fails ? { floor } : null;
}

/**
 * The first heading level that skips a level, or null.
 *
 * `h2 → h4` is the defect (a screen-reader user hears a level that does not
 * exist); `h4 → h2` going back up is not, and `h1` twice is a separate rule
 * (`website_quality.py` asserts exactly one `h1` per page in the built HTML).
 */
export function headingOrderProblem(levels) {
  for (let index = 1; index < levels.length; index += 1) {
    const previous = levels[index - 1];
    const current = levels[index];
    if (current > previous + 1) {
      return `h${previous} is followed by h${current} (level ${current - previous - 1} skipped)`;
    }
  }
  return null;
}

/**
 * Link texts that name more than one destination.
 *
 * "Read more" three times pointing at three pages is a screen-reader problem
 * (WCAG 2.4.4, link purpose in context is not enough when a list of links is
 * read out of context). The same text pointing at the *same* destination — the
 * primary CTA repeated at the top and the bottom of a page — is fine and
 * deliberate.
 */
export function duplicateLinkTargets(links) {
  const byText = new Map();
  for (const { text, href } of links) {
    const key = text.replace(/\s+/g, " ").trim().toLowerCase();
    if (!key) continue;
    if (!byText.has(key)) byText.set(key, new Set());
    byText.get(key).add(href);
  }
  const offenders = [];
  for (const [text, hrefs] of byText) {
    if (hrefs.size > 1) offenders.push(`“${text}” → ${[...hrefs].sort().join(" and ")}`);
  }
  return offenders;
}

/** The parts of a computed style that can make keyboard focus visible. */
export function focusSignature(style) {
  return [
    style.outlineStyle,
    style.outlineWidth,
    style.outlineColor,
    style.boxShadow,
    style.borderColor,
  ].join("|");
}

/**
 * Whether focusing an element changed how it renders.
 *
 * Comparing the focused and unfocused signatures, rather than demanding one
 * particular property, is deliberate: the site draws its ring with `outline`,
 * a future refactor could draw it with `box-shadow`, and a rule that hard-coded
 * `outline` would call a better implementation a failure.
 */
export function focusChanged(before, after) {
  return focusSignature(before) !== focusSignature(after);
}

/**
 * Whether a control still has a visible boundary under `forced-colors: active`.
 *
 * Windows High Contrast removes author backgrounds and gradients, so a control
 * whose only affordance was a filled background becomes indistinguishable from
 * the page. In that mode Chromium does not add a border to an `<a>` styled as a
 * button (it does for `<button>`), so the site needs an explicit
 * `@media (forced-colors: active)` border. This test is what keeps that true.
 */
export function forcedColorsBoundaryMissing(style) {
  const width = (value) => Number.parseFloat(value) || 0;
  // A `transparent` border is not a boundary, and this is the whole trap: the
  // site's `.btn` carries `border: 1px solid transparent`, so a check that only
  // looked at style/width would call every button visible while Windows High
  // Contrast paints the page with nothing to see. Chromium keeps a specified
  // `transparent` transparent under forced colours, so the colour is part of
  // the measurement, not a detail.
  const invisible = (value) => {
    if (!value) return true;
    const match = String(value).match(/rgba?\(([^)]+)\)/i);
    if (!match) return false; // a system colour keyword: assume it paints
    const parts = match[1].split(/[,\s/]+/).filter(Boolean);
    return parts.length > 3 && Number(parts[3]) === 0;
  };
  const borderVisible = ["Top", "Right", "Bottom", "Left"].some(
    (side) =>
      style[`border${side}Style`] !== "none" &&
      width(style[`border${side}Width`]) > 0 &&
      !invisible(style[`border${side}Color`]),
  );
  const outlineVisible =
    style.outlineStyle !== "none" &&
    width(style.outlineWidth) > 0 &&
    !invisible(style.outlineColor);
  return !(borderVisible || outlineVisible);
}

/**
 * Caveats that vanish when the page is printed.
 *
 * The site's whole honesty device is that three disclosures are visible on the
 * page they qualify (`data-caveat` in the templates, asserted in the built HTML
 * by `website_claims.py`). Print is a rendering path nobody looks at until a
 * reader prints the page and hands it to someone else, so the disclosures must
 * survive it. Elements are passed as `{ key, visible, height }`.
 */
export function caveatsHiddenInPrint(caveats) {
  return caveats
    .filter((caveat) => !caveat.visible || caveat.height <= 0)
    .map((caveat) => caveat.key);
}

/**
 * The link that marks the page the visitor is on.
 *
 * Three things have to be true, and only the last one needs a browser:
 * exactly one marker on the page; the marked link points at the page being
 * rendered (a marker on the wrong link is a lie about where the visitor is);
 * and the marker renders differently from the links that are *not* current —
 * otherwise the attribute is a machine-only fact and a sighted visitor cannot
 * tell where they are. `markers` are `{ href, tag, className, text, signature }`
 * gathered from the DOM; `siblingSignature` is the most common signature among
 * the other links in the same navigation, or null when there are none.
 */
export function currentPageProblem(route, markers, siblingSignature) {
  if (!Array.isArray(markers) || markers.length !== 1) {
    const count = Array.isArray(markers) ? markers.length : 0;
    return `expected exactly one aria-current="page", found ${count}`;
  }
  const [marker] = markers;
  if (marker.href !== route) {
    return `the current-page marker points at ${marker.href || "nothing"}, not at ${route}`;
  }
  if (siblingSignature && marker.signature === siblingSignature) {
    return (
      `the current link renders exactly like the non-current ones ` +
      `(${marker.signature}) — the mark is invisible`
    );
  }
  return null;
}

/**
 * Whether a mark still reads when the OS picks the colours.
 *
 * `forced-colors: active` (Windows High Contrast) repaints the page with the
 * system palette and *drops author backgrounds*, so a filled pill is gone: only
 * a decoration (underline / strike) or a frame (border / outline) survives.
 * Colour is deliberately not part of this: every author colour is repainted.
 */
export function markerPerceivable(style) {
  if (!style) return false;
  const width = (value) => Number.parseFloat(value) || 0;
  if (style.textDecorationLine && style.textDecorationLine !== "none") return true;
  const border = ["Top", "Right", "Bottom", "Left"].some(
    (side) =>
      style[`border${side}Style`] !== "none" && width(style[`border${side}Width`]) > 0,
  );
  if (border) return true;
  return style.outlineStyle !== "none" && width(style.outlineWidth) > 0;
}


/**
 * Whether a jump landed where a visitor can actually read it.
 *
 * The header is sticky, so a target whose top edge is above the header's bottom
 * edge is partly covered; and a target scrolled past the bottom of the viewport
 * has not landed at all (the scrollport was padded past it, usually on a short
 * page). `metrics` is measured by a real engine in CSS pixels: the target's top
 * edge, the header's bottom edge, and the viewport height. Returns a message or
 * null — the same shape as the other rules here.
 */
export function anchorLandingProblem({
  hash,
  targetTop,
  headerBottom,
  viewportHeight,
  scrollPaddingTop,
}) {
  if (!Number.isFinite(targetTop) || !Number.isFinite(headerBottom)) {
    return `#${hash} is not in the document after the jump, or has no header to measure against`;
  }
  if (targetTop < headerBottom - 1) {
    const hidden = Math.round(headerBottom - targetTop);
    const padding = Number.isFinite(scrollPaddingTop)
      ? `, scroll-padding-top ${Math.round(scrollPaddingTop)}px`
      : "";
    return (
      `#${hash} lands ${hidden}px behind the sticky header (top ${Math.round(targetTop)}px, ` +
      `header bottom ${Math.round(headerBottom)}px${padding}) — its first line is covered`
    );
  }
  if (targetTop > viewportHeight - 1) {
    const overshoot = Math.round(targetTop - viewportHeight);
    const padding = Number.isFinite(scrollPaddingTop)
      ? ` (scroll-padding-top ${Math.round(scrollPaddingTop)}px)`
      : "";
    return (
      `#${hash} sits ${overshoot}px below the bottom of the viewport after the jump — the ` +
      `scroll overshot the target${padding}`
    );
  }
  return null;
}
