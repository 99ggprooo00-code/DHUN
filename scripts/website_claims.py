#!/usr/bin/env python3
"""Honesty contract for the DHUN marketing site — the three D5 gates.

This module is the reviewer. The site ships to `main` with no human in the
loop, so its claims are checked mechanically against the **built** HTML
(`website/dist/`), in app CI step 1 and in the site's own workflow. The rules
and their sources are in `.ai/WEBSITE_PLAN.md` Part A §4.

Three checks:

1. `check_forbidden_claims` — the built pages must not claim a platform or a
   capability DHUN does not ship (iOS, a web player, cross-device sync, library
   import, FLAC/lossless, any audio bitrate, any store channel, and so on).
   A mention is allowed only inside a clause that negates it, which is how the
   "not in DHUN" list can name them honestly.
2. `check_required_caveats` — the front page must carry the three required
   disclosures, each in an element that declares itself with a `data-caveat`
   attribute so an empty element cannot satisfy the rule.
3. `check_no_baked_digests` — no SHA-256 and no byte size may be hard-coded in
   the built pages or the site sources. The rolling `test` release is replaced
   on every push to `main`, so its bytes change; the site links by URL only.

Run directly for a report:  python3 scripts/website_claims.py [dist-dir]
"""

from __future__ import annotations

import html
import pathlib
import re
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_DIST = REPO_ROOT / "website" / "dist"
SOURCE_DIR = REPO_ROOT / "website" / "src"

ROUTES = {
    "/": "index.html",
    "/features/": "features/index.html",
    "/ui/": "ui/index.html",
    "/404.html": "404.html",
}

# --------------------------------------------------------------------------
# 1. Forbidden claims.
# --------------------------------------------------------------------------

# (label used in the failure message, compiled pattern)
FORBIDDEN_TERMS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("Apple/iOS platform", re.compile(r"\b(iOS|iPadOS|iPhone|iPad|App Store)\b", re.I)),
    (
        "web player / browser client",
        re.compile(
            r"\b(web ?player|browser (client|player)|progressive web app|PWA|"
            r"web app|in your browser|play in the browser)\b",
            re.I,
        ),
    ),
    (
        # Any use of "sync" that is not negated: DHUN has no server and no
        # account, so no sync feature exists. (The Android permission constant
        # FOREGROUND_SERVICE_DATA_SYNC does not match: `_` is a word character.)
        "cross-device sync",
        # "Synced lyrics" is a real DHUN feature (Phase 11); sync *of a
        # library or between devices* is not, and that is what this catches.
        re.compile(
            r"(?<!lyrics )(?<!lyric )\bsync(s|ed|ing)?\b(?!\s+lyrics?\b)",
            re.I,
        ),
    ),
    (
        "library import",
        re.compile(
            r"\b(import (your )?(library|playlists|music)|library import|"
            r"import from (Spotify|Apple Music|YouTube Music))\b",
            re.I,
        ),
    ),
    (
        "audio quality claim",
        re.compile(
            r"\b(FLAC|ALAC|lossless|hi-?res|high[- ]resolution audio|"
            r"bitrate|kbps|studio quality|CD quality|24/192|24-bit)\b",
            re.I,
        ),
    ),
    (
        "store channel",
        re.compile(
            r"\b(Play Store|Google Play Store|F-Droid|IzzyOnDroid|"
            r"Microsoft Store|App Store|Flathub|Snap Store)\b",
            re.I,
        ),
    ),
    (
        "platform DHUN does not ship",
        re.compile(
            r"\b(Android Auto|CarPlay|Chromecast|AirPlay|tvOS|watchOS|"
            r"Apple Music|Spotify|SoundCloud|Deezer)\b",
            re.I,
        ),
    ),
    (
        "account / sign-in",
        re.compile(
            r"\b(sign in with|sign-in with|Google account|create an account|"
            r"log ?in to|sign up for)\b",
            re.I,
        ),
    ),
)

