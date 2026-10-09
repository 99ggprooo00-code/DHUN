/**
 * Browser measurements for the built site — the checks that need a real engine.
 *
 * Everything here exists because the sandbox that builds the site has no
 * browser: responsiveness, contrast, focus and icon rendering were argued from
 * the CSS and asserted structurally by `scripts/website_quality.py`. This script
 * runs on `ubuntu-latest` in `.github/workflows/website.yml` and turns those
 * arguments into numbers read from a real Chromium at six viewport widths, in
 * both colour schemes, with reduced motion emulated. It is deliberately *not*
 * the Playwright test runner: one script that prints every number as a
 * check-run annotation is easier to audit than a runner's report, and the
 * annotations are the only channel this repository can read back.
 *
 * Hard failures (exit 1):
 *   - horizontal overflow, per route per viewport (documentElement.scrollWidth
 *     and every element rect, ignoring content clipped by a scroll container);
 *   - a target under 44×44 CSS px where the target stands alone (WCAG 2.5.5),
 *     or under 24×24 where it does (WCAG 2.5.8);
 *   - the skip link not taking focus first, not becoming visible, or Enter not
 *     moving focus into <main>;
 *   - the primary navigation unreachable by Tab, or *any* Tab stop that shows no
 *     visible change when it takes focus (the ring is not one element's job);
 *   - a heading level skipped in the rendered document (h2 → h4);
 *   - one link text naming two different destinations (WCAG 2.4.4);
 *   - an in-page jump (the skip link's `#main`, a footnote) that lands its target
 *     behind the sticky header, or past the bottom of the viewport;
 *   - any console error, page error, or same-origin 4xx/5xx response;
 *   - rendered contrast below 4.5:1 for body text / 3:1 for large text and
 *     non-text affordances, in `prefers-color-scheme: dark` and `light`;
 *   - a transition or animation longer than 5 ms under
 *     `prefers-reduced-motion: reduce`;
 *   - an `<svg class="i">` that renders no geometry (the sprite safety net);
 *   - a button-shaped link that loses its boundary under
 *     `forced-colors: active` (Windows High Contrast), or an emulated
 *     preference the page cannot see (a vacuous check is a failure here);
 *   - a `data-caveat` disclosure that disappears when the page is printed;
 *   - an axe-core violation of serious or critical impact.
 *
 * The *decisions* above are pure functions in `tests/rules.mjs`, exercised by
 * `tests/rules.test.mjs` under `node --test` in the build job — the half of this
 * file that can be mutation-proven without a browser.
 *
 * What it does NOT claim: anything about browsers other than Chromium, anything
 * about a device, and anything about the deployed URL — that is
 * `scripts/website_smoke.py`'s job, and it can only run where the site is
 * actually served.
 */
import { appendFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { chromium } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

import {
  anchorLandingProblem,
  caveatsHiddenInPrint,
  currentPageProblem,
  markerPerceivable,
  duplicateLinkTargets,
  focusChanged,
  forcedColorsBoundaryMissing,
  headingOrderProblem,
  targetProblem,
} from "./rules.mjs";

import { MESSAGE_BUDGET, clipMessage, packReport, renderAllReports } from "./annotation-report.mjs";

// ---------------------------------------------------------------------------
// immediate evidence channel
// ---------------------------------------------------------------------------
// The summary and the annotations used to be written only at the very end of
// the run. Two consecutive runs (website 37864947840 and 37870564524)
// completed the whole suite — the screenshot artifacts prove it — and then
// the process died before the final emission: zero annotations, an empty
// step summary, and the job log is not retrievable from the sandbox that
// maintains this repository. A dying process must not get to decide what is
// recorded: every finding is now appended to the step summary the moment it
// happens, and process-level handlers put JS-level escapes on the record.
// (An external SIGKILL is still uncatchable; if the summary stops
// mid-run, that is the verdict.)
const SUMMARY = process.env.GITHUB_STEP_SUMMARY || "";
let summaryBroken = false;
function summaryLine(line) {
  if (!SUMMARY || summaryBroken) return;
  try {
    appendFileSync(SUMMARY, `${line}\n`);
  } catch {
    summaryBroken = true;
  }
}
process.on("uncaughtException", (error) => {
  const where = (error && error.stack ? error.stack : String(error)).replace(/\s+/g, " ").slice(0, 800);
  try {
    appendFileSync(SUMMARY, `\n**UNCAUGHT EXCEPTION (process level):** ${where}\n`);
  } catch {
    /* the summary is gone; the lines below are all that remain */
  }
  try {
    annotate("error", "crash", `uncaught exception killed the run: ${where}`);
  } catch {
    /* the annotation channel is gone too */
  }
  console.error(`UNCAUGHT EXCEPTION: ${where}`);
  throw error; // keep the crash semantics; the record above already exists
});
process.on("unhandledRejection", (reason) => {
  const where = (reason && reason.stack ? reason.stack : String(reason)).replace(/\s+/g, " ").slice(0, 800);
  try {
    appendFileSync(SUMMARY, `\n**UNHANDLED REJECTION (process level):** ${where}\n`);
  } catch {
    /* recorded on stderr instead */
  }
  console.error(`UNHANDLED REJECTION: ${where}`);
  // Deliberately not rethrown: the run continues and the rejection is on
  // record. A rejection that would have killed the process is now a finding.
});

const BASE = process.env.SITE_BASE || "http://127.0.0.1:8080";
// The routes are the site's *published* paths: the host serves the site under
// the /DHUN/ sub-path (project page), and the workflow serves the build the
// same way. A measurement taken at the origin root would be a measurement of
// a URL that does not exist (2026-10-09: the root-absolute link bug).
const ROUTES = ["/DHUN/", "/DHUN/features/", "/DHUN/ui/"];

// `touch` marks the viewports where a touch-target floor applies: phones in
// either orientation, plus the fold-class cover screen. A 200 %-zoom layout
// viewport (640×512 — see below) is a desktop pointer context and is not held
// to a touch floor.
const VIEWPORTS = [
  { name: "280x653", width: 280, height: 653, touch: true },
  { name: "320x568", width: 320, height: 568, touch: true },
  { name: "360x800", width: 360, height: 800, touch: true },
  { name: "390x844", width: 390, height: 844, touch: true },
  // Landscape phone: 844×390 is an iPhone-class landscape viewport, and it is
  // the case a portrait-only matrix cannot see — the sticky header, the hero
  // split and the mockups all lay out differently when the viewport is wider
  // than it is tall.
  { name: "844x390-landscape", width: 844, height: 390, touch: true },
  { name: "768x1024", width: 768, height: 1024, touch: true },
  { name: "1280x800", width: 1280, height: 800, touch: false },
  { name: "1440x900", width: 1440, height: 900, touch: false },
  // 200 % zoom, honestly emulated: a 1280×1024 window at 200 % browser zoom
  // lays out in a 640×512 CSS-pixel viewport, which is what WCAG 1.4.4 asks
  // about. `deviceScaleFactor` would not do this — it changes the pixels per
  // CSS pixel, not the layout viewport the CSS sees.
  { name: "zoom200-640x512", width: 640, height: 512, touch: false },
];
const SCHEMES = ["dark", "light"];
const SHOTS = fileURLToPath(new URL("./screenshots/", import.meta.url));

const failures = [];
const measurements = [];
const warnings = [];

function escape(value) {
  return String(value).replace(/%/g, "%25").replace(/\r/g, "%0D").replace(/\n/g, "%0A");
}

function annotate(level, title, message) {
  console.log(`::${level} title=${escape(title)}::${escape(message)}`);
}

// Findings are *collected*, not annotated one by one. GitHub caps the
// annotations the API returns (about ten per level), so a per-viewport
// annotation would push the real numbers out of the only channel this
// environment can read. Everything is printed to stdout in full and the tail
// of this script emits one annotation per category.
// Findings are still *collected* for the packed annotations, but each one is
// also written to the step summary and emitted as an annotation the moment
// it happens. The packed emission at the tail remains the full report; the
// immediate ones exist because the job log is not retrievable from the
// sandbox that maintains this repository, and a process that dies before the
// tail (observed on three consecutive runs, 2026-10-09) must not get to
// decide which problems are on record.
function fail(title, message) {
  const entry = `${title}: ${message}`;
  failures.push(entry);
  summaryLine(`- ${entry}`);
  try {
    annotate("error", "problem", clip(entry, MESSAGE_BUDGET));
  } catch {
    /* the summary line above already recorded it */
  }
}

function record(title, message) {
  const entry = `${title}: ${message}`;
  measurements.push(entry);
  summaryLine(`· ${entry}`);
}

function warn(title, message) {
  const entry = `${title}: ${message}`;
  warnings.push(entry);
  summaryLine(`! ${entry}`);
}

const category = (title) => title.split(/\s+[/@]/)[0].trim() || title;

function group(entries) {
  const grouped = new Map();
  for (const entry of entries) {
    const key = category(entry);
    if (!grouped.has(key)) grouped.set(key, []);
    grouped.get(key).push(entry);
  }
  return grouped;
}

const clip = clipMessage;

/**
 * One annotation per category is not enough and one carry annotation is worse:
 * GitHub clips a message at roughly 4 KB, so the old 24 KB carry silently
 * dropped everything past its first screenful — both axe scans among it. Every
 * message is now packed to a measured budget, the budget is spent across as
 * many annotations as the ~10-per-step cap allows, and whatever still does not
 * fit is *named* by category and count instead of disappearing.
 *
 * The record is the job log and the run summary, not the annotation: the full
 * report is written to both before any annotation is emitted.
 */
function emitReport(grouped, level, label) {
  const { annotations } = packReport([...grouped.entries()], label, { messageBudget: MESSAGE_BUDGET });
  for (const { title, body } of annotations) {
    annotate(level, title, clip(body, MESSAGE_BUDGET));
  }
}

function emitAnnotations() {
  // The full report goes where nothing is capped. Guarded: a failing step must
  // never die because a summary file was unavailable.
  const full = renderAllReports({
    failures: [...group(failures).entries()],
    warnings: [...group(warnings).entries()],
    measurements: [...group(measurements).entries()],
  });
  console.log(full);
  const summaryPath = process.env.GITHUB_STEP_SUMMARY;
  if (summaryPath) {
    try {
      appendFileSync(summaryPath, `${full}\n`);
    } catch (error) {
      warn("job summary unavailable", error instanceof Error ? error.message : String(error));
    }
  }

  if (failures.length) emitReport(group(failures), "error", "problem");
  if (warnings.length) emitReport(group(warnings), "warning", "note");
  // Measurements are the deliverable, not a courtesy: they go through the same
  // packing as findings so none can be dropped on the floor.
  if (measurements.length) emitReport(group(measurements), "notice", "measurement");
}

const url = (route) => `${BASE}${route}`;

// ---------------------------------------------------------------------------
// in-page helpers (serialised into the browser)
// ---------------------------------------------------------------------------

/** Rendered-colour measurement: no token table, no assumptions — what paints. */
function contrastReport() {
  const toRgb = (value) => {
    const match = String(value).match(/rgba?\(([^)]+)\)/);
    if (!match) return null;
    const parts = match[1].split(/[,\s/]+/).filter(Boolean).map(Number);
    if (parts.length < 3 || parts.slice(0, 3).some(Number.isNaN)) return null;
    return { r: parts[0], g: parts[1], b: parts[2], a: parts.length > 3 ? parts[3] : 1 };
  };
  const over = (fg, bg) => ({
    r: fg.r * fg.a + bg.r * (1 - fg.a),
    g: fg.g * fg.a + bg.g * (1 - fg.a),
    b: fg.b * fg.a + bg.b * (1 - fg.a),
    a: 1,
  });
  const luminance = ({ r, g, b }) => {
    const channel = (value) => {
      const v = value / 255;
      return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
    };
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
  };
  const ratio = (a, b) => {
    const [high, low] = [luminance(a), luminance(b)].sort((x, y) => y - x);
    return (high + 0.05) / (low + 0.05);
  };

  // The effective background of an element: every background-color from the
  // document root down, composited in order over the white canvas. Text over a
  // gradient is flagged `approximate` — the gradient's own pixels are not
  // sampled, so those ratios are a lower bound rather than a photograph.
  const backgroundOf = (element) => {
    const chain = [];
    for (let node = element; node && node.nodeType === 1; node = node.parentElement) {
      chain.unshift(node);
    }
    let background = { r: 255, g: 255, b: 255, a: 1 };
    let approximate = false;
    for (const node of chain) {
      const style = getComputedStyle(node);
      if (style.backgroundImage && style.backgroundImage !== "none") approximate = true;
      const colour = toRgb(style.backgroundColor);
      if (colour && colour.a > 0) background = over(colour, background);
    }
    return { background, approximate };
  };

  const describe = (element) => {
    const classes = String(element.className || "")
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .join(".");
    return `${element.tagName.toLowerCase()}${classes ? `.${classes}` : ""}`;
  };

  const results = [];
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    const node = walker.currentNode;
    const text = node.textContent.replace(/\s+/g, " ").trim();
    if (!text) continue;
    const element = node.parentElement;
    if (!element) continue;
    const rect = element.getBoundingClientRect();
    // 1×1 is the visually-hidden pattern; 0×0 is not rendered at all.
    if (rect.width <= 1 || rect.height <= 1) continue;
    const style = getComputedStyle(element);
    if (style.visibility === "hidden" || style.display === "none") continue;
    if (Number(style.opacity) === 0) continue;
    const colour = toRgb(style.color);
    if (!colour) continue;
    const { background, approximate } = backgroundOf(element);
    const foreground = over(colour, background);
    const size = Number.parseFloat(style.fontSize);
    const weight = Number.parseInt(style.fontWeight, 10) || 400;
    const large = size >= 24 || (size >= 18.66 && weight >= 700);
    results.push({
      element: describe(element),
      text: text.slice(0, 48),
      ratio: Math.round(ratio(foreground, background) * 100) / 100,
      required: large ? 3 : 4.5,
      approximate,
    });
  }
  return results;
}

/** Target sizes, with WCAG 2.5.8's inline-in-a-sentence exception applied. */
function targetReport() {
  const results = [];
  // Link rows are laid out as discrete targets even when their markup is
  // inside a list; `footer` is deliberately NOT here, because a footer also
  // contains ordinary prose and a link inside that prose is inline in a
  // sentence (WCAG 2.5.8's inline exception), not a standalone control.
  const standaloneContainers = ".site-nav, .footer-links, .cta-row, .asset-links, nav, .skip-link";
  for (const element of document.querySelectorAll("a, button, [role=\"button\"]")) {
    const rect = element.getBoundingClientRect();
    if (rect.width === 0 && rect.height === 0) continue;
    const style = getComputedStyle(element);
    if (style.display === "none" || style.visibility === "hidden") continue;
    // Inline in a sentence means the *block* the target sits in has other
    // text, not merely its immediate parent: a footnote marker lives in a
    // <sup> of its own inside a <span> that is full of words.
    const block = element.closest("p, li, dd, dt, figcaption, blockquote, td, th, h1, h2, h3, h4, h5, h6, .stat-label, .notice, .risk");
    const blockText = block ? block.textContent.replace(element.textContent, "").trim() : "";
    const inlineInText =
      style.display.startsWith("inline") &&
      blockText.length > 0 &&
      !element.closest(standaloneContainers);
    results.push({
      element: `${element.tagName.toLowerCase()}.${String(element.className || "").split(/\s+/)[0] || "no-class"}`,
      text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 40),
      width: Math.round(rect.width * 10) / 10,
      height: Math.round(rect.height * 10) / 10,
      standalone: !inlineInText,
    });
  }
  return results;
}

