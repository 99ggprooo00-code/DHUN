/**
 * Reading `main.js` as data.
 *
 * The interface is written in three places — the shell and the chrome in
 * `main.js`, the screens in `views.js`, the player in `player-ui.js` — and the
 * wiring between them is a string: `data-action="…"` on one side, a key in a
 * handler map on the other. There is no compiler check for a mismatch, so the
 * tests read both halves out of the source. Shared here so the check lives in
 * one place rather than in three files that could drift.
 */
import { readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
export const srcRoot = path.join(here, "..", "..", "src");
export const jsRoot = path.join(srcRoot, "js");

export const main = readFileSync(path.join(jsRoot, "main.js"), "utf8");
export const shellHtml = readFileSync(path.join(srcRoot, "index.html"), "utf8");

/** Every module, as [filename, source]. */
export const modules = readdirSync(jsRoot, { recursive: true })
  .filter((file) => String(file).endsWith(".js"))
  .map((file) => [String(file), readFileSync(path.join(jsRoot, String(file)), "utf8")])
  // The Android/desktop legal corpus is generated into this tree for the app's
  // gate; it is not part of this build.
  .filter(([file]) => file !== path.join("data", "legal-content.js"));

/** The action names `main.js` answers, from its handler map. Keys are quoted
 *  except where an identifier is legal, and a handler may be `async`. */
export function handledActions() {
  const start = main.indexOf("const actions = {");
  if (start < 0) throw new Error("main.js has no action map");
  const body = main.slice(start, main.indexOf("\n  };", start));
  const pattern = /\n\s{4}(?:"([a-z][a-z0-9-]*)"|'([a-z][a-z0-9-]*)'|([a-z][a-z0-9-]*))\s*:\s*(?:async\s*)?\(/g;
  return new Set([...body.matchAll(pattern)].map((match) => match[1] ?? match[2] ?? match[3]));
}

/** Actions the shortcut dispatcher answers itself, from its `switch`. Those are
 *  reachable by keystroke rather than through the handler map. */
export function switchActions() {
  const start = main.indexOf("switch (map[action.id])");
  if (start < 0) throw new Error("main.js has no shortcut dispatcher");
  const body = main.slice(start, main.indexOf("\n    }\n", start));
  return new Set([...body.matchAll(/case\s+"([a-z][a-z0-9-]*)":/g)].map((match) => match[1]));
}

/** The ids the shell renders into: `setRegion(id, markup)` needs a real element. */
export function shellRegionIds() {
  return new Set([...main.matchAll(/setRegion\(\s*"([^"]+)"/g)].map((match) => match[1]));
}

/** The ids the shell markup declares, wherever it is written. */
export function declaredIds() {
  const found = new Set();
  for (const body of [shellHtml, main]) {
    for (const match of body.matchAll(/\bid="([^"]+)"/g)) found.add(match[1]);
  }
  return found;
}

/** Action names a keypress can reach: the `case` labels of the shortcut switch. */
export function keyboardActions() {
  const start = main.indexOf("switch (action) {");
  if (start < 0) throw new Error("main.js has no shortcut dispatcher");
  const body = main.slice(start, main.indexOf("\n    }\n", start));
  return new Set([...body.matchAll(/case\s+"([a-z][a-z0-9-]*)":/g)].map((match) => match[1]));
}

/** Actions written by hand into chrome markup — the parts of the interface that
 *  live in `main.js` rather than in a view. */
export function chromeActions() {
  return new Set([...main.matchAll(/data-action="([^"$]+)"/g)].map((match) => match[1]));
}

/** Extract every `data-action` from rendered markup. */
export function actionsIn(markup) {
  return [...String(markup).matchAll(/data-action="([^"]+)"/g)].map((match) => match[1]);
}
