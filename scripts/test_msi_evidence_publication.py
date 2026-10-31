"""Execute the MSI evidence heredoc with a local fake ``gh`` (never the network)."""

from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


REPO = Path(__file__).resolve().parents[1]
WORKFLOW = REPO / ".github/workflows/test-release.yml"
STEP = "      - name: Publish immutable installed-app frame and result\n"


def evidence_source() -> str:
    text = WORKFLOW.read_text(encoding="utf-8")
    step = text.split(STEP, 1)[1]
    body = step.split("          python - <<'PY'\n", 1)[1].split("\n          PY", 1)[0]
    return "\n".join(
        line[10:] if line.startswith("          ") else line
        for line in body.splitlines()
    ) + "\n"


FAKE_GH = r'''#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys

calls_path = Path(os.environ["FAKE_GH_CALLS"])
calls = json.loads(calls_path.read_text()) if calls_path.exists() else []
body = Path(sys.argv[sys.argv.index("--input") + 1])
calls.append({"args": sys.argv[1:], "payload": json.loads(body.read_text())})
calls_path.write_text(json.dumps(calls))
if os.environ.get("FAKE_GH_MODE") == "fail":
    print("gh: Resource not accessible by integration (HTTP 403); unit-test-secret", file=sys.stderr)
    raise SystemExit(1)
print("a" * 40)
'''


class MsiEvidencePublicationTest(unittest.TestCase):
    maxDiff = None

    def setUp(self) -> None:
        self.source = evidence_source()
        compile(self.source, "msi-evidence-inline.py", "exec")

    def run_evidence(self, result: str, *, gh_mode: str = "success"):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence = root / "out/installer-check"
            evidence.mkdir(parents=True)
            (evidence / "result.txt").write_text(result, encoding="utf-8")
            (evidence / "app-diagnostics.txt").write_text(
                "note=Metadata only\nprocess=name=DHUN;id=42;handle=0;responding=True\n",
                encoding="utf-8",
            )
            script = root / "evidence.py"
            script.write_text(self.source, encoding="utf-8")
            fake_bin = root / "bin"
            fake_bin.mkdir()
            fake_gh = fake_bin / "gh"
            fake_gh.write_text(FAKE_GH, encoding="utf-8")
            fake_gh.chmod(0o755)
            calls = root / "calls.json"
            env = os.environ.copy()
            env.update({
                "PATH": str(fake_bin) + os.pathsep + env["PATH"],
                "GITHUB_REPOSITORY": "example/DHUN",
                "GITHUB_RUN_ID": "12345",
                "HEAD_SHA": "b" * 40,
                "GH_TOKEN": "unit-test-secret",
                "FAKE_GH_CALLS": str(calls),
                "FAKE_GH_MODE": gh_mode,
            })
            completed = subprocess.run(
                [sys.executable, str(script)],
                cwd=root,
                env=env,
                text=True,
                capture_output=True,
                check=False,
                timeout=20,
            )
            recorded = json.loads(calls.read_text()) if calls.exists() else []
            manifest_path = root / "build/test-evidence/packaged-windows-manifest.json"
            manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else None
            return completed, recorded, manifest

    def test_failed_launch_still_publishes_result_and_diagnostics(self) -> None:
        completed, calls, manifest = self.run_evidence(
            "status=failed\nappLaunch=failed\naudioAndVisuals=not tested\n"
        )
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        self.assertIn("::notice title=DHUN MSI result::", completed.stdout)
        self.assertIn("::notice title=DHUN MSI launch diagnostics::", completed.stdout)
        self.assertEqual(1, len(calls))
        args = calls[0]["args"]
        self.assertEqual("api", args[0])
        self.assertEqual("repos/example/DHUN/git/blobs", args[1])
        self.assertEqual("--input", args[2])
        self.assertEqual("--jq", args[4])
        self.assertEqual(".sha", args[5])
        self.assertNotIn("repos/$GITHUB_REPOSITORY", args)
        self.assertEqual("b" * 40, manifest["source"]["sha"])
        self.assertEqual("12345", manifest["source"]["run"])
        self.assertEqual([], manifest["images"])
        self.assertIn("appLaunch=failed", manifest["result"])
        self.assertIn("process=name=DHUN", manifest["safeLaunchDiagnostics"])

    def test_api_failure_preserves_annotations_and_redacts_secrets(self) -> None:
        completed, calls, _ = self.run_evidence(
            "status=failed\nappLaunch=failed\n",
            gh_mode="fail",
        )
        self.assertEqual(1, completed.returncode)
        self.assertEqual(1, len(calls))
        output = completed.stdout + completed.stderr
        self.assertLess(output.index("DHUN MSI result"), output.index("DHUN MSI launch diagnostics"))
        self.assertLess(output.index("DHUN MSI launch diagnostics"), output.index("HTTP 403"))
        self.assertIn("DHUN packaged Windows evidence publication", output)
        self.assertIn("<redacted>", output)
        self.assertNotIn("unit-test-secret", output)
        self.assertNotIn("Traceback", output)

    def test_passed_launch_without_png_fails_before_blob_operation(self) -> None:
        completed, calls, _ = self.run_evidence(
            "status=passed\nappLaunch=passed: native window opened\n"
        )
        self.assertEqual(1, completed.returncode)
        self.assertEqual([], calls)
        self.assertIn("no window PNG was preserved", completed.stdout)
        self.assertNotIn("manifest=", completed.stdout)


if __name__ == "__main__":
    unittest.main()
