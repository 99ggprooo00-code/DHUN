"""Check installed Android package identity from aapt, not just Gradle inputs."""
import argparse
import re
import subprocess
from pathlib import Path


def verify_identity(badging: str, expected_name: str, expected_code: int) -> None:
    packages = [line for line in badging.splitlines() if line.startswith("package:")]
    if len(packages) != 1:
        raise ValueError("expected exactly one Android package identity")
    fields = re.findall(r"(\w+)='([^']*)'", packages[0])
    values = dict(fields)
    if len(fields) != len(values):
        raise ValueError("duplicate Android identity fields")
    if (values.get("name"), values.get("versionName"), values.get("versionCode")) != (
        "dev.dhun.android", expected_name, str(expected_code)
    ):
        raise ValueError("packaged Android identity does not match this test build")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aapt")
    parser.add_argument("directory", type=Path)
    parser.add_argument("name")
    parser.add_argument("code", type=int)
    args = parser.parse_args()
    for filename in ("dhun-test.apk", "dhun-test-arm64-v8a.apk", "dhun-test-armeabi-v7a.apk"):
        apk = args.directory / filename
        badging = subprocess.check_output([args.aapt, "dump", "badging", str(apk)], text=True)
        verify_identity(badging, args.name, args.code)
        print(f"Verified {filename}: versionName={args.name} versionCode={args.code}")
