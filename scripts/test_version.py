"""Monotonic Android test identity, separate from the fixed public release.

Every APK-producing workflow uses allocation time for versionCode. A workflow-local
run counter cannot order a Build APK artifact against a test-release artifact:
their run numbers are independent. Unix seconds give all test APKs one nondecreasing
sequence; a same-second build may reuse a code, which Android accepts as a reinstall.
The readable versionName still records the workflow run and attempt. MSI keeps its
own run/attempt ProductVersion sequence.
"""
import argparse
import time
from installer_version import installer_version

# Android/Gradle reject larger codes. Fail on exhaustion or a bad runner clock;
# never wrap and silently turn an update into a downgrade.
MIN_ANDROID_TEST_CODE = 1_700_000_000
MAX_ANDROID_TEST_CODE = 2_100_000_000


def test_version(run_number: int, attempt: int, allocated_at: int | None = None) -> tuple[str, int]:
    installer_version(run_number, attempt)  # Validate the readable run/attempt identity.
    if attempt > 999:
        raise ValueError("test release attempts must be between 1 and 999")
    if allocated_at is None:
        allocated_at = int(time.time())
    if not MIN_ANDROID_TEST_CODE <= allocated_at <= MAX_ANDROID_TEST_CODE:
        raise ValueError(
            "Android test allocation time must produce a versionCode between "
            f"{MIN_ANDROID_TEST_CODE} and {MAX_ANDROID_TEST_CODE}"
        )
    return f"1.00.{run_number}-test.{attempt}", allocated_at


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_number", type=int)
    parser.add_argument("attempt", type=int)
    parser.add_argument(
        "allocated_at", type=int, nargs="?",
        help="optional Unix timestamp, for deterministic checks; defaults to the current time",
    )
    parser.add_argument("--field", choices=("name", "code", "outputs"), default="outputs")
    args = parser.parse_args()
    try:
        name, code = test_version(args.run_number, args.attempt, args.allocated_at)
    except ValueError as error:
        parser.error(str(error))
    if args.field == "outputs":
        print(f"version_name={name}\nversion_code={code}")
    else:
        print(name if args.field == "name" else code)
