/**
 * Per-route CSS pruning — DHUN's own code, no dependencies.
 *
 * Why this exists: every route inlines the CSS modules its front matter
 * declares, and a module is shared by several routes (`base.css` carries the
 * header, the footer *and* the home page's hero; `components.css` carries the
 * home page's stat row *and* the 404 page's error block). A route therefore
 * shipped rules it cannot use — measured at boot on 2026-10-08: 18 unused
 * classes on `/`, 52 on `/features/`, 53 on `/ui/`, 34 on `/404.html`. This
 * step drops exactly those rules after Eleventy has rendered the document, so
 * the decision is made against the page's real class attributes rather than
 * against a hand-maintained module list.
 *
 * What it is allowed to remove, and what it never touches:
 *   - A *style rule* whose selector list has classes and none of them appear in
 *     the page's markup loses the selectors that can never match. A selector
 *     with no positive class selector — `body`, `*`, `[aria-current]`, and
 *     anything whose only class sits inside `:not(...)` — is never removed,
 *     because it can match elements that have no class at all.
 *   - A conditional group (`@media`, `@supports`, `@container`, `@layer`) is
 *     pruned recursively and dropped only when nothing survives inside it.
 *   - Any other at-rule (`@keyframes`, `@font-face`, `@import`, …) is kept
 *     verbatim: its body is not a selector list, so the predicate above does
 *     not apply to it.
 *   - `:root` and the custom-property blocks are never touched. Tokens are the
 *     design system's published contract (`/ui/` prints their values), so an
 *     unused token stays; pruning rules only is deliberate and is stated in
 *     `.ai/WEBSITE_PLAN.md`.
 *
 * Safety: the only thing that can add a class at runtime is JavaScript, and the
 * site ships none (asserted by `scripts/website_quality.py`). The build is
 * still correct if this step is wrong: `css_coverage_violations` fails the
 * build when a page uses a class its own stylesheet does not define, and
 * `dist_source_drift_violations` fails it when a rule that should have survived
 * is missing, when order changed, or when a rule the page cannot use ships.
 *
 * `website/tests/prune.test.mjs` exercises every rule above with a must-keep
 * and a must-drop case; the drift and coverage checks in `scripts/` re-derive
 * the same predicate from the committed build, so a build that skipped this
 * step is a red test rather than a heavier page.
 */
import { readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

/** Conditional group at-rules: their body is a list of rules, not declarations. */
const CONDITIONAL_AT_RULES = ["@media", "@supports", "@container", "@layer"];

const STYLE_BLOCK = /(<style\b[^>]*>)([\s\S]*?)(<\/style>)/gi;

/** Class selectors *inside* `:not(...)`, which are negative, not positive. */
function stripNegations(selector) {
  let out = "";
  let index = 0;
  while (index < selector.length) {
    const at = selector.indexOf(":not(", index);
    if (at === -1) return out + selector.slice(index);
    out += selector.slice(index, at);
    let depth = 0;
    let cursor = at + 4;
    for (; cursor < selector.length; cursor += 1) {
      const character = selector[cursor];
      if (character === "(") depth += 1;
      else if (character === ")") {
        depth -= 1;
        if (depth === 0) break;
      }
    }
    index = cursor + 1;
  }
  return out;
}

/**
 * Every class a selector mentions *positively* — i.e. a class whose presence is
 * required for the selector to match. A selector with an empty result can match
 * an element without any class and is therefore never prunable.
 */
export function positiveClasses(selector) {
  const classes = new Set();
  for (const [, name] of stripNegations(selector).matchAll(/\.(-?[A-Za-z_][\w-]*)/g)) {
    classes.add(name);
  }
  return classes;
}

/** Split a selector list on the commas that are not inside `(...)`. */
export function splitSelectors(prelude) {
  const selectors = [];
  let depth = 0;
  let start = 0;
  for (let index = 0; index < prelude.length; index += 1) {
    const character = prelude[index];
    if (character === "(") depth += 1;
    else if (character === ")") depth -= 1;
    else if (character === "," && depth === 0) {
      selectors.push(prelude.slice(start, index));
      start = index + 1;
    }
  }
  selectors.push(prelude.slice(start));
  return selectors;
}

function skipString(css, index) {
  const quote = css[index];
  let cursor = index + 1;
  while (cursor < css.length) {
    if (css[cursor] === "\\") cursor += 2;
    else if (css[cursor] === quote) return cursor + 1;
    else cursor += 1;
  }
  return cursor;
}

function skipComment(css, index) {
  const end = css.indexOf("*/", index + 2);
  return end === -1 ? css.length : end + 2;
}

/**
 * Parse a stylesheet into a flat list of nodes: `{ type: "comment" }`,
 * `{ type: "statement" }` (an at-rule ending in `;`) and
 * `{ type: "block", prelude, body }`. Bodies are kept as text; only conditional
 * groups are parsed again, and only when they are pruned.
 */
export function parseCss(css) {
  const nodes = [];
  let index = 0;
  while (index < css.length) {
    const leadingStart = index;
    while (index < css.length && /\s/.test(css[index])) index += 1;
    const leading = css.slice(leadingStart, index);
    if (index >= css.length) {
      if (leading) nodes.push({ type: "whitespace", text: leading });
      break;
    }
    if (css.startsWith("/*", index)) {
      const stop = skipComment(css, index);
      if (leading) nodes.push({ type: "whitespace", text: leading });
      nodes.push({ type: "comment", text: css.slice(index, stop) });
      index = stop;
      continue;
    }
    let cursor = index;
    let depth = 0;
    let terminator = null;
    while (cursor < css.length) {
      const character = css[cursor];
      if (character === '"' || character === "'") {
        cursor = skipString(css, cursor);
        continue;
      }
      if (character === "/" && css[cursor + 1] === "*") {
        cursor = skipComment(css, cursor);
        continue;
      }
      if (character === "(") depth += 1;
      else if (character === ")") depth -= 1;
      else if (depth === 0 && (character === "{" || character === ";")) {
        terminator = character;
        break;
      }
      cursor += 1;
    }
    if (terminator === null) {
      // Trailing text with no block: an unterminated rule. Keep it verbatim
      // rather than guess — dropping bytes here would be a silent rewrite.
      nodes.push({ type: "whitespace", text: leading + css.slice(index) });
      break;
    }
    const prelude = css.slice(index, cursor);
    if (terminator === ";") {
      if (leading) nodes.push({ type: "whitespace", text: leading });
      nodes.push({ type: "statement", text: prelude });
      index = cursor + 1;
      continue;
    }
    let bodyStart = cursor + 1;
    let bodyEnd = bodyStart;
    let braces = 1;
    while (bodyEnd < css.length) {
      const character = css[bodyEnd];
      if (character === '"' || character === "'") {
        bodyEnd = skipString(css, bodyEnd);
        continue;
      }
      if (character === "/" && css[bodyEnd + 1] === "*") {
        bodyEnd = skipComment(css, bodyEnd);
        continue;
      }
      if (character === "{") braces += 1;
      else if (character === "}") {
        braces -= 1;
        if (braces === 0) break;
      }
      bodyEnd += 1;
    }
    if (leading) nodes.push({ type: "whitespace", text: leading });
    nodes.push({ type: "block", prelude, body: css.slice(bodyStart, bodyEnd) });
    index = bodyEnd < css.length ? bodyEnd + 1 : css.length;
  }
  return nodes;
}

function isConditional(prelude) {
  const head = prelude.trim().toLowerCase();
  return CONDITIONAL_AT_RULES.some(
    (name) => head.startsWith(name) && /^[\s(]/.test(head.slice(name.length) || " "),
  );
}

function hasContent(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, "").trim().length > 0;
}

/**
 * Keep only the rules that can match the page's markup.
 *
 * `used` is the set of class names in the document's `class` attributes. The
 * result is byte-identical to the input wherever nothing was pruned, so the
 * diff between a pruned and an unpruned build shows exactly what changed.
 */
export function pruneCss(css, used) {
  const output = [];
  // Whitespace and comments are held back and flushed with the next rule that
  // survives: a comment above a rule explains that rule, so it goes with it.
  let pending = "";
  for (const node of parseCss(css)) {
    if (node.type === "whitespace" || node.type === "comment") {
      pending += node.text;
      continue;
    }
    if (node.type === "statement") {
      output.push(pending + node.text + ";");
      pending = "";
      continue;
    }
    let kept = null;
    if (node.prelude.trimStart().startsWith("@")) {
      if (isConditional(node.prelude)) {
        const inner = pruneCss(node.body, used);
        if (hasContent(inner)) kept = node.prelude + "{" + inner + "}";
      } else {
        kept = node.prelude + "{" + node.body + "}";
      }
    } else {
      const selectors = splitSelectors(node.prelude);
      const keeps = selectors.map((selector) => {
        const classes = positiveClasses(selector);
        return classes.size === 0 || [...classes].some((name) => used.has(name));
      });
      if (keeps.every(Boolean)) {
        // Nothing prunable in this rule: the prelude is kept byte-for-byte, so
        // the pruned file differs from the source only where it had to.
        kept = node.prelude + "{" + node.body + "}";
      } else if (keeps.some(Boolean)) {
        kept =
          selectors
            .filter((_selector, index) => keeps[index])
            .map((selector) => selector.trim())
            .join(",") +
          "{" +
          node.body +
          "}";
      }
    }
    if (kept === null) {
      pending = "";
      continue;
    }
    output.push(pending + kept);
    pending = "";
  }
  // Trailing trivia is flushed so that a stylesheet with nothing prunable in it
  // comes out byte-identical — the property `tools/verify-minify.mjs` and the
  // drift check rely on when they compare a pruned build with a pruned source.
  if (pending) output.push(pending);
  return output.join("");
}

/**
 * The classes a document actually uses, read from `class` attributes only.
 * `<style>` and `<script>` bodies are removed first: the stylesheet contains
 * class *selectors*, and counting those would make every rule look used.
 */
export function classesUsedInHtml(html) {
  const outside = html
    .replace(STYLE_BLOCK, "$1$3")
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, "");
  const used = new Set();
  for (const [, doubleQuoted, singleQuoted] of outside.matchAll(
    /\bclass\s*=\s*(?:"([^"]*)"|'([^']*)')/gi,
  )) {
    for (const name of (doubleQuoted ?? singleQuoted).split(/\s+/)) {
      if (name) used.add(name);
    }
  }
  return used;
}

/** Prune every `<style>` block of one built page against its own markup. */
export function pruneHtmlDocument(html) {
  const used = classesUsedInHtml(html);
  return html.replace(STYLE_BLOCK, (_match, open, css, close) => {
    const pruned = pruneCss(css, used);
    return open + (pruned.trim() ? pruned : css) + close;
  });
}

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith(".html")) out.push(full);
  }
  return out;
}

/** CLI: prune every built HTML file in `dist/` in place. */
function main() {
  const dist = new URL("../dist/", import.meta.url).pathname;
  const files = walk(dist);
  if (!files.length) {
    console.error(`::error title=prune-css::no built HTML under ${dist}`);
    process.exitCode = 1;
    return;
  }
  let saved = 0;
  for (const file of files) {
    const before = readFileSync(file, "utf8");
    const after = pruneHtmlDocument(before);
    if (after !== before) writeFileSync(file, after);
    saved += before.length - after.length;
  }
  console.log(`pruned ${files.length} page(s): ${saved} bytes of CSS no page can use`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