function overflowReport() {
  const viewport = window.innerWidth;
  const allowed = viewport + 1;
  const offenders = [];
  for (const element of document.querySelectorAll("body *")) {
    const rect = element.getBoundingClientRect();
    if (rect.width === 0 && rect.height === 0) continue;
    if (rect.right <= allowed && rect.left >= -1) continue;
    // Content inside a scroll container is not viewport overflow: the container
    // clips or scrolls it, which is what the CSS asked for (`pre { overflow-x:
    // auto }`). Everything else is real horizontal overflow.
    let clipped = false;
    for (let node = element.parentElement; node; node = node.parentElement) {
      const overflowX = getComputedStyle(node).overflowX;
      if (overflowX === "auto" || overflowX === "scroll" || overflowX === "hidden") {
        clipped = true;
        break;
      }
    }
    if (clipped) continue;
    offenders.push({
      element: element.tagName.toLowerCase(),
      className: String(element.className || "").slice(0, 60),
      right: Math.round(rect.right),
      left: Math.round(rect.left),
    });
  }
  // The scrollable region can also exceed every border box (a nowrap run of
  // text inside a box that is itself inside the viewport, for instance), which
  // is exactly the case the loop above cannot see. Anything whose own content
  // is wider than its box and which is *not* a scroll container is spilling.
  const spilling = [];
  for (const element of document.querySelectorAll("body *")) {
    const overflowX = getComputedStyle(element).overflowX;
    if (overflowX !== "visible") continue;
    const spill = element.scrollWidth - element.clientWidth;
    if (spill > 1 && element.clientWidth > 0) {
      spilling.push({
        element: element.tagName.toLowerCase(),
        className: String(element.className || "").slice(0, 60),
        text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 40),
        clientWidth: element.clientWidth,
        scrollWidth: element.scrollWidth,
      });
    }
  }
  return {
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
    innerWidth: viewport,
    offenders: offenders.slice(0, 5),
    spilling: spilling
      .sort((a, b) => b.scrollWidth - b.clientWidth - (a.scrollWidth - a.clientWidth))
      .slice(0, 5),
  };
}

// The web mirror's state, gathered in the page (page scope only: this function
// must stay self-contained — see the harness guard in
// scripts/test_website_workflow.py). It gathers; Node decides.
function webAppReport() {
  const state = (selector) => {
    const el = document.querySelector(selector);
    if (!el) return null;
    const rect = el.getBoundingClientRect();
    return { visible: rect.width > 0 && rect.height > 0, text: (el.textContent || "").trim().slice(0, 60) };
  };
  return {
    mounted: (document.querySelector("#app") || { children: [] }).children.length > 0,
    navItems: document.querySelectorAll(".dhun-nav-item").length,
    title: document.title,
    previewNotice: state('[data-testid="preview-notice"]'),
    sampleDataNotice: state('[data-testid="sample-data-notice"]'),
    backendNotice: state('[data-testid="backend-notice"]'),
  };
}

function iconReport() {
  const icons = [...document.querySelectorAll("svg.i")];
  let smallest = Infinity;
  const broken = [];
  for (const svg of icons) {
    const reference = svg.querySelector("use");
    const symbol = reference ? document.getElementById((reference.getAttribute("href") || "").slice(1)) : null;
    if (!symbol) {
      broken.push(`${svg.outerHTML.slice(0, 60)} (no matching <symbol>)`);
      continue;
    }
    let box = null;
    try {
      box = svg.getBBox();
      if (!box.width || !box.height) box = reference.getBBox();
    } catch (error) {
      broken.push(`${svg.outerHTML.slice(0, 60)} (getBBox: ${error.message})`);
      continue;
    }
    if (!box || !box.width || !box.height) {
      broken.push(`${svg.outerHTML.slice(0, 60)} (rendered no geometry)`);
      continue;
    }
    smallest = Math.min(smallest, box.width, box.height);
  }
  return { count: icons.length, smallest: smallest === Infinity ? 0 : Math.round(smallest * 100) / 100, broken };
}

function motionReport() {
  const toMs = (value) => (value.endsWith("ms") ? Number.parseFloat(value) : Number.parseFloat(value) * 1000);
  const longest = [];
  for (const element of document.querySelectorAll("*")) {
    const style = getComputedStyle(element);
    const durations = [
      ...style.transitionDuration.split(","),
      ...style.animationDuration.split(","),
    ].map((value) => toMs(value.trim()));
    const worst = Math.max(...durations.filter((value) => !Number.isNaN(value)), 0);
    if (worst > 5) {
      longest.push({
        element: `${element.tagName.toLowerCase()}.${String(element.className || "").split(/\s+/)[0]}`,
        ms: worst,
      });
    }
  }
  return {
    reduced: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
    offenders: longest.slice(0, 5),
    count: longest.length,
  };
}

/** Rendered heading levels and every link's text → destination, for `rules.mjs`. */
function structureReport() {
  const headings = [];
  for (const element of document.querySelectorAll("h1, h2, h3, h4, h5, h6")) {
    if (element.closest('[aria-hidden="true"]')) continue;
    const rect = element.getBoundingClientRect();
    const style = getComputedStyle(element);
    if (rect.width <= 0 || rect.height <= 0) continue;
    if (style.display === "none" || style.visibility === "hidden") continue;
    headings.push({ level: Number(element.tagName.slice(1)), text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 40) });
  }
  const links = [];
  for (const element of document.querySelectorAll("a[href]")) {
    // An aria-hidden subtree is not read out; its link text cannot confuse
    // anyone. The mockups live there by design.
    if (element.closest('[aria-hidden="true"]')) continue;
    const rect = element.getBoundingClientRect();
    const style = getComputedStyle(element);
    if (rect.width <= 0 || rect.height <= 0) continue;
    if (style.display === "none" || style.visibility === "hidden") continue;
    links.push({ text: element.textContent || "", href: element.getAttribute("href") || "" });
  }
  return { headings, links };
}

/** Controls that must keep a boundary when the OS forces the colour palette. */
function forcedColorsReport() {
  const read = (element) => {
    const style = getComputedStyle(element);
    return {
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
      outlineColor: style.outlineColor,
      borderTopStyle: style.borderTopStyle,
      borderTopWidth: style.borderTopWidth,
      borderTopColor: style.borderTopColor,
      borderRightStyle: style.borderRightStyle,
      borderRightWidth: style.borderRightWidth,
      borderRightColor: style.borderRightColor,
      borderBottomStyle: style.borderBottomStyle,
      borderBottomWidth: style.borderBottomWidth,
      borderBottomColor: style.borderBottomColor,
      borderLeftStyle: style.borderLeftStyle,
      borderLeftWidth: style.borderLeftWidth,
      borderLeftColor: style.borderLeftColor,
    };
  };
  const controls = [];
  for (const element of document.querySelectorAll("a.btn, button")) {
    const rect = element.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) continue;
    controls.push({
      element: `${element.tagName.toLowerCase()}.${String(element.className || "").split(/\s+/)[0]}`,
      text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 24),
      style: read(element),
    });
  }
  // Recorded, not gated: an aria-hidden decoration losing its frame under
  // forced colours is a degradation to report, not an accessibility defect.
  //
  // The raw styles are gathered here and the rule is applied in Node, because
  // this function is serialized into the page: a reference to anything imported
  // from `./rules.mjs` is a ReferenceError *inside the page*, which rejects the
  // evaluate() call and — before the guard below existed — killed the whole run
  // with no annotation, no screenshots and only "Process completed with exit
  // code 1" to show for it (website run 37814413312, 2026-10-08).
  const decorations = [];
  for (const element of document.querySelectorAll(".mock .device")) {
    decorations.push({ element: element.className, style: read(element) });
  }
  // Whether the page's own CSS answers the preference at all. Counted here
  // rather than asserted in Node because the sheet ships inlined (`base.njk`
  // carries exactly one <style>), so the page can answer this about itself —
  // and a count of zero must not be *prose* in the reporter: the sentence
  // "this site declares no prefers-contrast rules" lived here until 2026-10-08
  // and stayed after the sheet gained a block (record 27).
  const prefersContrastRules = [...document.querySelectorAll("style")].filter((sheet) =>
    /@media[^{]*prefers-contrast/.test(sheet.textContent || ""),
  ).length;
  return {
    active: window.matchMedia("(forced-colors: active)").matches,
    contrastMore: window.matchMedia("(prefers-contrast: more)").matches,
    prefersContrastRules,
    controls,
    decorations,
  };
}

