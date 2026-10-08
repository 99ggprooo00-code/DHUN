"""The site's honesty contract, asserted in CI step 1 (no Node, no network).

`.ai/WEBSITE_PLAN.md` Part A §4 fixes what this site may claim. The rules live
in `website_claims.py`; these tests do two jobs:

1. **Rule-engine tests** — each rule is fed synthetic pages that *must* fail and
   synthetic pages that must pass, so a rule that silently stops matching is a
   red test rather than a quiet regression. This is the mutation proof, in
   miniature and permanently executed.
2. **Built-output tests** — the rules run against the committed build in
   `website/dist/`. If that directory is missing the tests fail: the contract
   is asserted against built HTML, and a missing build is not a pass. The site
   workflow rebuilds and fails on any drift from the committed output, so what
   ships is what was checked here.

Every mutation below is a real one: the "bad" fixtures are exactly the edits a
tired session would make (an iOS claim in the download list, a digest pasted in
because it looked helpful, a caveat deleted because it spoiled the hero).
"""

import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import website_claims as claims  # noqa: E402

DIST = claims.DEFAULT_DIST


def page(body: str) -> str:
    return f"<!DOCTYPE html><html lang=\"en\"><body>{body}</body></html>"


class ForbiddenClaims(unittest.TestCase):
    def test_flags_an_ios_claim(self):
        pages = {"/": page("<p>DHUN is available for iOS and Android.</p>")}
        violations = claims.forbidden_claim_violations(pages)
        self.assertTrue(any("iOS" in v for v in violations), violations)

    def test_flags_a_web_player_claim(self):
        pages = {"/": page("<p>Try the web player at app.dhun.example.</p>")}
        self.assertTrue(claims.forbidden_claim_violations(pages))

    def test_flags_a_sync_claim(self):
        pages = {"/": page("<p>DHUN syncs your library across devices.</p>")}
        self.assertTrue(claims.forbidden_claim_violations(pages))

    def test_flags_a_quality_claim(self):
        for phrase in (
            "<p>Streams in FLAC.</p>",
            "<p>Up to 320 kbps on every track.</p>",
            "<p>Lossless audio, album quality.</p>",
        ):
            with self.subTest(phrase=phrase):
                self.assertTrue(
                    claims.forbidden_claim_violations({"/": page(phrase)})
                )

    def test_flags_a_store_channel_claim(self):
        pages = {"/features/": page("<p>Get it on F-Droid.</p>")}
        self.assertTrue(claims.forbidden_claim_violations(pages))

    def test_allows_a_negated_mention(self):
        pages = {
            "/features/": page(
                "<p>DHUN has no accounts, so there is no cross-device sync, "
                "no iOS build, no web player and no FLAC claim.</p>"
            )
        }
        self.assertEqual(claims.forbidden_claim_violations(pages), [])

    def test_negation_must_be_in_the_same_or_previous_clause(self):
        pages = {
            "/": page(
                "<p>DHUN has no accounts. Sync across devices is coming next year.</p>"
            )
        }
        self.assertTrue(claims.forbidden_claim_violations(pages))


class RequiredCaveats(unittest.TestCase):
    REQUIRED = (
        ("rolling-unverified", "<p>Rolling UNVERIFIED development build</p>"),
        ("borrowed-time", "<p>This is borrowed time.</p>"),
        ("hardware-gates-open", "<p>Hardware gates still open</p>"),
    )

    def front_page(self, blocks: list[str]) -> dict[str, str]:
        return {"/": page("".join(blocks))}

    def test_all_three_pass_together(self):
        blocks = [
            f"<div data-caveat=\"{key}\">{body}</div>" for key, body in self.REQUIRED
        ]
        self.assertEqual(claims.required_caveat_violations(self.front_page(blocks)), [])

    def test_missing_caveat_fails(self):
        blocks = [
            f"<div data-caveat=\"{key}\">{body}</div>"
            for key, body in self.REQUIRED
            if key != "borrowed-time"
        ]
        violations = claims.required_caveat_violations(self.front_page(blocks))
        self.assertTrue(any("borrowed-time" in v for v in violations), violations)

    def test_empty_caveat_element_fails(self):
        blocks = [
            "<div data-caveat=\"rolling-unverified\"></div>",
            "<div data-caveat=\"borrowed-time\"><p>borrowed time</p></div>",
            "<div data-caveat=\"hardware-gates-open\"><p>Hardware gates still open</p></div>",
        ]
        violations = claims.required_caveat_violations(self.front_page(blocks))
        self.assertTrue(any("rolling-unverified" in v for v in violations), violations)

    def test_section_wrapper_is_supported(self):
        markup = page(
            '<section data-caveat="borrowed-time"><p>Extraction is borrowed time.</p></section>'
        )
        blocks = claims._element_blocks(markup)
        self.assertIn("borrowed-time", blocks)


