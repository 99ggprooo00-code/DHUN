"""Repository hygiene checks that run in CI with the other scripts/test_*.py suites.

Every check works on the git index (`git ls-files`) or on tracked Markdown and
workflow text. None needs the network, Gradle, Node or a browser. A failure
names the file and the rule. It never prints a matched secret value.
"""

import json
import re
import subprocess
import unittest
from collections import Counter
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
SELF = "scripts/test_repo_health.py"

# Token shapes that must never be committed. Each pattern is intentionally
# strict so that ordinary prose does not match.
SECRET_PATTERNS = {
    "private key block": re.compile(r"-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY"),
    "GitHub token": re.compile(r"\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36,}\b|\bgithub_pat_[A-Za-z0-9_]{20,}"),
    "AWS access key id": re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    "Slack token": re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}"),
    "Google API key": re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b"),
}

# Documented, public values that are allowed. Maps (path, rule) -> exact count.
# The InnerTube web client key is a public key embedded in the client, not a
# project credential (see .ai/DEPENDENCY_AUDIT.md, 2026-10-10 entry). Its value
# is deliberately not written here.
ALLOWED_PUBLIC_VALUES = {
    ("app-web/src/js/catalog.js", "Google API key"): 1,
}

KEY_SUFFIXES = (".p12", ".pfx", ".jks", ".keystore", ".pem", ".key")
ONLY_TRACKED_KEYSTORE = "app-android/keystores/dhun-test.p12"

DEBRIS_NAME = re.compile(r"(^|/)(\.DS_Store|Thumbs\.db|ehthumbs\.db|desktop\.ini)$|(^|/)\._[^/]*$|(\.swp|\.bak|\.orig|\.rej|~)$")
BUILD_SUFFIXES = (".apk", ".aab", ".msi", ".hprof", ".pyc")
GENERATED_SEGMENTS = {"__pycache__", "node_modules", ".gradle", ".kotlin", "test-results", "playwright-report"}


def tracked_files():
    out = subprocess.run(
        ["git", "ls-files", "-z"], cwd=ROOT, check=True, capture_output=True
    ).stdout
    return [p for p in out.decode("utf-8").split("\0") if p]


def strip_fenced_code(text):
    return re.sub(r"```.*?```", "", text, flags=re.S)


class RepoHealthTest(unittest.TestCase):
    def test_markdown_relative_links_resolve(self):
        link = re.compile(r"\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
        broken = []
        for rel in tracked_files():
            if not rel.endswith(".md"):
                continue
            text = strip_fenced_code((ROOT / rel).read_text(encoding="utf-8", errors="replace"))
            for match in link.finditer(text):
                target = match.group(1)
                if target.startswith(("http://", "https://", "mailto:", "#")):
                    continue
                path = unquote(target.split("#", 1)[0])
                if not path:
                    continue
                if path.startswith("/"):
                    # Root-absolute links would break under the GitHub Pages /DHUN/ base.
                    broken.append(f"{rel} -> {target} (root-absolute path in a Markdown file)")
                    continue
                candidate = (ROOT / rel).parent / path
                if not candidate.resolve().exists():
                    broken.append(f"{rel} -> {target}")
        self.assertEqual([], broken, "broken relative Markdown links")

    def test_no_secret_patterns_outside_the_public_allowlist(self):
        found = Counter()
        for rel in tracked_files():
            if rel == SELF:
                continue
            data = (ROOT / rel).read_bytes()
            if b"\0" in data:
                continue  # binary: not text-scannable
            text = data.decode("utf-8", errors="replace")
            for name, pattern in SECRET_PATTERNS.items():
                count = len(pattern.findall(text))
                if count:
                    found[(rel, name)] += count
        unexpected = {k: v for k, v in found.items() if ALLOWED_PUBLIC_VALUES.get(k, 0) != v}
        missing = {k: v for k, v in ALLOWED_PUBLIC_VALUES.items() if found.get(k, 0) != v}
        # Report paths, rule names and counts only. Never the matched text.
        self.assertEqual({}, unexpected, "secret-pattern hits outside the allowlist (paths and rules only)")
        self.assertEqual({}, missing, "allowlisted public value no longer matches; update the allowlist")

    def test_only_the_documented_public_keystore_is_tracked(self):
        keys = sorted(p for p in tracked_files() if p.lower().endswith(KEY_SUFFIXES))
        self.assertEqual([ONLY_TRACKED_KEYSTORE], keys)
        security = (ROOT / "SECURITY.md").read_text(encoding="utf-8")
        self.assertIn("app-android/keystores/dhun-test.p12", security)
        self.assertIn("public", security.lower())

    def test_no_editor_os_or_build_debris_is_tracked(self):
        bad = []
        for rel in tracked_files():
            parts = set(rel.split("/"))
            if DEBRIS_NAME.search(rel):
                bad.append(f"{rel}: OS or editor debris")
            elif rel.endswith(BUILD_SUFFIXES):
                bad.append(f"{rel}: build output or release binary")
            elif parts & GENERATED_SEGMENTS:
                bad.append(f"{rel}: generated directory")
        self.assertEqual([], bad)

    def test_app_web_ships_no_runtime_or_dev_dependency(self):
        package = json.loads((ROOT / "app-web/package.json").read_text(encoding="utf-8"))
        self.assertFalse(package.get("dependencies"), "app-web must ship no runtime dependency")
        self.assertFalse(package.get("devDependencies"), "app-web must ship no dev dependency")
        self.assertFalse(
            [p for p in tracked_files() if p.startswith("app-web/") and p.endswith(("package-lock.json", "yarn.lock", "pnpm-lock.yaml"))],
            "app-web has no lockfile because it has no dependencies",
        )

    def test_extraction_health_probe_failure_still_fails_the_check(self):
        # The live probe is continue-on-error by design, so a red probe is
        # reported by a later step. That step must keep existing and must fail.
        text = (ROOT / ".github/workflows/extraction-health.yml").read_text(encoding="utf-8")
        self.assertIn("id: probe", text)
        self.assertIn("continue-on-error: true", text)
        self.assertIn("name: Keep the check non-zero when live health is unverified", text)
        self.assertIn("if: steps.probe.outcome == 'failure'", text)
        self.assertIn("exit 1", text)

    def test_no_workflow_runs_the_stale_publish_helper(self):
        # scripts/publish.sh rewrites origin to SSH and pushes main directly.
        # It is not part of the release path and must not be run by CI.
        for rel in tracked_files():
            if rel.startswith(".github/workflows/"):
                text = (ROOT / rel).read_text(encoding="utf-8")
                self.assertFalse("scripts/publish.sh" in text, f"{rel} runs the stale scripts/publish.sh")


if __name__ == "__main__":
    unittest.main()