/**
 * The link that says "you are here", and how it renders.
 *
 * Self-contained on purpose: this function is serialized into the page, so a
 * reference to anything imported in Node scope is a ReferenceError *inside the
 * page* (see the note in `forcedColorsReport`). It gathers; `rules.mjs` decides.
 */
function currentPageReport() {
  const fields = [
    "color",
    "backgroundColor",
    "textDecorationLine",
    "textDecorationColor",
    "textUnderlineOffset",
    "borderTopStyle",
    "borderTopWidth",
    "borderRightStyle",
    "borderRightWidth",
    "borderBottomStyle",
    "borderBottomWidth",
    "borderLeftStyle",
    "borderLeftWidth",
    "outlineStyle",
    "outlineWidth",
  ];
  const snapshot = (element) => {
    const computed = getComputedStyle(element);
    const picked = {};
    for (const field of fields) picked[field] = computed[field];
    return {
      element: `${element.tagName.toLowerCase()}.${String(element.className || "").split(/\s+/)[0]}`,
      href: element.getAttribute("href"),
      text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 24),
      style: picked,
      signature: fields.map((field) => picked[field]).join("|"),
    };
  };
  const markers = [...document.querySelectorAll('[aria-current="page"]')].map(snapshot);
  const nav = document.querySelector(".site-nav");
  const siblings = nav
    ? [...nav.querySelectorAll("a")].filter((link) => link.getAttribute("aria-current") !== "page")
    : [];
  return {
    forcedColorsActive: window.matchMedia("(forced-colors: active)").matches,
    markers,
    siblings: siblings.map((link) => snapshot(link).signature),
    siblingElements: siblings.map((link) => snapshot(link).element),
  };
}

/** Every `data-caveat` disclosure, and whether print still renders it. */
function caveatReport() {
  const caveats = [];
  for (const element of document.querySelectorAll("[data-caveat]")) {
    const style = getComputedStyle(element);
    const rect = element.getBoundingClientRect();
    caveats.push({
      key: element.getAttribute("data-caveat"),
      visible: style.display !== "none" && style.visibility !== "hidden" && Number(style.opacity) > 0,
      height: Math.round(rect.height),
    });
  }
  return caveats;
}

function focusSnapshot() {
  const element = document.activeElement;
  if (!element) return null;
  const style = getComputedStyle(element);
  const rect = element.getBoundingClientRect();
  return {
    tag: element.tagName.toLowerCase(),
    id: element.id,
    className: String(element.className || ""),
    text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 30),
    outlineStyle: style.outlineStyle,
    outlineWidth: style.outlineWidth,
    top: Math.round(rect.top),
    left: Math.round(rect.left),
    inViewport: rect.top >= -1 && rect.left >= -1 && rect.bottom <= window.innerHeight + 1,
  };
}

// ---------------------------------------------------------------------------
// checks
// ---------------------------------------------------------------------------

async function checkViewports(browser) {
  for (const viewport of VIEWPORTS) {
    const context = await browser.newContext({
      viewport: { width: viewport.width, height: viewport.height },
      colorScheme: "dark",
      reducedMotion: "no-preference",
    });
    for (const route of ROUTES) {
      const page = await context.newPage();
      const consoleErrors = [];
      // Handlers must never throw: an exception here is an uncaughtException
      // in Node, which kills the process before the finally block emits the
      // annotations — the run dies with no readable channel at all.
      page.on("console", (message) => {
        try {
          if (message.type() === "error") consoleErrors.push(String(message.text()).slice(0, 120));
        } catch {
          /* a handler failure is noise, not a finding */
        }
      });
      page.on("pageerror", (error) =>
        consoleErrors.push(`pageerror: ${String((error && error.message) || error).slice(0, 120)}`),
      );
      const badResponses = [];
      page.on("response", (response) => {
        if (response.status() >= 400 && response.url().startsWith(BASE)) {
          badResponses.push(`${response.status()} ${response.url().slice(BASE.length)}`);
        }
      });

      await page.goto(url(route), { waitUntil: "load" });
      const overflow = await page.evaluate(overflowReport);
      if (overflow.scrollWidth > overflow.innerWidth + 1) {
        fail(
          `overflow ${route} @ ${viewport.name}`,
          `documentElement.scrollWidth ${overflow.scrollWidth} > innerWidth ${overflow.innerWidth}`,
        );
      }
      if (overflow.offenders.length) {
        fail(
          `overflow ${route} @ ${viewport.name}`,
          `${overflow.offenders.length} element(s) extend past the viewport: ` +
            overflow.offenders.map((o) => `${o.element}.${o.className} right=${o.right}`).join(", "),
        );
      }
      if (overflow.spilling.length) {
        fail(
          `overflow ${route} @ ${viewport.name}`,
          `content wider than its box: ` +
            overflow.spilling
              .map((o) => `${o.element}.${o.className} “${o.text}” ${o.scrollWidth}>${o.clientWidth}`)
              .join(", "),
        );
      }
      if (consoleErrors.length) {
        fail(`console ${route} @ ${viewport.name}`, consoleErrors.join(" | "));
      }
      if (badResponses.length) {
        fail(`response ${route} @ ${viewport.name}`, badResponses.join(" | "));
      }
      if (route === "/" && viewport.name === "390x844") {
        const icons = await page.evaluate(iconReport);
        if (icons.broken.length) {
          fail("icons do not render", `${route}: ${icons.broken.join(" | ")}`);
        }
        record("icons rendered", `${icons.count} icons, smallest bounding box ${icons.smallest} user units`);
      }
      if (viewport.touch) {
        const targets = await page.evaluate(targetReport);
        const tooSmall = targets
          .map((target) => ({ target, problem: targetProblem(target) }))
          .filter((entry) => entry.problem);
        if (tooSmall.length) {
          fail(
            `touch targets ${route} @ ${viewport.name}`,
            tooSmall
              .map(
                ({ target, problem }) =>
                  `${target.element} “${target.text}” is ${target.width}×${target.height} ` +
                  `(${problem.floor} px floor)`,
              )
              .join(" | "),
          );
        }
        const smallest = targets
          .filter((t) => t.standalone)
          .reduce((min, t) => Math.min(min, t.width, t.height), Infinity);
        if (Number.isFinite(smallest)) {
          record(
            `touch targets ${route} @ ${viewport.name}`,
            `${targets.length} targets, smallest standalone ${Math.round(smallest * 10) / 10} px`,
          );
        }
      }
      await page.close();
    }
    await context.close();
  }
}

