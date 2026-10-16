"""Mutation tests for the web workstream's document gate.

Each rule in `website/web_player_docs` is fed a document that *must* fail and the
real directory, which must pass. The pattern is the same one
`scripts/test_website_claims.py` uses, and for the same reason: a rule that
silently stops matching is worse than no rule, because the green run is believed.

Every mutation below is one a busy session could actually make — deleting a phase
file because it looked empty, ticking a box because the work felt done, copying a
sentence from a forum that promises playback, renumbering the tracker so phase 2
looks closed.
"""

from __future__ import annotations

import pathlib
import re
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import web_player_docs as docs  # noqa: E402

REAL = docs.DEFAULT_DIR


class DocsCopyMixin:
    """A writable copy of `docs/web-player/`, so a test can break it."""

    def copy_docs(self) -> pathlib.Path:
        temp = pathlib.Path(tempfile.mkdtemp(prefix="dhun-web-player-docs-"))
        self.addCleanup(shutil.rmtree, temp, ignore_errors=True)
        target = temp / "web-player"
        shutil.copytree(REAL, target)
        return target

    def set_cell(
        self, root: pathlib.Path, name: str, phase: str, column: str, value: str
    ) -> pathlib.Path:
        """Set one cell of the tracker's phase table, by column name.

        Mutation tests written as whole-line string replacements fail when a
        word in the row changes; addressing the cell keeps each test aimed at
        the *rule* under test rather than at today's wording of the table.
        """
        path = root / name
        lines = path.read_text(encoding="utf-8").splitlines()
        header: list[str] | None = None
        for index, line in enumerate(lines):
            if not line.startswith("|"):
                continue
            cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
            if header is None:
                header = cells
                continue
            if len(cells) != len(header) or cells[0] != phase:
                continue
            cells[header.index(column)] = value
            lines[index] = "| " + " | ".join(cells) + " |"
            break
        else:
            self.fail(f"no '{phase}' row in {name}")
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return path

    def drop_row(self, root: pathlib.Path, name: str, phase: str) -> pathlib.Path:
        path = root / name
        lines = path.read_text(encoding="utf-8").splitlines()
        kept = [line for line in lines if not line.startswith(f"| {phase} |")]
        self.assertEqual(len(kept), len(lines) - 1, f"no '| {phase} |' row in {name}")
        path.write_text("\n".join(kept) + "\n", encoding="utf-8")
        return path

    def mutate(self, root: pathlib.Path, name: str, change) -> pathlib.Path:
        path = root / name
        text = path.read_text(encoding="utf-8")
        path.write_text(change(text), encoding="utf-8")
        return path

    def assert_reports(self, root: pathlib.Path, needle: str) -> None:
        problems = docs.check(root)
        self.assertTrue(
            any(needle in problem for problem in problems),
            f"expected a problem mentioning {needle!r}; got {problems}",
        )


class StructureRules(DocsCopyMixin, unittest.TestCase):
    def test_the_real_documents_pass(self):
        self.assertEqual([], docs.check(REAL))

    def test_a_missing_phase_file_fails(self):
        root = self.copy_docs()
        (root / "PHASE-04-SEARCH-AND-CATALOGUE.md").unlink()
        self.assert_reports(root, "missing document")

    def test_a_phase_file_without_its_sections_fails(self):
        root = self.copy_docs()
        self.mutate(
            root,
            "PHASE-05-PLAYBACK-ENGINE.md",
            lambda text: text.replace("## Acceptance criteria", "## Notes"),
        )
        self.assert_reports(root, "missing section '## Acceptance criteria'")

    def test_a_phase_file_without_a_status_line_fails(self):
        root = self.copy_docs()
        self.mutate(root, "PHASE-03-ARCHITECTURE.md", lambda text: text.replace("> Status: ✅ Complete", ""))
        self.assert_reports(root, "no '> Status:")

    def test_a_phase_file_without_a_decision_fails(self):
        root = self.copy_docs()
        self.mutate(root, "PHASE-06-INTERFACE.md", lambda text: text.replace("**Go / No-Go:", "**Verdict:"))
        self.assert_reports(root, "no explicit Go/No-Go decision")


