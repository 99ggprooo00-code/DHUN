"""Contract for creating fixed-version assets from a successful test build."""

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from stage_versioned_release import ASSETS, stage_versioned_release


class StageVersionedReleaseTest(unittest.TestCase):
    SHA = "a" * 40
    REPOSITORY = "99ggprooo00-code/DHUN"
    ENVIRONMENT = {
        "GITHUB_SHA": SHA,
        "GITHUB_REPOSITORY": REPOSITORY,
        "GITHUB_RUN_ID": "12345",
        "GITHUB_RUN_ATTEMPT": "1",
        "GITHUB_REF": "refs/heads/main",
        "GITHUB_WORKFLOW": "test-release",
    }

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.output = self.root / "output"
        self.source.mkdir()
        for source_name, _destination in ASSETS:
            binary = self.source / source_name
            contents = (source_name + "\n").encode()
            binary.write_bytes(contents)
            digest = hashlib.sha256(contents).hexdigest()
            (self.source / (source_name + ".sha256")).write_text(
                f"{digest}  {source_name}\n", encoding="ascii"
            )
            (self.source / (source_name + ".build-info.json")).write_text(
                json.dumps(
                    {
                        "artifact": source_name,
                        "sha256": digest,
                        "bytes": len(contents),
                        "sourceSha": self.SHA,
                        "repository": self.REPOSITORY,
                        "buildOnly": False,
                        "installerVersion": "2.193.1" if source_name.endswith(".msi") else None,
                        "upgradeCode": "31ddb86b-9666-4071-b11c-45f16fa4682d"
                        if source_name.endswith(".msi")
                        else None,
                    }
                ),
                encoding="utf-8",
            )

    def tearDown(self):
        self.temp.cleanup()

    def test_renames_each_platform_asset_and_stages_exact_checksums(self):
        metadata = stage_versioned_release(
            self.source, self.output, "1.00.001", self.ENVIRONMENT
        )
        expected_names = [
            destination.format(version="1.00.001")
            for _source, destination in ASSETS
        ]
        self.assertEqual(expected_names, [item["artifact"] for item in metadata])
        for item in metadata:
            binary = self.output / item["artifact"]
            sidecar = binary.with_name(binary.name + ".sha256")
            info = binary.with_name(binary.name + ".build-info.json")
            expected_hash = hashlib.sha256(binary.read_bytes()).hexdigest()
            self.assertEqual(expected_hash, item["sha256"])
            self.assertEqual(
                f"{expected_hash}  {binary.name}\n", sidecar.read_text(encoding="ascii")
            )
            staged = json.loads(info.read_text(encoding="utf-8"))
            self.assertEqual(self.SHA, staged["sourceSha"])
            self.assertIn("dhun-v1.00.001", binary.name)
            self.assertFalse(staged["buildOnly"])
        msi = next(item for item in metadata if item["artifact"].endswith(".msi"))
        self.assertEqual("2.193.1", msi["installerVersion"])
        self.assertIsNotNone(msi["upgradeCode"])

    def test_rejects_assets_built_from_another_commit(self):
        info = self.source / "dhun-test.apk.build-info.json"
        data = json.loads(info.read_text(encoding="utf-8"))
        data["sourceSha"] = "b" * 40
        info.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "not built from this release commit"):
            stage_versioned_release(self.source, self.output, "1.00.001", self.ENVIRONMENT)

    def test_rejects_artifacts_from_another_repository(self):
        info = self.source / "dhun-test.apk.build-info.json"
        data = json.loads(info.read_text(encoding="utf-8"))
        data["repository"] = "other/repo"
        info.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "different repository"):
            stage_versioned_release(self.source, self.output, "1.00.001", self.ENVIRONMENT)

    def test_rejects_build_only_artifacts(self):
        info = self.source / "dhun-test.apk.build-info.json"
        data = json.loads(info.read_text(encoding="utf-8"))
        data["buildOnly"] = True
        info.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "not marked as a publishable build"):
            stage_versioned_release(self.source, self.output, "1.00.001", self.ENVIRONMENT)

    def test_rejects_source_bytes_that_do_not_match_provenance(self):
        binary = self.source / "dhun-test.apk"
        binary.write_bytes(b"changed after provenance was generated")
        with self.assertRaisesRegex(ValueError, "does not match its provenance checksum/size"):
            stage_versioned_release(self.source, self.output, "1.00.001", self.ENVIRONMENT)

    def test_rejects_a_mismatched_source_checksum_sidecar(self):
        sidecar = self.source / "dhun-test.apk.sha256"
        sidecar.write_text("0" * 64 + "  dhun-test.apk\n", encoding="ascii")
        with self.assertRaisesRegex(ValueError, "does not match its checksum sidecar"):
            stage_versioned_release(self.source, self.output, "1.00.001", self.ENVIRONMENT)

    def test_rejects_invalid_version_or_same_directory(self):
        with self.assertRaisesRegex(ValueError, "three numeric components"):
            stage_versioned_release(self.source, self.output, "1.0", self.ENVIRONMENT)
        with self.assertRaisesRegex(ValueError, "must be different"):
            stage_versioned_release(self.source, self.source, "1.00.001", self.ENVIRONMENT)


if __name__ == "__main__":
    unittest.main()
