"""Contract tests for `app-web/`, the browser mirror of the DHUN interface.

Why these live in Python: app CI step 1 is
`python3 -m unittest discover -s scripts -p 'test_*.py'` — a pure-Python,
network-free, Node-free gate. Node is available in the sandbox but is not
guaranteed on that runner, so every rule the repository must enforce on every
push is asserted here too. `app-web/tests/*.test.mjs` is the faster local
loop; the rules that matter are duplicated deliberately rather than left to
whichever runner happens to execute.

What is enforced:

* the design tokens are a **mirror** of the Compose source, not a palette
  someone typed (every colour traces to DhunAppearance.kt);
* no raw hex and no raw px outside `tokens.css` — the web app inherits
  DhunColors.kt's own rule ("no raw hex values exist outside design/");
* no third-party runtime asset: no external script, stylesheet, font or
  image, nothing from node_modules;
* the page is `noindex` and carries a strict Content-Security-Policy;
* the honesty notices (ADR-008 boundary 4) are present in the built source;
* the generated icon file still matches `DhunIcons.kt`.
"""

from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
APP_WEB = REPO_ROOT / "app-web"
CSS_DIR = APP_WEB / "src" / "css"
JS_DIR = APP_WEB / "src" / "js"
APPEARANCE_KT = (
    REPO_ROOT / "shared" / "src" / "commonMain" / "kotlin" / "dev" / "dhun" / "design" / "DhunAppearance.kt"
)
ICONS_KT = REPO_ROOT / "shared" / "src" / "commonMain" / "kotlin" / "dev" / "dhun" / "design" / "DhunIcons.kt"


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


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
        for js_file in sorted(JS_DIR.rglob("*.js")):
            for match in re.finditer(r"style=\"[^\"]*?\d+(?:\.\d+)?px[^\"]*\"", _read(js_file)):
                offenders.append(f"{js_file.name}: {match.group(0)[:60]}")
        self.assertEqual([], offenders, "inline px — put the value in tokens.css")

    def test_both_theme_sets_and_every_accent_ramp_are_declared(self) -> None:
        self.assertIn('[data-dhun-theme="dark"]', self.tokens)
        self.assertIn('[data-dhun-theme="light"]', self.tokens)
        for accent in ("brand", "azure", "jade", "amber", "rose", "cyan"):
            for mode in ("light", "dark"):
                self.assertIn(f"--dhun-accent-{accent}-{mode}:", self.tokens)


@unittest.skipUnless(APP_WEB.is_dir(), "app-web/ does not exist")
class ShipsNoThirdPartyRuntimeAsset(unittest.TestCase):
    """No CDN, no analytics, no icon library, no webfont file, no stock image."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.index_html = _read(APP_WEB / "src" / "index.html")

    def test_no_external_subresource_is_referenced(self) -> None:
        external = re.findall(r"(?:src|href)\s*=\s*\"(https?://[^\"]+)\"", self.index_html)
        self.assertEqual([], external, "the page must not request anything from another origin")

    def test_no_imported_stylesheet_or_font(self) -> None:
        for css in sorted(CSS_DIR.glob("*.css")):
            imports = re.findall(r"@import\s+url\(", _read(css))
            faces = re.findall(r"@font-face", _read(css))
            self.assertEqual([], imports, f"{css.name}: no @import of a third-party sheet")
            self.assertEqual([], faces, f"{css.name}: no webfont file ships")

    def test_no_packaged_dependency_directory_is_committed(self) -> None:
        self.assertFalse((APP_WEB / "node_modules").exists(), "node_modules must never be committed")
        self.assertFalse((APP_WEB / "dist").exists(), "build output must never be committed")

    def test_the_build_output_is_ignored(self) -> None:
        gitignore = APP_WEB / ".gitignore"
        self.assertTrue(gitignore.is_file(), "app-web/.gitignore is missing")
        body = _read(gitignore)
        for entry in ("node_modules/", "dist/"):
            self.assertIn(entry, body, f"{entry} must stay ignored")


@unittest.skipUnless(APP_WEB.is_dir(), "app-web/ does not exist")
class PageHygieneAndHonesty(unittest.TestCase):
    """ADR-008 boundary 4: no Web-support claim, no silent preview."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.index_html = _read(APP_WEB / "src" / "index.html")
        cls.views = _read(JS_DIR / "views.js")

    def test_the_preview_is_noindex(self) -> None:
        self.assertRegex(self.index_html, r'<meta\s+name="robots"\s+content="noindex')

    def test_a_strict_content_security_policy_is_declared(self) -> None:
        self.assertIn("default-src 'none'", self.index_html)
        self.assertIn("script-src 'self'", self.index_html)
        self.assertIn("base-uri 'none'", self.index_html)

    def test_the_page_declares_a_title_and_a_viewport(self) -> None:
        self.assertIn("<title>", self.index_html)
        self.assertIn('name="viewport"', self.index_html)

    def test_the_engineering_preview_notice_is_present(self) -> None:
        self.assertIn("Engineering preview", self.views)
        self.assertIn("not proven", self.views)

    def test_the_sample_data_notice_is_present(self) -> None:
        self.assertIn("Sample data", self.views)

    def test_the_preview_notice_forbids_calling_this_a_web_player(self) -> None:
        """The disclaimer has to be in the rendered copy, not only in a doc."""
        self.assertIn("Do not describe this as a web player", self.views)
        self.assertIn("produces no sound", self.views)

    def test_no_page_calls_this_a_supported_web_player(self) -> None:
        for phrase in ("DHUN web player", "listen in your browser", "play music in your browser"):
            self.assertNotIn(phrase, self.views, f"'{phrase}' would overstate this build")

    def test_the_entry_module_actually_boots(self) -> None:
        """The module is the entry point, so it must call itself to life.

        `index.html` loads `main.js` as the page's only script; exporting
        `boot` (which the DOM-stub tests call explicitly) is not booting. On
        2026-10-09 the first real browser pass in CI found the app loading,
        defining everything and rendering nothing — `#app` empty on every
        viewport — because nothing ever invoked `boot()`. The stub tests
        cannot see that (they call the export themselves), so the call is
        asserted here, in the gate that runs on every push without a browser.
        """
        main_js = _read(JS_DIR / "main.js")
        self.assertRegex(
            main_js,
            r"(?m)^boot\(\);\s*$",
            "main.js does not call boot() at top level — the browser loads the "
            "module and then gets an empty #app (first caught by the CI browser "
            "pass, 2026-10-09)",
        )


