"""Cross-surface direction/status checks, including deliberately stale mutations.

Provider-specific implementation details and historical records are legitimate;
these contracts do not ban YouTube, JioSaavn or Spotify from the repository.
"""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

import product_claims as claims

ROOT = Path(__file__).resolve().parents[1]

DIRECTION = (
    "DHUN is a free, open-source, cross-platform multi-source music client. "
    "YouTube / YouTube Music is first implementation priority. "
    "Native Android/desktop use YouTube Music; browser currently uses JioSaavn. "
    "Spotify is planned, not implemented, deferred pending technical, account, "
    "policy, legal, playback, privacy and cost checks."
)


def page() -> str:
    return (
        '<title>DHUN multi-source music client</title>'
        '<meta name="description" content="DHUN multi-source music client">'
        '<meta property="og:description" content="DHUN multi-source music client">'
        '<meta name="twitter:description" content="DHUN multi-source music client">'
        '<script type="application/ld+json">'
        + json.dumps({"description": "DHUN multi-source music client"})
        + '</script><p data-product-direction>' + DIRECTION + '</p>'
        '<a>Native Terms draft</a><a>Native Privacy draft</a>'
        '<a>Browser Terms draft</a><a>Browser Privacy draft</a>'
    )


class ProductDirection(unittest.TestCase):
    def test_real_current_surfaces_and_generated_pages_agree(self):
        self.assertEqual([], claims.repository_problems())

    def test_status_and_real_provider_details_are_allowed(self):
        self.assertEqual([], claims.direction_problems(DIRECTION))

    def test_does_not_require_identical_native_and_browser_sources(self):
        self.assertEqual([], claims.direction_problems(DIRECTION.replace("currently uses", "reads")))

    def test_single_source_identity_regression_fails(self):
        problems = claims.direction_problems(DIRECTION.replace("multi-source", "YouTube Music-only"))
        self.assertIn("multi-source identity", problems)

    def test_implemented_spotify_cannot_replace_the_plan(self):
        problems = claims.direction_problems(DIRECTION.replace("planned, not implemented", "implemented and supported"))
        self.assertIn("Spotify explicitly planned", problems)
        self.assertIn("Spotify not implemented", problems)

    def test_premium_is_not_the_only_feasibility_gate(self):
        self.assertIn("Spotify feasibility gates (not Premium alone)", claims.direction_problems(DIRECTION.replace("policy", "Premium")))

    def test_renaming_youtube_as_browser_source_fails(self):
        problems = claims.direction_problems(DIRECTION.replace("browser currently uses JioSaavn", "browser currently uses YouTube Music"))
        self.assertIn("browser JioSaavn scope", problems)

    def test_soft_wrapping_and_markdown_do_not_break_semantics(self):
        self.assertEqual([], claims.direction_problems(DIRECTION.replace("multi-source", "**multi-source**").replace("Spotify is planned", "Spotify is\nplanned")))


class PublicOutput(unittest.TestCase):
    def pages(self):
        return {route: page() for route in claims.ROUTES}

    def test_all_four_routes_pass(self):
        self.assertEqual([], claims.website_direction_problems(self.pages()))

    def test_deleting_an_actual_page_is_a_finding(self):
        pages = self.pages()
        del pages["/ui/"]
        self.assertTrue(any("/ui/: missing" in p for p in claims.website_direction_problems(pages)))

    def test_single_source_metadata_regression_is_detected(self):
        pages = self.pages()
        pages["/"] = pages["/"].replace('<title>DHUN multi-source music client</title>', '<title>DHUN YouTube Music client</title>')
        self.assertIn("/: title loses multi-source identity", claims.website_direction_problems(pages))

    def test_stale_structured_data_cannot_hide_behind_visible_copy(self):
        pages = self.pages()
        pages["/"] = pages["/"].replace('"description": "DHUN multi-source music client"', '"description": "DHUN YouTube Music-only"')
        self.assertIn("/: JSON-LD description loses multi-source direction", claims.website_direction_problems(pages))

    def test_browser_legal_links_cannot_be_silently_removed(self):
        pages = self.pages()
        pages["/features/"] = pages["/features/"].replace("Browser Privacy draft", "Privacy")
        self.assertTrue(any("/features/: missing labelled legal" in p for p in claims.website_direction_problems(pages)))


class AuthorityAndPrivacy(unittest.TestCase):
    def documents(self):
        names = set(claims.PRIMARY + claims.INSTRUCTIONS + ("legal/privacy.md", "app-web/tools/build.mjs"))
        return {name: (ROOT / name).read_text(encoding="utf-8") for name in names}

    def test_agent_instructions_must_point_to_authoritative_vision(self):
        docs = self.documents()
        docs[".ai/MASTER_PROMPT.md"] = "DHUN only streams YouTube Music."
        self.assertTrue(any("MASTER_PROMPT" in p for p in claims.source_problems(docs)))

    def test_native_privacy_must_not_forget_real_cache_and_network_flows(self):
        docs = self.documents()
        docs["legal/privacy.md"] = "Everything stays on the device."
        self.assertTrue(any("data-flow disclosure" in p for p in claims.source_problems(docs)))

    def test_site_source_evidence_paths_resolve(self):
        data = (ROOT / "website/src/_data/site.js").read_text(encoding="utf-8")
        paths = re.findall(r'path:\s*"([^"\n]+)"', data)
        self.assertGreater(len(paths), 20)
        for path in paths:
            with self.subTest(path=path):
                self.assertTrue((ROOT / path.split(" (")[0]).exists(), "stale implementation citation")

    def test_native_gallery_does_not_offer_browser_native_only_features(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        self.assertNotIn("/app/#/library/downloads", readme)
        self.assertNotIn("/app/#/player/lyrics", readme)
        self.assertNotIn("Open Desktop view in", readme)
        self.assertIn("historical native captures", readme)
        self.assertIn("Browser lyrics unavailable", readme)
        self.assertIn("Browser audio downloads unavailable", readme)
        ui = (ROOT / "website/src/ui.njk").read_text(encoding="utf-8")
        data = (ROOT / "website/src/_data/site.js").read_text(encoding="utf-8")
        self.assertNotIn("direct link into that exact screen", ui)
        self.assertNotIn("Open Desktop layout in the app", data)
        self.assertNotIn("so no unpermitted copyrighted art", data)

    def test_inactive_spotify_is_not_silently_added_as_a_runtime_dependency(self):
        # Comments/research/plans are allowed. A real SDK/import needs review and
        # a deliberate update of status/evidence/contracts, not just a new word.
        offenders = []
        for path in list(ROOT.glob("**/*.gradle.kts")) + list((ROOT / "shared/src").rglob("*.kt")):
            if any(part in {"node_modules", "build", ".gradle"} for part in path.parts):
                continue
            text = path.read_text(encoding="utf-8")
            if re.search(r'^\s*import\s+com\.spotify\.|(?:implementation|api)\("com\.spotify:', text, re.M):
                offenders.append(str(path.relative_to(ROOT)))
        self.assertEqual([], offenders, "Spotify remains Planned/Deferred, not an approved SDK implementation")


if __name__ == "__main__":
    unittest.main()
