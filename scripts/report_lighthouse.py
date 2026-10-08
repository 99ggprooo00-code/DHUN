#!/usr/bin/env python3
"""Turn a Lighthouse report into CI annotations, and gate the two budgets that matter.

Actions log archives are not retrievable in this environment, so a score that
only reaches `stdout` cannot be read back. Everything this script reports is
emitted as a **check-run annotation** (`::notice::` / `::error::`), which the
API does return.

Gates (`.ai/WEBSITE_PLAN.md` Part A §10): accessibility >= 0.95 and
performance >= 0.90. SEO and best-practices are reported, not gated.

Usage: python3 scripts/report_lighthouse.py <route> <report.json>
"""

from __future__ import annotations

import json
import sys

CATEGORIES = ("performance", "accessibility", "best-practices", "seo")
GATES = {"accessibility": 0.95, "performance": 0.90}


def main(argv: list[str]) -> int:
    route, path = argv[1], argv[2]
    with open(path, encoding="utf-8") as handle:
        report = json.load(handle)
    categories = report.get("categories", {})
    scores = {key: categories.get(key, {}).get("score") for key in CATEGORIES}
    summary = " · ".join(
        f"{key}={value * 100:.0f}" for key, value in scores.items() if value is not None
    )
    print(f"::notice title=Lighthouse {route}::{summary}")
    print(f"{route}: {summary}")
    if not summary:
        print(f"::error title=Lighthouse {route}::the report contained no category scores")
        return 1

    # Name the audits that failed, so the failure is actionable from the
    # annotations alone.
    for key in ("accessibility", "performance"):
        category = categories.get(key, {})
        for ref in category.get("auditRefs", []):
            audit = report.get("audits", {}).get(ref["id"], {})
            if audit.get("score") == 0 and audit.get("scoreDisplayMode") not in (
                "notApplicable",
                "informative",
                "manual",
            ):
                detail = (audit.get("title") or "").strip()
                print(
                    f"::warning title=Lighthouse {route} {key}::"
                    f"{ref['id']} — {detail}"
                )

    failures = []
    for key, minimum in GATES.items():
        score = scores.get(key)
        if score is not None and score < minimum:
            failures.append(f"{key} {score * 100:.0f} < {minimum * 100:.0f}")
    if failures:
        print(f"::error title=Lighthouse {route}::" + "; ".join(failures))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
