"""The web workstream's documents, checked rather than trusted.

Why this exists: this repository has, at different times, described the browser
build as an "engineering preview", published a features page saying the browser
client does not exist, and carried a README promising playback that no one had
ever heard. Every one of those was a document nobody was checking, and each was
found by a person reading the deployed page — the most expensive way to find it.

So the documents in `docs/web-player/` are gated the way the site's copy is:

* **Structure** — every phase file exists and carries the sections a phase file
  is supposed to have, so a "phase report" cannot be an empty promise.
* **Evidence** — a phase marked complete, or a box ticked in the definition of
  done, must name what proved it. A tick with nothing behind it is the exact
  failure mode this file is written against.
* **Consistency** — while Phase 2 (real playback) is not complete, the tracker
  must still say the project is not verified for release. The two facts cannot be
  allowed to drift apart, because the optimistic half is the one people read.
* **Claims** — the site's own honesty rules (`website_claims`) are applied to
  these documents too: no promise of browser playback, no quality figure, no
  store channel, no denial of the surfaces that do ship.

Usage:
    python3 scripts/web_player_docs.py [docs/web-player]
Exit status is 0 when every rule passes, 1 otherwise. The rules are exercised
against deliberately broken copies by `scripts/test_web_player_docs.py`, so a
rule that stops matching is a red test rather than a silent gap.
"""

from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import website_claims as claims  # noqa: E402

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_DIR = REPO_ROOT / "docs" / "web-player"

PHASE_FILES = tuple(f"PHASE-{index:02d}-{slug}.md" for index, slug in (
    (0, "AUDIT"),
    (1, "SOURCE-RESEARCH"),
    (2, "PLAYBACK-POC"),
    (3, "ARCHITECTURE"),
    (4, "SEARCH-AND-CATALOGUE"),
    (5, "PLAYBACK-ENGINE"),
    (6, "INTERFACE"),
    (7, "LIBRARY-AND-PERSISTENCE"),
    (8, "TEST-PLAN"),
    (9, "DEPLOYMENT"),
    (10, "MAINTENANCE-ROADMAP"),
))

SUPPORT_FILES = (
    "README.md",
    "IMPLEMENTATION-STATUS.md",
    "KNOWN-LIMITATIONS.md",
    "DEVELOPMENT-GUIDE.md",
)

REQUIRED_FILES = SUPPORT_FILES + PHASE_FILES

PHASE_SECTIONS = ("## Objective", "## Acceptance criteria", "## Completion decision")

TRACKER_HEADING = "## Phase tracker"
STATUS_MARKS = ("✅", "⚠️", "🔄", "🔲", "❌", "🚫")

# A phase may only be marked with one of these two when its evidence cell names
# the phase file that carries the evidence.
COMPLETE_MARKS = ("✅", "⚠️")

RELEASE_LINE = "NOT VERIFIED FOR RELEASE"
PHASE_TWO_ROW = re.compile(r"^\|\s*2\s*\|", re.M)

# Evidence on a ticked box has to be more than a word.
MIN_EVIDENCE_CHARS = 8


def _table_rows(text: str) -> list[dict[str, str]]:
    """The phase table's body rows, keyed by the table's own column names."""
    start = text.find(TRACKER_HEADING)
    if start < 0:
        return []
    header: list[str] = []
    rows: list[dict[str, str]] = []
    for line in text[start:].splitlines():
        if not line.startswith("|"):
            if rows:
                break
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if not header:
            header = cells
            continue
        if set("".join(cells)) <= set("-: "):
            continue
        if len(cells) == len(header):
            rows.append(dict(zip(header, cells)))
    return rows


def structure_problems(root: pathlib.Path) -> list[str]:
    """Every required document exists, and every phase file has its sections."""
    problems: list[str] = []
    for name in REQUIRED_FILES:
        path = root / name
        if not path.is_file():
            problems.append(f"missing document: docs/web-player/{name}")
            continue
        if name not in PHASE_FILES:
            continue
        text = path.read_text(encoding="utf-8")
        if not re.search(r"^> Status: ", text, re.M):
            problems.append(f"{name}: no '> Status: …' line — a phase report has to state its status")
        for section in PHASE_SECTIONS:
            if section not in text:
                problems.append(f"{name}: missing section '{section}'")
        if "**Go / No-Go:" not in text:
            problems.append(f"{name}: no explicit Go/No-Go decision")
    return problems


