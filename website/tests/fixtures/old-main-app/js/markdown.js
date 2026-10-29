/**
 * The Markdown subset the legal pages render from.
 *
 * A mirror of `shared/src/commonMain/kotlin/dev/dhun/legal/Markdown.kt`: same
 * rules, same block shapes, same order. The web app mirrors the app's interface
 * rather than redesigning it, and a policy that reads differently in the browser
 * than in the app is a different policy.
 *
 * Both parsers are pinned against the same bundled corpus —
 * `src/js/data/legal-content.js`, generated from `legal/*.md`. Neither is run
 * against the other (this sandbox has no JVM and CI's Python step has no Node),
 * so the two suites assert the same invariants over the same bytes: the licence
 * texts survive intact, indented prose stays prose, and the DRAFT banner stays a
 * banner. `tests/legal.test.mjs` is the web half.
 *
 * Deliberately not implemented, matching the Kotlin side:
 *  - 4-space indented code blocks. The licence texts use significant leading
 *    indentation for prose, so treating it as code would put most of Apache-2.0
 *    in a monospace box. Only fenced ``` blocks are code.
 *  - HTML, images, reference links, footnotes.
 */

const FENCE = /^\s*(```|~~~)\s*([A-Za-z0-9]*)\s*$/;
const HEADING = /^(#{1,6})\s+(.*)$/;
const BULLET = /^(\s*)[-*+]\s+(.*)$/;
const NUMBERED = /^(\s*)(\d+)[.)]\s+(.*)$/;
const QUOTE = /^\s*>\s?(.*)$/;
const RULE = /^\s*([-*_])\s*(?:\1\s*){2,}$/;
const TABLE_ROW = /^\s*\|.*\|\s*$/;
const TABLE_SEPARATOR = /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$/;

const LINK = /\[([^\]]+)\]\(([^)\s]+)\)/;
const BARE_URL = /(https?:\/\/[^\s<>"'`]+)/;
/** Trailing sentence punctuation that is not part of the URL. */
const URL_TRAILING = /[.,;:!?]+$/;
const MARKER = /(\*\*|~~|`)/;

/** Applies `**bold**`, `~~strike~~` and `` `code` `` to a marker-free fragment. */
function styled(source) {
  const spans = [];
  let rest = source;

  while (rest.length > 0) {
    const match = MARKER.exec(rest);
    if (!match) {
      spans.push({ kind: "text", text: rest });
      break;
    }
    const marker = match[1];
    const close = rest.indexOf(marker, match.index + marker.length);
    if (close < 0) {
      // Unbalanced marker: emit the rest literally rather than eating it.
      spans.push({ kind: "text", text: rest });
      break;
    }
    if (match.index > 0) spans.push({ kind: "text", text: rest.slice(0, match.index) });
    const body = rest.slice(match.index + marker.length, close);
    if (marker === "**") spans.push({ kind: "text", text: body, bold: true });
    else if (marker === "~~") spans.push({ kind: "text", text: body, strike: true });
    else spans.push({ kind: "text", text: body, code: true });
    rest = rest.slice(close + marker.length);
  }
  return spans.filter((span) => span.text.length > 0);
}

/** Splits one line into styled runs, turning links into real anchors. */
export function inlineSpans(source) {
  const spans = [];
  let rest = source;

  while (rest.length > 0) {
    const link = LINK.exec(rest);
    const url = BARE_URL.exec(rest);
    let next = null;
    let isLinkSyntax = false;
    if (link && (!url || link.index <= url.index)) {
      next = link;
      isLinkSyntax = true;
    } else if (url) {
      next = url;
    }
    if (!next) {
      spans.push(...styled(rest));
      break;
    }
    if (next.index > 0) spans.push(...styled(rest.slice(0, next.index)));
    if (isLinkSyntax) {
      spans.push({ kind: "link", text: link[1], url: link[2] });
    } else {
      const raw = url[1];
      const trimmed = raw.replace(URL_TRAILING, "");
      spans.push({ kind: "link", text: trimmed, url: trimmed });
      if (trimmed.length < raw.length) {
        spans.push({ kind: "text", text: raw.slice(trimmed.length) });
      }
    }
    rest = rest.slice(next.index + next[0].length);
  }
  return spans.filter((span) => span.text.length > 0);
}

function cells(row) {
  let text = row.trim();
  if (text.startsWith("|")) text = text.slice(1);
  if (text.endsWith("|")) text = text.slice(0, -1);
  return text.split("|").map((cell) => cell.trim());
}

function startsNewBlock(line) {
  return (
    FENCE.test(line) ||
    HEADING.test(line) ||
    RULE.test(line) ||
    BULLET.test(line) ||
    NUMBERED.test(line) ||
    QUOTE.test(line) ||
    TABLE_ROW.test(line)
  );
}

/**
 * Parses a legal document into renderable blocks.
 *
 * @returns {Array<object>} `{ kind: "heading"|"paragraph"|"bullet"|"numbered"|
 *   "quote"|"code"|"table"|"rule", … }`
 */
export function parseMarkdown(markdown) {
  const lines = String(markdown).replace(/\r\n?/g, "\n").split("\n");
  const blocks = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    if (line.trim() === "") {
      i += 1;
      continue;
    }

    const fence = FENCE.exec(line);
    if (fence) {
      const close = new RegExp(`^\\s*${fence[1].replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*$`);
      const body = [];
      i += 1;
      while (i < lines.length && !close.test(lines[i])) {
        body.push(lines[i]);
        i += 1;
      }
      i += 1; // step over the closing fence, or past the end of input
      blocks.push({ kind: "code", text: body.join("\n") });
      continue;
    }

    if (RULE.test(line)) {
      blocks.push({ kind: "rule" });
      i += 1;
      continue;
    }

    const heading = HEADING.exec(line);
    if (heading) {
      blocks.push({ kind: "heading", level: heading[1].length, spans: inlineSpans(heading[2].trim()) });
      i += 1;
      continue;
    }

    if (TABLE_ROW.test(line) && i + 1 < lines.length && TABLE_SEPARATOR.test(lines[i + 1])) {
      const headers = cells(line);
      const rows = [];
      i += 2;
      while (i < lines.length && TABLE_ROW.test(lines[i])) {
        rows.push(cells(lines[i]));
        i += 1;
      }
      blocks.push({ kind: "table", headers, rows });
      continue;
    }

    const quote = QUOTE.exec(line);
    if (quote) {
      const parts = [quote[1]];
      i += 1;
      while (i < lines.length) {
        const next = QUOTE.exec(lines[i]);
        if (!next) break;
        parts.push(next[1]);
        i += 1;
      }
      blocks.push({ kind: "quote", spans: inlineSpans(parts.join(" ").trim()) });
      continue;
    }

    const bullet = BULLET.exec(line);
    if (bullet) {
      blocks.push({
        kind: "bullet",
        indent: Math.floor(bullet[1].length / 2),
        spans: inlineSpans(bullet[2].trim()),
      });
      i += 1;
      continue;
    }

    const numbered = NUMBERED.exec(line);
    if (numbered) {
      blocks.push({
        kind: "numbered",
        number: Number(numbered[2]) || 1,
        indent: Math.floor(numbered[1].length / 2),
        spans: inlineSpans(numbered[3].trim()),
      });
      i += 1;
      continue;
    }

    // Paragraph: join the soft-wrapped lines that follow. The canonical
    // Markdown is hard-wrapped near column 80, so a sentence routinely spans
    // several lines and must be reflowed to read as one.
    const parts = [line.trim()];
    i += 1;
    while (i < lines.length && lines[i].trim() !== "" && !startsNewBlock(lines[i])) {
      parts.push(lines[i].trim());
      i += 1;
    }
    blocks.push({ kind: "paragraph", spans: inlineSpans(parts.join(" ")) });
  }

  return blocks;
}

/** Joins a block's spans back into plain text — used by tests, not by the UI. */
export function blockText(block) {
  if (block.kind === "code") return block.text;
  if (block.kind === "table") return [...block.headers, ...block.rows.flat()].join(" ");
  return (block.spans ?? []).map((span) => span.text).join("");
}
