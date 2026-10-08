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

import html
import json
import pathlib
import re
import sys

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

# What counts as "this claim came from somewhere" inside an HTML comment.
CITATION = re.compile(
    r"(ADR-\d+|PR #\d+|Phase \d+|MASTER_PROMPT|README\.md|LICENSE|THIRD_PARTY|"
    r"\.ai/[A-Za-z_]+\.md|scripts/[a-z_]+\.py|AndroidManifest|app-android|minSdk|"
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
ALLOWED_EXTERNAL_ORIGINS = (
    "https://github.com/99ggprooo00-code/DHUN",
    "https://99ggprooo00-code.github.io/DHUN",
)
ROUTES = ("/", "/features/", "/download/")
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
        "/download/": dist / "download" / "index.html",
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
    """The site ships no client-side JavaScript at all."""
    violations: list[str] = []
    scripts = [p for p in _files(dist) if p.suffix in {".js", ".mjs", ".cjs"}]
    for path in scripts:
        violations.append(f"{path.relative_to(dist)}: the site ships no client-side JavaScript")
    for route, path in page_paths(dist).items():
        markup = path.read_text(encoding="utf-8")
        for match in re.finditer(r"<script\b[^>]*>", markup, re.I):
            violations.append(f"{route}: inline <script> tag found: {match.group(0)[:70]}")
    return violations


def _resolve_internal(dist: pathlib.Path, reference: str) -> bool:
    if reference.startswith("#") or reference.startswith("mailto:"):
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
    return violations


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
                f"(/ , /features/, /download/)"
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
    ("accessibility floor", accessibility_violations),
    ("page metadata", metadata_violations),
    ("crawlability", crawlability_violations),
    ("responsive rules", responsive_violations),
    ("token contrast", contrast_violations),
    ("licence notice", footer_licence_violations),
    ("class coverage", css_coverage_violations),
    ("icon sprite integrity", sprite_violations),
    ("page-weight ratchet", budget_ratchet_violations),
    ("claim traceability", claim_traceability_violations),
    ("backlog ↔ site drift", backlog_drift_violations),
    ("no stale facts", stale_fact_violations),
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
