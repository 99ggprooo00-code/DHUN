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

import base64
import re
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from tempfile import mkdtemp as tmpdir

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
        "ui/index.html": page,
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
                "ui/index.html": self.BASE,
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


class ClassCoverage(unittest.TestCase):
    def page(self, css: str, body: str) -> str:
        return (
            "<!DOCTYPE html><html lang=\"en\"><head><style>"
            + css
            + "</style></head><body>"
            + body
            + '<main id="main"><h1>t</h1></main></body></html>'
        )

    def test_class_without_a_rule_fails(self):
        markup = self.page(".used{color:red}", '<p class="used orphaned">x</p>')
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), {"index.html": markup})
            violations = quality.css_coverage_violations(root)
            self.assertTrue(any("orphaned" in v for v in violations), violations)

    def test_dead_css_fails(self):
        markup = self.page(".used{color:red}.never{color:blue}", '<p class="used">x</p>')
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), {"index.html": markup})
            violations = quality.css_coverage_violations(root)
            self.assertTrue(any("dead CSS" in v and "never" in v for v in violations), violations)

    def test_per_page_modules_are_checked_separately(self):
        """A class defined on another route is not defined on this one."""
        index = self.page(".used{color:red}", '<p class="used">x</p>')
        other = self.page(".other{color:red}", '<p class="missing-here">x</p>')
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), {"index.html": index, "ui/index.html": other})
            violations = quality.css_coverage_violations(root)
            self.assertTrue(
                any("/ui/" in v and "missing-here" in v for v in violations), violations
            )

    def test_clean_site_passes(self):
        markup = self.page(".used{color:red}", '<p class="used">x</p>')
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), {"index.html": markup})
            self.assertEqual(quality.css_coverage_violations(root), [])


class IconSprite(unittest.TestCase):
    SPRITE = '<svg class="sprite" aria-hidden="true"><symbol id="i-play" viewBox="0 0 24 24"/></svg>'

    def page(self, sprite: str, uses: str) -> str:
        return (
            '<!DOCTYPE html><html lang="en"><head></head><body>'
            + sprite
            + uses
            + '<main id="main"><h1>t</h1></main></body></html>'
        )

    def test_dangling_use_fails(self):
        markup = self.page(self.SPRITE, '<svg class="i" aria-hidden="true"><use href="#i-play"/><use href="#i-missing"/></svg>')
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), {"index.html": markup})
            violations = quality.sprite_violations(root)
            # the rule reports the bare icon name (the `i-` prefix is the
            # sprite's namespace, and both directions strip it identically)
            self.assertTrue(
                any("does not define" in v and "‘missing’" in v for v in violations), violations
            )

    def test_unused_symbol_fails(self):
        sprite = '<svg class="sprite" aria-hidden="true"><symbol id="i-play"/><symbol id="i-unused"/></svg>'
        markup = self.page(sprite, '<svg class="i" aria-hidden="true"><use href="#i-play"/></svg>')
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), {"index.html": markup})
            violations = quality.sprite_violations(root)
            self.assertTrue(any("never used" in v for v in violations), violations)

    def test_symbols_without_the_sprite_container_fail(self):
        markup = self.page('<symbol id="i-play"/>', '<svg class="i" aria-hidden="true"><use href="#i-play"/></svg>')
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), {"index.html": markup})
            self.assertTrue(any("sprite container" in v for v in quality.sprite_violations(root)))

    def test_consistent_sprite_passes(self):
        markup = self.page(self.SPRITE, '<svg class="i" aria-hidden="true"><use href="#i-play"/></svg>')
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), {"index.html": markup})
            self.assertEqual(quality.sprite_violations(root), [])


