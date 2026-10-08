/**
 * Annotation packing for the browser harness.
 *
 * `browser.mjs` runs in CI only — there is no browser in this sandbox — so the
 * channel it reports through is all it has, and that channel is lossy in two
 * independent ways:
 *
 *   1. GitHub returns roughly ten annotations per step. A category that
 *      produces more than that loses the tail.
 *   2. An annotation *message* is truncated at roughly 4 KB. The previous
 *      carry annotation clipped at 24 KB, which looked generous and silently
 *      dropped whatever came after the first ~4 KB — including both axe scans
 *      and every emulated increase-contrast measurement.
 *
 * The fix is not a bigger number. It is:
 *
 *   - every message is packed to a measured budget (default 3.5 KB, under the
 *     observed truncation point);
 *   - the budget is spent on *several* annotations instead of one carry;
 *   - whatever still does not fit is named by category and count, never
 *     silently dropped;
 *   - the full, untruncated report is written to the job log and to
 *     `$GITHUB_STEP_SUMMARY`, which have no such cap — so an annotation is a
 *     convenience and the log is the record.
 *
 * Pure functions, no imports: `tests/annotation-report.test.mjs` proves the
 * packing without a browser.
 */

/** Largest message this module will emit. GitHub truncates around 4 KB; the
 *  slack is deliberate — the truncation point is measured, not specified. */
export const MESSAGE_BUDGET = 3500;

/** GitHub returns about ten annotations per step, whatever we emit. */
export const ANNOTATION_LIMIT = 10;

/** Room the title and the "::notice title=::" scaffolding need. */
const TITLE_RESERVE = 120;

export function clipMessage(text, limit = MESSAGE_BUDGET) {
  if (text.length <= limit) return text;
  return `${text.slice(0, limit)} …(clipped, ${text.length - limit} more chars — see the job log)`;
}

/** Splits `items` into as many messages as needed, each within the budget. */
export function packItems(items, limit = MESSAGE_BUDGET) {
  const chunks = [];
  let current = "";
  for (const item of items) {
    const piece = current === "" ? String(item) : ` || ${item}`;
    if (current.length + piece.length > limit) {
      if (current !== "") chunks.push(current);
      current = String(item).slice(0, limit);
    } else {
      current += piece;
    }
  }
  if (current !== "") chunks.push(current);
  return chunks;
}

/**
 * Turns grouped findings into annotations that fit both caps.
 *
 * @returns {{annotations: Array<{title: string, body: string}>,
 *            dropped: Array<{key: string, count: number}>}}
 *          `dropped` is never empty when anything was left out — a dropped
 *          measurement is reported as a dropped measurement.
 */
export function packReport(entries, label, options = {}) {
  const budget = options.messageBudget ?? MESSAGE_BUDGET;
  const limit = options.annotationLimit ?? ANNOTATION_LIMIT;

  const candidates = [];
  for (const [key, items] of entries) {
    const chunks = packItems(items, budget - TITLE_RESERVE);
    chunks.forEach((body, index) => {
      const suffix = chunks.length > 1 ? ` (${index + 1}/${chunks.length})` : "";
      candidates.push({ key, count: items.length, title: `${key}${suffix} — ${items.length} ${label}(s)`, body });
    });
  }

  const kept = candidates.slice(0, limit);
  const rest = candidates.slice(limit);

  const annotations = kept.map(({ title, body }) => ({ title, body }));
  const dropped = rest.map(({ key, count }) => ({ key, count }));
  if (dropped.length > 0) {
    annotations.pop(); // make room for the overflow note
    annotations.push({
      title: `${dropped.length} group(s) not annotated — ${dropped.reduce((sum, d) => sum + d.count, 0)} ${label}(s)`,
      body: clipMessage(
        `Annotated ${kept.length - 1} of ${candidates.length} groups; the rest are in the job log and the run summary: ` +
          dropped.map((d) => `${d.key} (${d.count})`).join(", "),
        budget - TITLE_RESERVE,
      ),
    });
  }
  return { annotations, dropped };
}

/** The complete report as markdown — no clipping anywhere. */
export function renderReport(entries, label) {
  // A Map (grouped) or an array of pairs (already spread) both work; a Map has
  // no `.length`, and that omission used to print "(undefined)" as the count.
  const rows = entries instanceof Map ? [...entries.entries()] : entries;
  if (rows.length === 0) return "";
  const lines = [`### ${label} (${rows.length})`, ""];
  for (const [key, items] of rows) {
    lines.push(`- **${key}** — ${items.length}`);
    for (const item of items) lines.push(`  - ${item}`);
  }
  lines.push("");
  return lines.join("\n");
}

/** The whole run's report, for the log and the step summary. */
export function renderAllReports({ failures = [], warnings = [], measurements = [] } = {}) {
  return [
    "# Browser harness report",
    "",
    `_Failures, warnings and measurements in full. Annotations are capped by GitHub; this is not._`,
    "",
    renderReport(failures, "problem"),
    renderReport(warnings, "note"),
    renderReport(measurements, "measurement"),
  ].join("\n");
}
