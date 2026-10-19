/**
 * The shipping contract: one app, one stylesheet set, one offline shell.
 *
 * These are the checks that would otherwise be discovered by a visitor — a class
 * the JavaScript uses that no stylesheet defines (an unstyled control), a rule no
 * screen can reach (dead CSS that will never be seen and never be cleaned), an
 * inline pixel value that bypasses the design system, a module the service worker
 * forgets to cache (an app that works until the network goes away), or an
 * external request that breaks the CSP.
 *
 * The class scanner reads classes where the app actually writes them — `class`
 * attributes, `classList` calls, and a variable used as a class. That distinction
 * matters: `setRegion("dhun-view", …)` names an element id, not a class, and the
 * service worker's `dhun-web-v2.0.0` is a cache namespace. A test that treats
 * every string starting with `dhun-` as a class demands styles for things that
 * are not elements at all.
 */
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const srcRoot = path.join(here, "..", "src");
const jsRoot = path.join(srcRoot, "js");
const cssRoot = path.join(srcRoot, "css");
const indexHtml = path.join(srcRoot, "index.html");

function walk(dir, predicate = () => true) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry);
    if (statSync(full).isDirectory()) out.push(...walk(full, predicate));
    else if (predicate(full)) out.push(full);
  }
  return out;
}

const jsFiles = walk(jsRoot, (file) => file.endsWith(".js"));
const cssFiles = walk(cssRoot, (file) => file.endsWith(".css"));
const appCss = readFileSync(path.join(cssRoot, "app.css"), "utf8");
const tokensCss = readFileSync(path.join(cssRoot, "tokens.css"), "utf8");
const allCss = cssFiles.map((file) => readFileSync(file, "utf8")).join("\n");
const html = readFileSync(indexHtml, "utf8");

const CLASS_TOKEN = /dhun-[a-zA-Z0-9_-]+/g;
const FILE_LIKE = /\.(json|js|mjs|css|html|png|svg|webmanifest|mp3|m3u8)$/;

/** Remove `${…}` expressions, keeping the template text around them. Braces
 *  nest inside an expression, so this counts depth instead of stopping at the
 *  first `}`. */
function stripExpressions(text) {
  let out = "";
  let depth = 0;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (char === "$" && text[index + 1] === "{") {
      depth += 1;
      index += 1;
      continue;
    }
    if (depth > 0) {
      if (char === "{") depth += 1;
      else if (char === "}") depth -= 1;
      continue;
    }
    out += char;
  }
  return out;
}

/**
 * The class names a module emits.
 *
 * `families` are prefixes a class is assembled from — `dhun-art--${size}` yields
 * the family `dhun-art--`, whose members are proven separately, because the
 * value that completes them comes from a call site rather than a literal.
 */
function scanClasses(source) {
  const classes = new Set();
  const families = new Set();

  const add = (text, familiesAllowed) => {
    for (const token of String(text ?? "").split(/\s+/)) {
      if (FILE_LIKE.test(token)) continue; // `dhun-library-${date}.json` is a filename
      for (const [name] of token.matchAll(CLASS_TOKEN)) {
        if (name.endsWith("-")) {
          if (familiesAllowed) families.add(name);
          continue;
        }
        classes.add(name);
      }
    }
  };

  const record = (text) => {
    // A class assembled from an expression still names its variants in string
    // literals: `dhun-row__main${variant === "queue" ? " dhun-row__main--queue" : ""}`.
    for (const match of String(text ?? "").matchAll(/"([^"]*)"|'([^']*)'/g)) add(match[1] ?? match[2] ?? "", false);
    add(stripExpressions(String(text ?? "")), true);
  };

  for (const match of source.matchAll(/\bclass\s*=\s*(?:"([^"]*)"|'([^']*)')/g)) {
    const value = match[1] ?? match[2] ?? "";
    const variable = value.match(/^\$\{(\w+)\}$/);
    if (!variable) {
      record(value);
      continue;
    }
    // `class="${classNames}"` — follow the variable to its template, one level.
    const assignment = source.match(new RegExp(String.raw`(?:const|let|var)\s+${variable[1]}\s*=\s*` + "`([\\s\\S]*?)`"));
    record(assignment ? assignment[1] : "");
  }
  for (const match of source.matchAll(/classList\s*\.\s*(?:add|remove|toggle)\s*\(([^)]*)\)/g)) record(match[1]);
  return { classes, families };
}