# A clause carrying one of these may name a forbidden term: the site is
# allowed (indeed required) to say what it does *not* do.
NEGATION_CUES = re.compile(
    r"(\bno\b|\bnot\b|\bnever\b|\bwithout\b|\bnothing\b|\bnone\b|\bneither\b|"
    r"\bnor\b|\bcannot\b|\bcan't\b|\bdoesn't\b|\bdon't\b|\bisn't\b|\bhasn't\b|"
    r"\bunavailable\b|\babsent\b|\bmissing\b|\bfree of\b)",
    re.I,
)

CLAUSE_BOUNDARY = re.compile(r"[.!?;:·—\n]|\s--\s")


def html_to_text(markup: str) -> str:
    """Visible text of a page: comments and non-content elements removed."""
    text = re.sub(r"<!--.*?-->", " ", markup, flags=re.S)
    text = re.sub(r"<(script|style)\b.*?</\1>", " ", text, flags=re.S | re.I)
    text = re.sub(r"<[^>]+>", " ", text)
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


def _clauses(text: str) -> list[str]:
    return CLAUSE_BOUNDARY.split(text)


def _clause_index_of(clauses: list[str], offset: int) -> int:
    """Index of the clause containing the character at `offset` of the join."""
    seen = 0
    for i, clause in enumerate(clauses):
        # +1 for the boundary character that was removed by the split
        seen += len(clause) + 1
        if offset < seen:
            return i
    return len(clauses) - 1


def forbidden_claim_violations(pages: dict[str, str]) -> list[str]:
    """pages: route -> built HTML. Returns human-readable violations."""
    violations: list[str] = []
    for route, markup in sorted(pages.items()):
        text = html_to_text(markup)
        for label, pattern in FORBIDDEN_TERMS:
            for match in pattern.finditer(text):
                clauses = _clauses(text)
                # A negation cue must sit in the *same* clause as the mention:
                # "DHUN has no accounts. Sync across devices is coming" is a
                # claim about sync, and the earlier "no" does not excuse it.
                clause = clauses[_clause_index_of(clauses, match.start())]
                if NEGATION_CUES.search(clause):
                    continue
                snippet = text[max(0, match.start() - 60) : match.end() + 60]
                violations.append(
                    f"{route}: forbidden claim ({label}): "
                    f"‘{match.group(0)}’ in “…{snippet.strip()}…”"
                )
    return violations


# --------------------------------------------------------------------------
# 2. Required caveats.
# --------------------------------------------------------------------------

# caveat key -> phrases, at least one of which must appear inside the element
# that carries data-caveat="<key>" on the front page.
REQUIRED_CAVEATS: dict[str, tuple[str, ...]] = {
    "rolling-unverified": ("rolling unverified development build",),
    "borrowed-time": ("borrowed time",),
    "hardware-gates-open": ("hardware gates still open",),
}

_DATA_CAVEAT_OPEN = re.compile(
    r"<(?P<tag>[a-z][a-z0-9]*)\b[^>]*\bdata-caveat=\"(?P<key>[a-z-]+)\"[^>]*>", re.I
)


def _element_blocks(markup: str) -> dict[str, str]:
    """Map every data-caveat key to the inner HTML of its element.

    Nesting is handled by matching the element's own closing tag; the markup
    this site emits for those elements is not nested recursively, so the first
    matching close tag is the right one.
    """
    blocks: dict[str, str] = {}
    for match in _DATA_CAVEAT_OPEN.finditer(markup):
        key = match.group("key")
        tag = match.group("tag")
        rest = markup[match.end() :]
        closing = re.search(rf"</{tag}\s*>", rest, re.I)
        blocks.setdefault(key, rest[: closing.start()] if closing else rest)
    return blocks


