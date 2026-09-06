import unittest

from installer_version import installer_version


def parts(version):
    return tuple(map(int, version.split(".")))


class InstallerVersionTest(unittest.TestCase):
    def test_upgrades_the_previously_published_1_0_5(self):
        self.assertGreater(parts(installer_version(1, 1)), (1, 0, 5))
        self.assertEqual(installer_version(33, 1), "1.33.1")

    def test_reruns_have_a_new_product_version(self):
        self.assertGreater(parts(installer_version(33, 2)), parts(installer_version(33, 1)))

    def test_every_new_run_outranks_all_attempts_of_the_previous_one(self):
        for run in (1, 32, 254, 255, 256, 511, 65_278):
            with self.subTest(run=run):
                self.assertGreater(parts(installer_version(run + 1, 1)), parts(installer_version(run, 65_535)))

    def test_numeric_rollover_stays_within_msi_limits(self):
        self.assertEqual(installer_version(255, 1), "1.255.1")
        self.assertEqual(installer_version(256, 1), "2.0.1")
        self.assertEqual(installer_version(65_279, 65_535), "255.255.65535")

    def test_invalid_or_exhausted_sequences_fail_instead_of_wrapping(self):
        for run, attempt in ((0, 1), (-1, 1), (65_280, 1), (33, 0), (33, -1), (33, 65_536)):
            with self.subTest(run=run, attempt=attempt):
                with self.assertRaises(ValueError):
                    installer_version(run, attempt)


if __name__ == "__main__":
    unittest.main()
