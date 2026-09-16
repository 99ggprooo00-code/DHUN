"""Contract for the required CI workflow (no third-party YAML parser).

The Android unit suite used to run only because assembleDebug dependsOn
testDebugUnitTest. That coupling is easy to miss: a red test aborted a step
named "Android debug build", and if AGP ever stopped honouring the lazy
dependsOn the suite would silently stop running. ci.yml must name the
Gradle task as its own step, before assembleDebug.

The desktop suite had the mirror-image gap: the jvmTest source set existed
but no workflow step executed it — compileKotlinJvm only compiles jvmMain.
ci.yml must name :app-desktop:jvmTest as its own step, after the compile
step so each failure keeps an honest name.
"""

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CI = ROOT / ".github" / "workflows" / "ci.yml"


class CiWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(CI.exists(), "ci.yml is the required compile/test gate")
        self.text = CI.read_text()

    def test_android_unit_suite_is_a_named_step(self):
        self.assertIn("./gradlew :app-android:testDebugUnitTest", self.text)
        self.assertIn("Unit tests — Android (Robolectric)", self.text)

    def test_android_unit_suite_runs_before_assemble_debug(self):
        test_at = self.text.index("./gradlew :app-android:testDebugUnitTest")
        assemble_at = self.text.index("./gradlew :app-android:assembleDebug")
        self.assertLess(
            test_at,
            assemble_at,
            "the named Android suite must run before assembleDebug so a red "
            "test fails a step called Unit tests, not Android debug build",
        )

    def test_shared_jvm_tests_still_run(self):
        self.assertIn("./gradlew :shared:jvmTest", self.text)

    def test_desktop_unit_suite_is_a_named_step(self):
        self.assertIn("./gradlew :app-desktop:jvmTest", self.text)
        self.assertIn("Unit tests — Desktop (JVM)", self.text)

    def test_desktop_unit_suite_runs_after_desktop_compile(self):
        compile_at = self.text.index("./gradlew :app-desktop:compileKotlinJvm")
        test_at = self.text.index("./gradlew :app-desktop:jvmTest")
        self.assertLess(
            compile_at,
            test_at,
            "the desktop suite must run after the compile step so a compile "
            "break fails Desktop compiles, not Unit tests",
        )


if __name__ == "__main__":
    unittest.main()
