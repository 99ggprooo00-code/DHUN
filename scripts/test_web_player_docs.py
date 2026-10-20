"""Mutation tests for the web build's document gate.

Each rule in `web_player_docs` is fed a document that *must* fail, and the real
directory, which must pass. The pattern is the same one
`test_website_claims.py` uses, and for the same reason: a rule that silently stops
matching is worse than no rule, because the green run is believed.

Every mutation below is one a busy session could actually make: deleting a
module from the table because it was renamed, leaving the old name in a sentence,
citing a test file that moved, trimming the "not verified" list to the one item
that felt important, or copying a sentence from a forum that promises playback.
"""

from __future__ import annotations

import pathlib
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

    def edit(self, root: pathlib.Path, name: str, old: str, new: str) -> pathlib.Path:
        path = root / name
        text = path.read_text(encoding="utf-8")
        self.assertIn(old, text, f"the fixture no longer contains {old!r} — update the test")
        path.write_text(text.replace(old, new, 1), encoding="utf-8")
        return path

    def drop_line(self, root: pathlib.Path, name: str, needle: str) -> pathlib.Path:
        path = root / name
        lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
        kept = [line for line in lines if needle not in line]
        self.assertLess(len(kept), len(lines), f"no line contained {needle!r}")
        path.write_text("".join(kept), encoding="utf-8")
        return path

    def drop_every_line_with(self, root: pathlib.Path, name: str, needle: str) -> pathlib.Path:
        """Remove a module's every mention: the table row and the prose."""
        path = root / name
        lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
        kept = [line for line in lines if needle not in line]
        self.assertLess(len(kept), len(lines), f"no line contained {needle!r}")
        path.write_text("".join(kept), encoding="utf-8")
        return path


class TheRealDirectoryPasses(unittest.TestCase):
    def test_the_checked_in_documents_pass_every_rule(self) -> None:
        self.assertEqual(docs.check(REAL), [])

    def test_a_missing_directory_is_a_finding(self) -> None:
        problems = docs.check(pathlib.Path(tempfile.gettempdir()) / "no-such-docs-here")
        self.assertTrue(problems)
        self.assertIn("no such directory", problems[0])


class Presence(DocsCopyMixin, unittest.TestCase):
    def test_a_deleted_document_is_a_finding(self) -> None:
        root = self.copy_docs()
        (root / "KNOWN-LIMITATIONS.md").unlink()
        problems = docs.check(root)
        self.assertTrue(any("KNOWN-LIMITATIONS.md" in problem for problem in problems))


class TheModuleMapMatchesTheDirectory(DocsCopyMixin, unittest.TestCase):
    def test_a_module_missing_from_the_table_is_a_finding(self) -> None:
        root = self.copy_docs()
        self.drop_every_line_with(root, "ARCHITECTURE.md", "`js/player.js`")
        problems = docs.check(root)
        self.assertTrue(
            any("js/player.js exists but the module table does not name it" in p for p in problems),
            problems,
        )

    def test_a_module_the_table_invents_is_a_finding(self) -> None:
        root = self.copy_docs()
        self.edit(
            root,
            "ARCHITECTURE.md",
            "| `js/version.js` |",
            "| `js/ghost.js` | A module that was deleted |\n| `js/version.js` |",
        )
        problems = docs.check(root)
        self.assertTrue(
            any("js/ghost.js, which does not exist" in p for p in problems),
            problems,
        )

    def test_a_new_module_must_be_added_to_the_table(self) -> None:
        """The rule bites in the direction that matters: the build moved on."""
        root = self.copy_docs()
        self.drop_line(root, "ARCHITECTURE.md", "`js/shortcuts.js` |")
        problems = docs.check(root)
        self.assertTrue(any("js/shortcuts.js exists" in p for p in problems), problems)


