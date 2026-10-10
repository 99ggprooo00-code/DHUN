"""Version identity gate: the Android app, the release workflow and the docs must agree.

The release workflow hard-codes the fixed public version. This test keeps that
duplication honest without automating away the decision: changing the version
means changing every listed place in one reviewed commit. It does not choose a
next version and does not contact GitHub.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return (ROOT / relative).read_text(encoding="utf-8")


class ReleaseIdentityTest(unittest.TestCase):
    def setUp(self):
        self.gradle = read("app-android/build.gradle.kts")
        self.workflow = read(".github/workflows/test-release.yml")

    def test_android_identity_is_declared_once_in_gradle(self):
        self.assertEqual(1, len(re.findall(r'applicationId = "dev\.dhun\.android"', self.gradle)))
        self.assertEqual(1, len(re.findall(r"\bversionCode = \d+\b", self.gradle)))
        self.assertEqual(1, len(re.findall(r'versionName = "\d+\.\d+\.\d+"', self.gradle)))

    def test_workflow_fixed_version_matches_android_version_name(self):
        version = re.search(r'versionName = "(\d+\.\d+\.\d+)"', self.gradle).group(1)
        self.assertIn(f"version='v{version}'", self.workflow)
        self.assertIn(f"--version '{version}'", self.workflow)
        for asset in (f"dhun-v{version}.apk", f"dhun-v{version}.msi"):
            self.assertIn(asset, self.workflow)

    def test_release_notes_file_matches_the_fixed_version(self):
        version = re.search(r'versionName = "(\d+\.\d+\.\d+)"', self.gradle).group(1)
        self.assertTrue((ROOT / f"docs/releases/v{version}.md").is_file())
        self.assertIn(f"--notes-file docs/releases/v{version}.md", self.workflow)

    def test_installer_sequence_is_not_the_app_version(self):
        # The MSI counter is intentionally separate (see scripts/installer_version.py).
        gradle_desktop = read("app-desktop/build.gradle.kts")
        self.assertIn('providers.gradleProperty("dhunInstallerVersion")', gradle_desktop)
        self.assertNotIn('getOrElse("1.00.001")', gradle_desktop)


if __name__ == "__main__":
    unittest.main()
