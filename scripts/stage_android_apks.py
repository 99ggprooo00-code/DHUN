"""Copy AGP ABI-split debug APKs to the three rolling-release names.

The test-release ``apk`` job's contract, and the only place that contract is
implemented: in the ``assembleDebug`` output directory, each of the globs
``*universal*``, ``*arm64-v8a*`` and ``*armeabi-v7a*`` must match exactly one
file. Those files are copied to:

- ``dhun-test.apk``              universal (stable install URL)
- ``dhun-test-arm64-v8a.apk``
- ``dhun-test-armeabi-v7a.apk``

The script prints each file's size and SHA-256, and whether each per-ABI APK
is byte-identical to the universal. Identity is reported, not enforced. DHUN
bundles no native libraries, so identical bytes are *expected*; a difference
is evidence to record (manifest split metadata, zip metadata) rather than a
packaging failure. Dropping the Gradle ABI split is a separate, explicit
decision once that report exists.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import sys
from dataclasses import dataclass
from fnmatch import fnmatch
from pathlib import Path

# These three globs are the apk-job contract. Each must match exactly one
# file in the assembleDebug output directory — not zero, not two.
GLOBS = (
    ("universal", "*universal*", "dhun-test.apk"),
    ("arm64-v8a", "*arm64-v8a*", "dhun-test-arm64-v8a.apk"),
    ("armeabi-v7a", "*armeabi-v7a*", "dhun-test-armeabi-v7a.apk"),
)


@dataclass(frozen=True)
class StagedApk:
    role: str
    source_name: str
    published_name: str
    path: Path
    size: int
    sha256: str
    identical_to_universal: bool


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _match_exactly_one(apk_dir: Path, pattern: str) -> Path:
    matches = sorted(
        path for path in apk_dir.iterdir()
        if path.is_file() and fnmatch(path.name, pattern)
    )
    if len(matches) == 1:
        return matches[0]
    listing = ", ".join(sorted(path.name for path in apk_dir.iterdir())) or "<empty>"
    found = ", ".join(path.name for path in matches) or "<none>"
    raise ValueError(
        f"glob {pattern!r} matched {len(matches)} file(s) ({found}); expected exactly one. "
        f"Directory {apk_dir} contains: {listing}"
    )


def stage_android_apks(apk_dir: Path, out_dir: Path) -> list[StagedApk]:
    if not apk_dir.is_dir():
        raise ValueError(f"APK output directory does not exist: {apk_dir}")
    out_dir.mkdir(parents=True, exist_ok=True)

    selected = [(role, _match_exactly_one(apk_dir, pattern), published)
                for role, pattern, published in GLOBS]
    universal_digest = _sha256(selected[0][1])
    staged: list[StagedApk] = []
    for role, source, published in selected:
        dest = out_dir / published
        # Copy bytes; do not hardlink back into the Gradle output tree.
        dest.write_bytes(source.read_bytes())
        digest = _sha256(dest)
        if digest != _sha256(source):
            raise ValueError(f"copy of {source.name} did not preserve bytes")
        staged.append(StagedApk(
            role=role,
            source_name=source.name,
            published_name=published,
            path=dest,
            size=dest.stat().st_size,
            sha256=digest,
            identical_to_universal=digest == universal_digest,
        ))
    return staged


def format_report(staged: list[StagedApk]) -> str:
    lines = []
    for item in staged:
        relation = (
            "reference"
            if item.role == "universal"
            else ("IDENTICAL to universal" if item.identical_to_universal else "DIFFERS from universal")
        )
        lines.append(
            f"{item.published_name} <= {item.source_name} "
            f"bytes={item.size} sha256={item.sha256} {relation}"
        )
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk_dir", type=Path)
    parser.add_argument("out_dir", type=Path)
    args = parser.parse_args(argv)
    try:
        staged = stage_android_apks(args.apk_dir, args.out_dir)
    except ValueError as error:
        print(f"::error title=APK split selection::{error}", file=sys.stderr)
        print(error, file=sys.stderr)
        return 1
    report = format_report(staged)
    print(report)
    # A notice is readable through the check-annotations API even when the
    # sandbox cannot download the Actions log archive.
    for line in report.splitlines():
        print(f"::notice title=APK split digest::{line}")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write("### Android APK splits\n\n```\n")
            stream.write(report)
            stream.write("\n```\n")
            stream.write(
                "Per-ABI equality with the universal is evidence, not a gate. "
                "DHUN bundles no native libraries, so identical bytes are expected. "
                "Playback and device acceptance remain unverified.\n"
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
