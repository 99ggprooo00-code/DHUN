/**
 * The legal renderer.
 *
 * The pages are the app's promises, so the renderer's job is narrow: fill the
 * build's own tokens, turn only `http(s)` into links, and draw every string as
 * text. A policy page that could be edited into a `javascript:` launcher is not
 * a policy page.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { WEB_LEGAL_DOCUMENTS, webLegalById } from "../src/js/data/web-legal.js";
import { WEB_APP_INFO, legalBody, legalUnresolved, substituteLegalTokens, unresolvedLegalTokens } from "../src/js/legal.js";

const text = (value) => String(value);

test("the web build has its own pages, and they name the real services", () => {
  const ids = WEB_LEGAL_DOCUMENTS.map((document) => document.id);
  assert.deepEqual(ids, ["about", "privacy", "terms", "licences", "support"]);
  const corpus = WEB_LEGAL_DOCUMENTS.map((document) => document.markdown).join("\n");
  assert.ok(corpus.includes("saavn.dev"), "the privacy page must name the API the code calls");
  assert.ok(corpus.includes("saavncdn.com"));
  assert.ok(corpus.includes("GNU General Public License"));
  assert.equal(webLegalById("nope"), null);
  assert.equal(webLegalById("privacy").title, "Privacy");
});

test("the app's identity fills every token", () => {
  const body = substituteLegalTokens("Version {{appVersion}} on {{platform}} ({{releaseChannel}})", {
    versionName: "9.9.9",
    versionCode: null,
    releaseChannel: "test",
    platform: "browser",
  });
  assert.equal(body, "Version 9.9.9 on browser (test)");
});

test("a build's version is the one in version.js, not a typed string", () => {
  assert.match(WEB_APP_INFO.versionName, /^\d+\.\d+\.\d+$/);
  assert.equal(WEB_APP_INFO.platform, "browser");
});

test("an unresolved token is reported rather than printed", () => {
  assert.deepEqual(unresolvedLegalTokens("{{appVersion}} and {{mystery}}"), ["{{appVersion}}", "{{mystery}}"]);
  for (const document of WEB_LEGAL_DOCUMENTS) {
    assert.deepEqual(unresolvedLegalTokens(substituteLegalTokens(document.markdown)), []);
    assert.deepEqual(legalUnresolved(document), []);
  }
});

test("the pages render their headings and prose", () => {
  const markup = text(legalBody(webLegalById("terms")));
  assert.ok(markup.includes("<h2"), "a page with no headings is not rendered");
  assert.ok(markup.includes("<p>"), "a page with no paragraphs is not rendered");
  assert.ok(markup.includes("GNU General Public License"), "the licence text went missing");
});

test("data is escaped and only http(s) becomes a link", () => {
  const hostile = { title: "Test", markdown: "## Head\n\n<script>alert(1)</script> [x](javascript:alert(1)) [y](https://github.com/99ggprooo00-code/DHUN)" };
  const markup = text(legalBody(hostile));
  assert.ok(!markup.includes("<script>"), "raw HTML in a document was inserted");
  assert.ok(markup.includes("&lt;script&gt;"));
  assert.ok(!markup.includes("href=\"javascript:"), "a javascript: URL became a link");
  assert.ok(markup.includes('href="https://github.com/99ggprooo00-code/DHUN"'));
});

test("tables become a description list rather than a 2-column table", () => {
  const markup = text(legalBody({ title: "T", markdown: "| | |\n| --- | --- |\n| Version | 3.0.0 |" }));
  assert.ok(markup.includes("<dl"), "the stacked table should be a dl");
  assert.ok(markup.includes("<dt>Version</dt>"));
  assert.ok(markup.includes("<dd>3.0.0</dd>"));
});

test("every page is shipped with the build", () => {
  for (const document of WEB_LEGAL_DOCUMENTS) {
    assert.ok(document.markdown.length > 400, `${document.id} is a stub`);
    assert.ok(document.title.length > 2, `${document.id} has no title`);
  }
});
