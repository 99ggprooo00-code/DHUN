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
  shellHtml,
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

/*
 * The app's own copy is a claim surface, and these are the claims it may not
 * make — the ones about playback.
 *
 * The authoritative lexicon is `scripts/website_claims.py`, which checks the
 * built site and the bytes a visitor receives. It cannot read these strings:
 * every screen here is assembled in the browser, so `/app/` serves an empty
 * `<div id="app">` and a module graph. Python cannot render the screens, and
 * hand-copying the lexicon into a second language is how two rule sets start to
 * disagree — so this check is deliberately *narrower* than the Python one and
 * covers the one class of claim the app could over-promise in its own chrome:
 * that audio comes out.
 *
 * The finding it was written for: the first-run strip read "Search and play from
 * YouTube Music anonymously", on screen at boot, on every route, for the whole
 * session — the exact claim `PHASE-02` exists to record as unproven.
 */
const PLAYBACK_PROMISES = [
  /plays in (any|every) browser/i,
  /works in (any|every) browser/i,
  /guaranteed playback/i,
  /offline playback/i,
  /downloads? (songs|tracks|music)/i,
  /play(s|able)? from YouTube Music/i,
  /YouTube Music,? playable/i,
  /\bFLAC\b|\blossless\b|\bbitrate\b|\bkbps\b/i,
];

/** Code, not copy: comments explain rules and quote the mistakes they prevent,
 *  so a rule scanning the *copy* has to read past them. */
