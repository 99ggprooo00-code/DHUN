#!/usr/bin/env python3
"""Turn a Lighthouse report into CI annotations, and gate the two budgets that matter.

Actions log archives are not retrievable in this environment, so a score that
only reaches `stdout` cannot be read back. Everything this script reports is
emitted as a **check-run annotation** (`::notice::` / `::error::`), which the
API does return.

Gates (`.ai/WEBSITE_PLAN.md` Part A §10): accessibility >= 0.95 and
performance >= 0.90. SEO and best-practices are reported, not gated.

A score alone is not evidence: "96 → 94 → 78" cannot be diagnosed from a number,
and the report JSON lives in an artifact this environment cannot retrieve. So the
metrics the score is computed from (FCP, LCP, TBT, CLS, Speed Index, total byte
weight and request count) and the largest measured savings are annotated too —
readable from the API, comparable run to run.

When several reports are given for one route, every run's score is annotated and
the **gate uses the median**. A single Lighthouse sample on a shared runner
swings by double digits (this repository has seen `/` score 96, 94 and 78 for
pages that differ by 64 bytes), so one sample is not a measurement of the page.
Median-of-three is not a looser gate: the same threshold applies to a number that
is actually about the site.

Usage: python3 scripts/report_lighthouse.py <route> <report.json> [<report2.json> …]
"""

from __future__ import annotations

import json
import sys

CATEGORIES = ("performance", "accessibility", "best-practices", "seo")
GATES = {"accessibility": 0.95, "performance": 0.90}


def load_scores(path: str) -> dict[str, float | None]:
    with open(path, encoding="utf-8") as handle:
        report = json.load(handle)
    categories = report.get("categories", {})
    return {key: categories.get(key, {}).get("score") for key in CATEGORIES}


def _urls_in(node: object, limit: int = 6) -> list[str]:
    """Every URL mentioned anywhere in a Lighthouse details structure.

    The insight's item shape is not part of Lighthouse's stable public API, and
    a reporter that pattern-matched one version's keys would silently report
    nothing when it changed — the same defect as the count it replaces. So this
    walks whatever it is given and collects `url`/`request`/`resourceUrl` string
    fields, in document order, de-duplicated, capped so one annotation stays
    readable.
    """
    found: list[str] = []
    stack: list[object] = [node]
    while stack:
        current = stack.pop(0)
        if isinstance(current, dict):
            for key, value in current.items():
                if key in ("url", "request", "resourceUrl") and isinstance(value, str) and value:
                    if value not in found:
                        found.append(value)
                else:
                    stack.append(value)
        elif isinstance(current, list):
            stack.extend(current)
        if len(found) >= limit:
            break
    return found[:limit]


def median(values: list[float]) -> float:
    ordered = sorted(values)
    middle = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[middle]
    return (ordered[middle - 1] + ordered[middle]) / 2


