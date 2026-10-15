"""Fail-closed checks for the one-time repair of the published release."""

import copy
import hashlib
import unittest

from restore_public_provenance import PROVENANCE, REPOSITORY, RUN_ID, SOURCE_SHA, TAG, reconstructed


class RestorePublicProvenanceTest(unittest.TestCase):
    def setUp(self):
        # These are the four original public binary digests and sizes, not test
        # build digests. The provenance hashes in PROVENANCE were recorded from
        # the draft before publication; this fixture checks byte identity.
        self.binaries = {
            "dhun-v1.00.002.apk": ("66a9e15a624599512b6d52be32614b2b99780a9c4e09c64bca69af141db2a816", 31745616),
            "dhun-v1.00.002-arm64-v8a.apk": ("de058d1987ab6b00888832ad4bbc19d48fa15aa1dcb500b441d2a114a59447df", 31695543),
            "dhun-v1.00.002-armeabi-v7a.apk": ("a18c4e751708a27162fb5c1b293cba81083f327f09a4b28e9ece1ed85b60fe29", 31692701),
            "dhun-v1.00.002.msi": ("0d2cae92a668d48baeebbec7da5efc601de7d02df4f52976c6d9a0ce63af3980", 127422464),
        }
        self.release = {
            "tag_name": TAG, "draft": False, "prerelease": True,
            "target_commitish": SOURCE_SHA,
            "assets": [
                {"name": name, "size": size, "digest": f"sha256:{digest}"}
                for name, (digest, size) in self.binaries.items()
            ] + [
                {"name": name + ".sha256", "size": 80, "digest": "sha256:" + "a" * 64}
                for name in self.binaries
            ],
        }
        self.run = {
            "head_sha": SOURCE_SHA, "event": "push", "conclusion": "failure",
            "run_number": 652, "run_attempt": 1,
        }

    def test_reconstructs_exact_original_bytes_and_then_is_idempotent(self):
        files = reconstructed(self.release, self.run)
        self.assertEqual(set(files), set(PROVENANCE))
        for name, content in files.items():
            digest, size = PROVENANCE[name]
            self.assertEqual((hashlib.sha256(content).hexdigest(), len(content)), (digest, size))
            self.assertIn(f"sourceSha={SOURCE_SHA}".encode(), content)
            self.assertIn(f"repository={REPOSITORY}".encode(), content)
            self.assertIn(f"runId={RUN_ID}".encode(), content)
            self.release["assets"].append({"name": name, "size": size, "digest": "sha256:" + digest})
        self.assertEqual({}, reconstructed(self.release, self.run))

    def test_never_overwrites_wrong_sidecar_or_unexpected_asset(self):
        self.release["assets"].append({"name": next(iter(PROVENANCE)), "size": 383, "digest": "sha256:" + "a" * 64})
        with self.assertRaisesRegex(ValueError, "differs from original"):
            reconstructed(self.release, self.run)
        self.release["assets"].pop()
        self.release["assets"].append({"name": "unexpected.exe", "size": 1, "digest": "sha256:" + "b" * 64})
        with self.assertRaisesRegex(ValueError, "unexpected"):
            reconstructed(self.release, self.run)

    def test_rejects_changed_release_source_run_or_binary(self):
        altered = copy.deepcopy(self.release)
        altered["target_commitish"] = "a" * 40
        with self.assertRaisesRegex(ValueError, "identity"):
            reconstructed(altered, self.run)
        altered = copy.deepcopy(self.release)
        altered["assets"][0]["digest"] = "sha256:" + "b" * 64
        with self.assertRaisesRegex(ValueError, "byte for byte"):
            reconstructed(altered, self.run)
        altered = copy.deepcopy(self.release)
        altered["assets"] = [a for a in altered["assets"] if a["name"] != "dhun-v1.00.002.apk"]
        with self.assertRaisesRegex(ValueError, "binary or checksum"):
            reconstructed(altered, self.run)
        with self.assertRaisesRegex(ValueError, "source build"):
            reconstructed(self.release, {**self.run, "run_attempt": 2})


if __name__ == "__main__":
    unittest.main()
