#!/usr/bin/env python3
"""Quality gates for the DHUN marketing site — the checks a machine can make.

Companion to `website_claims.py` (the honesty contract). This module asserts
what the pages must *have*: the page-weight budget, zero client-side
JavaScript, resolving internal links, no third-party origins, responsive
breakpoints, WCAG-AA contrast for the declared token pairs, landmarks and
heading structure, per-page metadata, crawlability, the label on every mockup,
and the screenshot backlog marker that keeps the mockups replaceable.

Everything here reads the **built** output (`website/dist/`), so it runs in
app CI step 1 with no Node, no network and no browser. The checks that need a
real browser (Lighthouse/axe/Playwright) run in `.github/workflows/website.yml`.

**Where the CSS lives.** The stylesheet is inlined into each page's `<head>`
(one request per route — see `website/eleventy.config.js`), so the rules below
read `<style>` blocks as well as any standalone `.css` file: `_read_css` returns
the union, and `page_stylesheet` returns one page's own CSS. The rules
themselves are unchanged, which is the point of the adaptation — a rule that only
ever read a linked file would have proven nothing about the bytes that ship.

Run directly for a report:  python3 scripts/website_quality.py [dist-dir]
"""

from __future__ import annotations

import base64
import html
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ElementTree

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_DIST = REPO_ROOT / "website" / "dist"
SOURCE_DIR = REPO_ROOT / "website" / "src"

# Budget from .ai/WEBSITE_PLAN.md Part A §10. `KB` is 1024 bytes.
KB = 1024
HTML_CSS_BUDGET = 60 * KB
JS_BUDGET = 10 * KB
ASSET_BUDGET = 150 * KB

# How much a route's HTML+CSS may grow over the committed baseline before the
# ratchet fails (Part A §10.2). Growth is allowed, silently is not.
RATCHET_TOLERANCE = 0.05

CANONICAL_ORIGIN = "https://99ggprooo00-code.github.io/DHUN"

# Copy that must never reach a visitor, whatever produced it: a leaked Markdown
# backtick, an HTML tag that was escaped instead of rendered, a link that goes
# nowhere, or a placeholder word. The site has no stubs by policy, so this is a
# rule rather than a review note (Part A §4).
ESCAPED_TAG = re.compile(r"&lt;/?(?:code|strong|em|b|i|a|span|br|p|div|ul|ol|li|pre)\b", re.I)
DEAD_LINK = re.compile(r'href\s*=\s*"(?:#|javascript:[^"]*)"', re.I)
PLACEHOLDER_WORDS = re.compile(r"\b(coming soon|lorem ipsum|to be announced|TBD|FIXME)\b", re.I)
# ...but “These are absent, not ‘coming soon’” is the opposite of a promise, so
# the word is only a violation when no negation sits in the same clause. This is
# website_claims.py's definition, imported, for exactly that reason.
from website_claims import CLAUSE_BOUNDARY, NEGATION_CUES  # noqa: E402


def _placeholder_clause(text: str, start: int) -> str:
    clauses, offset = [], 0
    for piece in CLAUSE_BOUNDARY.split(text):
        clauses.append((offset, piece))
        offset += len(piece) + 1
    for offset, piece in clauses:
        if offset <= start <= offset + len(piece):
            return piece
    return text

# What counts as "this claim came from somewhere" inside an HTML comment.
CITATION = re.compile(
    r"(ADR-\d+|PR #\d+|Phase \d+|MASTER_PROMPT|README\.md|LICENSE|THIRD_PARTY|"
    r"\.ai/[A-Za-z_]+\.md|scripts/[a-z_]+\.py|AndroidManifest|app-android|"
    r"app-desktop|shared/src|minSdk|"
    r"DhunAppearance|test-release\.yml|sitemap\.njk|shared/src)",
    re.I,
)

# Facts that rot: a semantic version, a calendar date, a build number. The site
# currently prints none of these; anything added here needs a checked source and
# an explicit allowlist entry (with the reason) rather than a looser pattern.
STALE_FACT_PATTERNS = (
    (re.compile(r"\bv?\d+\.\d+\.\d+\b"), "app version"),
    (re.compile(r"\b20\d\d-\d\d-\d\d\b"), "date"),
    (re.compile(r"\bbuild \d{3,}\b", re.I), "build number"),
)
STALE_FACT_ALLOWLIST: frozenset[str] = frozenset()
REPOSITORY_URL = "https://github.com/99ggprooo00-code/DHUN"
ALLOWED_EXTERNAL_ORIGINS = (
    REPOSITORY_URL,
    "https://99ggprooo00-code.github.io/DHUN",
)
# The site's routes. /download/ was removed by decision (the site is not a
# distribution channel — the release page is), and /ui/ took its place so the
# project's interface is what a visitor actually sees.
ROUTES = ("/", "/features/", "/ui/")
# The screenshot backlog lives in `.ai/WEBSITE_PLAN.md` Part A §9 and is parsed
# from there rather than duplicated here: one table, machine-read, so a mockup
# can never lose its replacement plan (see `backlog_drift_violations`).
BACKLOG_PLAN = REPO_ROOT / ".ai" / "WEBSITE_PLAN.md"
BACKLOG_ROW = re.compile(r"^\|\s*(\d+)\s*\|\s*`(mock-[a-z0-9-]+)`\s*\|\s*(shipped|planned)\s*\|", re.M)
BACKLOG_STATUSES = ("shipped", "planned")

# Token pairs asserted for WCAG AA, mirroring the app's own
# DhunThemeContrastTest discipline. (foreground, background, minimum ratio)
CONTRAST_PAIRS = (
    ("--text", "--bg", 4.5),
    ("--text-2", "--bg", 4.5),
    ("--text-3", "--bg", 4.5),
    ("--accent", "--bg", 4.5),
    ("--text", "--surface", 4.5),
    ("--text-2", "--surface", 4.5),
    ("--text-3", "--surface", 4.5),
    ("--accent", "--surface", 4.5),
    ("--on-accent", "--accent", 4.5),
    ("--on-accent-container", "--accent-container", 4.5),
    ("--accent", "--surface-variant", 3.0),
    ("--warning", "--surface", 4.5),
)


# --------------------------------------------------------------------------
# helpers
# --------------------------------------------------------------------------


def _files(dist: pathlib.Path) -> list[pathlib.Path]:
    return sorted(p for p in pathlib.Path(dist).rglob("*") if p.is_file())


def page_paths(dist: pathlib.Path) -> dict[str, pathlib.Path]:
    """route -> built file. A route only counts when its file exists."""
    mapping = {
        "/": dist / "index.html",
        "/features/": dist / "features" / "index.html",
        "/ui/": dist / "ui" / "index.html",
        "/404.html": dist / "404.html",
    }
    return {route: path for route, path in mapping.items() if path.is_file()}


def text_of(markup: str) -> str:
    stripped = re.sub(r"<!--.*?-->", " ", markup, flags=re.S)
    stripped = re.sub(r"<(script|style)\b.*?</\1>", " ", stripped, flags=re.S | re.I)
    stripped = re.sub(r"<[^>]+>", " ", stripped)
    return re.sub(r"\s+", " ", html.unescape(stripped)).strip()


def _compact(text: str) -> str:
    """Whitespace-free copy, so a check reads the same before and after minification."""
    return re.sub(r"\s+", "", text)


STYLE_BLOCK = re.compile(r"<style\b[^>]*>(.*?)</style>", re.S | re.I)
STYLESHEET_LINK = re.compile(
    r"<link\b(?=[^>]*\brel=\"stylesheet\")(?=[^>]*\bhref=\"([^\"]+)\")[^>]*>", re.I
)


def inline_styles(dist: pathlib.Path) -> dict[str, str]:
    """route -> the CSS in that page's own `<style>` blocks, if any."""
    styles: dict[str, str] = {}
    for route, path in page_paths(dist).items():
        css = "\n".join(STYLE_BLOCK.findall(path.read_text(encoding="utf-8")))
        if css.strip():
            styles[route] = css
    return styles


def external_css(dist: pathlib.Path) -> dict[str, str]:
    assets = dist / "assets"
    if not assets.is_dir():
        return {}
    return {p.name: p.read_text(encoding="utf-8") for p in sorted(assets.glob("*.css")) if p.is_file()}


def _read_css(dist: pathlib.Path) -> dict[str, str]:
    """Every stylesheet the site ships, whatever shape it ships in.

    External files keep their own names; inlined CSS is merged under the
    logical name `styles.css`, which is what the rule bodies below ask for.
    The site currently inlines one composed sheet per route, so this returns
    every module the site uses, in route order.
    """
    sheets = external_css(dist)
    inline = inline_styles(dist)
    if inline and "styles.css" not in sheets:
        sheets["styles.css"] = "\n".join(inline[route] for route in page_paths(dist) if route in inline)
    return sheets


def site_stylesheet(dist: pathlib.Path) -> str:
    """The whole site's CSS, from wherever it lives (see `_read_css`)."""
    return _read_css(dist).get("styles.css", "")


