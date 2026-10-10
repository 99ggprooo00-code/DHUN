#!/usr/bin/env python3
"""Single source → every legal surface.

There is exactly one hand-edited copy of each legal page: a Markdown file in
``legal/`` with a small front-matter block. Everything else is generated from it:

* ``shared/src/commonMain/kotlin/dev/dhun/legal/LegalContent.kt`` — the bundled
  copy the Android and Windows (Compose Desktop) UIs render. It is compiled in,
  so the pages work with no network and no resource plumbing.
* ``app-web/src/js/data/legal-content.js`` — the bundled copy the web SPA
  renders. Same documents, same bytes.

Two kinds of substitution happen here:

* **generation time** — ``{{appTitle}}``, ``{{appTitleDevanagari}}``,
  ``{{effectiveDate}}`` and ``{{include: PATH}}`` (a whole file, spliced in, so
  ``THIRD_PARTY.md``, ``SECURITY.md`` and ``LICENSE`` are never copied by hand).
* **render time** — ``{{appVersion}}``, ``{{appVersionCode}}``,
  ``{{releaseChannel}}`` and ``{{platform}}`` survive into the output. They are
  filled in by the UI from *this build's* metadata, so no version string is ever
  typed into a document.

Each document carries the SHA-256 of its rendered Markdown. ``legal_sha256`` is
the one value the app, the web SPA and ``scripts/test_legal_content.py`` all
compare, which is what stops the three targets from silently diverging.

Usage::

    python3 scripts/gen_legal_content.py           # rewrite the generated files
    python3 scripts/gen_legal_content.py --check   # exit 1 if they are stale
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
LEGAL_DIR = REPO / "legal"

KOTLIN_OUT = REPO / "shared" / "src" / "commonMain" / "kotlin" / "dev" / "dhun" / "legal" / "LegalContent.kt"
WEB_OUT = REPO / "app-web" / "src" / "js" / "data" / "legal-content.js"

APP_TITLE = "DHUN"
APP_TITLE_DEVANAGARI = "धुन"

#: Page order. It is also the order the Settings → About & Legal list renders.
PAGE_ORDER: tuple[str, ...] = (
    "about",
    "privacy",
    "terms",
    "open-source-licenses",
    "third-party-notices",
    "support",
    "security-reporting",
)

#: Tokens the *UI* fills in from build metadata. Left verbatim in the output.
RUNTIME_TOKENS: tuple[str, ...] = (
    "appVersion",
    "appVersionCode",
    "releaseChannel",
    "platform",
)

GENERATION_TOKENS: dict[str, str] = {
    "appTitle": APP_TITLE,
    "appTitleDevanagari": APP_TITLE_DEVANAGARI,
}

INCLUDE_RE = re.compile(r"^\{\{include:\s*(?P<path>[^}]+?)\s*\}\}\s*$")
TOKEN_RE = re.compile(r"\{\{(?P<name>[a-zA-Z][a-zA-Z0-9]*)\}\}")


@dataclass(frozen=True)
class LegalPage:
    id: str
    title: str
    effective_date: str
    status: str
    markdown: str

    @property
    def sha256(self) -> str:
        return hashlib.sha256(self.markdown.encode("utf-8")).hexdigest()


class LegalSourceError(RuntimeError):
    """The canonical source is malformed. Fail loudly rather than ship a gap."""


def _read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:  # pragma: no cover - depends on the filesystem
        raise LegalSourceError(f"cannot read {path}: {exc}") from exc


def split_front_matter(raw: str, origin: Path) -> tuple[dict[str, str], str]:
    """Parse the leading ``---`` block. Required, because the date is required."""
    lines = raw.split("\n")
    if not lines or lines[0].strip() != "---":
        raise LegalSourceError(f"{origin}: missing front matter (expected a leading '---' line)")
    try:
        end = next(i for i in range(1, len(lines)) if lines[i].strip() == "---")
    except StopIteration as exc:
        raise LegalSourceError(f"{origin}: front matter is never closed") from exc

    meta: dict[str, str] = {}
    for line in lines[1:end]:
        if not line.strip():
            continue
        if ":" not in line:
            raise LegalSourceError(f"{origin}: bad front-matter line: {line!r}")
        key, _, value = line.partition(":")
        meta[key.strip()] = value.strip()

    for required in ("id", "title", "effectiveDate", "status"):
        if not meta.get(required):
            raise LegalSourceError(f"{origin}: front matter is missing '{required}'")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", meta["effectiveDate"]):
        raise LegalSourceError(f"{origin}: effectiveDate must be YYYY-MM-DD, got {meta['effectiveDate']!r}")
    return meta, "\n".join(lines[end + 1 :]).strip("\n")


def resolve_includes(body: str, origin: Path, seen: frozenset[Path] = frozenset()) -> str:
    """Splice ``{{include: PATH}}`` lines, recursively, with cycle detection.

    Recursion matters because an included file is allowed to include another;
    the cycle check is what stops a self-include from looping forever instead of
    just happening to terminate today.
    """
    out: list[str] = []
    for line in body.split("\n"):
        match = INCLUDE_RE.match(line.strip())
        if not match:
            out.append(line)
            continue
        target = (REPO / match.group("path")).resolve()
        if not target.is_file():
            raise LegalSourceError(f"{origin}: include target does not exist: {match.group('path')}")
        if target in seen:
            raise LegalSourceError(f"{origin}: include cycle at {match.group('path')}")
        nested = resolve_includes(_read(target).strip("\n"), target, seen | {target})
        out.append(nested)
    return "\n".join(out)


def substitute_generation_tokens(body: str, origin: Path, effective_date: str) -> str:
    table = dict(GENERATION_TOKENS, effectiveDate=effective_date)

    def replace(match: re.Match[str]) -> str:
        name = match.group("name")
        if name in table:
            return table[name]
        if name in RUNTIME_TOKENS:
            return match.group(0)
        raise LegalSourceError(f"{origin}: unknown token {{{{{name}}}}}")

    return TOKEN_RE.sub(replace, body)


def load_pages() -> list[LegalPage]:
    """Every canonical page, in [PAGE_ORDER]. A missing file is an error."""
    found = {path.stem: path for path in sorted(LEGAL_DIR.glob("*.md"))}
    unknown = sorted(set(found) - set(PAGE_ORDER))
    if unknown:
        raise LegalSourceError(
            f"legal/ holds {', '.join(unknown)}, which no page order names. "
            "Add it to PAGE_ORDER or remove the file — a page that ships nowhere is a gap."
        )
    missing = [name for name in PAGE_ORDER if name not in found]
    if missing:
        raise LegalSourceError(f"legal/ is missing: {', '.join(missing)}")

    pages: list[LegalPage] = []
    for name in PAGE_ORDER:
        path = found[name]
        meta, body = split_front_matter(_read(path), path)
        if meta["id"] != name:
            raise LegalSourceError(f"{path}: front-matter id {meta['id']!r} != filename {name!r}")
        markdown = substitute_generation_tokens(
            resolve_includes(body, path, frozenset({path.resolve()})), path, meta["effectiveDate"]
        ).strip("\n")
        if not markdown.strip():
            raise LegalSourceError(f"{path}: rendered to nothing")
        pages.append(
            LegalPage(
                id=meta["id"],
                title=meta["title"],
                effective_date=meta["effectiveDate"],
                status=meta["status"],
                markdown=markdown,
            )
        )
    return pages


# --------------------------------------------------------------------------
# Kotlin target
# --------------------------------------------------------------------------

# The JVM constant pool caps one string literal at 65535 UTF-8 bytes, and
# LICENSE alone is ~35 KB. Chunking keeps every literal far below that and makes
# the generated file diffable.
KOTLIN_CHUNK = 3000


def kotlin_escape(text: str) -> str:
    out: list[str] = []
    for ch in text:
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ch == "$":
            out.append("\\$")
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        elif ord(ch) < 0x20:
            out.append(f"\\u{ord(ch):04x}")
        else:
            out.append(ch)
    return "".join(out)


def _const_name(page_id: str) -> str:
    return "DOC_" + page_id.upper().replace("-", "_")


def render_kotlin(pages: list[LegalPage]) -> str:
    lines: list[str] = [
        "// GENERATED FILE — DO NOT EDIT.",
        "// Regenerate with: python3 scripts/gen_legal_content.py",
        "// Canonical source: legal/*.md. scripts/test_legal_content.py fails the build",
        "// if this file is stale or if its text differs from the web SPA's copy.",
        "package dev.dhun.legal",
        "",
        "/**",
        " * One legal or About page, bundled into the build.",
        " *",
        " * Bundled rather than fetched: these pages must render with no network, on a",
        " * fresh install, on a device that never connects. There is no remote CMS and",
        " * no runtime dependency for legal text.",
        " *",
        " * @param sha256 SHA-256 (hex, lowercase) of [markdown]. The one value the app,",
        " *   the web SPA and `scripts/test_legal_content.py` compare, so the three",
        " *   targets cannot drift apart silently.",
        " */",
        "data class LegalDocument(",
        "    val id: String,",
        "    val title: String,",
        "    val effectiveDate: String,",
        "    val status: String,",
        "    val sha256: String,",
        "    val markdown: String,",
        ") {",
        "    /** True when the page still needs a maintainer or legal decision. */",
        '    val isDraft: Boolean get() = status == "draft"',
        "}",
        "",
        "/**",
        " * Every legal page this build ships, in Settings order.",
        " */",
        "object LegalContent {",
        "",
        "    /** Render-time tokens: the UI replaces these from this build's metadata. */",
        '    const val TOKEN_APP_VERSION = "{{appVersion}}"',
        '    const val TOKEN_APP_VERSION_CODE = "{{appVersionCode}}"',
        '    const val TOKEN_RELEASE_CHANNEL = "{{releaseChannel}}"',
        '    const val TOKEN_PLATFORM = "{{platform}}"',
        "",
        "    /** Joins the generated chunks back into one document body. */",
        "    private fun doc(vararg parts: String): String = parts.joinToString(\"\")",
        "",
        # The per-page vals are emitted BEFORE `val all` on purpose. Kotlin
        # initialises object members in source order, so a `val all = listOf(
        # DOC_ABOUT, ...)` written above the declarations is a forward reference
        # and the compiler rejects it with "Variable 'DOC_ABOUT' must be
        # initialized". That error reads like a missing initialiser and is very
        # easy to misdiagnose, so the ordering is pinned by a test.
    ]
    for page in pages:
        lines += [
            f"    private val {_const_name(page.id)} = LegalDocument(",
            f'        id = "{page.id}",',
            f'        title = "{kotlin_escape(page.title)}",',
            f'        effectiveDate = "{page.effective_date}",',
            f'        status = "{page.status}",',
            f'        sha256 = "{page.sha256}",',
            "        markdown = doc(",
        ]
        text = page.markdown
        for start in range(0, len(text), KOTLIN_CHUNK):
            lines.append(f'            "{kotlin_escape(text[start:start + KOTLIN_CHUNK])}",')
        lines += ["        ),", "    )", ""]

    lines.append("    val all: List<LegalDocument> = listOf(")
    for page in pages:
        lines.append(f"        {_const_name(page.id)},")
    lines += [
        "    )",
        "",
        "    fun byId(id: String): LegalDocument? = all.firstOrNull { it.id == id }",
        "}",
    ]
    return "\n".join(lines).rstrip("\n") + "\n"


# --------------------------------------------------------------------------
# Web target
# --------------------------------------------------------------------------


def render_web(pages: list[LegalPage]) -> str:
    payload = [
        {
            "id": p.id,
            "title": p.title,
            "effectiveDate": p.effective_date,
            "status": p.status,
            "sha256": p.sha256,
            "markdown": p.markdown,
        }
        for p in pages
    ]
    tokens = {
        "appVersion": "{{appVersion}}",
        "appVersionCode": "{{appVersionCode}}",
        "releaseChannel": "{{releaseChannel}}",
        "platform": "{{platform}}",
    }
    # JSON is a subset of JavaScript, so json.dumps doubles as a string-literal
    # escaper. `ensure_ascii=False` keeps the Devanagari name readable, and the
    # file is read back as UTF-8 by both the browser and the tests.
    body = json.dumps(payload, ensure_ascii=False, indent=2)
    return (
        "// GENERATED FILE — DO NOT EDIT.\n"
        "// Regenerate with: python3 scripts/gen_legal_content.py\n"
        "// Canonical source: legal/*.md. scripts/test_legal_content.py fails the build\n"
        "// if this file is stale or if its text differs from the app's bundled copy.\n"
        "\n"
        "/**\n"
        " * Every legal page, bundled into the app. Same documents, same SHA-256 as\n"
        " * `shared/src/commonMain/kotlin/dev/dhun/legal/LegalContent.kt`.\n"
        " */\n"
        f"export const LEGAL_DOCUMENTS = {body};\n"
        "\n"
        "/** Render-time tokens: the app replaces these from this build's metadata. */\n"
        f"export const LEGAL_TOKENS = {json.dumps(tokens, ensure_ascii=False, indent=2)};\n"
        "\n"
        "export function legalDocumentById(id) {\n"
        "  return LEGAL_DOCUMENTS.find((doc) => doc.id === id) ?? null;\n"
        "}\n"
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="do not write; exit 1 if a generated file is stale",
    )
    args = parser.parse_args(argv)

    try:
        pages = load_pages()
    except LegalSourceError as exc:
        print(f"legal content error: {exc}", file=sys.stderr)
        return 1

    targets = {KOTLIN_OUT: render_kotlin(pages), WEB_OUT: render_web(pages)}

    if args.check:
        stale: list[str] = []
        for path, expected in targets.items():
            actual = path.read_text(encoding="utf-8") if path.is_file() else None
            if actual != expected:
                stale.append(str(path.relative_to(REPO)))
        if stale:
            print(
                "legal content is stale — run 'python3 scripts/gen_legal_content.py' "
                "and commit the result:\n  " + "\n  ".join(stale),
                file=sys.stderr,
            )
            return 1
        print(f"legal content up to date ({len(pages)} pages, {len(targets)} targets).")
        return 0

    for path, text in targets.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        rel = path.relative_to(REPO)
        print(f"wrote {rel} ({len(text.encode('utf-8'))} bytes)")
    for page in pages:
        print(f"  {page.id:<22} {page.sha256}  {len(page.markdown):>7} chars")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
