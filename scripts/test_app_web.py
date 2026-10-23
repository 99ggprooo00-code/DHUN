"""Contract tests for `app-web/`, the DHUN web app.

Why these live in Python: CI step 1 is
`python3 -m unittest discover -s scripts -p 'test_*.py'` — a pure-Python,
network-free, Node-free gate. Node is not guaranteed on that runner, so every
rule the repository must enforce on every push is asserted here as well as in
`app-web/tests/*.test.mjs` (which is the faster local loop and can actually
render the screens). The two suites overlap on purpose: a rule that only one
runner checks is a rule that silently stops being checked on the other.

What is enforced:

* the design tokens are a **mirror** of the Compose source, not a palette
  someone typed (every colour traces to DhunAppearance.kt);
* no raw hex and no raw px outside `tokens.css` — the web app inherits
  DhunColors.kt's own rule ("no raw hex values exist outside design/");
* no third-party runtime asset: no external script, stylesheet, font or image,
  nothing from node_modules, no build step that needs a package install;
* the page is a real, installable web app: strict CSP, manifest with a maskable
  icon, a working `<noscript>` explanation, and an entry module that boots;
* the app refuses to run framed — `frame-ancestors` cannot be delivered from a
  meta tag or by GitHub Pages, so the rule is enforced in JavaScript;
* every surface the shell renders into exists, and every control the app emits
  is wired to a handler;
* the things a browser cannot do are **not sold**: no Downloads tab, no cache
  budget, no offline audio, and the demo catalogue is labelled as demo;
* the honesty the repository requires is in the rendered copy: the demo rows
  say they have no audio, a failed stream says why, and the About screen states
  that DHUN runs no server and is not affiliated with Google;
* the generated icon file still matches `DhunIcons.kt`.
"""

from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
APP_WEB = REPO_ROOT / "app-web"
SRC = APP_WEB / "src"
CSS_DIR = SRC / "css"
JS_DIR = SRC / "js"
APPEARANCE_KT = (
    REPO_ROOT / "shared" / "src" / "commonMain" / "kotlin" / "dev" / "dhun" / "design" / "DhunAppearance.kt"
)
ICONS_KT = REPO_ROOT / "shared" / "src" / "commonMain" / "kotlin" / "dev" / "dhun" / "design" / "DhunIcons.kt"

# Files that are generated rather than written: `icons.js` comes from the Kotlin
# enum. They obey the same rules as hand-written modules except where a rule is
# about authorship.
GENERATED = {"icons.js"}

# `data/legal-content.js` is the Android/desktop legal corpus, which
# `scripts/gen_legal_content.py` emits into this tree so the app's own gate can
# prove it is current. It is deliberately **not** part of the web build: the
# browser has its own pages (`data/web-legal.js`) because the installed app's
# documents describe downloads, a SQLite database and YouTube. Both gates that
# scan `src/js` know it is not this app's code.
NOT_PART_OF_THE_WEB_BUILD = {"data/legal-content.js"}


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def _js_sources() -> dict[str, str]:
    return {
        file.relative_to(JS_DIR).as_posix(): _read(file)
        for file in sorted(JS_DIR.rglob("*.js"))
        if file.name not in GENERATED
        and file.relative_to(SRC / "js").as_posix() not in NOT_PART_OF_THE_WEB_BUILD
    }


def _colour_literals(css: str) -> list[str]:
    """Every colour in `css` as the hex string Compose uses (RRGGBB / AARRGGBB)."""
    found = [match.group(1).upper() for match in re.finditer(r"#([0-9a-fA-F]{6})\b", css)]
    for r, g, b, a in re.findall(
        r"rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)", css
    ):
        alpha = format(int(round(float(a) * 255)), "02X")
        rgb = "".join(format(int(part), "02X") for part in (r, g, b))
        found.append(f"{alpha}{rgb}")
    return found