class BudgetRatchet(unittest.TestCase):
    def site(self, root: Path, size: int) -> Path:
        return write_tree(root, {"index.html": "x" * size, "features/index.html": "x" * size,
                                 "ui/index.html": "x" * size})

    def test_growth_beyond_tolerance_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.site(Path(tmp) / "dist", 1000)
            baseline = Path(tmp) / "baseline.json"
            quality.write_baseline(root, baseline)
            growth = int(1000 * (1 + quality.RATCHET_TOLERANCE)) + 1
            (root / "index.html").write_text("x" * growth, encoding="utf-8")
            violations = quality.budget_ratchet_violations(root, baseline)
            self.assertTrue(any("ratchet" in v for v in violations), violations)

    def test_growth_within_tolerance_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.site(Path(tmp) / "dist", 1000)
            baseline = Path(tmp) / "baseline.json"
            quality.write_baseline(root, baseline)
            (root / "index.html").write_text("x" * 1040, encoding="utf-8")
            self.assertEqual(quality.budget_ratchet_violations(root, baseline), [])

    def test_missing_baseline_is_a_violation_not_a_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.site(Path(tmp) / "dist", 1000)
            violations = quality.budget_ratchet_violations(root, Path(tmp) / "absent.json")
            self.assertTrue(any("baseline" in v for v in violations), violations)

    def test_inlined_css_is_not_counted_twice(self):
        """A page's inline CSS is inside its bytes; the weight must not double it."""
        title = ".skip-link{color:red}"
        markup = (
            '<!DOCTYPE html><html lang="en"><head><style>' + title + "</style></head><body>"
            '<a class="skip-link" href="#main">s</a><main id="main"><h1>t</h1></main></body></html>'
        )
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), {"index.html": markup})
            measured = quality.page_weight_bytes(root, "/", root / "index.html")
            self.assertEqual(measured, (root / "index.html").stat().st_size)


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
        # The weight that matters is HTML+CSS *as served*: the stylesheet is
        # inlined, so the page file is the whole request.
        for route, path in quality.page_paths(DIST).items():
            if route == "/404.html":
                continue
            total = quality.page_weight_bytes(DIST, route, path)
            self.assertLessEqual(
                total,
                quality.HTML_CSS_BUDGET,
                f"{route} is {total} B with a {quality.HTML_CSS_BUDGET} B budget",
            )

    def test_every_route_is_a_single_request(self):
        """One request per route: the CSS is inlined, nothing is linked.

        This is the Tier-A decision (Part A §10.1) stated as a rule, so a
        future edit cannot quietly reintroduce a render-blocking stylesheet or
        a second `<style>` block (which would duplicate CSS across modules).
        """
        for route, relative in (
            ("/", "index.html"),
            ("/features/", "features/index.html"),
            ("/ui/", "ui/index.html"),
            ("/404.html", "404.html"),
        ):
            markup = (DIST / relative).read_text(encoding="utf-8")
            self.assertEqual(
                markup.count("<style"), 1, f"{route} should inline exactly one stylesheet"
            )
            self.assertNotRegex(
                markup,
                r"<link[^>]*rel=\"stylesheet\"",
                f"{route} links a stylesheet, which costs a render-blocking request",
            )
            # The one `<script>` allowed is the JSON-LD data block: it has no
            # `src`, nothing evaluates it, and `javascript_violations` parses it
            # as JSON so it cannot carry code. Every other script tag is still a
            # failure here, `src` included.
            self.assertNotRegex(
                markup,
                r"<script(?![^>]*type=\"application/ld\+json\")",
                f"{route} ships a script tag that is not a JSON-LD data block",
            )
            self.assertNotRegex(
                markup, r"<script[^>]*\bsrc=", f"{route} fetches a script"
            )

    def test_inlined_css_is_minified(self):
        css = quality.site_stylesheet(DIST)
        self.assertNotIn("/*", css, "the inlined CSS still carries comments")
        self.assertNotRegex(css, r"\s\{\s", "the inlined CSS is not whitespace-collapsed")

    def test_no_release_digest_is_quoted_anywhere(self):
        for route, path in quality.page_paths(DIST).items():
            markup = path.read_text(encoding="utf-8")
            self.assertNotRegex(markup, r"\b[0-9a-f]{64}\b", f"{route} quotes a digest")

    def test_no_text_uses_the_faintest_token(self):
        """`--text-4` measures ~3.9:1 and fails the WCAG AA body-text floor.

        It stays defined for non-text affordances, but no `color:` declaration
        may use it. This is the rule that outlaws the real defect CI found:
        Lighthouse reported accessibility 95 on the routes, where
        the faint "traceable source" line and the list markers used it.
        """
        # The stylesheet is inlined into the pages now, so this reads the CSS
        # through the same helper the checks use: `assets/styles.css` no longer
        # exists in dist, and the rule must follow the bytes rather than the
        # file layout. The rule itself is unchanged.
        css = quality.site_stylesheet(DIST)
        self.assertTrue(css, "no CSS found in the built site at all")
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
        # Same adaptation as the contrast rule above: read the CSS where the
        # site ships it (inline), not where it used to ship it (a linked file).
        css = quality.site_stylesheet(DIST)
        self.assertTrue(css, "no CSS found in the built site at all")
        self.assertNotIn("/*", css)


if __name__ == "__main__":
    unittest.main()


class DistCopyMixin(unittest.TestCase):
    """Run one check against a disposable copy of the real built site."""

    def copy_dist(self) -> Path:
        root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, root, ignore_errors=True)
        target = root / "dist"
        shutil.copytree(DIST, target)
        return target


class DistributionBoundary(DistCopyMixin):
    """The site describes the software; it does not distribute or install it.

    This is the rule for the session's redirection: downloads are not the
    website's business, so a direct artifact link or a verification instruction
    is a defect rather than a convenience.
    """

    def test_the_real_site_is_clean(self):
        self.assertEqual(quality.distribution_boundary_violations(DIST), [])

    def mutate(self, relative: str, old: str, new: str) -> list[str]:
        dist = self.copy_dist()
        page = dist / relative
        markup = page.read_text(encoding="utf-8")
        self.assertIn(old, markup)
        page.write_text(markup.replace(old, new, 1), encoding="utf-8")
        violations = quality.distribution_boundary_violations(dist)
        self.assertTrue(violations, f"mutation {old!r} -> {new!r} was not caught")
        return violations

    def test_a_direct_apk_link_fails(self):
        violations = self.mutate(
            "features/index.html",
            'href="/ui/"',
            'href="https://github.com/99ggprooo00-code/DHUN/releases/download/test/dhun-test.apk"',
        )
        self.assertTrue(any("artifact" in v for v in violations), violations)

    def test_a_release_download_path_fails(self):
        violations = self.mutate(
            "features/index.html", 'href="/ui/"', 'href="https://github.com/x/releases/download/test/a"'
        )
        self.assertTrue(any("artifact" in v for v in violations), violations)

    def test_a_checksum_instruction_fails(self):
        violations = self.mutate(
            "features/index.html", "Read the code", "Read the code. Run sha256sum -c build.sha256", 
        )
        self.assertTrue(any("installation" in v for v in violations), violations)

    def test_a_sideload_instruction_fails(self):
        violations = self.mutate("features/index.html", "Read the code", "Sideload the build")
        self.assertTrue(any("installation" in v for v in violations), violations)

    def test_the_release_page_is_still_reachable(self):
        """Banning artifacts must not ban the one honest pointer to them."""
        markup = (DIST / "features" / "index.html").read_text(encoding="utf-8")
        self.assertIn("/releases/tag/test", markup)
        self.assertEqual(quality.distribution_boundary_violations(DIST), [])


