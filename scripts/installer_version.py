"""Internal MSI version sequence shared by rolling and fixed versioned releases.

Windows compares only three numeric ProductVersion fields. Every workflow run
must outrank every attempt of the previous run; every rerun must also upgrade.
This sequence is NOT the application's semantic version. A future stable
packaging workflow must continue this sequence (or supply a higher version),
not reset ProductVersion to 0.1.0.
"""

import argparse
import os

BETA_RUN_OFFSET_ENV = "DHUN_BETA_RUN_OFFSET"


def effective_run_number(run_number: int, environ=None) -> int:
    """Continue a renamed workflow's sequence without changing local defaults.

    beta-release.yml supplies the last old-workflow run (867) through the
    environment. Build APK and ordinary callers leave it unset. Exhaustion or
    malformed input fails closed rather than producing a Windows downgrade.
    """
    if not 1 <= run_number <= 65_279:
        raise ValueError("raw workflow run number must be between 1 and 65279")
    env = os.environ if environ is None else environ
    try:
        offset = int(env.get(BETA_RUN_OFFSET_ENV, "0"))
    except (TypeError, ValueError) as error:
        raise ValueError(f"{BETA_RUN_OFFSET_ENV} must be a non-negative integer") from error
    if offset < 0:
        raise ValueError(f"{BETA_RUN_OFFSET_ENV} must be a non-negative integer")
    effective = run_number + offset
    if not 1 <= effective <= 65_279:
        raise ValueError("effective workflow run number must be between 1 and 65279")
    return effective


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
        print(installer_version(effective_run_number(args.run_number), args.attempt))
    except ValueError as error:
        parser.error(str(error))
