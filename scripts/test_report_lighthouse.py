#!/usr/bin/env python3
"""Tests for the Lighthouse reporter.

`scripts/report_lighthouse.py` is the only reader of the Lighthouse reports, and
it failed silently in CI: an undefined name raised *after* the notices printed,
so the job exited 1 with a bare "Process completed with exit code 1" and no
annotation saying why. A crash in the reporter is a missing measurement, which
is exactly what these tests exist to prevent.

No network and no Lighthouse: the reports are synthetic JSON, and the assertions
are about the annotations (which are the only channel this environment can read)
and the exit code (which is the gate).
"""

import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

SCRIPTS = pathlib.Path(__file__).resolve().parent
REPORTER = SCRIPTS / "report_lighthouse.py"

sys.path.insert(0, str(SCRIPTS))

import report_lighthouse as reporter  # noqa: E402


def report(
    performance: float = 1.0,
    accessibility: float = 1.0,
    with_categories: bool = True,
) -> dict:
    categories = {
        key: {
            "score": score,
            "auditRefs": [{"id": "colour-contrast"}, {"id": "first-contentful-paint"}],
        }
        for key, score in (
            ("performance", performance),
            ("accessibility", accessibility),
            ("best-practices", 1.0),
            ("seo", 1.0),
        )
    }
    markdown = {
        "first-contentful-paint": {"numericValue": 900.0, "score": 1, "title": "FCP"},
        "largest-contentful-paint": {"numericValue": 1000.0, "score": 1, "title": "LCP"},
        "total-blocking-time": {"numericValue": 0.0, "score": 1, "title": "TBT"},
        "cumulative-layout-shift": {"numericValue": 0.0, "score": 1, "title": "CLS"},
        "speed-index": {"numericValue": 900.0, "score": 1, "title": "SI"},
        "total-byte-weight": {"numericValue": 54000},
        "network-requests": {"details": {"items": [{}, {}]}},
        "colour-contrast": {"score": 1, "title": "Contrast", "scoreDisplayMode": "binary"},
        "first-contentful-paint-audit": {"score": 1, "title": "FCP", "scoreDisplayMode": "numeric"},
        "render-blocking-resources": {"details": {"items": []}},
    }
    payload = {"audits": markdown}
    if with_categories:
        payload["categories"] = categories
    return payload


class Reporter(unittest.TestCase):
    def run_reporter(self, *reports: dict) -> tuple[int, str]:
        with tempfile.TemporaryDirectory() as tmp:
            paths = []
            for index, payload in enumerate(reports):
                path = pathlib.Path(tmp, f"report-{index}.json")
                path.write_text(json.dumps(payload), encoding="utf-8")
                paths.append(str(path))
            completed = subprocess.run(
                [sys.executable, str(REPORTER), "/ui/", *paths],
                capture_output=True,
                text=True,
                check=False,
            )
        return completed.returncode, completed.stdout + completed.stderr

    def test_a_clean_route_passes_and_reports_numbers(self):
        code, output = self.run_reporter(report(), report(), report())
        self.assertEqual(code, 0, output)
        self.assertIn("performance=100", output)
        self.assertIn("bytes=54.0kB", output)

    def test_a_low_accessibility_score_fails_the_gate(self):
        code, output = self.run_reporter(report(accessibility=0.9), report(accessibility=0.9))
        self.assertEqual(code, 1, output)
        self.assertIn("::error title=Lighthouse /ui/", output)

    def test_a_declining_score_is_reported_per_sample(self):
        code, output = self.run_reporter(report(0.96), report(1.0), report(1.0))
        self.assertEqual(code, 0, output)
        self.assertIn("run 1: 96", output)
        self.assertIn("median", output)

    def test_one_notice_per_route_so_the_numbers_survive(self):
        """GitHub returns about ten annotations per level: six per route lost
        the third route entirely in run 37806986878."""
        code, output = self.run_reporter(report(), report(), report())
        notices = [line for line in output.splitlines() if line.startswith("::notice")]
        self.assertEqual(len(notices), 1, output)
        self.assertEqual(code, 0)

    def test_a_report_without_categories_is_an_annotated_error(self):
        code, output = self.run_reporter(report(with_categories=False))
        self.assertEqual(code, 1, output)
        self.assertIn("::error title=Lighthouse /ui/", output)


if __name__ == "__main__":
    unittest.main()


class SubresourcesAreNamed(unittest.TestCase):
    """A request count is not evidence; the reporter must name the requests.

    On the merged head every route reported `requests=2` for a whole session and
    `network-dependency-tree-insight: 3 item(s)`, which no one could act on: the
    one extra subresource was never named in the only channel that is readable
    from this repository. These tests pin the naming.
    """

    def run_reporter(self, *reports: dict) -> tuple[int, str]:
        with tempfile.TemporaryDirectory() as tmp:
            paths = []
            for index, payload in enumerate(reports):
                path = pathlib.Path(tmp, f"report-{index}.json")
                path.write_text(json.dumps(payload), encoding="utf-8")
                paths.append(str(path))
            completed = subprocess.run(
                [sys.executable, str(REPORTER), "/ui/", *paths],
                capture_output=True,
                text=True,
                check=False,
            )
        return completed.returncode, completed.stdout + completed.stderr

    def test_a_single_request_is_reported_as_the_document_only(self):
        payload = report()
        payload["audits"]["network-requests"]["details"]["items"] = [
            {"url": "http://127.0.0.1:8080/ui/", "resourceType": "document", "transferSize": 55000}
        ]
        code, output = self.run_reporter(payload)
        self.assertEqual(code, 0, output)
        self.assertIn("requests=1", output)
        self.assertIn("subresources: none", output)

    def test_an_extra_subresource_is_named(self):
        payload = report()
        payload["audits"]["network-requests"]["details"]["items"] = [
            {"url": "http://127.0.0.1:8080/ui/", "resourceType": "document", "transferSize": 55000},
            {
                "url": "http://127.0.0.1:8080/assets/dhun-favicon.svg",
                "resourceType": "other",
                "transferSize": 512,
            },
        ]
        code, output = self.run_reporter(payload)
        self.assertEqual(code, 0, output)
        self.assertIn("requests=2", output)
        self.assertIn("dhun-favicon.svg (512 B, other)", output)

    def test_dependency_tree_items_are_reported_by_url(self):
        payload = report()
        payload["audits"]["network-dependency-tree-insight"] = {
            "details": {
                "items": [
                    {"url": "http://127.0.0.1:8080/ui/", "type": "document"},
                    {"url": "http://127.0.0.1:8080/assets/dhun-favicon.svg", "type": "other"},
                ]
            }
        }
        code, output = self.run_reporter(payload)
        self.assertEqual(code, 0, output)
        self.assertIn("network-dependency-tree-insight: ", output)
        self.assertIn("dhun-favicon.svg", output)
        self.assertNotIn("3 item(s)", output)

    def test_an_item_without_a_url_still_says_so(self):
        """The insight's shape is not a stable API: an unparseable item must
        report itself, not vanish into a count."""
        payload = report()
        payload["audits"]["network-dependency-tree-insight"] = {
            "details": {"items": [{"chain": {"depth": 2}}]}
        }
        code, output = self.run_reporter(payload)
        self.assertEqual(code, 0, output)
        self.assertIn("no url field", output)

    def test_urls_are_found_however_the_item_is_nested(self):
        urls = reporter._urls_in(
            [{"tree": {"nodes": [{"request": {"url": "a"}}, {"url": "b"}]}}, {"url": "a"}]
        )
        self.assertEqual(urls, ["a", "b"])