def linked_stylesheets(dist: pathlib.Path, route: str) -> list[pathlib.Path]:
    """The stylesheets a page links, resolved to files that exist in `dist`."""
    path = page_paths(dist).get(route)
    if path is None:
        return []
    files: list[pathlib.Path] = []
    for href in STYLESHEET_LINK.findall(path.read_text(encoding="utf-8")):
        reference = href.split("?", 1)[0].split("#", 1)[0]
        if reference.startswith(("http://", "https://", "data:")):
            continue
        candidate = dist / reference.lstrip("/") if reference.startswith("/") else path.parent / reference
        if candidate.is_file():
            files.append(candidate)
    return files


def page_weight_bytes(dist: pathlib.Path, route: str, path: pathlib.Path) -> int:
    """Bytes on the wire for one route: its HTML plus any stylesheet it links.

    Inlined CSS is already inside the HTML, so it is never counted twice.
    """
    total = path.stat().st_size
    total += sum(f.stat().st_size for f in linked_stylesheets(dist, route))
    return total


def page_stylesheet(dist: pathlib.Path, route: str) -> str:
    """The CSS that page actually ships: its inline blocks, or its linked file."""
    inline = inline_styles(dist).get(route)
    if inline:
        return inline
    return "\n".join(
        f.read_text(encoding="utf-8") for f in linked_stylesheets(dist, route)
    )


# --------------------------------------------------------------------------
# checks
# --------------------------------------------------------------------------


def weight_violations(dist: pathlib.Path) -> list[str]:
    violations: list[str] = []
    pages = page_paths(dist)

    for route in ROUTES:
        path = pages.get(route)
        if path is None:
            violations.append(f"{route}: built page is missing")
            continue
        html_bytes = path.stat().st_size
        linked = sum(f.stat().st_size for f in linked_stylesheets(dist, route))
        total = html_bytes + linked
        if total > HTML_CSS_BUDGET:
            where = f"HTML {html_bytes} B (CSS inlined)" if not linked else (
                f"HTML {html_bytes} B + linked CSS {linked} B"
            )
            violations.append(
                f"{route}: HTML+CSS is {total} B, over the {HTML_CSS_BUDGET} B budget ({where})"
            )

    js_bytes = sum(
        p.stat().st_size for p in _files(dist) if p.suffix in {".js", ".mjs", ".cjs"}
    )
    if js_bytes > JS_BUDGET:
        violations.append(f"client JavaScript is {js_bytes} B, over the {JS_BUDGET} B budget")

    for path in _files(dist):
        size = path.stat().st_size
        if size > ASSET_BUDGET:
            violations.append(
                f"{path.relative_to(dist)} is {size} B, over the {ASSET_BUDGET} B per-asset budget"
            )
    return violations


def javascript_violations(dist: pathlib.Path) -> list[str]:
    """The site ships no client-side JavaScript at all.

    JSON-LD is the one exception, and it is not JavaScript: it is a `<script>`
    of type `application/ld+json` whose body is data that no engine evaluates.
    It is admitted by *type* — the body is parsed as JSON below, so a `<script>`
    that is anything else is still a violation, including one that claims the
    JSON-LD type and carries code. `on*` event-handler attributes are banned
    here too: they are the other way to run script without a `<script>` tag, and
    a rule about "no client-side JavaScript" that only looked for tags would miss
    them.
    """
    violations: list[str] = []
    scripts = [p for p in _files(dist) if p.suffix in {".js", ".mjs", ".cjs"}]
    for path in scripts:
        violations.append(f"{path.relative_to(dist)}: the site ships no client-side JavaScript")
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        for match in re.finditer(r"<script\b([^>]*)>", markup, re.I):
            attributes = match.group(1)
            if re.search(r'\btype\s*=\s*"application/ld\+json"', attributes, re.I):
                continue
            violations.append(f"{route}: inline <script> tag found: {match.group(0)[:70]}")
        for match in re.finditer(r"<script\b[^>]*\btype=\"application/ld\+json\"[^>]*>(.*?)</script>", markup, re.I | re.S):
            try:
                json.loads(match.group(1).strip())
            except ValueError as error:
                violations.append(
                    f"{route}: the JSON-LD block is not valid JSON ({error}) — it is "
                    f"treated as data, so it has to be data"
                )
        for match in re.finditer(r"<[a-z][a-z0-9]*\b[^>]*?(\son[a-z]+)\s*=", markup, re.I):
            violations.append(
                f"{route}: inline event handler attribute found: {match.group(1).strip()}"
            )
    return violations


def _resolve_internal(dist: pathlib.Path, reference: str) -> bool:
    if reference.startswith("#") or reference.startswith("mailto:"):
        return True
    # A `data:` URI is not a file reference: there is nothing to resolve. Its
    # *contents* are asserted where the site depends on them —
    # `favicon_violations` decodes the inlined icon and compares it with its
    # source file — so this branch cannot be used to smuggle a dead asset past
    # the link check.
    if reference.startswith("data:"):
        return True
    path = reference.split("#", 1)[0].split("?", 1)[0]
    if not path.startswith("/"):
        return True  # relative links are not used by this site's markup
    candidate = dist / path.lstrip("/")
    if candidate.is_dir():
        candidate = candidate / "index.html"
    return candidate.is_file()


def link_violations(dist: pathlib.Path) -> list[str]:
    violations: list[str] = []
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        for match in re.finditer(
            r"""<(?:a|link|img|script)\b[^>]*?\b(?:href|src)="([^"]+)\"""",
            markup,
            re.I,
        ):
            reference = match.group(1)
            if reference.startswith("data:"):
                # Inlined bytes, not a URL to fetch. `favicon_violations` is the
                # rule that reads the bytes back out.
                continue
            if reference.startswith(("http://", "https://")):
                if not reference.startswith(ALLOWED_EXTERNAL_ORIGINS):
                    violations.append(
                        f"{route}: third-party origin in a URL: {reference} "
                        f"(no CDN, no analytics, no font service)"
                    )
                continue
            if not _resolve_internal(dist, reference):
                violations.append(f"{route}: dead internal link: {reference}")
        for match in re.finditer(r"""\burl\((['\"]?)([^)'\"]+)\1\)""", markup):
            reference = match.group(2)
            if reference.startswith(("http://", "https://", "data:")):
                continue
            if not _resolve_internal(dist, reference):
                violations.append(f"{route}: dead asset reference: {reference}")
    return violations


def backlog_rows() -> list[tuple[str, str]]:
    """(site id, status) for every row of the §9 screenshot backlog table."""
    if not BACKLOG_PLAN.is_file():
        return []
    return [(match.group(2), match.group(3)) for match in BACKLOG_ROW.finditer(BACKLOG_PLAN.read_text(encoding="utf-8"))]


def backlog_drift_violations(dist: pathlib.Path) -> list[str]:
    """The backlog and the built pages must agree in both directions.

    A mockup is a stand-in for a capture that does not exist yet, so the two can
    drift apart in two ways that both matter: a mockup with no replacement plan
    (nobody will ever replace it), and a plan entry that has quietly become a
    figure on a page (or a `planned` row used as if it were real). Both are
    failures here rather than a review note.
    """
    violations: list[str] = []
    rows = backlog_rows()
    if not rows:
        return [
            "the screenshot backlog in .ai/WEBSITE_PLAN.md §9 could not be parsed; "
            "expected rows like | 1 | `mock-home-phone` | shipped | … |"
        ]
    ids = [row_id for row_id, _status in rows]
    if len(ids) != len(set(ids)):
        violations.append("§9 lists the same mockup id twice")
    status = dict(rows)

    on_pages: dict[str, list[str]] = {}
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        for figure_id in re.findall(r'<figure\b[^>]*\bid="(mock-[a-z0-9-]+)"', markup, re.I):
            on_pages.setdefault(figure_id, []).append(route)

    for figure_id, routes in sorted(on_pages.items()):
        if figure_id not in status:
            violations.append(
                f"mockup ‘{figure_id}’ is on {'/'.join(routes)} but has no row in the §9 backlog"
            )
        elif status[figure_id] != "shipped":
            violations.append(
                f"mockup ‘{figure_id}’ is on {'/'.join(routes)} but §9 marks it ‘{status[figure_id]}’"
            )
    for figure_id, state in sorted(status.items()):
        if state == "shipped" and figure_id not in on_pages:
            violations.append(
                f"§9 marks ‘{figure_id}’ shipped, but no built page carries that figure"
            )
        if state == "planned" and figure_id in on_pages:
            violations.append(
                f"§9 marks ‘{figure_id}’ planned, but it is already on {'/'.join(on_pages[figure_id])}"
            )
    if not any(state == "shipped" for state in status.values()):
        violations.append("§9 lists no shipped mockup, so the backlog proves nothing about the site")
    return violations


