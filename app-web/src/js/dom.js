/** Tiny DOM helpers. No framework, no virtual DOM, no dependencies. */

/** Tagged template that escapes interpolated values by default; wrap trusted
 *  markup in `raw()` when it is genuinely markup (icon SVG, another html``). */
const RAW = Symbol("raw");

export function raw(value) {
  return { [RAW]: true, value };
}

export function html(strings, ...values) {
  return strings.reduce((out, chunk, i) => {
    if (i === 0) return chunk;
    const value = values[i - 1];
    const piece =
      value && value[RAW]
        ? value.value
        : Array.isArray(value)
          ? value.map((v) => (v && v[RAW] ? v.value : escapeHtml(v))).join("")
          : value === null || value === undefined || value === false
            ? ""
            : escapeHtml(value);
    return out + piece + chunk;
  }, "");
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
