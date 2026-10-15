"""Restore only missing, byte-identical v1.00.002 provenance assets.

GitHub retained the validated binaries/checksums but lost the four text
sidecars after the draft was published. The original draft's digests and sizes
were recorded before publication. Fail closed if any source, asset or digest
is inconsistent. Never replace an existing asset or move a tag.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path

from installer_version import installer_version
from stage_artifact import render_provenance

REPOSITORY = "99ggprooo00-code/DHUN"
TAG = "v1.00.002"
SOURCE_SHA = "1280e34ef6cb538b62d602c0f59b86b8e008923e"
RUN_ID = "38761062104"
# Original release-draft asset digests (recorded before publishing).
PROVENANCE = {
    "dhun-v1.00.002.apk.provenance.txt": ("f567e35969ec623976f3f46ae043f2bf97036d790442a3100d852add5881dd24", 383),
    "dhun-v1.00.002-arm64-v8a.apk.provenance.txt": ("cad46863c73959e24d1398b233d2ac895c5622b6ab489a581308a6547202a3e8", 393),
    "dhun-v1.00.002-armeabi-v7a.apk.provenance.txt": ("dec7c4c5021f5de63144b9b7e29296e3eaaf9369c58644a337b10725cc7fded1", 395),
    "dhun-v1.00.002.msi.provenance.txt": ("12e8fe22193fafd8e21efc54543555754eb68e93a75bfdf4ccb6e9a4726e3998", 458),
}
BINARY_NAMES = {name.removesuffix(".provenance.txt") for name in PROVENANCE}
EXPECTED_NAMES = BINARY_NAMES | {name + ".sha256" for name in BINARY_NAMES} | PROVENANCE.keys()
UPGRADE_CODE = "31ddb86b-9666-4071-b11c-45f16fa4682d"


def api(path: str) -> dict:
    return json.loads(subprocess.check_output(["gh", "api", f"repos/{REPOSITORY}/{path}"], text=True))


def assets_by_name(release: dict) -> dict:
    assets = release["assets"]
    names = [asset["name"] for asset in assets]
    if len(names) != len(set(names)) or not set(names) <= EXPECTED_NAMES:
        raise ValueError("unexpected or duplicate release assets")
    for asset in assets:
        if asset["size"] <= 0 or not re.fullmatch(r"sha256:[0-9a-f]{64}", asset.get("digest") or ""):
            raise ValueError(f"asset missing size/digest: {asset['name']}")
    return {asset["name"]: asset for asset in assets}


def reconstructed(release: dict, run: dict) -> dict[str, bytes]:
    if (release["tag_name"], release["draft"], release["prerelease"], release["target_commitish"]) != (TAG, False, True, SOURCE_SHA):
        raise ValueError("release identity changed")
    if (run["head_sha"], run["event"], run["conclusion"], run["run_number"], run["run_attempt"]) != (SOURCE_SHA, "push", "failure", 652, 1):
        # The publication check failed, but APK/MSI jobs succeeded and uploaded
        # all twelve assets into the draft. Never claim the full run was green.
        raise ValueError("source build identity changed")
    assets = assets_by_name(release)
    if not BINARY_NAMES | {n + ".sha256" for n in BINARY_NAMES} <= assets.keys():
        raise ValueError("binary or checksum asset missing")
    result: dict[str, bytes] = {}
    for name, (original_digest, original_size) in PROVENANCE.items():
        binary = name.removesuffix(".provenance.txt")
        data = assets[binary]
        msi = binary.endswith(".msi")
        metadata = {
            "schemaVersion": "1", "artifact": binary,
            "sha256": data["digest"].removeprefix("sha256:"), "bytes": data["size"],
            "sourceSha": SOURCE_SHA, "sourceRef": "refs/heads/main",
            "repository": REPOSITORY, "workflow": "test-release",
            "runId": RUN_ID, "runAttempt": "1",
            "runUrl": f"https://github.com/{REPOSITORY}/actions/runs/{RUN_ID}",
            "buildOnly": False,
            "installerVersion": installer_version(652, 1) if msi else None,
            "upgradeCode": UPGRADE_CODE if msi else None,
        }
        content = render_provenance(metadata).encode("ascii")
        if (hashlib.sha256(content).hexdigest(), len(content)) != (original_digest, original_size):
            raise ValueError(f"cannot reconstruct original {name} byte for byte")
        existing = assets.get(name)
        if existing and (existing["digest"], existing["size"]) != (f"sha256:{original_digest}", original_size):
            raise ValueError(f"existing {name} differs from original; refusing to overwrite")
        if not existing:
            result[name] = content
    return result


def main() -> None:
    if os.environ.get("GITHUB_REPOSITORY") != REPOSITORY or os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise ValueError("repair may run only on main in the expected repository")
    current = api("git/ref/heads/main")["object"]["sha"]
    if current != os.environ.get("GITHUB_SHA"):
        raise ValueError("refusing a superseded main build")
    if api(f"git/ref/tags/{TAG}")["object"]["sha"] != SOURCE_SHA:
        raise ValueError("immutable release tag does not match original build")
    release = api(f"releases/tags/{TAG}")
    run = api(f"actions/runs/{RUN_ID}")
    before = assets_by_name(release)
    missing = reconstructed(release, run)
    with tempfile.TemporaryDirectory() as temp:
        paths = []
        for name, content in missing.items():
            path = Path(temp) / name
            path.write_bytes(content)
            paths.append(str(path))
        if paths:
            subprocess.run(["gh", "release", "upload", TAG, "--repo", REPOSITORY, *paths], check=True)
    after_release = api(f"releases/tags/{TAG}")
    if (after_release["draft"], after_release["prerelease"], after_release["target_commitish"]) != (False, True, SOURCE_SHA):
        raise ValueError("release identity changed during repair")
    after = assets_by_name(after_release)
    if set(after) != EXPECTED_NAMES:
        raise ValueError("release still lacks the exact twelve assets")
    for name in before:
        if (after[name]["digest"], after[name]["size"]) != (before[name]["digest"], before[name]["size"]):
            raise ValueError(f"preexisting asset changed: {name}")
    if reconstructed(after_release, run):
        raise ValueError("one or more provenance sidecars remain missing")
    print(f"Confirmed {len(after)} exact, nonempty v1.00.002 assets; original binary/checksum digests and tag unchanged")


if __name__ == "__main__":
    main()
