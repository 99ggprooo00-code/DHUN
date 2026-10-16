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
# enum, `data/sample-catalog.js` from the demo fixture. They obey the same rules
# as hand-written modules except where a rule is about authorship.
GENERATED = {"icons.js"}


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def _js_sources() -> dict[str, str]:
    return {
        file.name: _read(file)
        for file in sorted(JS_DIR.rglob("*.js"))
        if file.name not in GENERATED
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
    start = main_js.index("const handlers = {")
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
            r"(?m)^boot\(\);\s*$",
            "main.js does not call boot() at top level — the browser loads the "
            "module and then gets an empty #app",
        )

    def test_a_strict_content_security_policy_is_declared(self) -> None:
        policy = re.search(r'http-equiv="Content-Security-Policy"\s*\n?\s*content="([^"]*)"', self.index_html)
        self.assertIsNotNone(policy, "index.html has no Content-Security-Policy")
        body = policy.group(1)
        for directive in ("default-src 'none'", "script-src 'self'", "base-uri 'none'", "frame-ancestors 'none'"):
            self.assertIn(directive, body, f"the policy is missing {directive}")
        self.assertNotIn("unsafe-eval", body, "the policy must not allow eval")

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
        self.assertIn("liked", tabs.group(1))
        self.assertIn("history", tabs.group(1))
        # A bookmark to the screen that used to exist still lands somewhere real.
        self.assertIn('#/library/downloads', router, "the old link must redirect, not 404")

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
        views = _read(JS_DIR / "views.js")
        self.assertIn("Demo catalogue", views)
        self.assertIn("no audio", views)
        source = _read(JS_DIR / "source.js")
        # Stream resolution never falls back to demo rows: a fictional track
        # must fail loudly, not play silently as a success.
        self.assertRegex(
            source,
            r"resolveStream:\s*\(videoId\)\s*=>\s*run\([^;]*allowDemo:\s*false",
            "stream resolution must not fall back to the demo catalogue",
        )

    def test_a_failed_stream_says_why(self) -> None:
        source = _read(JS_DIR / "source.js")
        for kind in ("network-or-cors", "timeout", "http", "shape"):
            self.assertIn(kind, source, f"the failure classifier lost the {kind} case")
        views = _read(JS_DIR / "views.js")
        self.assertIn("dhun-row__failure", views, "a row that could not resolve keeps its place and its reason")


@unittest.skipUnless(APP_WEB.is_dir(), "app-web/ does not exist")
class HonestyIsInTheRenderedCopy(unittest.TestCase):
    """ADR-008 boundary 4: no proxy, no unproven claim, no silence about limits.

    Every claim asserted here is asserted twice: once as copy the visitor can
    read, and once as the code that makes the copy true. A disclosure that
    outruns the implementation is the failure mode this class exists to catch.
    """

    @classmethod
    def setUpClass(cls) -> None:
        cls.views = _read(JS_DIR / "views.js")
        cls.legal = _read(JS_DIR / "data" / "legal-content.js")
        cls.main = _read(JS_DIR / "main.js")

    def test_the_app_states_it_runs_no_server(self) -> None:
        self.assertIn("no DHUN server", self.views)
        self.assertIn("no DHUN-operated server", self.legal)

    def test_the_app_states_it_is_not_affiliated_with_google(self) -> None:
        body = " ".join(self.views.split())
        self.assertIn("not affiliated with Google", body)

    def test_lyrics_traffic_is_disclosed_with_a_real_off_switch(self) -> None:
        """LRCLIB is named, its endpoint is the one the code calls, and the
        request sits behind the setting the page names."""
        self.assertIn("LRCLIB", self.legal)
        self.assertIn("lrclib.net", self.legal)
        self.assertIn('const LYRICS_ENDPOINT = "https://lrclib.net/api"', self.main)
        guard = self.main.index('if (!settings.get("lyricsEnabled"))')
        request = self.main.index("fetchImpl(", guard)
        self.assertLess(guard, request, "the lyrics request is not gated by the setting the page names")
        self.assertIn('credentials: "omit"', self.main, "a lyrics request that sends credentials contradicts the page")

    def test_no_request_carries_the_visitor_anywhere_but_a_named_service(self) -> None:
        """No DHUN-operated host. Every literal origin in the app source is one of
        the services the bundled pages name."""
        origins = set(re.findall(r"https://([a-z0-9.-]+)", self.main + _read(JS_DIR / "source.js")))
        self.assertNotIn("dhun", " ".join(origins).lower())
        for origin in origins:
            self.assertTrue(
                origin.endswith(("youtube.com", "googlevideo.com", "googleapis.com", "ytimg.com", "lrclib.net", "picsum.photos")),
                f"{origin} is not a service the bundled pages disclose",
            )

    def test_the_source_of_every_row_is_on_screen(self) -> None:
        self.assertIn("dhun-source", self.views, "the source badge disappeared from the interface")
        for kind, label in (("innertube", "YouTube Music"), ("endpoint", "Your endpoint"), ("demo", "Demo data")):
            self.assertIn(kind, self.views)
            self.assertIn(label, self.views)

    def test_a_browser_without_web_audio_is_told_so(self) -> None:
        self.assertIn("no Web Audio API", self.views)
        self.assertIn("not saving your library", self.views)

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
        generated = _read(JS_DIR / "icons.js")
        declared = set(re.findall(r"^\s{2}([A-Z][A-Za-z0-9_]*): \"", generated, re.M))
        main_js = _read(JS_DIR / "main.js")
        match = re.search(r"TAB_ICON\s*=\s*(?:Object\.freeze\()?\{(.*?)\}", main_js, re.S)
        self.assertIsNotNone(match, "the tab→icon map is missing")
        tab_icons = set(re.findall(r'"([A-Z][A-Za-z0-9_]*)"', match.group(1)))
        self.assertTrue(tab_icons, "the tab→icon map is empty")
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