def copy_hygiene_violations(dist: pathlib.Path) -> list[str]:
    """Nothing may ship that a reader would see as a mistake.

    A literal backtick is a Markdown habit that leaked into the copy; an
    `&lt;code&gt;` is markup that was escaped instead of rendered (both shipped
    on the download page once — the first looked like a typo, the second showed
    a tag name to every visitor); `href="#"` is a link that goes nowhere; and a
    placeholder word is a page that promises content instead of carrying it.
    """
    violations: list[str] = []
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        text = text_of(markup)
        for match in ESCAPED_TAG.finditer(markup):
            violations.append(f"{route}: escaped markup is rendered as text: {match.group(0)}…")
        for match in DEAD_LINK.finditer(markup):
            violations.append(f"{route}: a link goes nowhere: {match.group(0)}")
        for match in PLACEHOLDER_WORDS.finditer(text):
            if NEGATION_CUES.search(_placeholder_clause(text, match.start())):
                continue
            violations.append(f"{route}: placeholder wording ‘{match.group(0)}’")
        for index, line in enumerate(text.split("`")[1::2], 1):
            snippet = line.strip()[:40]
            violations.append(f"{route}: literal backtick #{index} in the copy: …{snippet}…")
    return violations


# --------------------------------------------------------------------------
# The site is not a distribution channel.
# --------------------------------------------------------------------------

# The user's direction for this session: downloads are not the website's
# business. Made checkable rather than remembered, in two parts.
#
# 1. No page may link a release *asset*. A direct link to dhun-test.apk implies
#    "this build is for you, now"; the rolling test build is unverified and
#    replaced on every merge, so the site points at the release page instead,
#    where the warning and the files live together.
BINARY_LINK = re.compile(
    r'href\s*=\s*"[^"]*?(?:/releases/download/[^"]*|\.(?:apk|msi|aab|dmg|exe|deb|rpm|zip|tar\.gz|sha256))"',
    re.I,
)

# 2. No page may carry installation or verification instructions — checksum
#    commands, sideloading, signing-key archaeology. Those belong with the
#    artifact and its own README, and they rot as that page changes.
INSTALL_INSTRUCTION_TERMS = (
    re.compile(r"\bsha256sum\b", re.I),
    re.compile(r"\bshasum\b", re.I),
    re.compile(r"\bGet-FileHash\b", re.I),
    re.compile(r"\badb install\b", re.I),
    re.compile(r"\bsideload\w*\b", re.I),
    re.compile(r"\.sha256\b", re.I),
    re.compile(r"\bchecksum\w*\b", re.I),
)


def distribution_boundary_violations(dist: pathlib.Path) -> list[str]:
    """The site describes the software; it does not hand out or verify builds."""
    violations: list[str] = []
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        text = text_of(markup)
        for match in BINARY_LINK.finditer(markup):
            violations.append(f"{route}: links a downloadable artifact: {match.group(0)[:80]}")
        for pattern in INSTALL_INSTRUCTION_TERMS:
            for match in pattern.finditer(text):
                violations.append(
                    f"{route}: carries installation/verification instructions "
                    f"(‘{match.group(0)}’) — that belongs with the release, not here"
                )
    return violations


def claim_traceability_violations(dist: pathlib.Path) -> list[str]:
    """Every claim block cites the source it came from, in the built HTML.

    `.ai/WEBSITE_PLAN.md` Part A §4 asks for one promise per section with its
    ADR/PR written next to it, so a reviewer can trace a sentence to its source
    without leaving the page. This asserts it per `<section>` (a comment naming
    an ADR, a PR, a phase, a manifest, a roadmap file or a script), on every
    page — the previous version of the rule was a convention on the front page
    only, which is not a rule.
    """
    violations: list[str] = []
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        for index, section in enumerate(re.findall(r"<section\b[^>]*>(.*?)</section>", markup, re.S), 1):
            comments = re.findall(r"<!--(.*?)-->", section, re.S)
            if any(CITATION.search(comment) for comment in comments):
                continue
            heading = re.search(r"<(h1|h2|h3)\b[^>]*>(.*?)</\1>", section, re.S)
            title = re.sub(r"<[^>]+>", "", heading.group(2)).strip()[:60] if heading else "?"
            violations.append(
                f"{route}: section {index} (“{title}”) carries claims with no citation comment "
                f"(expected an ADR, PR, phase or file reference in an HTML comment)"
            )
    return violations


def stale_fact_violations(dist: pathlib.Path) -> list[str]:
    """No app version, release identity or date may be printed without a source.

    The site describes a project whose artifacts are replaced on every push to
    `main`. A version string or a date written into the copy is wrong within
    days and nothing would catch it, so the copy carries none: facts that could
    rot are either absent or derived from a checked source at build time. If a
    value is genuinely derivable, add it to the allowlist below with the source
    named in the comment — an unexplained entry is how this rule would stop
    meaning anything.
    """
    violations: list[str] = []
    allowed = STALE_FACT_ALLOWLIST
    for route, path in page_paths(dist).items():
        text = text_of(path.read_text(encoding="utf-8"))
        for pattern, label in STALE_FACT_PATTERNS:
            for match in pattern.finditer(text):
                if match.group(0) in allowed:
                    continue
                snippet = text[max(0, match.start() - 40) : match.end() + 40].strip()
                violations.append(f"{route}: {label} ‘{match.group(0)}’ in “…{snippet}…”")
    return violations


def _sitemap_locations(dist: pathlib.Path) -> set[str]:
    sitemap = dist / "sitemap.xml"
    if not sitemap.is_file():
        return set()
    return set(re.findall(r"<loc>([^<]+)</loc>", sitemap.read_text(encoding="utf-8")))


def mockup_violations(dist: pathlib.Path) -> list[str]:
    """Every mockup is labelled as a recreation, and carries its backlog id."""
    violations: list[str] = []
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        for match in re.finditer(
            r"<figure\b[^>]*\bclass=\"[^\"]*\bmock\b[^\"]*\"[^>]*>", markup, re.I
        ):
            tag = match.group(0)
            id_match = re.search(r'id="([^"]+)"', tag)
            rest = markup[match.end() :]
            figure_end = rest.find("</figure>")
            body = rest[:figure_end] if figure_end != -1 else rest
            caption_match = re.search(r"<figcaption\b[^>]*>(.*?)</figcaption>", body, re.S | re.I)
            if not id_match:
                violations.append(f"{route}: a mockup figure has no id (backlog mapping)")
                continue
            figure_id = id_match.group(1)
            backlog = {row_id for row_id, _status in backlog_rows()}
            if figure_id not in backlog:
                violations.append(
                    f"{route}: mockup id ‘{figure_id}’ is not in the screenshot backlog "
                    f"(.ai/WEBSITE_PLAN.md Part A §9)"
                )
            if not caption_match:
                violations.append(f"{route}: mockup ‘{figure_id}’ has no <figcaption>")
                continue
            caption = text_of(caption_match.group(1)).lower()
            if "not a screenshot" not in caption:
                violations.append(
                    f"{route}: mockup ‘{figure_id}’ is not labelled as a recreation "
                    f"(caption must say it is not a screenshot)"
                )
    return violations


def accessibility_violations(dist: pathlib.Path) -> list[str]:
    violations: list[str] = []
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        if not re.search(r'<html[^>]*\blang="[a-z]{2}', markup, re.I):
            violations.append(f"{route}: <html> has no language attribute")
        if len(re.findall(r"<h1\b", markup, re.I)) != 1:
            violations.append(f"{route}: expected exactly one <h1>")
        levels = [int(level) for level in re.findall(r"<h([1-6])\b", markup, re.I)]
        for previous, current in zip(levels, levels[1:]):
            if current > previous + 1:
                violations.append(
                    f"{route}: heading order jumps from h{previous} to h{current}"
                )
                break
        for landmark in ("<header", "<main", "<footer", "<nav"):
            if landmark not in markup.lower():
                violations.append(f"{route}: missing {landmark}> landmark")
        if 'class="skip-link"' not in markup:
            violations.append(f"{route}: missing skip link")
        if 'id="main"' not in markup:
            violations.append(f"{route}: skip link target #main is missing")
        if "prefers-reduced-motion" not in _compact(_read_css(dist).get("styles.css", "")):
            violations.append("styles.css: no prefers-reduced-motion rule")
        for match in re.finditer(r"<img\b[^>]*>", markup, re.I):
            if not re.search(r'\balt="', match.group(0)):
                violations.append(f"{route}: <img> without alt: {match.group(0)[:60]}")
        for match in re.finditer(r"<svg\b[^>]*>", markup, re.I):
            tag = match.group(0)
            if not (re.search(r'aria-hidden="true"', tag) or re.search(r'aria-label="', tag) or re.search(r"role=\"img\"", tag)):
                violations.append(f"{route}: <svg> is neither labelled nor aria-hidden")
    return violations


