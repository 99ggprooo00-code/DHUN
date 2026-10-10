"""The legal pages are generated, so this suite is what stops them drifting.

Three things must stay true, and none of them is provable by argument:

1. **The generated files are current.** ``LegalContent.kt`` (Android + Windows)
   and ``legal-content.js`` (web SPA) are both derived from ``legal/*.md``. A
   hand edit to either, or a hand edit to a canonical page without regenerating,
   is a build failure here rather than a wrong policy text in a release.
2. **Every target carries the same bytes.** Each document records the SHA-256 of
   its rendered Markdown. The Kotlin bundle and the JS bundle are compared
   document by document on that digest, so "in-app text matches the other copy"
   is an assertion rather than a hope.
3. **The content is honest.** Full licence texts are not truncated, every factual
   sentence carries a status tag, no invented contact address exists, and the
   forbidden claims stay forbidden.

None of this needs the network, Gradle, Node or a browser.
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import gen_legal_content as gen  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def norm(text: str) -> str:
    """Collapse whitespace so prose assertions survive a re-wrap.

    The canonical Markdown is hard-wrapped near column 80. A sentence therefore
    straddles newlines, and an assertion written against the sentence as one line
    would fail the moment someone reformats the file. Matching on the collapsed
    form is what the reader actually sees, so it is what the test should check.
    """
    return re.sub(r"\s+", " ", text).strip()


#: Every value `status:` may take. A typo here would silently switch off the
#: draft-banner rule below, so the vocabulary is asserted rather than assumed.
KNOWN_STATUSES = frozenset({"draft", "current", "reference"})


def _js_documents() -> list[dict]:
    """Pull the JSON array out of the generated ES module."""
    body = _read(gen.WEB_OUT)
    match = re.search(r"export const LEGAL_DOCUMENTS = (\[.*?\n\]);\n", body, re.DOTALL)
    assert match, "legal-content.js does not declare LEGAL_DOCUMENTS as expected"
    return json.loads(match.group(1))


def _kotlin_documents() -> dict[str, dict]:
    """Read the (id, title, effectiveDate, status, sha256) of each Kotlin doc."""
    body = _read(gen.KOTLIN_OUT)
    pattern = re.compile(
        r'id = "(?P<id>[^"]+)",\s*'
        r'title = "(?P<title>[^"]*)",\s*'
        r'effectiveDate = "(?P<effectiveDate>[^"]+)",\s*'
        r'status = "(?P<status>[^"]+)",\s*'
        r'sha256 = "(?P<sha256>[0-9a-f]{64})",',
    )
    docs = {m.group("id"): m.groupdict() for m in pattern.finditer(body)}
    assert docs, "LegalContent.kt declares no documents"
    return docs


class CanonicalSourcesAreWellFormed(unittest.TestCase):
    """The front matter is the update process: no date, no page."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.pages = {p.id: p for p in gen.load_pages()}

    def test_every_ordered_page_exists_and_nothing_extra_does(self) -> None:
        self.assertEqual(set(self.pages), set(gen.PAGE_ORDER))

    def test_every_page_declares_an_effective_date(self) -> None:
        for page in self.pages.values():
            self.assertRegex(
                page.effective_date,
                r"^\d{4}-\d{2}-\d{2}$",
                f"{page.id}: effectiveDate must be a real date",
            )

    def test_every_status_value_is_one_we_know(self) -> None:
        for page in self.pages.values():
            self.assertIn(
                page.status, KNOWN_STATUSES, f"{page.id}: unknown status {page.status!r}"
            )

    def test_no_generation_token_survives_into_the_output(self) -> None:
        for page in self.pages.values():
            leftover = re.findall(r"\{\{(appTitle|appTitleDevanagari|effectiveDate)\}\}", page.markdown)
            self.assertEqual([], leftover, f"{page.id}: unresolved generation token(s) {leftover}")

    def test_no_include_directive_survives_into_the_output(self) -> None:
        for page in self.pages.values():
            self.assertNotIn("{{include:", page.markdown, f"{page.id}: include was not spliced")

    def test_render_time_tokens_appear_only_where_the_ui_fills_them(self) -> None:
        about = self.pages["about"].markdown
        for token in ("appVersion", "appVersionCode", "releaseChannel", "platform"):
            self.assertIn("{{" + token + "}}", about, f"about must carry {{{{{token}}}}}")
        # Nowhere else may mention a version: the version comes from build
        # metadata, so a second, typed copy would be a lie the moment it drifts.
        for page_id, page in self.pages.items():
            if page_id == "about":
                continue
            for token in gen.RUNTIME_TOKENS:
                self.assertNotIn("{{" + token + "}}", page.markdown, f"{page_id}: stray runtime token")

    def test_the_document_sha_is_the_sha_of_its_own_text(self) -> None:
        for page in self.pages.values():
            self.assertEqual(
                page.sha256,
                hashlib.sha256(page.markdown.encode("utf-8")).hexdigest(),
                f"{page.id}: digest does not match its text",
            )


