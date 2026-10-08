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
        # ABI splits remove the single app-android-debug.apk. The upload must
        # be the module's debug APK directory, not a guessed module or the
        # pre-split filename (which assembleDebug no longer writes).
        self.assertRegex(
            path,
            rf"^{re.escape(module)}/build/outputs/apk/debug/\*.apk$",
        )
        self.assertNotIn(
            "path: app-android/build/outputs/apk/debug/app-android-debug.apk",
            self.text,
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


    def test_pre_26_launcher_icons_exist_and_are_not_adaptive(self):
        res = ROOT / "app-android/src/main/res"
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            legacy = res / "mipmap-anydpi" / name
            adaptive = (res / "mipmap-anydpi-v26" / name).read_text()
            self.assertTrue(
                legacy.is_file(),
                f"{legacy} is required; an adaptive-only icon does not resolve below API 26",
            )
            text = legacy.read_text()
            # Comments may name <adaptive-icon>; the element itself must not.
            body = text.split("-->", 1)[-1]
            self.assertIn("<layer-list", body)
            self.assertNotIn("<adaptive-icon", body)
            self.assertIn("<adaptive-icon", adaptive)
            self.assertIn("@drawable/ic_launcher_legacy", text)

    def test_minsdk_24_and_abi_splits_are_the_android_release_contract(self):
        gradle = (ROOT / "app-android/build.gradle.kts").read_text()
        shared = (ROOT / "shared/build.gradle.kts").read_text()
        self.assertIn("minSdk = 24", gradle)
        self.assertIn("minSdk = 24", shared)
        self.assertNotIn("minSdk = 26", gradle)
        self.assertNotIn("minSdk = 26", shared)
        self.assertIn('include("arm64-v8a", "armeabi-v7a")', gradle)
        self.assertIn("isUniversalApk = true", gradle)



if __name__ == "__main__":
    unittest.main()