const withoutComments = (body) =>
  body.replace(/\/\*[\s\S]*?\*\//g, " ").replace(/(^|[^:"'\`])\/\/[^\n]*/g, "$1 ");

test("the app's own copy never promises playback", () => {
  // The manifest's description is copy too — it is what the install prompt and
  // an app list would show.
  const manifest = JSON.parse(readFileSync(path.join(jsRoot, "..", "manifest.webmanifest"), "utf8"));
  const copy = [
    ["main.js (chrome and boot copy)", withoutComments(main)],
    ["index.html", withoutComments(shellHtml)],
    ["manifest.webmanifest", `${manifest.name} ${manifest.description} ${JSON.stringify(manifest.shortcuts)}`],
  ];
  for (const [file, body] of modules) copy.push([file, withoutComments(body)]);

  for (const [where, body] of copy) {
    for (const promise of PLAYBACK_PROMISES) {
      const match = promise.exec(body);
      if (!match) continue;
      // A word inside quoted copy a person could read. Identifiers, property
      // names and URL paths are not claims: `bitrate:` as a key, `bitrate` as a
      // stream field, and the endpoint they travel in are all fine.
      const escaped = match[0].replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      const quoted = new RegExp(`["'\`][^"'\`]{0,120}${escaped}`);
      assert.ok(!quoted.test(body), `${where} promises playback: ${match[0]}`);
    }
  }
});

test("the first-run strip is dismissed by the visitor's first interaction", () => {
  // It used to sit above the dock on every route, for the whole session: nothing
  // ever cleared an `info` banner.
  assert.match(main, /dismissWelcomeOnFirstInteraction\(\)/, "nothing dismisses the strip");
  assert.match(main, /clearBanner\("info"\)/);
  assert.match(main, /removeEventListener\("pointerdown", once, true\)/);
  assert.match(main, /removeEventListener\("keydown", once, true\)/);
  assert.match(main, /addEventListener\("pointerdown", once, true\)/);
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

test("a playback failure is reported once, not twice", () => {
  // The expanded player draws the failure in its own control cluster, from the
  // same `playerState.error` this handler is handed. The toast repeated it word
  // for word and, being fixed above the dock, sat on top of the player's own
  // title and artist while it did — measured 2026-10-16 at 1280x900. So the
  // toast must stay for the screens that have no such strip, and only for those.
  const start = main.indexOf("function onTrackFailed(");
  assert.notEqual(start, -1, "main.js has no failure handler");
  const body = main.slice(start, main.indexOf("\n  }", start));
  const guard = body.indexOf('state.route.player === "open"');
  const toast = body.indexOf("showToast(");
  assert.notEqual(guard, -1, "the failure toast is not guarded by the player's own notice");
  assert.notEqual(toast, -1, "the failure handler stopped reporting anything at all");
  assert.ok(guard < toast, "the guard has to run before the toast is raised");
  assert.match(body, /return;/, "the guard has to end the handler, not just test the flag");
});

test("opening the player clears a toast raised before it", () => {
  // Measured 2026-10-16 on a 390x844 phone: the failure toast raised by the very
  // tap that expands the player rode in with it and covered the player's own
  // notice and its buttons. The player is a full-screen surface with its own
  // status strip, so it starts clean — and only on the transition, so a toast
  // raised while the player is already open still shows.
  assert.match(main, /function clearToast\(\)/, "nothing can take a toast down early");
  assert.match(
    main,
    /route\.player === "open" &&\s*\n?\s*previous\.player !== "open"\s*\)\s*clearToast\(\)/,
    "the player does not clear a toast it inherited",
  );
});

test("re-rendering the player does not throw the visitor back to the cover", () => {
  // The overlay's scroller is a child, so `setRegion`'s own `data-scroll`
  // restore never reached it and every tab press replaced the markup under the
  // visitor's finger. Measured 2026-10-16 on a 390x844 phone: switching to
  // Lyrics reset the scroll 613 → 0 and moved the panel from y=327 to y=940 —
  // the tab they pressed looked like it did nothing. Where they were is kept
  // for the same track, and only the track decides that.
  const start = main.indexOf("function renderOverlay(");
  assert.notEqual(start, -1, "main.js has no overlay renderer");
  const body = main.slice(start, main.indexOf("\n  }", start));
  assert.match(body, /\.dhun-full__inner/, "the player's own scroller is never read");
  assert.match(body, /playerScroll\.top = scroller\.scrollTop/, "the scroll position is not captured");
  assert.match(
    body,
    /trackKey === playerScroll\.trackKey\) next\.scrollTop = playerScroll\.top/,
    "the scroll position is not restored for the same track",
  );
  const key = body.match(/const trackKey = [^;]+;/);
  assert.ok(key, "no track key is computed");
  assert.ok(!/tab/i.test(key[0]), "the tab must not be part of the key — that is the bug");
});

test("the mood rail's control is wired to a real request", () => {
  assert.ok(handledActions().has("set-mood"), "no handler answers data-action=\"set-mood\"");
  // The rail is a tablist of requests, so re-pressing the selected tab must not
  // re-ask: a tab that reloads itself on every tap is a tab nobody trusts.
  const start = main.indexOf('"set-mood":');
  assert.notEqual(start, -1, "main.js has no set-mood handler");
  const body = main.slice(start, main.indexOf("\n    },", start));
  assert.match(body, /if \(id === state\.mood\) return;/, "pressing the selected mood re-fetches it");
  assert.match(body, /loadMood\(id\)/, "choosing a mood does not load it");
  // And the mood's own load path is a *source request*, not a client-side sort.
  const load = main.indexOf("async function loadMood(");
  assert.notEqual(load, -1, "there is no mood loader");
  const loadBody = main.slice(load, main.indexOf("\n  }", load));
  assert.match(loadBody, /sources\.search\(mood\.query\)/, "a mood must go to the source, not sort loaded rows");
  assert.match(loadBody, /if \(!mood\.query\)/, "\"For you\" has no query and must not be fetched");
  // A mood that arrives late must not overwrite a mood the visitor moved to.
  assert.match(loadBody, /if \(state\.mood !== mood\.id\) return;/, "a stale mood answer can overwrite the current one");
  // And a mood carries its own state, so its failure cannot empty the feed.
  assert.match(main, /moodFeed: \{ status: "idle"/, "the mood borrows the feed's state");
});
