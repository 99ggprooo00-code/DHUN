import hashlib
import tempfile
import unittest
from pathlib import Path

from stage_artifact import PROVENANCE_FIELDS, parse_provenance, render_provenance, stage_artifact


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
            sidecar = binary.with_name(binary.name + ".provenance.txt")
            text = sidecar.read_text(encoding="ascii")
            self.assertEqual(parse_provenance(text), metadata)
            self.assertNotIn("DO-NOT-COPY-THIS", text)
            self.assertNotIn("GH_TOKEN", text)
            # Release assets must not add JSON: no .json sidecar may be produced.
            self.assertEqual(sorted(p.name for p in Path(directory).iterdir()), sorted([
                "dhun-test.msi", "dhun-test.msi.sha256", "dhun-test.msi.provenance.txt",
            ]))

    def test_provenance_text_is_flat_key_value_with_whitelisted_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = Path(directory) / "dhun-test.apk"
            binary.write_bytes(b"apk fixture")
            stage_artifact(binary, self.environment(), build_only=True)
            lines = binary.with_name(binary.name + ".provenance.txt").read_text(encoding="ascii").splitlines()
            keys = [line.partition("=")[0] for line in lines]
            self.assertTrue(set(keys) <= set(PROVENANCE_FIELDS))
            self.assertNotIn("installerVersion", keys)  # not applicable to an APK
            self.assertIn("buildOnly=true", lines)

    def test_provenance_parser_rejects_tampered_or_unknown_input(self):
        good = "schemaVersion=1\nartifact=a.apk\nbytes=3\nbuildOnly=false\n"
        self.assertEqual(parse_provenance(good)["bytes"], 3)
        for bad in (
            "artifact=a.apk\n",                               # no schema version
            "schemaVersion=1\nbogus=1\n",                     # unknown field
            "schemaVersion=1\nbytes=3\nbytes=4\n",           # duplicate field
            "schemaVersion=1\nbytes=-3\n",                    # non-decimal size
            "schemaVersion=1\nbuildOnly=yes\n",               # non-boolean flag
        ):
            with self.subTest(bad=bad):
                with self.assertRaises(ValueError):
                    parse_provenance(bad)

    def test_provenance_rejects_multiline_values(self):
        with self.assertRaises(ValueError):
            render_provenance({"schemaVersion": "1", "artifact": "a\nb"})

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
