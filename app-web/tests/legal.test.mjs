/**
 * The legal pages in the browser preview.
 *
 * Three things are asserted here, and each is a real failure mode:
 *
 *  1. **The parser matches the app's rules.** `src/js/markdown.js` mirrors
 *     `shared/.../legal/Markdown.kt`. Neither can be run against the other (no
 *     JVM here, no Node in CI's Python step), so both suites pin the same
 *     invariants over the same bundled bytes: indented licence prose stays
 *     prose, both licence texts survive intact, and the DRAFT banner stays a
 *     banner.
 *  2. **The bundled content is complete and matches its digest.** The web copy
 *     and the app's copy are generated from the same `legal/*.md`; a mismatch
 *     means the browser preview is showing a different policy than the app.
 *  3. **The rendering is honest.** External links carry the `rel` triple and
 *     only ever for http(s); no `{{token}}` leaks to the reader; the About page
 *     never states a version this preview does not have.
 *
 * Runs with `npm test` — no dependencies, no browser, no network.
 */

import test from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";

import { parseMarkdown, inlineSpans, blockText } from "../src/js/markdown.js";
import { LEGAL_DOCUMENTS, legalDocumentById } from "../src/js/data/legal-content.js";
import {
  legalIndexScreen,
  legalDocumentScreen,
  legalMissingScreen,
  substituteLegalTokens,
  unresolvedLegalTokens,
  WEB_APP_INFO,
} from "../src/js/views.js";
import { boot } from "./helpers/boot-harness.mjs";

/** Escapes what the renderer escapes, so a title with "&" can be matched. */
const asHtml = (value) =>
  String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
/** Escapes for use inside a RegExp source. */
const asRe = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const PAGE_IDS = [
  "about",
  "privacy",
  "terms",
  "open-source-licenses",
  "third-party-notices",
  "support",
  "security-reporting",
];

/* ------------------------------------------------------------- the parser -- */

test("soft-wrapped lines reflow into one paragraph", () => {
  const blocks = parseMarkdown(
    "The GNU General Public License is a free, copyleft license for\nsoftware and other kinds of works.",
  );
  assert.equal(blocks.length, 1);
  assert.equal(blocks[0].kind, "paragraph");
  assert.equal(
    blockText(blocks[0]),
    "The GNU General Public License is a free, copyleft license for software and other kinds of works.",
  );
});

test("heading levels come from the hash count", () => {
  assert.equal(parseMarkdown("# Top")[0].level, 1);
  assert.equal(parseMarkdown("### Sub")[0].level, 3);
});

test("quote lines join into one banner", () => {
  const quote = parseMarkdown("> **DRAFT — not yet in force.** This page describes\n> what the build does.")[0];
  assert.equal(quote.kind, "quote");
  assert.match(blockText(quote), /DRAFT — not yet in force\./);
  assert.match(blockText(quote), /what the build does\./);
});

test("bullets carry their nesting depth and numbered items their number", () => {
  const bullets = parseMarkdown("- top\n  - nested");
  assert.equal(bullets[0].indent, 0);
  assert.equal(bullets[1].indent, 1);
  const numbered = parseMarkdown("1. first\n2. second");
  assert.equal(numbered[0].number, 1);
  assert.equal(numbered[1].number, 2);
});

test("a GFM table parses headers and rows", () => {
  const table = parseMarkdown(
    "| Recipient | What is sent |\n| --- | --- |\n| YouTube | Search terms |\n| LRCLIB | Title and artist |",
  )[0];
  assert.equal(table.kind, "table");
  assert.deepEqual(table.headers, ["Recipient", "What is sent"]);
  assert.deepEqual(table.rows[1], ["LRCLIB", "Title and artist"]);
});

test("a table without a separator row is not a table", () => {
  assert.equal(parseMarkdown("| not | a table |")[0].kind, "paragraph");
});