def main(argv: list[str]) -> int:
    route, paths = argv[1], argv[2:]
    if not paths:
        print("usage: report_lighthouse.py <route> <report.json> [<report2.json> …]")
        return 2
    runs = [load_scores(path) for path in paths]
    reports = []
    for path in paths:
        with open(path, encoding="utf-8") as handle:
            reports.append(json.load(handle))

    # The run whose performance score is the median: its audits and metrics are
    # the ones annotated in full, so a failure is described by a real run.
    perf = [run.get("performance") for run in runs if run.get("performance") is not None]
    if perf:
        target = median(perf)
        index = min(range(len(runs)), key=lambda i: abs((runs[i].get("performance") or 0) - target))
    else:
        index = 0
    report = reports[index]
    scores = runs[index]

    summary = " · ".join(
        f"{key}={value * 100:.0f}" for key, value in scores.items() if value is not None
    )
    print(f"{route}: {summary}")
    combined = [summary]
    if len(runs) > 1:
        per_run = " · ".join(
            f"run {n + 1}: {runs[n].get('performance') * 100:.0f}"
            for n in range(len(runs))
            if runs[n].get("performance") is not None
        )
        combined.append(f"samples {per_run} (gate uses the median)")
    if not summary:
        print(f"::error title=Lighthouse {route}::the report contained no category scores")
        return 1

    # The numbers behind the score: what actually moved between runs.
    metrics = {
        "FCP": ("first-contentful-paint", "ms"),
        "LCP": ("largest-contentful-paint", "ms"),
        "TBT": ("total-blocking-time", "ms"),
        "CLS": ("cumulative-layout-shift", ""),
        "SI": ("speed-index", "ms"),
    }
    measured = []
    for label, (audit_id, unit) in metrics.items():
        audit = report.get("audits", {}).get(audit_id, {})
        value = audit.get("numericValue")
        if value is None:
            continue
        measured.append(f"{label}={value:.0f}{unit}" if unit else f"{label}={value:.3f}")
    weight = report.get("audits", {}).get("total-byte-weight", {}).get("numericValue")
    requests = report.get("audits", {}).get("network-requests", {}).get("details", {}).get("items", [])
    if weight is not None:
        measured.append(f"bytes={weight / 1000:.1f}kB")
    if requests:
        measured.append(f"requests={len(requests)}")
    if measured:
        combined.append(" · ".join(measured))
        print(f"{route} metrics: " + " · ".join(measured))

    # A request *count* is not evidence: `requests=2` was on every route for a
    # whole session because one extra subresource was never named. So the
    # subresources are listed by URL (with their transfer size), and "none" is
    # stated explicitly rather than left to be inferred from a number.
    extras = []
    for item in requests:
        url = item.get("url") or ""
        if not url or item.get("resourceType") == "document":
            continue
        size = item.get("transferSize") or item.get("resourceSize") or 0
        extras.append(f"{url.rsplit('/', 1)[-1] or url} ({size} B, {item.get('resourceType') or '?'})")
    combined.append("subresources: " + (", ".join(extras) if extras else "none — the document only"))
    print(f"{route} subresources: " + (", ".join(extras) if extras else "none"))

    # The largest measured savings, so the next run can confirm an improvement
    # instead of asserting one.
    savings = []
    for audit_id, audit in report.get("audits", {}).items():
        overall = audit.get("details", {}).get("overallSavingsMs")
        if overall and overall > 0:
            savings.append((overall, audit_id, (audit.get("title") or "").strip()))
    for overall, audit_id, title in sorted(savings, reverse=True)[:4]:
        print(f"{route} opportunity: {audit_id} — {title} (about {overall:.0f} ms)")

    # Insights that carry no score but say whether the page still blocks on
    # requests; these are the ones Tier A set out to remove.
    for audit_id in ("render-blocking-resources", "network-dependency-tree-insight", "unused-css-rules"):
        audit = report.get("audits", {}).get(audit_id, {})
        if not audit:
            continue
        items = audit.get("details", {}).get("items", [])
        if audit_id == "network-dependency-tree-insight" and items:
            # "3 item(s)" cannot be acted on. The insight's own items carry the
            # URLs (in a shape that has moved between Lighthouse versions), so
            # every `url`/`request` field found anywhere in the details is
            # collected rather than guessed at, and the whole structure still
            # goes to stdout for a human with log access.
            combined.append(
                "network-dependency-tree-insight: "
                + ("; ".join(_urls_in(items)) or f"{len(items)} item(s), no url field")
            )
            print(f"{route} dependency tree detail: {json.dumps(items)[:1800]}")
        else:
            state = "none" if not items else f"{len(items)} item(s)"
            combined.append(f"{audit_id}: {audit.get('displayValue') or state}")

    # Name the audits that failed, so the failure is actionable from the
    # annotations alone.
    categories = report.get("categories", {})
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

    print(f"::notice title=Lighthouse {route}::" + " || ".join(combined))

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
    try:
        raise SystemExit(main(sys.argv))
    except SystemExit:
        raise
    except Exception as error:  # noqa: BLE001 — a silent crash is the defect
        # This script has already failed once with an undefined name: the
        # notices printed, the process exited 1, and nothing said why. Any
        # exception is now an annotation.
        route = sys.argv[1] if len(sys.argv) > 1 else "?"
        print(f"::error title=Lighthouse {route}::report_lighthouse.py crashed: {error!r}")
        raise SystemExit(1)
