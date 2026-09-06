"""Internal MSI version for the rolling release; public tag/asset names stay `test`.

Windows compares only three numeric ProductVersion fields. Every workflow run
must outrank every attempt of the previous run; every rerun must also upgrade.
This sequence is NOT the application's semantic version. A future stable
packaging workflow must continue this sequence (or supply a higher version),
not reset ProductVersion to 0.1.0.
"""

import argparse


def installer_version(run_number: int, attempt: int) -> str:
    # MSI bounds: major/minor <= 255, build <= 65535. Major must be > 0
    # for Compose's native packagers. Fail on exhaustion; never wrap/downgrade.
    if not 1 <= run_number <= 65_279:
        raise ValueError("run number must be between 1 and 65279")
    if not 1 <= attempt <= 65_535:
        raise ValueError("run attempt must be between 1 and 65535")
    return f"{1 + run_number // 256}.{run_number % 256}.{attempt}"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_number", type=int)
    parser.add_argument("attempt", type=int)
    args = parser.parse_args()
    try:
        print(installer_version(args.run_number, args.attempt))
    except ValueError as error:
        parser.error(str(error))
