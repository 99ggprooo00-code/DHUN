#!/usr/bin/env python3
"""Prepare fixed-version release assets from the already-validated main build."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import sys
from pathlib import Path
from typing import Mapping

sys.path.insert(0, str(Path(__file__).resolve().parent))
from stage_artifact import PROVENANCE_SUFFIX, parse_provenance, stage_artifact  # noqa: E402

ASSETS = (
    ("dhun-test.apk", "dhun-v{version}.apk"),
    ("dhun-test-arm64-v8a.apk", "dhun-v{version}-arm64-v8a.apk"),
    ("dhun-test-armeabi-v7a.apk", "dhun-v{version}-armeabi-v7a.apk"),
    ("dhun-test.msi", "dhun-v{version}.msi"),
)
VERSION_PATTERN = re.compile(r"\d+\.\d+\.\d+\Z")


def _build_info(path: Path) -> dict:
    try:
        return parse_provenance(path.read_text(encoding="ascii"))
    except (OSError, UnicodeError, ValueError) as error:
        raise ValueError(f"cannot read staged provenance {path}: {error}") from error


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def stage_versioned_release(
    source_dir: Path,
    output_dir: Path,
    version: str,
    environment: Mapping[str, str],
) -> list[dict]:
    """Copy verified `test` binaries under versioned names with fresh provenance.

    The source job outputs must all name this repository and the exact commit
    being released. Checksums and provenance are regenerated for the renamed
    files, so the sidecars remain byte-accurate.
    """
    if not VERSION_PATTERN.fullmatch(version):
        raise ValueError("version must contain exactly three numeric components")
    if source_dir.resolve() == output_dir.resolve():
        raise ValueError("source and output directories must be different")
    source_sha = environment.get("GITHUB_SHA", "")
    repository = environment.get("GITHUB_REPOSITORY", "")
    if not re.fullmatch(r"[0-9a-fA-F]{40}", source_sha):
        raise ValueError("a full source commit SHA is required")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("a GitHub repository is required")

    output_dir.mkdir(parents=True, exist_ok=True)
    staged: list[dict] = []
    for source_name, destination_template in ASSETS:
        source = source_dir / source_name
        if not source.is_file() or source.stat().st_size == 0:
            raise ValueError(f"source asset is missing or empty: {source}")
        source_info = _build_info(source.with_name(source.name + PROVENANCE_SUFFIX))
        if source_info.get("artifact") != source_name:
            raise ValueError(f"provenance artifact mismatch for {source_name}")
        if source_info.get("sourceSha") != source_sha:
            raise ValueError(f"{source_name} was not built from this release commit")
        if source_info.get("repository") != repository:
            raise ValueError(f"{source_name} came from a different repository")
        if source_info.get("buildOnly") is not False:
            raise ValueError(f"{source_name} is not marked as a publishable build")

        source_hash = _sha256(source)
        if source_info.get("sha256") != source_hash or source_info.get("bytes") != source.stat().st_size:
            raise ValueError(f"{source_name} does not match its provenance checksum/size")
        sidecar = source.with_name(source.name + ".sha256")
        try:
            checksum_fields = sidecar.read_text(encoding="ascii").split()
        except (OSError, UnicodeError) as error:
            raise ValueError(f"cannot read checksum sidecar {sidecar}: {error}") from error
        if len(checksum_fields) != 2 or checksum_fields[0].lower() != source_hash or checksum_fields[1] != source_name:
            raise ValueError(f"{source_name} does not match its checksum sidecar")

        destination = output_dir / destination_template.format(version=version)
        shutil.copy2(source, destination)
        installer_version = source_info.get("installerVersion") if source_name.endswith(".msi") else None
        upgrade_code = source_info.get("upgradeCode") if source_name.endswith(".msi") else None
        metadata = stage_artifact(
            destination,
            environment,
            build_only=False,
            installer_version=installer_version,
            upgrade_code=upgrade_code,
        )
        staged.append(metadata)
    return staged


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_dir", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--version", required=True)
    args = parser.parse_args()
    try:
        metadata = stage_versioned_release(args.source_dir, args.output_dir, args.version, os.environ)
    except ValueError as error:
        parser.error(str(error))
    for item in metadata:
        print(
            f"{item['artifact']} bytes={item['bytes']} sha256={item['sha256']} "
            f"installerVersion={item['installerVersion'] or '-'}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