class EvidenceResolves(DocsCopyMixin, unittest.TestCase):
    def test_a_row_citing_a_path_that_does_not_exist_is_a_finding(self) -> None:
        root = self.copy_docs()
        self.edit(root, "IMPLEMENTATION-STATUS.md", "`app-web/tests/`", "`app-web/tests-gone/`")
        problems = docs.check(root)
        self.assertTrue(any("does not exist" in problem for problem in problems), problems)

    def test_a_row_citing_nothing_is_a_finding(self) -> None:
        root = self.copy_docs()
        self.edit(
            root,
            "IMPLEMENTATION-STATUS.md",
            "| 147 assertions over the pure modules pass with no install and no network | `app-web/tests/`",
            "| 147 assertions over the pure modules pass | the tests, obviously",
        )
        problems = docs.check(root)
        self.assertTrue(any("cites no path" in problem for problem in problems), problems)

    def test_a_thin_table_is_a_finding(self) -> None:
        root = self.copy_docs()
        path = root / "IMPLEMENTATION-STATUS.md"
        text = path.read_text(encoding="utf-8")
        start = text.find(docs.STATUS_HEADING)
        end = text.find("\n## ", start + 1)
        table = text[start:end]
        rows = [line for line in table.splitlines() if line.startswith("|")]
        kept = "\n".join(rows[:4])  # header, separator, two rows
        path.write_text(text[:start] + docs.STATUS_HEADING + "\n\n" + kept + "\n" + text[end:], encoding="utf-8")
        problems = docs.check(root)
        self.assertTrue(any("fewer than five rows" in problem for problem in problems), problems)


class Honesty(DocsCopyMixin, unittest.TestCase):
    def test_removing_the_unverified_section_is_a_finding(self) -> None:
        root = self.copy_docs()
        path = root / "IMPLEMENTATION-STATUS.md"
        text = path.read_text(encoding="utf-8")
        path.write_text(text.replace(docs.UNVERIFIED_HEADING, "## Everything else", 1), encoding="utf-8")
        problems = docs.check(root)
        self.assertTrue(any("Not verified" in problem for problem in problems), problems)

    def test_shrinking_the_unverified_list_is_a_finding(self) -> None:
        root = self.copy_docs()
        path = root / "IMPLEMENTATION-STATUS.md"
        text = path.read_text(encoding="utf-8")
        start = text.find(docs.UNVERIFIED_HEADING)
        end = text.find("\n## ", start + 1)
        block = text[start:end]
        items = [line for line in block.splitlines() if line.startswith("- ")]
        self.assertGreater(len(items), 1)
        kept_cuts = set(items[1:])
        trimmed = "".join(
            line + "\n" for line in block.splitlines() if line not in kept_cuts
        )
        path.write_text(text[:start] + trimmed + text[end:], encoding="utf-8")
        problems = docs.check(root)
        self.assertTrue(any("Not verified" in problem and "item(s)" in problem for problem in problems), problems)

    def test_a_forbidden_claim_is_a_finding(self) -> None:
        root = self.copy_docs()
        self.edit(
            root,
            "README.md",
            "## The priority order",
            "The web build offers offline playback of everything you save.\n\n## The priority order",
        )
        problems = docs.check(root)
        self.assertTrue(any("forbidden claim" in problem for problem in problems), problems)


class RetiredNames(DocsCopyMixin, unittest.TestCase):
    def test_a_bare_mention_of_a_deleted_module_is_a_finding(self) -> None:
        root = self.copy_docs()
        self.edit(
            root,
            "DEVELOPMENT-GUIDE.md",
            "## Adding a screen",
            "The player lives in `js/source.js`.\n\n## Adding a screen",
        )
        problems = docs.check(root)
        self.assertTrue(any("js/source.js" in problem for problem in problems), problems)

    def test_a_mention_that_says_it_is_gone_passes(self) -> None:
        root = self.copy_docs()
        self.edit(
            root,
            "DEVELOPMENT-GUIDE.md",
            "## Adding a screen",
            "The old adapter, `js/source.js`, no longer exists.\n\n## Adding a screen",
        )
        self.assertEqual(docs.check(root), [])

    def test_a_retired_phase_document_is_a_finding(self) -> None:
        root = self.copy_docs()
        self.edit(
            root,
            "README.md",
            "## The priority order",
            "See PHASE-02-PLAYBACK-POC.md for the plan.\n\n## The priority order",
        )
        problems = docs.check(root)
        self.assertTrue(any("PHASE-02" in problem for problem in problems), problems)


if __name__ == "__main__":
    unittest.main()
