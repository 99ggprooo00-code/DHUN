"""Check every packaged Android APK from aapt/apksigner, not just Gradle inputs."""
import argparse
import re
import subprocess
import sys
from pathlib import Path

EXPECTED_CERT_SHA256 = "d8e57ec69f40956d34adff137872db9fec3b817e149e2d0c33af5924142468c3"
STAGED_APKS = (
    "dhun-test.apk",
    "dhun-test-arm64-v8a.apk",
    "dhun-test-armeabi-v7a.apk",
)
RAW_APKS = (
    "app-android-universal-debug.apk",
    "app-android-arm64-v8a-debug.apk",
    "app-android-armeabi-v7a-debug.apk",
)


def select_apks(directory: Path) -> list[Path]:
    """Require one complete raw or staged universal/ARM64/ARM32 package set."""
    present = {path.name for path in directory.glob("*.apk") if path.is_file()}
    for expected in (RAW_APKS, STAGED_APKS):
        if present == set(expected):
            return [directory / name for name in expected]
    raise ValueError(
        "expected exactly the universal, arm64-v8a and armeabi-v7a APK set; "
        f"found {sorted(present) or '<none>'}"
    )


def verify_identity(badging: str, expected_name: str, expected_code: int) -> None:
    packages = [line for line in badging.splitlines() if line.startswith("package:")]
    if len(packages) != 1:
        raise ValueError("expected exactly one Android package identity")
    fields = re.findall(r"(\w+)='([^']*)'", packages[0])
    values = dict(fields)
    if len(fields) != len(values):
        raise ValueError("duplicate Android identity fields")
    actual = (values.get("name"), values.get("versionName"), values.get("versionCode"))
    expected = ("dev.dhun.android", expected_name, str(expected_code))
    if actual != expected:
        raise ValueError(f"packaged Android identity {actual!r} != expected {expected!r}")


def verify_signature(apksigner_output: str) -> None:
    digests = [
        digest.lower()
        for digest in re.findall(
            r"(?im)^\s*(?:Signer #\d+\s+|V[1-4] Signer:\s*)certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$",
            apksigner_output,
        )
    ]
    if digests != [EXPECTED_CERT_SHA256]:
        output = " ".join(apksigner_output.split())[:900]
        raise ValueError(
            "APK does not have exactly the expected DHUN test-build signer; "
            f"found {digests or '<none>'}; apksigner output: {output or '<empty>'}"
        )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aapt")
    parser.add_argument("directory", type=Path)
    parser.add_argument("name")
    parser.add_argument("code", type=int)
    parser.add_argument("--apksigner", help="also verify the stable test certificate on every APK")
    args = parser.parse_args(argv)
    try:
        apks = select_apks(args.directory)
        print(f"::notice title=Android package set::{', '.join(apk.name for apk in apks)}")
        for apk in apks:
            badging = subprocess.check_output([args.aapt, "dump", "badging", str(apk)], text=True)
            verify_identity(badging, args.name, args.code)
            signer = ""
            if args.apksigner:
                signature = subprocess.check_output(
                    [args.apksigner, "verify", "--print-certs", str(apk)],
                    text=True,
                    stderr=subprocess.STDOUT,
                )
                verify_signature(signature)
                signer = f" signer={EXPECTED_CERT_SHA256}"
            message = (
                f"Verified {apk.name}: versionName={args.name} "
                f"versionCode={args.code}{signer}"
            )
            print(message)
            print(f"::notice title=Android package verified::{message}")
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        detail = " ".join(str(error).split())
        output = getattr(error, "output", None)
        if output:
            detail += " | output: " + " ".join(output.split())
        # Workflow-command message escaping; keep the annotation bounded and one-line.
        detail = detail[:1800].replace("%", "%25")
        print(f"::error title=Android package verification::{detail}", file=sys.stderr)
        print(detail, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