# Keys that must never appear in the site's structured data. Each one is a claim
# this repository cannot support: no stable release exists, so there is no
# version; the project has never collected a rating; and an `offers`/`price` or
# download URL would turn a description into an advertisement or a distribution
# channel (see `distribution_boundary_violations`).
STRUCTURED_DATA_BANNED = (
    "aggregateRating",
    "review",
    "ratingValue",
    "offers",
    "price",
    "softwareVersion",
    "datePublished",
    "dateModified",
    "downloadUrl",
    "installUrl",
    "fileSize",
)


def structured_data_violations(dist: pathlib.Path) -> list[str]:
    """One honest `SoftwareApplication` block per route, and nothing more.

    Structured data is the part of a page a machine repeats without a human
    reading the surrounding caveats, so it is where an over-claim travels
    furthest. The facts below are the ones the repository supports (free,
    GPL-3.0, Android 7.0+ and Windows, no sign-in); everything that would need a
    release, a rating or a store is asserted *absent* rather than merely
    unmentioned.
    """
    violations: list[str] = []
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        blocks = re.findall(
            r"<script\b[^>]*\btype=\"application/ld\+json\"[^>]*>(.*?)</script>",
            markup,
            re.I | re.S,
        )
        if len(blocks) != 1:
            violations.append(
                f"{route}: expected exactly one JSON-LD block, found {len(blocks)}"
            )
            continue
        try:
            data = json.loads(blocks[0].strip())
        except ValueError as error:
            violations.append(f"{route}: JSON-LD is not valid JSON ({error})")
            continue
        if data.get("@type") != "SoftwareApplication":
            violations.append(
                f"{route}: JSON-LD @type is {data.get('@type')!r}, expected 'SoftwareApplication'"
            )
        for key in STRUCTURED_DATA_BANNED:
            if key in data:
                violations.append(
                    f"{route}: JSON-LD claims '{key}', which this repository cannot "
                    f"support (no stable release, no ratings, no store listings)"
                )
        if data.get("isAccessibleForFree") is not True:
            violations.append(
                f"{route}: JSON-LD does not state isAccessibleForFree, and the app is GPL-3.0"
            )
        # The repository's own LICENSE file, which the footer links and
        # `footer_licence_violations` asserts — reused here rather than
        # re-spelled, so the two cannot disagree.
        expected_licence = f"{REPOSITORY_URL}/blob/main/LICENSE"
        if data.get("license") != expected_licence:
            violations.append(
                f"{route}: JSON-LD licence is {data.get('license')!r}, expected the "
                f"repository's own LICENSE file ({expected_licence})"
            )
        if data.get("url") != f"{CANONICAL_ORIGIN}/":
            violations.append(
                f"{route}: JSON-LD url is {data.get('url')!r}, expected the canonical origin"
            )
        if data.get("codeRepository") not in ALLOWED_EXTERNAL_ORIGINS:
            violations.append(f"{route}: JSON-LD codeRepository is not the repository itself")
    return violations


def metadata_violations(dist: pathlib.Path) -> list[str]:
    violations: list[str] = []
    for route in ROUTES:
        path = page_paths(dist).get(route)
        if path is None:
            continue
        markup = path.read_text(encoding="utf-8")
        expected = f"{CANONICAL_ORIGIN}{route}"
        canonical = re.search(r'<link[^>]*rel="canonical"[^>]*href="([^"]+)"', markup, re.I)
        if not canonical or canonical.group(1) != expected:
            violations.append(f"{route}: canonical URL should be {expected}")
        for name in ("description", "viewport", "color-scheme"):
            if not re.search(rf'<meta[^>]*name="{name}"', markup, re.I):
                violations.append(f"{route}: missing <meta name=\"{name}\">")
        for prop in ("og:title", "og:description", "og:url", "og:type"):
            if f'property="{prop}"' not in markup:
                violations.append(f"{route}: missing Open Graph {prop}")
        if 'name="twitter:card"' not in markup:
            violations.append(f"{route}: missing twitter:card")
        if '<link rel="icon"' not in markup:
            violations.append(f"{route}: missing favicon link")
        # The browser chrome takes its colour from these; both schemes are
        # asserted because a page with only the dark one shows the wrong bar in
        # a light-mode browser.
        for scheme, value in (("dark", "#161616"), ("light", "#F6F4F1")):
            needed = f'<meta name="theme-color" content="{value}" media="(prefers-color-scheme: {scheme})">'
            if needed not in markup:
                violations.append(
                    f"{route}: missing the {scheme}-scheme theme-color ({value})"
                )
        # og:url and the canonical URL are the same fact in two vocabularies; a
        # page whose canonical moved without its og:url following says two
        # different things about where it lives.
        og_url = re.search(r'<meta[^>]*property="og:url"[^>]*content="([^"]+)"', markup, re.I)
        if not og_url or og_url.group(1) != expected:
            violations.append(
                f"{route}: og:url should be {expected} (it is "
                f"{og_url.group(1) if og_url else 'absent'})"
            )
    return violations


# --------------------------------------------------------------------------
# One request per route.
# --------------------------------------------------------------------------

# The tab icon is inlined as a `data:` URI by `website/src/_data/favicon.js`, so
# a route is exactly one HTTP request — the document that carries everything.
# Lighthouse measured `requests=2` on the merged head (run 37808955045
# annotations) for exactly this reason. Both halves are asserted: the URI must
# decode back to the source SVG (folding the whitespace the encoder folds), and
# no page may keep the old file reference alive, which would silently restore
# the second request.
ICON_LINK = re.compile(r"<link\b[^>]*\brel=\"icon\"[^>]*>", re.I)
ICON_HREF = re.compile(r"\bhref=\"([^\"]+)\"", re.I)
DATA_SVG_ICON = re.compile(r"^data:image/svg\+xml;base64,([A-Za-z0-9+/]+={0,2})$")
FAVICON_SOURCE = SOURCE_DIR / "assets" / "dhun-favicon.svg"
REMOVED_ICON_PATH = "/assets/dhun-favicon.svg"


def _compact_svg(svg: str) -> str:
    """Fold the whitespace the encoder folds, so both sides compare equal.

    Only whitespace *between tags* is removed — the document has no text nodes,
    so no glyph spacing can change — and the encoder in `favicon.js` does the
    same thing. The two definitions are compared against each other by this
    rule, so a divergence is a red test rather than a guess.
    """
    return re.sub(r">\s+<", "><", svg).strip()


def favicon_violations(dist: pathlib.Path) -> list[str]:
    """The icon is inlined, it is the icon it claims to be, and it costs no request."""
    if not FAVICON_SOURCE.is_file():
        return [
            f"{FAVICON_SOURCE.relative_to(REPO_ROOT)} is missing, so the inlined "
            f"icon cannot be compared with its source"
        ]
    expected = _compact_svg(FAVICON_SOURCE.read_text(encoding="utf-8"))
    violations: list[str] = []
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        tags = ICON_LINK.findall(markup)
        if len(tags) != 1:
            violations.append(
                f"{route}: expected exactly one <link rel=\"icon\">, found {len(tags)}"
            )
            continue
        tag = tags[0]
        href = ICON_HREF.search(tag)
        if not href:
            violations.append(f"{route}: the icon link has no href: {tag[:60]}")
            continue
        value = href.group(1)
        match = DATA_SVG_ICON.match(value)
        if match is None:
            violations.append(
                f"{route}: the icon is not an inlined data: URI ({value[:60]}…) — "
                f"a separate file is a second HTTP request on every visit"
            )
        else:
            try:
                decoded = base64.b64decode(match.group(1), validate=True).decode("utf-8")
            except (ValueError, UnicodeDecodeError) as error:
                violations.append(f"{route}: the inlined icon is not base64 UTF-8: {error}")
            else:
                if decoded != expected:
                    violations.append(
                        f"{route}: the inlined icon has drifted from "
                        f"{FAVICON_SOURCE.name} ({len(decoded)} B decoded vs "
                        f"{len(expected)} B expected)"
                    )
            if 'type="image/svg+xml"' not in tag:
                violations.append(f"{route}: the inlined icon lost its type attribute: {tag[:60]}")
        if REMOVED_ICON_PATH in markup:
            violations.append(
                f"{route}: still references {REMOVED_ICON_PATH}; the icon must not be "
                f"fetched as a file as well as inlined"
            )
    return violations


def _referenced_paths(dist: pathlib.Path) -> set[str]:
    """Every site-root path a page (or robots.txt) names as a resource."""
    referenced: set[str] = set()
    for path in page_paths(dist).values():
        markup = path.read_text(encoding="utf-8")
        for match in re.finditer(r"\b(?:href|src)=\"([^\"]+)\"", markup, re.I):
            reference = match.group(1)
            if reference.startswith("/") and not reference.startswith("//"):
                referenced.add(reference.split("#", 1)[0].split("?", 1)[0])
        for match in re.finditer(r"url\((['\"]?)([^)'\"]+)\1\)", markup):
            reference = match.group(2)
            if reference.startswith("/") and not reference.startswith("//"):
                referenced.add(reference.split("#", 1)[0].split("?", 1)[0])
    robots = dist / "robots.txt"
    if robots.is_file():
        for match in re.finditer(r"^\s*Sitemap:\s*(\S+)", robots.read_text(encoding="utf-8"), re.M):
            reference = match.group(1)
            if reference.startswith(CANONICAL_ORIGIN):
                referenced.add("/" + reference[len(CANONICAL_ORIGIN) :].lstrip("/"))
    return referenced


