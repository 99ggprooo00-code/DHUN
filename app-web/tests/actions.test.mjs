/**
 * Every control in the interface does something, and every handler can be
 * reached.
 *
 * A web app's quietest failure is a button that looks alive and is not: there is
 * no compiler check for a `data-action` that no handler answers. The visitor
 * finds it. These checks read both halves out of the source — the chrome in
 * `main.js` here, the screens and the player in `views.test.mjs`, where every
 * screen is actually rendered — and compare them.
 *
 * They also check the shell's regions: `setRegion("dhun-view")` writes into an
 * element by id, so a typo is a screen that never appears.
 */
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";

import {
  jsRoot,
  main,
  modules,
  handledActions,
  shellRegionIds,
  declaredIds,
  keyboardActions,
  chromeActions,
  switchActions,
} from "./helpers/main-source.mjs";

test("every action the chrome emits is answered by a handler", () => {
  const handled = handledActions();
  const missing = [...chromeActions()].filter((action) => !handled.has(action));
  assert.deepEqual(missing, [], `these chrome controls do nothing when pressed: ${missing.join(", ")}`);
});

test("no handler is defined that nothing else in the app can reach", () => {
  // A handler is reachable through markup, through the keyboard map, or through
  // another handler calling it by name. A name that appears exactly once — as
  // its own key — is code nothing can run, and it will rot.
  const sources = modules.map(([, body]) => body);
  const dead = [...handledActions()].filter((name) => {
    const pattern = new RegExp(`(?<![a-z0-9-])${name}(?![a-z0-9-])`, "g");
    const mentions = sources.reduce((sum, body) => sum + (body.match(pattern)?.length ?? 0), 0);
    return mentions <= 1;
  });
  assert.deepEqual(dead.sort(), [], `handlers nothing can reach: ${dead.join(", ")}`);
});

test("the shell offers a way back out of every detail route", () => {
  const handled = handledActions();
  assert.ok(handled.has("back"), "the back handler was removed");
  assert.ok(main.includes('data-action="back"'), "nothing emits the back action");
  // A hash URL is not a visible affordance on a desktop window, so the top bar
  // carries the button whenever the route is one level down or the player is up.
  assert.match(main, /DETAIL_ROUTES\.includes\(route\.name\) \|\| route\.player === "open"/);
  const detailRoutes = main.match(/const DETAIL_ROUTES = Object\.freeze\(\[([^\]]*)\]\)/);
  assert.ok(detailRoutes, "DETAIL_ROUTES is gone");
  for (const route of ["playlist", "artist", "album", "settings", "about-legal"]) {
    assert.ok(detailRoutes[1].includes(`"${route}"`), `${route} has no way back`);
  }
});

test("every region the app renders into exists in the shell", () => {
  const regions = shellRegionIds();
  assert.ok(regions.size >= 6, `the shell should have several regions, found ${regions.size}`);
  const ids = declaredIds();
  const missing = [...regions].filter((region) => !ids.has(region));
  assert.deepEqual(missing, [], `the shell markup has no element for: ${missing.join(", ")}`);

  // The other direction: an id the shell declares that nothing renders into is
  // either a leftover or a region the app forgot to wire.
  const DECORATIVE = new Set(["app"]);
  const unwired = [...ids].filter((id) => id.startsWith("dhun-") && !regions.has(id) && !DECORATIVE.has(id));
  assert.deepEqual(unwired, [], `the shell declares ids nothing writes to: ${unwired.join(", ")}`);
});

test("every route the router can produce is rendered by the app", () => {
  const router = readFileSync(path.join(jsRoot, "router.js"), "utf8");
  const names = new Set([...router.matchAll(/name:\s*"([a-z-]+)"/g)].map((match) => match[1]));
  names.delete("home"); // the default branch, asserted below
  const rendered = new Set([...main.matchAll(/case\s+"([a-z-]+)":/g)].map((match) => match[1]));
  const unrendered = [...names].filter((name) => !rendered.has(name));
  assert.deepEqual(unrendered, [], `the router can open a screen the app never renders: ${unrendered.join(", ")}`);
  assert.match(main, /case "home":\s*\n\s*default:/, "home must be the fallback branch");
});

test("the keyboard map only points at handlers that exist", () => {
  // A shortcut resolves through the handler map, or through the dispatcher's own
  // switch for the few cases that need to move focus rather than render.
  const reachable = new Set([...handledActions(), ...switchActions()]);
  const dangling = [...keyboardActions()].filter((action) => !reachable.has(action));
  assert.deepEqual(dangling, [], `shortcuts point at handlers that do not exist: ${dangling.join(", ")}`);
});

test("no view emits an action name from an undefined value", () => {
  for (const [file, body] of modules) {
    assert.ok(!/data-action="\$\{[^}]*\bundefined\b/.test(body), `${file} builds an action name from undefined`);
  }
  // The one dynamic shape that matters: a queue row plays by position, a list
  // row plays by id, and the two must not be the same action.
  const views = readFileSync(path.join(jsRoot, "views.js"), "utf8");
  assert.match(views, /variant === "queue" \? "play-queue-row" : "play-track"/);
  const playerUi = readFileSync(path.join(jsRoot, "player-ui.js"), "utf8");
  assert.ok(playerUi.includes('data-action="seek"'), "the seek range drives playback through its action");
});