class CopyHygiene(DistCopyMixin):
    """Nothing may ship that a reader would read as a mistake.

    The download page shipped a literal Markdown backtick and a visibly escaped
    `<code>` tag; neither the weight, link, claim or traceability rules could
    see them, because both are *valid* HTML. These tests keep the rule firing.
    """

    def test_the_real_site_is_clean(self):
        self.assertEqual(quality.copy_hygiene_violations(DIST), [])

    def mutate(self, relative: str, old: str, new: str) -> list[str]:
        dist = self.copy_dist()
        page = dist / relative
        markup = page.read_text(encoding="utf-8")
        self.assertIn(old, markup)
        page.write_text(markup.replace(old, new, 1), encoding="utf-8")
        violations = quality.copy_hygiene_violations(dist)
        self.assertTrue(violations, f"mutation {old!r} -> {new!r} was not caught")
        return violations

    def test_a_literal_backtick_fails(self):
        violations = self.mutate("features/index.html", "Read the code", "`Read the code")
        self.assertTrue(any("backtick" in v for v in violations), violations)

    def test_escaped_markup_fails(self):
        violations = self.mutate(
            "features/index.html",
            "GPL-3.0",
            "&lt;code&gt;GPL-3.0&lt;/code&gt;",
        )
        self.assertTrue(any("escaped markup" in v for v in violations), violations)

    def test_a_link_to_nowhere_fails(self):
        violations = self.mutate("features/index.html", 'href="/ui/"', 'href="#"')
        self.assertTrue(any("goes nowhere" in v for v in violations), violations)

    def test_a_placeholder_word_fails(self):
        violations = self.mutate("features/index.html", "Read the code", "Lorem ipsum Read the code")
        self.assertTrue(any("placeholder" in v for v in violations), violations)

    def test_a_negated_placeholder_word_passes(self):
        """“These are absent, not coming soon” is the opposite of a promise."""
        dist = self.copy_dist()
        page = dist / "features" / "index.html"
        markup = page.read_text(encoding="utf-8")
        page.write_text(
            markup.replace("Read the code", "Absent, not coming soon. Read the code", 1),
            encoding="utf-8",
        )
        self.assertEqual(quality.copy_hygiene_violations(dist), [])


class ClaimTraceability(DistCopyMixin):
    def test_every_section_on_the_real_site_is_traceable(self):
        self.assertEqual(quality.claim_traceability_violations(DIST), [])

    def test_a_section_without_a_citation_fails(self):
        dist = self.copy_dist()
        page = dist / "features" / "index.html"
        markup = page.read_text(encoding="utf-8")
        start = markup.index("<section")
        end = markup.index("</section>") + len("</section>")
        section = re.sub(r"<!--.*?-->", " ", markup[start:end], flags=re.S)
        page.write_text(markup[:start] + section + markup[end:], encoding="utf-8")
        violations = quality.claim_traceability_violations(dist)
        self.assertTrue(violations, "a section stripped of its citation still passed")
        self.assertTrue(any("no citation" in v for v in violations), violations)


class BacklogDrift(DistCopyMixin):
    def test_the_real_site_matches_the_backlog(self):
        self.assertEqual(quality.backlog_drift_violations(DIST), [])

    def contract(self, dist: Path, plan: Path) -> list[str]:
        original = quality.BACKLOG_PLAN
        quality.BACKLOG_PLAN = plan
        try:
            return quality.backlog_drift_violations(dist)
        finally:
            quality.BACKLOG_PLAN = original

    def test_a_shipped_mockup_that_is_missing_fails(self):
        dist = self.copy_dist()
        plan = Path(tempfile.mkdtemp()) / "WEBSITE_PLAN.md"
        self.addCleanup(shutil.rmtree, plan.parent, ignore_errors=True)
        plan.write_text(
            "| # | Mockup (site id) | Status | Real capture | What it must show |\n"
            "|---|---|---|---|---|\n"
            "| 1 | `mock-home-phone` | shipped | Android Home | layout |\n"
            "| 2 | `mock-never-drawn` | shipped | nothing | nothing |\n",
            encoding="utf-8",
        )
        violations = self.contract(dist, plan)
        self.assertTrue(any("mock-never-drawn" in v for v in violations), violations)

    def test_a_mockup_on_a_page_with_no_row_fails(self):
        dist = self.copy_dist()
        route = quality.page_paths(dist)["/features/"]
        markup = route.read_text(encoding="utf-8")
        route.write_text(
            markup.replace(
                "<main",
                '<main data-x="1"',
                1,
            ).replace(
                "</main>",
                '<figure id="mock-not-in-the-plan"><span class="mock-badge">not a screenshot</span></figure></main>',
                1,
            ),
            encoding="utf-8",
        )
        violations = quality.backlog_drift_violations(dist)
        self.assertTrue(any("mock-not-in-the-plan" in v for v in violations), violations)

    def test_a_planned_mockup_that_is_already_on_a_page_fails(self):
        dist = self.copy_dist()
        plan = Path(tempfile.mkdtemp()) / "WEBSITE_PLAN.md"
        self.addCleanup(shutil.rmtree, plan.parent, ignore_errors=True)
        plan.write_text(
            "| # | Mockup (site id) | Status | Real capture | What it must show |\n"
            "|---|---|---|---|---|\n"
            "| 1 | `mock-home-phone` | planned | Android Home | layout |\n",
            encoding="utf-8",
        )
        violations = self.contract(dist, plan)
        self.assertTrue(any("mock-home-phone" in v for v in violations), violations)


