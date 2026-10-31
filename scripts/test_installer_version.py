import unittest

from installer_version import BETA_RUN_OFFSET_ENV, effective_run_number, installer_version


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

    def test_beta_workflow_offset_never_resets_the_upgrade_sequence(self):
        env = {BETA_RUN_OFFSET_ENV: "867"}
        self.assertEqual(effective_run_number(1, env), 868)
        self.assertEqual(installer_version(effective_run_number(1, env), 1), "4.100.1")
        self.assertGreater(
            parts(installer_version(effective_run_number(1, env), 1)),
            parts(installer_version(867, 65_535)),
        )
        # If GitHub continues rather than resets the counter, the sequence jumps
        # forward safely instead of colliding with an earlier beta.
        self.assertGreater(effective_run_number(868, env), 868)
        self.assertEqual(effective_run_number(33, {}), 33)
        for run, offset in ((1, "-1"), (1, "bad"), (65_279, "867"), (0, "867")):
            with self.subTest(run=run, offset=offset):
                with self.assertRaises(ValueError):
                    effective_run_number(run, {BETA_RUN_OFFSET_ENV: offset})

    def test_invalid_or_exhausted_sequences_fail_instead_of_wrapping(self):
        for run, attempt in ((0, 1), (-1, 1), (65_280, 1), (33, 0), (33, -1), (33, 65_536)):
            with self.subTest(run=run, attempt=attempt):
                with self.assertRaises(ValueError):
                    installer_version(run, attempt)


if __name__ == "__main__":
    unittest.main()
