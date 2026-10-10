"""Stage a test binary with SHA256 and non-secret, revision-bound build provenance.

Two sidecars are written next to the binary:

- ``<name>.sha256``          the standard ``sha256sum`` line (``<hex>  <name>``)
- ``<name>.provenance.txt``  flat ``key=value`` lines (no JSON). Only the fields
  listed in ``PROVENANCE_FIELDS`` are written, so CI credentials and other
  environment values can never leak into a release asset.

Release assets must not add ``.json`` files. The published v1.00.001 release
still carries older ``.build-info.json`` sidecars; those are historical and are
not read by this module.
"""

import argparse
import hashlib
import os
import re
from pathlib import Path

PROVENANCE_SUFFIX = ".provenance.txt"
SCHEMA_VERSION = "1"
# Ordered, whitelisted provenance fields. Nothing else is ever written.
PROVENANCE_FIELDS = (
    "schemaVersion",
    "artifact",
    "sha256",
    "bytes",
    "sourceSha",
    "sourceRef",
    "repository",
    "workflow",
    "runId",
    "runAttempt",
    "runUrl",
    "buildOnly",
    "installerVersion",
    "upgradeCode",
)


def provenance_path(binary: Path) -> Path:
    return binary.with_name(binary.name + PROVENANCE_SUFFIX)


def render_provenance(metadata: dict) -> str:
    lines = []
    for field in PROVENANCE_FIELDS:
        value = metadata.get(field)
        if value is None:
            continue  # absent means "not applicable" (e.g. no MSI version on an APK)
        text = str(value).lower() if isinstance(value, bool) else str(value)
        if "\n" in text or "\r" in text:
            raise ValueError(f"provenance field {field} must be single-line")
        lines.append(f"{field}={text}")
    return "\n".join(lines) + "\n"


def parse_provenance(text: str) -> dict:
    """Parse ``key=value`` provenance into the same typed shape as stage_artifact()."""
    values: dict[str, str] = {}
    for number, line in enumerate(text.splitlines(), start=1):
        if not line.strip():
            continue
        key, separator, value = line.partition("=")
        if not separator or not key or key not in PROVENANCE_FIELDS:
            raise ValueError(f"unknown provenance line {number}")
        if key in values:
            raise ValueError(f"duplicate provenance field {key}")
        values[key] = value
    if values.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("unsupported provenance schema version")
    parsed: dict = {}
    for key, value in values.items():
        if key == "bytes":
            if not value.isdecimal():
                raise ValueError("provenance bytes must be a decimal count")
            parsed[key] = int(value)
        elif key == "buildOnly":
            if value not in ("true", "false"):
                raise ValueError("provenance buildOnly must be true or false")
            parsed[key] = value == "true"
        else:
            parsed[key] = value
    for key in PROVENANCE_FIELDS:
        parsed.setdefault(key, None)
    return parsed


def stage_artifact(binary: Path, environment, *, build_only: bool, installer_version=None, upgrade_code=None):
    if not binary.is_file() or binary.stat().st_size == 0:
        raise ValueError("artifact is missing or empty")
    if not re.fullmatch(r"[A-Za-z0-9._-]+", binary.name):
        raise ValueError("artifact filename must be a simple basename")
    sha = environment.get("GITHUB_SHA", "")
    repository = environment.get("GITHUB_REPOSITORY", "")
    run_id = environment.get("GITHUB_RUN_ID", "")
    if not re.fullmatch(r"[0-9a-fA-F]{40}", sha):
        raise ValueError("a full source commit SHA is required")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) or not run_id.isdecimal():
        raise ValueError("a GitHub repository and numeric run ID are required")
    if installer_version is not None and not re.fullmatch(r"\d+\.\d+\.\d+", installer_version):
        raise ValueError("installer version must have three numeric components")
    if upgrade_code is not None and not re.fullmatch(r"[0-9a-fA-F-]{36}", upgrade_code):
        raise ValueError("invalid MSI upgrade code")

    digest = hashlib.sha256()
    with binary.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    metadata = {
        "schemaVersion": SCHEMA_VERSION,
        "artifact": binary.name,
        "sha256": digest.hexdigest(),
        "bytes": binary.stat().st_size,
        "sourceSha": sha,
        "sourceRef": environment.get("GITHUB_REF", ""),
        "repository": repository,
        "workflow": environment.get("GITHUB_WORKFLOW", ""),
        "runId": run_id,
        "runAttempt": environment.get("GITHUB_RUN_ATTEMPT", "1"),
        "runUrl": f"https://github.com/{repository}/actions/runs/{run_id}",
        "buildOnly": build_only,
        "installerVersion": installer_version,
        "upgradeCode": upgrade_code,
    }
    # Deliberately whitelist fields. Never dump CI's environment or credentials.
    binary.with_name(binary.name + ".sha256").write_text(
        f"{metadata['sha256']}  {binary.name}\n", encoding="ascii",
    )
    provenance_path(binary).write_text(render_provenance(metadata), encoding="ascii")
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("--build-only", choices=("true", "false"), required=True)
    parser.add_argument("--installer-version")
    parser.add_argument("--upgrade-code")
    args = parser.parse_args()
    try:
        metadata = stage_artifact(
            args.binary, os.environ, build_only=args.build_only == "true",
            installer_version=args.installer_version, upgrade_code=args.upgrade_code,
        )
    except ValueError as error:
        parser.error(str(error))
    # A notice is readable through the check-annotations API even when this
    # sandbox cannot download the Actions artifact/log archive itself.
    message = (
        f"{metadata['artifact']} source={metadata['sourceSha']} "
        f"version={metadata['installerVersion'] or 'APK'} bytes={metadata['bytes']} "
        f"sha256={metadata['sha256']} buildOnly={str(metadata['buildOnly']).lower()}"
    )
    print(f"::notice title=Test artifact provenance::{message}")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(f"### {metadata['artifact']}\n\n```text\n{render_provenance(metadata)}```\n")
            stream.write("Build/checksum evidence only; playback and user-machine acceptance remain unverified.\n")


if __name__ == "__main__":
    main()