class StaleFacts(DistCopyMixin):
    def test_the_real_site_states_no_version_date_or_build_number(self):
        self.assertEqual(quality.stale_fact_violations(DIST), [])

    def test_a_version_string_fails(self):
        dist = self.copy_dist()
        page = dist / "index.html"
        page.write_text(
            page.read_text(encoding="utf-8").replace("</h1>", "</h1><p>Version 3.2.1 is out.</p>", 1),
            encoding="utf-8",
        )
        violations = quality.stale_fact_violations(dist)
        self.assertTrue(any("3.2.1" in v for v in violations), violations)

    def test_a_bare_date_fails(self):
        dist = self.copy_dist()
        page = dist / "features" / "index.html"
        page.write_text(
            page.read_text(encoding="utf-8").replace("</h1>", "</h1><p>Updated 2026-10-08.</p>", 1),
            encoding="utf-8",
        )
        violations = quality.stale_fact_violations(dist)
        self.assertTrue(any("2026-10-08" in v for v in violations), violations)


class SitemapScope(DistCopyMixin):
    def test_a_fourth_route_in_the_sitemap_fails(self):
        dist = self.copy_dist()
        sitemap = dist / "sitemap.xml"
        sitemap.write_text(
            sitemap.read_text(encoding="utf-8").replace(
                "</urlset>",
                "<url><loc>https://99ggprooo00-code.github.io/DHUN/blog/</loc></url></urlset>",
            ),
            encoding="utf-8",
        )
        violations = quality.crawlability_violations(dist)
        self.assertTrue(any("/blog/" in v for v in violations), violations)


class InlinedIcon(DistCopyMixin):
    """One request per route: the icon is inlined, and it is the right bytes.

    Lighthouse measured `requests=2` per route on the merged head (run
    37808955045); the second request was this icon. The rule has two halves and
    both are exercised here, because either one failing silently puts the
    request back or changes what the browser draws.
    """

    @staticmethod
    def data_uri(body: bytes) -> str:
        return "data:image/svg+xml;base64," + base64.b64encode(body).decode("ascii")

    def site(self, markup: str, **extra: str) -> Path:
        """A tmp dist whose every page is `markup`; no separate icon file ships."""
        root = Path(tmpdir())
        files = {
            "index.html": markup,
            "features/index.html": markup,
            "ui/index.html": markup,
            "robots.txt": "Sitemap: https://99ggprooo00-code.github.io/DHUN/sitemap.xml\n",
            "sitemap.xml": "<urlset></urlset>",
            "404.html": markup,
        }
        files.update(extra)
        return write_tree(root, files)

    def page_with(self, icon_tag: str) -> str:
        return minimal_site()["index.html"].replace(
            '<link rel="icon" href="/assets/favicon.svg">', icon_tag
        )

    def test_the_real_build_inlines_the_icon(self):
        self.assertEqual(quality.favicon_violations(DIST), [])

    def test_a_separate_icon_file_fails(self):
        markup = self.page_with('<link rel="icon" href="/assets/favicon.svg" type="image/svg+xml">')
        violations = quality.favicon_violations(self.site(markup))
        self.assertTrue(
            any("second HTTP request" in v for v in violations), violations
        )

    def test_drifted_icon_bytes_fail(self):
        body = b'<svg xmlns="http://www.w3.org/2000/svg"><rect width="1" height="1"/></svg>'
        markup = self.page_with(f'<link rel="icon" href="{self.data_uri(body)}" type="image/svg+xml">')
        violations = quality.favicon_violations(self.site(markup))
        self.assertTrue(any("drifted from dhun-favicon.svg" in v for v in violations), violations)

    def test_a_missing_icon_link_fails(self):
        markup = minimal_site()["index.html"].replace(
            '<link rel="icon" href="/assets/favicon.svg">', ""
        )
        violations = quality.favicon_violations(self.site(markup))
        self.assertTrue(any("exactly one" in v for v in violations), violations)

    def test_a_leftover_file_reference_fails(self):
        source = quality._compact_svg(quality.FAVICON_SOURCE.read_text(encoding="utf-8"))
        markup = self.page_with(
            f'<link rel="icon" href="{self.data_uri(source.encode("utf-8"))}" type="image/svg+xml">'
        ).replace("<h1>t</h1>", '<h1>t</h1><a href="/assets/dhun-favicon.svg">icon</a>')
        violations = quality.favicon_violations(self.site(markup))
        self.assertTrue(any("must not be fetched as a file" in v for v in violations), violations)

    def test_the_exact_current_encoding_passes(self):
        source = quality._compact_svg(quality.FAVICON_SOURCE.read_text(encoding="utf-8"))
        markup = self.page_with(
            f'<link rel="icon" href="{self.data_uri(source.encode("utf-8"))}" type="image/svg+xml">'
        )
        self.assertEqual(quality.favicon_violations(self.site(markup)), [])