class GeneratedTargetsAreCurrent(unittest.TestCase):
    """A stale generated file means a shipped page that no longer matches legal/."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.pages = gen.load_pages()

    def test_the_kotlin_bundle_matches_the_generator(self) -> None:
        self.assertTrue(gen.KOTLIN_OUT.is_file(), "LegalContent.kt is not committed")
        self.assertEqual(
            gen.render_kotlin(self.pages),
            _read(gen.KOTLIN_OUT),
            "LegalContent.kt is stale — run: python3 scripts/gen_legal_content.py",
        )

    def test_the_web_bundle_matches_the_generator(self) -> None:
        self.assertTrue(gen.WEB_OUT.is_file(), "legal-content.js is not committed")
        self.assertEqual(
            gen.render_web(self.pages),
            _read(gen.WEB_OUT),
            "legal-content.js is stale — run: python3 scripts/gen_legal_content.py",
        )

    def test_the_generated_files_say_they_are_generated(self) -> None:
        for path in (gen.KOTLIN_OUT, gen.WEB_OUT):
            head = _read(path).split("\n")[:2]
            self.assertIn("GENERATED FILE", head[0], f"{path.name}: missing the do-not-edit banner")
            self.assertIn("gen_legal_content.py", head[1], f"{path.name}: banner omits the command")


class TargetsCarryTheSameContent(unittest.TestCase):
    """The one requirement the brief calls out: the copies must not diverge."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.canonical = {p.id: p for p in gen.load_pages()}
        cls.web = {d["id"]: d for d in _js_documents()}
        cls.kotlin = _kotlin_documents()

    def test_all_three_sources_name_the_same_pages(self) -> None:
        self.assertEqual(set(self.canonical), set(self.web), "web bundle page set differs")
        self.assertEqual(set(self.canonical), set(self.kotlin), "Kotlin bundle page set differs")

    def test_every_document_digest_agrees_across_targets(self) -> None:
        for page_id, page in self.canonical.items():
            self.assertEqual(
                page.sha256, self.kotlin[page_id]["sha256"], f"{page_id}: Kotlin digest differs"
            )
            self.assertEqual(
                page.sha256, self.web[page_id]["sha256"], f"{page_id}: web digest differs"
            )

    def test_the_web_digest_is_recomputed_from_its_own_payload(self) -> None:
        """Not just copied across: the web text really hashes to its digest."""
        for page_id, doc in self.web.items():
            self.assertEqual(
                doc["sha256"],
                hashlib.sha256(doc["markdown"].encode("utf-8")).hexdigest(),
                f"{page_id}: web markdown does not hash to its recorded digest",
            )

    def test_metadata_agrees_across_targets(self) -> None:
        for page_id, page in self.canonical.items():
            for field, value in (
                ("title", page.title),
                ("effectiveDate", page.effective_date),
                ("status", page.status),
            ):
                self.assertEqual(value, self.web[page_id][field], f"{page_id}.{field} differs in web")
                self.assertEqual(value, self.kotlin[page_id][field], f"{page_id}.{field} differs in Kotlin")

    def test_no_page_is_empty_or_stubbed_out(self) -> None:
        for page_id, page in self.canonical.items():
            self.assertGreater(len(page.markdown), 400, f"{page_id} looks like a stub")
            self.assertNotIn("TODO", page.markdown, f"{page_id} still has a TODO")
            self.assertNotIn("Lorem ipsum", page.markdown, f"{page_id} has placeholder text")


