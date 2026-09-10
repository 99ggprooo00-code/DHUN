"""Contract for the artifact-only debug-APK workflow (no third-party YAML parser).

Why this file exists: `.github/workflows/build-apk.yml` shipped calling
`./gradlew :app:assembleDebug` with artifact path `app/build/outputs/apk/debug/app-debug.apk`.
This repo has no `:app` module — Gradle fails with "project 'app' is ambiguous…
Candidates are: 'app-android', 'app-desktop'" — so the workflow was red on its
first two runs (34434063405 / 34432714601) and every later push inherits it.
A workflow that names a module or an output path nobody checked is exactly the
class of bug a static contract catches, so this pins it instead of re-reading.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github/workflows"


def gradle_modules():
    """Module dirs actually included by settings.gradle.kts (':app-android' -> 'app-android')."""
    text = (ROOT / "settings.gradle.kts").read_text()
    return {m.strip(':').replace(':', '/') for m in re.findall(r'include\("([^"]+)"\)', text)}


class ApkWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.path = WORKFLOWS / "build-apk.yml"
        self.assertTrue(self.path.exists(), "build-apk.yml is referenced by CI policy and must exist")
        self.text = self.path.read_text()

    def test_debug_apk_job_builds_the_android_module_and_uploads_its_real_output(self):
        self.assertIn("./gradlew :app-android:assembleDebug", self.text)
        self.assertNotIn(":app:assembleDebug", self.text.replace(":app-android:assembleDebug", ""))

        uploaded = re.search(r"^          path: (\S+)$", self.text, re.MULTILINE)
        self.assertIsNotNone(uploaded, "the upload step must name an artifact path")
        path = uploaded.group(1)
        module = path.split("/", 1)[0]
        self.assertIn(
            module,
            gradle_modules(),
            f"{path}: '{module}' is not a module included by settings.gradle.kts",
        )
        # The path must be the module's debug output, not a hand-written guess.
        self.assertRegex(
            path,
            rf"^{re.escape(module)}/build/outputs/apk/debug/{re.escape(module)}-debug\.apk$",
        )

    def test_build_job_never_asks_for_write_permissions(self):
        self.assertRegex(self.text, r"permissions:\n  contents: read\n")
        self.assertNotIn("contents: write", self.text)

    def test_apk_paths_across_all_workflows_point_at_included_modules(self):
        """One rule for every workflow: no `X/build/...` where X is not a Gradle module."""
        modules = gradle_modules()
        for workflow in sorted(WORKFLOWS.glob("*.yml")):
            for path in re.findall(r"[\w./-]*build/outputs/[\w./-]+", workflow.read_text()):
                head = path.split("*", 1)[0].split("/", 1)[0]
                if head.startswith("build/"):
                    continue  # relative to the module's own checkout step
                self.assertIn(
                    head,
                    modules,
                    f"{workflow.name}: '{path}' is not under an included module {sorted(modules)}",
                )


if __name__ == "__main__":
    unittest.main()