def unreferenced_file_violations(dist: pathlib.Path) -> list[str]:
    """Nothing ships that no page names.

    This is the rule that keeps "one request per route" true over time: an
    asset copied into `dist/` but referenced by no page is either dead weight in
    the deploy or — if a page *should* have referenced it — a broken page. The
    three route documents, `404.html`, `robots.txt` and `sitemap.xml` are
    artifacts in their own right (the routes *are* what they deliver, the 404 is
    served by the host, the crawl files are the crawl contract), so they are not
    "referenced" by anything and are exempt.
    """
    referenced = _referenced_paths(dist)
    route_files = set(page_paths(dist).values())
    violations: list[str] = []
    for file in _files(dist):
        if file in route_files:
            continue
        relative = file.relative_to(dist).as_posix()
        if relative in ("robots.txt", "sitemap.xml"):
            continue
        if f"/{relative}" in referenced:
            continue
        violations.append(
            f"{relative} ships but no page references it — remove it, or reference it "
            f"from the page that needs it"
        )
    return violations


def _parse_xml_locations(content: str) -> list[str]:
    """`<loc>` values via the XML parser, not a regex.

    The crawlability rule compares sets of URLs with a regex, which is enough to
    answer "is this route listed" but says nothing about whether the document is
    *well-formed* — an unclosed `<url>` or a stray ampersand is a malformed
    sitemap that Search Console rejects, and a regex reads straight past it.
    """
    root = ElementTree.fromstring(content)
    namespace = "{http://www.sitemaps.org/schemas/sitemap/0.9}"
    return [(element.text or "").strip() for element in root.iter(f"{namespace}loc")]


def sitemap_violations(dist: pathlib.Path) -> list[str]:
    """`sitemap.xml` must be a well-formed sitemap, not XML-shaped text."""
    sitemap = dist / "sitemap.xml"
    if not sitemap.is_file():
        return ["sitemap.xml is missing from the built site"]
    content = sitemap.read_text(encoding="utf-8")
    try:
        locations = _parse_xml_locations(content)
    except ElementTree.ParseError as error:
        return [f"sitemap.xml is not well-formed XML: {error}"]
    violations: list[str] = []
    if not locations:
        violations.append("sitemap.xml has no <loc> entries")
    for location in locations:
        if not location.startswith(f"{CANONICAL_ORIGIN}/"):
            violations.append(
                f"sitemap.xml lists {location}, which is not under {CANONICAL_ORIGIN}/"
            )
    if len(locations) != len(set(locations)):
        duplicates = sorted({url for url in locations if locations.count(url) > 1})
        violations.append(f"sitemap.xml lists the same URL twice: {', '.join(duplicates)}")
    return violations


def asset_reference_violations(dist: pathlib.Path) -> list[str]:
    """Any attribute that names a file must name a file that exists.

    `link_violations` reads `href`/`src` on the four element types that carry
    them. This is the general case: *every* attribute whose value is a
    site-root path is resolved, so a `poster`, a `srcset` candidate or an
    attribute nobody has invented yet cannot point at a file the build does not
    ship. A `data:` URI is inlined bytes, not a file — `favicon_violations`
    reads those back out.
    """
    violations: list[str] = []
    attribute = re.compile(r"\b([a-z-]+)=\"(/[^\"]*)\"", re.I)
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        for match in attribute.finditer(markup):
            reference = match.group(2)
            if not _resolve_internal(dist, reference):
                violations.append(
                    f"{route}: {match.group(1)}=\"{reference}\" names a file the build "
                    f"does not ship"
                )
    return violations


# --------------------------------------------------------------------------
# CSS shape: the pruner (website/tools/prune-css.mjs) and its Python mirror
# --------------------------------------------------------------------------
#
# Every route inlines the modules its front matter declares, and the build then
# drops the rules that page cannot use (`website/tools/prune-css.mjs`). This
# section re-derives that decision from the sources so the committed build can
# be checked without Node: it parses both sides into units, prunes the source
# composition with the same predicate, and compares the two sequences. A rule
# that should have survived and is missing, a rule the page cannot use that
# still ships, a reordering, or a hand-edited declaration all show up as a
# difference — which is what the older, byte-equality version of this check
# proved as well, plus pruning-awareness.

CONDITIONAL_AT_RULES = ("@media", "@supports", "@container", "@layer")


def _skip_string(css: str, index: int) -> int:
    quote = css[index]
    cursor = index + 1
    while cursor < len(css):
        if css[cursor] == "\\":
            cursor += 2
        elif css[cursor] == quote:
            return cursor + 1
        else:
            cursor += 1
    return cursor


def _matching_brace(css: str, index: int) -> int:
    depth = 0
    cursor = index
    while cursor < len(css):
        character = css[cursor]
        if character in "\"'":
            cursor = _skip_string(css, cursor)
            continue
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return cursor
        cursor += 1
    return len(css)


def _is_conditional_at_rule(prelude: str) -> bool:
    head = prelude.strip().lower()
    return any(
        head.startswith(name) and head[len(name) : len(name) + 1] in (" ", "(", "")
        for name in CONDITIONAL_AT_RULES
    )


def css_units(css: str, start: int = 0) -> list[dict]:
    """Split a stylesheet into rules, conditional at-blocks and statements.

    Comments are assumed to be already removed (the minifier removes them, and
    `_fold_css` folds them); a body is kept as text, exactly as the pruner does.
    """
    units: list[dict] = []
    index = start
    item_start = start
    while index < len(css):
        character = css[index]
        if character in "\"'":
            index = _skip_string(css, index)
            continue
        if character == "{":
            prelude = css[item_start:index]
            close = _matching_brace(css, index)
            body = css[index + 1 : close]
            if prelude.lstrip().startswith("@") and _is_conditional_at_rule(prelude):
                units.append({"kind": "at", "prelude": prelude, "children": css_units(body)})
            else:
                units.append({"kind": "rule", "prelude": prelude, "body": body})
            index = close + 1
            item_start = index
            continue
        if character == ";":
            units.append({"kind": "statement", "text": css[item_start : index + 1]})
            index += 1
            item_start = index
            continue
        index += 1
    tail = css[item_start:]
    if tail.strip():
        units.append({"kind": "statement", "text": tail})
    return units


def _strip_negations(selector: str) -> str:
    """Remove the contents of `:not(...)`, whose classes are negative matches."""
    out = ""
    index = 0
    while True:
        at = selector.find(":not(", index)
        if at == -1:
            return out + selector[index:]
        out += selector[index:at]
        depth = 0
        cursor = at + 4
        while cursor < len(selector):
            if selector[cursor] == "(":
                depth += 1
            elif selector[cursor] == ")":
                depth -= 1
                if depth == 0:
                    break
            cursor += 1
        index = cursor + 1


def positive_classes(selector: str) -> set[str]:
    """Classes whose presence the selector requires (see the pruner's docstring)."""
    return set(re.findall(r"\.(-?[A-Za-z_][\w-]*)", _strip_negations(selector)))


def _split_selectors(prelude: str) -> list[str]:
    selectors: list[str] = []
    depth = 0
    start = 0
    for index, character in enumerate(prelude):
        if character == "(":
            depth += 1
        elif character == ")":
            depth -= 1
        elif character == "," and depth == 0:
            selectors.append(prelude[start:index])
            start = index + 1
    selectors.append(prelude[start:])
    return selectors


def _rule_can_match(prelude: str, used: set[str]) -> bool:
    for selector in _split_selectors(prelude):
        classes = positive_classes(selector)
        if not classes or classes & used:
            return True
    return False


def _kept_selectors(prelude: str, used: set[str]) -> list[str]:
    """The selectors of one rule that can match, in order."""
    return [
        selector
        for selector in _split_selectors(prelude)
        if not (positive_classes(selector) and not positive_classes(selector) & used)
    ]


def _prune_units(units: list[dict], used: set[str]) -> list[dict]:
    kept: list[dict] = []
    for unit in units:
        if unit["kind"] == "rule":
            selectors = _split_selectors(unit["prelude"])
            survivors = _kept_selectors(unit["prelude"], used)
            if not survivors:
                continue
            # The pruner rebuilds a partially pruned selector list as the
            # surviving selectors joined by a comma, and leaves a rule whose
            # every selector survives byte-for-byte. Mirroring both branches is
            # what lets the two implementations agree exactly.
            prelude = (
                unit["prelude"]
                if len(survivors) == len(selectors)
                else ",".join(selector.strip() for selector in survivors)
            )
            kept.append({"kind": "rule", "prelude": prelude, "body": unit["body"]})
        elif unit["kind"] == "at":
            children = _prune_units(unit["children"], used)
            if children:
                kept.append({"kind": "at", "prelude": unit["prelude"], "children": children})
        else:
            kept.append(unit)
    return kept


