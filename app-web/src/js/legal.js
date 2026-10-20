/**
 * The web build's legal documents, rendered.
 *
 * The text lives in `data/web-legal.js` and describes *this* build — a static
 * page, a browser's storage, one third-party catalogue. It deliberately does
 * not reuse the Android corpus in `legal/*.md`: those pages describe downloads,
 * a SQLite database and YouTube, and every one of those sentences would be
 * false in a browser tab.
 *
 * This module is the HTML mirror of the app's `Markdown.kt`, not a general
 * Markdown library: it understands exactly the block kinds the corpus uses, and
 * every span it draws is escaped.
 *
 * Only `http(s)` URLs become links. A policy page that could be edited into a
 * `javascript:` launcher is not a policy page.
 */

import { html, raw } from "./dom.js";
import { parseMarkdown } from "./markdown.js";
import { WEB_VERSION } from "./version.js";

/** This build's identity, filled into the documents' `{{tokens}}`. */
export const WEB_APP_INFO = Object.freeze({
  versionName: WEB_VERSION,
  versionCode: null,
  releaseChannel: "web (static build)",
  platform: "browser",
});

function tokenTable(appInfo) {
  return {
    "{{appVersion}}": appInfo.versionName,
    "{{appVersionCode}}": appInfo.versionCode ?? "not applicable",
    "{{releaseChannel}}": appInfo.releaseChannel,
    "{{platform}}": appInfo.platform,
  };
}

export function substituteLegalTokens(markdown, appInfo = WEB_APP_INFO) {
  const table = tokenTable(appInfo);
  return String(markdown).replace(/\{\{[a-zA-Z][a-zA-Z0-9]*\}\}/g, (token) => (token in table ? table[token] : token));
}

export function unresolvedLegalTokens(markdown) {
  return [...new Set(String(markdown).match(/\{\{[a-zA-Z][a-zA-Z0-9]*\}\}/g) ?? [])];
}

function isSafeUrl(url) {
  return /^https?:\/\//i.test(url);
}

function spanMarkup(span) {
  if (span.kind === "link") {
    if (!isSafeUrl(span.url)) return span.text;
    return html`<a href="${span.url}" target="_blank" rel="noopener noreferrer">${span.text}</a>`;
  }
  const classes = [span.bold ? "is-strong" : "", span.code ? "is-code" : "", span.strike ? "is-strike" : ""].filter(Boolean).join(" ");
  return classes ? html`<span class="${classes}">${span.text}</span>` : span.text;
}

function spansMarkup(spans) {
  return html`${(spans ?? []).map(spanMarkup)}`;
}

function cellSpans(cell) {
  return parseMarkdown(cell).flatMap((block) => block.spans ?? []);
}

function blockMarkup(block) {
  switch (block.kind) {
    case "heading": {
      const level = Math.min(Math.max(block.level, 1), 6);
      // The screen's own title is the h1 and a second one would break the
      // outline. The corpus starts at `##` ("1. Summary"), so level 2 is the
      // first heading under the page title: it maps to h2, not h3. Mapping it
      // to h3 skipped a level and axe called it (heading-order) on About.
      const tag = level <= 2 ? "h2" : level === 3 ? "h3" : "h4";
      return html`<${raw(tag)} class="dhun-prose__heading">${spansMarkup(block.spans)}</${raw(tag)}>`;
    }
    case "paragraph":
      return html`<p>${spansMarkup(block.spans)}</p>`;
    case "quote":
      return html`<blockquote>${spansMarkup(block.spans)}</blockquote>`;
    case "bullet":
      return html`<li data-indent="${block.indent}">${spansMarkup(block.spans)}</li>`;
    case "numbered":
      return html`<li data-indent="${block.indent}" value="${block.number}">${spansMarkup(block.spans)}</li>`;
    case "code":
      return html`<pre><code>${block.text}</code></pre>`;
    case "rule":
      return html`<hr />`;
    case "table":
      // Stacked label/value records: a four-column table on a 360px phone is
      // either illegible or sideways-scrollable, and a policy that scrolls
      // sideways is a policy nobody finishes.
      // Two shapes appear in the corpus: a key/value table with an empty
      // header row (`| | |`), where the first cell of each row is the term, and
      // a labelled table, where the headers are. Both become one `<dl>` per
      // row; neither becomes a table the phone has to scroll sideways.
      const keyValue = block.headers.every((header) => !header || header.trim() === "");
      return html`${block.rows.map((row) =>
        keyValue && row.length === 2
          ? html`<dl class="dhun-prose__record"><dt>${spansMarkup(cellSpans(row[0]))}</dt><dd>${spansMarkup(cellSpans(row[1]))}</dd></dl>`
          : html`<dl class="dhun-prose__record">${row.map((cell, column) => {
              const label = block.headers[column];
              return html`${label && label.trim() !== "" ? html`<dt>${label}</dt>` : html``}<dd>${spansMarkup(cellSpans(cell))}</dd>`;
            })}</dl>`,
      )}`;
    default:
      return html``;
  }
}

function blocksMarkup(blocks) {
  const out = [];
  let listKind = null;
  let open = [];
  const flush = () => {
    if (!open.length) return;
    const tag = listKind === "numbered" ? "ol" : "ul";
    out.push(html`<${raw(tag)} class="dhun-prose__list">${open}</${raw(tag)}>`);
    open = [];
    listKind = null;
  };
  for (const block of blocks) {
    if (block.kind === "bullet" || block.kind === "numbered") {
      if (listKind && listKind !== block.kind) flush();
      listKind = block.kind;
      open.push(blockMarkup(block));
    } else {
      flush();
      out.push(blockMarkup(block));
    }
  }
  flush();
  return html`${out}`;
}

/** A document's body, as markup. */
export function legalBody(document, appInfo = WEB_APP_INFO) {
  if (!document) return html``;
  return blocksMarkup(parseMarkdown(substituteLegalTokens(document.markdown, appInfo)));
}

/** Tokens a document still carries, so the screen can say so rather than print one. */
export function legalUnresolved(document, appInfo = WEB_APP_INFO) {
  if (!document) return [];
  return unresolvedLegalTokens(substituteLegalTokens(document.markdown, appInfo));
}