test("an empty-header table still parses", () => {
  const table = parseMarkdown("| | |\n| --- | --- |\n| Version | 1.0 |")[0];
  assert.deepEqual(table.headers, ["", ""]);
  assert.deepEqual(table.rows, [["Version", "1.0"]]);
});

test("a rule becomes a rule, not a paragraph", () => {
  assert.equal(parseMarkdown("---")[0].kind, "rule");
  assert.equal(parseMarkdown("***")[0].kind, "rule");
});

test("fenced blocks stay verbatim and do not reflow", () => {
  const blocks = parseMarkdown("```\nline one\nline two\n```");
  assert.equal(blocks.length, 1);
  assert.equal(blocks[0].text, "line one\nline two");
});

test("markdown links keep their label and URL", () => {
  const link = inlineSpans("see [THIRD_PARTY.md](https://example.test/a.md) now").find(
    (span) => span.kind === "link",
  );
  assert.equal(link.text, "THIRD_PARTY.md");
  assert.equal(link.url, "https://example.test/a.md");
});

test("a bare URL becomes a link, without trailing punctuation", () => {
  const spans = inlineSpans("report at https://lrclib.net/api/get today");
  assert.equal(spans.find((s) => s.kind === "link").url, "https://lrclib.net/api/get");
  assert.equal(inlineSpans("see https://example.test/page.").find((s) => s.kind === "link").url, "https://example.test/page");
});

test("an angle-bracketed URL is linked without the brackets", () => {
  assert.equal(inlineSpans("<https://fsf.org/>").find((s) => s.kind === "link").url, "https://fsf.org/");
});

test("bold, code and strike are carried as flags", () => {
  const spans = inlineSpans("**bold** and `code` and ~~gone~~");
  assert.ok(spans.some((s) => s.text === "bold" && s.bold));
  assert.ok(spans.some((s) => s.text === "code" && s.code));
  assert.ok(spans.some((s) => s.text === "gone" && s.strike));
});

test("an unbalanced marker is rendered literally", () => {
  // A stray backtick in a policy must not swallow the rest of the sentence.
  const spans = inlineSpans("a ` b and the rest");
  assert.equal(spans.map((s) => s.text).join(""), "a ` b and the rest");
  assert.ok(!spans.some((s) => s.code));
});

/* ----------------------------------------------------- the bundled content -- */

test("the bundle carries every page, in the app's order", () => {
  assert.deepEqual(LEGAL_DOCUMENTS.map((doc) => doc.id), PAGE_IDS);
});

test("every document's markdown hashes to its recorded digest", () => {
  for (const doc of LEGAL_DOCUMENTS) {
    assert.ok(doc.markdown.length > 400, `${doc.id} looks like a stub`);
    assert.equal(
      createHash("sha256").update(doc.markdown, "utf8").digest("hex"),
      doc.sha256,
      `${doc.id}: the web copy does not match the digest the app ships`,
    );
  }
});

test("every document parses into renderable blocks", () => {
  for (const doc of LEGAL_DOCUMENTS) {
    const blocks = parseMarkdown(doc.markdown);
    assert.ok(blocks.length > 5, `${doc.id} parsed to only ${blocks.length} block(s)`);
    for (const block of blocks) {
      if (block.kind === "code") assert.ok(block.text.trim() !== "", `${doc.id}: empty code block`);
      else if (block.kind === "table") assert.ok(block.rows.length > 0, `${doc.id}: empty table`);
      else if (block.kind !== "rule") assert.ok(block.spans.length > 0, `${doc.id}: empty ${block.kind}`);
    }
  }
});

test("the indented licence text stays prose, not a code block", () => {
  // Apache-2.0 indents its definitions by three spaces. With 4-space code blocks
  // enabled most of that licence would render as a monospace slab.
  const doc = legalDocumentById("open-source-licenses");
  const blocks = parseMarkdown(doc.markdown);
  assert.deepEqual(
    blocks.filter((b) => b.kind === "code"),
    [],
    "indented prose must stay prose",
  );
  assert.ok(blocks.filter((b) => b.kind === "paragraph").length > 50);
});

