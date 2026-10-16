/**
 * The markup contract.
 *
 * `html` escapes data and inserts markup, and the difference between the two is
 * the single most consequential rule in this app: a track title is data, an icon
 * is markup, and a screen that nests another screen is markup too. Getting it
 * wrong in one direction is a cross-site scripting hole and in the other
 * direction is a page that prints its own HTML as text — and the mirror build
 * shipped the second bug across five screens, which is why these assertions exist
 * against the mechanism rather than only against the screens.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { html, raw, escapeHtml } from "../src/js/dom.js";

const text = (value) => String(value);

test("interpolated data is escaped", () => {
  const markup = text(html`<p>${'<img src=x onerror="alert(1)">'}</p>`);
  assert.equal(markup, "<p>&lt;img src=x onerror=&quot;alert(1)&quot;&gt;</p>");
  assert.equal(text(html`<p>${"a & b"}</p>`), "<p>a &amp; b</p>");
  assert.equal(text(html`<p>${"'quoted'"}</p>`), "<p>&#39;quoted&#39;</p>");
});

test("markup passes through, however it is nested", () => {
  const fragment = html`<b>bold</b>`;
  assert.equal(text(html`<div>${fragment}</div>`), "<div><b>bold</b></div>");
  assert.equal(text(html`<div>${html`<i>${html`<u>deep</u>`}</i>`}</div>`), "<div><i><u>deep</u></i></div>");
  assert.equal(text(raw("<hr />")), "<hr />");
  assert.equal(text(raw(raw("<hr />"))), "<hr />", "marking twice is not an error");
});

test("a fragment behaves like a string everywhere else", () => {
  const fragment = html`<p>hello</p>`;
  assert.equal(typeof fragment, "object", "the marker is what makes nesting safe");
  assert.equal(fragment.length, 12);
  assert.ok(fragment.includes("hello"));
  assert.equal(`${fragment}`, "<p>hello</p>");
  assert.equal(text(fragment).replace(/<[^>]+>/g, ""), "hello");
  // innerHTML assignment is a string coercion, which is why the mark survives
  // only as long as the value is not run through a string method.
  assert.equal(String(fragment), fragment.valueOf());
});

test("lists and holes disappear instead of printing themselves", () => {
  assert.equal(text(html`<ul>${["a", "b"].map((item) => html`<li>${item}</li>`)}</ul>`), "<ul><li>a</li><li>b</li></ul>");
  assert.equal(text(html`<p>${null}${undefined}${false}${true}</p>`), "<p></p>");
  assert.equal(text(html`<p>${0}</p>`), "<p>0</p>", "a zero is a value");
  assert.equal(text(html`<p>${[]}</p>`), "<p></p>");
});

test("a hostile array element is escaped, not inserted", () => {
  const markup = text(html`<ul>${['<script>alert(1)</script>', html`<li>safe</li>`]}</ul>`);
  assert.equal(markup, "<ul>&lt;script&gt;alert(1)&lt;/script&gt;<li>safe</li></ul>");
});

test("escapeHtml is the one escaper and it is total", () => {
  assert.equal(escapeHtml('<img src=x onerror="alert(1)">'), "&lt;img src=x onerror=&quot;alert(1)&quot;&gt;");
  assert.equal(escapeHtml("a & b"), "a &amp; b");
  assert.equal(escapeHtml("'quote'"), "&#39;quote&#39;");
  assert.equal(escapeHtml(null), "null", "the tag treats null as absence; this is the raw function");
  assert.equal(escapeHtml(undefined), "undefined");
});

test("no interpolation can produce a tag that was not in the template", () => {
  const attacks = [
    "</script><script>alert(1)</script>",
    "\"><img src=x onerror=alert(1)>",
    "<svg/onload=alert(1)>",
    "&lt;script&gt;",
  ];
  for (const attack of attacks) {
    const markup = text(html`<p title="${attack}">${attack}</p>`);
    assert.ok(!markup.includes("<img"), `${attack} produced an img tag`);
    assert.ok(!markup.includes("<svg"), `${attack} produced an svg tag`);
    assert.ok(!/<\/script>/i.test(markup), `${attack} closed a script tag`);
    assert.ok(markup.includes("&lt;") || markup.includes("&quot;") || markup.includes("&amp;"), `${attack} was not escaped`);
  }
});