class NothingShipsUnreferenced(DistCopyMixin):
    """An asset no page names is dead weight in the deploy, or a broken page."""

    def test_the_real_build_references_everything_it_ships(self):
        self.assertEqual(quality.unreferenced_file_violations(DIST), [])

    def test_an_unreferenced_file_fails(self):
        dist = self.copy_dist()
        (dist / "assets").mkdir(exist_ok=True)
        (dist / "assets" / "leftover.svg").write_text("<svg/>", encoding="utf-8")
        violations = quality.unreferenced_file_violations(dist)
        self.assertTrue(any("leftover.svg" in v for v in violations), violations)

    def test_a_referenced_file_passes(self):
        dist = self.copy_dist()
        (dist / "assets").mkdir(exist_ok=True)
        (dist / "assets" / "print.css").write_text("@media print{}", encoding="utf-8")
        page = dist / "index.html"
        page.write_text(
            page.read_text(encoding="utf-8").replace(
                "</head>", '<link rel="stylesheet" href="/assets/print.css"></head>', 1
            ),
            encoding="utf-8",
        )
        self.assertEqual(quality.unreferenced_file_violations(dist), [])

    def test_crawl_files_and_the_404_are_exempt(self):
        dist = self.copy_dist()
        self.assertEqual(quality.unreferenced_file_violations(dist), [])
        self.assertTrue((dist / "robots.txt").is_file())
        self.assertTrue((dist / "sitemap.xml").is_file())
        self.assertTrue((dist / "404.html").is_file())


class StyleBlocksForOtherOutputs(DistCopyMixin):
    """Print and forced-colours rules: the mechanism each browser check measures.

    The browser job measures *effects* (`print` and `forced colors` in
    `website/tests/browser.mjs`). Neither can run without a browser, so the
    mechanism those measurements depend on is asserted here, per route, in the
    cheap Python-only suite — and both rules are mutation-proven below.
    """

    PRINT = "@media print { :root { --text: #000000; --bg: #ffffff; } .mock .device { display: none; } }"
    # A page that draws a mockup, in the markup shapes the site really uses.
    DRAWING = '<div class="mock"><div class="device">drawing</div></div>' 
    FORCED = "@media (forced-colors: active) { .btn { border-color: CanvasText; } }"

    def page(self, css: str, body: str = '<p><a class="btn" href="/">Go</a></p>') -> str:
        return (
            '<!DOCTYPE html><html lang="en"><head><meta name="viewport" content="width=device-width">'
            f"<style>{css}</style></head><body><header><nav>m</nav></header>"
            '<a class="skip-link" href="#main">s</a><main id="main"><h1>t</h1>'
            f"{body}</main><footer>f</footer></body></html>"
        )

    def site(self, css: str, body: str | None = None) -> Path:
        page = self.page(css) if body is None else self.page(css, body)
        return write_tree(
            Path(tmpdir()),
            {rel: page for rel in ("index.html", "features/index.html", "ui/index.html")},
        )

    def test_the_real_site_ships_both_blocks(self):
        self.assertEqual(quality.print_style_violations(DIST), [])
        self.assertEqual(quality.forced_colors_violations(DIST), [])

    def test_a_page_without_a_print_block_fails(self):
        dist = self.copy_dist()
        page = dist / "ui" / "index.html"
        markup = page.read_text(encoding="utf-8")
        start = markup.index("@media print{")
        end = markup.index("}}", start) + 2
        page.write_text(markup.replace(markup[start:end], ""), encoding="utf-8")
        violations = quality.print_style_violations(dist)
        self.assertTrue(any("/ui/" in v and "@media print" in v for v in violations), violations)

    def test_a_print_block_without_paper_tokens_or_device_hiding_fails(self):
        css = "@media print { .card { break-inside: avoid; } }"
        violations = quality.print_style_violations(self.site(css, body=self.DRAWING))
        self.assertTrue(any("--text" in v for v in violations), violations)
        self.assertTrue(any(".device" in v or "mockup" in v for v in violations), violations)

    def test_device_hiding_is_required_only_of_a_page_that_draws_one(self):
        """Changed 2026-10-08 with the per-route CSS pruner.

        The rule used to demand the drawing-hiding declaration of *every* route,
        which was vacuous on `/404.html` and impossible once the
        unprunable-anywhere `.mock .device` rule stopped shipping there. It is now
        conditional on the markup: a page that draws a mockup must hide it, a page
        that draws nothing is not asked to hide nothing.
        """
        css = "@media print { :root { --text: #000000; --bg: #ffffff; } }" + self.FORCED
        self.assertEqual(quality.print_style_violations(self.site(css)), [])
        drawn = quality.print_style_violations(self.site(css, body=self.DRAWING))
        self.assertTrue(any("mockup" in v or ".device" in v for v in drawn), drawn)

    def test_a_rule_after_the_block_cannot_satisfy_it(self):
        """The false pass mutation found on 2026-10-08.

        The rule used to read 3000 characters after the `@media print` marker, so
        a `.device` rule *following* the block satisfied a check about the block.
        Brace matching reads the block itself, and this test is the regression:
        the same sheet fails when the hiding rule lives after the block.
        """
        css = (
            "@media print { :root { --text: #000000; --bg: #ffffff; } }"
            + self.FORCED
            + ".mock .device { display: none; }"
        )
        violations = quality.print_style_violations(self.site(css, body=self.DRAWING))
        self.assertTrue(any("mockup" in v or ".device" in v for v in violations), violations)

    def test_a_print_block_with_tokens_and_device_hiding_passes(self):
        self.assertEqual(quality.print_style_violations(self.site(self.PRINT + self.FORCED)), [])

    def test_a_print_palette_that_cannot_be_read_on_paper_fails(self):
        """The mutation that proved the first version too shallow: a print block
        with `--text:#ffffff` on a white `--bg` passed it, because the rule only
        checked that the tokens were *mentioned*. It now computes the ratio."""
        css = self.PRINT.replace("--text: #000000", "--text: #ffffff") + self.FORCED
        violations = quality.print_style_violations(self.site(css))
        self.assertTrue(any("unreadable" in v for v in violations), violations)

    def test_a_page_without_a_forced_colours_block_fails(self):
        dist = self.copy_dist()
        page = dist / "features" / "index.html"
        markup = page.read_text(encoding="utf-8")
        start = markup.index("@media (forced-colors:active){")
        end = markup.index("}}", start) + 2
        page.write_text(markup.replace(markup[start:end], ""), encoding="utf-8")
        violations = quality.forced_colors_violations(dist)
        self.assertTrue(
            any("/features/" in v and "forced-colors" in v for v in violations), violations
        )

    def test_a_forced_colours_block_that_forgets_the_button_fails(self):
        css = self.PRINT + "@media (forced-colors: active) { .card { border-color: CanvasText; } }"
        violations = quality.forced_colors_violations(self.site(css))
        self.assertTrue(any(".btn" in v for v in violations), violations)