def required_caveat_violations(pages: dict[str, str]) -> list[str]:
    violations: list[str] = []
    front = pages.get("/")
    if front is None:
        return ["/: front page (index.html) is missing from the built site"]

    blocks = _element_blocks(front)
    for key, phrases in REQUIRED_CAVEATS.items():
        if key not in blocks:
            violations.append(
                f"/: required caveat ‘{key}’ is not present "
                f"(no element carries data-caveat=\"{key}\")"
            )
            continue
        text = html_to_text(blocks[key]).lower()
        if not any(phrase in text for phrase in phrases):
            violations.append(
                f"/: caveat ‘{key}’ is empty or does not state the required "
                f"disclosure (expected one of {phrases})"
            )
    return violations


# --------------------------------------------------------------------------
# 3. No baked digests or byte sizes.
# --------------------------------------------------------------------------

DIGEST_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("SHA-256 digest", re.compile(r"\b[0-9a-fA-F]{64}\b")),
    ("SHA-1 digest", re.compile(r"\b[0-9a-fA-F]{40}\b")),
    ("labelled digest", re.compile(r"\bsha-?256\b\s*[:=]?\s*[0-9a-fA-F]{8,}", re.I)),
    (
        "byte size",
        re.compile(r"\b\d{1,3}(?:,\d{3}){2,}\s*(?:bytes|B\b)|\b\d{7,}\s*(?:bytes|B\b)"),
    ),
    (
        "file size in MB",
        re.compile(r"\b\d+(?:\.\d+)?\s?(?:MB|MiB|GB|GiB)\b"),
    ),
)

# The sidecar links themselves mention ".sha256" — that is a URL, not a digest.
_SHA256_URL = re.compile(r"[./-]sha256\b", re.I)


def digest_violations(documents: dict[str, str]) -> list[str]:
    """documents: label -> text (built HTML *and* site sources)."""
    violations: list[str] = []
    for label, text in sorted(documents.items()):
        for name, pattern in DIGEST_PATTERNS:
            for match in pattern.finditer(text):
                if _SHA256_URL.search(match.group(0)):
                    continue
                snippet = text[max(0, match.start() - 50) : match.end() + 30]
                violations.append(
                    f"{label}: baked {name} ‘{match.group(0)[:24]}…’ "
                    f"in “…{snippet.strip()}…”"
                )
    return violations


# --------------------------------------------------------------------------
# 4. Copy checked against the code that ships.
# --------------------------------------------------------------------------

# "No telemetry, no crash reporting, no advertising SDK" is a claim about the
# *application*, not about this site, and nothing above can check it: the
# forbidden-claim rules only read the site's own words back. So the claim is
# checked against the dependency graph the builds actually resolve. A claim
# whose evidence is a grep run once by hand is a claim with an expiry date.
TELEMETRY_CLAIMS = (
    "no telemetry",
    "no crash reporting",
    "no advertising sdk",
    "no trackers",
    "no analytics",
)

# Matched case-insensitively against every Gradle build script and version
# catalogue in the repository. Deliberately name-shaped rather than a bare
# "firebase": `google-services`/`firebase-bom` alone would be a false positive
# on a project that merely configures something else, and a rule that cries wolf
# gets switched off.
TELEMETRY_SDKS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("Firebase Analytics", re.compile(r"firebase-analytics|firebase\s*analytics|play-services-analytics", re.I)),
    ("Crashlytics", re.compile(r"crashlytics", re.I)),
    ("Sentry", re.compile(r"\bsentry", re.I)),
    ("Bugsnag", re.compile(r"\bbugsnag", re.I)),
    ("New Relic", re.compile(r"newrelic|new-relic", re.I)),
    ("Datadog", re.compile(r"\bdatadog", re.I)),
    ("Mixpanel", re.compile(r"\bmixpanel", re.I)),
    ("Amplitude", re.compile(r"\bamplitude", re.I)),
    # Broadened after the test caught the original being too narrow: the real
    # artefact is `com.segment.analytics.kotlin:android`, which contains none of
    # `analytics-android`, `segment-analytics` or `analytics-kotlin`.
    ("Segment", re.compile(r"segment[.-]analytics|analytics-kotlin|analytics-android", re.I)),
    ("AppsFlyer/Adjust", re.compile(r"appsflyer|adjust-android", re.I)),
    ("Braze", re.compile(r"\bbraze\b", re.I)),
    ("Google Analytics", re.compile(r"google-analytics|gtag", re.I)),
)

