"""Tests for the served-site smoke check (`scripts/website_smoke.py`).

The check itself needs the internet; the *rules* do not, so they are fed
synthetic responses here — a clean page set must pass, and each defect the
script exists to catch must fail. The live run happens in the `served` job of
`.github/workflows/website.yml`, after a real deploy.
"""

import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import website_claims as claims  # noqa: E402
import website_smoke as smoke  # noqa: E402

DIST = pathlib.Path(__file__).resolve().parent.parent / "website" / "dist"


def page(route: str, body: str = "<h1>DHUN</h1>") -> str:
    return (
        "<!DOCTYPE html><html lang=\"en\"><head>"
        f'<link rel="canonical" href="https://99ggprooo00-code.github.io/DHUN{route}">'
        "</head><body>" + body + "</body></html>"
    )


def front_page(body: str = "<h1>DHUN</h1>") -> str:
    """The front page as it ships: with the three required disclosures present."""
    blocks = "".join(
        f'<div data-caveat="{key}">{phrases[0]}</div>'
        for key, phrases in claims.REQUIRED_CAVEATS.items()
    )
    return page("/", body + blocks)


def served(**overrides: tuple[int, str]) -> dict[str, tuple[int, str]]:
    """route -> (status, body), with the real front page by default."""
    pages = {
        "/": (200, front_page()),
        "/features/": (200, page("/features/", "<h1>DHUN</h1>")),
        "/download/": (200, page("/download/", "<h1>DHUN</h1>")),
    }
    pages.update(overrides)
    return pages


class ResponseRules(unittest.TestCase):
    def test_clean_pages_have_no_problems(self):
        self.assertEqual(smoke.smoke_problems(served()), [])

    def test_non_200_fails(self):
        problems = smoke.smoke_problems(served(**{"/download/": (500, "")}))
        self.assertTrue(any("HTTP 500" in p for p in problems), problems)

    def test_empty_body_fails(self):
        problems = smoke.smoke_problems(served(**{"/features/": (200, "  ")}))
        self.assertTrue(any("empty body" in p for p in problems), problems)

    def test_wrong_canonical_url_fails(self):
        markup = page("/").replace("github.io/DHUN/", "example.com/")
        problems = smoke.smoke_problems(served(**{"/": (200, markup)}))
        self.assertTrue(any("canonical URL" in p for p in problems), problems)

    def test_forbidden_claim_on_a_served_page_fails(self):
        markup = front_page("<h1>DHUN</h1><p>DHUN is also available for iOS.</p>")
        problems = smoke.smoke_problems(served(**{"/": (200, markup)}))
        self.assertTrue(any("forbidden claim" in p for p in problems), problems)

    def test_negated_forbidden_term_is_allowed(self):
        """The site must be able to say what it does not have."""
        markup = front_page("<h1>DHUN</h1><p>There is no iOS build.</p>")
        self.assertEqual(smoke.smoke_problems(served(**{"/": (200, markup)})), [])


class RequiredCaveats(unittest.TestCase):
    """The three disclosures must be on the *served* front page, not just in Git."""

    def test_complete_front_page_passes(self):
        self.assertEqual(smoke.smoke_problems(served()), [])

    def test_missing_caveat_fails(self):
        markup = front_page().replace('data-caveat="borrowed-time"', 'data-caveat="gone"')
        problems = smoke.smoke_problems(served(**{"/": (200, markup)}))
        self.assertTrue(any("borrowed-time" in p for p in problems), problems)

    def test_legacy_readme_homepage_fails(self):
        """What the canonical URL actually serves while Pages is `legacy`."""
        problems = smoke.smoke_problems(served(**{"/": (200, "<html><body><p>DHUN — build matrix</p></body></html>")}))
        self.assertTrue(len(problems) >= 3, problems)


class ServedLinks(unittest.TestCase):
    def test_root_relative_links_are_collected(self):
        markup = page("/").replace("<h1>DHUN</h1>", '<h1>DHUN</h1><a href="/download/">d</a><a href="/features/">f</a>')
        self.assertEqual(smoke.internal_links(markup), ["/download/", "/features/"])

    def test_broken_internal_link_on_a_served_page_fails(self):
        markup = page("/").replace("<h1>DHUN</h1>", '<h1>DHUN</h1><a href="/gone/">x</a>')

        def fetch_one(url: str) -> tuple[int, str]:
            return (404, "") if url.endswith("/gone/") else (200, "<html></html>")

        problems = smoke.link_problems("https://example.test", served(**{"/": (200, markup)}), fetch_one)
        self.assertTrue(any("/gone/" in p and "404" in p for p in problems), problems)

    def test_resolving_internal_links_pass(self):
        markup = page("/").replace("<h1>DHUN</h1>", '<h1>DHUN</h1><a href="/download/">d</a>')
        problems = smoke.link_problems(
            "https://example.test", served(**{"/": (200, markup)}), lambda url: (200, "")
        )
        self.assertEqual(problems, [])


class CommittedBuild(unittest.TestCase):
    """Feed the real committed build through the served-site rules.

    This is not a substitute for fetching the live URL — it is the check that
    the rules the live run applies would pass on the bytes this repository
    actually publishes, so a failure in the `served` job points at deployment
    rather than at the pages.
    """

    def test_committed_pages_pass_the_served_site_rules(self):
        files = {"/": "index.html", "/features/": "features/index.html", "/download/": "download/index.html"}
        fetched = {
            route: (200, (DIST / name).read_text(encoding="utf-8"))
            for route, name in files.items()
            if (DIST / name).is_file()
        }
        self.assertEqual(len(fetched), 3, f"built pages missing under {DIST}")
        self.assertEqual(smoke.smoke_problems(fetched), [])


if __name__ == "__main__":
    unittest.main()
