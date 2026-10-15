import unittest
from pathlib import Path
from test_version import test_version


class TestVersionTest(unittest.TestCase):
    def test_increases_across_runs_and_reruns(self):
        for run in (1, 255, 256, 65278):
            first = test_version(run, 1)
            retry = test_version(run, 999)
            following = test_version(run + 1, 1)
            self.assertLess(first[1], retry[1])
            self.assertLess(retry[1], following[1])
            self.assertNotEqual(first[0], retry[0])
            self.assertNotEqual(first[0], following[0])

    def test_readable_name_and_android_bounds(self):
        self.assertEqual(("1.00.42-test.2", 43002), test_version(42, 2))
        self.assertGreater(test_version(1, 1)[1], 6)
        self.assertLess(test_version(65279, 999)[1], 2100000000)

    def test_invalid_counters_fail_without_wrapping(self):
        for run, attempt in ((0, 1), (-1, 1), (65280, 1), (1, 0), (1, 1000)):
            with self.assertRaises(ValueError):
                test_version(run, attempt)


class WorkflowTestVersionTest(unittest.TestCase):
    def test_both_android_jobs_apply_both_properties_on_test_channel(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/test-release.yml").read_text()
        for job, next_job in (("apk", "msi"), ("aab", "publish_test")):
            block = workflow.split(f"\n  {job}:\n")[1].split(f"\n  {next_job}:\n")[0]
            self.assertIn('python scripts/test_version.py "$GITHUB_RUN_NUMBER" "$GITHUB_RUN_ATTEMPT"', block)
            self.assertIn('-PdhunTestVersionName=$TEST_VERSION_NAME', block)
            self.assertIn('-PdhunTestVersionCode=$TEST_VERSION_CODE', block)
            self.assertIn('if [ "$DHUN_RELEASE_CHANNEL" = test ]; then', block)
        self.assertIn("      - 'scripts/**'", workflow)
        self.assertIn("      - '.github/workflows/test-release.yml'", workflow)
        self.assertIn('--title "DHUN test $version (private draft)"', workflow)