def _flatten_units(units: list[dict]) -> list[str]:
    """One folded string per unit, in document order — the comparison surface."""
    flat: list[str] = []
    for unit in units:
        if unit["kind"] == "rule":
            flat.append("rule:" + _fold_css(unit["prelude"] + "{" + unit["body"] + "}"))
        elif unit["kind"] == "at":
            flat.append("at:" + _fold_css(unit["prelude"]))
            flat.extend(_flatten_units(unit["children"]))
            flat.append("end")
        else:
            flat.append("statement:" + _fold_css(unit["text"]))
    return flat


def markup_without_style(path: pathlib.Path) -> str:
    """A page's markup with its stylesheet and scripts removed.

    What a rule *styles* is not evidence of what the page *draws*: `.mock .device`
    in the CSS must not count as a mockup on screen, or the print rule below could
    never see a page that stopped drawing one.
    """
    return re.sub(
        r"<(style|script)\b[^>]*>.*?</\1>",
        " ",
        path.read_text(encoding="utf-8"),
        flags=re.S | re.I,
    )


def classes_used_by_page(markup: str) -> set[str]:
    """The classes a built page uses, from `class` attributes only.

    `<style>` and `<script>` bodies are removed first: the stylesheet lists class
    *selectors*, and counting those would make every rule look used.
    """
    outside = re.sub(r"<(style|script)\b[^>]*>.*?</\1>", " ", markup, flags=re.S | re.I)
    used: set[str] = set()
    for value in re.findall(r"""\bclass\s*=\s*(?:"([^"]*)"|'([^']*)')""", outside):
        for name in (value[0] or value[1]).split():
            used.add(name)
    return used


def without_css_comments(css: str) -> str:
    """Comments removed, whitespace untouched.

    A comment can sit between a descendant combinator and its selector, or
    contain a brace; the minifier removes comments, so the comparison ignores
    them — but *whitespace must stay*, because folding it would glue
    `.site-nav ul` into a class name that no page has and every descendant rule
    would look prunable. That was a real bug in this check's first version.
    """
    return re.sub(r"/\*.*?\*/", "", css, flags=re.S)


def page_css_units(dist: pathlib.Path, route: str) -> list[dict]:
    """The units of the CSS that page actually ships."""
    return css_units(without_css_comments(page_stylesheet(dist, route)))


def prune_source_css(css: str, used: set[str]) -> str:
    """The pruner's decision, in Python: for tests and for `choose_modules`."""
    return "".join(_restore(_prune_units(css_units(without_css_comments(css)), used)))


def _restore(units: list[dict]) -> list[str]:
    out: list[str] = []
    for unit in units:
        if unit["kind"] == "rule":
            out.append(unit["prelude"] + "{" + unit["body"] + "}")
        elif unit["kind"] == "at":
            out.append(unit["prelude"] + "{" + "".join(_restore(unit["children"])) + "}")
        else:
            out.append(unit["text"])
    return out


PAGE_SOURCES = {
    "index.njk": "/",
    "features.njk": "/features/",
    "ui.njk": "/ui/",
    "404.njk": "/404.html",
}


def declared_modules(source_name: str) -> list[str]:
    """The CSS modules a page's front matter declares, in order."""
    source = SOURCE_DIR / source_name
    if not source.is_file():
        return []
    match = re.search(r"^cssModules:\s*\[([^\]]*)\]", source.read_text(encoding="utf-8"), re.M)
    if not match:
        return []
    return re.findall(r'"([a-z0-9-]+)"', match.group(1))


def dist_source_drift_violations(dist: pathlib.Path) -> list[str]:
    """The committed CSS must be the *pruned* composition of its sources.

    What this used to prove (until 2026-10-08): each page's inlined CSS equalled
    the byte-for-byte composition of the modules its front matter declares, so a
    hand-edit or a stale build was a red test.

    What it proves now: the same thing, plus the pruning step. The sources are
    parsed, pruned with the same predicate as `website/tools/prune-css.mjs`
    (a selector survives when it has no positive class, or at least one class the
    page uses), and compared with the built page unit by unit and in order — so a
    rule the page *can* use that went missing, a rule it cannot use that still
    ships, a reordering and a changed declaration are all differences. It runs
    without Node, in app CI as well as the site workflow, which is the point:
    app CI has to be able to tell a stale committed build from a fresh one.
    """
    violations: list[str] = []
    for source_name, route in PAGE_SOURCES.items():
        if not (SOURCE_DIR / source_name).is_file():
            violations.append(f"{source_name} is missing from the sources")
            continue
        modules = declared_modules(source_name)
        if not modules:
            violations.append(f"{source_name}: no cssModules declared")
            continue
        path = page_paths(dist).get(route)
        if path is None:
            violations.append(f"{route}: built page is missing")
            continue
        markup = path.read_text(encoding="utf-8")
        used = classes_used_by_page(markup)
        composed = "\n".join(
            (SOURCE_DIR.parent / "css" / f"{name}.css").read_text(encoding="utf-8").strip()
            for name in modules
        )
        expected = _flatten_units(
            _prune_units(css_units(without_css_comments(composed)), used)
        )
        built = _flatten_units(page_css_units(dist, route))
        if built == expected:
            continue
        detail = _first_difference(built, expected)
        violations.append(
            f"{route}: the committed CSS is not the pruned composition of "
            f"{', '.join(modules)} — {detail} "
            f"(built {len(built)} unit(s), expected {len(expected)}; "
            f"run `npm run build` in website/ and commit the result)"
        )
    return violations


def _first_difference(built: list[str], expected: list[str]) -> str:
    for index, (actual, wanted) in enumerate(zip(built, expected)):
        if actual != wanted:
            return (
                f"unit {index + 1} differs: built {actual[:70]!r}, "
                f"expected {wanted[:70]!r}"
            )
    if len(built) > len(expected):
        return f"the build carries {len(built) - len(expected)} extra unit(s), first: {built[len(expected)][:70]!r}"
    return f"the build is missing {len(expected) - len(built)} unit(s), first: {expected[len(built)][:70]!r}"


def _fold_css(css: str) -> str:
    """Fold every difference minification is allowed to introduce.

    Same normalisation `website/tools/verify-minify.mjs` applies — comments and
    whitespace removed, `;}` collapsed — so the two checks agree about what
    "unchanged" means. Comments go first: a comment removed by compaction would
    change a selector's bytes otherwise.
    """
    without_comments = re.sub(r"/\*.*?\*/", "", css, flags=re.S)
    return _compact(without_comments).replace(";}", "}")


def crawlability_violations(dist: pathlib.Path) -> list[str]:
    violations: list[str] = []
    robots = dist / "robots.txt"
    if not robots.is_file():
        violations.append("robots.txt is missing from the built site")
    else:
        content = robots.read_text(encoding="utf-8")
        if "Sitemap:" not in content:
            violations.append("robots.txt does not point at the sitemap")
    sitemap = dist / "sitemap.xml"
    if not sitemap.is_file():
        violations.append("sitemap.xml is missing from the built site")
    else:
        content = sitemap.read_text(encoding="utf-8")
        expected = {f"{CANONICAL_ORIGIN}{route}" for route in ROUTES}
        listed = _sitemap_locations(dist)
        for route in ROUTES:
            if f"<loc>{CANONICAL_ORIGIN}{route}</loc>" not in content:
                violations.append(f"sitemap.xml does not list {route}")
        for extra in sorted(listed - expected):
            violations.append(
                f"sitemap.xml lists {extra}, which is not one of the three routes "
                f"(/, /features/, /ui/)"
            )
        if "{{" in content or "{%" in content:
            violations.append("sitemap.xml still contains an unrendered template tag")
    if robots.is_file():
        other_urls = re.findall(r"^\s*(?:Allow|Disallow):\s*(\S+)", robots.read_text(encoding="utf-8"), re.M)
        for path in other_urls:
            if path not in ("/", ""):
                violations.append(f"robots.txt scopes {path}, but the site has exactly three routes")
    if not (dist / "404.html").is_file():
        violations.append("404.html is missing from the built site")
    return violations


def _at_rule_block(css: str, marker: str) -> str:
    """The body of the first at-rule whose prelude contains `marker`, or "".

    The earlier version of both rules below sliced a fixed number of characters
    after the marker and searched inside that — which runs past the end of the
    block into whatever rules follow it. A rule for an element *outside* the
    block could therefore satisfy a check about the block (a real false pass,
    found by mutation on 2026-10-08: deleting `.mock .device { display: none }`
    from the print block still passed because the mockup's own `.device` rule
    comes later in the same sheet). Brace matching reads the block and nothing
    else.
    """
    index = css.find(marker)
    if index == -1:
        return ""
    brace = css.find("{", index)
    if brace == -1:
        return ""
    return css[brace + 1 : _matching_brace(css, brace)]