def _tracked_under(*paths: str) -> list[str] | None:
    """Files git tracks under `paths`, or None when this is not a git checkout."""
    import subprocess

    try:
        result = subprocess.run(
            ["git", "ls-files", "--", *paths],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            timeout=30,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if result.returncode != 0:
        return None
    return [line for line in result.stdout.splitlines() if line.strip()]


def _handled_actions(main_js: str) -> set[str]:
    """The action names `main.js` answers, from its handler map."""
    start = main_js.index("const actions = {")
    body = main_js[start : main_js.index("\n  };", start)]
    pattern = re.compile(
        r"\n\s{4}(?:\"([a-z][a-z0-9-]*)\"|'([a-z][a-z0-9-]*)'|([a-z][a-z0-9-]*))\s*:\s*(?:async\s*)?\("
    )
    return {match.group(1) or match.group(2) or match.group(3) for match in pattern.finditer(body)}


@unittest.skipUnless(APP_WEB.is_dir(), "app-web/ does not exist")
class DesignTokensMirrorTheApp(unittest.TestCase):
    """The web app is a mirror. These tests are what keeps it one."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.tokens = _read(CSS_DIR / "tokens.css")
        cls.app_css = _read(CSS_DIR / "app.css")
        cls.appearance = _read(APPEARANCE_KT).upper()

    def test_every_colour_traces_to_the_kotlin_token_source(self) -> None:
        literals = _colour_literals(self.tokens)
        self.assertGreater(len(literals), 40, "the palette looks truncated")
        untraced = [value for value in literals if value not in self.appearance]
        self.assertEqual(
            [],
            untraced,
            "colours in tokens.css that do not exist in DhunAppearance.kt were invented, not mirrored",
        )

    def test_no_raw_hex_outside_the_token_file(self) -> None:
        for css in sorted(CSS_DIR.glob("*.css")):
            if css.name == "tokens.css":
                continue
            hexes = re.findall(r"#[0-9a-fA-F]{3,8}\b", _read(css))
            self.assertEqual([], hexes, f"{css.name}: raw hex colours belong in tokens.css")

    def test_no_module_writes_a_colour_into_javascript(self) -> None:
        """The one place a canvas needs a colour, it reads it from the stylesheet."""
        for name, body in _js_sources().items():
            hexes = re.findall(r"#[0-9a-fA-F]{6}\b", body)
            self.assertEqual([], hexes, f"{name}: a colour in JavaScript bypasses the design system")

    def test_no_raw_pixel_values_outside_the_token_file(self) -> None:
        for css in sorted(CSS_DIR.glob("*.css")):
            if css.name == "tokens.css":
                continue
            # @media conditions cannot use custom properties, so the two
            # breakpoint literals are the one documented exception. Comments
            # are prose and are stripped before the scan.
            body = re.sub(r"@media[^{]*\{", "@media {", _read(css))
            body = re.sub(r"/\*[\s\S]*?\*/", "", body)
            raw = re.findall(r":\s*[^;{}]*?\d+(?:\.\d+)?px", body)
            self.assertEqual([], raw, f"{css.name}: raw px belongs in tokens.css ({raw[:3]})")

    def test_the_breakpoint_literals_match_the_token_file(self) -> None:
        for token, literal in (
            ("--dhun-breakpoint-two-pane", "840px"),
            ("--dhun-breakpoint-wide-player", "480px"),
        ):
            declared = re.search(rf"{token}:\s*(\d+)px", self.tokens)
            self.assertIsNotNone(declared, f"{token} is missing from tokens.css")
            self.assertIn(
                f"min-width: {literal}",
                self.app_css,
                f"app.css must use {literal} where tokens.css declares {token}={declared.group(1)}px",
            )

    def test_inline_styles_use_tokens_too(self) -> None:
        offenders: list[str] = []
        for name, body in _js_sources().items():
            for match in re.finditer(r"style=\"[^\"]*?\d+(?:\.\d+)?px[^\"]*\"", body):
                offenders.append(f"{name}: {match.group(0)[:60]}")
        self.assertEqual([], offenders, "inline px — put the value in tokens.css")

    def test_both_theme_sets_and_every_accent_ramp_are_declared(self) -> None:
        self.assertIn('[data-dhun-theme="dark"]', self.tokens)
        self.assertIn('[data-dhun-theme="light"]', self.tokens)
        for accent in ("brand", "azure", "jade", "amber", "rose", "cyan"):
            for mode in ("light", "dark"):
                self.assertIn(
                    f"--dhun-accent-{accent}-{mode}:",
                    self.tokens,
                    f"the {accent} ramp has no {mode} value",
                )
        # Five of the six are overrides selected by a data attribute; `brand` is
        # the default the theme blocks themselves declare.
        for accent in ("azure", "jade", "amber", "rose", "cyan"):
            self.assertIn(
                f'[data-dhun-theme="dark"][data-dhun-accent="{accent}"]',
                self.tokens,
                f"{accent} cannot be selected in the dark theme",
            )
            self.assertIn(
                f'[data-dhun-theme="light"][data-dhun-accent="{accent}"]',
                self.tokens,
                f"{accent} cannot be selected in the light theme",
            )

    def test_every_custom_property_the_stylesheets_read_is_defined(self) -> None:
        defined = set(re.findall(r"(--dhun-[a-z0-9-]+)\s*:", self.tokens))
        used: set[str] = set()
        for css in sorted(CSS_DIR.glob("*.css")):
            used.update(re.findall(r"var\((--dhun-[a-z0-9-]+)", _read(css)))
        self.assertEqual(set(), used - defined, f"undefined tokens: {sorted(used - defined)}")


@unittest.skipUnless(APP_WEB.is_dir(), "app-web/ does not exist")
class IsARealWebAppNotAPreview(unittest.TestCase):
    """The page has to behave like an application, not like a demo of one."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.index_html = _read(SRC / "index.html")
        cls.manifest = json.loads(_read(SRC / "manifest.webmanifest"))
        cls.main_js = _read(JS_DIR / "main.js")

    def test_the_entry_module_actually_boots(self) -> None:
        """The module is the entry point, so it must call itself to life.

        `index.html` loads `main.js` as the page's only script; exporting `boot`
        is not booting. On 2026-10-09 the first real browser pass in CI found the
        app loading, defining everything and rendering nothing — `#app` empty on
        every viewport — because nothing ever invoked `boot()`.
        """
        self.assertRegex(
            self.main_js,
            r"(?m)^\s{2,}boot\(\);$",
            "main.js does not call boot() at module scope — the browser loads "
            "the module and then gets an empty #app",
        )
        # The call is guarded so the test runner can import the module's pure
        # helpers without a document. The guard must still fire on the real
        # page, and the page is the one that has `#app`.
        self.assertIn('document?.querySelector?.("#app")', self.main_js)
        self.assertIn("DOMContentLoaded", self.main_js)

    def test_a_strict_content_security_policy_is_declared(self) -> None:
        policy = re.search(r'http-equiv="Content-Security-Policy"\s*\n?\s*content="([^"]*)"', self.index_html)
        self.assertIsNotNone(policy, "index.html has no Content-Security-Policy")
        body = policy.group(1)
        for directive in ("default-src 'none'", "script-src 'self'", "base-uri 'none'"):
            self.assertIn(directive, body, f"the policy is missing {directive}")
        self.assertNotIn("unsafe-eval", body, "the policy must not allow eval")
        # `frame-ancestors` only works as a response header, and a meta tag is
        # required to ignore it: declaring it here is dead configuration that
        # logs "The Content Security Policy directive 'frame-ancestors' is
        # ignored when delivered via a <meta> element" on every page load, which
        # the browser job reports as a console problem. The app refuses to boot
        # inside a frame instead (test_framing_is_refused_in_javascript below).
        self.assertNotIn(
            "frame-ancestors",
            body,
            "frame-ancestors cannot be delivered from a meta tag — enforce it in the app",
        )

    def test_every_cover_comes_from_a_host_img_src_allows(self) -> None:
        """The page must not ask for an image the policy will refuse.

        The catalogue answers with JioSaavn's own generic artist art
        (`https://www.jiosaavn.com/_i/3.0/artist-default-music.png`), which is
        not a cover and is not on `*.saavncdn.com`. Putting it in an `<img>`
        produced one blocked request per artist row and a console full of CSP
        violations, which the browser job reported on the first deploy of this
        build (website run 39376581609, 2026-10-20). `cdnImage()` drops it in
        normalisation, so the drawn initial renders instead. This test keeps
        the two halves — the policy and the allowlist — from drifting apart:
        widening `img-src` without teaching `cdnImage`, or the reverse, fails.
        """
        policy = re.search(r"img-src ([^;]*)", self.index_html)
        self.assertIsNotNone(policy, "index.html declares no img-src")
        allowed = re.findall(r"https://([a-z0-9*.-]+)", policy.group(1))
        self.assertIn("*.saavncdn.com", allowed, "the CDN the catalogue serves covers from is not allowed")

        api = _read(JS_DIR / "api.js")
        self.assertIn("export function cdnImage(", api, "api.js has no image allowlist")
        pattern = re.search(r"const IMAGE_URL = /(.+)/i;", api)
        self.assertIsNotNone(pattern, "the image allowlist is not a single, testable pattern")
        for host in allowed:
            suffix = re.escape(host.removeprefix("*."))
            self.assertIn(
                suffix,
                pattern.group(1),
                f"{host} is in img-src but cdnImage would drop or admit it differently",
            )
        # Every cover the normalisers emit goes through the gate. A raw
        # `pickImage()` would put the URL in the page unfiltered.
        self.assertNotIn(
            "image: pickImage(",
            api,
            "a normaliser bypasses cdnImage — the browser would refuse that request",
        )

    def test_framing_is_refused_in_javascript(self) -> None:
        """`frame-ancestors` cannot be sent here, so the app enforces the rule.

        The specification requires a `<meta http-equiv="Content-Security-Policy">`
        to ignore `frame-ancestors` (Chrome logs it as a console problem on every
        load, which the browser job reported until 2026-10-16), and GitHub Pages
        serves a project site with no header control. Removing dead configuration
        is only half a fix: the rule it was meant to state has to hold somewhere,
        or the removal is a downgrade. It holds in `boot()`, before the app
        builds anything at all.
        """
        views_js = _read(JS_DIR / "views.js")
        self.assertIn("export function isFramed(", self.main_js)
        self.assertIn("isFramed(win)", self.main_js, "main.js never asks whether it is framed")
        self.assertIn("views.framedRefusal()", self.main_js)
        self.assertIn("export function framedRefusal(", views_js)
        guard = self.main_js.index("isFramed(win)")
        for later in ("createStorage(", "createSource(", "root.innerHTML = html`${views.framedRefusal()}"):
            self.assertLess(
                guard,
                self.main_js.index(later),
                "the framing guard runs after the app has already touched storage or the DOM",
            )

    def test_the_rail_pins_its_items_to_its_own_width(self) -> None:
        """The desktop rail is as wide as the app's NavigationRail, and stays it.

        Measured 2026-10-16 (browser job of website run 38850405071): the rail's
        item row was 90px inside a 79px rail at 1280x800, which the browser job's
        `overflowReport` reports as spilling with each child's width. An item
        sized by its longest label moves the page's own content column when a
        translation changes; the label wraps inside the rail instead of widening
        it.
        """
        app_css = _read(CSS_DIR / "app.css")
        tokens = _read(CSS_DIR / "tokens.css")
        self.assertIn("--dhun-space-navigation-rail-width:", tokens, "the rail has no width of its own")
        rail = re.search(r"\.dhun-rail-nav \{\s*display: none;([^}]*)\}", app_css)
        self.assertIsNotNone(rail, "app.css has no rail-nav rule")
        self.assertIn("width: var(--dhun-space-navigation-rail-width)", rail.group(1))
        block = re.search(r"\.dhun-rail-nav__item \{([^}]*)\}", app_css)
        self.assertIsNotNone(block, "app.css has no rule pinning the rail's items to the rail")
        self.assertIn("width: 100%", block.group(1))
        label = re.search(r"\.dhun-rail-nav__label \{([^}]*)\}", app_css)
        self.assertIsNotNone(label, "the rail's labels need a rule of their own")
        self.assertIn("white-space: normal", label.group(1), "a rail label wraps rather than clipping")

    def test_the_app_shell_stays_out_of_the_search_index(self) -> None:
        """`/app/` is a shell, not a page to rank, and the deploy check agrees.

        What a crawler receives is an empty `<div id="app">` and a module graph;
        an app shell in search results is a result that shows nothing until it
        runs. `scripts/website_smoke.py` asserts this on the served bytes after
        a deploy to `main` — a job the pull request never runs — so a rewrite
        can drop the line, pass every PR check, and only fail once merged. It
        is asserted here as well for that reason.
        """
        self.assertRegex(
            self.index_html,
            r'<meta name="robots" content="noindex, nofollow"\s*/>',
            "the web app must stay noindex, as the served-site smoke check asserts",
        )

    def test_the_page_declares_a_title_viewport_and_manifest(self) -> None:
        self.assertIn("<title>", self.index_html)
        self.assertIn('name="viewport"', self.index_html)
        self.assertIn('rel="manifest"', self.index_html)
        self.assertEqual("standalone", self.manifest.get("display"))
        self.assertTrue(self.manifest.get("start_url", "").startswith("./"), "installs under /DHUN/app/")

    def test_the_manifest_icons_exist_and_one_is_maskable(self) -> None:
        icons = self.manifest.get("icons", [])
        self.assertTrue(icons, "an installable app needs icons")
        for icon in icons:
            self.assertTrue((SRC / icon["src"].replace("./", "")).is_file(), f"{icon['src']} does not exist")
        self.assertTrue(
            any(icon.get("purpose") == "maskable" for icon in icons),
            "Android crops a launcher icon to its own shape — ship a maskable one",
        )

    def test_scripting_off_still_explains_itself(self) -> None:
        self.assertIn("<noscript>", self.index_html)
        self.assertRegex(self.index_html, r"<noscript>[\s\S]{100,}?</noscript>", "the noscript block is a stub")

    def test_every_region_the_shell_renders_into_exists(self) -> None:
        regions = set(re.findall(r'setRegion\(\s*"([^"]+)"', self.main_js))
        self.assertGreaterEqual(len(regions), 6, f"the shell should have several regions, found {sorted(regions)}")
        declared = set(re.findall(r'\bid="([^"]+)"', self.index_html)) | set(
            re.findall(r'\bid="([^"]+)"', self.main_js)
        )
        self.assertEqual(set(), regions - declared, f"the shell has no element for: {sorted(regions - declared)}")

    def test_every_control_written_in_the_chrome_is_wired(self) -> None:
        handled = _handled_actions(self.main_js)
        emitted = set(re.findall(r'data-action="([^"$]+)"', self.main_js))
        self.assertTrue(emitted, "the chrome emits no actions at all")
        self.assertEqual(
            set(),
            emitted - handled,
            f"chrome controls with no handler: {sorted(emitted - handled)}",
        )

    def test_every_module_is_in_the_offline_shell(self) -> None:
        shell = _read(SRC / "sw.js")
        listed = set(re.findall(r'"\./([^"]*)"', shell))
        for file in sorted(JS_DIR.rglob("*.js")):
            relative = file.relative_to(SRC).as_posix()
            if relative.removeprefix("js/") in NOT_PART_OF_THE_WEB_BUILD:
                self.assertNotIn(
                    relative,
                    listed,
                    f"{relative} is the app's legal corpus and must not ship in the web build",
                )
                continue
            self.assertIn(relative, listed, f"{relative} is not cached by the service worker")
        for entry in listed:
            target = SRC / (entry if entry else "index.html")
            self.assertTrue(target.is_file(), f"sw.js caches {entry or './'}, which does not exist")


@unittest.skipUnless(APP_WEB.is_dir(), "app-web/ does not exist")
class ShipsNoThirdPartyRuntimeAsset(unittest.TestCase):
    """No CDN, no analytics, no icon library, no webfont file, no stock image."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.index_html = _read(SRC / "index.html")

    def test_no_external_subresource_is_loaded(self) -> None:
        # Loading tags only: the page links to its own repository, which is
        # attribution rather than a dependency.
        for tag in re.findall(r"<(?:script|link|img|iframe|audio|video|source)\b[^>]*>", self.index_html, re.I):
            self.assertIsNone(
                re.search(r'(?:src|href)="https?://', tag),
                f"the page loads a third-party subresource: {tag.strip()[:80]}",
            )

    def test_no_imported_stylesheet_or_font(self) -> None:
        for css in sorted(CSS_DIR.glob("*.css")):
            imports = re.findall(r"@import\s+url\(", _read(css))
            faces = re.findall(r"@font-face", _read(css))
            self.assertEqual([], imports, f"{css.name}: no @import of a third-party sheet")
            self.assertEqual([], faces, f"{css.name}: no webfont file ships")

    def test_no_build_output_or_dependency_directory_is_tracked(self) -> None:
        """A build is expected to exist locally; what must never happen is that
        one gets committed. `git ls-files` answers that question exactly, and the
        check stands down when the tree is not a git checkout (a tarball)."""
        tracked = _tracked_under("app-web/node_modules", "app-web/dist")
        if tracked is None:
            self.skipTest("not a git checkout")
        self.assertEqual([], tracked, f"generated files are tracked in git: {tracked[:5]}")

    def test_the_build_output_is_ignored(self) -> None:
        gitignore = APP_WEB / ".gitignore"
        self.assertTrue(gitignore.is_file(), "app-web/.gitignore is missing")
        body = _read(gitignore)
        for entry in ("node_modules/", "dist/"):
            self.assertIn(entry, body, f"{entry} must stay ignored")


@unittest.skipUnless(APP_WEB.is_dir(), "app-web/ does not exist")
class DoesNotSellWhatABrowserCannotDo(unittest.TestCase):
    """Removed with the mirror build, and must not come back.

    A browser tab cannot keep a signed stream URL working offline, cannot
    promise a file the visitor owns, and cannot ask a user to budget a cache it
    does not have. The Android build can; this one says so instead of offering
    the button.
    """

    def test_there_is_no_downloads_surface(self) -> None:
        router = _read(JS_DIR / "router.js")
        tabs = re.search(r"LIBRARY_TABS = Object\.freeze\(\[([^\]]*)\]\)", router)
        self.assertIsNotNone(tabs, "LIBRARY_TABS is missing")
        self.assertNotIn("downloads", tabs.group(1), "Downloads cannot be a library tab in a browser")
        self.assertIn("playlists", tabs.group(1))
        self.assertIn("likes", tabs.group(1))
        self.assertIn("history", tabs.group(1))
        # A bookmark to the screen that used to exist still lands somewhere real:
        # an unknown library tab resolves to Playlists rather than to nothing.
        self.assertRegex(
            router,
            r'LIBRARY_TABS\.includes\(tab\) \? tab : "playlists"',
            "an unknown library tab must fall back to a real screen",
        )

    def test_no_cache_budget_setting_survives(self) -> None:
        for name, body in _js_sources().items():
            for token in ("CACHE_OPTIONS_MB", "cacheSizeMb", "cacheOptionLabel"):
                self.assertNotIn(token, body, f"{name} still carries the cache-budget setting")

    def test_no_module_offers_offline_audio(self) -> None:
        for name, body in _js_sources().items():
            lowered = body.lower()
            self.assertNotIn("offline playback", lowered, f"{name} promises offline audio")
            self.assertNotRegex(
                body,
                r"download[A-Za-z]*\s*\(\s*[^)]*\)\s*\{[^}]*save",
                f"{name} looks like a file-download path",
            )

    def test_the_demo_catalogue_is_labelled_as_demo(self) -> None:
        """Fictional rows are announced, and they never carry audio.

        The sample catalogue exists so a blocked network shows the shape of the
        app instead of an empty page. Both halves of that bargain are asserted:
        its rows say what they are, and they are declared with an empty stream
        so a press cannot produce a fake success.
        """
        demo = _read(JS_DIR / "demo.js")
        self.assertIn("Sample catalogue", demo)
        self.assertIn("carry no audio", demo)
        self.assertRegex(demo, r'stream:\s*""', "a demo row must not invent a stream URL")
        self.assertIn("demo: true", demo)
        views = _read(JS_DIR / "views.js")
        self.assertIn("Sample catalogue", views, "the screen never repeats the warning the data carries")
        # Pressing play on a demo row says so; it does not start a track.
        player = _read(JS_DIR / "player.js")
        self.assertIn("track?.demo", player, "pressing play on a sample row must say what it is")

    def test_a_failed_stream_says_why(self) -> None:
        source = _read(JS_DIR / "api.js")
        for kind in ("offline", "timeout", "http", "shape", "network"):
            self.assertIn(kind, source, f"the failure classifier lost the {kind} case")
        player = _read(JS_DIR / "player.js")
        self.assertIn("error", player, "the player has no error state")
        ui = _read(JS_DIR / "player-ui.js")
        self.assertIn("errorState(", ui, "the full player draws no error state")
        self.assertIn("vm.error", ui, "the player's own error never reaches the screen")
        # A failure is never silent: the full player draws it, and so does the
        # dock, so a visitor who pressed play and never opened the player still
        # sees why nothing happened.
        self.assertIn("miniError", _read(JS_DIR / "main.js"), "the dock never reports a playback failure")
        self.assertIn("miniError", _read(JS_DIR / "shell.js"))


@unittest.skipUnless(APP_WEB.is_dir(), "app-web/ does not exist")
class HonestyIsInTheRenderedCopy(unittest.TestCase):
    """ADR-008 boundary 4: no proxy, no unproven claim, no silence about limits.

    Every claim asserted here is asserted twice: once as copy the visitor can
    read, and once as the code that makes the copy true. A disclosure that
    outruns the implementation is the failure mode this class exists to catch.

    The copy lives in `data/web-legal.js`, not in the Android corpus. The
    installed app's pages describe downloads, SQLite and YouTube; rendering
    them in a browser tab would state things this build cannot do. The shared
    corpus is still generated and tested by `test_legal_content.py` for the
    app; the web build ships these pages instead.
    """

    @classmethod
    def setUpClass(cls) -> None:
        cls.views = _read(JS_DIR / "views.js")
        cls.legal = _read(JS_DIR / "data" / "web-legal.js")
        cls.main = _read(JS_DIR / "main.js")
        cls.player_ui = _read(JS_DIR / "player-ui.js")
        cls.api = _read(JS_DIR / "api.js")
        cls.index_html = _read(SRC / "index.html")

    def test_the_app_states_it_runs_no_server(self) -> None:
        self.assertIn("no DHUN server", self.legal)
        self.assertIn("no DHUN-operated server", self.legal)
        # The pages are only truthful if they are the ones the screen renders.
        self.assertIn("WEB_LEGAL_DOCUMENTS", self.main)
        self.assertIn("webLegalById", self.main)

    def test_the_app_states_it_is_not_affiliated_with_google(self) -> None:
        """The catalogue's operator, YouTube and Google are all named."""
        body = " ".join(self.legal.split())
        self.assertIn("not affiliated with", body)
        for name in ("JioSaavn", "YouTube", "Google"):
            self.assertIn(name, body, f"the independence notice never names {name}")

    def test_lyrics_are_not_invented_and_no_lyric_service_is_called(self) -> None:
        """There is no lyric endpoint in this build, and the panel says so.

        The previous build fetched lyrics from LRCLIB behind a setting. This
        one has no lyric source at all: the catalogue's API exposes a
        `hasLyrics` flag but no text. The honest interface is therefore an
        explanation, not an empty pane — and no request may quietly reappear.
        """
        body = " ".join(self.player_ui.split())
        self.assertIn("Lyrics are not available in this build.", body)
        self.assertIn("no lyric text", body)
        self.assertNotIn("lrclib", (self.api + self.main).lower(), "no lyric service is called by this build")
        self.assertNotRegex(self.api, r"/lyrics", "the API seam has no lyric path")

    def test_no_request_carries_the_visitor_anywhere_but_a_named_service(self) -> None:
        """No DHUN-operated host. Every literal origin is a disclosed service.

        This is asserted twice: in the source, and in the Content-Security-Policy
        that the browser enforces. A source scan alone would pass a module that
        builds its origin at run time.
        """
        sources = "\n".join(_js_sources().values())
        origins = set(re.findall(r"https://([a-z0-9*.-]+)", sources + self.index_html))
        self.assertNotIn("dhun", " ".join(origins).lower(), "a DHUN-operated origin would be a backend")
        # The catalogue instances the build ships, read out of `api.js` rather
        # than repeated here: a new instance is a change to one list, and the
        # allowlist below says which kinds of community deployment are allowed.
        declared = set(re.findall(r'origin: "https://([a-z0-9*.-]+)"', self.api))
        self.assertGreaterEqual(len(declared), 2, "the catalogue has no failover — one host is a single point of failure")
        # Player services the policy permits (decision 2026-10-22, ADR-010). The
        # policy allows them; no source file uses them yet. Each is listed here
        # so an addition is a reviewed change, not a silent one.
        # Only script and frame hosts are listed: the embedded players need them.
        # Network (connect), image and media hosts are added with the integration
        # that uses them, because a third-party script allowed to run could
        # otherwise reach them.
        player_services = {"www.youtube.com", "s.ytimg.com", "sdk.scdn.co", "js-cdn.music.apple.com"}
        for origin in origins:
            if origin in declared:
                continue
            self.assertTrue(
                origin.endswith(("saavncdn.com", "github.com")) or origin in player_services,
                f"{origin} is neither a declared catalogue instance nor a disclosed service",
            )
        for service in player_services:
            self.assertIn(service, self.index_html, f"{service} is listed as disclosed but is not in the policy")
        player_url = re.compile(r"https?://[^\s\"'`)]*(youtube|ytimg|spotify|scdn\.co|music\.apple|mzstatic|googlevideo)", re.I)
        for name, source in _js_sources().items():
            self.assertIsNone(
                player_url.search(source),
                f"{name} names a player service; the policy allows it but no code may use it yet",
            )
        # github.com appears only as a link in the support copy, never as a
        # fetch target; `connect-src` is the enforceable half.
        policy = re.search(r"connect-src ([^;]*)", self.index_html)
        self.assertIsNotNone(policy, "the policy declares no connect-src")
        connect = policy.group(1)
        for origin in sorted(declared):
            self.assertIn(
                f"https://{origin}",
                connect,
                f"{origin} is requested by api.js but the CSP would block it — the first host would always 'fail'",
            )
        self.assertIn("https://*.saavncdn.com", connect)
        self.assertNotIn("github", connect)

    def test_the_source_of_every_row_is_on_screen(self) -> None:
        """The badge exists, and it names the two possible catalogues."""
        self.assertIn("dhun-source-badge", _read(JS_DIR / "components.js"))
        self.assertIn("sourceBadge", self.views, "the source badge disappeared from the interface")
        self.assertIn("JioSaavn", self.main)
        self.assertIn("Sample catalogue", self.main)
        self.assertIn("Sample catalogue", _read(JS_DIR / "demo.js"))

    def test_a_browser_that_refuses_storage_is_told_so(self) -> None:
        """A private window must not silently pretend the library was saved."""
        self.assertIn("not saving data", self.main)
        self.assertIn("storage.persistent", self.views)
        self.assertIn("persistent", _read(JS_DIR / "library.js"))

    def test_nothing_claims_the_android_or_desktop_builds_ship_here(self) -> None:
        for phrase in ("Download the app", "Install the APK", "Get it on Google Play"):
            self.assertNotIn(phrase, self.views, f"'{phrase}' belongs to the project site, not the web app")


@unittest.skipUnless(APP_WEB.is_dir(), "app-web/ does not exist")
class IconsMatchTheAppSource(unittest.TestCase):
    """icons.js is generated from DhunIcons.kt; drift is a red test."""

    def test_the_icon_file_matches_the_kotlin_enum(self) -> None:
        source = _read(ICONS_KT)
        generated = _read(JS_DIR / "icons.js")
        entries = re.findall(r'^\s{4}([A-Z][A-Za-z0-9_]*)\(\"([^\"]+)\"\),\s*$', source, re.M)
        self.assertGreater(len(entries), 20, "the icon enum looks truncated")
        for name, path_data in entries:
            self.assertIn(f'{name}: "', generated, f"{name} is missing from icons.js")
            self.assertIn(path_data, generated, f"{name}'s vector drifted from DhunIcons.kt")

    def test_every_icon_the_views_reference_exists(self) -> None:
        generated = _read(JS_DIR / "icons.js")
        declared = set(re.findall(r"^\s{2}([A-Z][A-Za-z0-9_]*): \"", generated, re.M))
        used: set[str] = set()
        for name, body in _js_sources().items():
            used.update(re.findall(r"\bicon\(\"([A-Z][A-Za-z0-9_]*)\"", body))
        # icon() also takes the icon name via variables in the shell; those are
        # covered by the TAB_ICON map assertion below.
        missing = sorted(used - declared)
        self.assertEqual([], missing, f"icons used but not in DhunIcons.kt: {missing}")

    def test_the_tab_icon_map_only_names_real_icons(self) -> None:
        """Every navigation tab draws an icon that exists in DhunIcons.kt.

        The map used to live in `main.js` as `TAB_ICON`; the rebuild made it
        part of `NAV_TABS`, so the check follows the data rather than the old
        variable name.
        """
        generated = _read(JS_DIR / "icons.js")
        declared = set(re.findall(r"^\s{2}([A-Z][A-Za-z0-9_]*): \"", generated, re.M))
        router = _read(JS_DIR / "router.js")
        match = re.search(r"NAV_TABS = Object\.freeze\(\[(.*?)\]\)", router, re.S)
        self.assertIsNotNone(match, "the navigation tab list is missing")
        tab_icons = set(re.findall(r'icon: "([A-Z][A-Za-z0-9_]*)"', match.group(1)))
        self.assertGreaterEqual(len(tab_icons), 3, "the navigation is missing tabs")
        self.assertEqual(set(), tab_icons - declared, f"tab icons not in DhunIcons.kt: {sorted(tab_icons - declared)}")


@unittest.skipUnless(APP_WEB.is_dir(), "app-web/ does not exist")
class ModuleHasNoRuntimeDependency(unittest.TestCase):
    def test_the_manifest_declares_no_dependencies(self) -> None:
        manifest = json.loads(_read(APP_WEB / "package.json"))
        shipped = {"dependencies", "devDependencies", "peerDependencies", "optionalDependencies"}
        self.assertEqual(
            set(),
            shipped & set(manifest),
            "a dependency would ship to the browser; this module is dependency-free by design",
        )
        self.assertTrue(manifest.get("private"), "the module is not publishable")
        self.assertEqual("GPL-3.0-or-later", manifest.get("license"))

    def test_the_tests_need_no_dependency_either(self) -> None:
        """`node --test` is built in; the suite must run with no install."""
        tests = sorted((APP_WEB / "tests").glob("*.test.mjs"))
        self.assertGreaterEqual(len(tests), 6, "the app-web suite looks truncated")
        for test_file in tests:
            body = _read(test_file)
            for forbidden in ("require(", 'from "playwright', 'from "jsdom'):
                self.assertNotIn(forbidden, body, f"{test_file.name} pulls in a dependency")

    def test_the_module_has_no_build_step_that_needs_an_install(self) -> None:
        manifest = json.loads(_read(APP_WEB / "package.json"))
        scripts = manifest.get("scripts", {})
        self.assertIn("test", scripts, "the suite must be runnable")
        self.assertNotIn("postinstall", scripts, "nothing may run on install; there is nothing to install")
        for name in ("build", "dev"):
            self.assertIn(name, scripts, f"the {name} script is the documented entry point")


if __name__ == "__main__":
    unittest.main()
