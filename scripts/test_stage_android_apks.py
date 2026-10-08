"""Contract for the three-APK staging step the test-release apk job runs."""

import hashlib
import io
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from tempfile import TemporaryDirectory

from stage_android_apks import GLOBS, format_report, main, stage_android_apks


def _write(directory: Path, name: str, payload: bytes) -> None:
    (directory / name).write_bytes(payload)


class StageAndroidApksTest(unittest.TestCase):
    def test_globs_are_the_apk_job_contract(self):
        self.assertEqual(
            [pattern for _, pattern, _ in GLOBS],
            ["*universal*", "*arm64-v8a*", "*armeabi-v7a*"],
        )
        self.assertEqual(
            [published for _, _, published in GLOBS],
            ["dhun-test.apk", "dhun-test-arm64-v8a.apk", "dhun-test-armeabi-v7a.apk"],
        )

    def test_exactly_one_match_per_glob_is_copied_and_identical_bytes_are_reported(self):
        payload = b"same-bytes-no-native-libs"
        with TemporaryDirectory() as directory:
            src = Path(directory) / "apk"
            out = Path(directory) / "out"
            src.mkdir()
            _write(src, "app-android-universal-debug.apk", payload)
            _write(src, "app-android-arm64-v8a-debug.apk", payload)
            _write(src, "app-android-armeabi-v7a-debug.apk", payload)
            _write(src, "output-metadata.json", b"{\"not-an-apk\":true}")

            staged = stage_android_apks(src, out)

            self.assertEqual([item.published_name for item in staged], [
                "dhun-test.apk", "dhun-test-arm64-v8a.apk", "dhun-test-armeabi-v7a.apk",
            ])
            expected = hashlib.sha256(payload).hexdigest()
            for item in staged:
                self.assertEqual(item.path.read_bytes(), payload)
                self.assertEqual(item.sha256, expected)
                self.assertTrue(item.identical_to_universal)
                self.assertEqual(item.size, len(payload))
            report = format_report(staged)
            self.assertIn("IDENTICAL to universal", report)
            self.assertIn("dhun-test.apk <= app-android-universal-debug.apk", report)
            self.assertNotIn("DIFFERS from universal", report)

    def test_a_byte_difference_is_reported_and_does_not_fail_staging(self):
        with TemporaryDirectory() as directory:
            src = Path(directory) / "apk"
            out = Path(directory) / "out"
            src.mkdir()
            _write(src, "app-universal-debug.apk", b"universal")
            _write(src, "app-arm64-v8a-debug.apk", b"arm64-different")
            _write(src, "app-armeabi-v7a-debug.apk", b"universal")

            staged = stage_android_apks(src, out)

            by_role = {item.role: item for item in staged}
            self.assertTrue(by_role["universal"].identical_to_universal)
            self.assertFalse(by_role["arm64-v8a"].identical_to_universal)
            self.assertTrue(by_role["armeabi-v7a"].identical_to_universal)
            report = format_report(staged)
            self.assertIn("dhun-test-arm64-v8a.apk <= app-arm64-v8a-debug.apk", report)
            self.assertIn("DIFFERS from universal", report)

    def test_zero_or_two_matches_names_the_glob_and_the_directory(self):
        with TemporaryDirectory() as directory:
            src = Path(directory) / "apk"
            src.mkdir()
            _write(src, "app-android-debug.apk", b"no-split-name")
            with self.assertRaises(ValueError) as missing:
                stage_android_apks(src, Path(directory) / "out")
            self.assertIn("*universal*", str(missing.exception))
            self.assertIn("app-android-debug.apk", str(missing.exception))

            _write(src, "app-android-universal-debug.apk", b"one")
            _write(src, "also-universal.apk", b"two")
            _write(src, "app-android-arm64-v8a-debug.apk", b"one")
            _write(src, "app-android-armeabi-v7a-debug.apk", b"one")
            with self.assertRaises(ValueError) as extra:
                stage_android_apks(src, Path(directory) / "out2")
            self.assertIn("*universal*", str(extra.exception))
            self.assertIn("matched 2", str(extra.exception))

    def test_cli_failure_is_a_nonzero_exit_not_a_traceback_to_the_job(self):
        with TemporaryDirectory() as directory:
            src = Path(directory) / "apk"
            src.mkdir()
            err = io.StringIO()
            with redirect_stderr(err), redirect_stdout(io.StringIO()):
                code = main([str(src), str(Path(directory) / "out")])
            self.assertEqual(code, 1)
            self.assertIn("*universal*", err.getvalue())
            self.assertIn("::error title=APK split selection::", err.getvalue())


if __name__ == "__main__":
    unittest.main()
