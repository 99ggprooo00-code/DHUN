"""The web build's documents, checked rather than trusted.

Why this exists: this repository has, at different times, described the browser
build as an "engineering preview", published a features page saying the browser
client does not exist, and carried a README promising playback that no one had
heard. Every one of those was a document nobody was checking, and each was found
by a person reading the deployed page — the most expensive way to find it.

The web build was then rewritten from scratch, and the plan that described the
old one was wrong for the new one the same afternoon. So the documents here are
not a phase ledger to keep in step: they are a *description of the build that
exists*, and the gate asks the filesystem whether that is still true.

Four kinds of check, each of which has failed in this repository before:

* **Presence** — every document exists, so a directory cannot quietly lose the
  page that says what the project does not do.
* **The module map** — `ARCHITECTURE.md` names every `app-web/src/js/*.js` and
  names no module that has been deleted. This is the rule that would have caught
  the rebuild itself: the old documents named `js/source.js` for a day after it
  stopped existing.
* **Evidence** — every row of the status table must cite a path that resolves.
  A claim whose proof has been moved or deleted is a claim with no proof.
* **Claims and retired names** — the site's own honesty rules (`website_claims`)
  are applied to this prose, and the names of the deleted build may appear only
  in a sentence that says they are gone.

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

MODULE_DIR = REPO_ROOT / "app-web" / "src" / "js"
TEST_DIR = REPO_ROOT / "app-web" / "tests"

# The browser build's document set. Each one has a counterpart in the native
# document set (the parity table in README.md); the list is the contract, and the
# index rule below keeps README.md in step with it.
REQUIRED_FILES = (
    "README.md",
    "PRD.md",
    "TRD.md",
    "APP-FLOW.md",
    "UI-UX-DESIGN.md",
    "DATA-SCHEMA.md",
    "PROVIDER-INTEGRATION.md",
    "PLAYBACK.md",
    "SECURITY-PRIVACY.md",
    "TESTING.md",
    "RELEASE-DEPLOYMENT.md",
    "RUNBOOK.md",
    "RESEARCH-REGISTER.md",
    "DECISIONS-AND-CHANGES.md",
    "USER-GUIDE.md",
    "ARCHITECTURE.md",
    "DEVELOPMENT-GUIDE.md",
    "IMPLEMENTATION-STATUS.md",
    "IMPLEMENTATION-PLAN.md",
    "KNOWN-LIMITATIONS.md",
)

_LINK = re.compile(r"\]\(([^)#\s]+\.md)(?:#[^)]*)?\)")

STATUS_HEADING = "## What is verified, and by what"
UNVERIFIED_HEADING = "## Not verified"
MIN_UNVERIFIED_ITEMS = 3

# Files and documents of the build that was replaced. A document may mention one
# only while saying that it is gone; a bare mention is how an old plan survives a
# rewrite and then misleads the next person.
RETIRED_NAMES = (
    "js/source.js",
    "js/audio.js",
    "js/storage.js",
    "js/equalizer.js",
    "js/nav.js",
    "js/tabsync.js",
    "js/platform.js",
    "js/downloads.js",
    "js/offline.js",
    "js/video.js",
    "PHASE-00",
    "PHASE-02",
    "PHASE-10",
)

NEGATIONS = (
    "no longer",
    "removed",
    "deleted",
    "absent",
    "does not exist",
    "do not exist",
    "did not exist",
    "replaced",
    "gone",
)

# A path cited as evidence: a backticked token that looks like a repository path.
_PATH_SPAN = re.compile(r"`([^`\s]+)`")
_TABLE_ROW = re.compile(r"^\|(?!\s*-)[^|]+\|.+\|\s*$", re.M)

# A sentence about the JavaScript suite: the number it states has to be the
# number of tests that exist. The Python suite is described in the same pages
# with its own count, so only lines that name the browser suite are judged.
_SUITE_MARKERS = ("npm test", "app-web/tests", "pure modules")
_COUNT_CLAIM = re.compile(r"\b(\d+)\s+(assertions?|tests?|pass)\b")


def _read(root: pathlib.Path, name: str) -> str:
    path = root / name
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def _strip_code(text: str) -> str:
    """Fenced code and HTML comments are documentation, not claims."""
    text = re.sub(r"```.*?```", " ", text, flags=re.S)
    return re.sub(r"<!--.*?-->", " ", text, flags=re.S)


def presence_problems(root: pathlib.Path) -> list[str]:
    return [
        f"missing document: docs/web-player/{name}"
        for name in REQUIRED_FILES
        if not (root / name).is_file()
    ]


def index_problems(root: pathlib.Path) -> list[str]:
    """README.md indexes every document, and every link it makes resolves.

    A document that no index lists is a document nobody is pointed at; a link to a
    document that was renamed is a dead end. Both are findings.
    """
    text = _read(root, "README.md")
    if not text:
        return []  # presence_problems already reported it
    problems: list[str] = []
    # Only links within this directory are the index's business. A link that
    # leaves it (to ../README.md, ../decisions/...) is checked by the repository's
    # link test, which knows the whole tree.
    linked = {target for target in _LINK.findall(text) if "/" not in target}
    for name in REQUIRED_FILES:
        if name != "README.md" and name not in linked:
            problems.append(f"README.md: does not index {name}")
    for target in sorted(linked):
        if not (root / target).is_file():
            problems.append(f"README.md: links {target}, which does not exist")
    for path in sorted(root.glob("*.md")):
        if path.name not in REQUIRED_FILES:
            problems.append(
                f"docs/web-player/{path.name}: not in REQUIRED_FILES, so no rule checks it — "
                "add it to the list and to the README index"
            )
    return problems


def module_problems(root: pathlib.Path) -> list[str]:
    """The architecture document and the module directory agree."""
    text = _read(root, "ARCHITECTURE.md")
    if not text:
        return []  # presence_problems already reported it
    if not MODULE_DIR.is_dir():
        return [f"no module directory at {MODULE_DIR.relative_to(REPO_ROOT)}"]
    existing = sorted(path.stem for path in MODULE_DIR.glob("*.js"))
    named = set(re.findall(r"`js/([a-z0-9-]+)\.js`", text))
    problems: list[str] = []
    for module in existing:
        if module not in named:
            problems.append(
                f"ARCHITECTURE.md: js/{module}.js exists but the module table does not name it"
            )
    for module in sorted(named - set(existing)):
        problems.append(
            f"ARCHITECTURE.md: names js/{module}.js, which does not exist"
        )
    return problems


def evidence_problems(root: pathlib.Path) -> list[str]:
    """Every row of the status table cites a path that resolves."""
    text = _read(root, "IMPLEMENTATION-STATUS.md")
    if not text:
        return []
    start = text.find(STATUS_HEADING)
    if start < 0:
        return [f"IMPLEMENTATION-STATUS.md: missing section '{STATUS_HEADING}'"]
    section = text[start:]
    next_heading = section.find("\n## ", 1)
    if next_heading > 0:
        section = section[:next_heading]
    problems: list[str] = []
    rows = [row for row in _TABLE_ROW.findall(section)]
    if len(rows) < 5:
        problems.append(
            "IMPLEMENTATION-STATUS.md: the verified table has fewer than five rows "
            f"(found {len(rows)}) — the evidence would be a paragraph, not a ledger"
        )
    for row in rows:
        cells = [cell.strip() for cell in row.strip().strip("|").split("|")]
        if len(cells) != 2:
            continue
        claim, evidence = cells
        if claim == "Claim" or set(claim.replace(" ", "")) <= set("-:"):
            continue
        paths = [
            span.strip("./")
            for span in _PATH_SPAN.findall(evidence)
            if "/" in span and " " not in span
        ]
        if not paths:
            problems.append(
                f"IMPLEMENTATION-STATUS.md: the row {claim[:48]!r} cites no path — "
                "a claim needs a file that proves it"
            )
            continue
        resolved = [path for path in paths if (REPO_ROOT / path).exists()]
        if not resolved:
            problems.append(
                f"IMPLEMENTATION-STATUS.md: the row {claim[:48]!r} cites {paths[0]!r}, "
                "which does not exist"
            )

    unverified = text.find(UNVERIFIED_HEADING)
    if unverified < 0:
        problems.append(
            f"IMPLEMENTATION-STATUS.md: missing section '{UNVERIFIED_HEADING}' — a "
            "status page that lists only what works is the optimistic half of the truth"
        )
    else:
        block = text[unverified:]
        end = block.find("\n## ", 1)
        if end > 0:
            block = block[:end]
        items = [line for line in block.splitlines() if line.startswith("- ")]
        if len(items) < MIN_UNVERIFIED_ITEMS:
            problems.append(
                "IMPLEMENTATION-STATUS.md: 'Not verified' lists "
                f"{len(items)} item(s); at least {MIN_UNVERIFIED_ITEMS} are required — "
                "the release gate is one of them"
            )
    return problems


def retired_name_problems(root: pathlib.Path) -> list[str]:
    """A name from the replaced build may appear only with a note that it is gone."""
    problems: list[str] = []
    for name in REQUIRED_FILES:
        for number, line in enumerate(_read(root, name).splitlines(), start=1):
            haystack = line.lower()
            for retired in RETIRED_NAMES:
                if retired.lower() not in haystack:
                    continue
                if any(negation in haystack for negation in NEGATIONS):
                    continue
                problems.append(
                    f"{name}:{number}: mentions {retired!r}, which the current build "
                    "does not have — say that it is gone, or remove the line"
                )
    return problems


def declared_test_count() -> int:
    """Top-level `test(` declarations in the browser suite.

    Every test in `app-web/tests/` is a literal `test(...)` call, so this is the
    same number `node --test` prints. It is read from the files rather than
    remembered in prose: the documents said 153 for two rounds after the suite
    had grown, and nothing failed.
    """
    return sum(
        len(re.findall(r"(?m)^test\(", path.read_text(encoding="utf-8")))
        for path in sorted(TEST_DIR.glob("*.test.mjs"))
    )


def count_problems(root: pathlib.Path) -> list[str]:
    """Every stated size of the browser suite is the size of the suite."""
    count = declared_test_count()
    if not count:
        return [f"no tests found under {TEST_DIR.relative_to(REPO_ROOT)}"]
    documents = [(name, _read(root, name)) for name in REQUIRED_FILES]
    readme = REPO_ROOT / "app-web" / "README.md"
    if readme.is_file():
        documents.append(("app-web/README.md", readme.read_text(encoding="utf-8")))
    problems: list[str] = []
    for name, text in documents:
        for number, line in enumerate(text.splitlines(), start=1):
            if not any(marker in line for marker in _SUITE_MARKERS):
                continue
            for match in _COUNT_CLAIM.finditer(line):
                claimed = int(match.group(1))
                if claimed != count:
                    problems.append(
                        f"{name}:{number}: says {match.group(0)!r}, but "
                        f"{TEST_DIR.relative_to(REPO_ROOT)} holds {count} test(s)"
                    )
    return problems


def claim_problems(root: pathlib.Path) -> list[str]:
    problems: list[str] = []
    for name in REQUIRED_FILES:
        text = _strip_code(_read(root, name))
        for label, matched in claims.forbidden_claims_in(text):
            problems.append(f"{name}: forbidden claim ({label}): {matched!r}")
    return problems


def check(root: pathlib.Path = DEFAULT_DIR) -> list[str]:
    """Every rule, in the order a reader would meet them."""
    if not root.is_dir():
        return [f"no such directory: {root}"]
    return (
        presence_problems(root)
        + index_problems(root)
        + module_problems(root)
        + evidence_problems(root)
        + count_problems(root)
        + retired_name_problems(root)
        + claim_problems(root)
    )


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
        f"OK: {len(REQUIRED_FILES)} web-player document(s) describe the build that "
        "exists: the module map matches app-web/src/js, every evidence path "
        f"resolves, every stated suite size is the {declared_test_count()} tests "
        "that exist, no retired name survives, and no claim outruns the repository."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