async function checkKeyboard(browser) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await context.newPage();
  await page.goto(url("/DHUN/"), { waitUntil: "load" });

  await page.keyboard.press("Tab");
  // The skip link animates in over 120 ms. Measuring inside the first frame
  // reports its hidden position and calls that a failure; wait for it to settle,
  // then measure. If it never settles, the check below fails — which is exactly
  // what it exists to catch.
  await page
    .waitForFunction(
      () => {
        const link = document.querySelector(".skip-link");
        if (!link) return true;
        const rect = link.getBoundingClientRect();
        return rect.top >= -1 && rect.bottom <= window.innerHeight + 1;
      },
      { timeout: 1500 },
    )
    .catch(() => {});
  const skip = await page.evaluate(focusSnapshot);
  if (!skip || !skip.className.includes("skip-link")) {
    fail("skip link is not first", `first Tab landed on ${JSON.stringify(skip)}`);
  } else if (!skip.inViewport) {
    fail("skip link is not visible when focused", `rect top=${skip.top} left=${skip.left}`);
  } else {
    record("skip link", `first Tab focuses the skip link at top=${skip.top}px, visible`);
  }

  await page.keyboard.press("Enter");
  await page.waitForTimeout(150);
  const afterEnter = await page.evaluate(focusSnapshot);
  if (!afterEnter || afterEnter.id !== "main") {
    fail(
      "skip link does not move focus",
      `after Enter, activeElement is ${JSON.stringify(afterEnter)} (expected #main)`,
    );
  } else {
    record("skip link activation", "Enter moves focus to <main id=main>");
  }

  // Fresh load: the primary navigation must be reachable from the top and show
  // a visible focus ring while it is.
  await page.goto(url("/DHUN/"), { waitUntil: "load" });
  let reachedNav = null;
  for (let press = 0; press < 8 && !reachedNav; press += 1) {
    await page.keyboard.press("Tab");
    const insideNav = await page.evaluate(() => Boolean(document.activeElement?.closest(".site-nav")));
    if (insideNav) reachedNav = await page.evaluate(focusSnapshot);
  }
  if (!reachedNav) {
    fail("primary navigation unreachable", "no link inside .site-nav received focus within 8 Tab presses");
  } else if (reachedNav.outlineStyle === "none" || reachedNav.outlineWidth === "0px") {
    fail(
      "focus is not visible",
      `focused nav link “${reachedNav.text}” has outline ${reachedNav.outlineStyle} ${reachedNav.outlineWidth}`,
    );
  } else {
    record(
      "keyboard navigation",
      `primary nav reachable by Tab (“${reachedNav.text}”); focus ring ` +
        `${reachedNav.outlineStyle} ${reachedNav.outlineWidth}`,
    );
  }
  await context.close();
}

/**
 * The current page is marked — and the mark survives Windows High Contrast.
 *
 * Three parts, same as the static rule in `scripts/website_quality.py`: exactly
 * one marker, pointing at this route, and *visible* — a mark that renders
 * identically to the links that are not current tells a sighted visitor nothing.
 * The forced-colours pass is the one only a browser can make: that mode drops
 * author backgrounds, so the filled pill disappears and the underline is what
 * remains.
 */
async function checkCurrentPage(browser) {
  const mostCommon = (values) => {
    const counts = new Map();
    for (const value of values) counts.set(value, (counts.get(value) || 0) + 1);
    let best = null;
    let bestCount = 0;
    for (const [value, count] of counts) {
      if (count > bestCount) {
        best = value;
        bestCount = count;
      }
    }
    return best;
  };

  const context = await browser.newContext({ viewport: { width: 1280, height: 800 }, colorScheme: "dark" });
  for (const route of ROUTES) {
    const page = await context.newPage();
    await page.goto(url(route), { waitUntil: "load" });
    const report = await page.evaluate(currentPageReport);
    const problem = currentPageProblem(route, report.markers, mostCommon(report.siblings));
    if (problem) {
      fail(`current page ${route}`, problem);
    } else {
      const [marker] = report.markers;
      record(
        `current page ${route}`,
        `marked by ${marker.element} “${marker.text}” → ${marker.href}; ` +
          `${report.siblings.length} other nav link(s) (${report.siblingElements.join(", ")}) render differently`,
      );
    }
    await page.close();
  }
  await context.close();

  const forced = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    colorScheme: "dark",
    forcedColors: "active",
  });
  for (const route of ROUTES) {
    const page = await forced.newPage();
    await page.goto(url(route), { waitUntil: "load" });
    const report = await page.evaluate(currentPageReport);
    if (!report.forcedColorsActive) {
      fail(
        `current page ${route} (forced colours)`,
        "the emulated preference is not visible to the page (matchMedia forced-colors=false) — " +
          "every result below would be a false green",
      );
      await page.close();
      continue;
    }
    const [marker] = report.markers;
    if (!marker) {
      fail(`current page ${route} (forced colours)`, "no aria-current marker in the DOM");
    } else if (!markerPerceivable(marker.style)) {
      fail(
        `current page ${route} (forced colours)`,
        `${marker.element} “${marker.text}” loses its mark when the OS picks the colours: ` +
          `text-decoration ${marker.style.textDecorationLine}, border ` +
          `${marker.style.borderBottomStyle} ${marker.style.borderBottomWidth}, outline ` +
          `${marker.style.outlineStyle} ${marker.style.outlineWidth}`,
      );
    } else {
      record(
        `current page ${route} (forced colours)`,
        `${marker.element} keeps its mark (text-decoration ${marker.style.textDecorationLine}) ` +
          `after the background is repainted`,
      );
    }
    await page.close();
  }
  await forced.close();
}

async function checkContrast(browser) {
  for (const scheme of SCHEMES) {
    const context = await browser.newContext({
      viewport: { width: 1280, height: 800 },
      colorScheme: scheme,
    });
    for (const route of ROUTES) {
      const page = await context.newPage();
      await page.goto(url(route), { waitUntil: "load" });
      const results = await page.evaluate(contrastReport);
      const violations = results.filter((r) => r.ratio < r.required - 0.005);
      const worst = results.reduce((min, r) => Math.min(min, r.ratio), Infinity);
      const approximate = results.filter((r) => r.approximate).length;
      record(
        `contrast ${route} (${scheme})`,
        `${results.length} text nodes measured, lowest ratio ${worst}:1, ${approximate} over a gradient ` +
          `(measured against the layered background colour)`,
      );
      if (violations.length) {
        fail(
          `contrast ${route} (${scheme})`,
          violations
            .slice(0, 6)
            .map((v) => `${v.element} “${v.text}” ${v.ratio}:1 < ${v.required}:1`)
            .join(" | ") + (violations.length > 6 ? ` (+${violations.length - 6} more)` : ""),
        );
      }
      await page.close();
    }
    await context.close();
  }
}

async function checkReducedMotion(browser) {
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    reducedMotion: "reduce",
  });
  for (const route of ROUTES) {
    const page = await context.newPage();
    await page.goto(url(route), { waitUntil: "load" });
    const report = await page.evaluate(motionReport);
    if (!report.reduced) {
      fail(`reduced motion ${route}`, "the emulated preference was not visible to the page");
    } else if (report.offenders.length) {
      fail(
        `reduced motion ${route}`,
        report.offenders.map((o) => `${o.element} runs ${o.ms}ms`).join(" | "),
      );
    } else {
      record(`reduced motion ${route}`, "no transition or animation longer than 5 ms");
    }
    await page.close();
  }
  await context.close();
}

/**
 * Rendered structure: heading order and link-text uniqueness.
 *
 * `website_quality.py` already asserts heading order and one `h1` in the *built
 * HTML*. This is the rendered document instead: an element hidden by CSS is not
 * part of the outline a screen reader walks, and an `aria-hidden` subtree is not
 * read at all — two ways the static rule and the real page can disagree.
 */
async function checkStructure(browser) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  for (const route of ROUTES) {
    const page = await context.newPage();
    await page.goto(url(route), { waitUntil: "load" });
    const { headings, links } = await page.evaluate(structureReport);

    const problem = headingOrderProblem(headings.map((heading) => heading.level));
    if (problem) {
      const rendered = headings.map((heading) => `h${heading.level} “${heading.text}”`).join(" → ");
      fail(`heading order ${route}`, `${problem}. Rendered outline: ${rendered}`);
    } else {
      record(
        `headings ${route}`,
        `${headings.length} rendered heading(s), no skipped level: ` +
          headings.map((heading) => `h${heading.level}`).join(" → "),
      );
    }

    const duplicates = duplicateLinkTargets(links);
    if (duplicates.length) {
      fail(`link text ${route}`, `${duplicates.length} text(s) name more than one destination: ${duplicates.join(" | ")}`);
    } else {
      const distinct = new Set(links.map((link) => link.href)).size;
      record(`link text ${route}`, `${links.length} rendered link(s), ${distinct} destination(s), no text reused for two`);
    }
    await page.close();
  }
  await context.close();
}