test("both licence texts survive parsing intact", () => {
  const words = parseMarkdown(legalDocumentById("open-source-licenses").markdown)
    .map(blockText)
    .join(" ");
  for (const landmark of [
    "GNU GENERAL PUBLIC LICENSE",
    "END OF TERMS AND CONDITIONS",
    "TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION",
    "APPENDIX: How to apply the Apache License",
  ]) {
    assert.match(words, new RegExp(landmark.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
});

test("every draft page opens with its DRAFT banner", () => {
  const drafts = LEGAL_DOCUMENTS.filter((doc) => doc.status === "draft");
  assert.ok(drafts.length > 0, "no draft page — the check would be vacuous");
  for (const doc of drafts) {
    const first = parseMarkdown(doc.markdown)[0];
    assert.equal(first.kind, "quote", `${doc.id} does not open with a quote banner`);
    assert.match(blockText(first), /DRAFT/);
  }
});

test("the support page lists the three real data controls", () => {
  const table = parseMarkdown(legalDocumentById("support").markdown).find(
    (b) => b.kind === "table" && b.headers.includes("Control"),
  );
  assert.deepEqual(table.headers, ["Control", "Where", "What it removes", "What remains"]);
  const names = table.rows.map((row) => row[0]);
  assert.deepEqual(names, ["Clear downloads", "Clear history", "Clear recent searches"]);
});

/* ---------------------------------------------------------- the rendering -- */

test("the index lists every page as a hash-route link", () => {
  const html = legalIndexScreen(LEGAL_DOCUMENTS);
  for (const doc of LEGAL_DOCUMENTS) {
    assert.match(html, new RegExp(`href="#/legal/${doc.id}"`), `${doc.id} is not linked`);
    assert.match(html, new RegExp(asRe(asHtml(doc.title))), `${doc.id}'s title is missing`);
  }
});

test("the index marks draft pages as drafts", () => {
  const html = legalIndexScreen(LEGAL_DOCUMENTS);
  assert.match(html, /dhun-legal__entry-meta--draft/);
  assert.match(html, /Draft · effective \d{4}-\d{2}-\d{2}/);
  // The non-draft rows must not stutter the word "effective" twice.
  assert.match(html, /class="dhun-legal__entry-meta">\s*Effective \d{4}-\d{2}-\d{2}/);
  assert.doesNotMatch(html, /Effective · effective/);
});

test("no {{token}} reaches the reader", () => {
  for (const doc of LEGAL_DOCUMENTS) {
    const html = legalDocumentScreen(doc);
    assert.doesNotMatch(html, /\{\{[a-zA-Z]/, `${doc.id} leaked a placeholder`);
  }
});

test("substitution fills the About page from this build's metadata", () => {
  const filled = substituteLegalTokens("v={{appVersion}} c={{releaseChannel}}");
  assert.equal(filled, `v=${WEB_APP_INFO.versionName} c=${WEB_APP_INFO.releaseChannel}`);
  assert.deepEqual(unresolvedLegalTokens(filled), []);
  assert.deepEqual(unresolvedLegalTokens("left {{appVersion}}"), ["{{appVersion}}"]);
});

test("the About page never claims a version this preview does not have", () => {
  // package.json says 0.0.0. That is a module version, not a release version, so
  // showing it would state something false about the app.
  assert.notEqual(WEB_APP_INFO.versionName, "0.0.0");
  const html = legalDocumentScreen(legalDocumentById("about"));
  assert.doesNotMatch(html, /0\.0\.0/);
  assert.match(html, /not applicable/);
});

test("a draft document renders its banner", () => {
  const html = legalDocumentScreen(legalDocumentById("privacy"));
  assert.match(html, /dhun-legal__banner/);
  assert.match(html, /Draft — not yet in force/);
});

test("external links carry the rel triple and open in a new tab", () => {
  const html = legalDocumentScreen(legalDocumentById("third-party-notices"));
  const links = [...html.matchAll(/<a class="dhun-legal__link"[^>]*>/g)].map((m) => m[0]);
  assert.ok(links.length > 0, "the notices page has real links");
  for (const link of links) {
    assert.match(link, /target="_blank"/);
    assert.match(link, /rel="noopener noreferrer"/);
    assert.match(link, /href="https?:\/\//);
  }
});

test("a non-http URL is never rendered as a link", () => {
  // Defence in depth: the corpus is http(s) only, but a future edit must not be
  // able to turn a policy page into a javascript: launcher.
  const spans = inlineSpans("[click](javascript:alert(1))");
  const html = legalDocumentScreen({
    id: "x",
    title: "X",
    effectiveDate: "2026-10-10",
    status: "current",
    sha256: "",
    markdown: "see [click](javascript:alert(1)) here",
  });
  assert.doesNotMatch(html, /javascript:/);
  assert.doesNotMatch(html, /<a class="dhun-legal__link"/);
  assert.ok(spans.some((s) => s.kind === "link"), "the parser still tags it; the renderer refuses it");
});

test("an unknown page says so instead of rendering nothing", () => {
  assert.equal(legalDocumentById("nope"), null);
  assert.match(legalMissingScreen("nope"), /not in this build/);
});

test("the support page renders real links to the issue tracker", () => {
  const html = legalDocumentScreen(legalDocumentById("support"));
  assert.match(html, /href="https:\/\/github\.com\/99ggprooo00-code\/DHUN\/issues\/new"/);
  assert.match(html, /href="https:\/\/github\.com\/99ggprooo00-code\/DHUN\/issues"/);
  assert.match(html, />Report a bug<\/a>/);
  assert.match(html, />Request a feature<\/a>/);
});

test("the security page links its interim channel", () => {
  const html = legalDocumentScreen(legalDocumentById("security-reporting"));
  assert.match(html, /href="https:\/\/github\.com\/99ggprooo00-code\/DHUN\/issues\/new"/);
  assert.match(html, /Security contact/);
});

test("no legal page renders a JS artefact instead of text", () => {
  // Regression: spanMarkup/blockMarkup used to return a mix of raw() marker
  // objects and strings, and joining them produced the literal "[object Object]"
  // in place of every link and heading. The boot test cannot catch that because
  // the legal pages are not part of the boot render.
  for (const doc of LEGAL_DOCUMENTS) {
    const html = legalDocumentScreen(doc);
    assert.doesNotMatch(html, /\[object Object\]/, `${doc.id} rendered a JS artefact`);
    assert.doesNotMatch(html, /\bundefined\b/, `${doc.id} rendered 'undefined'`);
    assert.doesNotMatch(html, />\s*NaN\s*</, `${doc.id} rendered NaN`);
  }
});

test("every legal page renders a real heading and real body text", () => {
  for (const doc of LEGAL_DOCUMENTS) {
    const html = legalDocumentScreen(doc);
    // h2/h3/h4 — the screen's own <h1> is the page title, so a document's top
    // heading maps to h2 and its sections to h3. about.md starts at `##`.
    assert.match(html, /<h[234] class="dhun-legal__heading/, `${doc.id} has no heading`);
    assert.match(html, /<p class="dhun-legal__para">/, `${doc.id} has no paragraph`);
    // The body is not a stub: a legal page with one paragraph has lost its text.
    assert.ok((html.match(/<p class="dhun-legal__para">/g) || []).length > 3, `${doc.id} is too short`);
  }
});

test("list items are grouped into real lists, one per run", () => {
  const html = legalDocumentScreen(legalDocumentById("privacy"));
  assert.match(html, /<ul class="dhun-legal__list">/);
  assert.match(html, /<li class="dhun-legal__li"/);
  // Every <li> must sit inside a list element, never bare.
  const lists = (html.match(/<ul class="dhun-legal__list">/g) || []).length;
  const closes = (html.match(/<\/ul>/g) || []).length;
  assert.equal(lists, closes, "unbalanced list markup");
});

test("tables render as stacked records with their labels", () => {
  const html = legalDocumentScreen(legalDocumentById("privacy"));
  assert.match(html, /<dl class="dhun-legal__record">/);
  assert.match(html, /<dt class="dhun-legal__label">Recipient<\/dt>/);
  assert.match(html, /<dd class="dhun-legal__value">/);
});

/* --------------------------------------------------------- app integration -- */

test("Settings offers the About & Legal entry", async (t) => {
  const { mount, setHash, applyHashRoute } = await boot(t);
  setHash("#/settings");
  applyHashRoute();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.match(mount.innerHTML, /data-action="open-about-legal"/);
  assert.match(mount.innerHTML, /About &amp; Legal/);
});

test("#/about-legal opens the index with every page linked", async (t) => {
  const { mount, app, setHash, applyHashRoute } = await boot(t);
  setHash("#/about-legal");
  applyHashRoute();
  await new Promise((resolve) => setTimeout(resolve, 0));
  const html = mount.innerHTML;
  assert.match(html, /<h1 class="dhun-topbar__title">About &amp; Legal<\/h1>/);
  for (const id of PAGE_IDS) assert.match(html, new RegExp(`#/legal/${id}`));
  assert.ok(app.nav.detailStack.some((route) => route.kind === "about-legal"));
});

test("#/legal/<id> opens the document directly — the refresh and share path", async (t) => {
  const { mount, setHash, applyHashRoute } = await boot(t);
  setHash("#/legal/privacy");
  applyHashRoute();
  await new Promise((resolve) => setTimeout(resolve, 0));
  const html = mount.innerHTML;
  assert.match(html, /<h1 class="dhun-topbar__title">Privacy Policy<\/h1>/);
  assert.match(html, /data-doc-id="privacy"/);
  // The bundled text really rendered, not a loading state.
  assert.match(html, /LRCLIB/);
  assert.match(html, /lyrics_enabled/);
});

test("every page id resolves through its deep link", async (t) => {
  for (const id of PAGE_IDS) {
    const { mount, setHash, applyHashRoute } = await boot();
    setHash(`#/legal/${id}`);
    applyHashRoute();
    await new Promise((resolve) => setTimeout(resolve, 0));
    assert.match(mount.innerHTML, new RegExp(`data-doc-id="${id}"`), `${id} did not render`);
  }
});

test("an unknown legal id renders the honest missing state", async (t) => {
  const { mount, setHash, applyHashRoute } = await boot(t);
  setHash("#/legal/nope");
  applyHashRoute();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.match(mount.innerHTML, /not in this build/);
});

test("the about-legal route is a singleton on the stack", async (t) => {
  const { app } = await boot(t);
  app.nav.push({ kind: "settings" });
  app.nav.push({ kind: "about-legal" });
  app.nav.push({ kind: "about-legal" });
  assert.deepEqual(
    app.nav.detailStack.map((r) => r.kind),
    ["settings", "about-legal"],
  );
});

test("back from a legal page returns to the index, then to Settings", async (t) => {
  // The app's contract: player -> one page -> one tab. Mirrored here so the
  // browser preview cannot unwind differently from the app.
  const { app, mount, setHash, applyHashRoute } = await boot(t);
  setHash("#/settings");
  applyHashRoute();
  app.nav.push({ kind: "about-legal" });
  app.nav.push({ kind: "legal", id: "privacy" });
  app.render();
  assert.equal(app.nav.detailStack.length, 3);

  app.nav.popDetail();
  app.render();
  assert.equal(app.nav.detailStack.at(-1).kind, "about-legal");
  assert.match(mount.innerHTML, /About &amp; Legal/);

  app.nav.popDetail();
  app.render();
  assert.equal(app.nav.detailStack.at(-1).kind, "settings");
  assert.match(mount.innerHTML, /<h1 class="dhun-topbar__title">Settings<\/h1>/);
});
