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
 *   - the primary navigation unreachable by Tab, or focused without a visible
 *     outline;
 *   - any console error, page error, or same-origin 4xx/5xx response;
 *   - rendered contrast below 4.5:1 for body text / 3:1 for large text and
 *     non-text affordances, in `prefers-color-scheme: dark` and `light`;
 *   - a transition or animation longer than 5 ms under
 *     `prefers-reduced-motion: reduce`;
 *   - an `<svg class="i">` that renders no geometry (the sprite safety net);
 *   - an axe-core violation of serious or critical impact.
 *
 * What it does NOT claim: anything about browsers other than Chromium, anything
 * about a device, and anything about the deployed URL — that is
 * `scripts/website_smoke.py`'s job, and it can only run where the site is
 * actually served.
 */
import { mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { chromium } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const BASE = process.env.SITE_BASE || "http://127.0.0.1:8080";
const ROUTES = ["/", "/features/", "/ui/"];
const VIEWPORTS = [
  { name: "320x568", width: 320, height: 568 },
  { name: "360x800", width: 360, height: 800 },
  { name: "390x844", width: 390, height: 844 },
  { name: "768x1024", width: 768, height: 1024 },
  { name: "1280x800", width: 1280, height: 800 },
  { name: "1440x900", width: 1440, height: 900 },
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
function fail(title, message) {
  failures.push(`${title}: ${message}`);
}

function record(title, message) {
  measurements.push(`${title}: ${message}`);
}

function warn(title, message) {
  warnings.push(`${title}: ${message}`);
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

const clip = (text, limit = 4000) => (text.length > limit ? `${text.slice(0, limit)} …(truncated)` : text);

// GitHub's annotation API is the only channel this environment can substitute a
// browser for, and it returns annotations per level with a practical cap around
// ten. So: one annotation per category, at most eight, and a ninth that carries
// *everything* suppressed rather than a count of it. Five notices were silently
// lost to an earlier six-notice cap — including every axe scan — so a dropped
// measurement is a bug in this file, not a display detail.
const ANNOTATION_CAP = 8;

function emitReport(grouped, level, label) {
  const entries = [...grouped.entries()];
  for (const [key, items] of entries.slice(0, ANNOTATION_CAP)) {
    annotate(level, `${key} — ${items.length} ${label}(s)`, clip(items.join(" || ")));
  }
  const rest = entries.slice(ANNOTATION_CAP);
  if (rest.length) {
    const suppressed = rest.flatMap(([, items]) => items);
    annotate(
      level,
      `${rest.length} more categor(y|ies) — ${suppressed.length} ${label}(s)`,
      clip(suppressed.join(" || ")),
    );
  }
}

function emitAnnotations() {
  if (failures.length) emitReport(group(failures), "error", "problem");
  if (warnings.length) emitReport(group(warnings), "warning", "note");
  // Measurements are the deliverable, not a courtesy: they go through the same
  // cap-and-carry path as findings so that none can be dropped on the floor.
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
      page.on("console", (message) => {
        if (message.type() === "error") consoleErrors.push(message.text().slice(0, 120));
      });
      page.on("pageerror", (error) => consoleErrors.push(`pageerror: ${error.message.slice(0, 120)}`));
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
              .map((o) => `${o.element}.${o.className} ${o.scrollWidth}>${o.clientWidth}`)
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
      if (viewport.width <= 768) {
        const targets = await page.evaluate(targetReport);
        const tooSmall = targets.filter(
          (t) => (t.standalone && (t.width < 44 || t.height < 44)) || (!t.standalone && (t.width < 24 && t.height < 24)),
        );
        if (tooSmall.length) {
          fail(
            `touch targets ${route} @ ${viewport.name}`,
            tooSmall
              .map(
                (t) =>
                  `${t.element} “${t.text}” is ${t.width}×${t.height} (${t.standalone ? "44" : "24"} px floor)`,
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
  await page.goto(url("/"), { waitUntil: "load" });

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
  await page.goto(url("/"), { waitUntil: "load" });
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
        const name = `${route === "/" ? "home" : route.replace(/\//g, "")}-${shot.label}-${scheme}.png`;
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

const browser = await chromium.launch();
try {
  await checkViewports(browser);
  await checkKeyboard(browser);
  await checkContrast(browser);
  await checkReducedMotion(browser);
  await checkAxe(browser);
  await captureScreenshots(browser);
} finally {
  await browser.close();
}

console.log(`\n${measurements.length} measurement(s), ${warnings.length} note(s), ${failures.length} failure(s).`);
console.error(measurements.map((line) => `  · ${line}`).join("\n"));
if (warnings.length) console.error(warnings.map((line) => `  ! ${line}`).join("\n"));
emitAnnotations();
if (failures.length) {
  console.error(failures.map((line) => `  - ${line}`).join("\n"));
  process.exitCode = 1;
}