/**
 * Every tab stop, not just the first one.
 *
 * The previous version checked that the primary navigation was reachable and
 * had a ring. That is one element out of twenty-odd. This walks the whole
 * document with Tab, compares each stop's style with the same element's
 * *unfocused* style (recorded before the walk, so the walk is never interrupted
 * by a blur), and fails on: an element that cannot be reached at all, and an
 * element that takes focus without any visible change.
 */
async function checkTabStops(browser, route = "/DHUN/") {
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await context.newPage();
  await page.goto(url(route), { waitUntil: "load" });

  const probes = await page.evaluate(() => {
    const focusable = "a[href], button:not([disabled]), input:not([disabled]), select, textarea, [tabindex]:not([tabindex=\"-1\"])";
    const found = [];
    for (const element of document.querySelectorAll(focusable)) {
      if (element.closest('[aria-hidden="true"]')) continue;
      const rect = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      if (rect.width <= 0 || rect.height <= 0) continue;
      if (style.display === "none" || style.visibility === "hidden") continue;
      element.dataset.dhunProbe = String(found.length);
      found.push((element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 24));
    }
    return found;
  });

  // The unfocused style of every probe, recorded before the walk: blurring an
  // element mid-walk would move focus and make the walk test its own
  // side-effects instead of the page. The comparison itself is
  // `focusChanged()` in rules.mjs — this file gathers, that file decides.
  const idle = await page.evaluate(() => {
    const read = (style) => ({
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
      outlineColor: style.outlineColor,
      boxShadow: style.boxShadow,
      borderColor: style.borderColor,
    });
    const map = {};
    for (const element of document.querySelectorAll("[data-dhun-probe]")) {
      map[element.dataset.dhunProbe] = read(getComputedStyle(element));
    }
    return map;
  });

  const visited = new Set();
  const noRing = [];
  for (let press = 0; press < probes.length + 8; press += 1) {
    await page.keyboard.press("Tab");
    const stop = await page.evaluate(() => {
      const element = document.activeElement;
      if (!element || element === document.body) return null;
      const style = getComputedStyle(element);
      return {
        key: element.dataset?.dhunProbe ?? null,
        tag: element.tagName.toLowerCase(),
        text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 24),
        style: {
          outlineStyle: style.outlineStyle,
          outlineWidth: style.outlineWidth,
          outlineColor: style.outlineColor,
          boxShadow: style.boxShadow,
          borderColor: style.borderColor,
        },
      };
    });
    if (!stop) break;
    if (stop.key === null) {
      fail(`tab stops ${route}`, `focus left the probed set: ${stop.tag} “${stop.text}”`);
      break;
    }
    if (visited.has(stop.key)) break; // wrapped around to the start: the walk is done
    visited.add(stop.key);
    if (!focusChanged(idle[stop.key], stop.style)) {
      noRing.push(`${stop.tag} “${stop.text}”`);
    }
  }

  const unreachable = probes.filter((_text, index) => !visited.has(String(index)));
  if (unreachable.length) {
    fail(
      `tab stops ${route}`,
      `${unreachable.length} visibly focusable element(s) never took focus: ` +
        unreachable.slice(0, 5).map((text) => `“${text}”`).join(", "),
    );
  }
  if (noRing.length) {
    fail(`focus ring ${route}`, `${noRing.length} tab stop(s) show no visible change when focused: ${noRing.slice(0, 6).join(", ")}`);
  }
  if (!unreachable.length && !noRing.length) {
    record(`focus ring ${route}`, `${visited.size} tab stop(s) visited, every one shows a visible change when focused`);
  }
  await context.close();
}

/**
 * Forced colours and increased contrast, emulated — and the check that the
 * emulation took effect.
 *
 * A check that cannot tell whether its own condition is active proves nothing:
 * if `forced-colors` did not apply, the boundary test below would pass on
 * ordinary dark-mode CSS and report a false green. So the page's own
 * `matchMedia` result is asserted first, and a mismatch is a failure.
 *
 * Under `prefers-contrast: more` the measurement is taken twice — once with the
 * preference on, once in a default context — because a lone number here has
 * nothing to be right or wrong against.
 */
async function checkPreferences(browser) {
  const modes = [
    { name: "forced-colors", options: { forcedColors: "active" }, probe: "active" },
    { name: "prefers-contrast-more", options: { contrast: "more" }, probe: "contrastMore" },
  ];
  for (const mode of modes) {
    const context = await browser.newContext({
      viewport: { width: 1280, height: 800 },
      colorScheme: "dark",
      ...mode.options,
    });
    for (const route of ROUTES) {
      const page = await context.newPage();
      await page.goto(url(route), { waitUntil: "load" });
      const report = await page.evaluate(forcedColorsReport);
      if (!report[mode.probe]) {
        fail(
          `${mode.name} ${route}`,
          `the emulated preference is not visible to the page (matchMedia ${mode.probe}=false) — ` +
            `every result below would be a false green`,
        );
        await page.close();
        continue;
      }

      const overflow = await page.evaluate(overflowReport);
      if (overflow.scrollWidth > overflow.innerWidth + 1 || overflow.offenders.length || overflow.spilling.length) {
        fail(
          `overflow ${route} @ ${mode.name}`,
          `scrollWidth ${overflow.scrollWidth} > ${overflow.innerWidth}; ` +
            `${overflow.offenders.length} offender(s), ${overflow.spilling.length} spilling`,
        );
      }

      if (mode.name === "forced-colors") {
        const missing = report.controls.filter((control) =>
          forcedColorsBoundaryMissing(control.style),
        );
        if (missing.length) {
          fail(
            `forced colors ${route}`,
            `${missing.length}/${report.controls.length} control(s) have no visible boundary when ` +
              `colours are forced: ${missing.slice(0, 5).map((c) => `${c.element} “${c.text}”`).join(", ")}`,
          );
        } else {
          record(
            `forced colors ${route}`,
            `${report.controls.length} control(s) keep a boundary; ` +
              `${report.decorations.filter((d) => forcedColorsBoundaryMissing(d.style)).length}/${report.decorations.length} ` +
              `decorative mockup frame(s) lose theirs`,
          );
        }
      } else {
        const results = await page.evaluate(contrastReport);
        const worst = results.reduce((min, r) => Math.min(min, r.ratio), Infinity);
        // The same measurement with the preference *unset*, in its own context, so
        // the number above is compared with something instead of described. Until
        // this build the branch printed "this site declares no prefers-contrast
        // rules, so the number is the same as the default scheme by design" — true
        // when written, false the moment the sheet gained a block, and exactly the
        // kind of stale prose a measurement should replace (record 27).
        //
        // The assertion is one-directional on purpose: a preference that asks for
        // *more* contrast may never leave a text node worse off than the default
        // ladder, but "strictly better" would depend on which token the worst node
        // happens to use, which this reporter does not know.
        const baselineContext = await browser.newContext({
          viewport: { width: 1280, height: 800 },
          colorScheme: "dark",
        });
        const baselinePage = await baselineContext.newPage();
        await baselinePage.goto(url(route), { waitUntil: "load" });
        const baseline = await baselinePage.evaluate(contrastReport);
        await baselineContext.close();
        const baselineWorst = baseline.reduce((min, r) => Math.min(min, r.ratio), Infinity);
        if (worst < baselineWorst - 0.005) {
          fail(
            `contrast ${route} (prefers-contrast: more)`,
            `lowest ratio ${worst}:1 is below the default scheme's ${baselineWorst}:1 — asking ` +
              `for more contrast made the page worse`,
          );
        } else {
          record(
            `contrast ${route} (prefers-contrast: more)`,
            `${results.length} text node(s) measured, lowest ratio ${worst}:1 against ` +
              `${baselineWorst}:1 with the preference unset; the page's own CSS carries ` +
              `${report.prefersContrastRules} prefers-contrast block(s)`,
          );
        }
      }

      const results = await new AxeBuilder({ page }).analyze();
      const serious = results.violations.filter((v) => ["serious", "critical"].includes(v.impact));
      if (serious.length) {
        fail(
          `axe ${route} (${mode.name})`,
          serious.map((v) => `${v.id} (${v.impact}, ${v.nodes.length} node(s))`).join(", "),
        );
      } else {
        record(
          `axe ${route} (${mode.name})`,
          `${results.violations.length} violation(s), ${results.passes.length} passes`,
        );
      }
      await page.close();
    }
    await context.close();
  }
}