class TrackerRules(DocsCopyMixin, unittest.TestCase):
    def test_a_completed_phase_with_no_evidence_fails(self):
        root = self.copy_docs()
        self.set_cell(root, "IMPLEMENTATION-STATUS.md", "3", "Evidence", "—")
        self.assert_reports(root, "with no evidence beside it")

    def test_evidence_that_does_not_point_at_its_phase_file_fails(self):
        root = self.copy_docs()
        self.set_cell(
            root,
            "IMPLEMENTATION-STATUS.md",
            "5",
            "Evidence",
            "the code in `app-web/src/js/player.js`, which is unit-tested",
        )
        self.assert_reports(root, "does not point at its phase file")

    def test_exceptions_must_be_written_down(self):
        root = self.copy_docs()
        self.set_cell(root, "IMPLEMENTATION-STATUS.md", "4", "Remaining work", "—")
        self.assert_reports(root, "does not say what the exception is")

    def test_a_ticked_box_with_no_evidence_fails(self):
        root = self.copy_docs()
        self.mutate(
            root,
            "IMPLEMENTATION-STATUS.md",
            lambda text: re.sub(r"^- \[x\] \*\*No tracking\*\*.*?(?=\n- |\n\n)", "- [x] **No tracking**", text, flags=re.S | re.M),
        )
        self.assert_reports(root, "ticked box with no evidence")

    def test_a_ticked_box_with_an_empty_evidence_note_fails(self):
        root = self.copy_docs()
        self.mutate(
            root,
            "IMPLEMENTATION-STATUS.md",
            lambda text: text.replace(
                "- [x] **Licensing and attribution are recorded** — `LICENSE` (GPL-3.0-or-later),",
                "- [x] **Licensing and attribution are recorded** — ok",
            ),
        )
        self.assert_reports(root, "empty evidence note")

    def test_a_status_mark_outside_the_legend_fails(self):
        root = self.copy_docs()
        self.set_cell(root, "IMPLEMENTATION-STATUS.md", "5", "Status", "🎉 Done")
        self.assert_reports(root, "legend marks")

    def test_the_release_line_must_match_the_gate(self):
        root = self.copy_docs()
        self.mutate(
            root,
            "IMPLEMENTATION-STATUS.md",
            lambda text: text.replace("NOT VERIFIED FOR RELEASE", "READY FOR RELEASE"),
        )
        self.assert_reports(root, "NOT VERIFIED FOR RELEASE")

    def test_a_phase_row_that_disappears_fails(self):
        root = self.copy_docs()
        self.drop_row(root, "IMPLEMENTATION-STATUS.md", "7")
        self.assert_reports(root, "no row for phase(s) 7")


class ClaimRules(DocsCopyMixin, unittest.TestCase):
    def test_a_promise_of_browser_playback_in_a_document_fails(self):
        root = self.copy_docs()
        self.mutate(
            root,
            "PHASE-02-PLAYBACK-POC.md",
            lambda text: text + "\n\nIt plays in every browser, so this phase is done.\n",
        )
        self.assert_reports(root, "forbidden claim")

    def test_a_denial_of_a_shipped_surface_fails(self):
        root = self.copy_docs()
        self.mutate(
            root,
            "KNOWN-LIMITATIONS.md",
            lambda text: text + "\n\nThere is no web app in this repository.\n",
        )
        self.assert_reports(root, "denies the browser client")

    def test_a_quality_figure_fails(self):
        root = self.copy_docs()
        self.mutate(root, "README.md", lambda text: text + "\n\nStreams at 320 kbps.\n")
        self.assert_reports(root, "forbidden claim")

    def test_an_acceptable_sentence_passes(self):
        """The gate must not be a ban on the subject — only on unsupportable claims."""
        root = self.copy_docs()
        self.mutate(
            root,
            "PHASE-02-PLAYBACK-POC.md",
            lambda text: text
            + "\n\nPlayback is not proven, no audio has been heard, and the native builds "
            "remain the supported players.\n",
        )
        self.assertEqual([], docs.check(root))

    def test_code_blocks_are_not_scanned(self):
        """A quoted rule is documentation, not a claim."""
        root = self.copy_docs()
        self.mutate(
            root,
            "README.md",
            lambda text: text + "\n\n```text\nIt plays in every browser.\n```\n",
        )
        self.assertEqual([], docs.check(root))


if __name__ == "__main__":
    unittest.main()