@unittest.skipUnless(APP_WEB.is_dir(), "app-web/ does not exist")
class IconsMatchTheAppSource(unittest.TestCase):
    """icons.js is generated from DhunIcons.kt; drift is a red test."""

    def test_the_icon_file_matches_the_kotlin_enum(self) -> None:
        source = _read(ICONS_KT)
        generated = _read(JS_DIR / "icons.js")
        entries = re.findall(r'^\s{4}([A-Z][A-Za-z0-9_]*)\("([^"]+)"\),\s*$', source, re.M)
        self.assertGreater(len(entries), 20, "the icon enum looks truncated")
        for name, path_data in entries:
            self.assertIn(f'{name}: "', generated, f"{name} is missing from icons.js")
            self.assertIn(path_data, generated, f"{name}'s vector drifted from DhunIcons.kt")

    def test_every_icon_the_views_reference_exists(self) -> None:
        generated = _read(JS_DIR / "icons.js")
        declared = set(re.findall(r"^\s{2}([A-Z][A-Za-z0-9_]*): \"", generated, re.M))
        used: set[str] = set()
        for js_file in sorted(JS_DIR.rglob("*.js")):
            if js_file.name == "icons.js":
                continue
            used.update(re.findall(r"\bicon\(\"([A-Z][A-Za-z0-9_]*)\"", _read(js_file)))
        # icon() also takes the icon name via variables in the shell; those are
        # covered by the TAB_ICON map assertion below.
        missing = sorted(used - declared)
        self.assertEqual([], missing, f"icons used but not in DhunIcons.kt: {missing}")

    def test_the_tab_icon_map_only_names_real_icons(self) -> None:
        generated = _read(JS_DIR / "icons.js")
        declared = set(re.findall(r"^\s{2}([A-Z][A-Za-z0-9_]*): \"", generated, re.M))
        main_js = _read(JS_DIR / "main.js")
        tab_icons = set(re.findall(r"TAB_ICON\s*=\s*\{(.*?)\}", main_js, re.S)[0].split('"')[1::2])
        self.assertTrue(tab_icons, "the tab→icon map is missing")
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
        self.assertGreaterEqual(len(tests), 4, "the app-web suite looks truncated")
        for test_file in tests:
            body = _read(test_file)
            for forbidden in ("require(", "from \"playwright", "from \"jsdom"):
                self.assertNotIn(forbidden, body, f"{test_file.name} pulls in a dependency")


if __name__ == "__main__":
    unittest.main()