/**
 * Print: the disclosures must survive the rendering path nobody looks at.
 *
 * `data-caveat` elements are the site's own honesty device — the same hook
 * `scripts/website_claims.py` asserts in the built HTML. They are checked here
 * in the print media, because a reader can print the page and the printed copy
 * is what someone else reads.
 */
/**
 * In-page jumps land on their target, below the sticky header.
 *
 * Two failure modes that no static check can see: the header is `position:
 * sticky`, so a fragment jump can leave the target's first line behind it (the
 * skip link and the footnote links are the only in-page jumps today), and a page
 * padded past a short target can scroll it off the bottom. The page functions
 * below are self-contained because `page.evaluate` serialises them into the page.
 */
async function checkAnchorLanding(browser) {
  // 280×653 is the smallest display class the site supports (Galaxy Fold cover
  // screen) and the width where the navigation itself can wrap, making the header
  // three rows; 380×800 is the ordinary phone width, where it is two.
  const cases = [
    { name: "1280×800", viewport: { width: 1280, height: 800 } },
    { name: "380×800 (header wraps to two rows)", viewport: { width: 380, height: 800 } },
    { name: "280×653 (navigation can wrap too)", viewport: { width: 280, height: 653 } },
  ];
  for (const testCase of cases) {
    const context = await browser.newContext({
      viewport: testCase.viewport,
      colorScheme: "dark",
    });
    for (const route of ROUTES) {
      const page = await context.newPage();
      await page.goto(url(route), { waitUntil: "load" });
      const hashes = await page.evaluate(inPageHashes);
      if (!hashes.length) {
        record(`anchors ${route} @ ${testCase.name}`, "no in-page anchors");
        await page.close();
        continue;
      }
      for (const hash of hashes) {
        const metrics = await page.evaluate(landingReport, hash);
        const problem = anchorLandingProblem({
          hash,
          viewportHeight: testCase.viewport.height,
          ...metrics,
        });
        if (problem) {
          fail(`anchors ${route} @ ${testCase.name}`, problem);
        } else {
          record(
            `anchors ${route} @ ${testCase.name}`,
            `#${hash} lands at ${Math.round(metrics.targetTop)}px, clear of the header ` +
              `bottom at ${Math.round(metrics.headerBottom)}px ` +
              `(scroll-padding-top ${Math.round(metrics.scrollPaddingTop)}px)`,
          );
        }
      }
      await page.close();
    }
    await context.close();
  }
}

function inPageHashes() {
  const hashes = new Set();
  for (const link of document.querySelectorAll("a[href^='#']")) {
    const hash = (link.getAttribute("href") || "").slice(1);
    if (hash) hashes.add(hash);
  }
  return [...hashes];
}

function landingReport(hash) {
  return new Promise((resolve) => {
    const target = document.getElementById(hash);
    if (!target) {
      resolve({ targetTop: Number.NaN, headerBottom: Number.NaN });
      return;
    }
    window.scrollTo(0, 0);
    window.location.hash = hash;
    let frames = 0;
    const measure = () => {
      if (++frames < 6) {
        window.requestAnimationFrame(measure);
        return;
      }
      const header = document.querySelector(".site-header");
      resolve({
        targetTop: target.getBoundingClientRect().top,
        headerBottom: header ? header.getBoundingClientRect().bottom : 0,
        scrollPaddingTop: Number.parseFloat(
          window.getComputedStyle(document.documentElement).scrollPaddingTop,
        ),
      });
    };
    window.requestAnimationFrame(measure);
  });
}

async function checkPrint(browser) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  for (const route of ROUTES) {
    const page = await context.newPage();
    await page.goto(url(route), { waitUntil: "load" });
    await page.emulateMedia({ media: "print" });
    const caveats = await page.evaluate(caveatReport);
    const hidden = caveatsHiddenInPrint(caveats);
    if (hidden.length) {
      fail(
        `print ${route}`,
        `${hidden.length} data-caveat disclosure(s) do not render in print: ${hidden.join(", ")}`,
      );
    } else if (caveats.length) {
      record(`print ${route}`, `${caveats.length} data-caveat disclosure(s) still rendered in print`);
    } else {
      record(`print ${route}`, "no data-caveat disclosure on this route");
    }

    // Print is a rendering path, not a colour scheme: browsers drop background
    // colours on paper, so a dark-themed page with no print rules prints white
    // text on white paper — legible on screen, blank on paper. Same floors as
    // the screen measurement, measured through the same code.
    const contrast = await page.evaluate(contrastReport);
    const violations = contrast.filter((entry) => entry.ratio < entry.required - 0.005);
    const worst = contrast.reduce((min, entry) => Math.min(min, entry.ratio), Infinity);
    if (violations.length) {
      fail(
        `print contrast ${route}`,
        `${violations.length} of ${contrast.length} text node(s) would print below the floor ` +
          `(lowest ${worst}:1): ` +
          violations
            .slice(0, 5)
            .map((entry) => `${entry.element} “${entry.text}” ${entry.ratio}:1 < ${entry.required}:1`)
            .join(" | "),
      );
    } else {
      record(
        `print contrast ${route}`,
        `${contrast.length} text nodes measured on paper, lowest ratio ${worst}:1`,
      );
    }
    await page.close();
  }
  await context.close();
}

// The web mirror, deployed at /app/ under the same published path (ADR-008
// amendment 2026-10-09 (2)). Its gates are deliberately narrower than the
// marketing site's — it is a JavaScript application, not a static document,
// and it is noindex: it must boot, it must say what it is (the engineering-
// preview notice is honesty, not decoration), nothing unhandled may throw,
// and it must not overflow. Which catalogue answered (live or sample) is
// recorded, not gated: on a runner with no upstream, the app falls back and
// says so, and that fallback is the correct behaviour.
const NETWORK_NOISE = /Failed to load resource|net::ERR_|ERR_NAME_NOT_RESOLVED/i;
async function checkWebApp(browser) {
  const viewports = [
    { name: "desktop", width: 1280, height: 800 },
    { name: "phone", width: 390, height: 844 },
  ];
  for (const scheme of SCHEMES) {
    for (const viewport of viewports) {
      const context = await browser.newContext({
        viewport: { width: viewport.width, height: viewport.height },
        colorScheme: scheme,
      });
      const page = await context.newPage();
      // Handlers must never throw: an exception here is an uncaughtException
      // in Node, which kills the process before the finally block emits the
      // annotations — the run dies with no readable channel at all.
      const consoleErrors = [];
      page.on("console", (message) => {
        try {
          if (message.type() === "error" && !NETWORK_NOISE.test(message.text())) {
            consoleErrors.push(String(message.text()).slice(0, 120));
          }
        } catch {
          /* a handler failure is noise, not a finding */
        }
      });
      const pageErrors = [];
      page.on("pageerror", (error) =>
        pageErrors.push(String((error && error.message) || error).slice(0, 120)),
      );

      await page.goto(url("/DHUN/app/"), { waitUntil: "load" });
      const report = await page.evaluate(webAppReport);
      const overflow = await page.evaluate(overflowReport);
      const label = `/DHUN/app/ (${scheme}, ${viewport.name})`;

      if (!report.mounted) {
        fail(`web app ${label}`, "the application did not render: #app is empty");
      } else {
        const source = report.sampleDataNotice
          ? "sample catalogue (the live source did not answer, and the page says so)"
          : "live source answered";
        record(
          `web app ${label}`,
          `mounted, ${report.navItems} nav item(s), ${report.title ? report.title : "no title"}, ${source}` +
            (report.backendNotice ? `; backend notice: ${report.backendNotice.text}` : ""),
        );
      }
      if (!report.previewNotice || !report.previewNotice.text) {
        fail(`web app ${label}`, "the engineering-preview notice is absent — the mirror may be claiming more than it proves");
      }
      if (report.navItems < 3) {
        fail(`web app ${label}`, `only ${report.navItems} nav item(s); the shell mirrors Home/Search/Library(+Settings)`);
      }
      if (pageErrors.length) {
        fail(`web app ${label} unhandled`, pageErrors.join(" | "));
      }
      if (consoleErrors.length) {
        fail(`web app ${label} console`, consoleErrors.join(" | "));
      }
      if (overflow.scrollWidth > overflow.innerWidth + 1) {
        fail(`web app ${label} overflow`, `scrollWidth ${overflow.scrollWidth} > innerWidth ${overflow.innerWidth}`);
      }
      if (overflow.offenders.length || overflow.spilling.length) {
        fail(
          `web app ${label} overflow`,
          [
            ...overflow.offenders.map((o) => `${o.element}.${o.className} right=${o.right}`),
            ...overflow.spilling.map((o) => `${o.element}.${o.className} spilling`),
          ].join(", "),
        );
      }
      if (scheme === "dark") {
        await page.screenshot({ path: `${SHOTS}webapp-${viewport.name}-${scheme}.png`, fullPage: true });
      }
      await page.close();
      await context.close();
    }
  }
}