def tracker_problems(root: pathlib.Path) -> list[str]:
    """The tracker's table and checklist carry evidence, and stay consistent."""
    path = root / "IMPLEMENTATION-STATUS.md"
    if not path.is_file():
        return ["missing document: docs/web-player/IMPLEMENTATION-STATUS.md"]
    text = path.read_text(encoding="utf-8")
    problems: list[str] = []

    rows = _table_rows(text)
    if len(rows) < 11:
        problems.append(
            "IMPLEMENTATION-STATUS.md: the phase tracker does not list all eleven phases "
            f"(found {len(rows)} rows)"
        )
    wanted = ("Phase", "Status", "Evidence", "Remaining work")
    if rows and not all(key in rows[0] for key in wanted):
        return problems + [
            "IMPLEMENTATION-STATUS.md: the phase tracker is missing a column "
            f"(needs {', '.join(wanted)})"
        ]
    seen: set[str] = set()
    for row in rows:
        number, status = row["Phase"], row["Status"]
        evidence, remaining = row["Evidence"], row["Remaining work"]
        seen.add(number)
        marks = [mark for mark in STATUS_MARKS if mark in status]
        if len(marks) != 1:
            problems.append(
                f"IMPLEMENTATION-STATUS.md: phase {number}'s status {status!r} carries "
                f"{len(marks)} legend marks — exactly one is required"
            )
            continue
        if marks[0] in COMPLETE_MARKS:
            if len(evidence) < MIN_EVIDENCE_CHARS or evidence.startswith("—"):
                problems.append(
                    f"IMPLEMENTATION-STATUS.md: phase {number} is marked {marks[0]} with no "
                    "evidence beside it — a completed phase has to name what proved it"
                )
            elif not re.search(r"PHASE-\d\d", evidence):
                problems.append(
                    f"IMPLEMENTATION-STATUS.md: phase {number}'s evidence does not point at its "
                    "phase file — the row is a claim, the file is the evidence"
                )
            if marks[0] == "⚠️" and (len(remaining) < MIN_EVIDENCE_CHARS or remaining.startswith("—")):
                problems.append(
                    f"IMPLEMENTATION-STATUS.md: phase {number} is 'complete with exceptions' and "
                    "does not say what the exception is"
                )
    missing = [str(index) for index in range(11) if str(index) not in seen]
    if missing:
        problems.append(f"IMPLEMENTATION-STATUS.md: the phase tracker has no row for phase(s) {', '.join(missing)}")

    for line in text.splitlines():
        stripped = line.strip()
        if not stripped.startswith("- [x]"):
            continue
        body = stripped[len("- [x]"):].strip()
        if " — " not in body:
            problems.append(
                f"IMPLEMENTATION-STATUS.md: ticked box with no evidence: {body[:70]!r} — "
                "write what proved it, or untick it"
            )
            continue
        evidence = body.split(" — ", 1)[1].strip()
        if len(evidence) < MIN_EVIDENCE_CHARS:
            problems.append(
                f"IMPLEMENTATION-STATUS.md: ticked box with an empty evidence note: {body[:70]!r}"
            )

    # Consistency: the optimistic claim may not outrun the release gate.
    phase_two = [row for row in rows if row["Phase"] == "2"]
    if phase_two:
        gate_open = not any(mark in phase_two[0]["Status"] for mark in COMPLETE_MARKS)
        if gate_open and RELEASE_LINE not in text:
            problems.append(
                "IMPLEMENTATION-STATUS.md: phase 2 is not complete, so the file must still say "
                f"'{RELEASE_LINE}' — the status line and the gate have drifted apart"
            )
        if not gate_open and "Playback proof" not in text:
            problems.append(
                "IMPLEMENTATION-STATUS.md: phase 2 is marked complete with no mention of what "
                "the playback proof was"
            )
    return problems


def claim_problems(root: pathlib.Path) -> list[str]:
    """No document here may carry a claim the repository cannot back.

    The site's rules, applied to prose. Code blocks and HTML comments are
    stripped first: a rule engine quoted in a document is documentation, not a
    claim, and comments are notes to a future editor.
    """
    problems: list[str] = []
    for path in sorted(root.glob("*.md")):
        text = path.read_text(encoding="utf-8")
        text = re.sub(r"```.*?```", " ", text, flags=re.S)
        text = re.sub(r"<!--.*?-->", " ", text, flags=re.S)
        for label, matched in claims.forbidden_claims_in(text):
            problems.append(f"{path.name}: forbidden claim ({label}): {matched!r}")
    return problems


def check(root: pathlib.Path = DEFAULT_DIR) -> list[str]:
    """Every rule, in the order a reader would meet them."""
    if not root.is_dir():
        return [f"no such directory: {root}"]
    return structure_problems(root) + tracker_problems(root) + claim_problems(root)


def main(argv: list[str]) -> int:
    root = pathlib.Path(argv[1]) if len(argv) > 1 else DEFAULT_DIR
    if not root.is_absolute():
        root = (pathlib.Path.cwd() / root).resolve()
    problems = check(root)
    if problems:
        print(f"FAIL: {len(problems)} problem(s) in {root}:")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print(
        f"OK: {len(REQUIRED_FILES)} web-player document(s) hold their structure, "
        f"their evidence and the site's honesty rules."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
