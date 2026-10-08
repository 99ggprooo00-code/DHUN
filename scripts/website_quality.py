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
real browser (Lighthouse/axe) run in `.github/workflows/website.yml`.

Run directly for a report:  python3 scripts/website_quality.py [dist-dir]
"""

from __future__ import annotations

import html
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

CANONICAL_ORIGIN = "https://99ggprooo00-code.github.io/DHUN"
ALLOWED_EXTERNAL_ORIGINS = (
    "https://github.com/99ggprooo00-code/DHUN",
    "https://99ggprooo00-code.github.io/DHUN",
)
ROUTES = ("/", "/features/", "/download/")
MOCKUP_IDS = (
    "mock-home-phone",
    "mock-player-phone",
    "mock-downloads-phone",
    "mock-desktop-window",
    # Planned captures that have no mockup yet — listed so the backlog and the
    # markup stay in step (see .ai/WEBSITE_PLAN.md Part A §9).
    "mock-widget",
    "mock-lyrics",
)

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


def _read_css(dist: pathlib.Path) -> dict[str, str]:
    return {
        p.name: p.read_text(encoding="utf-8")
        for p in (dist / "assets").glob("*.css")
        if p.is_file()
    } if (dist / "assets").is_dir() else {}


# --------------------------------------------------------------------------
# checks
# --------------------------------------------------------------------------


def weight_violations(dist: pathlib.Path) -> list[str]:
    violations: list[str] = []
    css = _read_css(dist)
    css_bytes = sum(len(v.encode("utf-8")) for v in css.values())
    pages = page_paths(dist)

    for route in ROUTES:
        path = pages.get(route)
        if path is None:
            violations.append(f"{route}: built page is missing")
            continue
        html_bytes = path.stat().st_size
        total = html_bytes + css_bytes
        if total > HTML_CSS_BUDGET:
            violations.append(
                f"{route}: HTML+CSS is {total} B, over the {HTML_CSS_BUDGET} B budget "
                f"(HTML {html_bytes} B + CSS {css_bytes} B)"
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
            if figure_id not in MOCKUP_IDS:
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
        for route in ROUTES:
            if f"<loc>{CANONICAL_ORIGIN}{route}</loc>" not in content:
                violations.append(f"sitemap.xml does not list {route}")
        if "{{" in content or "{%" in content:
            violations.append("sitemap.xml still contains an unrendered template tag")
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