class StructuredData(DistCopyMixin):
    """One `SoftwareApplication` block, and only claims the repository supports.

    Structured data is the part of a page a machine repeats without the caveats
    around it, so an over-claim travels furthest from here — a rating, a
    version, an offer or a download URL would each be repeated as fact.
    """

    JSON_LD = (
        '<script type="application/ld+json">'
        '{"@context":"https://schema.org","@type":"SoftwareApplication","name":"DHUN",'
        '"isAccessibleForFree":true,"license":"https://github.com/99ggprooo00-code/DHUN/blob/main/LICENSE",'
        '"url":"https://99ggprooo00-code.github.io/DHUN/",'
        '"codeRepository":"https://github.com/99ggprooo00-code/DHUN"}'
        "</script>"
    )

    def page(self, block: str = None) -> str:
        return minimal_site()["index.html"].replace("</head>", f"{block if block is not None else self.JSON_LD}</head>")

    def site(self, block: str = None) -> Path:
        markup = self.page(block)
        return write_tree(
            Path(tmpdir()),
            {
                "index.html": markup,
                "features/index.html": markup,
                "ui/index.html": markup,
                "404.html": markup,
                "robots.txt": "Sitemap: https://99ggprooo00-code.github.io/DHUN/sitemap.xml\n",
                "sitemap.xml": "<urlset></urlset>",
            },
        )

    def test_the_real_site_is_clean(self):
        self.assertEqual(quality.structured_data_violations(DIST), [])

    def test_an_invented_rating_fails(self):
        block = self.JSON_LD.replace(
            '"isAccessibleForFree":true',
            '"aggregateRating":{"ratingValue":"4.9"},"isAccessibleForFree":true',
        )
        violations = quality.structured_data_violations(self.site(block))
        self.assertTrue(any("aggregateRating" in v for v in violations), violations)

    def test_an_invented_version_or_offer_fails(self):
        for key, value in (("softwareVersion", '"9.9.9"'), ("offers", '"free"')):
            block = self.JSON_LD.replace('"name":"DHUN"', f'"name":"DHUN","{key}":{value}')
            violations = quality.structured_data_violations(self.site(block))
            self.assertTrue(any(key in v for v in violations), (key, violations))

    def test_the_wrong_type_fails(self):
        block = self.JSON_LD.replace("SoftwareApplication", "WebSite")
        violations = quality.structured_data_violations(self.site(block))
        self.assertTrue(any("@type" in v for v in violations), violations)

    def test_a_block_without_a_licence_fails(self):
        block = self.JSON_LD.replace('"license":"https://github.com/99ggprooo00-code/DHUN/blob/main/LICENSE",', "")
        violations = quality.structured_data_violations(self.site(block))
        self.assertTrue(any("licence" in v for v in violations), violations)

    def test_a_missing_block_fails(self):
        violations = quality.structured_data_violations(self.site(""))
        self.assertTrue(any("exactly one JSON-LD block" in v for v in violations), violations)


class NoJavaScriptStillHolds(unittest.TestCase):
    """The JSON-LD exception must not become a hole in the no-JS rule."""

    def page(self, head: str) -> dict[str, str]:
        markup = minimal_site()["index.html"].replace("</head>", f"{head}</head>")
        files = minimal_site()
        files["index.html"] = markup
        return files

    def test_json_ld_alone_is_not_client_side_javascript(self):
        block = (
            '<script type="application/ld+json">{"@type":"SoftwareApplication"}</script>'
        )
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(Path(tmp), self.page(block))
            self.assertEqual(quality.javascript_violations(root), [])

    def test_json_ld_that_is_not_json_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(
                Path(tmp),
                self.page('<script type="application/ld+json">{oops}</script>'),
            )
            self.assertTrue(
                any("not valid JSON" in v for v in quality.javascript_violations(root))
            )

    def test_a_typed_script_tag_is_still_a_violation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = write_tree(
                Path(tmp),
                self.page('<script type="text/javascript">x()</script>'),
            )
            self.assertTrue(quality.javascript_violations(root))

    def test_an_inline_event_handler_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            files = minimal_site()
            files["index.html"] = files["index.html"].replace(
                "<h1>t</h1>", '<h1 onclick="run()">t</h1>'
            )
            root = write_tree(Path(tmp), files)
            violations = quality.javascript_violations(root)
            self.assertTrue(any("event handler" in v for v in violations), violations)