BUILD_FILE_GLOBS = ("*.gradle.kts", "*.gradle", "*.versions.toml")


def build_files(root: pathlib.Path = REPO_ROOT) -> list[pathlib.Path]:
    """Every Gradle build script and version catalogue under `root`."""
    found: list[pathlib.Path] = []
    for pattern in BUILD_FILE_GLOBS:
        found.extend(sorted(pathlib.Path(root).rglob(pattern)))
    return [path for path in found if ".git" not in path.parts]


def telemetry_claim_violations(
    pages: dict[str, str], root: pathlib.Path = REPO_ROOT
) -> list[str]:
    """A page that claims no telemetry must not ship a telemetry SDK.

    Direction matters and is asserted in both: the rule fires when the claim and
    the SDK are both present, and is silent when either is absent — a site that
    stopped making the claim is not checked, and a tree with no SDK passes. The
    tests in `test_website_claims.py` exercise all four combinations against
    synthetic trees, because the real one is not allowed to be broken on purpose
    (the Gradle files are outside this workstream).
    """
    claimed: list[str] = []
    for route, markup in pages.items():
        text = html_to_text(markup).lower() + " " + markup.lower()
        for phrase in TELEMETRY_CLAIMS:
            if phrase in text:
                claimed.append(f"{route} ({phrase})")
                break
    if not claimed:
        return []
    violations: list[str] = []
    for path in build_files(root):
        content = path.read_text(encoding="utf-8", errors="replace")
        for name, pattern in TELEMETRY_SDKS:
            for match in pattern.finditer(content):
                violations.append(
                    f"{path.relative_to(root)}: {name} dependency ‘{match.group(0)}’ is in "
                    f"the build, but {' and '.join(claimed)} claim there is none"
                )
    return violations


# --------------------------------------------------------------------------
# Loading and reporting.
# --------------------------------------------------------------------------


def load_built_pages(dist_dir: pathlib.Path = DEFAULT_DIST) -> dict[str, str]:
    pages: dict[str, str] = {}
    for route, relative in ROUTES.items():
        path = pathlib.Path(dist_dir) / relative
        if path.is_file():
            pages[route] = path.read_text(encoding="utf-8")
    return pages


def load_site_sources(source_dir: pathlib.Path = SOURCE_DIR) -> dict[str, str]:
    documents: dict[str, str] = {}
    for path in sorted(pathlib.Path(source_dir).rglob("*")):
        if path.is_file() and path.suffix in {".njk", ".js", ".html", ".css", ".txt", ".xml"}:
            documents[str(path.relative_to(REPO_ROOT))] = path.read_text(encoding="utf-8")
    return documents


def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    dist = pathlib.Path(argv[0]) if argv else DEFAULT_DIST
    pages = load_built_pages(dist)
    if not pages:
        print(f"FAIL: no built pages under {dist} — build the site first", file=sys.stderr)
        return 2

    problems = []
    problems += forbidden_claim_violations(pages)
    problems += required_caveat_violations(pages)
    problems += digest_violations({**pages, **load_site_sources()})
    problems += telemetry_claim_violations(pages)

    if problems:
        print(f"FAIL: {len(problems)} honesty-contract violation(s):", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    print(
        f"OK: {len(pages)} built page(s) pass the honesty contract "
        f"({len(FORBIDDEN_TERMS)} forbidden-claim rules, "
        f"{len(REQUIRED_CAVEATS)} required caveats, {len(DIGEST_PATTERNS)} digest rules, "
        f"{len(TELEMETRY_SDKS)} telemetry-SDK rules against the shipped dependency graph)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
