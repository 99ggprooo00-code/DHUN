#!/usr/bin/env python3
"""Smoke-check the **served** site — the one thing a build cannot verify.

`website_claims.py` checks the committed build, and `website.yml`'s browser job
checks a local server. Neither proves that the bytes a visitor receives are the
bytes that passed. This script fetches the published URL and runs the *same*
honesty contract over the response bodies (it imports the rules rather than
restating them), then resolves every link on the served pages over HTTP.

It is deliberately narrow: the three routes, their required caveats, the
forbidden-claim rules, the canonical URL, and link resolution. It is not a
Lighthouse run and it makes no claim about performance.

Two deployment facts matter to how this is called (see `.ai/ROADMAP.md`):

1. While Pages is `build_type: legacy` the canonical URL serves the repository
   `README.md` through Jekyll, so the caveats are *absent* and this script would
   fail — correctly, but not usefully. The workflow only runs it after a
   successful deploy, and the deploy job already reports the legacy setting as a
   warning.
2. Even once Pages is switched, GitHub's Pages CDN can serve the previous
   deployment for a short time. A stale miss is reported as a failure with the
   HTTP status and the digest of what was actually fetched, so the next run (or
   a human) can tell "not deployed yet" from "deployed wrong".

Usage:
    python3 scripts/website_smoke.py [base-url]
"""

from __future__ import annotations

import hashlib
import pathlib
import re
import sys
import urllib.error
import urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import website_claims as claims  # noqa: E402

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_BASE = "https://99ggprooo00-code.github.io/DHUN"
CANONICAL = "https://99ggprooo00-code.github.io/DHUN"
ROUTES = ("/", "/features/", "/ui/")
TIMEOUT = 30
USER_AGENT = "DHUN-site-smoke/1.0 (+https://github.com/99ggprooo00-code/DHUN)"


def fetch(url: str) -> tuple[int, str]:
    """(status, body). A non-200 is returned, not raised, so it can be reported."""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, ""
    except urllib.error.URLError as error:
        return 0, f"<unreachable: {error.reason}>"


def digest(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:12]


def internal_links(markup: str) -> list[str]:
    """Root-relative hrefs of a served page (the site's own assets and routes)."""
    found = re.findall(r'<(?:a|link)\b[^>]*\bhref="(/[^"]*)"', markup, re.I)
    return sorted(set(found))


def page_problems(route: str, status: int, markup: str) -> list[str]:
    """Everything wrong with one served page. Pure: takes the response as input."""
    problems: list[str] = []
    if status != 200:
        return [f"{route}: served HTTP {status}"]
    if not markup.strip():
        return [f"{route}: served an empty body"]

    canonical = re.search(r'<link[^>]*rel="canonical"[^>]*href="([^"]+)"', markup, re.I)
    expected = f"{CANONICAL}{route}" if route != "/" else f"{CANONICAL}/"
    if not canonical or canonical.group(1) != expected:
        problems.append(f"{route}: canonical URL is {canonical.group(1) if canonical else 'missing'}, expected {expected}")

    text = claims.html_to_text(markup)
    for label, pattern in claims.FORBIDDEN_TERMS:
        for match in pattern.finditer(text):
            clauses = claims._clauses(text)
            clause = clauses[claims._clause_index_of(clauses, match.start())]
            if claims.NEGATION_CUES.search(clause):
                continue
            problems.append(f"{route}: served page carries forbidden claim ({label}): ‘{match.group(0)}’")
    return problems


def caveat_problems(pages: dict[str, str]) -> list[str]:
    """The three required disclosures must survive publication.

    Same rule, same message, same code path as the build-time honesty contract —
    this only changes which bytes are handed to it.
    """
    return [f"served page: {problem}" for problem in claims.required_caveat_violations(pages)]


def smoke_problems(fetched: dict[str, tuple[int, str]]) -> list[str]:
    """The whole check, from already-fetched bodies (so it is testable offline)."""
    problems: list[str] = []
    pages: dict[str, str] = {}
    for route in ROUTES:
        status, markup = fetched.get(route, (0, "<not fetched>"))
        problems += page_problems(route, status, markup)
        if status == 200 and markup.strip():
            pages[route] = markup
    if "/" in pages:
        problems += caveat_problems(pages)
    return problems


def link_problems(
    base: str, fetched: dict[str, tuple[int, str]], fetch_one=fetch
) -> list[str]:
    """Every root-relative link on the served pages must resolve on the server.

    `fetch_one` is injectable so the rule can be exercised without a network.
    """
    problems: list[str] = []
    seen: dict[str, int] = {}
    for route in ROUTES:
        status, markup = fetched.get(route, (0, ""))
        if status != 200:
            continue
        for href in internal_links(markup):
            if href in seen:
                continue
            link_status, _ = fetch_one(f"{base}{href}")
            seen[href] = link_status
            if link_status != 200:
                problems.append(f"{route}: served page links {href}, which returns HTTP {link_status}")
    return problems


def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    base = (argv[0] if argv else DEFAULT_BASE).rstrip("/")
    fetched = {route: fetch(f"{base}{route}") for route in ROUTES}

    for route in ROUTES:
        status, markup = fetched[route]
        print(f"{route}: HTTP {status}, {len(markup)} bytes, sha256:{digest(markup)}")

    problems = smoke_problems(fetched)
    problems += link_problems(base, fetched)
    if problems:
        print(f"FAIL: {len(problems)} served-site problem(s):", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    print(
        f"OK: {len(ROUTES)} served route(s) carry the three required caveats, "
        f"trip no forbidden-claim rule, and every internal link resolves."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