class MetadataConsistency(DistCopyMixin):
    def test_the_real_site_passes(self):
        self.assertEqual(quality.metadata_violations(DIST), [])

    def test_a_page_missing_a_theme_colour_fails(self):
        dist = self.copy_dist()
        page = dist / "index.html"
        page.write_text(
            page.read_text(encoding="utf-8").replace('media="(prefers-color-scheme: light)"', 'media="print"'),
            encoding="utf-8",
        )
        violations = quality.metadata_violations(dist)
        self.assertTrue(any("light-scheme theme-color" in v for v in violations), violations)

    def test_og_url_that_disagrees_with_the_canonical_fails(self):
        dist = self.copy_dist()
        page = dist / "features" / "index.html"
        page.write_text(
            page.read_text(encoding="utf-8").replace(
                'property="og:url" content="https://99ggprooo00-code.github.io/DHUN/features/"',
                'property="og:url" content="https://example.com/features/"',
            ),
            encoding="utf-8",
        )
        violations = quality.metadata_violations(dist)
        self.assertTrue(any("og:url should be" in v for v in violations), violations)


class SitemapWellFormedness(DistCopyMixin):
    """A regex can find a `<loc>` inside malformed XML; a parser cannot."""

    def test_the_real_sitemap_parses(self):
        self.assertEqual(quality.sitemap_violations(DIST), [])

    def test_an_unclosed_urlset_fails(self):
        dist = self.copy_dist()
        sitemap = dist / "sitemap.xml"
        sitemap.write_text(sitemap.read_text(encoding="utf-8").replace("</urlset>", ""), encoding="utf-8")
        violations = quality.sitemap_violations(dist)
        self.assertTrue(any("not well-formed" in v for v in violations), violations)

    def test_a_duplicate_url_fails(self):
        dist = self.copy_dist()
        sitemap = dist / "sitemap.xml"
        sitemap.write_text(
            sitemap.read_text(encoding="utf-8").replace(
                "</urlset>", "<url><loc>https://99ggprooo00-code.github.io/DHUN/</loc></url></urlset>"
            ),
            encoding="utf-8",
        )
        violations = quality.sitemap_violations(dist)
        self.assertTrue(any("twice" in v for v in violations), violations)

    def test_an_off_origin_url_fails(self):
        dist = self.copy_dist()
        sitemap = dist / "sitemap.xml"
        sitemap.write_text(
            sitemap.read_text(encoding="utf-8").replace(
                "</urlset>", "<url><loc>https://example.com/</loc></url></urlset>"
            ),
            encoding="utf-8",
        )
        violations = quality.sitemap_violations(dist)
        self.assertTrue(any("not under" in v for v in violations), violations)


class ReferencedFilesExist(DistCopyMixin):
    """Every attribute naming a site path must name a file the build ships."""

    def test_the_real_site_resolves_every_attribute(self):
        self.assertEqual(quality.asset_reference_violations(DIST), [])

    def test_a_poster_naming_a_missing_file_fails(self):
        dist = self.copy_dist()
        page = dist / "index.html"
        page.write_text(
            page.read_text(encoding="utf-8").replace(
                "<h1", '<video poster="/assets/missing.jpg"></video><h1', 1
            ),
            encoding="utf-8",
        )
        violations = quality.asset_reference_violations(dist)
        self.assertTrue(any("missing.jpg" in v for v in violations), violations)

    def test_a_data_uri_is_not_a_file_reference(self):
        dist = self.copy_dist()
        self.assertEqual(quality.asset_reference_violations(dist), [])


class CssPruningPredicate(unittest.TestCase):
    """The Python mirror of `website/tools/prune-css.mjs`, unit by unit.

    The pruner decides which rules a route ships, and this is the same decision
    expressed in Python so app CI can check the committed build without Node.
    Two implementations of one predicate is a real risk, so each half of the
    predicate is pinned here with the case that distinguishes it.
    """

    def test_a_selector_with_no_class_can_always_match(self):
        self.assertTrue(quality._rule_can_match("body", set()))
        self.assertTrue(quality._rule_can_match("a:hover", set()))
        self.assertTrue(quality._rule_can_match("*", set()))

    def test_a_selector_with_a_used_class_can_match(self):
        self.assertTrue(quality._rule_can_match(".card", {"card"}))
        self.assertTrue(quality._rule_can_match(".card .title", {"title"}))
        self.assertFalse(quality._rule_can_match(".card .title", {"other"}))

    def test_a_class_inside_not_is_negative_and_keeps_the_rule(self):
        self.assertTrue(quality._rule_can_match("a:not(.btn)", set()))
        self.assertEqual(quality.positive_classes("a:not(.btn)"), set())
        self.assertEqual(quality.positive_classes(":is(.a, .b)"), {"a", "b"})

    def test_a_condition_group_is_pruned_inside_and_dropped_when_empty(self):
        css = "@media print { .used { a: b; } .unused { c: d; } }"
        pruned = quality.prune_source_css(css, {"used"})
        self.assertIn(".used", pruned)
        self.assertNotIn(".unused", pruned)
        self.assertEqual(quality.prune_source_css(css, set()), "")

    def test_pruning_the_real_css_for_one_page_leaves_a_prefix_of_its_rules(self):
        """Every rule the pruner keeps must be one of the source's own rules."""
        css = (quality.SOURCE_DIR.parent / "css" / "base.css").read_text(encoding="utf-8")
        pruned = quality.prune_source_css(css, {"btn", "site-header"})
        self.assertLess(len(pruned), len(css))
        for unit in quality.css_units(quality.without_css_comments(pruned)):
            if unit["kind"] == "rule":
                self.assertTrue(
                    quality._rule_can_match(unit["prelude"], {"btn", "site-header"}),
                    unit["prelude"],
                )


