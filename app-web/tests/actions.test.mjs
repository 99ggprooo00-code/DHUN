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

import {
  main,
  modules,
  handledActions,
  shellRegionIds,
  declaredIds,
  keyboardActions,
  chromeActions,
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
  const keyboard = keyboardActions();
  const dead = [...handledActions()].filter((name) => {
    if (keyboard.has(name)) return false;
    const pattern = new RegExp(`(?<![a-z0-9-])${name}(?![a-z0-9-])`, "g");
    const mentions = sources.reduce((sum, body) => sum + (body.match(pattern)?.length ?? 0), 0);
    return mentions <= 1;
  });
  assert.deepEqual(dead.sort(), [], `handlers nothing can reach: ${dead.join(", ")}`);
});

test("every key the shell claims is dispatched somewhere", () => {
  // Keystrokes are answered by the dispatcher's own `switch`, not by the click
  // action map: a shortcut id is not a `data-action`. The link that matters is
  // the reverse one — every id in SHORTCUTS has a case, or the help sheet
  // advertises a key nobody listens for.
  const shortcuts = modules.find(([file]) => file === "shortcuts.js")?.[1] ?? "";
  const declared = new Set([...shortcuts.matchAll(/id: "([a-z][a-z0-9-]*)"/g)].map((match) => match[1]));
  assert.ok(declared.size >= 10, "the shortcut table lost most of its keys");
  const missing = [...declared].filter((id) => !keyboardActions().has(id));
  assert.deepEqual(missing, [], `the help sheet lists keys the dispatcher ignores: ${missing.join(", ")}`);
  const extra = [...keyboardActions()].filter((id) => !declared.has(id));
  assert.deepEqual(extra, [], `the dispatcher answers keys the help sheet never mentions: ${extra.join(", ")}`);
});

test("the shell offers a way back out of every detail route", () => {
  const handled = handledActions();
  assert.ok(handled.has("back"), "the back handler was removed");
  const shell = modules.find(([file]) => file === "shell.js")?.[1] ?? "";
  assert.ok(shell.includes('data-action="back"'), "nothing emits the back action");
  // A hash URL is not a visible affordance on a desktop window, so the top bar
  // carries the button whenever the route is one level down.
  assert.match(main, /DETAIL_ROUTES\.includes\(route\.name\)/);
  const router = modules.find(([file]) => file === "router.js")?.[1] ?? "";
  const detailRoutes = router.match(/DETAIL_ROUTES = Object\.freeze\(\[([^\]]*)\]\)/);
  assert.ok(detailRoutes, "DETAIL_ROUTES is gone");
  for (const route of ["playlist", "artist", "album", "settings", "about"]) {
    assert.ok(detailRoutes[1].includes(`"${route}"`), `${route} has no way back`);
  }
});

test("every region the app renders into exists in the shell", () => {
  const regions = shellRegionIds();
  assert.ok(regions.size >= 6, `the shell should have several regions, found ${regions.size}`);
  const ids = declaredIds();
  const missing = [...regions].filter((region) => !ids.has(region));
  assert.deepEqual(missing, [], `the shell markup has no element for: ${missing.join(", ")}`);
});

test("the app never writes markup into a region that does not exist", () => {
  // `setRegion` warns and returns when the id is missing, which is right at
  // runtime and invisible in a test; this reads the ids it is asked for.
  const ids = declaredIds();
  for (const region of shellRegionIds()) {
    assert.ok(ids.has(region), `${region} is rendered into but never declared`);
  }
});


test("keyboard seek actions use audio seconds, not native-style milliseconds", () => {
  const forward = main.split('case "seek-forward":', 2)[1]?.split("break;", 1)[0] ?? "";
  const backward = main.split('case "seek-back":', 2)[1]?.split("break;", 1)[0] ?? "";
  assert.match(forward, /player\.seekBy\(5\)/);
  assert.match(backward, /player\.seekBy\(-5\)/);
  assert.doesNotMatch(forward + backward, /5000/);
});

test("media-session play and pause are intent-specific and seek in audio seconds", () => {
  assert.match(main, /play: \(\) => \{ if \(!player\.requested\) player\.toggle\(\); \}/);
  assert.match(main, /pause: \(\) => \{ if \(player\.requested\) player\.toggle\(\); \}/);
  assert.match(main, /seekBackward: \(\) => player\.seekBy\(-10\)/);
  assert.match(main, /seekForward: \(\) => player\.seekBy\(10\)/);
  assert.match(main, /recordedPlayback !== player\.playbackId/);
});