def print_style_violations(dist: pathlib.Path) -> list[str]:
    """Every route carries a print block that makes paper legible.

    The site has no background colours on paper (browsers drop them) and a dark
    default palette, so a page without print rules prints white-on-white. The
    *effect* is measured in the browser job — caveats still rendered, print-media
    contrast still above the floors — and this rule keeps the block those
    measurements depend on from being deleted or from shipping on two routes out
    of three.

    Changed 2026-10-08, when the build started pruning each page's CSS against
    its own markup (`website/tools/prune-css.mjs`): the rule that the print block
    must hide the mockup drawing is now asserted only for a page that *has* a
    drawing (`class="device` in its markup). It used to be asserted for every
    route, which was vacuous on `/404.html` and became impossible once the
    unprunable-anywhere `.mock .device` rule stopped shipping there. The floor is
    the same where the effect exists: a printed drawing is a page of ink.
    """
    violations: list[str] = []
    marker = "@mediaprint"
    for route, path in page_paths(dist).items():
        compact = _compact(page_stylesheet(dist, route))
        if marker not in compact:
            violations.append(
                f"{route}: no `@media print` block ships in this page's own CSS, so "
                f"the printed sheet is white text on white paper"
            )
            continue
        block = _at_rule_block(compact, marker)
        markup = path.read_text(encoding="utf-8")
        for token in ("--text:", "--bg:"):
            if token not in block:
                violations.append(
                    f"{route}: the @media print block does not redefine {token.rstrip(':')} "
                    f"for paper"
                )
        # The page *draws* a mockup when its markup uses the class — checked
        # against class attributes, not against the string ".device", which is a
        # selector and (measured 2026-10-08) never matches markup at all. That
        # mistake made this rule unfailable, which is not a rule.
        draws_mockup = "device" in classes_used_by_page(markup)
        if draws_mockup and ".device" not in block:
            violations.append(
                f"{route}: the @media print block does not hide the decorative mockup "
                f"drawing, so printing spends a page of ink on a recreation"
            )
        # The paper palette is checked for the one thing that makes the block
        # worth having: the printed text must be readable against the printed
        # background. Without this, `--text:#ffffff` on a white `--bg` shipped
        # green here and could only be caught by a browser run — a mutation that
        # proved the rule was too shallow (2026-10-08), now computed locally with
        # the same WCAG maths the token rule uses.
        tokens = dict(re.findall(r"(--[a-z0-9-]+):([^;}]+)", block))
        if "--text" in tokens and "--bg" in tokens:
            foreground = _colour(tokens["--text"], tokens["--bg"])
            background = _colour(tokens["--bg"])
            if foreground is None or background is None:
                violations.append(
                    f"{route}: cannot resolve the print palette ({tokens['--text']} on "
                    f"{tokens['--bg']})"
                )
            else:
                lighter, darker = sorted(
                    (_relative_luminance(foreground), _relative_luminance(background)),
                    reverse=True,
                )
                ratio = (lighter + 0.05) / (darker + 0.05)
                if ratio < 4.5:
                    violations.append(
                        f"{route}: print --text on --bg is {ratio:.2f}:1, below the 4.5:1 "
                        f"floor — the sheet would be unreadable"
                    )
    return violations


def forced_colors_violations(dist: pathlib.Path) -> list[str]:
    """The site must say what it looks like when the OS picks the colours.

    `forced-colors: active` is Windows High Contrast. Chromium removes author
    backgrounds there, and an `<a>` styled as a button gets no control border
    (a real `<button>` does), so without an explicit rule the site's primary
    action renders as plain text. The *effect* is measured in a real browser —
    the `forced colors` check in `website/tests/browser.mjs`, whose decision
    logic is mutation-proven without a browser in `website/tests/rules.mjs` —
    and this rule keeps the mechanism it measures from being deleted.
    """
    marker = "@media(forced-colors:active)"
    violations: list[str] = []
    # Per route, not over the union of every page's CSS. The first version of
    # this rule read `site_stylesheet()`, which concatenates all three routes, so
    # deleting the block from one page still passed — a mutation that has to be
    # run to be believed, and it failed to fire (see the session's verification
    # record). A route ships its own <style>, so it is checked on its own.
    for route, path in page_paths(dist).items():
        compact = _compact(page_stylesheet(dist, route))
        if marker not in compact:
            violations.append(
                f"{route}: no `@media (forced-colors: active)` block ships in this "
                f"page's own CSS: Windows High Contrast would draw its buttons as "
                f"plain text"
            )
            continue
        block = _at_rule_block(compact, marker)
        markup = path.read_text(encoding="utf-8")
        if 'class="btn' in markup and ".btn" not in block:
            violations.append(
                f"{route}: @media (forced-colors: active) does not mention .btn, so "
                f"the control border the forced-colors check measures is not restored"
            )
    return violations


def responsive_violations(dist: pathlib.Path) -> list[str]:
    violations: list[str] = []
    css = _read_css(dist).get("styles.css", "")
    compact = _compact(css)
    for width in (480, 640, 768, 1024):
        if f"@media(min-width:{width}px)" not in compact:
            violations.append(f"styles.css: no breakpoint at {width}px")
    if "clamp(" not in css:
        violations.append("styles.css: fluid type via clamp() is not used")
    if "--target:44px" not in compact:
        violations.append("styles.css: the 44px touch-target token is missing")
    for page in page_paths(dist).values():
        markup = page.read_text(encoding="utf-8")
        if 'content="width=device-width' not in markup:
            violations.append(f"{page.name}: viewport meta does not set width=device-width")
    # A bare fixed width above 320px would break the smallest supported screen.
    for match in re.finditer(r"(?<!max-)(?<!min-)\bwidth:\s*(\d{3,})px", css):
        if int(match.group(1)) > 320:
            violations.append(
                f"styles.css: fixed width:{match.group(1)}px can overflow a 320px screen"
            )
    if "100vw" in css:
        violations.append("styles.css: 100vw usage risks horizontal overflow")
    return violations


def _parse_tokens(css: str) -> dict[str, str]:
    block = re.search(r":root\s*\{(.*?)\}", css, re.S)
    tokens: dict[str, str] = {}
    if block:
        for name, value in re.findall(r"(--[a-z0-9-]+)\s*:\s*([^;]+);", block.group(1)):
            tokens[name] = value.strip()
    return tokens


