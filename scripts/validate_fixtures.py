"""Strictly validate repository JSON test inputs, without a JVM or third-party packages.

This checks fixture syntax only. It does not execute Kotlin parsers/tests.
"""

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURE_ROOTS = (
    ROOT / "tests/fixtures",
    ROOT / "shared/src/jvmTest/resources/fixtures",
)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate object key: {key!r}")
        result[key] = value
    return result


def reject_constant(value):
    raise ValueError(f"non-JSON numeric constant: {value}")


def validate_file(path: Path):
    with path.open(encoding="utf-8") as source:
        return json.load(source, object_pairs_hook=unique_object, parse_constant=reject_constant)


def fixture_files(roots):
    return sorted({path for root in roots for path in (root.rglob("*.json") if root.is_dir() else [root])})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", type=Path, help="JSON files or fixture directories")
    args = parser.parse_args()
    files = fixture_files(args.paths or FIXTURE_ROOTS)
    if not files:
        parser.error("no JSON fixtures found")
    failures = []
    for path in files:
        try:
            validate_file(path)
        except (OSError, ValueError) as error:
            failures.append(f"{path}: {error}")
    if failures:
        for failure in failures:
            print(failure)
        return 1
    print(f"PASS: {len(files)} JSON fixture files (syntax only; Kotlin tests not executed)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
