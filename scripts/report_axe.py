#!/usr/bin/env python3
"""Turn an axe-core JSON report into CI annotations.

Evidence, not a gate: axe-cli needs a browser and a matching driver, and when
that is unavailable the workflow reports the environment problem instead of a
number. Violations found are emitted one annotation each, so the run is
actionable from the check-run annotations alone (log archives are not
retrievable in this environment).

Usage: python3 scripts/report_axe.py <route> <axe.json>
"""

from __future__ import annotations

import json
import sys


def main(argv: list[str]) -> int:
    route, path = argv[1], argv[2]
    with open(path, encoding="utf-8") as handle:
        report = json.load(handle)

    # axe-cli writes a list of reports (one per page) or a single report.
    reports = report if isinstance(report, list) else [report]
    violations: list[tuple[str, str, int]] = []
    passes = 0
    for entry in reports:
        data = entry.get("results", entry)
        for violation in data.get("violations", []):
            violations.append(
                (
                    violation.get("id", "unknown"),
                    violation.get("impact", "unknown"),
                    len(violation.get("nodes", [])),
                )
            )
        passes += len(data.get("passes", []))

    if not violations:
        print(
            f"::notice title=axe-core {route}::no violations ({passes} rules passed)"
        )
        return 0

    print(
        f"::error title=axe-core {route}::{len(violations)} violation(s) across "
        f"{len(reports)} page report(s); {passes} rules passed"
    )
    for rule, impact, nodes in violations:
        print(f"::warning title=axe-core {route}::{rule} ({impact}) — {nodes} node(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
