#!/usr/bin/env python3
"""Positive product/status contracts across DHUN's current claim surfaces.

Not a provider-name ban, not a legal reviewer and not a playback test. PRD §1
owns intent; code/dated verification owns behaviour. Historical records, source
identifiers and fixtures are intentionally not rewritten to satisfy marketing.
Existing CI auto-discovers test_product_claims.py; website_claims also calls the
built-page contract, so source and public-output checks cannot substitute for
each other. No network, Node or JVM needed.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ROUTES = {"/": "index.html", "/features/": "features/index.html", "/ui/": "ui/index.html", "/404.html": "404.html"}
PRIMARY = (
    "README.md", "docs/PRD.md", "legal/about.md", "legal/terms.md",
    "app-web/src/js/data/web-legal.js", "website/src/_data/site.js",
)
INSTRUCTIONS = (
    ".ai/MASTER_PROMPT.md", ".ai/README.md", ".ai/ROADMAP.md",
    ".ai/HANDOFF_NEXT_SESSION.md", ".ai/AUTONOMY_PROMPT.md", ".ai/WEBSITE_PLAN.md",
    "docs/web-player/PRD.md", "docs/web-player/README.md", "app-web/README.md",
)
GATES = ("technical", "account", "policy", "legal", "playback", "privacy", "cost")


def normalise(text: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[*`\\]", "", text)).strip()


def direction_problems(text: str) -> list[str]:
    """Semantic anchors, allowing rewrites/formatting and real provider names."""
    text = normalise(text)
    requirements = (
        ("multi-source identity", r"\bmulti[- ]source\b"),
        ("free/open-source identity", r"\bfree\b.*\bopen[- ]source\b"),
        ("cross-platform identity", r"\bcross[- ]platform\b"),
        ("YouTube first priority", r"YouTube.{0,80}\bfirst\b.{0,40}\bpriority\b"),
        ("Spotify explicitly planned", r"Spotify.{0,100}\bplanned\b"),
        ("Spotify not implemented", r"Spotify.{0,130}\bnot implemented\b"),
        ("native YouTube scope", r"\bnative\b.{0,240}YouTube"),
        ("browser JioSaavn scope", r"\bbrowser\b.{0,180}JioSaavn"),
    )
    problems = [label for label, pattern in requirements if not re.search(pattern, text, re.I)]
    if not all(re.search(rf"\b{gate}\b", text, re.I) for gate in GATES):
        problems.append("Spotify feasibility gates (not Premium alone)")
    return problems


def source_problems(documents: dict[str, str]) -> list[str]:
    problems = []
    for name in PRIMARY:
        for label in direction_problems(documents.get(name, "")):
            problems.append(f"{name}: missing {label}")
    for name in INSTRUCTIONS:
        text = documents.get(name, "")
        if not re.search(r"PRD\.md", text) or "multi-source" not in text.lower():
            problems.append(f"{name}: read/link PRD §1, not an independent single-source vision")
    privacy = normalise(documents.get("legal/privacy.md", ""))
    for phrase in ("visitor", "startup log", "whole tracks", "lyrics", "Spotify", "no-store", "settings", "shell"):
        if phrase.lower() not in privacy.lower():
            problems.append(f"legal/privacy.md: missing implemented data-flow disclosure: {phrase}")
    # Keep native and browser corpora distinct; generated parity is not web policy.
    build = documents.get("app-web/tools/build.mjs", "")
    if "legal-content.js" not in build:
        problems.append("app-web/tools/build.mjs: native legal parity fixture must remain excluded")
    return problems


def website_direction_problems(pages: dict[str, str]) -> list[str]:
    """Every actual page must display the same scoped direction and legal links."""
    problems = []
    for route in ROUTES:
        markup = pages.get(route, "")
        block = re.search(r"<p\b[^>]*\bdata-product-direction(?:=[^>]*)?[^>]*>(.*?)</p>", markup, re.S | re.I)
        if not block:
            problems.append(f"{route}: missing visible product/provider direction block")
            continue
        for label in direction_problems(re.sub(r"<[^>]+>", " ", block.group(1))):
            problems.append(f"{route}: product direction missing {label}")
        for label in ("Native Terms draft", "Native Privacy draft", "Browser Terms draft", "Browser Privacy draft"):
            if label not in markup:
                problems.append(f"{route}: missing labelled legal source link: {label}")
        structured = re.search(r'<script\b[^>]*type="application/ld\+json"[^>]*>(.*?)</script>', markup, re.S)
        try:
            data = json.loads(structured.group(1)) if structured else {}
        except json.JSONDecodeError:
            data = {}
        if "multi-source" not in data.get("description", "").lower():
            problems.append(f"{route}: JSON-LD description loses multi-source direction")
    front = pages.get("/", "")
    for pattern, label in (
        (r"<title>(.*?)</title>", "title"),
        (r'<meta name="description" content="([^"]+)"', "description"),
        (r'<meta property="og:description" content="([^"]+)"', "Open Graph description"),
        (r'<meta name="twitter:description" content="([^"]+)"', "Twitter description"),
    ):
        value = re.search(pattern, front, re.S | re.I)
        if not value or "multi-source" not in value.group(1).lower():
            problems.append(f"/: {label} loses multi-source identity")
    return problems


def repository_problems(root: Path = ROOT) -> list[str]:
    files = set(PRIMARY + INSTRUCTIONS + ("legal/privacy.md", "app-web/tools/build.mjs"))
    documents = {name: (root / name).read_text(encoding="utf-8") for name in files if (root / name).is_file()}
    pages = {route: (root / "website/dist" / name).read_text(encoding="utf-8") for route, name in ROUTES.items() if (root / "website/dist" / name).is_file()}
    return source_problems(documents) + website_direction_problems(pages)


def main() -> int:
    problems = repository_problems()
    if problems:
        print("FAIL: product/status alignment:")
        print("\n".join(f"  - {problem}" for problem in problems))
        return 1
    print("OK: product direction, platform/provider status, privacy anchors and all four public pages agree.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