def _relative_luminance(rgb: tuple[float, float, float]) -> float:
    def channel(value: float) -> float:
        value /= 255.0
        return value / 12.92 if value <= 0.03928 else ((value + 0.055) / 1.055) ** 2.4

    r, g, b = (channel(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def _colour(value: str, background: str | None = None) -> tuple[float, float, float] | None:
    """Resolve #rgb/#rrggbb, rgb()/rgba() and `transparent` against a backdrop."""
    value = value.strip()
    hex_match = re.fullmatch(r"#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})", value)
    if hex_match:
        digits = hex_match.group(1)
        if len(digits) == 3:
            digits = "".join(ch * 2 for ch in digits)
        return tuple(float(int(digits[i : i + 2], 16)) for i in (0, 2, 4))
    rgb_match = re.fullmatch(
        r"rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)",
        value,
    )
    if not rgb_match:
        return None
    red, green, blue = (float(rgb_match.group(i)) for i in (1, 2, 3))
    alpha = float(rgb_match.group(4)) if rgb_match.group(4) is not None else 1.0
    if alpha < 1.0 and background is not None:
        backdrop = _colour(background) or (0.0, 0.0, 0.0)
        red = red * alpha + backdrop[0] * (1 - alpha)
        green = green * alpha + backdrop[1] * (1 - alpha)
        blue = blue * alpha + backdrop[2] * (1 - alpha)
    return (red, green, blue)


def contrast_violations(dist: pathlib.Path) -> list[str]:
    violations: list[str] = []
    css = _read_css(dist).get("styles.css", "")
    tokens = _parse_tokens(css)
    if not tokens:
        return ["styles.css: no :root token block found to check contrast"]
    for foreground, background, minimum in CONTRAST_PAIRS:
        if foreground not in tokens or background not in tokens:
            violations.append(f"styles.css: token {foreground} or {background} is missing")
            continue
        fg = _colour(tokens[foreground], tokens[background])
        bg = _colour(tokens[background])
        if fg is None or bg is None:
            violations.append(
                f"styles.css: cannot resolve {foreground} ({tokens[foreground]}) "
                f"or {background} ({tokens[background]})"
            )
            continue
        lighter, darker = sorted((_relative_luminance(fg), _relative_luminance(bg)), reverse=True)
        ratio = (lighter + 0.05) / (darker + 0.05)
        if ratio < minimum:
            violations.append(
                f"styles.css: {foreground} on {background} is {ratio:.2f}:1, "
                f"below the {minimum}:1 requirement"
            )
    return violations


def footer_licence_violations(dist: pathlib.Path) -> list[str]:
    violations: list[str] = []
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        if "GNU General Public License" not in html.unescape(markup):
            violations.append(f"{route}: footer does not state the GPL-3.0 licence")
        for needed in ("/blob/main/LICENSE", "/blob/main/THIRD_PARTY.md"):
            if needed not in markup:
                violations.append(f"{route}: footer does not link {needed}")
    return violations


def css_coverage_violations(dist: pathlib.Path) -> list[str]:
    """Every class a page uses must be defined in the CSS that page ships.

    This is the safety net for the module split (eleventy.config.js): a route
    declares its modules in front matter, and if a page starts using a class
    from a module it does not carry — or a rule is moved between modules and
    missed — the page would render unstyled with no other check noticing. It
    also catches a class that is only defined but never used anywhere, because
    each page is checked against its own sheet.
    """
    violations: list[str] = []
    defined_by_route = {
        route: set(re.findall(r"\.([a-zA-Z_][\w-]*)", page_stylesheet(dist, route)))
        for route in page_paths(dist)
    }
    used_anywhere: set[str] = set()
    for route, path in page_paths(dist).items():
        markup = re.sub(r"<!--.*?-->", " ", path.read_text(encoding="utf-8"), flags=re.S)
        for value in re.findall(r'\bclass="([^"]*)"', markup):
            used_anywhere.update(value.split())
    # The other direction: a rule no page uses is bytes shipped for nothing.
    # This is what "prune dead CSS" means as a rule rather than a one-off edit.
    dead = sorted(set().union(*defined_by_route.values()) - used_anywhere if defined_by_route else [])
    if dead:
        violations.append(
            f"CSS defines {len(dead)} class(es) no built page uses (dead CSS): {', '.join(dead[:8])}"
        )

    for route, path in page_paths(dist).items():
        markup = re.sub(r"<!--.*?-->", " ", path.read_text(encoding="utf-8"), flags=re.S)
        used: set[str] = set()
        for value in re.findall(r'\bclass="([^"]*)"', markup):
            used.update(value.split())
        missing = sorted(used - defined_by_route[route])
        if missing:
            violations.append(
                f"{route}: {len(missing)} class(es) used but not defined in this page's CSS "
                f"(add the module to its cssModules front matter): {', '.join(missing[:8])}"
            )
    return violations


def sprite_violations(dist: pathlib.Path) -> list[str]:
    """Icons are `<use>` references into a per-page `<symbol>` sprite.

    The sprite is assembled by an Eleventy transform, so it can be wrong in two
    ways that are invisible to every other check: a reference with no definition
    (an icon that renders as nothing) and a definition with no reference (bytes
    shipped for no reason). Both directions are asserted, per page.
    """
    violations: list[str] = []
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        used = set(re.findall(r'<use\b[^>]*\bhref="#i-([^"]+)"', markup, re.I))
        defined = set(re.findall(r'<symbol\b[^>]*\bid="i-([^"]+)"', markup, re.I))
        for name in sorted(used - defined):
            violations.append(f"{route}: <use> points at icon ‘{name}’, which this page does not define")
        for name in sorted(defined - used):
            violations.append(f"{route}: icon ‘{name}’ is defined in the sprite but never used")
        if defined and '<svg class="sprite"' not in markup:
            violations.append(f"{route}: icon symbols are present without the hidden sprite container")
        for match in re.finditer(r"<use\b([^>]*)>", markup, re.I):
            if not re.search(r'\bhref="#i-', match.group(1)):
                violations.append(f"{route}: <use> without an internal #i- reference: {match.group(0)[:60]}")
    return violations


def _baseline_path() -> pathlib.Path:
    return REPO_ROOT / "website" / "budget-baseline.json"


def budget_ratchet_violations(
    dist: pathlib.Path, baseline_path: pathlib.Path | None = None
) -> list[str]:
    """The weight budget, ratcheted against the committed baseline.

    The absolute budget (60 KB HTML+CSS per route) is generous enough that a
    content edit could double a page and still pass. Page sizes here are
    deterministic — same input, same bytes, on any machine — so growth can be
    policed exactly: a route may not exceed its committed baseline by more than
    RATCHET_TOLERANCE. Legitimate growth is a deliberate act: regenerate the
    baseline (python3 scripts/website_quality.py --write-baseline) so the diff
    shows the new number and a reviewer sees what changed.

    A missing or unreadable baseline is a violation, not a silent pass: a
    ratchet nobody can fail is decoration.
    """
    baseline_path = baseline_path or _baseline_path()
    if not baseline_path.is_file():
        return [
            f"budget baseline {baseline_path} is missing — regenerate it with "
            f"python3 scripts/website_quality.py --write-baseline"
        ]
    try:
        baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    except ValueError as error:
        return [f"budget baseline {baseline_path} is not valid JSON: {error}"]

    violations: list[str] = []
    routes = baseline.get("routes")
    if not isinstance(routes, dict) or not routes:
        return [f"budget baseline {baseline_path} has no routes table"]

    pages = page_paths(dist)
    for route, entry in routes.items():
        path = pages.get(route)
        if path is None:
            violations.append(f"{route}: budgeted route is missing from the built site")
            continue
        recorded = int(entry["htmlCss"])
        measured = page_weight_bytes(dist, route, path)
        limit = int(recorded * (1 + RATCHET_TOLERANCE))
        if measured > limit:
            violations.append(
                f"{route}: HTML+CSS grew from the committed {recorded} B baseline to "
                f"{measured} B (+{measured - recorded} B, {100 * (measured - recorded) / recorded:.1f}%), "
                f"over the {int(RATCHET_TOLERANCE * 100)}% ratchet. Regenerate the baseline if this "
                f"growth is intended."
            )
    for route in ROUTES:
        if route not in routes:
            violations.append(f"{route}: route is not in the budget baseline")
    return violations


def write_baseline(dist: pathlib.Path, baseline_path: pathlib.Path | None = None) -> pathlib.Path:
    """Record the current per-route weight as the ratchet's reference point."""
    baseline_path = baseline_path or _baseline_path()
    pages = page_paths(dist)
    document = {
        "_comment": (
            "Committed page weights (uncompressed bytes on the wire) so that growth is "
            "deliberate. Written by scripts/website_quality.py --write-baseline; asserted by "
            "budget_ratchet_violations() in the same module."
        ),
        "tolerance": RATCHET_TOLERANCE,
        "routes": {
            route: {
                "htmlCss": page_weight_bytes(dist, route, pages[route]),
                "html": pages[route].stat().st_size,
                "cssInlined": len(
                    "".join(STYLE_BLOCK.findall(pages[route].read_text(encoding="utf-8")))
                ),
                "cssLinked": sum(f.stat().st_size for f in linked_stylesheets(dist, route)),
            }
            for route in ROUTES
            if route in pages
        },
    }
    baseline_path.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    return baseline_path


CHECKS = (
    ("page weight budget", weight_violations),
    ("no client-side JavaScript", javascript_violations),
    ("internal links resolve", link_violations),
    ("mockups are labelled", mockup_violations),
    ("icon is inlined and true to its source", favicon_violations),
    ("nothing ships unreferenced", unreferenced_file_violations),
    ("accessibility floor", accessibility_violations),
    ("page metadata", metadata_violations),
    ("structured data", structured_data_violations),
    ("crawlability", crawlability_violations),
    ("sitemap well-formedness", sitemap_violations),
    ("referenced files exist", asset_reference_violations),
    ("dist matches its sources", dist_source_drift_violations),
    ("responsive rules", responsive_violations),
    ("print stylesheet", print_style_violations),
    ("forced-colours fallback", forced_colors_violations),
    ("token contrast", contrast_violations),
    ("licence notice", footer_licence_violations),
    ("class coverage", css_coverage_violations),
    ("icon sprite integrity", sprite_violations),
    ("page-weight ratchet", budget_ratchet_violations),
    ("claim traceability", claim_traceability_violations),
    ("backlog ↔ site drift", backlog_drift_violations),
    ("no stale facts", stale_fact_violations),
    ("copy hygiene", copy_hygiene_violations),
    ("distribution boundary", distribution_boundary_violations),
)


def run_checks(dist: pathlib.Path = DEFAULT_DIST) -> list[str]:
    dist = pathlib.Path(dist)
    if not dist.is_dir():
        return [f"built site directory {dist} does not exist"]
    violations: list[str] = []
    for _name, check in CHECKS:
        violations.extend(check(dist))
    return violations


def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    if "--write-baseline" in argv:
        argv = [name for name in argv if name != "--write-baseline"]
        dist = pathlib.Path(argv[0]) if argv else DEFAULT_DIST
        written = write_baseline(dist)
        print(f"wrote {written} from {dist}")
        return 0
    dist = pathlib.Path(argv[0]) if argv else DEFAULT_DIST
    violations = run_checks(dist)
    if violations:
        print(f"FAIL: {len(violations)} quality violation(s):", file=sys.stderr)
        for violation in violations:
            print(f"  - {violation}", file=sys.stderr)
        return 1
    print(f"OK: {len(CHECKS)} quality checks pass on {dist}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
