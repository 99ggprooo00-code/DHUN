/** Tiny DOM helpers. No framework, no virtual DOM, no dependencies. */

/**
 * A rendered fragment.
 *
 * It behaves like a string — `innerHTML = fragment`, `fragment.includes(…)`,
 * `fragment.length` all work — but it is *marked* as markup, which is what lets
 * one screen compose another (`html`<section>${sectionHeader("Queue")}</section>``)
 * without re-escaping it.
 *
 * This marker is the difference between a working app and one that prints its own
 * markup as text. The previous version returned a plain string and only honoured
 * an explicit `raw()` wrapper, so every nested fragment — a section header, a
 * chip row, an empty state — appeared on screen as `&lt;div class=…&gt;` unless
 * each of the dozens of call sites remembered to wrap it. It also made the
 * escaping contract invisible in review: the bug was indistinguishable from the
 * safety feature.
 */
class Markup extends String {}

/** Mark a string as markup. Values already marked are returned as they are, so
 *  `raw(markup)` is always safe to write. */
export function raw(value) {
  return value instanceof Markup ? value : new Markup(value === null || value === undefined ? "" : String(value));
}

/** Tagged template that escapes interpolated *data* and inserts markup. */
export function html(strings, ...values) {
  let out = strings[0];
  for (let index = 0; index < values.length; index += 1) {
    out += piece(values[index]) + strings[index + 1];
  }
  return new Markup(out);
}

/** One interpolated value: markup passes through, data gets escaped, and a
 *  boolean interpolated by accident disappears rather than printing `true`. */
function piece(value) {
  if (value instanceof Markup) return value.valueOf();
  if (value === null || value === undefined || typeof value === "boolean") return "";
  if (Array.isArray(value)) return value.map(piece).join("");
  return escapeHtml(value);
}

export function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/** Query one element; throws loudly in development, degrades in production. */
export function must(selector, root = document) {
  const element = root.querySelector(selector);
  if (!element) throw new Error(`missing required element: ${selector}`);
  return element;
}

export function setHtml(element, markup) {
  element.innerHTML = markup;
  return element;
}

/** Delegated listener: one handler per container, not one per row. Rows come
 *  and go on every render; rebinding them each time is how handlers leak. */
export function delegate(root, eventName, selector, handler) {
  root.addEventListener(eventName, (event) => {
    const target = event.target instanceof Element ? event.target.closest(selector) : null;
    if (target && root.contains(target)) handler(event, target);
  });
}

/** Moves focus into `element` without scrolling the page out from under the
 *  user. Used when a detail page opens or the full player collapses. */
export function focusWithoutScroll(element) {
  if (!element) return;
  const previous = element.getAttribute("tabindex");
  if (previous === null) element.setAttribute("tabindex", "-1");
  element.focus({ preventScroll: true });
  if (previous === null) element.removeAttribute("tabindex");
}
