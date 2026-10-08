"""Quality gates for the built site, asserted in CI step 1 (no Node, no network).

`.ai/WEBSITE_PLAN.md` Part A §10 lists the gates: page-weight budget, zero
client-side JavaScript, resolving internal links, labelled mockups, accessibility
floor, per-page metadata, crawlability, responsive rules, token contrast and the
licence notice. The rules live in `website_quality.py`.

As in `test_website_claims.py`, each rule is exercised against synthetic input
that must fail *and* input that must pass, so a rule that stops firing is a red
test — and then the whole set runs against the committed build in
`website/dist/`. A missing build is a failure: this contract is about built
HTML, not about source files looking plausible.
"""

import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import website_quality as quality  # noqa: E402

DIST = quality.DEFAULT_DIST


def write_tree(root: Path, files: dict[str, str]) -> Path:
    for relative, content in files.items():
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    return root


def minimal_site(**overrides) -> dict[str, str]:
    """A tiny but rule-clean site, used as the baseline for mutation tests."""
    page = (
        "<!DOCTYPE html><html lang=\"en\"><head>"
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        "<link rel=\"icon\" href=\"/assets/favicon.svg\">"
        "</head><body><header><nav>menu</nav></header>"
        "<a class=\"skip-link\" href=\"#main\">Skip</a>"
        "<main id=\"main\"><h1>t</h1></main><footer>f</footer></body></html>"
    )
    files = {
        "index.html": page,
        "features/index.html": page,
        "download/index.html": page,
        "assets/styles.css": ":root{--x:1px}",
        "assets/favicon.svg": "<svg xmlns=\"http://www.w3.org/2000/svg\"/>",
    }
    files.update(overrides)
    return files


class WeightBudget(unittest.TestCase):
    def test_over_budget_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), minimal_site())
            (root / "index.html").write_text("x" * (61 * 1024), encoding="utf-8")
            violations = quality.weight_violations(root)
            self.assertTrue(any("index.html" in v or "/:" in v for v in violations), violations)

    def test_oversized_asset_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), minimal_site())
            (root / "assets" / "big.css").write_text("y" * (151 * 1024), encoding="utf-8")
            violations = quality.weight_violations(root)
            self.assertTrue(any("per-asset budget" in v for v in violations), violations)

    def test_under_budget_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), minimal_site())
            self.assertEqual(quality.weight_violations(root), [])


class NoJavaScript(unittest.TestCase):
    def test_shipped_script_file_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), minimal_site())
            (root / "assets" / "app.js").write_text("console.log(1)", encoding="utf-8")
            self.assertTrue(quality.javascript_violations(root))

    def test_inline_script_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(
                Path(tmp), minimal_site(**{"index.html": "<html><body><script>x()</script></body></html>"})
            )
            self.assertTrue(any("script" in v for v in quality.javascript_violations(root)))

    def test_no_script_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), minimal_site())
            self.assertEqual(quality.javascript_violations(root), [])


class Links(unittest.TestCase):
    def test_dead_internal_link_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            markup = minimal_site()["index.html"].replace(
                "<h1>t</h1>", '<h1>t</h1><a href="/nope/">gone</a>'
            )
            root = write_tree(Path(tmp), minimal_site(**{"index.html": markup}))
            self.assertTrue(any("dead internal link" in v for v in quality.link_violations(root)))

    def test_third_party_origin_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            markup = minimal_site()["index.html"].replace(
                "<h1>t</h1>", '<h1>t</h1><a href="https://fonts.example/x.css">font</a>'
            )
            root = write_tree(Path(tmp), minimal_site(**{"index.html": markup}))
            self.assertTrue(
                any("third-party origin" in v for v in quality.link_violations(root))
            )

    def test_github_links_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            markup = minimal_site()["index.html"].replace(
                "<h1>t</h1>",
                '<h1>t</h1><a href="https://github.com/99ggprooo00-code/DHUN">src</a>',
            )
            root = write_tree(Path(tmp), minimal_site(**{"index.html": markup}))
            self.assertEqual(quality.link_violations(root), [])


class MockupLabelling(unittest.TestCase):
    FIGURE = (
        '<figure class="mock mock--phone" id="mock-home-phone">'
        "<div aria-hidden=\"true\">ui</div>"
        "<figcaption>Illustrative recreation — not a screenshot.</figcaption>"
        "</figure>"
    )

    def test_unlabelled_mockup_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            markup = minimal_site()["index.html"].replace(
                "<h1>t</h1>",
                '<h1>t</h1>' + self.FIGURE.replace("not a screenshot", "actual app"),
            )
            root = write_tree(Path(tmp), minimal_site(**{"index.html": markup}))
            self.assertTrue(
                any("not labelled as a recreation" in v for v in quality.mockup_violations(root))
            )

    def test_unknown_mockup_id_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            markup = minimal_site()["index.html"].replace(
                "<h1>t</h1>", '<h1>t</h1>' + self.FIGURE.replace("mock-home-phone", "hero-shot")
            )
            root = write_tree(Path(tmp), minimal_site(**{"index.html": markup}))
            self.assertTrue(
                any("screenshot backlog" in v for v in quality.mockup_violations(root))
            )

    def test_labelled_mockup_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            markup = minimal_site()["index.html"].replace("<h1>t</h1>", "<h1>t</h1>" + self.FIGURE)
            root = write_tree(Path(tmp), minimal_site(**{"index.html": markup}))
            self.assertEqual(quality.mockup_violations(root), [])