class LicenceTextsAreNotTruncated(unittest.TestCase):
    """GPL-3.0 §1 and Apache-2.0 §4 both require the full text to travel."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.licences = next(
            p.markdown for p in gen.load_pages() if p.id == "open-source-licenses"
        )

    def test_the_gpl_v3_text_is_complete(self) -> None:
        upstream = _read(ROOT / "LICENSE").strip()
        self.assertIn(upstream, self.licences, "the GPL-3.0 text is not reproduced verbatim")
        # First and last lines, so a mid-file elision cannot pass.
        lines = upstream.split("\n")
        for probe in (lines[0], lines[-1]):
            self.assertIn(probe, self.licences, f"GPL-3.0 text is missing: {probe[:60]!r}")
        self.assertIn("END OF TERMS AND CONDITIONS", self.licences)

    def test_the_apache_text_is_complete(self) -> None:
        upstream = _read(ROOT / "LICENSES" / "MaterialDesignIcons-Apache-2.0.txt").strip()
        self.assertIn(upstream, self.licences, "the Apache-2.0 text is not reproduced verbatim")
        self.assertIn("APPENDIX: How to apply the Apache License", self.licences)

    def test_the_licence_page_admits_what_it_does_not_reproduce(self) -> None:
        self.assertIn("not reproduced", self.licences.lower())

    def test_the_dependency_record_travels_with_the_app(self) -> None:
        notices = next(p.markdown for p in gen.load_pages() if p.id == "third-party-notices")
        upstream = _read(ROOT / "THIRD_PARTY.md").strip()
        self.assertIn(upstream, notices, "THIRD_PARTY.md is not reproduced in full")


class PolicyTextIsHonest(unittest.TestCase):
    """The brief's forbidden claims, asserted against the rendered pages."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.pages = {p.id: p for p in gen.load_pages()}
        cls.all_text = "\n".join(p.markdown for p in cls.pages.values())

    def test_a_draft_page_says_it_is_a_draft(self) -> None:
        """Only `draft` pages: `reference` pages reproduce a repo file verbatim,
        and pasting DRAFT over the GPL text would misdescribe that text."""
        drafts = [p for p in self.pages.values() if p.status == "draft"]
        self.assertTrue(drafts, "no page is a draft — the rule would be vacuous")
        for page in drafts:
            self.assertIn("DRAFT", page.markdown, f"{page.id} is draft but never says so")

    def test_no_page_claims_compliance_with_law_or_platform_terms(self) -> None:
        for phrase in (
            "complies with all laws",
            "compliant with YouTube",
            "authorised by YouTube",
            "authorized by YouTube",
            "approved by Google",
            "official YouTube",
        ):
            self.assertNotIn(phrase.lower(), norm(self.all_text).lower(), f"forbidden claim: {phrase}")

    def test_independence_is_stated_not_implied(self) -> None:
        for page_id in ("about", "terms"):
            text = self.pages[page_id].markdown
            self.assertIn(
                "not affiliated with", norm(text), f"{page_id} must state the disaffiliation"
            )

    def test_deleting_local_data_is_not_confused_with_deleting_theirs(self) -> None:
        self.assertIn("does not delete anything YouTube or Google", norm(self.all_text))

    def test_the_lyrics_setting_gap_is_disclosed(self) -> None:
        """The setting exists and gates nothing; the policy must say so."""
        privacy = norm(self.pages["privacy"].markdown)
        self.assertIn("lyrics_enabled", privacy)
        self.assertIn("no code reads it", privacy)

    def test_no_contact_address_is_invented(self) -> None:
        emails = re.findall(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", self.all_text)
        self.assertEqual([], emails, f"a contact address appears with no maintainer behind it: {emails}")
        for page_id in ("privacy", "support", "terms"):
            self.assertIn(
                "not yet published",
                norm(self.pages[page_id].markdown),
                f"{page_id} must say a contact is not yet published",
            )

    def test_the_support_page_links_the_real_tracker(self) -> None:
        """The brief asks for bug and feature links; prose alone is not a link."""
        support = norm(self.pages["support"].markdown)
        for target in (
            "https://github.com/99ggprooo00-code/DHUN/issues/new",
            "https://github.com/99ggprooo00-code/DHUN/issues",
        ):
            self.assertIn(target, support, f"the support page must link {target}")
        self.assertIn("Report a bug", support)
        self.assertIn("Request a feature", support)
        # And the links must be real Markdown links, not bare URLs in prose.
        self.assertRegex(support, r"\[Report a bug\]\(https://")
        self.assertRegex(support, r"\[Request a feature\]\(https://")

    def test_the_security_page_names_its_interim_channel_as_a_link(self) -> None:
        page = norm(self.pages["security-reporting"].markdown)
        self.assertIn("https://github.com/99ggprooo00-code/DHUN/issues/new", page)
        self.assertIn("Security contact request", page)

    def test_the_security_page_reproduces_the_real_policy(self) -> None:
        page = self.pages["security-reporting"].markdown
        upstream = _read(ROOT / "SECURITY.md").strip()
        self.assertIn(upstream, page, "SECURITY.md is not reproduced in full")
        self.assertIn("Security contact request", page, "the interim procedure is missing")

    def test_every_documented_privacy_control_is_one_that_exists(self) -> None:
        """Guards against a policy describing a control the app does not have."""
        raw = self.pages["support"].markdown
        support = norm(raw)
        for control in ("Clear downloads", "Clear history", "Clear recent searches"):
            self.assertIn(control, support, f"{control} is a real control and must be listed")
        for absent in ("Clear audio cache", "Clear lyrics cache", "Delete your account"):
            self.assertNotIn(f"| {absent} |", raw, f"{absent} does not exist as a UI control")
        self.assertIn("Controls that do not exist", support)


# --------------------------------------------------------------------------
# Kotlin syntax invariants that cannot be checked here without a JVM
# --------------------------------------------------------------------------
#
# CI compiles the shared module, but a compile failure costs a full pipeline run
# and its log lives on a host that is not always reachable. Both bugs below were
# real, both were caught only by CI, and both are cheap to pin in Python.


def _kotlin_comment_state(src: str) -> tuple[int, int | None, bool]:
    """Walk Kotlin source tracking nested comments, strings and raw strings.

    Returns ``(comment depth at EOF, line a still-open comment started on,
    raw string still open)``. Kotlin block comments nest, which is the whole
    reason this exists: a literal slash-star inside a KDoc opens a comment that
    nothing in the file closes.
    """
    i, n = 0, len(src)
    depth = 0
    opened: int | None = None
    raw = False

    def line(pos: int) -> int:
        return src[:pos].count("\n") + 1

    while i < n:
        c, d = src[i], src[i + 1 : i + 2]
        if raw:
            if src[i : i + 3] == '"""':
                raw = False
                i += 3
            else:
                i += 1
            continue
        if depth > 0:
            if c == "/" and d == "*":
                depth += 1
                i += 2
            elif c == "*" and d == "/":
                depth -= 1
                if depth == 0:
                    opened = None
                i += 2
            else:
                i += 1
            continue
        if src[i : i + 3] == '"""':
            raw = True
            i += 3
            continue
        if c == '"':
            i += 1
            while i < n and src[i] != '"':
                if src[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        if c == "\'":
            i += 1
            while i < n and src[i] != "\'":
                if src[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue
        if c == "/" and d == "/":
            while i < n and src[i] != "\n":
                i += 1
            continue
        if c == "/" and d == "*":
            depth += 1
            opened = line(i)
            i += 2
            continue
        i += 1
    return depth, opened, raw


class TestGeneratedKotlinParses(unittest.TestCase):
    """Guards the two syntax errors CI found in the first version of this file."""

    KOTLIN_TARGET = gen.KOTLIN_OUT
    MARKDOWN_KT = gen.KOTLIN_OUT.parent / "Markdown.kt"

    def test_no_unclosed_block_comment_or_raw_string(self) -> None:
        """A slash-star inside a KDoc swallows the rest of the file.

        The first revision of Markdown.kt wrote ``legal/*.md`` and
        ``LICENSES/*.txt`` in its header KDoc. Kotlin comments nest, so those two
        opened comments nothing closed, the compiler reported "Unclosed comment"
        at EOF, and MarkdownBlock/MarkdownParser became unresolved in
        AboutLegalScreen.kt.
        """
        for path in (self.MARKDOWN_KT, self.KOTLIN_TARGET):
            with self.subTest(path=path.name):
                depth, opened, raw = _kotlin_comment_state(path.read_text(encoding="utf-8"))
                self.assertEqual(depth, 0, f"{path.name}: block comment opened at line {opened} never closes")
                self.assertFalse(raw, f"{path.name}: unterminated raw string")

    def test_no_comment_contains_a_literal_slash_star(self) -> None:
        """The direct cause of the bug above, checked over the whole corpus."""
        offenders = []
        for path in (self.MARKDOWN_KT, self.KOTLIN_TARGET):
            src = path.read_text(encoding="utf-8")
            depth, _, _ = 0, None, False
            # Re-walk, recording any "/*" seen while already inside a comment.
            i, n = 0, len(src)
            while i < n:
                c, d = src[i], src[i + 1 : i + 2]
                if src[i : i + 3] == '"""':
                    i += 3
                    while i < n and src[i : i + 3] != '"""':
                        i += 1
                    i += 3
                    continue
                if depth == 0 and c == "/" and d == "/":
                    while i < n and src[i] != "\n":
                        i += 1
                    continue
                if c == "/" and d == "*":
                    if depth > 0:
                        offenders.append(f"{path.name}:{src[:i].count(chr(10)) + 1}")
                    depth += 1
                    i += 2
                    continue
                if c == "*" and d == "/":
                    depth = max(0, depth - 1)
                    i += 2
                    continue
                i += 1
        self.assertEqual(offenders, [], f"nested slash-star inside a comment at {offenders}")

    def test_val_all_is_declared_after_the_pages_it_lists(self) -> None:
        """Kotlin initialises object members in source order.

        The first revision emitted `val all = listOf(DOC_ABOUT, ...)` above the
        `private val DOC_ABOUT = LegalDocument(...)` declarations. That is a
        forward reference, and the compiler rejects it with "Variable 'DOC_ABOUT'
        must be initialized" — which reads like a missing initialiser and is easy
        to misdiagnose as a generator escaping bug.
        """
        lines = self.KOTLIN_TARGET.read_text(encoding="utf-8").split("\n")
        decls = {
            i: line.strip()
            for i, line in enumerate(lines)
            if line.strip().startswith("private val DOC_")
        }
        all_line = next(
            (i for i, line in enumerate(lines) if line.strip().startswith("val all: List<LegalDocument>")),
            None,
        )
        self.assertIsNotNone(all_line, "`val all` is missing from the generated object")
        self.assertTrue(decls, "no per-page vals were emitted")
        first_decl = min(decls)
        self.assertGreater(
            all_line,
            first_decl,
            f"`val all` is on line {all_line + 1} but the first DOC_ val is on line "
            f"{first_decl + 1}; Kotlin would reject the forward reference",
        )
        # And every name `val all` lists must have a declaration.
        block = lines[all_line : all_line + len(decls) + 4]
        listed = [
            b.strip().rstrip(",")
            for b in block
            if b.strip().startswith("DOC_")
        ]
        declared = {d.split(" ")[2] for d in decls.values()}
        self.assertEqual(sorted(listed), sorted(declared))


if __name__ == "__main__":
    unittest.main()