async function checkAxe(browser) {
  for (const scheme of SCHEMES) {
    const context = await browser.newContext({
      viewport: { width: 1280, height: 800 },
      colorScheme: scheme,
    });
    for (const route of ROUTES) {
      const page = await context.newPage();
      await page.goto(url(route), { waitUntil: "load" });
      const results = await new AxeBuilder({ page }).analyze();
      const serious = results.violations.filter((v) => ["serious", "critical"].includes(v.impact));
      const other = results.violations.filter((v) => !["serious", "critical"].includes(v.impact));
      const summary = (
        list,
      ) => list.map((v) => `${v.id} (${v.impact}, ${v.nodes.length} node(s))`).join(", ");
      record(
        `axe ${route} (${scheme})`,
        `${results.violations.length} violation(s), ${results.passes.length} passes, ` +
          `${results.incomplete.length} incomplete` +
          (results.violations.length ? `: ${summary(results.violations)}` : ""),
      );
      if (serious.length) {
        fail(`axe ${route} (${scheme})`, `serious/critical: ${summary(serious)}`);
      }
      if (other.length) {
        warn(`axe ${route} (${scheme}) minor`, summary(other));
      }
      await page.close();
    }
    await context.close();
  }
}

async function captureScreenshots(browser) {
  mkdirSync(SHOTS, { recursive: true });
  const shots = [
    { viewport: { width: 390, height: 844 }, label: "phone" },
    { viewport: { width: 1440, height: 900 }, label: "desktop" },
  ];
  for (const scheme of SCHEMES) {
    for (const shot of shots) {
      const context = await browser.newContext({ viewport: shot.viewport, colorScheme: scheme });
      for (const route of ROUTES) {
        const page = await context.newPage();
        await page.goto(url(route), { waitUntil: "load" });
        const name = `${route === "/DHUN/" ? "home" : route.replace(/\//g, "")}-${shot.label}-${scheme}.png`;
        await page.screenshot({ path: `${SHOTS}${name}`, fullPage: true });
        await page.close();
      }
      await context.close();
    }
  }
  record("screenshots", "full-page captures written to tests/screenshots (CI artifact, never committed)");
}

// ---------------------------------------------------------------------------
// run
// ---------------------------------------------------------------------------

// Every check runs through the guard. A thrown error used to end the process
// before `emitAnnotations()` ran, so the only trace in the readable channel was
// "Process completed with exit code 1" — no route, no viewport, no message. A
// crash is a finding like any other: it is recorded, annotated, and the run
// continues to the next check, because a broken tab walk must not hide the
// contrast measurement behind it.
async function guard(name, run) {
  try {
    await run();
  } catch (error) {
    const where = (error && error.stack ? error.stack.split("\n").slice(0, 3).join(" | ") : String(error))
      .replace(/\s+/g, " ")
      .slice(0, 400);
    fail(`${name} crashed`, `${error && error.message ? error.message : error} — ${where}`);
  }
}

const CHECKS = [
  ["viewports", () => checkViewports(browser)],
  ["keyboard", () => checkKeyboard(browser)],
  ["structure", () => checkStructure(browser)],
  ["tab stops", () => checkTabStops(browser)],
  ["current page", () => checkCurrentPage(browser)],
  ["anchor landings", () => checkAnchorLanding(browser)],
  ["contrast", () => checkContrast(browser)],
  ["reduced motion", () => checkReducedMotion(browser)],
  ["preferences", () => checkPreferences(browser)],
  ["print", () => checkPrint(browser)],
  ["axe", () => checkAxe(browser)],
  ["web app", () => checkWebApp(browser)],
  ["screenshots", () => captureScreenshots(browser)],
];

let browser;
try {
  browser = await chromium.launch();
  summaryLine(`\n## Browser measurements — progress\n`);
  for (const [name, run] of CHECKS) {
    summaryLine(`- check started: **${name}**`);
    await guard(name, run);
    summaryLine(`- check finished: ${name}`);
    // One short progress annotation per check. The API view caps annotations
    // per level, so these are deliberately minimal: they exist to answer
    // "how far did the run get?" when the process dies before the packed
    // report is emitted (three consecutive runs did, 2026-10-09).
    annotate("notice", "progress", `check finished: ${name} (${failures.length} problem(s) so far)`);
  }
  summaryLine(`\nAll ${CHECKS.length} checks finished; emitting the packed report.\n`);
  annotate("notice", "progress", `all ${CHECKS.length} checks finished — emitting the packed report`);
} catch (error) {
  fail("browser launch", `${error && error.message ? error.message : error}`);
} finally {
  // Closing the browser can itself reject (the process dies mid-teardown and
  // the protocol errors on close). That used to escape the finally block and
  // skip emitAnnotations() entirely — a full run of measurements reduced to
  // "exit code 1" with no annotation and no summary (observed in website run
  // 37864947840). A teardown error is a finding, not a reason to lose the
  // evidence: record it and still emit.
  if (browser) {
    try {
      await browser.close();
    } catch (error) {
      fail("browser teardown", `${error && error.message ? error.message : error}`);
    }
  }
  // The summary and the annotations are emitted even when a check above threw:
  // the annotations are the only channel this repository can read from CI, so
  // losing them is losing the evidence itself. The stdout writes are guarded
  // too: if the runner's log pipe is already gone, emitAnnotations() must
  // still run, because the summary file (written as findings happen) and the
  // annotations are what remain.
  try {
    console.log(`\n${measurements.length} measurement(s), ${warnings.length} note(s), ${failures.length} failure(s).`);
    console.error(measurements.map((line) => `  · ${line}`).join("\n"));
    if (warnings.length) console.error(warnings.map((line) => `  ! ${line}`).join("\n"));
  } catch {
    /* stdout is gone; the summary and the annotations carry the record */
  }
  try {
    emitAnnotations();
  } catch (error) {
    // The renderer must not be able to eat the evidence either: fall back to
    // raw lines, one annotation each, no packing.
    for (const line of [...failures, ...warnings, ...measurements].slice(0, 20)) {
      annotate("error", "report", line);
    }
    annotate("error", "report", `the report renderer itself failed: ${error && error.message ? error.message : error}`);
  }
  if (failures.length) {
    console.error(failures.map((line) => `  - ${line}`).join("\n"));
    process.exitCode = 1;
  }
  // The last thing the script ever writes: without this marker in the
  // annotations, a run that reached the end of the script and then died is
  // indistinguishable from one that never got here.
  annotate(
    "notice",
    "progress",
    `script finished cleanly: ${measurements.length} measurement(s), ${warnings.length} note(s), ${failures.length} failure(s), exit ${process.exitCode ?? 0}`,
  );
}
