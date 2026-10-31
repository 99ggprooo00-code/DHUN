import unittest
from pathlib import Path
from beta_version import (
    MAX_ANDROID_BETA_CODE,
    MIN_ANDROID_BETA_CODE,
    beta_version,
)

NOW = 1_800_000_000


class BetaVersionTest(unittest.TestCase):
    def test_allocation_time_orders_different_workflows_and_reruns(self):
        rolling = beta_version(822, 1, NOW)
        # Build APK has an independent, lower run number. Its later allocation
        # must still be an upgrade over the rolling-beta package.
        pr_build = beta_version(659, 1, NOW + 1)
        retry = beta_version(659, 2, NOW + 2)
        self.assertLess(rolling[1], pr_build[1])
        self.assertLess(pr_build[1], retry[1])
        self.assertNotEqual(pr_build[0], retry[0])

    def test_same_second_is_a_reinstall_not_a_downgrade(self):
        first = beta_version(100, 1, NOW)
        second = beta_version(101, 1, NOW)
        self.assertEqual(first[1], second[1])
        self.assertNotEqual(first[0], second[0])

    def test_readable_name_and_android_bounds(self):
        self.assertEqual(("1.00.42-beta.2", NOW), beta_version(42, 2, NOW))
        self.assertGreater(beta_version(1, 1, NOW)[1], 65_280_999)  # legacy counter maximum
        self.assertEqual(MIN_ANDROID_BETA_CODE, beta_version(1, 1, MIN_ANDROID_BETA_CODE)[1])
        self.assertEqual(MAX_ANDROID_BETA_CODE, beta_version(1, 1, MAX_ANDROID_BETA_CODE)[1])

    def test_invalid_counters_fail_without_wrapping(self):
        for run, attempt in ((0, 1), (-1, 1), (65280, 1), (1, 0), (1, 1000)):
            with self.subTest(run=run, attempt=attempt):
                with self.assertRaises(ValueError):
                    beta_version(run, attempt, NOW)

    def test_bad_or_exhausted_clock_fails_instead_of_downgrading(self):
        for allocated_at in (0, MIN_ANDROID_BETA_CODE - 1, MAX_ANDROID_BETA_CODE + 1):
            with self.subTest(allocated_at=allocated_at):
                with self.assertRaises(ValueError):
                    beta_version(1, 1, allocated_at)


class WorkflowBetaVersionTest(unittest.TestCase):
    def test_both_android_jobs_apply_both_properties_on_beta_channel(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/beta-release.yml").read_text()
        for job, next_job in (("apk", "msi"), ("aab", "publish_beta")):
            block = workflow.split(f"\n  {job}:\n")[1].split(f"\n  {next_job}:\n")[0]
            self.assertIn('python scripts/beta_version.py "$GITHUB_RUN_NUMBER" "$GITHUB_RUN_ATTEMPT"', block)
            self.assertIn('-PdhunBetaVersionName=$BETA_VERSION_NAME', block)
            self.assertIn('-PdhunBetaVersionCode=$BETA_VERSION_CODE', block)
            self.assertIn('if [ "$DHUN_RELEASE_CHANNEL" = beta ]; then', block)
        self.assertIn("      - 'scripts/**'", workflow)
        self.assertIn("      - '.github/workflows/beta-release.yml'", workflow)
        self.assertIn("DHUN_BETA_RUN_OFFSET: '867'", workflow)
        root = Path(__file__).resolve().parents[1]
        self.assertIn("beta_version(effective_run_number(args.run_number)", (root / "scripts/beta_version.py").read_text())
        self.assertIn("installer_version(effective_run_number(args.run_number)", (root / "scripts/installer_version.py").read_text())
        self.assertEqual(2, workflow.count('--apksigner "$apksigner"'))
        self.assertIn('--title "DHUN beta"', workflow)
        self.assertIn('--target "$GITHUB_SHA"', workflow)
        self.assertIn('--title "DHUN v1.00.002 — public pre-release"', workflow)

    def test_fast_apk_workflow_uses_the_same_beta_identity(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/build-apk.yml").read_text()
        self.assertIn('python scripts/beta_version.py "$GITHUB_RUN_NUMBER" "$GITHUB_RUN_ATTEMPT"', workflow)
        self.assertNotIn("DHUN_BETA_RUN_OFFSET", workflow)
        self.assertIn('"-PdhunReleaseChannel=development"', workflow)
        self.assertNotIn('"-PdhunReleaseChannel=test"', workflow)
        self.assertIn('"-PdhunBetaVersionName=$BETA_VERSION_NAME"', workflow)
        self.assertIn('"-PdhunBetaVersionCode=$BETA_VERSION_CODE"', workflow)
        self.assertIn('python scripts/check_android_identity.py "$aapt"', workflow)
        self.assertIn('--apksigner "$apksigner"', workflow)
        gradle = (Path(__file__).resolve().parents[1] / "app-android/build.gradle.kts").read_text()
        self.assertIn('require(dhunReleaseChannel != "public")', gradle)


if __name__ == "__main__":
    unittest.main()
