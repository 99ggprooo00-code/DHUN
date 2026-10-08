/**
 * Mutation proof for `tools/prune-css.mjs` — the per-route CSS pruner.
 *
 * Every predicate gets a must-keep and a must-drop case, so a pruner that stops
 * pruning (or starts pruning something it must not) is a red test in the build
 * job rather than a heavier page. No browser, no network, no build:
 *   cd website && node --test tests/prune.test.mjs
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import {
  classesUsedInHtml,
  positiveClasses,
  pruneCss,
  pruneHtmlDocument,
  splitSelectors,
} from "../tools/prune-css.mjs";

const used = (...names) => new Set(names);

test("a rule whose classes the page never uses is dropped", () => {
  const css = ".kept { color: red; }\n.dropped { color: blue; }\n";
  assert.equal(pruneCss(css, used("kept")), ".kept { color: red; }\n");
});

test("a selector with no class at all is never dropped", () => {
  const css = "body { color: red; }\n* { box-sizing: border-box; }\na:hover { color: blue; }\n";
  assert.equal(pruneCss(css, used()), css);
});

test("a selector list keeps its used selectors and loses only the unused ones", () => {
  const css = ".a, .b, .c { color: red; }\n";
  // A selector list that loses part of itself is rebuilt without the padding
  // the dropped selectors sat in; the surviving selector text is unchanged.
  assert.equal(pruneCss(css, used("b")), ".b{ color: red; }\n");
});

test("a selector list whose every class is unused is dropped whole", () => {
  const css = ".a, .b { color: red; } .c { color: blue; }";
  assert.equal(pruneCss(css, used("c")), " .c { color: blue; }");
});

test("whitespace and comments go with the rule they precede", () => {
  const css = "/* header */\n.a { color: red; }\n\n/* hero */\n.b { color: blue; }\n";
  assert.equal(pruneCss(css, used("a")), "/* header */\n.a { color: red; }\n");
  assert.equal(pruneCss(css, used("b")), "\n\n/* hero */\n.b { color: blue; }\n");
});

test("a rule whose only class is inside :not() is kept, because it can match", () => {
  const css = "a:not(.btn) { text-decoration: underline; }";
  assert.equal(pruneCss(css, used()), css);
  assert.deepEqual([...positiveClasses("a:not(.btn)")], []);
  assert.deepEqual([...positiveClasses("a:not(.x).y")].sort(), ["y"]);
});

test("a class inside :is() counts as positive", () => {
  const css = ":is(.a, .b) > * { color: red; }";
  assert.equal(pruneCss(css, used("b")), css);
  assert.equal(pruneCss(css, used("z")), "");
});

test("a conditional group is pruned inside and dropped only when it empties", () => {
  const css =
    "@media (min-width: 640px) {\n  .used { color: red; }\n  .unused { color: blue; }\n}\n" +
    "@media print {\n  .unused { color: blue; }\n}\n";
  assert.equal(
    pruneCss(css, used("used")),
    "@media (min-width: 640px) {\n  .used { color: red; }\n}\n",
  );
  assert.equal(pruneCss(css, used("used", "unused")), css, "nothing prunable must be a no-op");
});

test("a conditional group that keeps element rules survives with them", () => {
  const css = "@media print { :root { --bg: #fff; } .mock .device { display: none; } }";
  assert.equal(pruneCss(css, used()), "@media print { :root { --bg: #fff; } }");
});

test("at-rules that are not conditional groups are kept verbatim", () => {
  const css =
    "@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }\n" +
    "@font-face { font-family: x; src: url(x.woff2); }\n" +
    "@import url(y.css);";
  assert.equal(pruneCss(css, used()), css);
});

test("declaration bodies are never rewritten, only whole selectors are removed", () => {
  const css = ".a { color: red; /* keep */ background: url('a,{b}.css'); }";
  assert.equal(pruneCss(css, used("a")), css);
  assert.equal(pruneCss(css, used()), "");
});



test("pruning is idempotent", () => {
  const css =
    "/* c */\n.a{color:red}\n@media (min-width: 480px){.b{color:blue}}\nbody{margin:0}\n";
  const once = pruneCss(css, used("a"));
  assert.equal(pruneCss(once, used("a")), once);
  assert.equal(once, "/* c */\n.a{color:red}\nbody{margin:0}\n");
});

test("classesUsedInHtml reads class attributes and ignores the stylesheet", () => {
  const html =
    '<style>.onlyinstyle { color: red; }</style>' +
    "<p class=\"a b\">x</p><span class='c'>y</span>" +
    "<script>var s = 'class=\"d\"';</script>";
  assert.deepEqual([...classesUsedInHtml(html)].sort(), ["a", "b", "c"]);
});

test("pruneHtmlDocument prunes each page against its own markup", () => {
  const html =
    "<style>.mine{color:red}.theirs{color:blue}</style><p class=\"mine\">x</p>";
  assert.equal(pruneHtmlDocument(html), "<style>.mine{color:red}</style><p class=\"mine\">x</p>");
});

test("a page whose stylesheet prunes away entirely keeps its stylesheet", () => {
  // Defensive: an empty <style> can only mean the pruner was wrong about the
  // page, and a page with no CSS at all is worse than one carrying a spare rule.
  const html = "<style>.theirs{color:blue}</style><p>x</p>";
  assert.equal(pruneHtmlDocument(html), html);
});

test("splitSelectors respects commas inside functional selectors", () => {
  assert.deepEqual(splitSelectors(":is(.a, .b), .c"), [":is(.a, .b)", " .c"]);
});

test("the real stylesheets lose rules only for pages that cannot use them", () => {
  // Reproduces the build's own decision on real input: every module the site
  // ships, pruned for a page that uses one class. The pruner must not throw on
  // any real stylesheet and must not touch the tokens.
  const modules = ["tokens", "base", "components", "mockups", "ui"];
  for (const name of modules) {
    const css = readFileSync(new URL(`../css/${name}.css`, import.meta.url), "utf8");
    const pruned = pruneCss(css, used("btn"));
    assert.ok(pruned.length <= css.length, `${name}.css grew while pruning`);
    assert.equal(pruneCss(pruned, used("btn")), pruned, `${name}.css is not idempotent`);
    if (name === "tokens") assert.equal(pruned, css, "tokens.css must survive pruning whole");
  }
});
