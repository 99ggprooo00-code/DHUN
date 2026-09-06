import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from stage_artifact import stage_artifact


class StageArtifactTest(unittest.TestCase):
    def environment(self):
        return {
            "GITHUB_SHA": "a" * 40,
            "GITHUB_REPOSITORY": "owner/project",
            "GITHUB_REF": "refs/heads/arena/example",
            "GITHUB_RUN_ID": "1234",
            "GITHUB_RUN_ATTEMPT": "2",
            "GITHUB_WORKFLOW": "test-release",
            "GH_TOKEN": "DO-NOT-COPY-THIS",
        }

    def test_checksum_and_manifest_describe_the_exact_binary(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / "dhun-test.msi"
            binary.write_bytes(b"test binary\0\xff")
            metadata = stage_artifact(
                binary, self.environment(), build_only=True, installer_version="1.33.2",
                upgrade_code="31ddb86b-9666-4071-b11c-45f16fa4682d",
            )
            expected = hashlib.sha256(binary.read_bytes()).hexdigest()
            self.assertEqual(metadata["sha256"], expected)
            self.assertEqual(metadata["bytes"], len(binary.read_bytes()))
            self.assertEqual(metadata["sourceSha"], "a" * 40)
            self.assertEqual(metadata["installerVersion"], "1.33.2")
            self.assertTrue(metadata["buildOnly"])
            self.assertEqual(binary.with_name(binary.name + ".sha256").read_text(), f"{expected}  dhun-test.msi\n")
            text = binary.with_name(binary.name + ".build-info.json").read_text()
            self.assertEqual(json.loads(text), metadata)
            self.assertNotIn("DO-NOT-COPY-THIS", text)
            self.assertNotIn("GH_TOKEN", text)

    def test_apk_does_not_claim_an_msi_version(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / "dhun-test.apk"
            binary.write_bytes(b"apk fixture")
            info = stage_artifact(binary, self.environment(), build_only=False)
            self.assertIsNone(info["installerVersion"])
            self.assertIsNone(info["upgradeCode"])
            self.assertFalse(info["buildOnly"])

    def test_missing_or_empty_binary_is_an_error(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / "missing.msi"
            with self.assertRaises(ValueError):
                stage_artifact(binary, self.environment(), build_only=True)
            binary.touch()
            with self.assertRaises(ValueError):
                stage_artifact(binary, self.environment(), build_only=True)

    def test_provenance_cannot_silently_use_an_unknown_source(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / "dhun-test.msi"
            binary.write_bytes(b"fixture")
            for field, value in (("GITHUB_SHA", "short"), ("GITHUB_RUN_ID", ""), ("GITHUB_REPOSITORY", "")):
                with self.subTest(field=field):
                    environment = self.environment()
                    environment[field] = value
                    with self.assertRaises(ValueError):
                        stage_artifact(binary, environment, build_only=True)


if __name__ == "__main__":
    unittest.main()
