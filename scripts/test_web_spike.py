"""Contract tests for ADR-008's disposable B1 browser feasibility probe.

B1 is deliberately smaller than a website or Web target. These tests keep it a
self-contained, credential-free diagnostic page and prevent a later edit from
quietly adding remote assets, persistence, a proxy, autoplay, or a Web-support
claim before the deployed-origin evidence exists.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPIKE = ROOT / "web-spike"
HTML = SPIKE / "index.html"
CSS = SPIKE / "styles.css"
JS = SPIKE / "probe.js"
ADR = ROOT / "docs" / "decisions" / "ADR-008-browser-web-player-target.md"


class WebSpikeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html = HTML.read_text()
        cls.css = CSS.read_text()
        cls.js = JS.read_text()
        cls.adr = ADR.read_text()

    def test_probe_is_three_self_contained_static_files(self):
        self.assertTrue(HTML.exists())
        self.assertTrue(CSS.exists())
        self.assertTrue(JS.exists())
        self.assertEqual(
            {"index.html", "styles.css", "probe.js"},
            {path.name for path in SPIKE.iterdir() if path.is_file()},
        )
        self.assertIn('href="./styles.css"', self.html)
        self.assertIn('src="./probe.js"', self.html)
        self.assertNotRegex(self.html, r'<(?:script|link)[^>]+(?:src|href)="https?://')
        self.assertNotIn("<img", self.html)
        self.assertNotIn("<iframe", self.html)

    def test_page_is_noindex_and_has_a_strict_network_allowlist(self):
        self.assertIn('name="robots" content="noindex, nofollow"', self.html)
        self.assertIn('name="referrer" content="no-referrer"', self.html)
        self.assertIn("default-src 'self'", self.html)
        self.assertIn("connect-src https://www.youtube.com https://music.youtube.com https://*.googlevideo.com", self.html)
        self.assertIn("media-src https://*.googlevideo.com", self.html)
        self.assertIn("frame-src 'none'", self.html)
        self.assertIn("object-src 'none'", self.html)

    def test_probe_cannot_masquerade_as_a_product_or_autoplay(self):
        self.assertIn("not a Web player", self.html)
        self.assertIn("A pass still stops at B2", self.html)
        self.assertNotIn("autoplay", self.html)
        self.assertIn('preload="none"', self.html)
        self.assertIn('crossorigin="anonymous"', self.html)
        self.assertIn('id="playResolved"', self.html)
        self.assertIn('id="heardAudio"', self.html)

    def test_requests_are_fixed_and_credential_free(self):
        urls = set(re.findall(r'https://[^"`\s]+', self.js))
        allowed = {
            "https://music.youtube.com/youtubei/v1/player?prettyPrint=false",
            "https://www.youtube.com/oembed",
            "https://www.youtube.com/watch?v=${videoId}",
        }
        self.assertEqual(allowed, urls)
        self.assertIn('credentials: "omit"', self.js)
        self.assertIn('referrerPolicy: "no-referrer"', self.js)
        self.assertIn('mode: "cors"', self.js)
        self.assertNotIn('credentials: "include"', self.js)

    def test_probe_has_no_persistence_telemetry_or_credentials_api(self):
        forbidden = (
            "localStorage",
            "sessionStorage",
            "indexedDB",
            "document.cookie",
            "sendBeacon",
            "WebSocket",
            "EventSource",
            "gtag(",
            "analytics",
        )
        for token in forbidden:
            self.assertNotIn(token, self.js, f"B1 must not use {token}")

    def test_stream_url_is_allowlisted_and_excluded_from_report(self):
        self.assertIn('url.hostname.endsWith(".googlevideo.com")', self.js)
        self.assertIn("responseRedirected: response.redirected", self.js)
        self.assertIn('finalHost: finalUrlAllowed ? new URL(response.url).hostname : "not allow-listed"', self.js)
        self.assertIn("streamUrlsExported: false", self.js)
        self.assertIn("queryExported: false", self.js)
        self.assertIn("urlExported: false", self.js)
        self.assertIn("responseBodiesExported: false", self.js)
        self.assertIn("safeError(mediaError).message", self.js)
        self.assertNotIn("selectedUrl.toString()", self.js)
        self.assertNotIn("report.streamUrl", self.js)

    def test_web_remix_probe_identity_matches_the_production_fallback(self):
        source = (
            ROOT
            / "shared/src/commonMain/kotlin/dev/dhun/innertube/InnerTubeClient.kt"
        ).read_text()
        header_id = re.search(
            r'CLIENT_NAME_WEB_REMIX\s*=\s*"([^"]+)"', source
        ).group(1)
        version = re.search(
            r'CLIENT_VERSION_FALLBACK\s*=\s*"([^"]+)"', source
        ).group(1)
        self.assertIn(f'clientHeaderId: "{header_id}"', self.js)
        self.assertIn(f'clientVersion: "{version}"', self.js)

    def test_adr_accepts_only_b1_and_rejects_product_scope(self):
        self.assertIn("ACCEPTED FOR B1 FEASIBILITY ONLY", self.adr)
        self.assertIn("B1 is the entire authorized implementation scope", self.adr)
        self.assertIn("No unapproved proxy", self.adr)
        self.assertIn("No claim before proof", self.adr)


if __name__ == "__main__":
    unittest.main()