class CurrentPageIsMarked(DistCopyMixin):
    """`aria-current` on the page the visitor is on, and not on the 404.

    The rendered half of this (does the marker *look* different, does it survive
    Windows High Contrast) is a browser measurement; these are the mutations that
    need no browser.
    """

    def test_the_committed_site_marks_each_destination_once(self):
        self.assertEqual(quality.navigation_state_violations(DIST), [])

    def test_a_page_without_a_marker_fails(self):
        dist = self.copy_dist()
        page = dist / "features" / "index.html"
        page.write_text(
            page.read_text(encoding="utf-8").replace(' aria-current="page"', "", 1),
            encoding="utf-8",
        )
        violations = quality.navigation_state_violations(dist)
        self.assertTrue(
            any("/features/" in v and "exactly one" in v for v in violations), violations
        )

    def test_a_marker_on_two_links_fails(self):
        dist = self.copy_dist()
        page = dist / "ui" / "index.html"
        page.write_text(
            page.read_text(encoding="utf-8").replace(
                '<a class="wordmark" href="/"', '<a aria-current="page" class="wordmark" href="/"', 1
            ),
            encoding="utf-8",
        )
        violations = quality.navigation_state_violations(dist)
        self.assertTrue(any("/ui/" in v and "found 2" in v for v in violations), violations)

    def test_a_marker_pointing_at_another_route_fails(self):
        dist = self.copy_dist()
        page = dist / "features" / "index.html"
        markup = page.read_text(encoding="utf-8")
        start = markup.index('<a href="/features/" aria-current="page"')
        page.write_text(
            markup.replace(markup[start : start + 35], '<a href="/ui/" aria-current="page"', 1),
            encoding="utf-8",
        )
        violations = quality.navigation_state_violations(dist)
        self.assertTrue(any("/features/" in v and "/ui/" in v for v in violations), violations)

    def test_a_marker_on_the_404_fails(self):
        dist = self.copy_dist()
        page = dist / "404.html"
        page.write_text(
            page.read_text(encoding="utf-8").replace(
                '<a class="btn btn--primary" href="/ui/"',
                '<a class="btn btn--primary" aria-current="page" href="/ui/"',
                1,
            ),
            encoding="utf-8",
        )
        violations = quality.navigation_state_violations(dist)
        self.assertTrue(any("/404.html" in v for v in violations), violations)

    def test_a_marker_with_no_visible_style_fails(self):
        dist = self.copy_dist()
        page = dist / "index.html"
        markup = page.read_text(encoding="utf-8")
        # Drop every rule that styles the marker, wherever the pruner kept it.
        for prelude, body in re.findall(r'([^{}]*\[aria-current="page"\][^{}]*)\{([^{}]*)\}', markup):
            markup = markup.replace(prelude + "{" + body + "}", "", 1)
        page.write_text(markup, encoding="utf-8")
        violations = quality.navigation_state_violations(dist)
        self.assertTrue(
            any("/: " in v and "no rule in this page" in v for v in violations), violations
        )

    def test_a_background_only_marker_fails(self):
        """Forced colours drops author backgrounds, so a pill alone is not a marker."""
        dist = self.copy_dist()
        page = dist / "ui" / "index.html"
        markup = page.read_text(encoding="utf-8")
        # Keep only the pill: remove the rule whose body carries the underline.
        for prelude, body in re.findall(r'([^{}]*\[aria-current="page"\][^{}]*)\{([^{}]*)\}', markup):
            if "text-decoration" in body:
                markup = markup.replace(prelude + "{" + body + "}", "", 1)
        page.write_text(markup, encoding="utf-8")
        violations = quality.navigation_state_violations(dist)
        self.assertTrue(any("/ui/" in v and "High Contrast" in v for v in violations), violations)


class DistMatchesItsSources(DistCopyMixin):
    """The cheap, Node-free half of the workflow's drift check."""

    def test_the_committed_build_matches_the_sources(self):
        self.assertEqual(quality.dist_source_drift_violations(DIST), [])

    def test_a_hand_edited_page_fails(self):
        dist = self.copy_dist()
        page = dist / "ui" / "index.html"
        page.write_text(
            page.read_text(encoding="utf-8").replace("--accent:#bb86fc", "--accent:#ff0000"),
            encoding="utf-8",
        )
        violations = quality.dist_source_drift_violations(dist)
        self.assertTrue(any("/ui/" in v for v in violations), violations)

    def test_a_reordered_sheet_fails(self):
        """Order is part of the contract: the cascade depends on it."""
        dist = self.copy_dist()
        page = dist / "index.html"
        markup = page.read_text(encoding="utf-8")
        matches = list(re.finditer(r"\.(-?[A-Za-z_][\w-]*)\{[^{}]*\}", markup))
        pair = next(
            (first, second)
            for first, second in zip(matches, matches[1:])
            if second.start() == first.end() and first.group(1) != second.group(1)
        )
        first, second = pair
        page.write_text(
            markup[: first.start()] + second.group(0) + first.group(0) + markup[second.end() :],
            encoding="utf-8",
        )
        violations = quality.dist_source_drift_violations(dist)
        self.assertTrue(
            any("/: the committed CSS is not the pruned composition" in v for v in violations),
            violations,
        )

    def test_a_page_carrying_a_module_it_did_not_declare_fails(self):
        dist = self.copy_dist()
        page = dist / "404.html"
        page.write_text(
            page.read_text(encoding="utf-8").replace(
                "</style>", ".token-swatch{color:red}</style>", 1
            ),
            encoding="utf-8",
        )
        violations = quality.dist_source_drift_violations(dist)
        self.assertTrue(any("/404.html" in v for v in violations), violations)