const emitted = new Set();
const families = new Set();
for (const file of [...jsFiles, indexHtml]) {
  const found = scanClasses(readFileSync(file, "utf8"));
  for (const name of found.classes) emitted.add(name);
  for (const name of found.families) families.add(name);
}

/** Every string literal in the app, for proving that a parameterised family has
 *  a value that can complete each member the stylesheet defines. */
const literals = new Set();
for (const file of [...jsFiles, indexHtml]) {
  const body = readFileSync(file, "utf8");
  for (const match of body.matchAll(/"([^"]*)"/g)) literals.add(match[1]);
  for (const match of body.matchAll(/'([^']*)'/g)) literals.add(match[1]);
  for (const match of body.matchAll(/`([^`]*)`/g)) literals.add(match[1]);
}

const declared = new Set([...allCss.matchAll(/\.(dhun-[a-zA-Z0-9_-]+)/g)].map((match) => match[1]));

test("every class the app builds has a rule in a stylesheet", () => {
  const unstyled = [...emitted].filter((className) => !declared.has(className));
  assert.deepEqual(unstyled, [], `used by the app, defined nowhere: ${unstyled.join(", ")}`);
});

test("no stylesheet ships a rule the app cannot reach", () => {
  const mentioned = new Set(emitted);
  for (const file of [...jsFiles, indexHtml]) {
    const body = readFileSync(file, "utf8");
    for (const match of body.matchAll(CLASS_TOKEN)) {
      if (!FILE_LIKE.test(match[0])) mentioned.add(match[0]);
    }
  }
  const orphans = [];
  for (const className of declared) {
    if (mentioned.has(className)) continue;
    // A parameterised family is reachable when the app holds a value that can
    // complete the name: `.dhun-art--hero` needs a `"hero"` call site.
    const family = [...families].find((prefix) => className.startsWith(prefix) && literals.has(className.slice(prefix.length)));
    if (!family) orphans.push(className);
  }
  assert.deepEqual(orphans.sort(), [], `dead CSS: ${orphans.join(", ")}`);
});

test("the app has no raw colour and no raw pixel value outside the token file", () => {
  for (const file of cssFiles) {
    if (path.basename(file) === "tokens.css") continue;
    const body = readFileSync(file, "utf8")
      .replace(/@media[^{]*\{/g, "@media {")
      .replace(/\/\*[\s\S]*?\*\//g, "");
    assert.deepEqual([...body.matchAll(/#[0-9a-fA-F]{3,8}\b/g)].map((m) => m[0]), [], `${path.basename(file)} has a raw colour`);
    const pixels = [...body.matchAll(/:\s*[^;{}]*?\d+(?:\.\d+)?px/g)].map((m) => m[0]);
    assert.deepEqual(pixels, [], `${path.basename(file)} has a raw pixel value: ${pixels.join(", ")}`);
  }
});

test("no module writes an inline pixel value either", () => {
  for (const file of jsFiles) {
    const body = readFileSync(file, "utf8");
    const offenders = [...body.matchAll(/style="[^"]*?\d+(?:\.\d+)?px[^"]*"/g)].map((m) => m[0]);
    assert.deepEqual(offenders, [], `${path.basename(file)}: ${offenders.join(", ")}`);
  }
});

test("the token file still carries both themes and all six accent ramps", () => {
  assert.match(tokensCss, /:root,\s*\n\[data-dhun-theme="dark"\]/);
  assert.match(tokensCss, /\[data-dhun-theme="light"\]/);
  // `brand` is the default ramp, declared on the theme blocks themselves; the
  // other five are overrides, so each needs its own selector in both themes.
  for (const theme of ["dark", "light"]) {
    assert.ok(
      tokensCss.includes(`--dhun-accent-brand-${theme}:`),
      `the ${theme} theme has no brand accent to fall back to`,
    );
  }
  for (const accent of ["azure", "jade", "amber", "rose", "cyan"]) {
    assert.ok(tokensCss.includes(`--dhun-accent-${accent}-light:`), `${accent} has no light ramp`);
    assert.ok(tokensCss.includes(`--dhun-accent-${accent}-dark:`), `${accent} has no dark ramp`);
    assert.ok(tokensCss.includes(`[data-dhun-theme="dark"][data-dhun-accent="${accent}"]`), `${accent} is not selectable in the dark theme`);
    assert.ok(tokensCss.includes(`[data-dhun-theme="light"][data-dhun-accent="${accent}"]`), `${accent} is not selectable in the light theme`);
  }
});

test("every custom property the stylesheets read is defined", () => {
  const defined = new Set([...tokensCss.matchAll(/(--dhun-[a-z0-9-]+)\s*:/g)].map((match) => match[1]));
  const used = new Set();
  for (const file of cssFiles) {
    const body = readFileSync(file, "utf8");
    for (const match of body.matchAll(/var\((--dhun-[a-z0-9-]+)/g)) used.add(match[1]);
  }
  const missing = [...used].filter((token) => !defined.has(token));
  assert.deepEqual(missing, [], `undefined design tokens: ${missing.join(", ")}`);
});

test("the breakpoints in the stylesheet match the breakpoints in the tokens", () => {
  const twoPane = tokensCss.match(/--dhun-breakpoint-two-pane:\s*(\d+)px/);
  const widePlayer = tokensCss.match(/--dhun-breakpoint-wide-player:\s*(\d+)px/);
  assert.ok(twoPane && widePlayer, "the breakpoint tokens are missing");
  assert.ok(appCss.includes(`min-width: ${twoPane[1]}px`), "the two-pane breakpoint is not used");
  assert.ok(appCss.includes(`min-width: ${widePlayer[1]}px`), "the wide-player breakpoint is not used");
});

test("the page declares the CSP, the manifest and the viewport", () => {
  // The policy itself, not the whole document: the comment above it explains
  // what the app refuses to allow, and naming a directive is not using it.
  const policy = html.match(/http-equiv="Content-Security-Policy"\s*\n?\s*content="([^"]*)"/);
  assert.ok(policy, "index.html has no Content-Security-Policy");
  for (const directive of ["default-src 'none'", "script-src 'self'", "base-uri 'none'"]) {
    assert.ok(policy[1].includes(directive), `the policy is missing ${directive}`);
  }
  // `frame-ancestors` is a response-header directive and a meta tag is required
  // to ignore it, so declaring it here would be dead configuration that logs a
  // console error on every load. The app enforces the rule itself instead; the
  // test below reads the guard, and the browser job proves it in a real frame.
  assert.ok(
    !policy[1].includes("frame-ancestors"),
    "frame-ancestors cannot be delivered from a meta tag — it must not be declared there",
  );
  assert.ok(!policy[1].includes("unsafe-eval"), "the policy must not allow eval");
  assert.ok(!policy[1].includes("unsafe-inline'") || policy[1].match(/script-src 'self'/), "scripts must not be inline");

  // The app shell is what a crawler would get: an empty #app and a module graph
  // that crawlers mostly will not run. `scripts/website_smoke.py` asserts this
  // on the served bytes after a deploy; asserting it here means a rewrite cannot
  // quietly publish the app into search results again.
  assert.match(html, /<meta name="robots" content="noindex, nofollow" \/>/);
  assert.match(html, /rel="manifest" href="\.\/manifest\.webmanifest"/);
  assert.match(html, /name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"/);
  assert.match(html, /<script type="module" src="\.\/js\/main\.js"><\/script>/);
  assert.match(html, /<noscript>/, "scripting-off gets an explanation, not a blank page");
});

test("nothing in the app loads a third-party script, style or font", () => {
  for (const file of jsFiles) {
    const body = readFileSync(file, "utf8");
    assert.ok(
      !/cdn\.|unpkg|jsdelivr|googleapis\.com\/ajax|@import\s+url|@font-face/.test(body),
      `${path.basename(file)} pulls in a third-party asset`,
    );
  }
  // Loading tags only. A link to the project's own source is attribution, not a
  // dependency, and the About screen is supposed to carry it.
  const loadingTags = [...html.matchAll(/<(?:script|link|img|iframe|audio|video|source)\b[^>]*>/gi)].map((match) => match[0]);
  const external = loadingTags.filter((tag) => /(?:src|href)="https?:\/\//.test(tag));
  assert.deepEqual(external, [], "the document must not load anything from another origin");
});

test("the entry point boots itself", () => {
  // The mirror build shipped a main.js that exported `boot` and never called it,
  // and the app rendered nothing in a real browser for a whole CI cycle. The
  // same rule is asserted here and in scripts/test_app_web.py.
  const main = readFileSync(path.join(jsRoot, "main.js"), "utf8");
  assert.match(main, /^boot\(\);$/m);
});

test("every module is listed in the offline shell, and every listed file exists", () => {
  const sw = readFileSync(path.join(srcRoot, "sw.js"), "utf8");
  const block = sw.match(/const SHELL = \[([\s\S]*?)\];/);
  assert.ok(block, "sw.js has no SHELL list");
  const listed = new Set(
    [...block[1].matchAll(/"\.\/([^"]*)"/g)].map((match) => match[1]).map((entry) => entry.replace(/^\.\//, "")),
  );

  for (const file of jsFiles) {
    const relative = path.relative(srcRoot, file).split(path.sep).join("/");
    assert.ok(listed.has(relative), `${relative} is not in the service worker's offline shell`);
  }
  for (const entry of listed) {
    const target = entry === "" ? "index.html" : entry;
    assert.ok(existsSync(path.join(srcRoot, target)), `sw.js caches ${entry}, which does not exist`);
  }
  assert.match(sw, /skipWaiting|skip-waiting/, "an update must be applicable");
  assert.match(sw, /caches\.delete/, "an old cache must be retired");
  assert.ok(
    !/cache\.put\(\s*request[\s\S]{0,400}(music\.youtube|googlevideo|lrclib)/.test(sw),
    "audio and API responses must never be cached as if they were downloads",
  );
});

