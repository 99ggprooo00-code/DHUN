import tempfile
import unittest
from pathlib import Path

from check_android_identity import (
    EXPECTED_CERT_SHA256,
    RAW_APKS,
    STAGED_APKS,
    select_apks,
    verify_identity,
    verify_signature,
)


class AndroidIdentityTest(unittest.TestCase):
    badge = "package: name='dev.dhun.android' versionCode='43002' versionName='1.00.42-test.2' platformBuildVersionName='15'"

    def test_matches_actual_packaged_identity(self):
        verify_identity(self.badge + "\napplication-label:'DHUN'", "1.00.42-test.2", 43002)

    def test_missing_duplicate_or_wrong_identity_fails_closed(self):
        for badging in ("", self.badge + "\n" + self.badge,
                        self.badge.replace("43002", "6"),
                        self.badge.replace("1.00.42-test.2", "1.00.001"),
                        self.badge.replace("dev.dhun.android", "another.app"),
                        self.badge + " versionCode='43002'"):
            with self.subTest(badging=badging):
                with self.assertRaises(ValueError):
                    verify_identity(badging, "1.00.42-test.2", 43002)

    def test_raw_and_staged_sets_include_every_variant(self):
        for names in (RAW_APKS, STAGED_APKS):
            with self.subTest(names=names), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp)
                for name in names:
                    (directory / name).write_bytes(b"apk")
                self.assertEqual([directory / name for name in names], select_apks(directory))

    def test_missing_or_extra_apk_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            for name in RAW_APKS:
                (directory / name).write_bytes(b"apk")
            with self.assertRaises(ValueError):
                select_apks(directory / "missing")
            (directory / RAW_APKS[0]).unlink()
            with self.assertRaises(ValueError):
                select_apks(directory)
            (directory / RAW_APKS[0]).write_bytes(b"apk")
            (directory / "unexpected.apk").write_bytes(b"apk")
            with self.assertRaises(ValueError):
                select_apks(directory)

    def test_expected_test_certificate_is_required(self):
        output = (
            "Signer #1 certificate DN: CN=DHUN Test Build, O=DHUN\n"
            f"Signer #1 certificate SHA-256 digest: {EXPECTED_CERT_SHA256}\n"
        )
        verify_signature(output)
        for bad in (
            "",
            output.replace(EXPECTED_CERT_SHA256, "0" * 64),
            output + output.replace("Signer #1", "Signer #2"),
        ):
            with self.subTest(bad=bad):
                with self.assertRaises(ValueError):
                    verify_signature(bad)


if __name__ == "__main__":
    unittest.main()
