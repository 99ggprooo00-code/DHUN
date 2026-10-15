import unittest
from check_android_identity import verify_identity


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
            with self.assertRaises(ValueError):
                verify_identity(badging, "1.00.42-test.2", 43002)