class NoBakedDigests(unittest.TestCase):
    def test_flags_a_sha256(self):
        digest = "a" * 64
        violations = claims.digest_violations({"/features/": f"<p>SHA-256: {digest}</p>"})
        self.assertTrue(violations)

    def test_flags_a_byte_size(self):
        violations = claims.digest_violations(
            {"/features/": "<p>dhun-test.apk — 18,405,859 bytes</p>"}
        )
        self.assertTrue(violations)

    def test_flags_a_megabyte_size(self):
        violations = claims.digest_violations({"/features/": "<p>The APK is 18.4 MB.</p>"})
        self.assertTrue(violations)

    def test_allows_the_sidecar_url(self):
        documents = {
            "/features/": (
                "<a href=\"https://github.com/99ggprooo00-code/DHUN/releases/download/test/"
                "dhun-test.apk.sha256\">Get the .sha256 sidecar</a>"
            )
        }
        self.assertEqual(claims.digest_violations(documents), [])

    def test_allows_hex_colours_and_css(self):
        documents = {"/": "<style>.a{color:#bb86fc;grid-template-columns:1fr auto}</style>"}
        self.assertEqual(claims.digest_violations(documents), [])


class BuiltOutput(unittest.TestCase):
    """The gates as they actually run in CI against the committed build."""

    @classmethod
    def setUpClass(cls):
        if not DIST.is_dir():
            raise AssertionError(
                f"{DIST} is missing — the honesty contract is asserted against the "
                "built site, so the committed build must be present"
            )
        cls.pages = claims.load_built_pages(DIST)

    def test_every_route_built(self):
        self.assertEqual(set(self.pages), set(claims.ROUTES))

    def test_no_forbidden_claims(self):
        self.assertEqual(claims.forbidden_claim_violations(self.pages), [])

    def test_required_caveats_present(self):
        self.assertEqual(claims.required_caveat_violations(self.pages), [])

    def test_no_baked_digests_in_built_pages_or_sources(self):
        documents = {**self.pages, **claims.load_site_sources()}
        self.assertEqual(claims.digest_violations(documents), [])

    def test_no_release_asset_is_linked_and_the_release_page_is(self):
        """Downloads are not this site's business: point, never hand out.

        Until this session the download page linked all eight rolling assets
        directly. That page is gone by decision, and the rule is inverted: a
        direct link to an artifact is a defect (the files are unverified and
        replaced on every merge), while the release *page* — where the warning
        lives next to the files — must still be reachable from the site.
        """
        for route, markup in self.pages.items():
            artifacts = re.findall(r'href="([^"]*/releases/download/[^"]*)"', markup)
            self.assertEqual(artifacts, [], f"{route} links a release asset")
            for extension in (".apk", ".msi", ".aab", ".sha256"):
                self.assertNotRegex(
                    markup,
                    rf'href="[^"]*\{extension}"',
                    f"{route} links a {extension} file",
                )
        self.assertTrue(
            any("/releases/tag/test" in markup for markup in self.pages.values()),
            "the release page is not reachable from any built page",
        )

    def test_front_page_states_the_honest_status_visibly(self):
        text = claims.html_to_text(self.pages["/"]).lower()
        for phrase in (
            "rolling unverified development build",
            "borrowed time",
            "hardware gates still open",
            "gpl-3.0",
        ):
            self.assertIn(phrase, text)

    def test_claims_carry_a_citation_comment(self):
        """Every claim block in the built HTML is traceable to an ADR or PR."""
        index = self.pages["/"]
        comments = re.findall(r"<!--(.*?)-->", index, re.S)
        cited = [c.strip() for c in comments if re.search(r"ADR-\d+|PR #\d+|\.md", c)]
        self.assertGreaterEqual(len(cited), 8, comments)


if __name__ == "__main__":
    unittest.main()
