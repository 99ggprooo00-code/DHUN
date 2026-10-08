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
import shutil
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
            self.assertNotRegex(markup, r"<script", f"{route} ships a script tag")

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