test("the manifest describes an installable app with real icons", () => {
  const manifest = JSON.parse(readFileSync(path.join(srcRoot, "manifest.webmanifest"), "utf8"));
  assert.equal(manifest.display, "standalone");
  assert.ok(manifest.start_url.startsWith("./"), "a project sub-path install must stay relative");
  assert.ok(manifest.icons.some((icon) => icon.sizes === "192x192"));
  assert.ok(manifest.icons.some((icon) => icon.sizes === "512x512"));
  assert.ok(manifest.icons.some((icon) => icon.purpose === "maskable"), "Android crops a launcher icon to its own shape");
  for (const icon of manifest.icons) {
    const relative = icon.src.replace(/^\.\//, "");
    assert.ok(existsSync(path.join(srcRoot, relative)), `${icon.src} does not exist`);
  }
});

/**
 * The appearance settings theme the *document*, not just the app shell.
 *
 * The regression: `applyTheme()` wrote `data-dhun-theme` onto `#app`, but
 * `tokens.css` keys its palettes on the root element and `body`'s background —
 * the one behind the shell — is painted by an element *above* `#app`. Choosing
 * Light therefore turned the app bar and the nav pale while the backdrop stayed
 * dark, and light-theme ink on it disappeared: the artist meta line and the
 * Shuffle button were literally invisible. The attribute belongs where
 * `index.html` already carries its pre-JavaScript default.
 */
test("the appearance attributes are written to the document element", () => {
  const main = readFileSync(path.join(jsRoot, "main.js"), "utf8");
  assert.match(main, /const themeRoot = doc\.documentElement \?\? root;/, "applyTheme no longer writes to the document element");
  assert.match(main, /themeRoot\.dataset\.dhunTheme = resolved;/, "the resolved theme is not written to the document element");
  assert.match(main, /themeRoot\.dataset\.dhunAccent = settings\.get\("accent"\);/, "the accent is not written to the document element");
  assert.ok(!/root\.dataset\.dhunTheme|root\.dataset\.dhunAccent/.test(main), "an appearance attribute still lands on #app");
});

/**
 * One heading per screen, and it is the screen's own.
 *
 * `AlbumScreen.kt` / `ArtistScreen.kt` / `PlaylistScreen.kt` draw the entity's
 * name in the content. The web wrote the same name into the app bar as an
 * `<h1>` too, so an album page had two `h1`s with identical text, 313px apart
 * on a 1280px window — a duplicate heading for a screen reader and a visibly
 * repeated name for everyone else.
 */
test("the detail screens own their heading and the app bar does not repeat it", () => {
  const main = readFileSync(path.join(jsRoot, "main.js"), "utf8");
  assert.match(
    main,
    /const ENTITY_ROUTES = Object\.freeze\(\["playlist", "artist", "album"\]\);/,
    "the routes that draw their own title are not named",
  );
  assert.match(
    main,
    /const pageOwnsTitle = ENTITY_ROUTES\.includes\(route\.name\);/,
    "nothing decides whether the screen owns its title",
  );
  assert.match(
    main,
    /\$\{pageOwnsTitle \? html`` : html`<h1 class="dhun-topbar__title">\$\{title\}<\/h1>`\}/,
    "the app bar draws its own `<h1>` on a screen that already has one",
  );
  for (const view of ["albumHeader", "artistHeader", "playlistHeader"]) {
    const views = readFileSync(path.join(jsRoot, "views.js"), "utf8");
    assert.match(views, /dhun-entity__title/, `${view} exists but no entity title element does`);
  }
});

/**
 * A player link with nothing to play is answered, not swallowed.
 *
 * `#/?player=open` is what the project site's "Launch /app/ Player" link and
 * any bookmark of an expanded player produce. The overlay only draws once a
 * track is loaded, so before this the request vanished: the visitor landed on
 * Home with no player and no explanation. The notice is derived from the route,
 * so it disappears by itself the moment something plays — and it is placed above
 * the first-run strip, because a strip that always renders would shadow it.
 */
test("an empty player request gets an answer in the dock", () => {
  const main = readFileSync(path.join(jsRoot, "main.js"), "utf8");
  const banner = main.slice(main.indexOf("function renderBanner(vm)"), main.indexOf("/* The overlay's scroller is a child"));
  assert.match(banner, /state\.route\.player === "open" && !vm\.track/, "nothing notices a player link with no track");
  assert.match(banner, /title: "Nothing is playing yet"/, "the empty state has no title");
  assert.match(banner, /The player opens on a track/, "the empty state does not say what it is waiting for");
  // Order matters: the first-run strip renders whenever nothing else does, so a
  // rule placed after it can never fire.
  const emptyAt = banner.indexOf('state.route.player === "open" && !vm.track');
  const welcomeAt = banner.indexOf('state.banner?.kind === "info"');
  assert.ok(emptyAt > 0 && welcomeAt > 0 && emptyAt < welcomeAt, "the empty-player notice is shadowed by the first-run strip");
});
