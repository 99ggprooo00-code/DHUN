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

The minSdk-24 floor has the same shape of gap, twice over. Android Lint
analyses only the module it runs in, so PR #134's `:app-android:lintDebug`
step never looked at `shared/src/androidMain`, and `shared`'s own lint block
had `abortOnError = false`, so a run there could not fail either. Both
Android modules must have a named NewApi step AND a lint block that scopes
to NewApi with abortOnError on — a step naming a task whose gate is off is
green theater, which is the exact failure mode this file exists to catch.

The same "exists but never executes" shape applies to the App Bundle. Stage
S6 requires a clean-installed AAB, yet the only job that builds one
(test-release.yml `aab`) is `workflow_dispatch`-gated, and the maintenance
agent's token gets HTTP 403 on dispatch. ci.yml must therefore name
`:app-android:bundleDebug` itself, or the bundle path stays unverified until
release day.
"""

import re
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

    def test_app_bundle_path_is_a_named_step(self):
        # S6 needs a clean-installed AAB, but test-release.yml's `aab` job is
        # workflow_dispatch-gated and the maintenance agent's token gets 403 on
        # dispatch — so without this step `:app-android:bundleDebug` has no
        # automated coverage at all, and it now builds alongside a
        # `splits { abi }` block (PR #131) it has never been exercised against.
        self.assertIn("./gradlew :app-android:bundleDebug", self.text)
        self.assertIn("Android App Bundle compiles (S6 AAB gate)", self.text)

    def test_app_bundle_step_runs_after_the_apk_build(self):
        assemble_at = self.text.index("./gradlew :app-android:assembleDebug")
        bundle_at = self.text.index("./gradlew :app-android:bundleDebug")
        self.assertLess(
            assemble_at,
            bundle_at,
            "the APK build must come first so a shared compile break fails "
            "'Android debug build' and not the bundle step",
        )

    def test_api24_newapi_gate_is_a_named_step_for_the_app_module(self):
        # minSdk 24 is otherwise proven only by compilation: assembleDebug
        # links an API-26+ call without complaint and it crashes on API 24-25.
        self.assertIn("./gradlew :app-android:lintDebug", self.text)
        self.assertIn("Android Lint — API 24 floor (NewApi)", self.text)

    def test_api24_newapi_gate_is_a_named_step_for_the_shared_module(self):
        # Android Lint analyses only the module it runs in, so
        # `:app-android:lintDebug` never looked at shared/src/androidMain —
        # the expect/actual Android code (connectivity, blur, download
        # transport, storage probes). Both Android modules need a NewApi run.
        self.assertIn("./gradlew :shared:lintDebug", self.text)
        self.assertIn(
            "Android Lint — shared androidMain API 24 floor (NewApi)",
            self.text,
        )

    def test_api24_newapi_gate_steps_keep_honest_names(self):
        # Two lint steps must not collapse into one ambiguous name, or a red
        # shared-module floor violation reads as an app-module failure.
        app_at = self.text.index("./gradlew :app-android:lintDebug")
        shared_at = self.text.index("./gradlew :shared:lintDebug")
        self.assertLess(
            app_at,
            shared_at,
            "the app-module NewApi step must come first so the two lint steps "
            "keep distinct, honest names in the run summary",
        )


class Api24LintConfigTest(unittest.TestCase):
    """The workflow step is worthless if the Gradle gate behind it is off.

    Both lint blocks must scope to NewApi (so unrelated warnings cannot redden
    the build) AND abort on error (so a violation actually fails the step).
    `shared` previously had `abortOnError = false`, i.e. a lint run there could
    never fail.
    """

    def lint_block(self, module):
        text = (ROOT / module / "build.gradle.kts").read_text()
        start = text.index("lint {")
        return text[start : text.index("}", start)]

    def test_app_android_lint_gates_newapi_and_aborts(self):
        block = self.lint_block("app-android")
        self.assertIn('"NewApi"', block)
        self.assertIn("abortOnError = true", block)

    def test_shared_lint_gates_newapi_and_aborts(self):
        block = self.lint_block("shared")
        self.assertIn('"NewApi"', block)
        self.assertIn("abortOnError = true", block)
        self.assertNotIn(
            "abortOnError = false",
            block,
            "a lint block that cannot fail is not a gate",
        )

    def test_both_android_modules_keep_the_same_min_sdk_floor(self):
        # The merged manifest enforces the HIGHER of the two, so a drift here
        # silently raises the real floor above 24 and the lint gate would then
        # be checking against the wrong API level.
        def min_sdk(module):
            text = (ROOT / module / "build.gradle.kts").read_text()
            return int(re.search(r"minSdk\s*=\s*(\d+)", text).group(1))

        self.assertEqual(24, min_sdk("app-android"))
        self.assertEqual(24, min_sdk("shared"))


if __name__ == "__main__":
    unittest.main()