class Contrast(unittest.TestCase):
    def test_low_contrast_pair_fails(self):
        css = ":root{--text:#777777;--bg:#888888;}"
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), minimal_site(**{"assets/styles.css": css}))
            self.assertTrue(quality.contrast_violations(root))

    def test_high_contrast_pair_passes(self):
        css = ":root{--text:#ffffff;--bg:#000000;--text-2:#ffffff;--text-3:#ffffff;--accent:#ffffff;--on-accent:#000000;--on-accent-container:#ffffff;--accent-container:#000000;--surface:#000000;--surface-variant:#000000;--warning:#ffffff;}"
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), minimal_site(**{"assets/styles.css": css}))
            self.assertEqual(quality.contrast_violations(root), [])

    def test_relative_luminance_matches_wcag_examples(self):
        self.assertAlmostEqual(quality._relative_luminance((0, 0, 0)), 0.0, places=6)
        self.assertAlmostEqual(quality._relative_luminance((255, 255, 255)), 1.0, places=6)


class Responsive(unittest.TestCase):
    def test_missing_breakpoint_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), minimal_site())
            self.assertTrue(any("breakpoint" in v for v in quality.responsive_violations(root)))

    def test_fixed_wide_width_fails(self):
        css = (
            "--target: 44px;"
            "@media (min-width: 480px){a{}}@media (min-width: 640px){a{}}"
            "@media (min-width: 768px){a{}}@media (min-width: 1024px){a{}}"
            "h1{font-size:clamp(1rem,2vw,2rem)}"
            ".wide{width: 900px}"
        )
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), minimal_site(**{"assets/styles.css": css}))
            self.assertTrue(
                any("overflow a 320px screen" in v for v in quality.responsive_violations(root))
            )


class AccessibilityFloor(unittest.TestCase):
    BASE = (
        "<!DOCTYPE html><html lang=\"en\"><head>"
        '<meta name="viewport" content="width=device-width, initial-scale=1">'
        '<link rel="icon" href="/assets/favicon.svg"></head><body>'
        "<header><nav>m</nav></header>"
        '<a class="skip-link" href="#main">s</a><main id="main"><h1>t</h1></main>'
        "<footer>f</footer></body></html>"
    )

    def make(self, markup: str) -> Path:
        css = "--target: 44px;@media (prefers-reduced-motion: reduce){a{}}"
        root = Path(tempfile.mkdtemp())
        return write_tree(
            root,
            {
                "index.html": markup,
                "features/index.html": self.BASE,
                "download/index.html": self.BASE,
                "assets/styles.css": css,
            },
        )

    def test_two_h1_fails(self):
        root = self.make(self.BASE.replace("<h1>t</h1>", "<h1>a</h1><h1>b</h1>"))
        self.assertTrue(any("exactly one <h1>" in v for v in quality.accessibility_violations(root)))

    def test_unlabelled_svg_fails(self):
        root = self.make(self.BASE.replace("<h1>t</h1>", "<h1>t</h1><svg viewBox=\"0 0 1 1\"></svg>"))
        self.assertTrue(any("svg" in v.lower() for v in quality.accessibility_violations(root)))

    def test_clean_page_passes(self):
        self.assertEqual(quality.accessibility_violations(self.make(self.BASE)), [])


class BuiltSite(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not DIST.is_dir():
            raise AssertionError(
                f"{DIST} is missing — the quality gates read the built site, so the "
                "committed build must be present"
            )

    def test_every_check_passes_on_the_built_site(self):
        violations = quality.run_checks(DIST)
        self.assertEqual(violations, [], "\n".join(violations))

    def test_weight_budget_has_headroom_reported(self):
        """Record the real numbers, so a future change sees how close it is."""
        css_bytes = sum(
            path.stat().st_size for path in (DIST / "assets").glob("*.css")
        )
        for route, relative in (
            ("/", "index.html"),
            ("/features/", "features/index.html"),
            ("/download/", "download/index.html"),
        ):
            total = (DIST / relative).stat().st_size + css_bytes
            self.assertLessEqual(
                total,
                quality.HTML_CSS_BUDGET,
                f"{route} is {total} B with a {quality.HTML_CSS_BUDGET} B budget",
            )

    def test_no_release_digest_is_quoted_on_the_download_page(self):
        markup = (DIST / "download" / "index.html").read_text(encoding="utf-8")
        self.assertNotRegex(markup, r"\b[0-9a-f]{64}\b")

    def test_no_text_uses_the_faintest_token(self):
        """`--text-4` measures ~3.9:1 and fails the WCAG AA body-text floor.

        It stays defined for non-text affordances, but no `color:` declaration
        may use it. This is the rule that outlaws the real defect CI found:
        Lighthouse reported accessibility 95 on /features/ and /download/, where
        the faint "traceable source" line and the list markers used it.
        """
        css = (DIST / "assets" / "styles.css").read_text(encoding="utf-8")
        offenders = re.findall(r"[^}{]*\{[^}]*color:\s*var\(--text-4\)[^}]*\}", css)
        self.assertEqual(
            offenders,
            [],
            "these rules paint text with --text-4 (below the 4.5:1 floor): "
            + " | ".join(offender.strip()[:80] for offender in offenders),
        )

    def test_committed_build_is_minified(self):
        """A build that was never minified would fail the weight gate elsewhere;
        here it is asserted directly, because the gate must not pass by luck."""
        markup = (DIST / "index.html").read_text(encoding="utf-8")
        self.assertNotIn("\n      ", markup)
        css = (DIST / "assets" / "styles.css").read_text(encoding="utf-8")
        self.assertNotIn("/*", css)


if __name__ == "__main__":
    unittest.main()
