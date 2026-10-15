"""Monotonic Android test identity, separate from the fixed public release.

One workflow counter owns both Android and MSI test upgrades. Reserve 1000
Android codes per run; fail rather than wrap or downgrade on exhaustion.
"""
import argparse
from installer_version import installer_version


def test_version(run_number: int, attempt: int) -> tuple[str, int]:
    installer_version(run_number, attempt)  # Share the MSI counter's bounds.
    if attempt > 999:
        raise ValueError("test release attempts must be between 1 and 999")
    return f"1.00.{run_number}-test.{attempt}", 1000 + run_number * 1000 + attempt


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_number", type=int)
    parser.add_argument("attempt", type=int)
    parser.add_argument("--field", choices=("name", "code", "outputs"), default="outputs")
    args = parser.parse_args()
    try:
        name, code = test_version(args.run_number, args.attempt)
    except ValueError as error:
        parser.error(str(error))
    if args.field == "outputs":
        print(f"version_name={name}\nversion_code={code}")
    else:
        print(name if args.field == "name" else code)
